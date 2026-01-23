/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.newDir
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.ThrowingReporter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

const val ARG_SDK_VALUES = "--sdk-values"
/** Used by Firebase, see b/116185431#comment15, not used by Android Platform or AndroidX */
const val ARG_PROGUARD = "--proguard"
const val ARG_EXTRACT_ANNOTATIONS = "--extract-annotations"
const val ARG_MANIFEST = "--manifest"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val MISCELLANEOUS_OPTIONS_GROUP = "Miscellaneous"

class MiscellaneousOptions(
    private val reporterSupplier: () -> Reporter = { ThrowingReporter.INSTANCE },
) : OptionGroup(MISCELLANEOUS_OPTIONS_GROUP, help = "Miscellaneous options.") {
    /** Proguard Keep list file to write */
    val proguardFile by
        option(
                ARG_PROGUARD,
                metavar = "<file>",
                help = "Write a ProGuard keep file for the API.",
            )
            .newFile()

    /** Path to directory to write SDK values to */
    val sdkValueDir by
        option(
                ARG_SDK_VALUES,
                metavar = "<dir>",
                help = "Write SDK values files to the given directory.",
            )
            .newDir()

    /**
     * If set, a file to write extracted annotations to. Corresponds to the --extract-annotations
     * flag.
     */
    val externalAnnotationsFile by
        option(
                ARG_EXTRACT_ANNOTATIONS,
                metavar = "<zipfile>",
                help =
                    """
                        Extracts source annotations from the source files and writes them into the
                        given zip file.
                    """
                        .trimIndent(),
            )
            .newFile()

    /** An optional manifest [File]. */
    private val manifestFile by
        option(
                ARG_MANIFEST,
                help =
                    """
        A manifest file, used to check permissions to cross check APIs and retrieve min_sdk_version.
        (default: no manifest)
                    """
                        .trimIndent()
            )
            .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    /**
     * A [Manifest] object to look up available permissions and min_sdk_version.
     *
     * Created lazily to make sure that the [reporter] has been initialized.
     */
    val manifest by lazy { manifestFile?.let { Manifest(it, reporter) } ?: emptyManifest }

    /** [Reporter] that will redirect [Issues.Issue] depending on their [Issues.Category]. */
    private val reporter
        get() = reporterSupplier()
}
