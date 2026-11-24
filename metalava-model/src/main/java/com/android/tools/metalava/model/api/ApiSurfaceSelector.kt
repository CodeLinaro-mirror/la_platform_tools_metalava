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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfaces

/** Helps determine to which api surface a [SelectableItem] belongs. */
class ApiSurfaceSelector(
    apiSurfaceRules: ApiSurfaceRules = ApiSurfaceRules.DEFAULT,
    val addAdditionalOverrides: Boolean = false,
) {
    /** True if unannotated items should be included in the main [ApiSurface]. */
    val showUnannotated: Boolean

    /** True if this has any annotations that can hide a [SelectableItem] from the public API. */
    val hasAnyHideAnnotations: Boolean

    /**
     * Associates an annotation pattern, e.g. `--show-annotation android.annotation.TestApi` with
     * its [SurfaceAnnotationData].
     */
    internal val matcher: AnnotationMatcher<SurfaceAnnotationData>

    /**
     * The optional [ApiSurface] that contains any items that are not annotated or contained within
     * another item that is annotated with a surface annotation.
     */
    val unannotatedApiSurface: ApiSurface?

    init {
        var unannotatedSurface: ApiSurface? = null
        var hasHideAnnotations = false

        val matcherRules = buildList {
            fun addMatcherRule(
                surface: ApiSurface?,
                annotated: SelectAnnotated,
            ) {
                add(
                    AnnotationMatcher.Rule(
                        annotated.annotationPattern,
                        SurfaceAnnotationData(
                            surface,
                            annotated.effect,
                            annotated.recursive,
                        )
                    )
                )
            }
            val apiSurfaces = apiSurfaceRules.apiSurfaces

            val main = apiSurfaces.main
            val all = apiSurfaces.all
            val narrowest = all.first()

            for (surface in all) {
                val isNarrowest = surface === narrowest
                val rules = apiSurfaceRules[surface.name] ?: continue
                for (rule in rules) {
                    if (rule is SelectUnannotated) {
                        require(isNarrowest) {
                            "unannotated rule is only allowed on narrowest surface $narrowest but was found on $surface"
                        }
                        unannotatedSurface = surface
                    } else if (rule is SelectAnnotated) {
                        val effect = rule.effect
                        if (effect == Effect.HIDE) {
                            require(isNarrowest) {
                                "hide rules are only allowed on narrowest surface $narrowest but $rule was found on $surface"
                            }
                            hasHideAnnotations = true
                            addMatcherRule(surface, rule)
                        } else if (effect == Effect.SHOW) {
                            if (surface.isMain) {
                                if (rule.recursive) {
                                    addMatcherRule(surface, rule)
                                } else {
                                    addMatcherRule(surface, rule)
                                }
                            } else {
                                // TODO(b/508331653): Remove this restriction which only exists due
                                //  to limitations in the Showability mechanism. Once that has been
                                //  replaced with the ApiSurface mechanism it should be possible to
                                //  remove this.
                                require(rule.recursive) {
                                    "non-recursive rules are only allowed on main surface $main but was found on $surface"
                                }
                                addMatcherRule(surface, rule)
                            }
                        } else {
                            error("Unsupported effect $effect in surface $rule")
                        }
                    }
                }
            }

            // Register variant rules (e.g. doc-only) which are not bound to a specific API surface
            // but affect visibility variants of the items.
            for (rule in apiSurfaceRules.variantRules) {
                if (rule !is SelectAnnotated) {
                    error("$rule is not a SelectAnnotated")
                }

                when (val effect = rule.effect) {
                    Effect.DOC_ONLY -> {}
                    Effect.REMOVED -> {}
                    else -> {
                        error("Unsupported effect $effect in variant $rule")
                    }
                }

                // Variant type rules are orthogonal to rules that determine whether an item is in
                // a specific surface or its showability so provide null for both.
                addMatcherRule(surface = null, rule)
            }
        }

        // Sort the rules.
        val sortedRules = matcherRules.sortedWith(comparator)

        matcher = AnnotationMatcher.createFromRules(sortedRules)

        // showUnannotated only affects the main surface.
        showUnannotated = unannotatedSurface?.isMain == true

        hasAnyHideAnnotations = hasHideAnnotations
        unannotatedApiSurface = unannotatedSurface
    }

    /** The qualified names of all annotations that can affect API surface selection. */
    val annotationNames = matcher.annotationNames

    /** The [ApiSurfaces] this selects between. */
    val apiSurfaces = apiSurfaceRules.apiSurfaces

    /**
     * Compute the [SurfaceAnnotationData] for [annotationItem], returns `null` if [annotationItem]
     * does not affect API selection.
     */
    fun findSurfaceAnnotationData(annotationItem: AnnotationItem) =
        matcher.matchResult(annotationItem)

    companion object {
        /**
         * Comparator that sorts [AnnotationMatcher.Rule] by
         * [AnnotationMatcher.Rule.annotationPattern].
         */
        private val patternComparator: Comparator<AnnotationMatcher.Rule<SurfaceAnnotationData>> =
            Comparator.comparing { it.annotationPattern }

        /**
         * Comparator that sorts [AnnotationMatcher.Rule] by [Effect] then reverse order of
         * [patternComparator]
         */
        private val comparator =
            // First sort by [Effect] so that SHOW comes before HIDE because SHOW overrides HIDE.
            Comparator.comparing<AnnotationMatcher.Rule<SurfaceAnnotationData>, Effect> {
                    it.result.effect
                }
                // Then make sure that a rule that matches the main surface comes before any other
                // surface.
                .thenComparing { it.result.surface?.isMain != true }
                // Then make sure that recursive rules come before non-recursive rules.
                .thenComparing { !it.result.recursive }
                // Finally, sort from longest (most specific pattern) to shortest. That is because a
                // longer more specific pattern should be matched before a shorter, less specific
                // one.
                .thenDescending(patternComparator)

        /** Default instance of an [ApiSurfaceSelector]. */
        internal val DEFAULT = ApiSurfaceSelector()
    }
}

