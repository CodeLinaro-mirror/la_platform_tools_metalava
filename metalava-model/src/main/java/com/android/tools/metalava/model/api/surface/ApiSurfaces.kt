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
        /** Returns a simple initializer for use with [build]. */
        private fun simpleSurfacesInitializer(needsBase: Boolean): Builder.() -> Unit = {
            val extends =
                if (needsBase) {
                    createSurface(
                        "base",
                        extends = null,
                        isMain = false,
                    )
                    "base"
                } else {
                    null
                }

            createSurface(
                "main",
                extends = extends,
                isMain = true,
            )
        }

        /**
         * Create an [ApiSurfaces] instance.
         *
         * @param needsBase if `false` (the default) then the returned [ApiSurfaces.base] property
         *   is null, otherwise it is an [ApiSurface] that the [ApiSurfaces.main] references in its
         *   [ApiSurface.extends] property.
         */
        fun create(needsBase: Boolean = false): ApiSurfaces =
            build(simpleSurfacesInitializer(needsBase))

        /** Create an [ApiSurfaces] instance using a [Builder]. */
        fun build(initializer: Builder.() -> Unit): ApiSurfaces = DefaultApiSurfaces(initializer)

        /**
         * A default set of [ApiSurface]s.
         *
         * Includes [main] but not [base].
         */
        val DEFAULT = create()
    }

    /**
     * Provides support for creating a more complicated [ApiSurfaces] instance than is supported by
     * [create].
     */
    interface Builder {
        /**
         * Create an [ApiSurface] with the specified [name] which has an optional [extends].
         *
         * If [extends] is not `null` then the referenced [ApiSurface] must already have been
         * created with this method.
         *
         * If the surface is the one to be created then [isMain] must be `true`. Exactly one surface
         * can have [isMain] set to `true`, none or more than one will fail.
         */
        fun createSurface(name: String, extends: String? = null, isMain: Boolean = false)
    }
}

/** Default implementation of [ApiSurfaces]. */
private class DefaultApiSurfaces(initializer: ApiSurfaces.Builder.() -> Unit) : ApiSurfaces {

    override val all: List<DefaultApiSurface>

    override val base: DefaultApiSurface?

    override val main: DefaultApiSurface

    override val variants: List<ApiVariant>

    init {
        // Create a builder for this.
        val builder = BuilderImpl(this)

        // Invoke the initializer on the builder.
        builder.initializer()

        // Populate the fields from the builder.
        main = builder.mainSurface
        base = main.extends

        all = builder.all
        variants = builder.variants
    }

    override val emptyVariantSet: ApiVariantSet = ApiVariantSet.emptySet(this)

    /** Provides support for initializing [apiSurfaces] by implementing [ApiSurfaces.Builder]. */
    private class BuilderImpl(private val apiSurfaces: DefaultApiSurfaces) : ApiSurfaces.Builder {
        /** Map from name to [DefaultApiSurface]. */
        private val nameToSurface = mutableMapOf<String, DefaultApiSurface>()

        /**
         * The list of all ApiVariants belonging to this. Will be populated in the DefaultApiSurface
         * initializer.
         */
        private val allVariants = mutableListOf<ApiVariant>()

        /** Backing property for [mainSurface]. */
        private lateinit var _mainSurface: DefaultApiSurface

        /** Get the main surface, error if none has been set. */
        val mainSurface
            get() =
                if (::_mainSurface.isInitialized) _mainSurface
                else error("No call to createSurface() set isMain to true")

        /** Get the list of all the [DefaultApiSurface]s added to this. */
        val all
            get() = nameToSurface.values.toList()

        /** Get the list of all the [ApiVariant]s of all the [DefaultApiSurface]s. */
        val variants
            get() = allVariants.toList()

        override fun createSurface(name: String, extends: String?, isMain: Boolean) {
            val existing = nameToSurface[name]
            if (existing != null) error("Duplicate surfaces called `$name`")

            val extendsSurface =
                extends?.let {
                    nameToSurface[it]
                        ?: error("Unknown extends surface `$it` referenced from `$name`")
                }

            val surface =
                DefaultApiSurface(
                    apiSurfaces,
                    name,
                    extendsSurface,
                    isMain,
                    allVariants,
                )
            nameToSurface[name] = surface

            if (isMain) {
                if (::_mainSurface.isInitialized)
                    error(
                        "Main surface already set to `${_mainSurface.name}`, cannot set to `$name`"
                    )
                _mainSurface = surface
            }
        }
    }
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
    override val isMain: Boolean,
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

    override fun toString(): String = "ApiSurface($name)"
}
