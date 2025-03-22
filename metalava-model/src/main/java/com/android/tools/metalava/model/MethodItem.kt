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

package com.android.tools.metalava.model

@MetalavaApi
interface MethodItem : CallableItem, InheritableItem {
    /**
     * The property this method is an accessor for; inverse of [PropertyItem.getter] and
     * [PropertyItem.setter]
     */
    val property: PropertyItem?
        get() = null

    override val effectivelyDeprecated: Boolean
        get() =
            originallyDeprecated ||
                containingClass().effectivelyDeprecated ||
                // Accessors inherit deprecation from their properties. Uses originallyDeprecated to
                // prevent a cycle because effectivelyDeprecated on the property checks the getter.
                // Also prevents deprecation from propagating getter -> property -> setter.
                property?.originallyDeprecated == true

    @Deprecated(
        message =
            "There is no point in calling this method on MethodItem as it always returns false",
        ReplaceWith("")
    )
    override fun isConstructor() = false

    /** Returns true if this method is a Kotlin extension method */
    fun isExtensionMethod(): Boolean

    /** Returns the super methods that this method is overriding */
    fun superMethods(): List<MethodItem>

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ): MethodItem? {
        val correspondingClassItem = containingClass().findCorrespondingItemIn(codebase)
        val correspondingMethodItem =
            correspondingClassItem?.findMethod(
                this,
                includeSuperClasses = superMethods,
                includeInterfaces = superMethods,
            )
        return if (
            correspondingMethodItem != null &&
                duplicate &&
                correspondingMethodItem.containingClass() !== correspondingClassItem
        )
            correspondingMethodItem.duplicate(correspondingClassItem)
        else correspondingMethodItem
    }

    fun allSuperMethods(): Sequence<MethodItem> {
        val original = superMethods().firstOrNull() ?: return emptySequence()
        return generateSequence(original) { item ->
            val superMethods = item.superMethods()
            superMethods.firstOrNull()
        }
    }

    /**
     * If this method requires override in the child class to prevent error when compiling the stubs
     */
    @Deprecated("This property should not be accessed directly.") var _requiresOverride: Boolean?

    /**
     * Duplicates this method item.
     *
     * Override to specialize the return type.
     */
    override fun duplicate(targetContainingClass: ClassItem): MethodItem

    fun findPredicateSuperMethod(predicate: FilterPredicate): MethodItem? {
        val superMethods = superMethods()
        for (method in superMethods) {
            if (predicate.test(method)) {
                return method
            }
        }

        for (method in superMethods) {
            val found = method.findPredicateSuperMethod(predicate)
            if (found != null) {
                return found
            }
        }

        return null
    }

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    companion object {
        /**
         * Compare two types to see if they are considered the same.
         *
         * Same means, functionally equivalent at both compile time and runtime.
         *
         * TODO: Compare annotations to see for example whether you've refined the nullness policy;
         *   if so, that should be included
         */
        private fun sameType(
            t1: TypeItem,
            t2: TypeItem,
            addAdditionalOverrides: Boolean,
        ): Boolean {
            // Compare the types in two ways.
            // 1. Using `TypeItem.equals(TypeItem)` which is basically a textual comparison that
            //    ignores type parameter bounds but includes everuthing else that is present in the
            //    string representation of the type apart from white space differences. This is
            //    needed to preserve methods that change annotations, e.g. adding `@NonNull`, which
            //    are significant to the API, and also to preserver legacy behavior to reduce churn
            //    in API signature files.
            // 2. Comparing their erased types which takes into account type parameter bounds but
            //    ignores annotations and generic types. Comparing erased types will retain more
            //    methods overrides in the signature file so only do it when adding additional
            //    overrides.
            return t1 == t2 &&
                (!addAdditionalOverrides || t1.toErasedTypeString() == t2.toErasedTypeString())
        }

        fun sameSignature(
            method: MethodItem,
            superMethod: MethodItem,
            addAdditionalOverrides: Boolean,
        ): Boolean {
            // If the return types differ, override it (e.g. parent implements clone(),
            // subclass overrides with more specific return type)
            if (
                !sameType(
                    method.returnType(),
                    superMethod.returnType(),
                    addAdditionalOverrides = addAdditionalOverrides
                )
            ) {
                return false
            }

            if (
                method.effectivelyDeprecated != superMethod.effectivelyDeprecated &&
                    !method.effectivelyDeprecated
            ) {
                return false
            }

            // Compare modifier lists; note that here we need to
            // skip modifiers that don't apply in compat mode if set
            if (!method.modifiers.equivalentTo(method, superMethod.modifiers)) {
                return false
            }

            val parameterList1 = method.parameters()
            val parameterList2 = superMethod.parameters()

            if (parameterList1.size != parameterList2.size) {
                return false
            }

            assert(parameterList1.size == parameterList2.size)
            for (i in parameterList1.indices) {
                val p1 = parameterList1[i]
                val p2 = parameterList2[i]
                val pt1 = p1.type()
                val pt2 = p2.type()

                if (!sameType(pt1, pt2, addAdditionalOverrides)) {
                    return false
                }
            }

            // Also compare throws lists
            val throwsList12 = method.throwsTypes()
            val throwsList2 = superMethod.throwsTypes()

            if (throwsList12.size != throwsList2.size) {
                return false
            }

            assert(throwsList12.size == throwsList2.size)
            for (i in throwsList12.indices) {
                val p1 = throwsList12[i]
                val p2 = throwsList2[i]
                val pt1 = p1.toTypeString()
                val pt2 = p2.toTypeString()
                if (pt1 != pt2) { // assumes throws lists are sorted!
                    return false
                }
            }

            return true
        }
    }

    /**
     * Check whether this method is a synthetic enum method.
     *
     * i.e. `getEntries()` from Kotlin and `values()` and `valueOf(String)` from both Java and
     * Kotlin.
     */
    fun isEnumSyntheticMethod(): Boolean {
        if (!containingClass().isEnum()) return false
        val parameters = parameters()
        return when (parameters.size) {
            0 -> name().let { name -> name == JAVA_ENUM_VALUES || name == "getEntries" }
            1 -> name() == JAVA_ENUM_VALUE_OF && parameters[0].type().isString()
            else -> false
        }
    }

    /**
     * If annotation method, returns the legacy default value as a source expression.
     *
     * This is called `legacy` because this an old, inconsistent representation of the default value
     * that exposes implementation details. It will be replaced by a properly modelled value
     * representation.
     */
    fun legacyDefaultValue(): String

    /** Whether this method is a getter/setter for an underlying Kotlin property (val/var) */
    fun isKotlinProperty(): Boolean = false

    /**
     * Determines if the method is a method that needs to be overridden in any child classes that
     * extend this [MethodItem] in order to prevent errors when compiling the stubs or the reverse
     * dependencies of stubs.
     *
     * @return Boolean value indicating whether the method needs to be overridden in the child
     *   classes
     */
    @Suppress("DEPRECATION")
    private fun requiresOverride(): Boolean {
        _requiresOverride?.let {
            return _requiresOverride as Boolean
        }

        _requiresOverride = computeRequiresOverride()

        return _requiresOverride as Boolean
    }

    private fun computeRequiresOverride(): Boolean {
        val isVisible = !hidden || hasShowAnnotation()

        // When the method is a concrete, non-default method, its overriding method is not required
        // to be shown in the signature file.
        return if (!modifiers.isAbstract() && !modifiers.isDefault()) {
            false
        } else if (superMethods().isEmpty()) {
            // If the method is abstract and is not overriding any parent methods,
            // it requires override in the child class if it is visible
            isVisible
        } else {
            // If the method is abstract and is overriding any visible parent methods:
            // it needs to be overridden if:
            // - it is visible or
            // - all super methods are either java.lang.Object method or requires override
            isVisible ||
                superMethods().all {
                    it.containingClass().isJavaLangObject() || it.requiresOverride()
                }
        }
    }

    private fun getUniqueSuperInterfaceMethods(
        superInterfaceMethods: List<MethodItem>
    ): List<MethodItem> {
        val visitCountMap = mutableMapOf<ClassItem, Int>()

        // perform BFS on all super interfaces of each super interface methods'
        // containing interface to determine the leaf interface of each unique hierarchy.
        superInterfaceMethods.forEach {
            val superInterface = it.containingClass()
            val queue = mutableListOf(superInterface)
            while (queue.isNotEmpty()) {
                val s = queue.removeFirst()
                visitCountMap[s] = visitCountMap.getOrDefault(s, 0) + 1
                queue.addAll(
                    s.interfaceTypes().mapNotNull { interfaceType -> interfaceType.asClass() }
                )
            }
        }

        // If visit count is greater than 1, it means the interface is within the hierarchy of
        // another method, thus filter out.
        return superInterfaceMethods.filter { visitCountMap[it.containingClass()]!! == 1 }
    }

    /**
     * Determines if the method needs to be added to the signature file in order to prevent errors
     * when compiling the stubs or the reverse dependencies of the stubs.
     *
     * @return Boolean value indicating whether the method needs to be added to the signature file
     */
    fun isRequiredOverridingMethodForTextStub(): Boolean {
        return (containingClass().isClass() &&
            !modifiers.isAbstract() &&
            superMethods().isNotEmpty() &&
            superMethods().let {
                if (it.size == 1 && it.first().containingClass().isJavaLangObject()) {
                    // If the method is extending a java.lang.Object method,
                    // it only required override when it is directly (not transitively) overriding
                    // it and the signature differs (e.g. visibility or modifier
                    // changes)
                    !sameSignature(
                        this,
                        it.first(),
                        // This method is only called when add-additional-overrides=yes.
                        addAdditionalOverrides = true,
                    )
                } else {
                    // Since a class can extend a single class except Object,
                    // there is only one non-Object super class method at max.
                    val superClassMethods =
                        it.firstOrNull { superMethod ->
                            superMethod.containingClass().isClass() &&
                                !superMethod.containingClass().isJavaLangObject()
                        }

                    // Assume a class implements two interfaces A and B;
                    // A provides a default super method, and B provides an abstract super method.
                    // In such case, the child method is a required overriding method when:
                    // - A and B do not extend each other or
                    // - A is a super interface of B
                    // On the other hand, the child method is not a required overriding method when:
                    // - B is a super interface of A
                    // Given this, we should make decisions only based on the leaf interface of each
                    // unique hierarchy.
                    val uniqueSuperInterfaceMethods =
                        getUniqueSuperInterfaceMethods(
                            it.filter { superMethod -> superMethod.containingClass().isInterface() }
                        )

                    // If super method is non-null, whether this method is required
                    // is determined by whether the super method requires override.
                    // If super method is null, this method is required if there is a
                    // unique super interface that requires override.
                    superClassMethods?.requiresOverride()
                        ?: uniqueSuperInterfaceMethods.any { s -> s.requiresOverride() }
                }
            }) ||
            // To inherit methods with override-equivalent signatures
            // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.4.1.3
            (containingClass().isInterface() &&
                superMethods().count { it.modifiers.isAbstract() || it.modifiers.isDefault() } > 1)
    }
}
