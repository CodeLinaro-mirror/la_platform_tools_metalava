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

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.testOrTrue

open class ApiVisitor(
    /** @see BaseItemVisitor.preserveClassNesting */
    preserveClassNesting: Boolean = false,

    /** @see BaseItemVisitor.visitParameterItems */
    visitParameterItems: Boolean = true,

    /** The filters to use to determine what parts of the API will be visited. */
    apiFilters: ApiFilters?,

    /** @see BaseItemVisitor.orderClassesByName */
    orderClassesByName: Boolean = true,
) :
    BaseItemVisitor(
        preserveClassNesting = preserveClassNesting,
        visitParameterItems = visitParameterItems,
        orderClassesByName = orderClassesByName,
    ) {

    /** The filter to use to determine if we should emit an item */
    protected val filterEmit: FilterPredicate? = apiFilters?.emit

    /** The filter to use to determine if we should emit a reference to an item */
    protected val filterReference: FilterPredicate? = apiFilters?.reference

    /** The filter to use to determine if an item should be visited during traversal */
    private val traversalPredicate = apiFilters?.traversal

    /**
     * If a [traversalPredicate] is configured, skip any [SelectableItem] that does not match it.
     * Otherwise, do not skip any items here.
     */
    override fun skip(item: SelectableItem): Boolean {
        if (traversalPredicate != null) {
            return !traversalPredicate.test(item)
        }

        return false
    }

    /**
     * Implement to redirect to [VisitCandidate.accept] if necessary, or delegate to
     * [BaseItemVisitor.visit] when [traversalPredicate] is set.
     *
     * When [traversalPredicate] is null, this is not called during normal codebase traversal by
     * this [ApiVisitor]. Instead, [visit(PackageItem)] calls [VisitCandidate.accept] which does not
     * delegate to this method but visits the class and its members itself so that it can access the
     * filtered and sorted members. However, this may be called by some other code calling
     * [ClassItem.accept] directly on this [ApiVisitor]. In that case this creates and then
     * delegates through to [VisitCandidate.visitWrappedClassAndFilteredMembers].
     *
     * When [traversalPredicate] is set, [visit(PackageItem)] delegates to [BaseItemVisitor.visit],
     * which calls this method to traverse the class and its members directly while respecting
     * [skip].
     */
    override fun visit(cls: ClassItem) {
        // When [traversalPredicate] is set, delegate directly to [BaseItemVisitor.visit] to
        // traverse the class and its members directly while respecting [skip].
        if (traversalPredicate != null) {
            super.visit(cls)
            return
        }

        // Get a VisitCandidate and visit it, if needed.
        getVisitCandidateIfNeeded(cls)?.visitWrappedClassAndFilteredMembers()
    }

    override fun visit(pkg: PackageItem) {
        // When [traversalPredicate] is set, bypass [VisitCandidate] creation and delegate directly
        // to [BaseItemVisitor.visit] to traverse the package and its classes directly while
        // respecting [skip].
        if (traversalPredicate != null) {
            super.visit(pkg)
            return
        }

        if (!pkg.emit) {
            return
        }

        // Get the list of classes to visit directly. If nested classes are to appear as nested
        // then just visit the top level classes directly and then the nested classes will be
        // visited by their containing classes. Otherwise, flatten the nested classes and treat
        // them all as top level classes.
        val classesToVisitDirectly: List<ClassItem> =
            packageClassesAsSequence(pkg).mapNotNull { getVisitCandidateIfNeeded(it) }.toList()

        // If none of the classes or typealiases in this package will be visited then ignore the
        // package entirely.
        if (classesToVisitDirectly.isEmpty()) return

        wrapBodyWithCallsToVisitMethodsForSelectableItem(pkg) {
            visitPackage(pkg)

            visitClassList(classesToVisitDirectly)

            afterVisitPackage(pkg)
        }
    }

    /** @return Whether this class is generally one that we want to recurse into */
    private fun include(cls: ClassItem): Boolean {
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
        val emit = filterEmit.testOrTrue(cls)

        // If the class is emitted then create a VisitCandidate immediately.
        if (emit) return VisitCandidate(cls)

        // Check to see if the class could be emitted if it contains emittable members. If not then
        // return `null` to ignore this class. This will happen for a hidden class, e.g. package
        // private, that implements/overrides methods from the API.
        if (!filterReference.testOrTrue(cls)) return null

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
        /** The backing field of [members]. */
        private lateinit var _members: List<MemberItem>

        /** Get the members. */
        private val members: List<MemberItem>
            get() {
                if (!::_members.isInitialized) {
                    // Construct a single list of all members.
                    _members = buildList {
                        cls.constructors().filterTo(this) { filterEmit.testOrTrue(it) }
                        cls.methods().filterTo(this) { filterEmit.testOrTrue(it) }
                        cls.properties().filterTo(this) { filterEmit.testOrTrue(it) }
                        cls.fields().filterTo(this) { filterEmit.testOrTrue(it) }
                    }
                }

                return _members
            }

        /** Whether the class body contains any emittable [MemberItem]s. */
        fun containsNoEmittableMembers() = members.isEmpty()

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

                for (member in members) {
                    member.accept(this@ApiVisitor)
                }

                if (preserveClassNesting) { // otherwise done in visit(PackageItem)
                    visitClassList(cls.nestedClasses().mapNotNull { getVisitCandidateIfNeeded(it) })
                }

                afterVisitClass(cls)
            }
        }
    }
}
