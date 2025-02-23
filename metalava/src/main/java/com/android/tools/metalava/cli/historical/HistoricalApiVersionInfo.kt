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

package com.android.tools.metalava.cli.historical

import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.MatchedPatternFile
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/**
 * Encapsulates information relating to a historical API version.
 *
 * Includes all API files related to [version], and the set of [ApiSurface]s. The [infoBySurface]
 * maps from [ApiSurface] to [SurfaceInfo].
 */
class HistoricalApiVersionInfo
private constructor(
    /** The [ApiVersion] to which this refers. */
    val version: ApiVersion,
    /** Information about each surface found for this version. */
    val infoBySurface: Map<ApiSurface, SurfaceInfo>,
) {
    companion object {
        /**
         * Scan for historical API version information and create a list of
         * [HistoricalApiVersionInfo] representing that.
         *
         * @param reporter for errors found.
         * @param jarFilePattern the pattern for jar files, see [PatternNode.parsePatterns].
         * @param signatureFilePattern the pattern for signature files, see
         *   [PatternNode.parsePatterns].
         * @param scanConfig see [PatternNode.scan].
         */
        internal fun scan(
            reporter: Reporter,
            jarFilePattern: String,
            signatureFilePattern: String,
            scanConfig: PatternNode.ScanConfig,
        ): List<HistoricalApiVersionInfo> {
            // Get all the matching jar and signature files.
            val matchedPatternFiles =
                scanForPattern(jarFilePattern, scanConfig) +
                    scanForPattern(signatureFilePattern, scanConfig)

            // Construct a list of HistoricalApiVersionInfo from them.
            return fromMatchedPatternFiles(reporter, matchedPatternFiles)
        }

        /**
         * Construct a [PatternNode] for [pattern] and then use that to scan for files with
         * [scanConfig].
         */
        private fun scanForPattern(pattern: String, scanConfig: PatternNode.ScanConfig) =
            PatternNode.parsePatterns(listOf(pattern)).scan(scanConfig)

        /** Construct a list of [HistoricalApiVersionInfo]s from a list of [MatchedPatternFile]s. */
        private fun fromMatchedPatternFiles(
            reporter: Reporter,
            matchedPatternFiles: List<MatchedPatternFile>,
        ) =
            // Group by versions.
            matchedPatternFiles
                .groupBy { it.version }
                // Map to list of HistoricalApiVersionInfo
                .mapNotNull { (version, versionFiles) ->
                    fromVersionFiles(reporter, version, versionFiles)
                }

        /**
         * Construct a [HistoricalApiVersionInfo] from [versionFiles] for [version].
         *
         * If an error is encountered which prevents it from being constructed then the error is
         * reported to [reporter] and this returns `null`.
         */
        private fun fromVersionFiles(
            reporter: Reporter,
            version: ApiVersion,
            versionFiles: List<MatchedPatternFile>,
        ): HistoricalApiVersionInfo? {
            val noSurface = versionFiles.filter { it.surface == null }
            if (noSurface.isNotEmpty()) {
                reporter.report(
                    Issues.IO_ERROR,
                    reportable = null,
                    "All files must have a surface but found ${noSurface.size} files without:${
                        noSurface.joinToString(
                            "\n    ",
                            prefix = "\n    "
                        ) { it.file.path }
                    }"
                )
                return null
            }

            val infoBySurface =
                buildMap<ApiSurface, SurfaceInfo> {
                    val bySurface = versionFiles.groupBy { it.surface!! }
                    for ((surface, surfaceFiles) in bySurface) {
                        fromSurfaceFiles(reporter, this, version, surface, surfaceFiles)
                    }
                }

            return HistoricalApiVersionInfo(version, infoBySurface)
        }

        /**
         * Construct a [SurfaceInfo] from [surfaceFiles] for [surface] in [version] and add it to
         * [infoBySurface].
         *
         * If an error is encountered which prevents it from being constructed then the error is
         * reported to [reporter] and this returns `null`.
         */
        private fun fromSurfaceFiles(
            reporter: Reporter,
            infoBySurface: MutableMap<ApiSurface, SurfaceInfo>,
            version: ApiVersion,
            surface: ApiSurface,
            surfaceFiles: List<MatchedPatternFile>,
        ) {
            // Partition into jar files and other files which are assumed to be signature files.
            val (jarFiles, signatureFiles) =
                surfaceFiles.map { it.file }.partition { it.extension == ("jar") }

            val jarFile =
                singleFileIfPossible(jarFiles, reporter, version, surface, "jar") ?: return

            val signatureFile =
                singleFileIfPossible(signatureFiles, reporter, version, surface, "signature")
                    ?: return

            val extendsInfo = surface.extends?.let { infoBySurface[it] }

            val info = SurfaceInfo(surface, jarFile, signatureFile, extendsInfo)
            infoBySurface[surface] = info
        }

        /**
         * Get a single file from [files].
         *
         * If [files] is empty then it is an error and this will return `null` as it has no [File]
         * to return. If it has more than one [File] then it is also an error, but it returns the
         * first [File] in the list. Otherwise, it just returns the first [File].
         */
        private fun singleFileIfPossible(
            files: List<File>,
            reporter: Reporter,
            version: ApiVersion,
            surface: ApiSurface,
            label: String,
        ): File? {
            val count = files.size
            if (count != 1) {
                if (count == 0) {
                    reporter.report(
                        Issues.IO_ERROR,
                        reportable = null,
                        "Expected exactly one $label file per version per surface but did not find any, skipping version $version, surface $surface"
                    )
                    return null
                } else {
                    reporter.report(
                        Issues.IO_ERROR,
                        reportable = null,
                        "Version $version: Expected exactly one $label file per version but found $count; using first:${
                            files.joinToString(
                                "\n    ",
                                prefix = "\n    "
                            )
                        }"
                    )
                }
            }

            return files.first()
        }
    }
}

/** Information related to a specific surface. */
class SurfaceInfo(
    /** The [ApiSurface] to which this refers. */
    val surface: ApiSurface,

    /** The jar [File]. */
    val jarFile: File,

    /** The signature [File]. */
    val signatureFile: File,

    /**
     * Optional [SurfaceInfo] that this extends.
     *
     * This refers to the [SurfaceInfo] corresponding to [surface]'s [ApiSurface.extends] property,
     * if any.
     */
    val extends: SurfaceInfo?,
) {
    /**
     * Get the [SignatureFile]s that contribute to this surface.
     *
     * In order, such that the file for an extending surface comes after the files for the extended
     * surface.
     */
    fun contributingSignatureFiles(): List<SignatureFile> {
        val files = buildList { addContributingSignatureFiles(this) }
        return SignatureFile.fromFiles(files)
    }

    /**
     * Add extended [SurfaceInfo]s files that contribute to this surface, if any, followed by this
     * surfaces file.
     */
    private fun addContributingSignatureFiles(list: MutableList<File>) {
        // Add any files for the extended surface, if any, first.
        extends?.addContributingSignatureFiles(list)

        // Then add the files for this surface.
        list.add(signatureFile)
    }
}
