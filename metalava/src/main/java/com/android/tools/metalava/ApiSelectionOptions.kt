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
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.model.annotation.AnnotationFilter
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

const val ARG_API_SURFACE = "--api-surface"
const val ARG_SHOW_UNANNOTATED = "--show-unannotated"
const val ARG_SHOW_ANNOTATION = "--show-annotation"
const val ARG_SHOW_SINGLE_ANNOTATION = "--show-single-annotation"
const val ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION = "--show-for-stub-purposes-annotation"

const val ARG_HIDE_ANNOTATION = "--hide-annotation"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val API_SELECTION_OPTIONS_GROUP = "Api Selection"

/**
 * Options related to selecting which parts of the source files will be part of the generated API.
 *
 * @param apiSurfacesConfigProvider Provides the [ApiSurfacesConfig] that was provided in an
 *   [ARG_CONFIG_FILE], if any. This must only be called after all the options have been parsed.
 * @param checkSurfaceConsistencyProvider Returns `true` if the configured [ApiSurfaces] should be
 *   checked for consistency with the [showUnannotated] property.
 */
class ApiSelectionOptions(
    private val apiSurfacesConfigProvider: () -> ApiSurfacesConfig? = { null },
    private val checkSurfaceConsistencyProvider: () -> Boolean = { true },
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

    private val apiSurface by
        option(
            ARG_API_SURFACE,
            metavar = "<surface>",
            help =
                """
                    The API surface currently being generated. Must correspond to an <api-surface>
                    element in a $ARG_CONFIG_FILE.
                """,
        )

    val showUnannotated by
        option(help = "Include un-annotated public APIs in the signature file as well.")
            .switch(ARG_SHOW_UNANNOTATED to true)
            .defaultLazy(defaultForHelp = "true if no --show*-annotation options specified") {
                // If the caller has not explicitly requested that unannotated classes and members
                // should be shown in the output then only show them if no show annotations were
                // provided.
                allShowAnnotations.isEmpty()
            }

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

    private val showSingleAnnotationValues by
        option(
                ARG_SHOW_SINGLE_ANNOTATION,
                help =
                    """
                        Like $ARG_SHOW_ANNOTATION, but does not apply to members; these must also be
                        explicitly annotated.
                    """
                        .trimIndent(),
                metavar = "<annotation-filter>",
            )
            .multiple()

    private val showForStubPurposesAnnotationValues by
        option(
                ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
                help =
                    """
                        Like $ARG_SHOW_ANNOTATION, but elements annotated with it are assumed to be
                        "implicitly" included in the API surface, and they'll be included in certain
                        kinds of output such as stubs, but not in others, such as the signature file
                        and API lint.
                    """
                        .trimIndent(),
                metavar = "<annotation-filter>",
            )
            .multiple()

    private val hideAnnotationValues by
        option(
                ARG_HIDE_ANNOTATION,
                help = "Treat any elements annotated with the given annotation as hidden.",
                metavar = "<annotation-filter>",
            )
            .multiple()

    /**
     * Whether to include APIs with annotations (intended for documentation purposes). This includes
     * [showAnnotations], [showSingleAnnotations] and [showForStubPurposesAnnotations].
     */
    internal val allShowAnnotations by
        lazy(LazyThreadSafetyMode.NONE) {
            AnnotationFilter.create(
                showAnnotationValues +
                    showSingleAnnotationValues +
                    showForStubPurposesAnnotationValues
            )
        }

    /**
     * A filter that will match annotations which will cause an annotated item (and its enclosed
     * items unless overridden by a closer annotation) to be included in the API surface.
     *
     * @see [allShowAnnotations]
     */
    internal val showAnnotations by
        lazy(LazyThreadSafetyMode.NONE) { AnnotationFilter.create(showAnnotationValues) }

    /**
     * Like [showAnnotations], but does not work recursively.
     *
     * @see [allShowAnnotations]
     */
    internal val showSingleAnnotations by
        lazy(LazyThreadSafetyMode.NONE) { AnnotationFilter.create(showSingleAnnotationValues) }

    /**
     * Annotations that defines APIs that are implicitly included in the API surface. These APIs
     * will be included in certain kinds of output such as stubs, but others (e.g. API lint and the
     * API signature file) ignore them.
     *
     * @see [allShowAnnotations]
     */
    internal val showForStubPurposesAnnotations by
        lazy(LazyThreadSafetyMode.NONE) {
            AnnotationFilter.create(showForStubPurposesAnnotationValues)
        }

    /** Annotations that mark items which should be treated as hidden. */
    internal val hideAnnotations by
        lazy(LazyThreadSafetyMode.NONE) { AnnotationFilter.create(hideAnnotationValues) }

    val apiSurfaces by
        lazy(LazyThreadSafetyMode.NONE) {
            val apiSurfacesConfig = apiSurfacesConfigProvider()
            val checkSurfaceConsistency = checkSurfaceConsistencyProvider()
            createApiSurfaces(
                showUnannotated,
                apiSurface,
                apiSurfacesConfig,
                checkSurfaceConsistency,
            )
        }

    companion object {
        /**
         * Create [ApiSurfaces] and associated [ApiSurface] objects from these options.
         *
         * @param showUnannotated true if unannotated items should be included in the API, false
         *   otherwise.
         * @param targetApiSurface the optional name of the target API surface to be created. If
         *   supplied it MUST reference an [ApiSurfaceConfig] in [apiSurfacesConfig].
         * @param apiSurfacesConfig the optional [ApiSurfacesConfig].
         * @param checkSurfaceConsistency if `true` and [targetApiSurface] is not-null then check
         *   the consistency between the configured surfaces and the [ApiSelectionOptions].
         */
        private fun createApiSurfaces(
            showUnannotated: Boolean,
            targetApiSurface: String?,
            apiSurfacesConfig: ApiSurfacesConfig?,
            checkSurfaceConsistency: Boolean,
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
            if (targetApiSurface == null) {
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
                apiSurfacesConfig.getByNameOrError(targetApiSurface) {
                    "$ARG_API_SURFACE (`$it`) does not match an <api-surface> in a --config-file"
                }

            val extendedSurface = targetApiSurfaceConfig.extends
            val extendsSurface = extendedSurface != null

            // If show annotations should not be ignored then perform a consistency check to ensure
            // that the configuration and command line options are compatible.
            if (checkSurfaceConsistency) {
                if (extendsSurface != needsBase) {
                    val reason =
                        if (extendsSurface)
                            "extends $extendedSurface which requires that it not show unannotated items but $ARG_SHOW_UNANNOTATED is true"
                        else
                            "does not extend another surface which requires that it show unannotated items but $ARG_SHOW_UNANNOTATED is false"
                    throw MetalavaCliException(
                        """Configuration of `<api-surface name="$targetApiSurface">` is inconsistent with command line options because `$targetApiSurface` $reason"""
                    )
                }
            }

            // Create the ApiSurfaces from the configured API surfaces.
            return apiSurfacesFromConfig(
                apiSurfacesConfig.contributesTo(targetApiSurfaceConfig),
                targetApiSurface
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
                isMain = surfaceConfig.name == targetApiSurface,
            )
        }
    }
