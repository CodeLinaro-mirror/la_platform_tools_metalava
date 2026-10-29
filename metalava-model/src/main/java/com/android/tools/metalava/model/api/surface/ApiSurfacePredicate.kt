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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.EmittedOnlyPredicate
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Indenter
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.andPredicates
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.orPredicates
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiType

/** Factory for creating [FilterPredicate] instances based on [ApiSurface]s and [ApiVariant]s. */
object ApiSurfacePredicate {

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core [ApiVariant] of
     * [apiSurface] or any surface that it includes.
     */
    fun wholeCoreApi(
        apiSurface: ApiSurface,
    ) = wholeApiForVariants(apiSurface, ApiType.CORE.emitVariantTypes)

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core [ApiVariant] of
     * [apiSurface] or any surface that it includes.
     *
     * Only matches items for which [SelectableItem.emit] is true, filtering out non-emittable items
     * such as external classpath dependencies (e.g. `java.lang.Object`) that are not part of the
     * emitted API even if they have been assigned API variants during traversal.
     */
    fun wholeCoreEmittableApi(apiSurface: ApiSurface): FilterPredicate =
        andPredicates(
            EmittedOnlyPredicate,
            wholeCoreApi(apiSurface),
        )

    /**
     * Return a [FilterPredicate] that matches any item that belongs to the core or removed
     * [ApiVariant] of [apiSurface] or any surface that it includes.
     */
    fun wholeCoreAndRemovedApi(
        apiSurface: ApiSurface,
    ) =
        wholeApiForVariants(
            apiSurface,
            ApiType.REMOVED.referenceVariantTypes,
        )

    /**
     * Return a [FilterPredicate] that matches any item that belongs to any of [variantTypes] of
     * [apiSurface] or any surface that it includes.
     */
    private fun wholeApiForVariants(
        apiSurface: ApiSurface,
        variantTypes: List<ApiVariantType>,
    ): FilterPredicate {
        val apiSurfaces = apiSurface.surfaces
        val inclusionMask =
            computeVariantBitMask(
                apiSurfaces,
                apiSurface.includedSurfaces,
                variantTypes,
            )

        return ItemApiVariantsPredicate(apiSurfaces, inclusionMask)
    }

