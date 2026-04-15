/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.snapshot.CodebaseSnapshotTaker
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor

/**
 * Encapsulates [codebase] to visit and a [visitorFactory] that if given a [DelegatedVisitor] will
 * return an [ItemVisitor] that can be used to visit some fragment of the [codebase].
 */
abstract class CodebaseFragment
private constructor(
    private val callableComparator: Comparator<CallableItem>,
) {

    /** The [Codebase] whose fragment will be visited. */
    abstract val codebase: Codebase

    /**
     * A factory for creating an [ItemVisitor] that delegates to a [DelegatedVisitor].
     *
     * The [ItemVisitor] is used to determine which parts of [codebase] are considered to be defined
     * within and emitted from this fragment.
     */
    protected abstract val visitorFactory: (DelegatedVisitor) -> ItemVisitor

    /**
     * Return a [CodebaseFragment] that will take a snapshot of this [CodebaseFragment].
     *
     * @param referenceVisitorFactory a factory for creating an [ItemVisitor] that delegates to a
     *   [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase] will
     *   be referenced from within but not emitted from the snapshot.
     */
    fun snapshotIncludingRevertedItems(
        referenceVisitorFactory: (DelegatedVisitor) -> ItemVisitor,
        includeDocumentation: Boolean = false,
    ): CodebaseFragment {
        return LazyCodebaseFragment(
            {
                CodebaseSnapshotTaker.takeSnapshot(
                    codebase,
                    definitionVisitorFactory = visitorFactory,
                    referenceVisitorFactory = referenceVisitorFactory,
                    includeDocumentation,
                )
            },
            callableComparator,
            ::EmittableDelegatingVisitor,
        )
    }

    /** Visit this fragment, delegating to [delegate]. */
    fun accept(delegate: DelegatedVisitor) {
        val memberComparator = MemberItemComparator(callableComparator)
        val sortingDelegate = MemberSortingDelegatedVisitor(memberComparator, delegate)
        val visitor = visitorFactory(sortingDelegate)
        codebase.accept(visitor)
    }

    companion object {
        /**
         * Create a [CodebaseFragment] from an existing [Codebase].
         *
         * @param callableComparator the [Comparator] to use for sorting [CallableItem]s when
         *   visiting a [ClassItem]'s members.
         * @param factory a factory for creating an [ItemVisitor] that delegates to a
         *   [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase]
         *   are considered to be defined within and emitted from this fragment.
         */
        fun create(
            codebase: Codebase,
            callableComparator: Comparator<CallableItem> = CallableItem.comparator,
            factory: (DelegatedVisitor) -> ItemVisitor,
        ): CodebaseFragment =
            ExistingCodebaseFragment(
                codebase,
                callableComparator,
                factory,
            )
    }

    /** A [CodebaseFragment] of an existing [Codebase]. */
    private class ExistingCodebaseFragment(
        override val codebase: Codebase,
        callableComparator: Comparator<CallableItem>,
        override val visitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ) : CodebaseFragment(callableComparator)

    /** A [CodebaseFragment] of a [Codebase] that will be provided lazily. */
    private class LazyCodebaseFragment(
        codebaseProvider: () -> Codebase,
        callableComparator: Comparator<CallableItem>,
        override val visitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ) : CodebaseFragment(callableComparator) {

        override val codebase by lazy(LazyThreadSafetyMode.NONE) { codebaseProvider() }
    }
}

/**
 * A [Comparator] used to sort all [MemberItem]s of a class.
 *
 * This sorts [MemberItem]s into the following groups (using the [kindOrdinal] function):
 * * Record component [PropertyItem]s
 * * [ConstructorItem]s
 * * [MethodItem]s
 * * Other [PropertyItem]s
 * * Enum constant [FieldItem]s.
 * * Other [FieldItem]s.
 *
 * Each item within the group is sorted using the appropriate [Comparator]. For the [CallableItem]s
 * that is [callableComparator], for the others that is a stable , source order independent
 * [Comparator], e.g. [FieldItem.comparator].
 */
