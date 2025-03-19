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

package com.android.tools.metalava.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/** The top level configuration object. */
@JacksonXmlRootElement(localName = "config", namespace = CONFIG_NAMESPACE)
// Ignore the xsi:schemaLocation property if present on the root <config> element.
@JsonIgnoreProperties("schemaLocation")
data class Config(
    @field:JacksonXmlProperty(localName = "api-surfaces", namespace = CONFIG_NAMESPACE)
    val apiSurfaces: ApiSurfacesConfig? = null,
) {
    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    internal fun validate() {
        apiSurfaces?.validate()
    }
}

// Neither Kotlin nor Java has an interface for an ordered collection of unique elements, i.e. an
// ordered set. However, the standard Kotlin [Set] and [MutableSet] as returned by [setOf],
// [buildSet], [mutableSetOf], as well as various `.toSet()` methods all return an implementation
// that does maintain order, e.g. [LinkedHashSet].
//
// See https://discuss.kotlinlang.org/t/ordered-sets/5420.

/**
 * A [Set] that should be used when order is important.
 *
 * As [Set] does not provide any order guarantees use of this must be tested to ensure that
 * iteration order is maintained.
 */
typealias OrderedSet<E> = Set<E>

/**
 * A [MutableSet] that should be used when order is important.
 *
 * As [MutableSet] does not provide any order guarantees use of this must be tested to ensure that
 * iteration order is maintained.
 */
typealias MutableOrderedSet<E> = MutableSet<E>

/** A set of [ApiSurfaceConfig]s. */
data class ApiSurfacesConfig(
    @field:JacksonXmlProperty(localName = "api-surface", namespace = CONFIG_NAMESPACE)
    val apiSurfaceList: List<ApiSurfaceConfig> = emptyList(),
) {
    /**
     * Map of [ApiSurfaceConfig]s by [ApiSurfaceConfig.name].
     *
     * Groups them by name, throws an exception if there are two surfaces with the same name.
     */
    @get:JsonIgnore
    val byName by
        lazy(LazyThreadSafetyMode.NONE) {
            apiSurfaceList
                .groupingBy { it.name }
                .reduce { name, surface1, surface2 ->
                    error("Found duplicate surfaces called `$name`")
                }
        }

    /**
     * Get the [ApiSurfaceConfig] by [name].
     *
     * If no such config exists then raise an error include [reason].
     */
    inline fun getByNameOrError(name: String, reason: (String) -> String) =
        byName[name]
            ?: error("${reason(name)}, expected one of ${byName.keys.joinToString {"`$it`"}}")

    /**
     * Ordered set of [ApiSurfaceConfig]s that maintains the order from the configuration except
     * that an [ApiSurfaceConfig] that extends another [ApiSurfaceConfig] always comes after the one
     * it extends.
     */
    @get:JsonIgnore
    internal val orderedSurfaces: OrderedSet<ApiSurfaceConfig> by
        lazy(LazyThreadSafetyMode.NONE) {
            buildSet {
                for (apiSurfaceConfig in apiSurfaceList) {
                    apiSurfaceConfig.flatten(this, mutableSetOf())
                }
            }
        }

    /**
     * Get the ordered set of [ApiSurfaceConfig]s that contribute to the [targetSurface].
     *
     * A surface that contributes to [targetSurface] is one which is extended (possibly indirectly)
     * by [targetSurface] or [targetSurface] itself.
     */
    fun contributesTo(targetSurface: ApiSurfaceConfig): Set<ApiSurfaceConfig> {
        return buildSet { targetSurface.flatten(this, mutableSetOf()) }
    }

    /**
     * Flatten the [ApiSurfaceConfig.extends] hierarchy of this [ApiSurfaceConfig], if any.
     *
     * If this has a non-null [ApiSurfaceConfig.extends] then this will be called on the
     * [ApiSurfaceConfig] it references and then this will be added to [flattened].
     *
     * @param flattened the ordered set of [ApiSurfaceConfig]s, such that each [ApiSurfaceConfig]
     *   appears after any [ApiSurfaceConfig] that it [ApiSurfaceConfig.extends]. Any
     *   [ApiSurfaceConfig] in this list is guaranteed not to be part of a cycle as it will only
     *   have been added after checking for cycles.
     * @param visited the ordered set of names of [ApiSurfaceConfig] that have already been visited
     *   while flattening an [ApiSurfaceConfig] that extends (possibly indirectly) this one. Used to
     *   detect cycles.
     */
    private fun ApiSurfaceConfig.flatten(
        flattened: MutableOrderedSet<ApiSurfaceConfig>,
        visited: MutableSet<String>
    ) {
        // If this has already been added then it is not part of a cycle as it will only have been
        // added after checking for cycles so there is nothing to do.
        if (this in flattened) return

        // If this has already been visited while visiting a surface that extends (possibly
        // indirectly) this one then there is a cycle in the graph.
        if (name in visited) {
            error(
                "Cycle detected in extends relationship: ${visited.joinToString(" -> ") {"`$it`"}} -> `$name`."
            )
        }

        // Remember this has been visited before visiting a surface this extends.
        visited += name

        // If this extends another surface then resolve it and flatten it first.
        if (extends != null) {
            val extendedSurface =
                getByNameOrError(extends) {
                    // This should not occur outside tests as the schema should ensure that
                    // `extends` always references an actual surface but throw a meaningful error
                    // anyway, just in case.
                    "Surface `$name` extends an unknown surface `$it`"
                }
            extendedSurface.flatten(flattened, visited)
        }

        // Finally, add this to the set. At this point it is guaranteed not to be part of a cycle
        // as that will have been detected above.
        flattened += this
    }

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {
        // Force check for duplicates.
        byName

        // Force check for cycles.
        orderedSurfaces
    }
}

/** An API surface that Metalava could generate. */
data class ApiSurfaceConfig(
    /** The name of the API surface, e.g. `public`, `restricted`, etc. */
    @field:JacksonXmlProperty(isAttribute = true) val name: String,

    /** The optional name of the API surface that this surface extends, e.g. `public`. */
    @field:JacksonXmlProperty(isAttribute = true) val extends: String? = null,
)
