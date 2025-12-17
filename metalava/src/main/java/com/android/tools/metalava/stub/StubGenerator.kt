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

package com.android.tools.metalava.stub

import com.android.tools.metalava.MarkPackagesAsRecent
import com.android.tools.metalava.NullnessMigration
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.doc.ApiVersionFilter
import com.android.tools.metalava.doc.ApiVersionLabelProvider
import com.android.tools.metalava.doc.DocAnalyzer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Reporter
import com.google.common.base.Stopwatch
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

/** Generates stubs from [codebase]. */
internal class StubGenerator(
    private val config: Config,
    private val codebase: Codebase,
    private val progressTracker: ProgressTracker,
    private val executionEnvironment: ExecutionEnvironment,
    private val reporter: Reporter,
    private val signatureFileCache: SignatureFileCache,
    private val apiPredicateConfig: ApiPredicate.Config,
) {
    data class Config(
        /** Configuration needed by [StubWriter]. */
        val stubWriterConfig: StubWriterConfig = StubWriterConfig(),

        /** Determines whether [enhanceCodebaseDocumentationFromOptions] is called. */
        val enhanceDocumentation: Boolean = false,

        /** Determines whether documentation stubs should be written or normal stubs. */
        val isDocStubs: Boolean = false,

        /**
         * The directory into which the stubs will be written.
         *
         * Is nullable because even when no stubs are generated this does some work which has side
         * effects, e.g. reporting errors.
         */
        val stubsDir: File? = null,

        /** Determines whether the annotations will be included in the stubs. */
        val generateAnnotations: Boolean = false,

        /**
         * An optional [PreviouslyReleasedApi] that is used to determine whether a nullability
         * annotation was added recently and so requires converting to warning nullability.
         */
        val nullabilityConversionPreviouslyReleasedApi: PreviouslyReleasedApi? = null,

        /**
         * An optional [PackageFilter] that matches packages whose nullability annotations all need
         * to be converted to warning nullability.
         */
        val nullabilityConversionPackageFilter: PackageFilter? = null,

        /**
         * Optional `api-versions.xml` file that if specified will be applied to the documentation.
         */
        val apiVersionsXmlFile: File? = null,

        /**
         * Maps from [ApiVersion] to the form to use in the documentation, which may be a symbolic
         * name.
         */
        val apiVersionLabelProvider: ApiVersionLabelProvider = ApiVersion::toString,

        /** Determines whether an [ApiVersion] should be included in the documentation. */
        val includeApiLevelInDocumentation: ApiVersionFilter = { true }
    )

    /** Generate the stubs. */
    fun generateStubs() {
        // Use information from various sources to enhance the documentation, if required.
        if (config.enhanceDocumentation) {
            enhanceCodebaseDocumentationFromOptions()
        }

        // Only convert to warning nullability for normal, i.e. not doc, stubs. That is because
        // the warning nullability only affects the Kotlin compiler which uses the normal stubs
        // but the documentation needs to show the correct nullability.
        if (!config.isDocStubs) {
            convertToWarningNullabilityAnnotations(
                config.nullabilityConversionPreviouslyReleasedApi,
                config.nullabilityConversionPackageFilter,
            )
        }

        // Generate the stubs, normal or documentation.
        config.stubsDir?.let { stubDir -> createStubFiles(stubDir, config.isDocStubs) }
    }

    /** Depending on option flags, enhance codebase documentation */
    private fun enhanceCodebaseDocumentationFromOptions() {
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }

        progressTracker.progress("Enhancing docs: ")
        val docAnalyzer =
            DocAnalyzer(
                executionEnvironment,
                codebase,
                reporter,
                config.apiVersionLabelProvider,
                config.includeApiLevelInDocumentation,
                apiPredicateConfig,
            )
        docAnalyzer.enhance()

        // If provided apply information in the api-versions.xml to the documentation.
        val applyApiLevelsXmlFile = config.apiVersionsXmlFile
        if (applyApiLevelsXmlFile != null) {
            progressTracker.progress("Applying API levels")
            docAnalyzer.applyApiVersions(applyApiLevelsXmlFile)
        }
    }

    private fun createStubFiles(stubDir: File, isDocStubs: Boolean) {
        if (isDocStubs) {
            progressTracker.progress("Generating documentation stub files: ")
        } else {
            progressTracker.progress("Generating stub files: ")
        }

        val localTimer = Stopwatch.createStarted()

        var codebaseFragment =
            CodebaseFragment.create(codebase) { delegate ->
                createFilteringVisitorForStubs(
                    delegate = delegate,
                    isDocStubs = isDocStubs,
                    preFiltered = codebase.preFiltered,
                    apiPredicateConfig = apiPredicateConfig,
                )
            }

        // If reverting some changes then create a snapshot that combines the items from the sources
        // for any un-reverted changes and items from the previously released API for any reverted
        // changes.
        if (codebaseFragment.codebase.containsRevertedItem) {
            codebaseFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    referenceVisitorFactory = { delegate ->
                        createFilteringVisitorForStubs(
                            delegate = delegate,
                            isDocStubs = isDocStubs,
                            preFiltered = codebase.preFiltered,
                            apiPredicateConfig = apiPredicateConfig,
                            ignoreEmit = true,
                        )
                    },
                )
        }

        // Add additional constructors needed by the stubs.
        val filterEmit: FilterPredicate =
            if (codebaseFragment.codebase.preFiltered) {
                FilterPredicate { true }
            } else {
                val apiPredicateConfigIgnoreShown = apiPredicateConfig.copy(ignoreShown = true)
                ApiPredicate(ignoreRemoved = false, config = apiPredicateConfigIgnoreShown)
            }
        val stubConstructorManager = StubConstructorManager(codebaseFragment.codebase)
        stubConstructorManager.addConstructors(filterEmit)

        val stubWriter =
            StubWriter(
                stubsDir = stubDir,
                generateAnnotations = config.generateAnnotations,
                isDocStubs = isDocStubs,
                reporter = reporter,
                config = config.stubWriterConfig,
                stubConstructorManager = stubConstructorManager,
            )

        codebaseFragment.accept(stubWriter)

        if (isDocStubs) {
            // Overview docs? These are generally in the empty package.
            codebase.findPackage("")?.let { empty ->
                val overview = empty.overviewDocumentation
                if (overview != null) {
                    stubWriter.writeDocOverview(empty, overview)
                }
            }
        }

        progressTracker.progress(
            "$PROGRAM_NAME wrote ${if (isDocStubs) "documentation" else ""} stubs directory $stubDir in ${
                localTimer.elapsed(SECONDS)} seconds\n"
        )
    }

    private fun convertToWarningNullabilityAnnotations(
        previouslyReleasedApi: PreviouslyReleasedApi?,
        filter: PackageFilter?,
    ) {
        if (previouslyReleasedApi != null) {
            val previousCodebase =
                previouslyReleasedApi.load { signatureFiles ->
                    signatureFileCache.load(signatureFiles)
                }

            // If configured, checks for newly added nullness information compared
            // to the previous stable API and marks the newly annotated elements
            // as migrated (which will cause the Kotlin compiler to treat problems
            // as warnings instead of errors

            NullnessMigration.migrateNulls(codebase, previousCodebase)

            previousCodebase.dispose()
        }

        if (filter != null) {
            // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
            // their callers make incorrect nullness assumptions (for example, calling a function on
            // a reference of nullable type). The way to communicate this to kotlinc is to mark
            // these APIs as RecentlyNullable/RecentlyNonNull
            codebase.accept(MarkPackagesAsRecent(filter, apiPredicateConfig))
        }
    }
}
