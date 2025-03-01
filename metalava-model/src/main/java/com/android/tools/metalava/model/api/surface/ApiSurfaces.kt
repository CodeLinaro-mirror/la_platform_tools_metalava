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

/** The configured set of [ApiSurface]s. */
sealed interface ApiSurfaces {
    /**
     * The list of all [ApiSurface]s.
     *
     * If [base] is set then it comes first; [main] is always last.
     */
    val all: List<ApiSurface>

    /** The list of all possible [ApiVariant]s. */
    val variants: List<ApiVariant>

    /** The main [ApiSurface]. */
    val main: ApiSurface

    /** The optional base [ApiSurface]. */
    val base: ApiSurface?

    /** An immutable, empty set of variants. */
    val emptyVariantSet: ApiVariantSet

    companion object {
        /**
         * Create an [ApiSurfaces] instance.
         *
         * @param needsBase if `false` (the default) then the returned [ApiSurfaces.base] property
         *   is null, otherwise it is an [ApiSurface] that the [ApiSurfaces.main] references in its
         *   [ApiSurface.extends] property.
         */
        fun create(needsBase: Boolean = false): ApiSurfaces = DefaultApiSurfaces(needsBase)

        /**
         * A default set of [ApiSurface]s.
         *
         * Includes [main] but not [base].
         */
        val DEFAULT = create()
    }

    /** Default implementation of [ApiSurfaces]. */
    private class DefaultApiSurfaces(needsBase: Boolean) : ApiSurfaces {

        override val all: List<DefaultApiSurface>

        override val base: DefaultApiSurface?

        override val main: DefaultApiSurface

        override val variants: List<ApiVariant>

        init {
            val surfaceList = mutableListOf<DefaultApiSurface>()

            // The list of all ApiVariants belonging to this. Will be populated in the
            // DefaultApiSurface initializer.
            val allVariants = mutableListOf<ApiVariant>()

            /**
             * Create an [ApiSurface] with the specified [name] which has an optional [extends].
             *
             * Adds the created [ApiSurface] to [all].
             */
            fun createSurface(name: String, extends: DefaultApiSurface?) =
                DefaultApiSurface(
                        surfaces = this,
                        name = name,
                        extends = extends,
                        allVariants = allVariants,
                    )
                    .also { surfaceList.add(it) }

            base =
                if (needsBase)
                    createSurface(
                        "base",
                        extends = null,
                    )
                else null

            main =
                createSurface(
                    "main",
                    extends = base,
                )

            all = surfaceList.toList()
            variants = allVariants.toList()
        }

        override val emptyVariantSet: ApiVariantSet = ApiVariantSet.emptySet(this)
    }

    /**
     * Default implementation of [ApiSurface].
     *
     * @param allVariants the list of all [ApiVariant]s belonging to [surfaces]. This must be
     *   initialised with all the [ApiVariant]s belonging to this [ApiSurface].
     */
    private class DefaultApiSurface(
        override val surfaces: ApiSurfaces,
        override val name: String,
        override val extends: DefaultApiSurface?,
        allVariants: MutableList<ApiVariant>,
    ) : ApiSurface {

        /**
         * Create a list of [ApiVariant]s for this surface, one for each [ApiVariantType]. Each
         * [ApiVariant] will add themselves to the `allVariants` list that contains all the
         * [ApiVariant]s belong to [surfaces].
         */
        override val variants =
            ApiVariantType.entries.map { type -> ApiVariant(this, type, allVariants) }

        override val variantSet =
            // Create an ApiVariantSet that contains all ApiVariants in this surface.
            ApiVariantSet.build(surfaces) {
                for (variant in variants) {
                    add(variant)
                }
            }

        override fun variantFor(type: ApiVariantType): ApiVariant {
            return variants[type.ordinal]
        }

        override val isMain = name == "main"

        override fun toString(): String = "ApiSurface($name)"
    }
}
