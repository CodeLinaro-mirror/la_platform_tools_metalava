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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_MERGE_QUALIFIER_ANNOTATIONS
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

const val ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS = "--validate-nullability-from-merged-stubs"
const val ARG_VALIDATE_NULLABILITY_FROM_LIST = "--validate-nullability-from-list"
const val ARG_NULLABILITY_WARNINGS_TXT = "--nullability-warnings-txt"
const val ARG_NULLABILITY_ERRORS_NON_FATAL = "--nullability-errors-non-fatal"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val NULLABILITY_VALIDATION_OPTIONS_GROUP = "Nullability Validation"

class NullabilityValidationOptions() :
    OptionGroup(
        NULLABILITY_VALIDATION_OPTIONS_GROUP,
        help = "Options control nullability validation."
    ) {

    /** Whether nullability validation errors should be considered fatal. */
    private val nullabilityErrorsNonFatal by
        option(
                ARG_NULLABILITY_ERRORS_NON_FATAL,
                help =
                    """
                        Specifies that errors encountered during validation of nullability
                        annotations should not be treated as errors. They will be written out to the
                        file specified in $ARG_NULLABILITY_WARNINGS_TXT instead.
                    """
                        .trimIndent(),
            )
            .flag()

    internal val nullabilityErrorsFatal
        get() = !nullabilityErrorsNonFatal

    /**
     * A file to write non-fatal nullability validation issues to. If null, all issues are treated
     * as fatal or else logged as warnings, depending on the value of [nullabilityErrorsFatal].
     */
    internal val nullabilityWarningsTxt by
        option(
                ARG_NULLABILITY_WARNINGS_TXT,
                metavar = "<file>",
                help =
                    """
                        Specifies where to write warnings encountered during validation of
                        nullability annotations. (Does not trigger validation by itself.)
                    """
                        .trimIndent(),
            )
            .newFile()

    /**
     * Whether to validate nullability for all the classes where we are merging annotations from
     * external java stub files.
     */
    val validateNullabilityFromMergedStubs by
        option(
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS,
                help =
                    """
                        Triggers validation of nullability annotations for any class where
                        $ARG_MERGE_QUALIFIER_ANNOTATIONS includes a Java stub file.
                    """
                        .trimIndent(),
            )
            .flag()

    /** A file containing a list of classes whose nullability annotations should be validated. */
    internal val validateNullabilityFromList by
        option(
                ARG_VALIDATE_NULLABILITY_FROM_LIST,
                help =
                    """
                        Triggers validation of nullability annotations for any class listed in the
                        named file (one top-level class per line, # prefix for comment line).
                    """
                        .trimIndent(),
            )
            .existingFile()
}
