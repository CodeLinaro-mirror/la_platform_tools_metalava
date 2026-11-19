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
 *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ApiFlagsCreator
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.commonOptions
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.registerPostCommandAction
import com.android.tools.metalava.cli.common.splitMultiple
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.cli.common.tracer
import com.android.tools.metalava.createOutputFileFromCodebaseFragment
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.AnnotationInfo
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.optionalFlagName
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.EmitFileHeader
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.text.SnapshotDeltaMaker
import com.android.tools.metalava.model.text.createCodebaseFragmentForSignatureFile
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.reporter.DefaultReporter
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.trace
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import java.io.File

/** Name of the `--api-files` option. */
const val ARG_API_FILES = "--api-files"

/** Name of the `--removed-api-files` option. */
const val ARG_REMOVED_API_FILES = "--removed-api-files"

/** Name of the `--previously-released-api` option. */
const val ARG_PREVIOUSLY_RELEASED_API = "--previously-released-api"

/** Name of the `--previously-released-removed-api` option. */
const val ARG_PREVIOUSLY_RELEASED_REMOVED_API = "--previously-released-removed-api"

/** Name of the `--output-file` option. */
private const val ARG_OUTPUT_FILE = "--output-file"

/** Name of the `--output-removed-file` option. */
const val ARG_OUTPUT_REMOVED_FILE = "--output-removed-file"

/**
 * Command that filters signature files (possibly a delta on another signature file) against a set
 * of `<api-flags>` provided via `--config-file`, optionally reverting flagged changes against a
 * previously released API.
 */
