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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem

open class ApiVisitor(
    /** @see BaseItemVisitor.preserveClassNesting */
    preserveClassNesting: Boolean = false,

    /** @see BaseItemVisitor.visitParameterItems */
    visitParameterItems: Boolean = true,

    /** Whether to include inherited fields too */
    private val inlineInheritedFields: Boolean = true,

    /** Comparator to sort callables with. */
    private val callableComparator: Comparator<CallableItem> = CallableItem.comparator,

    /** The filters to use to determine what parts of the API will be visited. */
    private val apiFilters: ApiFilters,

    /**
     * Whether this visitor should visit elements that have not been annotated with one of the
     * annotations passed in using the --show-annotation flag. This is normally true, but signature
     * files sometimes sets this to false so the signature file only contains the "diff" of the
     * annotated API relative to the base API.
     */
    protected val showUnannotated: Boolean = true,
) : BaseItemVisitor(preserveClassNesting, visitParameterItems) {

    constructor(
        /** @see BaseItemVisitor.visitParameterItems */
        visitParameterItems: Boolean = true,

        /** Configuration that may come from the command line. */
        apiPredicateConfig: ApiPredicate.Config,
    ) : this(
        visitParameterItems = visitParameterItems,
        apiFilters = defaultFilters(apiPredicateConfig),
    )

    /** The filter to use to determine if we should emit an item */
    protected val filterEmit = apiFilters.emit

    /** The filter to use to determine if we should emit a reference to an item */
    protected val filterReference = apiFilters.reference

    companion object {
        /** Get the default [ApiFilters] to use with [ApiVisitor]. */
        fun defaultFilters(
            apiPredicateConfig: ApiPredicate.Config,
        ): ApiFilters {
            return ApiFilters(
                emit = defaultEmitFilter(apiPredicateConfig),
                reference =
                    ApiPredicate(
                        ignoreRemoved = false,
                        config = apiPredicateConfig.copy(ignoreShown = true),
                    ),
            )
        }

        /** Get the default emit filter to use with [ApiVisitor]. */
        fun defaultEmitFilter(apiPredicateConfig: ApiPredicate.Config) =
            ApiPredicate(
                matchRemoved = false,
                includeApisForStubPurposes = true,
                config = apiPredicateConfig.copy(ignoreShown = true),
            )
    }

    /**
     * Visit a [List] of [ClassItem]s after sorting it into order defined by
     * [ClassItem.classNameSorter].
     */
    private fun visitClassList(classes: List<ClassItem>) {
        classes.sortedWith(ClassItem.classNameSorter()).forEach { it.accept(this) }
    }

    /**
     * Implement to redirect to [VisitCandidate.accept] if necessary,
     *
     * This is not called by this [ApiVisitor]. Instead, it calls [VisitCandidate.accept] which does
     * not delegate to this method but visits the class and its members itself so that it can access
     * the filtered and sorted members. However, this may be called by some other code calling
     * [ClassItem.accept] directly on this [ApiVisitor]. In that case this creates and then
     * delegates through to the [VisitCandidate.visitWrappedClassAndFilteredMembers]
     */
    override fun visit(cls: ClassItem) {
        // Get a VisitCandidate and visit it, if needed.
        getVisitCandidateIfNeeded(cls)?.visitWrappedClassAndFilteredMembers()
    }

    override fun visit(pkg: PackageItem) {
        if (!pkg.emit) {
            return
        }

        // Get the list of classes to visit directly. If nested classes are to appear as nested
        // then just visit the top level classes directly and then the nested classes will be
        // visited
        // by their containing classes. Otherwise, flatten the nested classes and treat them all as
        // top level classes.
        val classesToVisitDirectly: List<ClassItem> =
            packageClassesAsSequence(pkg).mapNotNull { getVisitCandidateIfNeeded(it) }.toList()

        // If none of the classes in this package will be visited them ignore the package entirely.
        if (classesToVisitDirectly.isEmpty()) return

        wrapBodyWithCallsToVisitMethodsForSelectableItem(pkg) {
            visitPackage(pkg)

            visitClassList(classesToVisitDirectly)

            afterVisitPackage(pkg)
        }
    }

    /** @return Whether this class is generally one that we want to recurse into */
    open fun include(cls: ClassItem): Boolean {
        if (skip(cls)) {
            return false
        }

        return cls.emit
    }

    /**
     * Returns a [VisitCandidate] if the [cls] needs to be visited, otherwise return `null`.
     *
     * The [cls] needs to be visited if it passes the various checks that determine whether it
     * should be emitted as part of an API surface as determined by [filterEmit] and
     * [filterReference].
     */
    private fun getVisitCandidateIfNeeded(cls: ClassItem): VisitCandidate? {
        if (!include(cls)) return null

        // Check to see whether this class should be emitted in its entirety. If not then it may
        // still be emitted if it contains emittable members.
        val emit = filterEmit.test(cls)

        // If the class is emitted then create a VisitCandidate immediately.
        if (emit) return VisitCandidate(cls)

        // Check to see if the class could be emitted if it contains emittable members. If not then
        // return `null` to ignore this class. This will happen for a hidden class, e.g. package
        // private, that implements/overrides methods from the API.
        if (!filterReference.test(cls)) return null

        // Create a VisitCandidate to encapsulate the emittable members, if any.
        val vc = VisitCandidate(cls)

        // Check to see if the class has any emittable members, if not return `null` to ignore this
        // class.
        if (vc.containsNoEmittableMembers()) return null

        // The class is emittable so return it.
        return vc
    }

    /**
     * Encapsulates a [ClassItem] that is being visited and its members, filtered by [filterEmit],
     * and sorted by various members specific comparators.
     *
     * The purpose of this is to store the lists of filtered and sorted members that were created
     * during filtering of the classes in the [PackageItem] visit method. They need to be stored as
     * they can take a long time to generate and will be needed again when visiting the class
     * contents.
     *
     * Note: This implements [ClassItem] to allow visiting code to be more easily shared between
     * this and [BaseItemVisitor]. It must not escape out of this class, e.g. be passed to
     * `visitClass(...)`.
     */
    private inner class VisitCandidate(val cls: ClassItem) : ClassItem by cls {

        /**
         * If the list this is called upon is empty then just return [emptyList], else apply the
         * [transform] to the list and return that.
         */
        private inline fun <T> List<T>.mapIfNotEmpty(transform: List<T>.() -> List<T>) =
            if (isEmpty()) emptyList() else transform(this)

        /**
         * Sort the sequence into a [List].
         *
         * The standard [Sequence.sortedWith] will sort it into a list and then return a sequence
         * wrapper which would then have to be converted back into a list. Instead, this just sorts
         * it into a [List] and returns that.
         */
        private fun <T> Sequence<T>.sortToList(comparator: Comparator<in T>) =
            if (none()) emptyList()
            else
                toMutableList().let {
                    // Sort the list in place.
                    it.sortWith(comparator)
                    // Return the sorter list.
                    it
                }

        private val constructors =
            cls.constructors().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(callableComparator)
            }

        private val methods =
            cls.methods().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(callableComparator)
            }

        private val fields by
            lazy(LazyThreadSafetyMode.NONE) {
                val fieldSequence =
                    if (inlineInheritedFields) {
                        cls.filteredFields(filterEmit, showUnannotated).asSequence()
                    } else {
                        cls.fields().asSequence().filter { filterEmit.test(it) }
                    }

                // Sort the fields so that enum constants come first.
                fieldSequence.sortToList(FieldItem.comparatorEnumConstantFirst)
            }

        private val properties =
            cls.properties().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(PropertyItem.comparator)
            }

        /** Whether the class body contains any emmittable [MemberItem]s. */
        fun containsNoEmittableMembers() =
            constructors.isEmpty() && methods.isEmpty() && fields.isEmpty() && properties.isEmpty()

        /**
         * Intercepts the call to visit this class and instead of using the default implementation
         * which delegate to the appropriate method in [visitor] calls
         */
        override fun accept(visitor: ItemVisitor) {
            if (visitor !== this@ApiVisitor)
                error(
                    "VisitCandidate instance must only be visited by its creating ApiVisitor, not $visitor"
                )
            visitWrappedClassAndFilteredMembers()
        }

        fun visitWrappedClassAndFilteredMembers() {
            wrapBodyWithCallsToVisitMethodsForSelectableItem(cls) {
                visitClass(cls)

                for (constructor in constructors) {
                    constructor.accept(this@ApiVisitor)
                }

                for (method in methods) {
                    method.accept(this@ApiVisitor)
                }

                for (property in properties) {
                    property.accept(this@ApiVisitor)
                }
                for (field in fields) {
                    field.accept(this@ApiVisitor)
                }

                if (preserveClassNesting) { // otherwise done in visit(PackageItem)
                    visitClassList(cls.nestedClasses().mapNotNull { getVisitCandidateIfNeeded(it) })
                }

                afterVisitClass(cls)
            }
        }
    }
}
