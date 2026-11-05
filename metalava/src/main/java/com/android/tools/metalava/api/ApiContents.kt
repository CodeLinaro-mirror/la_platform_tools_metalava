/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.api

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.EmittedOnlyPredicate
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.api.surface.ApiReferenceStrategy
import com.android.tools.metalava.model.api.surface.ReferenceCheckResult
import com.android.tools.metalava.model.api.surface.ReferenceKind
import com.android.tools.metalava.reporter.Issues

/** Determines all the [ClassItem]s that are part of the API. */
internal class ApiContents(
    private val codebase: Codebase,
) :
    BaseItemVisitor(
        // Preserve class nesting so that visiting a top-level ClassItem also visits its nested
        // classes, and skipping an outer class skips its nested classes.
        preserveClassNesting = true,

        // Parameter items are already visited by [visitCallable].
        visitParameterItems = false,
    ) {
    private val reporter = codebase.reporter

    /**
     * The [ApiReferenceStrategy] used to check whether items and referenced classes are valid code
     * references in the target API surface.
     */
    private val referenceStrategy = codebase.apiSurfaces.referenceStrategy

    /**
     * The set of [ClassItem]s that are part of the API.
     *
     * Populated by [computeTransitiveClosure].
     */
    private val notStrippable = HashSet<ClassItem>(5000)

    /**
     * The filter that determines which [SelectableItem]s are included in the API.
     *
     * This is constructed from predicates in order from quickest to slowest to avoid calling the
     * slower predicates unnecessarily.
     */
    private val filter =
        // Only consider items that are emitted in the codebase as part of the API.
        EmittedOnlyPredicate
            // Don't consider references from elements that only exist in bytecode.
            .and { selectableItem ->
                selectableItem.targetLanguages != TargetLanguageSet.BYTECODE_ONLY
            }
            // Only include items that are valid code references in the target API surface (or any
            // of its base surfaces), excluding hidden, removed, and `@DocOnly` items.
            .and { selectableItem ->
                referenceStrategy.isValidReference(null, selectableItem, ReferenceKind.CODE)
            }

    /**
     * Computes the transitive closure of the API surface.
     *
     * Starts with the set of all top level classes that are usable outside the package/module in
     * which they are defined, currently marked as emitted and not hidden. It then proceeds to find
     * any class referenced from those classes, directly or indirectly. Returning the set of all
     * classes that were visited.
     */
    private fun computeTransitiveClosure(): Set<ClassItem> {
        // Iterate over all the top level classes.
        for (classItem in codebase.getPackages().allTopLevelClasses()) {
            // Check the class reference.
            classItem.accept(this)
        }

        // Return the set of classes that were found.
        return notStrippable
    }

    /**
     * Override to ensure that when an outer class is skipped then its nested classes are not
     * visited.
     */
    override fun visit(cls: ClassItem) {
        // If a class is not public or protected, hidden, or not marked for emitting then it is not
        // part of the API and neither are its nested classes.
        if (!cls.isApiCandidate() || !cls.emit) return

        super.visit(cls)
    }

    /**
     * Check [cls]'s references to other [ClassItem]s.
     *
     * This is called both for top-level classes and nested classes.
     */
    override fun visitClass(cls: ClassItem) {
        checkClassReferences(cls, cls, "self")
        if (cls.classKind == ClassKind.TYPEALIAS) {
            checkTypeReferences(cls.aliasedType, cls, "aliased type")
        }
    }

    /** Check [cl]'s references to other [ClassItem]s. */
    private fun checkClassReferences(
        cl: ClassItem,
        from: Item,
        usage: String,
    ) {
        // Ignore any class from the class path.
        if (cl.origin == ClassOrigin.CLASS_PATH) {
            return
        }

        // Report issues before checking to see if this class has been visited before so that it
        // will report all references to the unavailable class (e.g. hidden, not public, removed, or
        // `@DocOnly`).
        val result = referenceStrategy.checkReference(from, cl, ReferenceKind.CODE)
        if (result is ReferenceCheckResult.Invalid) {
            reporter.report(
                Issues.REFERENCES_HIDDEN,
                from,
                "Class ${cl.qualifiedName()} is ${result.reasonLabel} but was referenced ($usage) from public ${from.describe()}"
            )
        }

        // Only check each class one.
        if (!notStrippable.add(cl)) {
            return
        }

        // Check the containing class.
        // This is not needed for classes checked directly from [computeTransitiveClosure] as that
        // always starts with the outermost class. This is needed for type references to nested
        // source classes that are found on the source path.
        val containingClass = cl.containingClass()
        if (containingClass != null) {
            checkClassReferences(containingClass, cl, "as containing class")
        }

        // Check this class's type parameters.
        checkTypeParameterListReferences(cl.typeParameterList, cl)

        // Check super type references.
        // TODO: Consider using val superClass = cl.filteredSuperclass(filter)
        val allSuperItems = cl.allInterfaces().toMutableSet()
        val directSuperItems = cl.interfaceTypes().map { it.qualifiedName }.toMutableSet()
        cl.superClass()?.let { superClass ->
            allSuperItems.add(superClass)
            directSuperItems.add(superClass.qualifiedName())
        }

        for (superItem in allSuperItems) {
            // allInterfaces includes cl itself if cl is an interface.
            if (superItem == cl) {
                continue
            }
            // java.lang.Object is the implicit superclass of all classes and is never unavailable.
            if (superItem.isJavaLangObject()) {
                continue
            }

            // Implicit super types of annotations and enums are never unavailable.
            val implicitSuperType =
                when (val classKind = cl.classKind) {
                    ClassKind.ANNOTATION_TYPE -> classKind.implicitInterfaceType
                    ClassKind.ENUM -> classKind.implicitSuperClassType
                    else -> null
                }
            if (superItem.qualifiedName() == implicitSuperType?.qualifiedName) {
                continue
            }

            // Check whether `superItem` is a valid code reference from `cl` in the target API
            // surface (excluding hidden, removed, or `@DocOnly` superclasses/interfaces).
            if (
                !referenceStrategy.isValidReference(cl, superItem, ReferenceKind.CODE) &&
                    (superItem.modifiers.isPublic() || superItem.modifiers.isProtected())
            ) {
                // cl is a public class declared as extending a hidden superclass or implementing
                // a hidden interface. This is not a desired practice, but it's happened, so we deal
                // with it by finding the first super class which passes checkLevel for purposes of
                // generating the doc & stub information, and proceeding normally.
                if (
                    // Make sure the parent element is either the superclass or an interface
                    // that cl is implementing directly (as opposed to indirectly via parent class)
                    superItem.qualifiedName() in directSuperItems
                ) {
                    reporter.report(
                        Issues.HIDDEN_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " stripped of unavailable superclass " +
                            superItem.qualifiedName()
                    )
                }
            } else {
                if (superItem.isPrivate && superItem.origin != ClassOrigin.CLASS_PATH) {
                    reporter.report(
                        Issues.PRIVATE_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " extends private class " +
                            superItem.qualifiedName()
                    )
                }
            }
        }
    }

    /** Check all the references from [field] to [ClassItem]s. */
    override fun visitField(field: FieldItem) {
        if (!filter.test(field)) {
            return
        }
        checkTypeReferences(field.type(), field, "in field type")
    }

    /**
     * Check all the references from [property] to [ClassItem]s when the property is not already
     * covered by a getter method or backing field (e.g. in non-JVM multiplatform source sets or for
     * Kotlin properties with inline value class types).
     */
    override fun visitProperty(property: PropertyItem) {
        if (property.getter != null || property.backingField != null) {
            return
        }
        if (!filter.test(property)) {
            return
        }
        checkTypeParameterListReferences(property.typeParameterList, property)
        property.receiver?.let { checkTypeReferences(it, property, "in receiver type") }
        checkTypeReferences(property.type(), property, "in property type")
    }

    /** Check all the references from [callable] to [ClassItem]s. */
    override fun visitCallable(callable: CallableItem) {
        if (!filter.test(callable)) {
            return
        }
        checkTypeParameterListReferences(callable.typeParameterList, callable)
        for (parameter in callable.parameters()) {
            checkTypeReferences(parameter.type(), parameter, "in parameter type")
        }
        for (thrown in callable.throwsTypes()) {
            if (thrown is VariableTypeItem) continue
            val classItem = thrown.asErasedClass(codebase) ?: continue
            checkClassReferences(classItem, callable, "as exception")
        }
        // Constructor return types are the containing class which has already been checked so there
        // is no point in checking that.
        if (!callable.isConstructor()) {
            checkTypeReferences(callable.returnType(), callable, "in return type")
        }
    }

    /** Check all the references from [typeParameterList] to [ClassItem]s. */
    private fun checkTypeParameterListReferences(
        typeParameterList: TypeParameterList,
        from: Item,
    ) {
        for (typeParameter in typeParameterList) {
            for (bound in typeParameter.typeBounds()) {
                checkTypeReferences(bound, from, "as type parameter")
            }
        }
    }

    /** Check all the references from [type] to [ClassItem]s. */
    private fun checkTypeReferences(
        type: TypeItem,
        context: Item,
        usage: String,
    ) {
        type.accept(
            object : BaseTypeVisitor() {
                override fun visitClassType(classType: ClassTypeItem) {
                    val asClass = classType.resolveClass(codebase) ?: return
                    checkClassReferences(asClass, context, usage)
                }
            }
        )
    }

    companion object {
        /** Compute the set of [ClassItem]s that are in the API. */
        fun computeContents(
            codebase: Codebase,
        ): Set<ClassItem> {
            val apiContents = ApiContents(codebase)
            return apiContents.computeTransitiveClosure()
        }
    }
}

/**
 * Returns true if this item is public or protected and is a valid [ReferenceKind.CODE] reference in
 * the target API surface, making it a candidate for inclusion in an API.
 */
internal fun SelectableItem.isApiCandidate() =
    codebase.apiSurfaces.referenceStrategy.isValidReference(null, this, ReferenceKind.CODE) &&
        (modifiers.isPublic() || modifiers.isProtected())
