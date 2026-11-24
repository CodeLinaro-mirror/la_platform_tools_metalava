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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.ARG_BASELINE
import com.android.tools.metalava.ARG_REMOVED_API
import com.android.tools.metalava.ARG_UPDATE_BASELINE
import com.android.tools.metalava.cli.common.BaselineOptionsMixin
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.runWithReporter
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.cli.compatibility.CheckRequest.CheckType
import com.android.tools.metalava.compatibility.CompatibilityCheck
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.trace
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option

/** Name of the `--api-released` option specifying previously released API signature files. */
const val ARG_API_RELEASED = "--api-released"

/**
 * Command that checks compatibility between a previously released API and a current API, both
 * defined by signature files, without parsing Java or Kotlin source files.
 */
class CheckCompatibilityCommand :
    MetalavaSubCommand(
        help = "Check compatibility between two APIs.",
    ) {

    /** Previously released API signature files against which compatibility will be checked. */
    private val releasedFiles by
        option(
                ARG_API_RELEASED,
                metavar = "<file>",
                help =
                    """
                        Previously released API signature file against which compatibility will be
                        checked. Can be specified multiple times; when multiple files are provided
                        they must be in order from the narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple(required = true)

    /** Removed API signature files for the current API. */
    private val removedApiFiles by
        option(
                ARG_REMOVED_API,
                metavar = "<file>",
                help =
                    """
                        Removed API signature file for the current API. Can be specified multiple
                        times; when multiple files are provided they must be in order from the
                        narrowest API to the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple()

    private val baselineOptionsMixin =
        BaselineOptionsMixin(
            containingParameterHolder = this,
            baselineOptionName = ARG_BASELINE,
            updateBaselineOptionName = ARG_UPDATE_BASELINE,
            issueType = "compatibility",
            description = "compatibility",
        )

    /** Add options for controlling the behavior of the issue Numerical/Category reporters. */
    private val issueReportingOptions by IssueReportingOptions()

    /** The list of signature files defining the current API to check for compatibility. */
    private val sourceFiles by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files defining the current API to check for compatibility. When
                        multiple files are provided they must be in order from the narrowest API to
                        the widest API.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        runWithReporter(
            issueReportingOptions = issueReportingOptions,
            baselineOptionsMixin = baselineOptionsMixin,
        ) { reporter ->
            val apiSurfaces = ApiSurfaces.DEFAULT
            val apiSurface = apiSurfaces.main

            val annotationManager =
                DefaultAnnotationManager(
                    DefaultAnnotationManager.Config(
                        reporter = reporter,
                        annotationClassPredicate = ApiSurfacePredicate.wholeCoreApi(apiSurface),
                    )
                )

            val codebaseConfig =
                Codebase.Config(
                    annotationManager = annotationManager,
                    apiSurfaces = apiSurfaces,
                    reporter = reporter,
                )

            val signatureFileLoader = DefaultSignatureFileLoader(codebaseConfig, tracer = tracer)

            val oldCodebase = signatureFileLoader.load(SignatureFile.fromFiles(releasedFiles))
            val newSignatureFiles =
                SignatureFile.fromFiles(sourceFiles) +
                    SignatureFile.fromFiles(
                        removedApiFiles,
                        apiVariantTypeChooser = { ApiVariantType.REMOVED },
                    )
            val newCodebase = signatureFileLoader.load(newSignatureFiles)

            tracer.trace("CompatibilityCheck.checkCompatibility") {
                CompatibilityCheck.checkCompatibility(
                    newCodebase = newCodebase,
                    oldCodebase = oldCodebase,
                    checkType = CheckType.PUBLIC_API,
                    reporter = reporter,
                    issueConfiguration = reporter.issueConfiguration,
                    apiCompatAnnotations = emptySet(),
                    apiName = null,
                    apiSurface = apiSurface,
                )
            }
        }
    }
}
