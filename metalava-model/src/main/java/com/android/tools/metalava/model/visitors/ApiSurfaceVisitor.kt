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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.testOrTrue

/**
 * A visitor that visits items that are part of an API surface as determined by [filterEmit].
 *
 * Traverses packages and classes that match [filterEmit]. When visiting a class, its members are
 * visited, and nested classes are visited according to [preserveClassNesting].
 *
 * It is the sole responsibility of the [filterEmit] to determine whether a class or package is
 * visited by this for any reason. That differs from [ApiVisitor] which can visit a class that does
 * not need visiting itself but whose members need visiting.
 *
 * ### Benefits compared with [ApiVisitor]
 * * **Simpler visitation model:** [ApiVisitor] intercepts class visitation using `VisitCandidate`
 *   wrappers that implement [ClassItem] via delegation and override [ClassItem.accept] to bypass
 *   normal visitor dispatch. [ApiSurfaceVisitor] avoids this indirection and uses standard visitor
 *   dispatch, making execution flow and subclass overrides predictable.
 * * **Reduced allocations and memory overhead:** [ApiVisitor] allocates a `VisitCandidate` instance
 *   for each visited class and builds cached member lists in memory. [ApiSurfaceVisitor] traverses
 *   classes and members directly without wrapper allocations or member caching.
 * * **Direct API surface semantics:** [ApiVisitor] uses [ApiVisitor.filterReference] to include
 *   classes that do not match [filterEmit] if they contain emittable members (a legacy requirement
 *   for signature generation). In contrast, [ApiSurfaceVisitor] does not need or use a reference
 *   filter, and strictly visits classes that belong to the API surface as determined by
 *   [filterEmit].
 * * **Consistent package filtering:** [ApiSurfaceVisitor] filters packages using [filterEmit],
 *   whereas [ApiVisitor] checks [PackageItem.emit] directly, which ignores the filter predicate.
 */
open class ApiSurfaceVisitor(
    /** @see BaseItemVisitor.preserveClassNesting */
    preserveClassNesting: Boolean = false,

    /** @see BaseItemVisitor.visitParameterItems */
    visitParameterItems: Boolean = true,

    /** The filter to use to determine if we should visit an item */
    protected val filterEmit: FilterPredicate?,
) : BaseItemVisitor(preserveClassNesting, visitParameterItems) {
    /** Skip any item that does not match [filterEmit]. */
    override fun skip(item: SelectableItem) = !filterEmit.testOrTrue(item)
}

/**
 * An [ApiSurfaceVisitor] that is constructed using [ApiFilters] and provides a [filterReference]
 * property for use by subclasses.
 */
open class ApiFiltersVisitor(
    /** @see BaseItemVisitor.preserveClassNesting */
    preserveClassNesting: Boolean = false,

    /** @see BaseItemVisitor.visitParameterItems */
    visitParameterItems: Boolean = true,

    /** The filters to use to determine if we should visit an item */
    apiFilters: ApiFilters?,
) : BaseItemVisitor(preserveClassNesting, visitParameterItems) {

    /**
     * The filter predicate used to determine which items are visited during traversal.
     *
     * Defaults to [ApiFilters.traversal] if specified, otherwise falls back to [ApiFilters.emit].
     */
    private val traversalPredicate = apiFilters?.traversal ?: apiFilters?.emit

    /** Skip any item that does not match [traversalPredicate]. */
    override fun skip(item: SelectableItem) = !traversalPredicate.testOrTrue(item)

    /**
     * Filter predicate that determines whether an [Item] should be defined and emitted as part of
     * the API surface.
     *
     * Subclasses can use this to check whether a visited item is actually part of the emitted API,
     * which may differ from [traversalPredicate] if a separate traversal filter was provided in
     * [ApiFilters].
     *
     * Use [FilterPredicate.testOrTrue] when querying this property so that if no filter was
     * provided, all items are treated as emittable.
     */
    protected val filterEmit: FilterPredicate? = apiFilters?.emit

    /**
     * Filter predicate that determines whether an [Item] can be referenced from the API surface.
     *
     * Unlike [filterEmit], which controls which items are visited during traversal, this filter is
     * not used by the visitor traversal itself. Subclasses should use this when checking whether
     * items referenced by the visited API (such as field constants referenced in typedef
     * annotations, types in signatures, or supertypes) are accessible as part of the API.
     *
     * Use [FilterPredicate.testOrTrue] when querying this property so that if no filter was
     * provided, all items are treated as referenceable.
     */
    protected val filterReference: FilterPredicate? = apiFilters?.reference
}
