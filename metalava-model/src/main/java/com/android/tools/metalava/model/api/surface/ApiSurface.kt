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

package com.android.tools.metalava.model.api.surface

/**
 * A specific API surface.
 *
 * Their natural ordering is determined by the order in which they were created in [surfaces].
 */
sealed class ApiSurface : Comparable<ApiSurface> {
    /** The set of [ApiSurface]s to which this belongs. */
    abstract val surfaces: ApiSurfaces

    /** The name of the surface. */
    abstract val name: String

    /** The optional [ApiSurface] that this extends. */
    abstract val extends: ApiSurface?

    /** True if this is the main [ApiSurface] being generated. */
    abstract val isMain: Boolean

    /** The list of [ApiVariant]s, in the same order as [ApiVariantType]s. */
    abstract val variants: List<ApiVariant>

    /** The set of all [ApiVariant]s in this [ApiSurface]. */
    abstract val variantSet: ApiVariantSet

    /**
     * The default [ApiVariant]s that will be included in this surface.
     *
     * @see ApiVariantType.isDefault
     */
    abstract val defaultVariantSet: ApiVariantSet

    /**
     * The set of all [ApiSurface]s narrower than this one, i.e. the set of all [ApiSurface]s that
     * this one extends, either directly or indirectly.
     *
     * Does not include this [ApiSurface].
     */
    abstract val narrowerSurfaces: Set<ApiSurface>

    /**
     * The set of all [ApiSurface]s included in this one, including this one.
     *
     * Is basically [narrowerSurfaces] + this.
     */
    abstract val includedSurfaces: Set<ApiSurface>

    /**
     * The index of the bit within [ApiVariantSet.bits] that contains the first [ApiVariant] in
     * [variants].
     */
    internal abstract val variantStartBitIndex: Int

    /** Get the [ApiVariant] for [ApiVariantType] in this [ApiSurface]. */
    abstract fun variantFor(type: ApiVariantType): ApiVariant
}
