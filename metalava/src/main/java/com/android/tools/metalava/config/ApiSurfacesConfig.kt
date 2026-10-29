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

package com.android.tools.metalava.config

import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import kotlin.collections.plus

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

    /**
     * Specifies the annotation patterns that determine if an item is a member of
     * [ApiVariantType.DOC_ONLY].
     */
    @field:JacksonXmlProperty(localName = "doc-only", namespace = CONFIG_NAMESPACE)
    val docOnly: ApiVariantTypeRuleConfig? = null,

    /**
     * Specifies the annotation patterns that determine if an item is a member of
     * [ApiVariantType.REMOVED].
     */
    @field:JacksonXmlProperty(localName = "removed", namespace = CONFIG_NAMESPACE)
    val removed: ApiVariantTypeRuleConfig? = null,
) : CombinableConfig<ApiSurfacesConfig> {
    /**
     * Combine with another [ApiSurfacesConfig] by concatenating the [apiSurfaceList]s.
     *
     * Allows for the same surface to be defined in separate files as long as they are identical.
     * This makes it possible to add separate config files that extend the standard API surfaces.
     */
    override fun combineWith(other: ApiSurfacesConfig): ApiSurfacesConfig {
        val combined = apiSurfaceList + other.apiSurfaceList
        val byName =
            combined
                .groupingBy { it.name }
                .reduce { name, surface1, surface2 ->
                    if (surface1 == surface2) {
                        surface1
                    } else {
                        error(
                            buildString {
                                append("Found duplicate surfaces called `")
                                append(name)
                                append("`\n")
                                append("    Definition #1:\n")
                                val indent = "        "
                                append(surface1.toConfigXml(indent))
                                append("\n")
                                append("    Definition #2:\n")
                                append(surface2.toConfigXml(indent))
                            }
                        )
                    }
                }

        return ApiSurfacesConfig(
            byName.values.toList(),
            docOnly = combine(docOnly, other.docOnly),
            removed = combine(removed, other.removed),
        )
    }

    /**
     * Map of [ApiSurfaceConfig]s by [ApiSurfaceConfig.name].
     *
     * Groups them by name, throws an exception if there are two surfaces with the same name. This
     * will only happen if a single file contains duplicate surfaces, which should not be allowed by
     * the config.xsd schema.
     */
    @get:JsonIgnore
    val byName by
        lazy(LazyThreadSafetyMode.NONE) {
            apiSurfaceList
                .groupingBy { it.name }
                .reduce { name, _, _ -> error("Found duplicate surfaces called `$name`") }
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
                    apiSurfaceConfig.flattenExtends(this, mutableSetOf())
                }
            }
        }

    /**
     * Get the ordered set of [ApiSurfaceConfig]s that contribute to the [targetSurface].
     *
     * A surface that contributes to [targetSurface] is one which is extended (possibly indirectly)
     * by [targetSurface] or [targetSurface] itself.
     *
     * This is returned in order from narrowest (i.e. does not extend anything) to [targetSurface].
     */
    fun contributesTo(targetSurface: ApiSurfaceConfig): Set<ApiSurfaceConfig> {
        return buildSet { targetSurface.flattenExtends(this, mutableSetOf()) }
    }

    /**
     * Get the ordered set of [ApiSurfaceConfig]s needed when generating [targetSurface].
     *
     * This walks backwards along [ApiSurfaceConfig.extends] starting from [targetSurface], stopping
     * when it reaches a standalone surface or a root surface.
     *
     * This is returned in order from narrowest to widest [targetSurface].
     */
    fun surfacesFor(targetSurface: ApiSurfaceConfig): Set<ApiSurfaceConfig> {
        val result = mutableListOf<ApiSurfaceConfig>()
        var current: ApiSurfaceConfig? = targetSurface
        while (current != null) {
            result.add(current)
            if (current.contents == ContentsConfig.STANDALONE) {
                break
            }
            current = current.extends?.let { byName[it] }
        }
        return result.asReversed().toSet()
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
    private fun ApiSurfaceConfig.flattenExtends(
        flattened: MutableOrderedSet<ApiSurfaceConfig>,
        visited: MutableSet<String>,
    ) {
        flattenEdges(flattened, visited) { surface ->
            val extends = surface.extends
            if (extends == null) emptyList()
            else {
                val extendedSurface =
                    getByNameOrError(extends) {
                        // This should not occur outside tests as the schema should ensure that
                        // `extends` always references an actual surface but throw a meaningful
                        // error anyway, just in case.
                        "Surface `$name` extends an unknown surface `$it`"
                    }

                listOf(extendedSurface)
            }
        }
    }

    /**
     * The order in which an [ApiSurfaceConfig] must be added relative to any connected
     * [ApiSurfaceConfig].
     */
    private enum class FlattenOrder {
        /** Add the [ApiSurfaceConfig] before flattening any connected [ApiSurfaceConfig]. */
        PRE,

        /** Add the [ApiSurfaceConfig] after flattening any connected [ApiSurfaceConfig]. */
        POST,
    }

    /**
     * Flatten an [ApiSurfaceConfig] hierarchy.
     *
     * The hierarchy is determined by the [edgesProvider] which given an [ApiSurfaceConfig] must
     * return a possibly empty list of reachable [ApiSurfaceConfig].
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
     * @param flattenOrder the relative order in which this [ApiSurfaceConfig] and any
     *   [ApiSurfaceConfig] reachable through an edge will be added.
     * @param edgesProvider provides a list of [ApiSurfaceConfig] reachable from this
     *   [ApiSurfaceConfig].
     */
    private fun ApiSurfaceConfig.flattenEdges(
        flattened: MutableOrderedSet<ApiSurfaceConfig>,
        visited: MutableSet<String>,
        flattenOrder: FlattenOrder = FlattenOrder.POST,
        edgesProvider: (ApiSurfaceConfig) -> List<ApiSurfaceConfig>,
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

        if (flattenOrder == FlattenOrder.PRE) {
            flattened += this
        }

        // Flatten any edges of this surface.
        val edges = edgesProvider(this)
        for (edge in edges) {
            edge.flattenEdges(flattened, visited, flattenOrder, edgesProvider)
        }

        if (flattenOrder == FlattenOrder.POST) {
            flattened += this
        }
    }

    /**
     * Finds all [ApiSurfaceConfig] instances related to [targetSurface].
     *
     * An [ApiSurfaceConfig] is related to another one, if it extends or is extended by it, either
     * directly or indirectly.
     *
     * This is returned in order from narrowest to widest. Where an API surface is extended by
     * multiple surfaces the relative order of those is not defined.
     */
    fun relatedTo(targetSurface: ApiSurfaceConfig): Collection<ApiSurfaceConfig> {
        // Create a map from ApiSurfaceConfig to those ApiSurfaceConfigs that directly extend it.
        val extendedBy =
            orderedSurfaces.filter { it.extends != null }.groupBy { byName[it.extends]!! }

        // Find the narrowest API surface that all the others extend, directly or indirectly.
        val narrowest = contributesTo(targetSurface).first()

        return buildSet {
            narrowest.flattenEdges(
                this,
                mutableSetOf(),
                flattenOrder = FlattenOrder.PRE,
            ) { surface ->
                extendedBy[surface] ?: emptyList()
            }
        }
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
@JacksonXmlRootElement(localName = "api-surface", namespace = CONFIG_NAMESPACE)
data class ApiSurfaceConfig(
    /** The name of the API surface, e.g. `public`, `restricted`, etc. */
    @field:JacksonXmlProperty(isAttribute = true) val name: String,

    /** The optional name of the API surface that this surface extends, e.g. `public`. */
    @field:JacksonXmlProperty(isAttribute = true) val extends: String? = null,

    /**
     * Specifies the contents of this surface.
     *
     * Is only of significance if [extends] is not `null`. It defaults to [ContentsConfig.DELTA] if
     * unspecified.
     */
    @field:JacksonXmlProperty(isAttribute = true) val contents: ContentsConfig? = null,

    /** The selection criteria that determines what is included in this API surface. */
    @field:JacksonXmlProperty(localName = "selection-criteria", namespace = CONFIG_NAMESPACE)
    val selectionCriteria: SelectionCriteriaConfig = SelectionCriteriaConfig(),
)

/** Enumeration of the possible contents of this surface. */
enum class ContentsConfig {
    /** It is a delta on a surface that it extends. */
    DELTA,

    /** It is a standalone surface that includes everything that its extended surfaces contain. */
    STANDALONE,
    ;

    /** Name to use when serializing and deserializing this [ContentsConfig] instance. */
    @JsonValue fun forJackson() = name.lowercase()
}

/** Enumeration of the possible effects that [SelectionCriteriaConfig] may have an on an item. */
enum class EffectConfig {
    /** Include the affected item in the API. */
    SHOW,

    /**
     * Exclude the affected item from the public API; where public is just the narrowest surface.
     */
    HIDE,
    ;

    /** Name to use when serializing and deserializing this [EffectConfig] instance. */
    @JsonValue fun forJackson() = name.lowercase()
}

/**
 * The criteria that determine what belongs in the API surface represented by the referencing
 * [ApiSurfaceConfig].
 */
data class SelectionCriteriaConfig(
    /**
     * Determines what is done with items that are not annotated with one of the annotations in
     * [annotationRules].
     *
     * If unspecified this defaults to [EffectConfig.HIDE].
     */
    @field:JacksonXmlProperty(isAttribute = true) val unannotated: EffectConfig? = null,

    /** Rules that determine what effect an annotation has on its annotated item. */
    @field:JacksonXmlProperty(localName = "annotation-rule", namespace = CONFIG_NAMESPACE)
    val annotationRules: List<AnnotationRuleConfig> = emptyList(),
)

/**
 * A rule that specifies the effect annotations have on annotated items and their enclosed items.
 */
data class AnnotationRuleConfig(
    /** Determines which annotation instances are matched by this rule. */
    @field:JacksonXmlProperty(isAttribute = true) val pattern: String,

    /** The effect that the matching annotation has on its annotated item. */
    @field:JacksonXmlProperty(isAttribute = true) val effect: EffectConfig = EffectConfig.SHOW,

    /** Determines if [effect] also applies to an annotated item's enclosed items or not. */
    @field:JacksonXmlProperty(isAttribute = true) val recursive: Boolean = true,
)

/**
 * Contains patterns that are used to select an API variant type, e.g. [ApiVariantType.DOC_ONLY].
 *
 * The [ApiVariantType] is determined by the [ApiSurfacesConfig] field that references this.
 */
data class ApiVariantTypeRuleConfig(
    /** Rules that determine what effect an annotation has on its annotated item. */
    @field:JacksonXmlProperty(localName = "annotation-rule", namespace = CONFIG_NAMESPACE)
    val annotationRules: List<AnnotationPatternRuleConfig> = emptyList(),
) : CombinableConfig<ApiVariantTypeRuleConfig> {
    /** Combine with another [ApiVariantTypeRuleConfig] by concatenating the [annotationRules]s. */
    override fun combineWith(other: ApiVariantTypeRuleConfig) =
        ApiVariantTypeRuleConfig(annotationRules + other.annotationRules)
}

/**
 * A rule that specifies an annotation pattern.
 *
 * Its effect is determined by the [ApiSurfacesConfig] field that references this.
 */
data class AnnotationPatternRuleConfig(
    /** Determines which annotation instances are matched by this rule. */
    @field:JacksonXmlProperty(isAttribute = true) val pattern: String,
)
