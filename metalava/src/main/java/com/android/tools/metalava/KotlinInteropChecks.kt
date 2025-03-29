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
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JVM_STATIC
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.psi.PsiEnvironmentManager
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.intellij.psi.util.PsiUtil

// Enforces the interoperability guidelines outlined in
//   https://android.github.io/kotlin-guides/interop.html
//
// Also potentially makes other API suggestions.
class KotlinInteropChecks(val reporter: Reporter) {

    @Suppress("DEPRECATION")
    private val javaLanguageLevel =
        PsiEnvironmentManager.javaLanguageLevelFromString(options.javaLanguageLevelAsString)

    fun checkField(field: FieldItem, isKotlin: Boolean = field.isKotlin()) {
        ensureFieldNameNotKeyword(field)
    }

    fun checkMethod(method: MethodItem, isKotlin: Boolean = method.isKotlin()) {
        if (isKotlin) {
            ensureDefaultParamsHaveJvmOverloads(method)
            ensureCompanionJvmStatic(method)
            ensureExceptionsDocumented(method)
        } else {
            ensureMethodNameNotKeyword(method)
            ensureParameterNamesNotKeywords(method)
            ensureLambdaLastParameter(method)
        }
    }

    fun checkClass(cls: ClassItem, isKotlin: Boolean = cls.isKotlin()) {
        if (isKotlin) {
            disallowValueClasses(cls)
        }
    }

    fun checkProperty(property: PropertyItem) {
        ensureCompanionJvmField(property)
    }

    private fun ensureExceptionsDocumented(method: MethodItem) {
        if (!method.isKotlin()) {
            return
        }

        val exceptions = method.body.findThrownExceptions()
        if (exceptions.isEmpty()) {
            return
        }
        val doc =
            method.documentation.text.ifEmpty { method.property?.documentation?.text.orEmpty() }
        for (exception in exceptions.sortedBy { it.qualifiedName() }) {
            val checked =
                !(exception.extends("java.lang.RuntimeException") ||
                    exception.extends("java.lang.Error"))
            if (checked) {
                val annotation = method.modifiers.findAnnotation("kotlin.jvm.Throws")
                if (annotation != null) {
                    // There can be multiple values
                    for (attribute in annotation.attributes) {
                        for (v in attribute.leafValues()) {
                            val source = v.toSource()
                            if (source.endsWith(exception.simpleName() + "::class")) {
                                return
                            }
                        }
                    }
                }
                reporter.report(
                    Issues.DOCUMENT_EXCEPTIONS,
                    method,
                    "Method ${method.containingClass().simpleName()}.${method.name()} appears to be throwing ${exception.qualifiedName()}; this should be recorded with a @Throws annotation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions"
                )
            } else {
                if (!doc.contains(exception.simpleName())) {
                    reporter.report(
                        Issues.DOCUMENT_EXCEPTIONS,
                        method,
                        "Method ${method.containingClass().simpleName()}.${method.name()} appears to be throwing ${exception.qualifiedName()}; this should be listed in the documentation; see https://android.github.io/kotlin-guides/interop.html#document-exceptions"
                    )
                }
            }
        }
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

    private fun ensureCompanionJvmStatic(method: MethodItem) {
        if (
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
     * Warn if companion constants are not marked with @JvmField.
     *
     * Properties that we can expect to be constant (that is, declared via `val`, so they don't have
     * a setter) but that aren't declared 'const' in a companion object should have @JvmField, and
     * not have @JvmStatic.
     *
     * See https://developer.android.com/kotlin/interop#companion_constants
     */
    private fun ensureCompanionJvmField(property: PropertyItem) {
        if (property.containingClass().modifiers.isCompanion() && property.canHaveJvmField()) {
            if (property.modifiers.findAnnotation(JVM_STATIC) != null) {
                reporter.report(
                    Issues.MISSING_JVMSTATIC,
                    property,
                    "Companion object constants like ${property.name()} should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants"
                )
            } else if (property.modifiers.findAnnotation("kotlin.jvm.JvmField") == null) {
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
            !type().isValueClassType()
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
        if (parameters.size <= 1) {
            // No need for overloads when there is at most one version...
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
        } else if (isJavaKeyword(name)) {
            reporter.report(
                Issues.KOTLIN_KEYWORD,
                item,
                "Avoid $typeLabel names that are Java keywords (\"$name\"); this makes it harder to use the API from Java"
            )
        }
    }

    /**
     * @return whether [parameter] can be invoked by Kotlin callers using SAM conversion. This does
     *   not check TextParameterItem, as there is missing metadata (such as whether the type is
     *   defined in Kotlin source or not, which can affect SAM conversion).
     */
    private fun isSamCompatible(parameter: ParameterItem): Boolean {
        val cls = parameter.type().asClass()
        // Some interfaces, while they have a single method are not considered to be SAM that we
        // want to be the last argument because often it leads to unexpected behavior of the
        // trailing lambda.
        when (cls?.qualifiedName()) {
            "java.util.concurrent.Executor",
            "java.lang.Iterable" -> return false
        }

        return parameter.isSamCompatibleOrKotlinLambda()
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
    private fun isJavaKeyword(keyword: String): Boolean {
        return PsiUtil.isKeyword(keyword, javaLanguageLevel)
    }
}
