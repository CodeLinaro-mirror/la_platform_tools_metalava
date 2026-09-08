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
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Issues

/** Determines all the [ClassItem]s that are part of the API. */
internal class ApiContents(
    private val codebase: Codebase,
    apiPredicateConfig: ApiPredicate.Config,
) :
    BaseItemVisitor(
        // Preserve class nesting as otherwise this requires that PackageItem visit method is called
        // to flatten the classes. This does not visit packages because that could cause
        // ConcurrentModificationException issues as this may cause additional ClassItems to be
        // added to
        // the PackageItem that is being visited.
        preserveClassNesting = true,

        // Parameter items are already visited by [visitCallable].
        visitParameterItems = false,
    ) {
    private val reporter = codebase.reporter

    /**
     * The set of [ClassItem]s that are part of the API.
     *
     * Populated by [computeTransitiveClosure].
     */
    private val notStrippable = HashSet<ClassItem>(5000)

    /** The filter that determines which [SelectableItem]s are included in the API. */
    private val filter =
        ApiPredicate(
                // Use the whole API surface so that classes belonging to any API surface in the
                // hierarchy (such as base surfaces) are recognized as part of the API and not
                // stripped when referenced.
                config = apiPredicateConfig,
            )
            .and { selectableItem ->
                // Don't consider references from elements that only exist in bytecode.
                selectableItem.targetLanguages != TargetLanguageSet.BYTECODE_ONLY
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
        // Create a list containing all top level classes to avoid a ConcurrentModificationException
        // when visiting.
        val allTopLevelClasses = codebase.getPackages().allTopLevelClasses().toList()

        // Iterate over the list of classes.
        for (classItem in allTopLevelClasses) {
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
    override fun skip(item: SelectableItem): Boolean {
        if (item is ClassItem) {
            // If a class is not public or protected, hidden, or not marked for emitting then it
            // not part of the API and neither are its nested classes.
            if (!item.isApiCandidate() || !item.emit) return true
        }

        return false
    }

    /**
     * Check [cls]'s references to other [ClassItem]s.
     *
     * This is called both for top-level classes and nested classes.
     */
    override fun visitClass(cls: ClassItem) {
        val containingClass = cls.containingClass()
        if (containingClass == null) {
            checkClassReferences(cls, cls, "self")
        } else {
            checkClassReferences(cls, containingClass, "as nested class")
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
        // will report all references to the hidden class.
        if (cl.isHiddenOrRemoved() || cl.isPackagePrivate && !cl.isApiCandidate()) {
            reporter.report(
                Issues.REFERENCES_HIDDEN,
                from,
                "Class ${cl.qualifiedName()} is ${if (cl.isHiddenOrRemoved()) "hidden" else "not public"} but was referenced ($usage) from public ${from.describe()}"
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
            // allInterfaces includes cl itself if cl is an interface
            if (superItem.isHiddenOrRemoved() && superItem != cl) {
                // cl is a public class declared as extending a hidden superclass or implementing
                // a hidden interface.
                // this is not a desired practice, but it's happened, so we deal
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
            apiPredicateConfig: ApiPredicate.Config,
        ): Set<ClassItem> {
            val apiContents = ApiContents(codebase, apiPredicateConfig)
            return apiContents.computeTransitiveClosure()
        }
    }
}

/** Returns true if this item is public or protected and so a candidate for inclusion in an API. */
internal fun SelectableItem.isApiCandidate() =
    !isHiddenOrRemoved() && (modifiers.isPublic() || modifiers.isProtected())
