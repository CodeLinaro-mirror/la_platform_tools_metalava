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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.cli.common.splitMultiple
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.config.EffectConfig
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Companion.unannotated
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurface.Contents
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.options.unique
import kotlin.collections.buildList

const val ARG_API_SURFACE = "--api-surface"
const val ARG_SHOW_UNANNOTATED = "--show-unannotated"
const val ARG_SHOW_ANNOTATION = "--show-annotation"

const val ARG_HIDE_ANNOTATION = "--hide-annotation"

const val ARG_EXCLUDE_ANNOTATION = "--exclude-annotation"
const val ARG_PASS_THROUGH_ANNOTATION = "--pass-through-annotation"

const val ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION = "--suppress-compatibility-meta-annotation"

const val ARG_TYPEDEFS_IN_SIGNATURES = "--typedefs-in-signatures"

const val DEPRECATED_API_SURFACE_OPTION_MESSAGE =
    "This option is deprecated. Use a configuration file and --api-surface instead. " +
        "See `metalava help api-surfaces` for details."

/** The name of the group, can be used in help text to refer to the options in this group. */
const val API_SELECTION_OPTIONS_GROUP = "Api Selection"

/**
 * Options related to selecting which parts of the source files will be part of the generated API.
 *
 * @param apiSurfacesConfigProvider Provides the [ApiSurfacesConfig] that was provided in an
 *   [ARG_CONFIG_FILE], if any. This must only be called after all the options have been parsed.
 */
