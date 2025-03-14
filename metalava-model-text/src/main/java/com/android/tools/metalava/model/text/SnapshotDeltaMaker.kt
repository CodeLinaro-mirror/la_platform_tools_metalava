/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor

/**
 * Creates a snapshot that is a delta between two [Codebase]s.
 *
 * This effectively does the opposite of what [ApiFile] does when creating a [Codebase] from
 * multiple signature files, where the first is a standalone surface and each subsequent file is for
 * a surface that extends the surface in the preceding file.
 *
 * This can be used to create deltas that can be used when class nesting is not maintained as it
 * does not emit a class just because a nested class needs emitting.
 */
class SnapshotDeltaMaker private constructor(private val base: Codebase) :
    BaseItemVisitor(
        preserveClassNesting = true,
        visitParameterItems = false,
    ) {
    /**
     * Mark the package to emit.
     *
     * The containing package is not marked to emit as packages are flattened before visiting.
     */
    private fun PackageItem.markEmit() {
        if (!emit) {
            emit = true
        }
    }

    /**
     * Mark the class to emit.
     *
     * The containing package is marked to emit as otherwise its contents will not usually be
     * visited. The containing class of nested classes is not marked to emit as this is used for
     * files that flatten nested classes so nested classes can be visited without checking the
     * [SelectableItem.emit] of the containing class.
     */
    private fun ClassItem.markEmit() {
        if (!emit) {
            emit = true
            containingPackage().markEmit()
        }
    }

    /**
     * Mark a member to emit.
     *
     * The containing class is marked to emit as otherwise its members will not be visited.
     */
    private fun MemberItem.markEmit() {
        if (!emit) {
            emit = true
            containingClass().markEmit()
        }
    }

    /** Override to visit all packages. */
    override fun skipPackage(pkg: PackageItem) = false

    /** Override to skip any non-public or protected items. */
    override fun skip(item: Item): Boolean = !item.modifiers.isPublicOrProtected()

    /** Convert a list of [AnnotationItem]s into a list of [String]s for comparison. */
    // TODO(b/354633349): Use equality once value abstraction provides consistent behavior across
    //   models.
    private fun List<AnnotationItem>.normalize() = map { it.toString() }.sorted()

    override fun visitClass(cls: ClassItem) {
        cls.findCorrespondingItemIn(base)?.let { baseClass ->
            // If super class type is set and is different to the base class then drop out to emit
            // this class.
            val superClassType = cls.superClassType()
            if (superClassType != null && baseClass.superClassType() != superClassType) {
                return@let
            }

            // If this class has different annotations to the base class then drop out to emit
            // this class.
            val annotations = cls.modifiers.annotations().normalize()
            val baseAnnotations = baseClass.modifiers.annotations().normalize()
            if (annotations != baseAnnotations) {
                return@let
            }

            // The class is not different so do not emit it.
            return
        }

        // The class is new or different.
        cls.markEmit()
    }

    override fun visitCallable(callable: CallableItem) {
        callable.findCorrespondingItemIn(base)?.let {
            return
        }

        // The callable is new.
        callable.markEmit()
    }

    override fun visitField(field: FieldItem) {
        field.findCorrespondingItemIn(base)?.let {
            return
        }

        // The field is new.
        field.markEmit()
    }

    override fun visitProperty(property: PropertyItem) {
        property.findCorrespondingItemIn(base)?.let {
            return
        }

        // The property is new.
        property.markEmit()
    }

    companion object {
        /**
         * Create a text [Codebase] that is a delta between [base] and [codebaseFragment], i.e. it
         * includes all the [Item] that are in [codebaseFragment] but not in [base].
         *
         * This is expected to be used where [codebaseFragment] is a super set of [base] but that is
         * not enforced. If [base] contains [Item]s which are not present in [codebaseFragment] then
         * they will not appear in the delta.
         *
         * [ClassItem]s are treated specially. If [codebaseFragment] and [base] have [ClassItem]s
         * with the same name and [codebaseFragment]'s has members which are not present in [base]'s
         * then a [ClassItem] containing the additional [codebaseFragment] members will appear in
         * the delta, otherwise it will not unless the two [ClassItem]s differ in one of the
         * following ways:
         * * The modifiers are not [ModifierList.equivalentTo] each other.
         * * The [ClassItem.superClassType]s are not the same.
         *
         * Note: A [MemberItem] that exists in both will not be emitted even if they differ in some
         * way, e.g. annotations, extends list. That is because [ApiFile] has no mechanism to
         * combine them and does not even throw an error if it encounters duplicates.
         */
        fun createDelta(
            base: Codebase,
            codebaseFragment: CodebaseFragment,
        ): CodebaseFragment {
            // Take a snapshot.
            val snapshotFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                )

            val snapshot = snapshotFragment.codebase

            // Assume that none of it will be emitted.
            snapshot.accept(
                object : BaseItemVisitor() {
                    override fun visitSelectableItem(item: SelectableItem) {
                        item.emit = false
                    }
                }
            )

            // Mark those items that are new (or different) to be emitted. Also, marks their
            // containers, e.g. class members and nested classes will mark their containing class,
            // classes will mark their containing package.
            val deltaMaker = SnapshotDeltaMaker(base)
            snapshot.accept(deltaMaker)

            return CodebaseFragment.create(snapshot, ::EmittableDelegatingVisitor)
        }
    }
}
