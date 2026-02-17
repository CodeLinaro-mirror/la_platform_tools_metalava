/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrVariableTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JVM_FIELD
import com.android.tools.metalava.model.JVM_NAME
import com.android.tools.metalava.model.JVM_STATIC
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter

// Enforces the interoperability guidelines outlined in
//   https://android.github.io/kotlin-guides/interop.html
//
// Also potentially makes other API suggestions.
class KotlinInteropChecks(val reporter: Reporter) {
    fun checkField(field: FieldItem, isKotlin: Boolean = field.isKotlin()) {
        ensureFieldNameNotKeyword(field)
    }

    fun checkMethod(method: MethodItem, isKotlin: Boolean = method.isKotlin()) {
        if (isKotlin) {
            ensureDefaultParamsHaveJvmOverloads(method)
            ensureCompanionJvmStatic(method)
            disallowValueClassUsageWithoutJvmName(method)
        } else {
            ensureMethodNameNotKeyword(method)
            ensureParameterNamesNotKeywords(method)
            ensureLambdaLastParameter(method)
        }
    }

    /**
     * Check for interop issues on the [cls]. The [filteredMembers] should be any callables and
     * fields defined on the class which are part of the API surface.
     */
    fun checkClass(
        cls: ClassItem,
        filteredMembers: Sequence<MemberItem>,
        isKotlin: Boolean = cls.isKotlin(),
    ) {
        if (isKotlin) {
            disallowValueClasses(cls)
            requireJvmNameForFacadeClass(cls, filteredMembers)
        }
    }

    fun checkConstructor(constructor: ConstructorItem, isKotlin: Boolean = constructor.isKotlin()) {
        if (isKotlin) {
            disallowValueClassUsageInConstructorParameters(constructor)
        }
    }

    fun checkProperty(property: PropertyItem) {
        ensureCompanionJvmField(property)
        disallowValueClassUsageWithoutJvmName(property)
    }