class ApiSelectionOptions(
    private val apiSurfacesConfigProvider: () -> ApiSurfacesConfig? = { null },
) :
    OptionGroup(
        name = API_SELECTION_OPTIONS_GROUP,
        help =
            """
                Options that select which parts of the source files will be part of the generated
                API.
            """
                .trimIndent()
    ) {
    /** The [ApiSurfaceConfig] extracted from configuration files. */
    private val apiSurfacesConfig by lazy(LazyThreadSafetyMode.NONE) { apiSurfacesConfigProvider() }

    /**
     * Return true if at least one `--show*-annotation`, `--show-unannotated` or `--hide-annotation`
     * option was specified.
     */
    private fun atLeastOneApiSelectionOptionWasSpecified() =
        optionalShowUnannotated == ShowUnannotated.SPECIFIED ||
            hideAnnotationValues.isNotEmpty() ||
            atLeastOneShowAnnotationOptionWasSpecified()

    /** Return true if at least one `--show*-annotation` option was specified. */
    private fun atLeastOneShowAnnotationOptionWasSpecified() = showAnnotationValues.isNotEmpty()

    internal val apiSurfaceName by
        option(
            ARG_API_SURFACE,
            metavar = "<surface>",
            help =
                """
                    The API surface currently being generated. Must correspond to an <api-surface>
                    element in a $ARG_CONFIG_FILE.
                """,
        )

    /** The values of [optionalShowUnannotated]. */
    private enum class ShowUnannotated {
        /** Indicates that [ARG_SHOW_UNANNOTATED] was not specified on the command line. */
        UNSPECIFIED,

        /** Indicates that [ARG_SHOW_UNANNOTATED] was specified on the command line. */
        SPECIFIED,
    }

    private val optionalShowUnannotated by
        option(help = "Include un-annotated public APIs in the signature file as well.")
            .switch(ARG_SHOW_UNANNOTATED to ShowUnannotated.SPECIFIED)
            .default(
                ShowUnannotated.UNSPECIFIED,
                defaultForHelp = "true if no --show*-annotation options specified",
            )
            .deprecated(DEPRECATED_API_SURFACE_OPTION_MESSAGE)

    /**
     * Convert [optionalShowUnannotated] into a [Boolean], defaulting to `true` if no show options
     * were specified.
     */
    private val showUnannotatedOption by
        lazy(LazyThreadSafetyMode.NONE) {
            when (optionalShowUnannotated) {
                ShowUnannotated.UNSPECIFIED -> {
                    // If the caller has not explicitly requested that unannotated classes and
                    // members should be shown in the output then only show them if no show
                    // annotations were provided.
                    !atLeastOneShowAnnotationOptionWasSpecified()
                }
                else -> true
            }
        }

    val showUnannotated
        get() = apiSurfaceSelector.showUnannotated

    private val showAnnotationValues by
        option(
                ARG_SHOW_ANNOTATION,
                help =
                    """
                        Unhide any hidden elements that are also annotated with the given
                        annotation.
                    """
                        .trimIndent(),
                metavar = "<annotation-filter>",
            )
            .multiple()
            .deprecated(DEPRECATED_API_SURFACE_OPTION_MESSAGE)

    private val hideAnnotationValues by
        option(
                ARG_HIDE_ANNOTATION,
                help = "Treat any elements annotated with the given annotation as hidden.",
                metavar = "<annotation-filter>",
            )
            .multiple()
            .deprecated(DEPRECATED_API_SURFACE_OPTION_MESSAGE)

    /**
     * Select the [ApiSurfaceRules] to use between the [apiSurfaceRulesFromConfig] and
     * [apiSurfaceRulesFromOptions].
     */
    private fun selectApiSurfaceRulesToUse(
        apiSurfaceRulesFromConfig: ApiSurfaceRules?,
        apiSurfaceRulesFromOptions: ApiSurfaceRules?,
    ): ApiSurfaceRules {
        // If no config was available then just use the one from the options.
        if (apiSurfaceRulesFromConfig == null) {
            return apiSurfaceRulesFromOptions ?: ApiSurfaceRules.DEFAULT
        } else if (apiSurfaceRulesFromOptions == null) {
            return apiSurfaceRulesFromConfig
        }

        // The configuration provided rules take priority as they are better in many ways, i.e.
        // more consistent to specify, simpler, less duplication.
        return apiSurfaceRulesFromConfig
    }

    /** The [ApiSurfaceSelector] that will determine to which API surface an item belongs. */
    internal val apiSurfaceSelector by
        lazy(LazyThreadSafetyMode.NONE) {
            val apiSurfaceRulesFromConfig = createApiSurfaceRulesFromConfig()
            val apiSurfaceRulesFromOptions = createApiSurfaceRulesFromOptions()
            val apiSurfaceRules =
                selectApiSurfaceRulesToUse(
                    apiSurfaceRulesFromConfig,
                    apiSurfaceRulesFromOptions,
                )

            ApiSurfaceSelector(apiSurfaceRules)
        }

    /**
     * Treat this [String] as an annotation pattern that will hide any annotated item from the
     * narrowest API.
     */
    private fun String.toHideRule() =
        SurfaceSelectionRule.createAnnotationRule(
            annotationPattern = this,
            effect = Effect.HIDE,
            recursive = true,
        )

    /**
     * Treat this [String] as an annotation pattern that will include any annotated item as part of
     * the main API.
     *
     * @param recursive if `true` then it will also include any enclosed items, otherwise it will
     *   not.
     */
    private fun String.toShowRule(recursive: Boolean) =
        SurfaceSelectionRule.createAnnotationRule(
            annotationPattern = this,
            effect = Effect.SHOW,
            recursive = recursive,
        )

    /** Create [ApiSurfaceRules] from the [apiSurfacesConfig], if available. */
    internal fun createApiSurfaceRulesFromConfig(): ApiSurfaceRules? {
        // If --api-surface has not been specified then rules cannot be created even if there is
        // configuration.
        if (apiSurfaceName == null) return null

        // If there is no configuration then they cannot be created.
        val surfacesConfig = apiSurfacesConfig ?: return null

        // Get the main surface.
        val main = apiSurfaces.main

        // Compute the actual surfaces to use.
        val actualSurfaces =
            when (main.contents) {
                // Delta uses the full set of api surfaces.
                Contents.DELTA -> apiSurfaces

                // Standalone creates a new set of api surfaces that only contains a single
                // surface.
                Contents.STANDALONE -> ApiSurfaces.build { createSurface(main.name, isMain = true) }
            }

        // Build a map from surface name to the [SurfaceSelectionRule]s that apply to that surface.
        val rulesBySurfaceName = buildMap {
            when (main.contents) {
                Contents.DELTA -> {
                    // Iterate over the surfaces, adding information from the --show-* and
                    // --hide-annotation options.
                    for (surface in apiSurfaces.all) {
                        val surfaceRules = surfacesConfig.createRulesForSurface(surface) ?: continue

                        val name = surface.name
                        put(name, surfaceRules)
                    }
                }
                Contents.STANDALONE -> {
                    // Combine all the rules from each of the API surfaces into a single list which
                    // is used for the standalone API surface.
                    val allSurfaceRules =
                        apiSurfaces.all.flatMap {
                            surfacesConfig.createRulesForSurface(it) ?: emptyList()
                        }
                    val name = main.name
                    put(name, allSurfaceRules)
                }
            }

            // Check to see if any related API surfaces need to be hidden. If so, add them to the
            // narrowest surface.
            val hideRules = surfacesConfig.createHideRulesForRelatedButUntrackedSurfaces(main.name)
            if (hideRules.isNotEmpty()) {
                // Add the hide rules to the narrowest API in the actual surfaces.
                val narrowestName = actualSurfaces.all.first().name
                val existing = this[narrowestName]
                this[narrowestName] =
                    if (existing == null) {
                        hideRules
                    } else {
                        buildList {
                            addAll(existing)
                            addAll(hideRules)
                        }
                    }
            }
        }

        // Parse configured doc-only and removed annotations from the surfaces configuration file
        // and register them as variant rules.
        val variantRules = buildList {
            surfacesConfig.docOnly?.let { docOnly ->
                for (ruleConfig in docOnly.annotationRules) {
                    val annotationRule =
                        SurfaceSelectionRule.createAnnotationRule(
                            ruleConfig.pattern,
                            Effect.DOC_ONLY,
                            recursive = true,
                        )

                    add(annotationRule)
                }
            }

            surfacesConfig.removed?.let { removed ->
                for (ruleConfig in removed.annotationRules) {
                    val annotationRule =
                        SurfaceSelectionRule.createAnnotationRule(
                            ruleConfig.pattern,
                            Effect.REMOVED,
                            recursive = true,
                        )

                    add(annotationRule)
                }
            }
        }

        // If the map is empty then there are no rules so return null.
        if (rulesBySurfaceName.isEmpty()) return null

        // Create and return the rules.
        return ApiSurfaceRules(
            actualSurfaces,
            rulesBySurfaceName,
            variantRules,
        )
    }

    /** Create the [SurfaceSelectionRule]s for [surface] from this [ApiSurfacesConfig]. */
    private fun ApiSurfacesConfig.createRulesForSurface(
        surface: ApiSurface,
    ): List<SurfaceSelectionRule>? {
        val name = surface.name
        val surfaceConfig = byName[name] ?: return null
        val selectionCriteria = surfaceConfig.selectionCriteria

        val surfaceRules = buildList {
            if (selectionCriteria.unannotated == EffectConfig.SHOW) {
                add(unannotated)
            }
            for (annotationRule in selectionCriteria.annotationRules) {
                val effect =
                    when (annotationRule.effect) {
                        EffectConfig.SHOW -> Effect.SHOW
                        EffectConfig.HIDE -> Effect.HIDE
                    }
                add(
                    SurfaceSelectionRule.createAnnotationRule(
                        annotationRule.pattern,
                        effect,
                        annotationRule.recursive
                    )
                )
            }
        }

        return surfaceRules
    }

    /**
     * Add rules to this list that will hide related surfaces that are not currently being tracked.
     */
    private fun ApiSurfacesConfig.createHideRulesForRelatedButUntrackedSurfaces(
        surfaceName: String,
    ): List<SurfaceSelectionRule> {
        // Get the tracked surfaces by name.
        val trackedSurfaces = apiSurfaces.byName

        // Get the set of annotation rules that must be hidden.
        val rulesToHide =
            this
                // Get all API surfaces related to this one.
                .relatedTo(
                    byName[surfaceName] ?: error("Cannot find $surfaceName in ${byName.keys}")
                )
                // Remove any that are being tracked.
                .filter { it.name !in trackedSurfaces }
                // Flatten all the rules.
                .flatMap { it.selectionCriteria.annotationRules }
                // Ignore all non-show annotations.
                .filter { it.effect == EffectConfig.SHOW }
                // Ignore kotlin.PublishedApi as it can only be used on internal APIs and so does
                // not need hiding.
                .filter { rule -> rule.pattern != "kotlin.PublishedApi" }
                // Sort by pattern.
                .sortedBy { it.pattern }
                .toSet()

        // Hide all the rules that need to be hidden.
        return rulesToHide.map { rule ->
            SurfaceSelectionRule.createAnnotationRule(rule.pattern, Effect.HIDE, rule.recursive)
        }
    }

    /** Create [ApiSurfaceRules] from the command line options. */
    internal fun createApiSurfaceRulesFromOptions(): ApiSurfaceRules? {
        if (!atLeastOneApiSelectionOptionWasSpecified()) return null

        val surfaces = apiSurfaces
        val main = surfaces.main
        val rulesBySurfaceName = buildMap {
            // Iterate over the surfaces, adding information from the --show-* and --hide-annotation
            // options.
            for (surface in surfaces.all) {
                val name = surface.name
                val surfaceRules = buildList {
                    // The --show-unannotated and --hide-annotation options only apply to the
                    // narrowest API surface.
                    if (surface.extends == null) {
                        // If --show-unannotated is set (explicitly or implicitly) or this is not
                        // the main surface then include unannotated items in the API. The latter is
                        // there to match the behavior in createApiSurfaces which assumes that if
                        // --show-unannotated is not set that the surface must extend one where it
                        // is set.
                        if (showUnannotatedOption || !surface.isMain) {
                            add(unannotated)
                        }

                        addAll(hideAnnotationValues.map { it.toHideRule() })
                    }

                    if (surface === main) {
                        // The --show-annotation only applies to the main surface.
                        addAll(showAnnotationValues.map { it.toShowRule(recursive = true) })
                    }
                }

                put(name, surfaceRules)
            }
        }

        return ApiSurfaceRules(
            apiSurfaces,
            rulesBySurfaceName,
        )
    }

    /** The set of annotation classes that should be removed from all outputs */
    internal val excludeAnnotations by
        option(
                ARG_EXCLUDE_ANNOTATION,
                metavar = "<annotation-classes>",
                help =
                    """
                A comma separated list of fully qualified names of annotation classes that must be
                stripped from metalava's outputs.
            """
                        .trimIndent(),
            )
            .splitMultiple(",")
            .map { it.toSet() }

    /** The set of annotation classes that should be passed through unchanged */
    internal val passThroughAnnotations by
        option(
                ARG_PASS_THROUGH_ANNOTATION,
                metavar = "<annotation-classes>",
                help =
                    """
                A comma separated list of fully qualified names of annotation classes that must be
                passed through unchanged.
            """
                        .trimIndent(),
            )
            .splitMultiple(",")
            .map { it.toSet() }

    /** Meta-annotations for which annotated APIs should not be checked for compatibility. */
    internal val suppressCompatibilityMetaAnnotations by
        option(
                ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION,
                metavar = "<meta-annotation-class>",
                help =
                    """
                       Suppress compatibility checks for any elements within the scope of an
                       annotation which is itself annotated with the given `meta-annotation-class`.
                    """
                        .trimIndent(),
            )
            .multiple()
            .unique()

    /**
     * How to handle typedef annotations in signature files; corresponds to
     * $ARG_TYPEDEFS_IN_SIGNATURES
     */
    internal val typedefMode by
        enumOption(
            ARG_TYPEDEFS_IN_SIGNATURES,
            help = "Whether to include typedef annotations in signature files.",
            enumValueHelpGetter = { it.help },
            default = TypedefMode.NONE,
            key = { it.optionValue },
        )

    val apiSurfaces by
        lazy(LazyThreadSafetyMode.NONE) {
            if (apiSurfaceName != null && atLeastOneApiSelectionOptionWasSpecified()) {
                cliError(
                    "$ARG_API_SURFACE is mutually exclusive with $ARG_SHOW_UNANNOTATED, $ARG_SHOW_ANNOTATION and $ARG_HIDE_ANNOTATION"
                )
            }

            createApiSurfaces(
                showUnannotatedOption,
                apiSurfaceName,
                apiSurfacesConfig,
            )
        }

    /**
     * Whether API surfaces have been configured via a configuration file (e.g. using the
     * `--api-surface` flag and associated config XML). When true, Javadoc `@hide` block tags are
     * ignored for hiding.
     */
    val apiSurfacesConfigured: Boolean
        get() = apiSurfacesConfig?.apiSurfaceList?.isNotEmpty() == true

    companion object {
        /**
         * Create [ApiSurfaces] and associated [ApiSurface] objects from these options.
         *
         * @param showUnannotated true if unannotated items should be included in the API, false
         *   otherwise.
         * @param targetApiSurfaceName the optional name of the target API surface to be created. If
         *   supplied it MUST reference an [ApiSurfaceConfig] in [apiSurfacesConfig].
         * @param apiSurfacesConfig the optional [ApiSurfacesConfig].
         */
        private fun createApiSurfaces(
            showUnannotated: Boolean,
            targetApiSurfaceName: String?,
            apiSurfacesConfig: ApiSurfacesConfig?,
        ): ApiSurfaces {
            // A base API surface is needed if and only if the main API surface being generated
            // extends another API surface. That is not currently explicitly specified on the
            // command line so has to be inferred from the existing arguments. There are four main
            // supported cases:
            //
            // * Public which does not extend another API surface so does not need a base. This
            //   happens by default unless one or more `--show*annotation` options were specified.
            //   In that case it behaves as if `--show-unannotated` was specified.
            //
            // * Restricted API in AndroidX which is basically public + other and does not need a
            //   base. This happens when `--show-unannotated` was provided (the public part) as well
            //   as `--show-annotation RestrictTo(...)` (the other part).
            //
            // * System delta on public in Android build. This happens when --show-unannotated was
            //   not specified (so the public part is not included in signature files at least) but
            //   `--show-annotation SystemApi` was.
            //
            // * Test API delta on system (or similar) in Android build. This happens when
            //   `--show-unannotated` was not specified (so the public part is not included),
            //   `--show-for-stub-purposes-only SystemApi` was (so system API is included in the
            //   stubs but not the signature files) and `--show-annotation TestApi` was.
            //
            // There are other combinations of the `--show*` options which are not used, and it is
            // not clear whether they make any sense so this does not cover them.
            //
            // This does not need a base if --show-unannotated was specified, or it defaulted to
            // behaving as if it was.
            val needsBase = !showUnannotated

            // If no --api-surface option was provided, then create the ApiSurfaces from the command
            // line options.
            if (targetApiSurfaceName == null) {
                return ApiSurfaces.create(
                    needsBase = needsBase,
                )
            }

            // Otherwise, create it from the configured API surfaces.
            if (apiSurfacesConfig == null || apiSurfacesConfig.apiSurfaceList.isEmpty()) {
                throw MetalavaCliException(
                    "$ARG_API_SURFACE requires at least one <api-surface> to have been configured in a --config-file"
                )
            }

            val targetApiSurfaceConfig =
                apiSurfacesConfig.getByNameOrError(targetApiSurfaceName) {
                    "$ARG_API_SURFACE (`$it`) does not match an <api-surface> in a --config-file"
                }

            // Create the ApiSurfaces from the configured API surfaces.
            return apiSurfacesFromConfig(
                apiSurfacesConfig.contributesTo(targetApiSurfaceConfig),
                targetApiSurfaceName
            )
        }
    }
}

/**
 * Create [ApiSurfaces] from a collection of [ApiSurfaceConfig]s.
 *
 * The [ApiSurfaceConfig]s must be in order such that every [ApiSurfaceConfig] comes before any
 * [ApiSurfaceConfig] that extends it. It must also be complete such that the collection must
 * contain every [ApiSurfaceConfig] that is extended by another in the collection.
 */
internal fun apiSurfacesFromConfig(
    surfaceConfigs: Collection<ApiSurfaceConfig>,
    targetApiSurface: String?
) =
    ApiSurfaces.build {
        // Add ApiSurface instances in order so that surfaces referenced by another (i.e.
        // through `extends`) come before the surfaces that reference them. This ensures
        // that the `extends` can be resolved to an existing `ApiSurface`.
        for (surfaceConfig in surfaceConfigs) {
            createSurface(
                name = surfaceConfig.name,
                extends = surfaceConfig.extends,
                contents = surfaceConfig.contents?.surfaceContents ?: Contents.DELTA,
                isMain = surfaceConfig.name == targetApiSurface,
            )
        }
    }