/**
 * Associates a list of [SurfaceSelectionRule]s with each [ApiSurface] in [ApiSurfaces.all].
 *
 * @property variantRules variant rules (such as doc-only and removed) that are applicable across
 *   all surfaces.
 */
class ApiSurfaceRules(
    val apiSurfaces: ApiSurfaces,
    private val byName: Map<String, List<SurfaceSelectionRule>>,
    internal val variantRules: List<SurfaceSelectionRule> = emptyList(),
) {
    operator fun get(surfaceName: String) = byName[surfaceName]

    override fun toString() = buildString {
        append("ApiSurfaceRules(\n")
        for ((surface, rules) in byName) {
            append("    ")
            append(surface)
            append(" -> {\n")
            for (rule in rules) {
                append("        ")
                append(rule)
                append("\n")
            }
            append("    }\n")
        }
        append(")")
    }

    /**
     * Retarget these rules at [surface].
     *
     * This takes this set of rules and then rewrites them to target [surface] which must be one of
     * the surfaces in [apiSurfaces]. That involves dropping any surfaces that extend [surface] and
     * treating any of their rules as [Effect.HIDE].
     *
     * Useful for testing as a single set of [apiSurfaces] and rules [byName] can be used for
     * multiple tests targeting different surfaces.
     */
    fun retargetAt(surface: String): ApiSurfaceRules {
        val selectedSurface = apiSurfaces.byName[surface]!!
        val subSurfaces =
            ApiSurfaces.build {
                val subset = selectedSurface.includedSurfaces
                for (s in subset) {
                    createSurface(
                        s.name,
                        extends = s.extends?.name,
                        isMain = s === selectedSurface,
                    )
                }
            }

        val extraHideRules =
            byName.flatMap { (name, rules) ->
                if (name in subSurfaces.byName) emptyList()
                else
                    rules.filterIsInstance<SelectAnnotated>().map { rule ->
                        SurfaceSelectionRule.createAnnotationRule(
                            rule.annotationPattern,
                            effect = Effect.HIDE,
                            rule.recursive
                        )
                    }
            }

        val subsetRules =
            byName
                .mapNotNull { (name, rules) ->
                    val surface = subSurfaces.byName[name]
                    if (surface == null) {
                        null
                    } else if (surface.extends == null) {
                        name to rules + extraHideRules
                    } else {
                        name to rules
                    }
                }
                .toMap()

        return ApiSurfaceRules(subSurfaces, subsetRules, variantRules)
    }

    companion object {
        /** Rules with "base" and "main" surfaces. */
        private val ruleByNameWithBase =
            mapOf(
                "base" to listOf(SurfaceSelectionRule.unannotated),
                "main" to listOf(SurfaceSelectionRule.createAnnotationRule("test.api.MainApi")),
            )

        /** Rules with only a "main" surface. */
        private val ruleByNameWithoutBase =
            mapOf(
                "main" to listOf(SurfaceSelectionRule.unannotated),
            )

        val DEFAULT = create(needsBase = false)

        /**
         * Create an [ApiSurfaceRules] with a "main" surface and, depending on [needsBase] as "base"
         * surface too.
         */
        fun create(needsBase: Boolean) =
            ApiSurfaceRules(
                apiSurfaces = ApiSurfaces.create(needsBase = needsBase),
                byName =
                    if (needsBase) {
                        ruleByNameWithBase
                    } else {
                        ruleByNameWithoutBase
                    }
            )
    }
}

