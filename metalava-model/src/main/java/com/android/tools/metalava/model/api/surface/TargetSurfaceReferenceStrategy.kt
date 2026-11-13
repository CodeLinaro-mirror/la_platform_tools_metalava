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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem

/**
 * An [ApiReferenceStrategy] that checks all references against a fixed [targetSurface] and its
 * [ApiSurface.includedSurfaces].
 *
 * @property targetSurface The [ApiSurface] (and its [ApiSurface.includedSurfaces]) against which
 *   references are checked.
 * @property includeDocOnlyInCode If true, [ApiVariantType.DOC_ONLY] targets are treated as valid
 *   even for [ReferenceKind.CODE] references from [ApiVariantType.CORE] items (e.g. when generating
 *   doc stubs).
 */
class TargetSurfaceReferenceStrategy(
    private val targetSurface: ApiSurface,
    private val includeDocOnlyInCode: Boolean = false,
) : ApiReferenceStrategy {

    /** The [ApiSurfaces] instance to which [targetSurface] belongs. */
    private val apiSurfaces = targetSurface.surfaces

    /** The set of [ApiSurface]s included in [targetSurface] (including [targetSurface] itself). */
    private val includedSurfaces = targetSurface.includedSurfaces

    /** Bit mask matching [ApiVariantType.CORE] across all [includedSurfaces]. */
    private val coreMask = computeVariantBitMask(ApiVariantType.CORE)

    /** Bit mask matching [ApiVariantType.DOC_ONLY] across all [includedSurfaces]. */
    private val docOnlyMask = computeVariantBitMask(ApiVariantType.DOC_ONLY)

    /**
     * Bit mask matching [ApiVariantType.CORE] or [ApiVariantType.DOC_ONLY] across
     * [includedSurfaces].
     */
    private val coreOrDocOnlyMask = coreMask or docOnlyMask

    /**
     * Compute an [Int] bit mask matching all [ApiVariant]s of [variantType] across
     * [includedSurfaces].
     */
    private fun computeVariantBitMask(variantType: ApiVariantType): Int {
        val variants = includedSurfaces.map { it.variantFor(variantType) }
        return apiSurfaces.createVariantSet(variants).bits
    }

    override fun checkReference(
        from: Item?,
        to: SelectableItem,
        kind: ReferenceKind,
    ): ReferenceCheckResult {
        // A reference from an item to one of its containing classes is always valid, even if the
        // containing class is hidden (e.g. reverted by a disabled @FlaggedApi annotation), because
        // an enclosed item cannot exist in the API without its containing classes.
        if (from != null && isContainingClass(from, to)) {
            return ReferenceCheckResult.Valid
        }

        // Determine which variant bit mask the target item must match to be considered a valid
        // reference in this context.
        val allowedMask =
            when (kind) {
                // Documentation references may target either CORE or DOC_ONLY items.
                ReferenceKind.DOCUMENTATION -> coreOrDocOnlyMask
                ReferenceKind.CODE -> {
                    if (includeDocOnlyInCode) {
                        // When `includeDocOnlyInCode` is enabled (such as when generating doc
                        // stubs), code references may target either CORE or DOC_ONLY items
                        // regardless of `from`.
                        coreOrDocOnlyMask
                    } else {
                        // Resolve the enclosing SelectableItem (e.g. a ParameterItem's parent
                        // CallableItem) to inspect its API variants.
                        val fromSelectable = (from as? SelectableItem) ?: from?.parent()
                        val fromBits = fromSelectable?.selectedApi?.itemApiVariants?.bits ?: 0
                        when {
                            // References from CORE items in code may only target CORE items.
                            (fromBits and coreMask) != 0 -> coreMask
                            // References from DOC_ONLY items in code (such as doc stubs) may target
                            // either CORE or DOC_ONLY items.
                            (fromBits and docOnlyMask) != 0 -> coreOrDocOnlyMask
                            // When `from` is null or does not belong to any variant in
                            // `includedSurfaces`, default to requiring a CORE target.
                            else -> coreMask
                        }
                    }
                }
            }

        // Fast path: check if the referenced item matches any allowed variant in includedSurfaces.
        val toBits = to.selectedApi.itemApiVariants.bits
        if ((toBits and allowedMask) != 0) {
            return ReferenceCheckResult.Valid
        }

        // Slow path: classify why the referenced item is not valid in this context.
        return when {
            // The referenced item is @doconly in an included surface (and the referencing context
            // does not allow doconly targets).
            (toBits and docOnlyMask) != 0 -> ReferenceCheckResult.DocOnly
            // Otherwise, the referenced item is not part of the target API surface. Distinguish
            // between items that are public/protected (e.g. excluded via @hide) and items that are
            // package-private or private.
            else ->
                ReferenceCheckResult.Hidden(
                    isPublicOrProtected = to.modifiers.isPublic() || to.modifiers.isProtected()
                )
        }
    }

    /** Return `true` if [to] is one of [from]'s containing classes. */
    private fun isContainingClass(from: Item, to: SelectableItem): Boolean {
        if (to !is ClassItem) return false
        var current = from.containingClass()
        while (current != null) {
            if (current == to) return true
            current = current.containingClass()
        }
        return false
    }
}
