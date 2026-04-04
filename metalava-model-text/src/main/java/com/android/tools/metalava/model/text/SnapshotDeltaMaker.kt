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

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterListOwner
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
 *
 * If [checkMemberItemEquivalence] is true, then [MemberItem]s are emitted when there is a change
 * between the base and extension members. If it is false, [MemberItem]s are not emitted when they
 * are present in both the base and extension, even if there is some difference between them.
 */
class SnapshotDeltaMaker
private constructor(private val base: Codebase, private val checkMemberItemEquivalence: Boolean) :
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

    override fun visitClass(cls: ClassItem) {
        cls.findCorrespondingItemIn(base)?.let { baseClass ->
            // If super class type is set and is different to the base class then drop out to emit
            // this class.
            val superClassType = cls.superClassType()
            if (superClassType != null && baseClass.superClassType() != superClassType) {
                return@let
            }

            // If the interface types are set and different from the base class then drop out to
            // emit this class.
            if (
                cls.interfaceTypes().isNotEmpty() &&
                    cls.interfaceTypes().toSet() != baseClass.interfaceTypes().toSet()
            ) {
                return@let
            }

            // If this class has different annotations to the base class then drop out to emit
            // this class.
            if (!equivalentAnnotations(baseClass, cls)) {
                return@let
            }

            // If the class has changed to a typealias then drop out to emit it (no other class kind
            // changes are allowed).
            if (
                baseClass.classKind != ClassKind.TYPEALIAS && cls.classKind == ClassKind.TYPEALIAS
            ) {
                return@let
            }

            // The class is not different so do not emit it.
            return
        }

        // The class is new or different.
        cls.markEmit()
    }

    override fun visitCallable(callable: CallableItem) {
        callable.findCorrespondingItemIn(base)?.let { baseCallable ->
            if (checkMemberItemEquivalence) {
                // Check if a change in modifiers requires emitting the callable.
                if (!equivalentModifiers(baseCallable, callable)) return@let

                // Check if a change in a parameter requires emitting the callable.
                val zippedParameters = baseCallable.parameters().zip(callable.parameters())
                for ((baseParameter, callableParameter) in zippedParameters) {
                    if (!equivalentModifiers(baseParameter, callableParameter)) {
                        return@let
                    }
                }

                // Check if there are changes in type parameters that require emitting the callable.
                if (!equivalentTypeParameters(baseCallable, callable)) {
                    return@let
                }
            }

            return
        }

        // The callable is new or changed.
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
        property.findCorrespondingItemIn(base)?.let { baseProperty ->
            if (checkMemberItemEquivalence) {
                // Check if a change in modifiers requires emitting the property.
                if (!equivalentModifiers(baseProperty, property)) return@let

                // Check if there are changes in type parameters that require emitting the property.
                if (!equivalentTypeParameters(baseProperty, property)) {
                    return@let
                }
            }

            return
        }

        // The property is new or changed.
        property.markEmit()
    }

    /** Checks if the two items have the same set of annotations. */
    private fun equivalentAnnotations(baseItem: Item, extensionItem: Item): Boolean {
        val baseAnnotations = baseItem.modifiers.annotations().toSet()
        val extensionAnnotations = extensionItem.modifiers.annotations().toSet()
        return extensionAnnotations == baseAnnotations
    }

    /** Checks whether the two items have equivalent modifiers and annotations. */
    private fun equivalentModifiers(baseItem: Item, extensionItem: Item): Boolean {
        if (!baseItem.modifiers.equivalentTo(baseItem, extensionItem.modifiers)) return false
        return equivalentAnnotations(baseItem, extensionItem)
    }

    /**
     * Checks if the type parameter lists on the two owners should be considered equivalent. They
     * are not equivalent if any type parameters differ in whether they are reified.
     */
    private fun equivalentTypeParameters(
        baseOwner: TypeParameterListOwner,
        extensionOwner: TypeParameterListOwner,
    ): Boolean {
        val zippedParameters = baseOwner.typeParameterList.zip(extensionOwner.typeParameterList)
        for ((baseParameter, callableParameter) in zippedParameters) {
            if (baseParameter.isReified() != callableParameter.isReified()) return false
        }
        return true
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
         * If [checkMemberItemEquivalence] is false, a [MemberItem] that exists in both will not be
         * emitted even if they differ in some way, e.g. annotations, extends list. If it is true,
         * then [MemberItem]s will be emitted if they differ. When being parsed back by [ApiFile],
         * the definition from the extension file will be used and the [base] definition will be
         * ignored.
         */
        fun createDelta(
            base: Codebase,
            codebaseFragment: CodebaseFragment,
            checkMemberItemEquivalence: Boolean,
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
            val deltaMaker = SnapshotDeltaMaker(base, checkMemberItemEquivalence)
            snapshot.accept(deltaMaker)

            return CodebaseFragment.create(
                snapshot,
                factory = ::EmittableDelegatingVisitor,
            )
        }
    }
}