    private fun ensureLambdaLastParameter(method: MethodItem) {
        val parameters = method.parameters()
        if (parameters.size > 1) {
            // Make sure that SAM-compatible parameters are last
            val lastIndex = parameters.size - 1
            if (!isSamCompatible(parameters[lastIndex])) {
                for (i in lastIndex - 1 downTo 0) {
                    val parameter = parameters[i]
                    if (isSamCompatible(parameter)) {
                        val message =
                            "SAM-compatible parameters (such as parameter ${i + 1}, " +
                                "\"${parameter.name()}\", in ${
                                method.containingClass().qualifiedName()}.${method.name()
                                }) should be last to improve Kotlin interoperability; see " +
                                "https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions"
                        reporter.report(Issues.SAM_SHOULD_BE_LAST, method, message)
                        break
                    }
                }
            }
        }
    }

    /**
     * Warn if functions in unnamed companions are not marked with @JvmStatic.
     *
     * This is so Java developers don't have to access the functions through the "Companion" class,
     * but if the companion is named, accessing the function through the named object isn't the same
     * kind of interop issue.
     *
     * See https://developer.android.com/kotlin/interop#companion-functions
     */
    private fun ensureCompanionJvmStatic(method: MethodItem) {
        if (
            method.containingClass().modifiers.isCompanion() &&
                method.containingClass().simpleName() == "Companion" &&
                // Many properties will be checked through [ensureCompanionJvmField]. If this method
                // is not a property or its property can't use @JvmField, it should use @JvmStatic.
                method.property?.canHaveJvmField() != true &&
                method.modifiers.findAnnotation(JVM_STATIC) == null &&
                method.property?.modifiers?.findAnnotation(JVM_STATIC) == null
        ) {
            reporter.report(
                Issues.MISSING_JVMSTATIC,
                method,
                "Companion object methods like ${method.name()} should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions"
            )
        }
    }

    /**
     * Warn if constants in unnamed companions are not marked with @JvmField.
     *
     * Properties that we can expect to be constant (that is, declared via `val`, so they don't have
     * a setter) but that aren't declared 'const' in a companion object should have @JvmField, and
     * not have @JvmStatic.
     *
     * This is so Java developers don't have to access the constants through the "Companion" class,
     * but if the companion is named, accessing the constant through the named object isn't the same
     * kind of interop issue.
     *
     * See https://developer.android.com/kotlin/interop#companion-constants
     */
    private fun ensureCompanionJvmField(property: PropertyItem) {
        if (
            property.containingClass().modifiers.isCompanion() &&
                property.containingClass().simpleName() == "Companion" &&
                property.canHaveJvmField()
        ) {
            if (property.modifiers.findAnnotation(JVM_STATIC) != null) {
                reporter.report(
                    Issues.MISSING_JVMSTATIC,
                    property,
                    "Companion object constants like ${property.name()} should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants"
                )
            } else if (property.backingField?.modifiers?.findAnnotation(JVM_FIELD) == null) {
                reporter.report(
                    Issues.MISSING_JVMSTATIC,
                    property,
                    "Companion object constants like ${property.name()} should be marked @JvmField for Java interoperability; see https://developer.android.com/kotlin/interop#companion_constants"
                )
            }
        }
    }

    /**
     * Whether the property (assumed to be a companion property) is allowed to be have @JvmField.
     *
     * If it can't be annotated with @JvmField, it should use @JvmStatic for its accessors instead.
     */
    private fun PropertyItem.canHaveJvmField(): Boolean {
        val companionContainer = containingClass().containingClass()
        return !modifiers.isConst() &&
            setter == null &&
            // @JvmField can only be used on interface companion properties in limited situations --
            // all the companion properties must be public and constant, so adding more properties
            // might mean @JvmField would no longer be allowed even if it was originally. Because of
            // this, don't suggest using @JvmField for interface companion properties.
            // https://github.com/Kotlin/KEEP/blob/master/proposals/jvm-field-annotation-in-interface-companion.md
            containingClass().containingClass()?.isInterface() != true &&
            // @JvmField can only be used when the property has a backing field. The backing
            // field is present on the containing class of the companion.
            companionContainer?.findField(name()) != null &&
            // The compiler does not allow @JvmField on value class type properties.
            !type().isValueClassType
    }

    private fun ensureFieldNameNotKeyword(field: FieldItem) {
        checkKotlinKeyword(field.name(), "field", field)
    }

    private fun ensureMethodNameNotKeyword(method: MethodItem) {
        checkKotlinKeyword(method.name(), "method", method)
    }

    private fun ensureDefaultParamsHaveJvmOverloads(method: MethodItem) {
        if (!method.isKotlin()) {
            // Rule does not apply for Java, e.g. if you specify @DefaultValue
            // in Java you still don't have the option of adding @JvmOverloads
            return
        }
        if (method.containingClass().isInterface()) {
            // '@JvmOverloads' annotation cannot be used on interface methods
            // (https://github.com/JetBrains/kotlin/blob/dc7b1fbff946d1476cc9652710df85f65664baee/compiler/frontend.java/src/org/jetbrains/kotlin/resolve/jvm/diagnostics/DefaultErrorMessagesJvm.java#L50)
            return
        }
        val parameters = method.parameters()
        if (parameters.isEmpty()) {
            // No need for overloads when there is at most one version...
            return
        }

        if (method.containingClass().modifiers.isData() && method.name() == "copy") {
            // The generated copy method for a data class cannot be annotated. It is possible this
            // also skips warning for a copy method defined in source for a data class.
            return
        }

        var haveDefault = false
        for (parameter in parameters) {
            if (parameter.hasDefaultValue()) {
                haveDefault = true
                break
            }
        }

        if (
            haveDefault &&
                method.modifiers.findAnnotation("kotlin.jvm.JvmOverloads") == null &&
                // Extension methods and inline functions aren't really useful from Java anyway
                !method.isExtensionMethod() &&
                !method.modifiers.isInline() &&
                // Suspend methods are also difficult to use from Java
                !method.modifiers.isSuspend() &&
                // Methods marked @JvmSynthetic are hidden from java, overloads not useful
                !method.modifiers.hasJvmSyntheticAnnotation()
        ) {
            reporter.report(
                Issues.MISSING_JVMSTATIC,
                method,
                "A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults"
            )
        }
    }

    private fun ensureParameterNamesNotKeywords(method: MethodItem) {
        val parameters = method.parameters()

        if (parameters.isNotEmpty() && method.isJava()) {
            // Public java parameter names should also not use Kotlin keywords as names
            for (parameter in parameters) {
                val publicName = parameter.publicName() ?: continue
                checkKotlinKeyword(publicName, "parameter", parameter)
            }
        }
    }

    // Don't use Kotlin hard keywords in Java signatures
    private fun checkKotlinKeyword(name: String, typeLabel: String, item: Item) {
        if (isKotlinHardKeyword(name)) {
            reporter.report(
                Issues.KOTLIN_KEYWORD,
                item,
                "Avoid $typeLabel names that are Kotlin hard keywords (\"$name\"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords"
            )
        } else if (isReservedJavaKeyword(name)) {
            reporter.report(
                Issues.KOTLIN_KEYWORD,
                item,
                "Avoid $typeLabel names that are Java keywords (\"$name\"); this makes it harder to use the API from Java"
            )
        }
    }

    /** @return whether [parameter] can be invoked by Kotlin callers using SAM conversion. */
    private fun isSamCompatible(parameter: ParameterItem): Boolean {
        val type = parameter.type()
        when (type) {
            // Handle class types and variable types (check their lower bound).
            is ClassOrVariableTypeItem -> {
                // Some interfaces, while they have a single method are not considered to be SAM
                // that we want to be the last argument because often it leads to unexpected
                // behavior of the trailing lambda.
                when (type.asErasedType().qualifiedName) {
                    "java.util.concurrent.Executor",
                    "java.lang.Iterable" -> return false
                }
            }
        }

        return type.isSamCompatibleOrKotlinLambda(parameter.codebase)
    }

    private fun disallowValueClasses(cls: ClassItem) {
        if (cls.modifiers.isValue()) {
            reporter.report(
                Issues.VALUE_CLASS_DEFINITION,
                cls,
                "Value classes should not be public in APIs targeting Java clients."
            )
        }
    }

    /**
     * If a file facade class has any members which can be used from Java source, it should use
     * JvmName.
     */
    private fun requireJvmNameForFacadeClass(
        cls: ClassItem,
        filteredMembers: Sequence<MemberItem>,
    ) {
        if (
            cls.isFileFacade &&
                // Technically it is possible to use JvmMultifileClass without using JvmName, but it
                // wouldn't make sense to and it is difficult to find the annotations in psi in this
                // case, so skip the check for multi-file classes.
                !cls.isMultiFileClass &&
                !cls.modifiers.hasAnnotation { it.qualifiedName == JVM_NAME } &&
                filteredMembers.any {
                    // Check that there are no members that can be used from Java. While it is
                    // technically possible to call suspend functions from Java, they generally
                    // aren't intended for Java use so skip them for the check.
                    TargetLanguage.JAVA in it.targetLanguages && !it.modifiers.isSuspend()
                }
        ) {
            reporter.report(
                Issues.FACADE_CLASS_JVM_NAME,
                cls,
                "Use `@file:JvmName` to provide a name for this file facade class for Java callers"
            )
        }
    }

    private fun disallowValueClassUsageWithoutJvmName(property: PropertyItem) {
        fun missingJvmName(accessor: MethodItem?): Boolean {
            return accessor == null ||
                (accessor.targetLanguages == TargetLanguageSet.BYTECODE_ONLY &&
                    !accessor.effectivelyDeprecated)
        }

        val description =
            if (property.type().isValueClassType) {
                "type"
            } else if (property.receiver?.isValueClassType == true) {
                "receiver type"
            } else {
                return
            }
        if (missingJvmName(property.getter)) {
            reporter.report(
                Issues.VALUE_CLASS_USAGE_WITHOUT_JVM_NAME,
                property,
                "Property ${property.name()} with value class $description should use `@get:JvmName` to have a usable getter for Java clients"
            )
        }
        val hasVisibleSetter =
            property.setterVisibility?.let { it > VisibilityLevel.INTERNAL } ?: false
        if (hasVisibleSetter && missingJvmName(property.setter)) {
            reporter.report(
                Issues.VALUE_CLASS_USAGE_WITHOUT_JVM_NAME,
                property,
                "Property ${property.name()} with value class $description should use `@set:JvmName` to have a usable setter for Java clients"
            )
        }
    }

    private fun disallowValueClassUsageWithoutJvmName(method: MethodItem) {
        if (TargetLanguage.KOTLIN !in method.targetLanguages) return
        if (method.modifiers.hasAnnotation { it.qualifiedName == JVM_NAME }) return

        if (method.returnType().isValueClassType) {
            reporter.report(
                Issues.VALUE_CLASS_USAGE_WITHOUT_JVM_NAME,
                method,
                "Method ${method.name()} returning value class type should use JvmName to be usable for Java clients"
            )
            // Don't need to also check parameters if the issue is already reported on the method.
            return
        }

        for (parameter in method.parameters()) {
            if (parameter.type().isValueClassType) {
                reporter.report(
                    Issues.VALUE_CLASS_USAGE_WITHOUT_JVM_NAME,
                    method,
                    "Method ${method.name()} with parameter ${parameter.name()} of value class type should use JvmName to be usable for Java clients"
                )
                // Don't need to continue checking parameters if the issue is already reported on
                // the method.
                break
            }
        }
    }

    private fun disallowValueClassUsageInConstructorParameters(constructor: ConstructorItem) {
        if (TargetLanguage.KOTLIN !in constructor.targetLanguages) return
        for (parameter in constructor.parameters()) {
            if (parameter.type().isValueClassType) {
                reporter.report(
                    Issues.VALUE_CLASS_USAGE_FROM_CONSTRUCTOR,
                    constructor,
                    "Constructor of class ${constructor.name()} has parameter ${parameter.name()} of value class type which makes it unusable for Java clients"
                )
                // Don't need to continue checking parameters if the issue is already reported on
                // the constructor.
                break
            }
        }
    }

    private fun isKotlinHardKeyword(keyword: String): Boolean {
        // From
        // https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
        when (keyword) {
            "as",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "interface",
            "is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while" -> return true
        }

        return false
    }

    /** Returns true if the given string is a reserved Java keyword */
    private fun isReservedJavaKeyword(keyword: String): Boolean {
        return JavaKeywords.isReservedJavaKeyword(keyword)
    }
}