    /**
     * Base class for [FilterPredicate]s that match items based on an [inclusionMask] of
     * [ApiVariant]s.
     */
    abstract class ApiVariantsPredicate(
        private val apiSurfaces: ApiSurfaces,
        protected val inclusionMask: Int,
    ) : FilterPredicate() {

        override fun format(indenter: Indenter) {
            indenter.append(
                "${javaClass.simpleName}(${ApiVariantSet(inclusionMask).formatFor(apiSurfaces)})"
            )
        }
    }

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] matching
     * [inclusionMask] via [SelectedApi.itemApiVariants].
     */
    private class ItemApiVariantsPredicate(
        apiSurfaces: ApiSurfaces,
        inclusionMask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, inclusionMask) {
        override fun test(t: SelectableItem) =
            t.selectedApi.itemApiVariants.bits and inclusionMask != 0
    }

    /**
     * A [FilterPredicate] that matches an item if its contents belong to at least one [ApiVariant]
     * matching [inclusionMask] via [SelectedApi.contentApiVariants].
     *
     * This ensures that base classes containing delta members are traversed rather than skipped,
     * allowing visitors to reach those delta members.
     */
    private class ContentApiVariantsPredicate(
        apiSurfaces: ApiSurfaces,
        inclusionMask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, inclusionMask) {
        override fun test(t: SelectableItem) =
            t.selectedApi.contentApiVariants.bits and inclusionMask != 0
    }

    /**
     * A [FilterPredicate] that matches an item if its super class belongs to at least one
     * [ApiVariant] matching [inclusionMask] via [SelectedApi.superClassApiVariants].
     *
     * This ensures that classes extending a super class in a delta surface are traversed or
     * included in signature files to accurately reveal the inheritance hierarchy.
     */
    private class SuperClassApiVariantsPredicate(
        apiSurfaces: ApiSurfaces,
        inclusionMask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, inclusionMask) {
        override fun test(t: SelectableItem) =
            t.selectedApi.superClassApiVariants.bits and inclusionMask != 0
    }

    /**
     * Return [ApiFilters] for generating stubs for [apiSurface].
     *
     * Stubs must include the whole API surface (both base and extended surfaces, such as public API
     * when generating system stubs) so code compiling against stubs can resolve all referenced and
     * inherited APIs.
     * - [ApiFilters.reference]: matches items across the whole API surface (including doc-only APIs
     *   if [includeDocOnly] is true).
     * - [ApiFilters.emit]: matches items marked for emission across the whole API surface.
     * - [ApiFilters.traversal]: uses the same predicate as [ApiFilters.emit]. Because stubs include
     *   the whole API surface rather than a delta, any package or class containing an emitted item
     *   is also part of the whole API surface and matches [ApiFilters.emit]. Setting this
     *   explicitly enables `ApiVisitor` to filter items during traversal instead of creating
     *   `VisitCandidate`s.
     */
    fun forStubs(
        apiSurface: ApiSurface,
        includeDocOnly: Boolean,
    ): ApiFilters {
        val variantTypes =
            if (includeDocOnly) ApiType.CORE_PLUS_DOC_ONLY.emitVariantTypes
            else ApiType.CORE.emitVariantTypes
        val filterReference =
            wholeApiForVariants(
                apiSurface,
                variantTypes,
            )
        val filterEmit =
            // Only emit stubs for items marked for emission.
            andPredicates(
                EmittedOnlyPredicate,
                wholeApiForVariants(
                    apiSurface,
                    variantTypes,
                ),
            )

        return ApiFilters(
            // Because stubs include the whole API surface rather than a delta, any container of an
            // emitted item is also emitted, so `filterEmit` can be used directly for traversal.
            // Setting `traversal` explicitly enables `ApiVisitor` to filter items via `skip`
            // instead of creating `VisitCandidate`s.
            traversal = filterEmit,
            reference = filterReference,
            emit = filterEmit,
        )
    }

    /**
     * Return a [FilterPredicate] for matching only that part of the whole API that belongs to the
     * [apiSurface] delta.
     *
     * If [apiType] is [ApiType.REMOVED] then it will only match variants of type
     * [ApiVariantType.REMOVED] else it will only match variants of type [ApiVariantType.CORE].
     *
     * Unlike [forStubs], this only matches items in [apiSurface] itself, not any surface that it
     * extends. In addition to matching items that directly belong to [apiSurface], it also matches
     * classes that inherit variants from a super class belonging to [apiSurface] via
     * [SelectedApi.superClassApiVariants]. Currently, this only works with subclasses of
     * [ApiVisitor] as it relies on its support for visiting classes that either match the predicate
     * or where one of its members does.
     *
     * TODO(b/512093496): Make it work with ApiSurfaceVisitor.
     */
    fun forDelta(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): FilterPredicate {
        val variantTypes = apiType.emitVariantTypes
        val apiSurfaces = apiSurface.surfaces
        val inclusionMask =
            computeVariantBitMask(
                apiSurfaces,
                setOf(apiSurface),
                variantTypes,
            )

        return orPredicates(
            ItemApiVariantsPredicate(apiSurfaces, inclusionMask),
            SuperClassApiVariantsPredicate(apiSurfaces, inclusionMask),
        )
    }

    /**
     * Return [ApiFilters] for traversing and analyzing items belonging to the [apiSurface] delta
     * for the given [apiType].
     *
     * The returned filters:
     * - [ApiFilters.reference]: matches types referenced across the entire API surface hierarchy
     *   (including base surfaces that [apiSurface] extends).
     * - [ApiFilters.emit]: matches items that belong to the [apiSurface] delta and are marked for
     *   emission (without eliding method overrides).
     * - [ApiFilters.traversal]: matches items in the delta, classes containing delta members (via
     *   [SelectedApi.contentApiVariants]), and classes extending delta classes (via
     *   [SelectedApi.superClassApiVariants]), allowing visitors to traverse into base classes that
     *   contain delta members while skipping unrelated items.
     */
    fun forSurfaceFilters(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): ApiFilters {
        // Items in this API surface can reference types or paired methods across the whole API
        // surface hierarchy (including base surfaces that this surface extends).
        val reference = referenceFilter(apiType, apiSurface)

        // Create a mask matching the specific variant for this API surface delta.
        val variantTypes = apiType.emitVariantTypes
        val emitMask = computeVariantBitMask(apiSurface.surfaces, listOf(apiSurface), variantTypes)

        // Emitted items must belong to this delta (or have a superclass in the delta) and be
        // marked for emission.
        val emit = nonElidingFilter(apiType, apiSurface)

        // Traversal includes items in the delta as well as base classes whose contents belong to
        // the delta (via contentApiVariants) so that visitors can visit delta members within
        // base classes.
        val apiSurfaces = apiSurface.surfaces
        val traversalPredicate =
            orPredicates(
                ItemApiVariantsPredicate(apiSurfaces, emitMask),
                ContentApiVariantsPredicate(apiSurfaces, emitMask),
                SuperClassApiVariantsPredicate(apiSurfaces, emitMask),
            )
        val traversal =
            andPredicates(
                EmittedOnlyPredicate,
                traversalPredicate,
            )
        return ApiFilters(
            reference = reference,
            emit = emit,
            traversal = traversal,
        )
    }

    /**
     * Return a [FilterPredicate] that matches any item that is NOT an elidable override in the
     * specified [apiSurface] delta.
     *
     * If [apiType] is [ApiType.REMOVED] then it checks against variants of type
     * [ApiVariantType.REMOVED] else it checks against variants of type [ApiVariantType.CORE].
     */
    fun elidingFilter(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): FilterPredicate {
        val variantTypes = apiType.emitVariantTypes
        val mask = computeVariantBitMask(apiSurface.surfaces, listOf(apiSurface), variantTypes)

        return NotElidablePredicate(apiSurface.surfaces, mask)
    }

    /**
     * A [FilterPredicate] that matches an item if it is not an elidable override matching [mask]
     * via [SelectedApi.elidableApiVariants].
     */
    private class NotElidablePredicate(
        apiSurfaces: ApiSurfaces,
        mask: Int,
    ) : ApiVariantsPredicate(apiSurfaces, mask) {
        override fun test(t: SelectableItem): Boolean =
            t.selectedApi.elidableApiVariants.bits and inclusionMask == 0
    }

    /**
     * Return a [FilterPredicate] that matches items belonging to the [apiSurface] delta for the
     * given [apiType] and marked for emission.
     *
     * Does not elide matching method overrides.
     */
    fun nonElidingFilter(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): FilterPredicate =
        // Only items marked for emission should appear in the signature file.
        andPredicates(
            EmittedOnlyPredicate,
            forDelta(
                apiType = apiType,
                apiSurface = apiSurface,
            ),
        )

    /**
     * Return a [FilterPredicate] for emitting API items for the given [apiType] using the
     * configuration for [apiSurface].
     *
     * Unlike [nonElidingFilter], this will elide method overrides that match the overridden method.
     */
    fun emitFilter(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): FilterPredicate {
        val nonElidingFilter =
            nonElidingFilter(
                apiType,
                apiSurface,
            )
        return andPredicates(
            nonElidingFilter,
            elidingFilter(apiType, apiSurface),
        )
    }

    /**
     * Return a [FilterPredicate] matching types that can be referenced by APIs of the given
     * [apiType] across [apiSurface] and any surface that it extends.
     */
    fun referenceFilter(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ): FilterPredicate = wholeApiForVariants(apiSurface, apiType.referenceVariantTypes)

    /**
     * Return the [ApiFilters] for [apiType] using information from [apiSurface] to customize their
     * behavior.
     *
     * The returned [ApiFilters.emit] will elide method overrides that match the overridden method.
     */
    fun apiFilters(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ) =
        ApiFilters(
            reference = referenceFilter(apiType, apiSurface),
            emit = emitFilter(apiType, apiSurface),
        )

    /**
     * Return the [ApiFilters] for [apiType] using information from [apiSurface] to customize their
     * behavior.
     *
     * The returned [ApiFilters.emit] will NOT elide method overrides that match the overridden
     * method.
     */
    fun nonElidingApiFilters(
        apiType: ApiType,
        apiSurface: ApiSurface,
    ) =
        ApiFilters(
            reference = referenceFilter(apiType, apiSurface),
            emit =
                nonElidingFilter(
                    apiType,
                    apiSurface,
                ),
        )

    /**
     * Compute the bitmask for the [ApiVariantSet] containing the [variantTypes] of each
     * [ApiSurface] in [variantSurfaces].
     */
    private fun computeVariantBitMask(
        apiSurfaces: ApiSurfaces,
        variantSurfaces: Collection<ApiSurface>,
        variantTypes: List<ApiVariantType>,
    ): Int {
        val variants = buildList {
            for (surface in variantSurfaces) {
                for (variantType in variantTypes) {
                    add(surface.variantFor(variantType))
                }
            }
        }

        return apiSurfaces.createVariantSet(variants).bits
    }
}
