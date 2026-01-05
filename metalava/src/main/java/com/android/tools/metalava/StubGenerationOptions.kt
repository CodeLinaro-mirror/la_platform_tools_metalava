/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.cli.common.newDir
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.stub.StubGenerator
import com.android.tools.metalava.stub.StubWriterConfig
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

private const val STUB_GENERATION_GROUP = "Stub Generation"

const val ARG_INCLUDE_ANNOTATIONS = "--include-annotations"
const val ARG_EXCLUDE_ALL_ANNOTATIONS = "--exclude-all-annotations"
const val ARG_STUBS = "--stubs"
const val ARG_DOC_STUBS = "--doc-stubs"
const val ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS =
    "--force-convert-to-warning-nullability-annotations"
const val ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS = "--exclude-documentation-from-stubs"
const val ARG_ENHANCE_DOCUMENTATION = "--enhance-documentation"

class StubGenerationOptions :
    OptionGroup(
        name = STUB_GENERATION_GROUP,
        help = "Options controlling the generation of stub files.",
    ) {
    private val stubsDir by
        option(
                ARG_STUBS,
                metavar = "<dir>",
                help =
                    """
                        Base directory to output the generated stub source files for the API, if
                        specified.

                        At most one of this and $ARG_DOC_STUBS can be provided.
                    """
                        .trimIndent(),
            )
            .newDir()

    /**
     * If set, a directory to write documentation stub files to. Corresponds to the --doc-stubs
     * flag.
     */
    private val docStubsDir by
        option(
                ARG_DOC_STUBS,
                metavar = "<dir>",
                help =
                    """
                        Generate documentation stub source files for the API. Documentation stub
                        files are similar to regular stub files, but there are some differences. For
                        example, in the stub files, we'll use special annotations like
                        @RecentlyNonNull instead of @NonNull to indicate that an element is recently
                        marked as non null, whereas in the documentation stubs we'll just list this
                        as @NonNull. Another difference is that @doconly elements are included in
                        documentation stubs, but not regular stubs, etc.

                        At most one of this and $ARG_STUBS can be provided.
                    """
                        .trimIndent(),
            )
            .newDir()

    val includeAnnotations by
        option(
                ARG_INCLUDE_ANNOTATIONS,
                help = "Include/exclude annotations such as @Nullable in/from the stub files.",
            )
            .flag(
                ARG_EXCLUDE_ALL_ANNOTATIONS,
                default = false,
                defaultForHelp = "exclude",
            )

    /**
     * Whether to exclude element documentation (javadoc and KDoc) from the generated stubs.
     * (Copyright notices are not affected by this, they are always included. Documentation stubs
     * (--doc-stubs) are not affected.)
     */
    private val excludeDocumentationFromStubs by
        option(
                ARG_EXCLUDE_DOCUMENTATION_FROM_STUBS,
                help =
                    """
                        Exclude element documentation (javadoc and kdoc) from the generated stubs.
                        Copyright notices are not affected by this, they are always included.
                        Documentation stubs ($ARG_DOC_STUBS) are not affected either.
                    """
                        .trimIndent(),
            )
            .flag(
                default = false,
                defaultForHelp = "exclude",
            )

    /**
     * Enhance documentation in various ways, for example auto-generating documentation based on
     * source annotations present in the code. This is implied by `--doc-stubs`.
     */
    private val enhanceDocumentation by
        option(
                ARG_ENHANCE_DOCUMENTATION,
                help =
                    """
                        Enhance documentation in various ways, for example auto-generating
                        documentation based on source annotations present in the code. This is
                        implied by $ARG_DOC_STUBS.
                    """
                        .trimIndent(),
            )
            .flag(
                default = false,
                defaultForHelp = "do not enhance unless $ARG_DOC_STUBS is specified",
            )

    val forceConvertToWarningNullabilityAnnotations by
        option(
                ARG_FORCE_CONVERT_TO_WARNING_NULLABILITY_ANNOTATIONS,
                metavar = "<package1:-package2:...>",
                help =
                    """
                        On every API declared in a class referenced by the given filter, makes
                        nullability issues appear to callers as warnings rather than errors by
                        replacing @Nullable/@NonNull in these APIs with
                        @RecentlyNullable/@RecentlyNonNull.

                        See `metalava help package-filters` for more information.
                    """
                        .trimIndent()
            )
            .convert { PackageFilter.parse(it) }

    /** Construct a [StubGenerator.Config] based on these options. */
    internal fun generatorConfig(): StubGenerator.Config {
        // Always include documentations in the doc stubs and include documentation in the normal
        // stubs unless explicitly excluded.
        val includeDocumentationInStubs = !excludeDocumentationFromStubs || docStubsDir != null

        val stubsDir =
            when {
                stubsDir == null -> docStubsDir
                docStubsDir == null -> stubsDir
                else -> {
                    throw MetalavaCliException(
                        "Cannot use $ARG_STUBS and $ARG_DOC_STUBS, they are mutually exclusive"
                    )
                }
            }

        return StubGenerator.Config(
            // Create configuration for StubWriter.
            stubWriterConfig =
                StubWriterConfig(
                    includeDocumentationInStubs = includeDocumentationInStubs,
                ),

            // Enhance the documentation if explicitly requested of generating the doc stubs.
            enhanceDocumentation = enhanceDocumentation || docStubsDir != null,

            // Documentation stubs are only written when ARG_DOC_STUBS is specified.
            isDocStubs = docStubsDir != null,

            // Specify the stubs directory, may be null.
            stubsDir = stubsDir,
        )
    }
}
