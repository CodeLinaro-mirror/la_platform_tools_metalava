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

package com.android.tools.metalava.cli.common

import com.android.SdkConstants
import com.android.tools.metalava.model.PackageFilter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_SOURCE_PATH = "--source-path"

const val ARG_STUB_PACKAGES = "--stub-packages"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SOURCE_OPTIONS_GROUP = "Sources"

class SourceOptions :
    OptionGroup(
        name = SOURCE_OPTIONS_GROUP,
        help =
            """
            Options that control which source files will be processed.
        """
                .trimIndent()
    ) {

    private val sourcePathString by
        option(
            ARG_SOURCE_PATH,
            metavar = "<path>",
            help =
                """
                    A ${File.pathSeparator} separated list of directories containing source
                    files (organized in a standard Java package hierarchy).
                """
                    .trimIndent(),
        )

    internal val sourcePath by
        lazy(LazyThreadSafetyMode.NONE) { getSourcePath(ARG_SOURCE_PATH, sourcePathString) }

    private fun getSourcePath(argName: String, path: String?) =
        if (path == null) {
            emptyList()
        } else if (path.isBlank()) {
            // Don't compute absolute path; we want to skip this file later on.
            // For current directory one should use ".", not "".
            listOf(File(""))
        } else {
            path.split(File.pathSeparator).map {
                if (it.endsWith(SdkConstants.DOT_JAVA)) {
                    cliError(
                        "$argName should point to a source root directory, not a source file ($it)"
                    )
                }

                stringToExistingDir(it)
            }
        }

    val apiPackages by
        option(
                ARG_STUB_PACKAGES,
                metavar = "<package-list>",
                help =
                    """
                        List of packages (separated by ${File.pathSeparator}) which will be used to
                        filter out irrelevant classes. If specified, only classes in these packages
                        will be included in signature files, stubs, etc.. This is not limited to
                        just the stubs; the $ARG_STUB_PACKAGES name is historical.

                        See `metalava help package-filters` for more information.
                    """
                        .trimIndent()
            )
            .convert { PackageFilter.parse(it) }
}
