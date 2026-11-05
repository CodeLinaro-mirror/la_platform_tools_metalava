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
 */
class TargetSurfaceReferenceStrategy(
    private val targetSurface: ApiSurface,
) : ApiReferenceStrategy {

    /** The [ApiSurfaces] instance to which [targetSurface] belongs. */
    private val apiSurfaces = targetSurface.surfaces

    /** The set of [ApiSurface]s included in [targetSurface] (including [targetSurface] itself). */
    private val includedSurfaces = targetSurface.includedSurfaces

    /** Bit mask matching [ApiVariantType.CORE] across all [includedSurfaces]. */
    private val coreMask = computeVariantBitMask(ApiVariantType.CORE)

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

        // Fast path: check if the referenced item belongs to the core API of any included surface.
        val toBits = to.selectedApi.itemApiVariants.bits
        if ((toBits and coreMask) != 0) {
            return ReferenceCheckResult.Valid
        }

        // Otherwise, the referenced item is not part of the target API surface. Distinguish between
        // items that are public/protected (e.g. excluded via @hide) and items that are
        // package-private or private.
        return ReferenceCheckResult.Hidden(
            isPublicOrProtected = to.modifiers.isPublic() || to.modifiers.isProtected()
        )
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
