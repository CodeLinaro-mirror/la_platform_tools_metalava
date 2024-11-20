/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.JVM_DEFAULT_WITH_COMPATIBILITY
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.ANDROID_TEST_API
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.Item.Companion.describe
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MultipleTypeVisitor
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.options
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import com.intellij.psi.PsiField

/**
 * Compares the current API with a previous version and makes sure the changes are compatible. For
 * example, you can make a previously nullable parameter non null, but not vice versa.
 */
class CompatibilityCheck(
    val filterReference: FilterPredicate,
    private val apiType: ApiType,
    private val reporter: Reporter,
    private val issueConfiguration: IssueConfiguration,
    private val apiCompatAnnotations: Set<String>,
) : ComparisonVisitor() {

    var foundProblems = false

    private fun possibleContainingMethod(item: Item): MethodItem? {
        if (item is MethodItem) {
            return item
        }
        if (item is ParameterItem) {
            return item.possibleContainingMethod()
        }
        return null
    }

    private fun compareItemNullability(old: Item, new: Item) {
        val oldMethod = possibleContainingMethod(old)
        val newMethod = possibleContainingMethod(new)

        if (oldMethod != null && newMethod != null) {
            if (
                oldMethod.containingClass().qualifiedName() !=
                    newMethod.containingClass().qualifiedName() ||
                    (oldMethod.inheritedFromAncestor != newMethod.inheritedFromAncestor)
            ) {
                // If the old method and new method are defined on different classes, then it's
                // possible that the old method was previously overridden and we omitted it.
                // So, if the old method and new methods are defined on different classes, then we
                // skip nullability checks
                return
            }
        }

        // In a final method, you can change a parameter from nonnull to nullable.
        // This will also allow a constructor parameter to be changed from nonnull to nullable if
        // the class is not extensible.
        // TODO: Allow the parameter of any constructor to be switched from nonnull to nullable as
        //  they can never be overridden.
        val allowNonNullToNullable =
            new is ParameterItem && !new.containingCallable().canBeExternallyOverridden()
        // In a final method, you can change a method return from nullable to nonnull
        val allowNullableToNonNull = new is MethodItem && !new.canBeExternallyOverridden()

        old.type()
            ?.accept(
                object : MultipleTypeVisitor() {
                    override fun visitType(type: TypeItem, other: List<TypeItem>) {
                        val newType = other.singleOrNull() ?: return
                        compareTypeNullability(
                            type,
                            newType,
                            new,
                            allowNonNullToNullable,
                            allowNullableToNonNull,
                        )
                    }
                },
                listOfNotNull(new.type())
            )
    }

    private fun compareTypeNullability(
        old: TypeItem,
        new: TypeItem,
        context: Item,
        allowNonNullToNullable: Boolean,
        allowNullableToNonNull: Boolean,
    ) {
        // Should not remove nullness information
        // Can't change information incompatibly
        val oldNullability = old.modifiers.nullability
        val newNullability = new.modifiers.nullability
        if (
            (oldNullability == TypeNullability.NONNULL ||
                oldNullability == TypeNullability.NULLABLE) &&
                newNullability == TypeNullability.PLATFORM
        ) {
            report(
                Issues.INVALID_NULL_CONVERSION,
                context,
                "Attempted to remove nullability from ${new.toTypeString()} (was $oldNullability) in ${describe(context)}"
            )
        } else if (oldNullability != newNullability) {
            if (
                (oldNullability == TypeNullability.NULLABLE &&
                    newNullability == TypeNullability.NONNULL &&
                    !allowNullableToNonNull) ||
                    (oldNullability == TypeNullability.NONNULL &&
                        newNullability == TypeNullability.NULLABLE &&
                        !allowNonNullToNullable)
            ) {
                // This check used to be more permissive. To transition to a stronger check, use
                // WARNING_ERROR_WHEN_NEW if the change used to be allowed.
                val previouslyAllowed =
                    (oldNullability == TypeNullability.NULLABLE && context is MethodItem) ||
                        ((oldNullability == TypeNullability.NONNULL && context is ParameterItem))
                val maximumSeverity =
                    if (previouslyAllowed) {
                        Severity.WARNING_ERROR_WHEN_NEW
                    } else {
                        Severity.ERROR
                    }
                report(
                    Issues.INVALID_NULL_CONVERSION,
                    context,
                    "Attempted to change nullability of ${new.toTypeString()} (from $oldNullability to $newNullability) in ${describe(context)}",
                    maximumSeverity = maximumSeverity,
                )
            }
        }
    }

    override fun compareItems(old: Item, new: Item) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers
        if (oldModifiers.isOperator() && !newModifiers.isOperator()) {
            report(
                Issues.OPERATOR_REMOVAL,
                new,
                "Cannot remove `operator` modifier from ${describe(new)}: Incompatible change"
            )
        }

        if (oldModifiers.isInfix() && !newModifiers.isInfix()) {
            report(
                Issues.INFIX_REMOVAL,
                new,
                "Cannot remove `infix` modifier from ${describe(new)}: Incompatible change"
            )
        }

        if (!old.isCompatibilitySuppressed() && new.isCompatibilitySuppressed()) {
            report(
                Issues.BECAME_UNCHECKED,
                old,
                "Removed ${describe(old)} from compatibility checked API surface"
            )
        }

        apiCompatAnnotations.forEach { annotation ->
            val isOldAnnotated = oldModifiers.isAnnotatedWith(annotation)
            val newAnnotation = newModifiers.findAnnotation(annotation)
            if (isOldAnnotated && newAnnotation == null) {
                report(
                    Issues.REMOVED_ANNOTATION,
                    new,
                    "Cannot remove @$annotation annotation from ${describe(old)}: Incompatible change",
                )
            } else if (!isOldAnnotated && newAnnotation != null) {
                report(
                    Issues.ADDED_ANNOTATION,
                    new,
                    "Cannot add @$annotation annotation to ${describe(old)}: Incompatible change",
                    newAnnotation.fileLocation,
                )
            }
        }

        compareItemNullability(old, new)
    }

    override fun compareParameterItems(old: ParameterItem, new: ParameterItem) {
        val prevName = old.publicName()
        val newName = new.publicName()
        if (prevName != null) {
            if (newName == null) {
                report(
                    Issues.PARAMETER_NAME_CHANGE,
                    new,
                    "Attempted to remove parameter name from ${describe(new)}"
                )
            } else if (newName != prevName) {
                report(
                    Issues.PARAMETER_NAME_CHANGE,
                    new,
                    "Attempted to change parameter name from $prevName to $newName in ${describe(new.containingCallable())}"
                )
            }
        }

        if (old.hasDefaultValue() && !new.hasDefaultValue()) {
            report(
                Issues.DEFAULT_VALUE_CHANGE,
                new,
                "Attempted to remove default value from ${describe(new)}"
            )
        }

        if (old.isVarArgs() && !new.isVarArgs()) {
            // In Java, changing from array to varargs is a compatible change, but
            // not the other way around. Kotlin is the same, though in Kotlin
            // you have to change the parameter type as well to an array type; assuming you
            // do that it's the same situation as Java; otherwise the normal
            // signature check will catch the incompatibility.
            report(
                Issues.VARARG_REMOVAL,
                new,
                "Changing from varargs to array is an incompatible change: ${describe(
                    new,
                    includeParameterTypes = true,
                    includeParameterNames = true
                )}"
            )
        }
    }

    override fun compareClassItems(old: ClassItem, new: ClassItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        if (
            old.isInterface() != new.isInterface() ||
                old.isEnum() != new.isEnum() ||
                old.isAnnotationType() != new.isAnnotationType()
        ) {
            report(
                Issues.CHANGED_CLASS,
                new,
                "${describe(new, capitalize = true)} changed class/interface declaration"
            )
            return // Avoid further warnings like "has changed abstract qualifier" which is implicit
            // in this change
        }

        for (iface in old.interfaceTypes()) {
            val qualifiedName = iface.asClass()?.qualifiedName() ?: continue
            if (!new.implements(qualifiedName)) {
                report(
                    Issues.REMOVED_INTERFACE,
                    new,
                    "${describe(old, capitalize = true)} no longer implements $iface"
                )
            }
        }

        for (iface in new.filteredInterfaceTypes(filterReference)) {
            val qualifiedName = iface.asClass()?.qualifiedName() ?: continue
            if (!old.implements(qualifiedName)) {
                report(
                    Issues.ADDED_INTERFACE,
                    new,
                    "Added interface $iface to class ${describe(old)}"
                )
            }
        }

        if (!oldModifiers.isSealed() && newModifiers.isSealed()) {
            report(
                Issues.ADD_SEALED,
                new,
                "Cannot add 'sealed' modifier to ${describe(new)}: Incompatible change"
            )
        } else if (old.isClass() && !oldModifiers.isAbstract() && newModifiers.isAbstract()) {
            report(
                Issues.CHANGED_ABSTRACT,
                new,
                "${describe(new, capitalize = true)} changed 'abstract' qualifier"
            )
        }

        if (oldModifiers.isFunctional() && !newModifiers.isFunctional()) {
            report(
                Issues.FUN_REMOVAL,
                new,
                "Cannot remove 'fun' modifier from ${describe(new)}: source incompatible change"
            )
        }

        // Check for changes in final & static, but not in enums (since PSI and signature files
        // differ
        // a bit in whether they include these for enums
        if (!new.isEnum()) {
            if (!oldModifiers.isFinal() && newModifiers.isFinal()) {
                // It is safe to make a class final if was impossible for an application to create a
                // subclass.
                if (!old.isExtensible()) {
                    report(
                        Issues.ADDED_FINAL_UNINSTANTIABLE,
                        new,
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } added 'final' qualifier but was previously uninstantiable and therefore could not be subclassed"
                    )
                } else {
                    report(
                        Issues.ADDED_FINAL,
                        new,
                        "${describe(new, capitalize = true)} added 'final' qualifier"
                    )
                }
            }

            if (oldModifiers.isStatic() != newModifiers.isStatic()) {
                val hasPublicConstructor = old.constructors().any { it.isPublic }
                if (!old.isNestedClass() || hasPublicConstructor) {
                    report(
                        Issues.CHANGED_STATIC,
                        new,
                        "${describe(new, capitalize = true)} changed 'static' qualifier"
                    )
                }
            }
        }

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // TODO: Use newModifiers.asAccessibleAs(oldModifiers) to provide different error
            // messages
            // based on whether this seems like a reasonable change, e.g. making a private or final
            // method more
            // accessible is fine (no overridden method affected) but not making methods less
            // accessible etc
            report(
                Issues.CHANGED_SCOPE,
                new,
                "${describe(new, capitalize = true)} changed visibility from $oldVisibility to $newVisibility"
            )
        }

        if (!old.effectivelyDeprecated == new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }

        val oldSuperClassName = old.superClass()?.qualifiedName()
        if (oldSuperClassName != null) { // java.lang.Object can't have a superclass.
            if (!new.extends(oldSuperClassName)) {
                report(
                    Issues.CHANGED_SUPERCLASS,
                    new,
                    "${describe(
                        new,
                        capitalize = true
                    )} superclass changed from $oldSuperClassName to ${new.superClass()?.qualifiedName()}"
                )
            }
        }

        if (old.hasTypeVariables() || new.hasTypeVariables()) {
            val oldTypeParamsCount = old.typeParameterList.size
            val newTypeParamsCount = new.typeParameterList.size
            if (oldTypeParamsCount > 0 && oldTypeParamsCount != newTypeParamsCount) {
                report(
                    Issues.CHANGED_TYPE,
                    new,
                    "${
                        describe(
                            old,
                            capitalize = true
                        )
                    } changed number of type parameters from $oldTypeParamsCount to $newTypeParamsCount"
                )
            }
        }

        if (
            old.modifiers.isAnnotatedWith(JVM_DEFAULT_WITH_COMPATIBILITY) &&
                !new.modifiers.isAnnotatedWith(JVM_DEFAULT_WITH_COMPATIBILITY)
        ) {
            report(
                Issues.REMOVED_JVM_DEFAULT_WITH_COMPATIBILITY,
                new,
                "Cannot remove @$JVM_DEFAULT_WITH_COMPATIBILITY annotation from " +
                    "${describe(new)}: Incompatible change"
            )
        }
    }

    /**
     * Check if the return types are compatible, which is true when:
     * - they're equal
     * - both are arrays, and the component types are compatible
     * - both are variable types, and they have equal bounds
     * - the new return type is a variable and has the old return type in its bounds
     *
     * TODO(b/111253910): could this also allow changes like List<T> to List<A> where A and T have
     *   equal bounds?
     */
    private fun compatibleReturnTypes(old: TypeItem, new: TypeItem): Boolean {
        when (new) {
            is ArrayTypeItem ->
                return old is ArrayTypeItem &&
                    compatibleReturnTypes(old.componentType, new.componentType)
            is VariableTypeItem -> {
                if (old is VariableTypeItem) {
                    // If both return types are parameterized then the constraints must be
                    // exactly the same.
                    return old.asTypeParameter.typeBounds() == new.asTypeParameter.typeBounds()
                } else {
                    // If the old return type was not parameterized but the new return type is,
                    // the new type parameter must have the old return type in its bounds
                    // (e.g. changing return type from `String` to `T extends String` is valid).
                    val constraints = new.asTypeParameter.typeBounds()
                    val oldClass = old.asClass()
                    for (constraint in constraints) {
                        val newClass = constraint.asClass()
                        if (
                            oldClass == null ||
                                newClass == null ||
                                !oldClass.extendsOrImplements(newClass.qualifiedName())
                        ) {
                            return false
                        }
                    }
                    return true
                }
            }
            else -> return old == new
        }
    }

    override fun compareCallableItems(old: CallableItem, new: CallableItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // Only report issue if the change is a decrease in access; e.g. public -> protected
            if (!newModifiers.asAccessibleAs(oldModifiers)) {
                report(
                    Issues.CHANGED_SCOPE,
                    new,
                    "${describe(new, capitalize = true)} changed visibility from $oldVisibility to $newVisibility"
                )
            }
        }

        if (old.effectivelyDeprecated != new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }

        for (throwType in old.throwsTypes()) {
            // Get the throwable class, if none could be found then it is either because there is an
            // error in the codebase or the codebase is incomplete, either way reporting an error
            // would be unhelpful.
            val throwableClass = throwType.erasedClass ?: continue
            if (!new.throws(throwableClass.qualifiedName())) {
                // exclude 'throws' changes to finalize() overrides with no arguments
                if (old.name() != "finalize" || old.parameters().isNotEmpty()) {
                    report(
                        Issues.CHANGED_THROWS,
                        new,
                        "${describe(new, capitalize = true)} no longer throws exception ${throwType.description()}"
                    )
                }
            }
        }

        for (throwType in new.filteredThrowsTypes(filterReference)) {
            // Get the throwable class, if none could be found then it is either because there is an
            // error in the codebase or the codebase is incomplete, either way reporting an error
            // would be unhelpful.
            val throwableClass = throwType.erasedClass ?: continue
            if (!old.throws(throwableClass.qualifiedName())) {
                // exclude 'throws' changes to finalize() overrides with no arguments
                if (!(old.name() == "finalize" && old.parameters().isEmpty())) {
                    val message =
                        "${describe(new, capitalize = true)} added thrown exception ${throwType.description()}"
                    report(Issues.CHANGED_THROWS, new, message)
                }
            }
        }
    }

    override fun compareMethodItems(old: MethodItem, new: MethodItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        val oldReturnType = old.returnType()
        val newReturnType = new.returnType()

        if (!compatibleReturnTypes(oldReturnType, newReturnType)) {
            // For incompatible type variable changes, include the type bounds in the string.
            val oldTypeString = describeBounds(oldReturnType)
            val newTypeString = describeBounds(newReturnType)
            val message =
                "${describe(new, capitalize = true)} has changed return type from $oldTypeString to $newTypeString"
            report(Issues.CHANGED_TYPE, new, message)
        }

        // Annotation methods
        if (
            new.containingClass().isAnnotationType() &&
                old.containingClass().isAnnotationType() &&
                new.defaultValue() != old.defaultValue()
        ) {
            val prevValue = old.defaultValue()
            val prevString =
                if (prevValue.isEmpty()) {
                    "nothing"
                } else {
                    prevValue
                }

            val newValue = new.defaultValue()
            val newString =
                if (newValue.isEmpty()) {
                    "nothing"
                } else {
                    newValue
                }
            val message =
                "${describe(
                new,
                capitalize = true
            )} has changed value from $prevString to $newString"

            // Adding a default value to an annotation method is safe
            val annotationMethodAddingDefaultValue =
                new.containingClass().isAnnotationType() && old.defaultValue().isEmpty()

            if (!annotationMethodAddingDefaultValue) {
                report(Issues.CHANGED_VALUE, new, message)
            }
        }

        // Check for changes in abstract, but only for regular classes; older signature files
        // sometimes describe interface methods as abstract
        if (new.containingClass().isClass()) {
            if (!oldModifiers.isAbstract() && newModifiers.isAbstract()) {
                report(
                    Issues.CHANGED_ABSTRACT,
                    new,
                    "${describe(new, capitalize = true)} has changed 'abstract' qualifier"
                )
            }
        }

        if (new.containingClass().isInterface() || new.containingClass().isAnnotationType()) {
            if (oldModifiers.isDefault() && newModifiers.isAbstract()) {
                report(
                    Issues.CHANGED_DEFAULT,
                    new,
                    "${describe(new, capitalize = true)} has changed 'default' qualifier"
                )
            }
        }

        if (oldModifiers.isNative() != newModifiers.isNative()) {
            report(
                Issues.CHANGED_NATIVE,
                new,
                "${describe(new, capitalize = true)} has changed 'native' qualifier"
            )
        }

        // Check changes to final modifier. But skip enums where it varies between signature files
        // and PSI
        // whether the methods are considered final.
        if (!new.containingClass().isEnum() && !oldModifiers.isStatic()) {
            // Compiler-generated methods vary in their 'final' qualifier between versions of
            // the compiler, so this check needs to be quite narrow. A change in 'final'
            // status of a method is only relevant if (a) the method is not declared 'static'
            // and (b) the method is not already inferred to be 'final' by virtue of its class.
            if (!old.isEffectivelyFinal() && new.isEffectivelyFinal()) {
                if (!old.containingClass().isExtensible()) {
                    report(
                        Issues.ADDED_FINAL_UNINSTANTIABLE,
                        new,
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } added 'final' qualifier but containing ${old.containingClass().describe()} was previously uninstantiable and therefore could not be subclassed"
                    )
                } else {
                    report(
                        Issues.ADDED_FINAL,
                        new,
                        "${describe(new, capitalize = true)} has added 'final' qualifier"
                    )
                }
            } else if (old.isEffectivelyFinal() && !new.isEffectivelyFinal()) {
                // Disallowed removing final: If an app inherits the class and starts overriding
                // the method it's going to crash on earlier versions where the method is final
                // It doesn't break compatibility in the strict sense, but does make it very
                // difficult to extend this method in practice.
                report(
                    Issues.REMOVED_FINAL_STRICT,
                    new,
                    "${describe(new, capitalize = true)} has removed 'final' qualifier"
                )
            }
        }

        if (oldModifiers.isStatic() != newModifiers.isStatic()) {
            report(
                Issues.CHANGED_STATIC,
                new,
                "${describe(new, capitalize = true)} has changed 'static' qualifier"
            )
        }

        if (new.modifiers.isInline()) {
            val oldTypes = old.typeParameterList
            val newTypes = new.typeParameterList
            for (i in oldTypes.indices) {
                if (i == newTypes.size) {
                    break
                }
                if (newTypes[i].isReified() && !oldTypes[i].isReified()) {
                    val message =
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } made type variable ${newTypes[i].name()} reified: incompatible change"
                    report(Issues.ADDED_REIFIED, new, message)
                }
            }
        }
    }

    /**
     * Returns a string representation of the type, including the bounds for a variable type or
     * array of variable types.
     *
     * TODO(b/111253910): combine into [TypeItem.toTypeString]
     */
    private fun describeBounds(type: TypeItem): String {
        return when (type) {
            is ArrayTypeItem -> describeBounds(type.componentType) + "[]"
            is VariableTypeItem -> {
                type.name +
                    if (type.asTypeParameter.typeBounds().isEmpty()) {
                        " (extends java.lang.Object)"
                    } else {
                        " (extends ${type.asTypeParameter.typeBounds().joinToString(separator = " & ") { it.toTypeString() }})"
                    }
            }
            else -> type.toTypeString()
        }
    }

    override fun compareFieldItems(old: FieldItem, new: FieldItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        if (!old.isEnumConstant()) {
            val oldType = old.type()
            val newType = new.type()
            if (oldType != newType) {
                val message =
                    "${describe(new, capitalize = true)} has changed type from $oldType to $newType"
                report(Issues.CHANGED_TYPE, new, message)
            } else if (!old.hasSameValue(new)) {
                val prevValue = old.initialValue()
                val prevString =
                    if (prevValue == null && !old.modifiers.isFinal()) {
                        "nothing/not constant"
                    } else {
                        prevValue
                    }

                val newValue = new.initialValue()
                val newString =
                    if (newValue is PsiField) {
                        newValue.containingClass?.qualifiedName + "." + newValue.name
                    } else {
                        newValue
                    }
                val message =
                    "${describe(
                    new,
                    capitalize = true
                )} has changed value from $prevString to $newString"

                report(Issues.CHANGED_VALUE, new, message)
            }
        }

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // Only report issue if the change is a decrease in access; e.g. public -> protected
            if (!newModifiers.asAccessibleAs(oldModifiers)) {
                report(
                    Issues.CHANGED_SCOPE,
                    new,
                    "${
                    describe(
                        new,
                        capitalize = true
                    )
                    } changed visibility from $oldVisibility to $newVisibility"
                )
            }
        }

        if (oldModifiers.isStatic() != newModifiers.isStatic()) {
            report(
                Issues.CHANGED_STATIC,
                new,
                "${describe(new, capitalize = true)} has changed 'static' qualifier"
            )
        }

        if (!oldModifiers.isFinal() && newModifiers.isFinal()) {
            report(
                Issues.ADDED_FINAL,
                new,
                "${describe(new, capitalize = true)} has added 'final' qualifier"
            )
        } else if (
            // Final can't be removed if field is static with compile-time constant
            oldModifiers.isFinal() &&
                !newModifiers.isFinal() &&
                oldModifiers.isStatic() &&
                old.initialValue() != null
        ) {
            report(
                Issues.REMOVED_FINAL,
                new,
                "${describe(new, capitalize = true)} has removed 'final' qualifier"
            )
        }

        if (oldModifiers.isVolatile() != newModifiers.isVolatile()) {
            report(
                Issues.CHANGED_VOLATILE,
                new,
                "${describe(new, capitalize = true)} has changed 'volatile' qualifier"
            )
        }

        if (old.effectivelyDeprecated != new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun handleAdded(issue: Issue, item: SelectableItem) {
        if (item.originallyHidden) {
            // This is an element which is hidden but is referenced from
            // some public API. This is an error, but some existing code
            // is doing this. This is not an API addition.
            return
        }

        if (!filterReference.test(item)) {
            // This item is something we weren't asked to verify
            return
        }

        var message = "Added ${describe(item)}"

        // Clarify error message for removed API to make it less ambiguous
        if (apiType == ApiType.REMOVED) {
            message += " to the removed API"
        } else if (options.allShowAnnotations.isNotEmpty()) {
            if (options.allShowAnnotations.matchesAnnotationName(ANDROID_SYSTEM_API)) {
                message += " to the system API"
            } else if (options.allShowAnnotations.matchesAnnotationName(ANDROID_TEST_API)) {
                message += " to the test API"
            }
        }

        report(issue, item, message)
    }

    private fun handleRemoved(issue: Issue, item: SelectableItem) {
        if (!item.emit) {
            // It's a stub; this can happen when analyzing partial APIs
            // such as a signature file for a library referencing types
            // from the upstream library dependencies.
            return
        }

        report(
            issue,
            item,
            "Removed ${if (item.effectivelyDeprecated) "deprecated " else ""}${describe(item)}"
        )
    }

    override fun addedPackageItem(new: PackageItem) {
        handleAdded(Issues.ADDED_PACKAGE, new)
    }

    override fun addedClassItem(new: ClassItem) {
        val error =
            if (new.isInterface()) {
                Issues.ADDED_INTERFACE
            } else {
                Issues.ADDED_CLASS
            }
        handleAdded(error, new)
    }

    override fun addedCallableItem(new: CallableItem) {
        if (new is MethodItem) {
            // *Overriding* methods from super classes that are outside the
            // API is OK (e.g. overriding toString() from java.lang.Object)
            val superMethods = new.superMethods()
            for (superMethod in superMethods) {
                if (superMethod.origin == ClassOrigin.CLASS_PATH) {
                    return
                }
            }

            // In most cases it is not permitted to add a new method to an interface, even with a
            // default implementation because it could create ambiguity if client code implements
            // two interfaces that each now define methods with the same signature.
            // Annotation types cannot implement other interfaces, however, so it is permitted to
            // add new default methods to annotation types.
            if (new.containingClass().isAnnotationType() && new.hasDefaultValue()) {
                return
            }
        }

        // Do not fail if this "new" method is really an override of an
        // existing superclass method, but we should fail if this is overriding
        // an abstract method, because method's abstractness affects how users use it.
        // See if there's a member from inherited class
        val inherited =
            if (new is MethodItem) {
                new.containingClass()
                    .findMethod(new, includeSuperClasses = true, includeInterfaces = false)
            } else null

        // It is ok to add a new abstract method to a class that has no public constructors
        if (
            new.containingClass().isClass() &&
                !new.containingClass().constructors().any { it.isPublic && !it.hidden } &&
                new.modifiers.isAbstract()
        ) {
            return
        }

        if (inherited == null || inherited == new || !inherited.modifiers.isAbstract()) {
            val error =
                when {
                    new.modifiers.isAbstract() -> Issues.ADDED_ABSTRACT_METHOD
                    new.containingClass().isInterface() ->
                        when {
                            new.modifiers.isStatic() -> Issues.ADDED_METHOD
                            new.modifiers.isDefault() -> {
                                // Hack to always mark added Kotlin interface methods as abstract
                                // until we properly support JVM default methods for Kotlin.
                                // TODO(b/200077254): Remove Kotlin special case
                                if (new.itemLanguage == ItemLanguage.KOTLIN) {
                                    Issues.ADDED_ABSTRACT_METHOD
                                } else {
                                    Issues.ADDED_METHOD
                                }
                            }
                            else -> Issues.ADDED_ABSTRACT_METHOD
                        }
                    else -> Issues.ADDED_METHOD
                }
            handleAdded(error, new)
        }
    }

    override fun addedFieldItem(new: FieldItem) {
        handleAdded(Issues.ADDED_FIELD, new)
    }

    override fun removedPackageItem(old: PackageItem, from: PackageItem?) {
        handleRemoved(Issues.REMOVED_PACKAGE, old)
    }

    override fun removedClassItem(old: ClassItem, from: SelectableItem) {
        val error =
            when {
                old.isInterface() -> Issues.REMOVED_INTERFACE
                old.effectivelyDeprecated -> Issues.REMOVED_DEPRECATED_CLASS
                else -> Issues.REMOVED_CLASS
            }

        handleRemoved(error, old)
    }

    override fun removedCallableItem(old: CallableItem, from: ClassItem) {
        // See if there's a member from inherited class
        val inherited =
            if (old is MethodItem) {
                // This can also return self, specially handled below
                from
                    .findMethod(
                        old,
                        includeSuperClasses = true,
                        includeInterfaces = from.isInterface()
                    )
                    ?.let {
                        // If it was inherited but should still be treated as if it was removed then
                        // pretend that it was not inherited.
                        if (it.treatAsRemoved(old)) null else it
                    }
            } else null

        if (inherited == null) {
            val error =
                if (old.effectivelyDeprecated) Issues.REMOVED_DEPRECATED_METHOD
                else Issues.REMOVED_METHOD
            handleRemoved(error, old)
        }
    }

    /**
     * Check the [Item] to see whether it should be treated as if it was removed.
     *
     * If an [Item] is an unstable API that will be reverted then it will not be treated as if it
     * was removed. That is because reverting it will replace it with the old item against which it
     * is being compared in this compatibility check. So, while this specific item will not appear
     * in the API the old item will and so it has not been removed.
     *
     * Otherwise, an [Item] will be treated as it was removed it if it is hidden/removed or the
     * [possibleMatch] does not match.
     */
    private fun MethodItem.treatAsRemoved(possibleMatch: MethodItem) =
        !showability.revertUnstableApi() && (isHiddenOrRemoved() || this != possibleMatch)

    override fun removedFieldItem(old: FieldItem, from: ClassItem) {
        val inherited =
            from.findField(
                old.name(),
                includeSuperClasses = true,
                includeInterfaces = from.isInterface()
            )
        if (inherited == null) {
            val error =
                if (old.effectivelyDeprecated) Issues.REMOVED_DEPRECATED_FIELD
                else Issues.REMOVED_FIELD
            handleRemoved(error, old)
        }
    }

    private fun report(
        issue: Issue,
        item: Item,
        message: String,
        location: FileLocation = FileLocation.UNKNOWN,
        maximumSeverity: Severity = Severity.UNLIMITED,
    ) {
        if (item.isCompatibilitySuppressed()) {
            // Long-term, we should consider allowing meta-annotations to specify a different
            // `configuration` so it can use a separate set of severities. For now, though, we'll
            // treat all issues for all unchecked items as `Severity.IGNORE`.
            return
        }
        if (reporter.report(issue, item, message, location, maximumSeverity = maximumSeverity)) {
            // If the issue was reported and was an error then remember that this found some
            // problems so that the process can be aborted after finishing the checks.
            val severity = minOf(maximumSeverity, issueConfiguration.getSeverity(issue))
            if (severity == Severity.ERROR) {
                foundProblems = true
            }
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun checkCompatibility(
            newCodebase: Codebase,
            oldCodebase: Codebase,
            apiType: ApiType,
            reporter: Reporter,
            issueConfiguration: IssueConfiguration,
            apiCompatAnnotations: Set<String>,
        ) {
            val filter =
                apiType
                    .getReferenceFilter(options.apiPredicateConfig)
                    .or(apiType.getEmitFilter(options.apiPredicateConfig))
                    .or(ApiType.PUBLIC_API.getReferenceFilter(options.apiPredicateConfig))
                    .or(ApiType.PUBLIC_API.getEmitFilter(options.apiPredicateConfig))

            val checker =
                CompatibilityCheck(
                    filter,
                    apiType,
                    reporter,
                    issueConfiguration,
                    apiCompatAnnotations,
                )

            val oldFullCodebase =
                if (options.showUnannotated && apiType == ApiType.PUBLIC_API) {
                    MergedCodebase(listOf(oldCodebase))
                } else {
                    // To avoid issues with partial oldCodeBase we fill gaps with newCodebase, the
                    // first parameter is master, so we don't change values of oldCodeBase
                    MergedCodebase(listOf(oldCodebase, newCodebase))
                }
            val newFullCodebase = MergedCodebase(listOf(newCodebase))

            CodebaseComparator().compare(checker, oldFullCodebase, newFullCodebase, filter)

            val message =
                "Found compatibility problems checking " +
                    "the ${apiType.displayName} API (${newCodebase.location}) against the API in ${oldCodebase.location}"

            if (checker.foundProblems) {
                throw MetalavaCliException(exitCode = -1, stderr = message)
            }
        }
    }
}