/** A rule for selecting items to belong to an [ApiSurface]. */
sealed interface SurfaceSelectionRule {
    /** The effect that a [SurfaceSelectionRule] created by [createAnnotationRule] will have. */
    enum class Effect {
        /** The annotated item will be included in the [ApiSurface]. */
        SHOW,

        /**
         * The annotated item will be excluded from the [ApiSurface].
         *
         * This must only be used on the narrowest [ApiSurface].
         */
        HIDE,

        /** The annotated item will be included in doc-only stubs, but omitted from normal stubs. */
        DOC_ONLY,

        /** The annotated item will be considered removed. */
        REMOVED,
    }

    companion object {
        /**
         * Include unannotated items.
         *
         * Must only be specified on the narrowest [ApiSurface].
         */
        val unannotated: SurfaceSelectionRule = SelectUnannotated()

        /**
         * Create a rule that will show/hide items that are annotated with an annotation that
         * matches [annotationPattern].
         *
         * @param annotationPattern the pattern that the annotation must match.
         * @param effect the effect of this rule on the annotated item.
         * @param recursive if `true` then enclosed items will also be included in the [ApiSurface].
         */
        fun createAnnotationRule(
            annotationPattern: String,
            effect: Effect = Effect.SHOW,
            recursive: Boolean = true,
        ): SurfaceSelectionRule = SelectAnnotated(annotationPattern, effect, recursive)
    }
}

/**
 * A [SurfaceSelectionRule] that will include unannotated items in the [ApiSurface].
 *
 * Must only be specified on the narrowest [ApiSurface].
 */
private class SelectUnannotated : SurfaceSelectionRule {
    override fun toString() = "SelectUnannotated"
}

/**
 * A [SurfaceSelectionRule] that will include/exclude annotated items in/from the [ApiSurface].
 *
 * @param annotationPattern the pattern that the annotation must match.
 * @param effect the effect of this rule on the annotated item.
 * @param recursive if `true` then enclosed items will also be included in the [ApiSurface].
 */
private data class SelectAnnotated(
    val annotationPattern: String,
    val effect: Effect,
    val recursive: Boolean,
) : SurfaceSelectionRule

/**
 * Information related to a surface [AnnotationItem], i.e. an annotation that affects the
 * [ApiSurface], if any, to which an item belongs.
 */
data class SurfaceAnnotationData(
    /** The [ApiSurface] to which the annotation applies, if any. */
    val surface: ApiSurface?,

    /** The [Effect] that this annotation has on the annotated item. */
    val effect: Effect,

    /**
     * True if [effect] applies to the contents of the annotated item, false if it only applies to
     * the item itself.
     */
    val recursive: Boolean,
) {
    /**
     * The [ApiSurface], if any, to which an annotated item will belong, ignoring the effects of
     * flags.
     */
    val showSurface = surface.takeIf { effect == Effect.SHOW }
}