class FilterSignatureCommand :
    MetalavaSubCommand(
        help =
            """
                Filter signature files using configured API flags.

                Reads signature files defining an API surface (and optionally its removed API) and
                writes filtered signature files after applying any `<api-flags>` from
                `--config-file`, reverting flagged changes against `--previously-released-api` and
                `--previously-released-removed-api` if provided.
            """
                .trimIndent(),
    ) {

    /** Options for loading configuration files. */
    private val configFileOptions by ConfigFileOptions()

    /** Options for configuring issue reporting. */
    private val issueReportingOptions by IssueReportingOptions()

    /**
     * Signature files defining the current API from narrowest to widest API surface. The last file
     * is the target signature file to filter; any preceding files are base surface signature files
     * that it extends.
     */
    private val apiFiles by
        option(
                ARG_API_FILES,
                metavar = "<files>",
                help =
                    """
                        One or more signature files (separated by `${File.pathSeparator}`) defining
                        the current API from narrowest to widest API surface. The last file is the
                        target signature file to filter; any preceding files are base surface
                        signature files that it extends.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator, required = true)

    /**
     * Optional signature files defining the current removed API from narrowest to widest API
     * surface, matching the surfaces in [apiFiles].
     */
    private val removedApiFiles by
        option(
                ARG_REMOVED_API_FILES,
                metavar = "<files>",
                help =
                    """
                        One or more signature files (separated by `${File.pathSeparator}`) defining
                        the current removed API from narrowest to widest API surface, corresponding
                        to the surfaces in `$ARG_API_FILES`.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator)

    /**
     * Optional signature files defining the previously released API from narrowest to widest API
     * surface, used when reverting flagged changes to previously released APIs.
     */
    private val previouslyReleasedApiFiles by
        option(
                ARG_PREVIOUSLY_RELEASED_API,
                metavar = "<files>",
                help =
                    """
                        One or more signature files (separated by `${File.pathSeparator}`) defining
                        the previously released API from narrowest to widest API surface, used when
                        reverting flagged changes to previously released APIs.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator)

    /**
     * Optional signature files defining the previously released removed API from narrowest to
     * widest API surface, used when reverting flagged changes to previously released removed APIs.
     */
    private val previouslyReleasedRemovedApiFiles by
        option(
                ARG_PREVIOUSLY_RELEASED_REMOVED_API,
                metavar = "<files>",
                help =
                    """
                        One or more signature files (separated by `${File.pathSeparator}`) defining
                        the previously released removed API from narrowest to widest API surface,
                        used when reverting flagged changes to previously released removed APIs.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .splitMultiple(File.pathSeparator)

    /** The output file into which the filtered signature file will be written. */
    private val outputFile by
        option(
                ARG_OUTPUT_FILE,
                metavar = "<file>",
                help = "The output file into which the filtered signature file will be written.",
            )
            .newFile()
            .required()

    /** The optional output file into which the filtered removed signature file will be written. */
    private val outputRemovedFile by
        option(
                ARG_OUTPUT_REMOVED_FILE,
                metavar = "<file>",
                help =
                    "The output file into which the filtered removed signature file will be written.",
            )
            .newFile()

    override fun run() {
        val config = configFileOptions.config

        // Configure the issue reporter from CLI options and any <issues> elements in config files.
        val computedIssueReportingOptions =
            issueReportingOptions.compute(commonOptions, config.issues)
        val reporter =
            DefaultReporter(
                environment = executionEnvironment.reporterEnvironment,
                issueConfiguration = computedIssueReportingOptions.issueConfiguration,
                config = computedIssueReportingOptions.reporterConfig,
            )

        // Ensure the suppressed-issues writer is closed when the command finishes.
        registerPostCommandAction {
            computedIssueReportingOptions.reporterConfig.reportEvenIfSuppressedWriter?.close()
        }

        try {
            if (removedApiFiles.isNotEmpty() && removedApiFiles.size != apiFiles.size) {
                cliError(
                    "$ARG_REMOVED_API_FILES must specify the same number of files (${removedApiFiles.size}) as $ARG_API_FILES (${apiFiles.size})"
                )
            }

            val apiFlags = ApiFlagsCreator.createFromConfig(config.apiFlags)

            // Preserve the FileFormat from the header of the target (last) input signature file,
            // defaulting to V2 if the file is empty.
            val fileFormat = parseFileFormat(apiFiles.last()) ?: FileFormat.V2

            var previousFilteredCodebase: Codebase? = null
            var currentFilteredFragment: CodebaseFragment? = null

            for (i in 1..apiFiles.size) {
                val currentSignatureFiles =
                    createSignatureFiles(
                        coreFiles = apiFiles.take(i),
                        removedFiles = removedApiFiles.take(i),
                    )
                val currentPreviouslyReleasedSignatureFiles =
                    createSignatureFiles(
                        coreFiles =
                            if (i == apiFiles.size) {
                                previouslyReleasedApiFiles
                            } else {
                                previouslyReleasedApiFiles.take(i)
                            },
                        removedFiles =
                            if (i == apiFiles.size) {
                                previouslyReleasedRemovedApiFiles
                            } else {
                                previouslyReleasedRemovedApiFiles.take(i)
                            },
                    )
                val previouslyReleasedCodebase =
                    if (currentPreviouslyReleasedSignatureFiles.isNotEmpty()) {
                        try {
                            ApiFile.parseApi(
                                currentPreviouslyReleasedSignatureFiles,
                                Codebase.Config(reporter = reporter),
                            )
                        } catch (e: ApiParseException) {
                            cliError(e.message!!)
                        }
                    } else {
                        null
                    }

                val fallbacks = listOfNotNull(previouslyReleasedCodebase, previousFilteredCodebase)
                previousFilteredCodebase = currentFilteredFragment?.codebase
                currentFilteredFragment =
                    filterCodebase(
                        currentSignatureFiles,
                        fallbacks,
                        apiFlags,
                        fileFormat,
                        reporter,
                    )
            }

            val baseCodebase = previousFilteredCodebase
            val targetCodebase = currentFilteredFragment!!.codebase

            writeOutputSignatureFile(
                targetCodebase = targetCodebase,
                baseCodebase = baseCodebase,
                apiType = ApiType.CORE,
                fileFormat = fileFormat,
                emitHeader = EmitFileHeader.ALWAYS,
                output = outputFile,
            )

            outputRemovedFile?.let { removedOutput ->
                val lastRemovedHeaderFormat =
                    removedApiFiles.lastOrNull()?.let { parseFileFormat(it) }
                val removedFileFormat = lastRemovedHeaderFormat ?: fileFormat
                val emitRemovedHeader =
                    if (lastRemovedHeaderFormat != null) {
                        EmitFileHeader.ALWAYS
                    } else {
                        EmitFileHeader.IF_NONEMPTY_FILE
                    }

                writeOutputSignatureFile(
                    targetCodebase = targetCodebase,
                    baseCodebase = baseCodebase,
                    apiType = ApiType.REMOVED,
                    fileFormat = removedFileFormat,
                    emitHeader = emitRemovedHeader,
                    output = removedOutput,
                )
            }
        } finally {
            // Write all saved reports. Do this even if the previous code threw an exception.
            tracer.trace("writeSavedReports") { reporter.writeSavedReports() }
        }

        if (reporter.hasErrors()) {
            // Repeat the errors at the end to make it easy to find the actual problems.
            if (issueReportingOptions.repeatErrorsMax > 0) {
                stderr.println("Error: $PROGRAM_NAME detected the following problems:")
                val totalErrors = reporter.errorCount
                val numShown = reporter.printErrors(stderr, issueReportingOptions.repeatErrorsMax)
                if (numShown < totalErrors) {
                    stderr.println(
                        "${totalErrors - numShown} more error(s) omitted. Search the log for 'error:' to find all of them."
                    )
                }
            }

            // Make sure that the process exits with an error code.
            throw MetalavaCliException(exitCode = -1)
        }
    }

    /**
     * Parse the [FileFormat] header of [file], returning `null` if the file is empty or has no
     * header.
     */
    private fun parseFileFormat(file: File): FileFormat? =
        try {
            file.reader().use { FileFormat.parseHeader(file.toPath(), it) }
        } catch (e: ApiParseException) {
            cliError(e.message!!)
        }

    /**
     * Combine [coreFiles] ([ApiVariantType.CORE]) and [removedFiles] ([ApiVariantType.REMOVED])
     * into a single list of [SignatureFile]s with all core files preceding all removed files so
     * that classes present in both are created in the core variant before removed members are
     * merged into them.
     */
    private fun createSignatureFiles(
        coreFiles: List<File>,
        removedFiles: List<File>,
    ): List<SignatureFile> =
        SignatureFile.fromFiles(
            coreFiles,
            apiVariantTypeChooser = { ApiVariantType.CORE },
        ) +
            SignatureFile.fromFiles(
                removedFiles,
                apiVariantTypeChooser = { ApiVariantType.REMOVED },
            )

    /**
     * Write the filtered signature output for [apiType] (`CORE` or `REMOVED`) from [targetCodebase]
     * (computing a delta against [baseCodebase] if non-null) to [output].
     */
    private fun writeOutputSignatureFile(
        targetCodebase: Codebase,
        baseCodebase: Codebase?,
        apiType: ApiType,
        fileFormat: FileFormat,
        emitHeader: EmitFileHeader,
        output: File,
    ) {
        val targetVariantFragment =
            createVariantCodebaseFragment(targetCodebase, fileFormat, apiType)
        val outputFragment =
            if (baseCodebase == null) {
                targetVariantFragment
            } else {
                // Take a variant-filtered snapshot of baseCodebase so SnapshotDeltaMaker compares
                // the target variant against only the base items belonging to the same variant.
                val baseVariantCodebase =
                    createVariantCodebaseFragment(baseCodebase, fileFormat, apiType)
                        .snapshotIncludingRevertedItems(
                            referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                        )
                        .codebase
                val deltaFragment =
                    SnapshotDeltaMaker.createDelta(
                        base = baseVariantCodebase,
                        codebaseFragment = targetVariantFragment,
                        checkMemberItemEquivalence = false,
                        allowClassModifierChanges = false,
                    )
                createVariantCodebaseFragment(deltaFragment.codebase, fileFormat, apiType)
            }

        createOutputFileFromCodebaseFragment(
            outputFragment,
            output,
        ) { printWriter ->
            SignatureWriter(
                writer = printWriter,
                fileFormat = fileFormat,
                emitHeader = emitHeader,
            )
        }
    }

    /**
     * Create a [CodebaseFragment] for [codebase] filtered to the items belonging to [apiType] on
     * the main API surface.
     */
    private fun createVariantCodebaseFragment(
        codebase: Codebase,
        fileFormat: FileFormat,
        apiType: ApiType,
    ): CodebaseFragment =
        createCodebaseFragmentForSignatureFile(
            codebase,
            fileFormat = fileFormat,
            apiFilters =
                ApiSurfacePredicate.apiFilters(
                    apiType,
                    codebase.config.apiSurfaces.main,
                ),
        )

    /**
     * Parse [currentSignatureFiles] into a [Codebase] and filter it using [apiFlags] and
     * [fallbackCodebases].
     */
    private fun filterCodebase(
        currentSignatureFiles: List<SignatureFile>,
        fallbackCodebases: List<Codebase>,
        apiFlags: ApiFlags?,
        fileFormat: FileFormat,
        reporter: Reporter,
    ): CodebaseFragment {
        val droppedItems = mutableSetOf<SelectableItem>()
        val revertItems = mutableMapOf<SelectableItem, SelectableItem>()

        val annotationManager = SignatureFilterAnnotationManager(apiFlags, droppedItems)
        val codebaseConfig =
            Codebase.Config(
                annotationManager = annotationManager,
                apiFlags = apiFlags,
                reporter = reporter,
            )
        val codebase =
            try {
                ApiFile.parseApi(
                    currentSignatureFiles,
                    codebaseConfig,
                )
            } catch (e: ApiParseException) {
                cliError(e.message!!)
            }
        annotationManager.codebase = codebase

        // Walk items top-down (containing classes before nested classes and members) to identify
        // reverted and dropped items.
        codebase.accept(
            object : BaseItemVisitor() {
                override fun visitSelectableItem(item: SelectableItem) {
                    if (item.containingClass()?.let { it in droppedItems } == true) {
                        droppedItems.add(item)
                        return
                    }
                    if (isMarkedForRevert(item)) {
                        val revertItem =
                            fallbackCodebases.firstNotNullOfOrNull {
                                item.findCorrespondingItemIn(it)
                            }
                        if (revertItem != null) {
                            revertItems[item] = revertItem
                        } else {
                            droppedItems.add(item)
                        }
                    }
                }
            }
        )

        val filterPredicate =
            object : FilterPredicate() {
                override fun test(item: SelectableItem) = item.emit && item !in droppedItems
            }
        val apiFilters = ApiFilters(emit = filterPredicate, reference = filterPredicate)
        val fragment =
            createCodebaseFragmentForSignatureFile(
                codebase,
                fileFormat = fileFormat,
                apiFilters = apiFilters,
            )

        val snapshotFragment =
            fragment.snapshotIncludingRevertedItems(
                referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                revertItemGetter = { revertItems[it] },
            )

        // Strip any annotations from the snapshot that should not be written to a signature file
        // (such as finalized/reverted @FlaggedApi annotations or dropped @interface annotations) so
        // that SnapshotDeltaMaker sees the post-filtered annotations on both base and extension
        // codebases.
        snapshotFragment.codebase.accept(
            object : BaseItemVisitor(visitParameterItems = true) {
                override fun visitItem(item: Item) {
                    item.mutateModifiers {
                        mutateAnnotations {
                            removeIf { AnnotationTarget.SIGNATURE_FILE !in it.targets }
                        }
                    }
                }
            }
        )

        return snapshotFragment
    }

    /**
     * Find the effective `@FlaggedApi` [AnnotationItem] gating the addition or modification of
     * [item], checking [item] directly first and walking up its [SelectableItem.containingClass]
     * hierarchy if none is present on [item].
     *
     * Ignores `@FlaggedApi` annotations on items marked with `@ForRemoval`
     * ([SelectedApi.hasRemovedAnnotation]), since those gate the *removal* of an API item rather
     * than its addition or modification (reverting the flag cancels the removal and keeps the item
     * in the core API). Also only inherits `@FlaggedApi` from an enclosing class when the enclosing
     * class's [SelectedApi.itemApiVariants] overlaps with [item]'s [SelectedApi.itemApiVariants],
     * so a removed member in `removed.txt` does not inherit a `@FlaggedApi` annotation that only
     * applies to its enclosing class in `current.txt`.
     */
    private fun effectiveFlaggedApi(item: SelectableItem): AnnotationItem? {
        var current: SelectableItem? = item
        while (current != null) {
            if (
                !current.selectedApi.hasRemovedAnnotation() &&
                    (current === item ||
                        current.selectedApi.itemApiVariants.containsAny(
                            item.selectedApi.itemApiVariants
                        ))
            ) {
                val annotation =
                    current.modifiers.annotations().firstOrNull {
                        it.qualifiedName == ANDROID_FLAGGED_API
                    }
                if (annotation != null) return annotation
            }
            current = current.containingClass()
        }
        return null
    }

    /**
     * Check whether [item] is marked for reversion, either via its own or an enclosing class's
     * `@FlaggedApi` annotation, or (for a [MethodItem]) because it overrides a super method that is
     * marked for reversion.
     */
    private fun isMarkedForRevert(item: SelectableItem): Boolean {
        if (effectiveFlaggedApi(item)?.apiFlag?.revert == true) return true
        if (item is MethodItem && item.superMethods().any { isMarkedForRevert(it) }) return true
        return false
    }
}

/**
 * Custom [AnnotationManager] for [FilterSignatureCommand] that delegates to [noOpAnnotationManager]
 * so that unresolved annotations in signature files (such as `@NonNull` or `@IntRange` without a
 * classpath) are preserved in signature output, while computing [AnnotationInfo.apiFlag] and
 * [AnnotationInfo.targets] for `@FlaggedApi` annotations and suppressing any `@interface` classes
 * that were dropped during filtering.
 */
private class SignatureFilterAnnotationManager(
    private val apiFlags: ApiFlags?,
    private val droppedItems: Set<SelectableItem>,
    private val delegate: AnnotationManager = noOpAnnotationManager,
) : AnnotationManager by delegate {
    var codebase: Codebase? = null

    private val infoCache = mutableMapOf<String, AnnotationInfo>()

    override fun getAnnotationInfo(annotation: AnnotationItem): AnnotationInfo {
        val qualifiedName = annotation.qualifiedName
        val key =
            if (apiFlags != null && qualifiedName == ANDROID_FLAGGED_API) {
                "$qualifiedName(${annotation.optionalFlagName})"
            } else {
                qualifiedName
            }
        return infoCache.getOrPut(key) {
            val baseInfo = delegate.getAnnotationInfo(annotation)
            val apiFlag =
                if (qualifiedName == ANDROID_FLAGGED_API) {
                    val flagName = annotation.optionalFlagName
                    if (apiFlags == null || flagName == null) {
                        ApiFlag.KEEP_ALL
                    } else {
                        apiFlags[flagName]
                    }
                } else {
                    null
                }
            object : AnnotationInfo by baseInfo {
                override val apiFlag: ApiFlag? = apiFlag
                override val targets: Set<AnnotationTarget>
                    get() =
                        if (apiFlag != null) {
                            apiFlag.annotationTargets
                        } else {
                            val cls = codebase?.findClass(qualifiedName)
                            if (cls != null && cls in droppedItems) {
                                NO_ANNOTATION_TARGETS
                            } else {
                                baseInfo.targets
                            }
                        }
            }
        }
    }
}
