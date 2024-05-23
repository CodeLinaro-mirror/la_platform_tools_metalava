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

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option

const val ARG_SHOW_ANNOTATION = "--show-annotation"
const val ARG_SHOW_SINGLE_ANNOTATION = "--show-single-annotation"
const val ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION = "--show-for-stub-purposes-annotation"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val API_SELECTION_OPTIONS_GROUP = "Api Selection"

/**
 * Options related to selecting which parts of the source files will be part of the generated API.
 */
class ApiSelectionOptions() :
    OptionGroup(
        name = API_SELECTION_OPTIONS_GROUP,
        help =
            """
                Options that select which parts of the source files will be part of the generated
                API.
            """
                .trimIndent()
    ) {

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
}