internal class MemberItemComparator(private val callableComparator: Comparator<CallableItem>) :
    Comparator<MemberItem> {
    /**
     * Get the order of this [MemberItem] kind within the list of [MemberItem]s.
     *
     * Defines the order of kinds of [MemberItem]s, e.g. constructors come before methods, enum
     * constants come before other fields, etc. The value returned is such that all [MemberItem]s
     * that return a specific value must be of the same [MemberItem] subclass as that is relied upon
     * by [compare].
     */
    private fun MemberItem.kindOrdinal() =
        when (this) {
            is ConstructorItem -> CONSTRUCTOR_ORDER
            is MethodItem -> METHOD_ORDER
            is PropertyItem -> if (isRecordComponent()) RECORD_COMPONENT_ORDER else PROPERTY_ORDER
            is FieldItem -> if (isEnumConstant()) ENUM_CONSTANT_ORDER else FIELD_ORDER
            else -> error("unknown member item type $this of $javaClass")
        }

    override fun compare(m1: MemberItem, m2: MemberItem): Int {
        // First compare the kind of member items.
        val o1 = m1.kindOrdinal()
        val o2 = m2.kindOrdinal()
        if (o1 != o2) return o1 - o2

        // Then, use a type specific comparator.
        return when (m1) {
            is CallableItem -> callableComparator.compare(m1, m2 as CallableItem)
            is FieldItem -> FieldItem.comparator.compare(m1, m2 as FieldItem)
            is PropertyItem -> PropertyItem.comparator.compare(m1, m2 as PropertyItem)
            else -> error("unknown member item type $this of $javaClass")
        }
    }

    companion object {
        const val RECORD_COMPONENT_ORDER = 0
        const val CONSTRUCTOR_ORDER = RECORD_COMPONENT_ORDER + 1
        const val METHOD_ORDER = CONSTRUCTOR_ORDER + 1
        const val PROPERTY_ORDER = METHOD_ORDER + 1
        const val ENUM_CONSTANT_ORDER = PROPERTY_ORDER + 1
        const val FIELD_ORDER = ENUM_CONSTANT_ORDER + 1
    }
}

/**
 * A [DelegatedVisitor] implementation that collates the [MemberItem]s into a [members] list and
 * sorts it before visiting.
 */
class MemberSortingDelegatedVisitor(
    private val comparator: Comparator<MemberItem>,
    private val delegate: DelegatedVisitor,
) : DelegatedVisitor by delegate {
    private val members = mutableListOf<MemberItem>()

    override fun visitConstructor(constructor: ConstructorItem) {
        members.add(constructor)
    }

    override fun visitField(field: FieldItem) {
        members.add(field)
    }

    override fun visitMethod(method: MethodItem) {
        members.add(method)
    }

    override fun visitProperty(property: PropertyItem) {
        members.add(property)
    }

    override fun visitClass(cls: ClassItem) {
        // Flush any class members before visiting any nested classes. This is needed to ensure
        // that nested classes appear after the class members.
        flushClassMembers()
        delegate.visitClass(cls)
    }

    override fun afterVisitClass(cls: ClassItem) {
        // Flush any class members after visiting a class.
        flushClassMembers()
        delegate.afterVisitClass(cls)
    }

    /**
     * Flushes the [members] list.
     *
     * Thst involves sorting the list according to [comparator], visiting each in turn and then
     * clearing the list.
     */
    private fun flushClassMembers() {
        if (members.isNotEmpty()) {
            members.sortWith(comparator)
            for (memberItem in members) {
                when (memberItem) {
                    is ConstructorItem -> delegate.visitConstructor(memberItem)
                    is MethodItem -> delegate.visitMethod(memberItem)
                    is FieldItem -> delegate.visitField(memberItem)
                    is PropertyItem -> delegate.visitProperty(memberItem)
                    else -> error("unknown member $memberItem of ${memberItem.javaClass}")
                }
            }
            members.clear()
        }
    }
}
