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
import com.android.tools.metalava.cli.common.existingDir
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.map
import com.android.tools.metalava.cli.common.newFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"

const val ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS =
    "--remove-missing-class-references-in-api-levels"

const val ARG_CURRENT_VERSION = "--current-version"
const val ARG_FIRST_VERSION = "--first-version"
const val ARG_CURRENT_CODENAME = "--current-codename"

const val ARG_ANDROID_JAR_PATTERN = "--android-jar-pattern"

const val ARG_SDK_JAR_ROOT = "--sdk-extensions-root"
const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"

class ApiLevelsGenerationOptions :
    OptionGroup(
        name = "Api Levels Generation",
        help =
            """
                Options controlling the API levels file, e.g. `api-versions.xml` file.
            """
                .trimIndent()
    ) {
    /** API level XML file to generate. */
    val generateApiLevelXml: File? by
        option(
                ARG_GENERATE_API_LEVELS,
                metavar = "<xmlfile>",
                help =
                    """
                        Reads android.jar SDK files and generates an XML file recording the API
                        level for each class, method and field
                    """
                        .trimIndent(),
            )
            .newFile()

    /** Whether references to missing classes should be removed from the api levels file. */
    val removeMissingClassReferencesInApiLevels: Boolean by
        option(
                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                help =
                    """
                        Removes references to missing classes when generating the API levels XML
                        file. This can happen when generating the XML file for the non-updatable
                        portions of the module-lib sdk, as those non-updatable portions can
                        reference classes that are part of an updatable apex.
                    """
                        .trimIndent(),
            )
            .flag()

    /**
     * The first api level of the codebase; typically 1 but can be higher for example for the System
     * API.
     */
    val firstApiLevel: Int by
        option(
                ARG_FIRST_VERSION,
                metavar = "<numeric-version>",
                help =
                    """
                        Sets the first API level to generate an API database from.
                    """
                        .trimIndent()
            )
            .int()
            .default(1)

    /** The api level of the codebase, or null if not known/specified */
    val currentApiLevel: Int? by
        option(
                ARG_CURRENT_VERSION,
                metavar = "<numeric-version>",
                help =
                    """
                        Sets the current API level of the current source code. Must be greater than
                        or equal to 27.
                    """
                        .trimIndent(),
            )
            .int()
            .validate {
                if (it <= 26) {
                    throw MetalavaCliException("Suspicious currentApi=$it, expected at least 27")
                }
            }

    /**
     * The codename of the codebase: non-null string if this is a developer preview build, null if
     * this is a release build.
     */
    private val currentCodeName: String? by
        option(
                ARG_CURRENT_CODENAME,
                metavar = "<version-codename>",
                help =
                    """
                        Sets the code name for the current source code.
                    """
                        .trimIndent(),
            )
            .map { if (it == "REL") null else it }

    /** True if [currentCodeName] is specified, false otherwise. */
    val isDeveloperPreviewBuild
        get() = currentCodeName != null

    /** The list of patterns used to find matching jars in the set of files visible to Metalava. */
    val androidJarPatterns: List<String> by
        option(
                ARG_ANDROID_JAR_PATTERN,
                metavar = "<android-jar-pattern>",
                help =
                    """
                        Pattern to use to locate Android JAR files. Each pattern must contain a %
                        character that will be replaced with each API level that is being included
                        and if the result is an existing jar file then it will be taken as the
                        definition of the API at that level.
                    """
                        .trimIndent(),
            )
            .multiple(default = emptyList())
            .map {
                buildList {
                    addAll(it)
                    // Fallbacks
                    add("prebuilts/tools/common/api-versions/android-%/android.jar")
                    add("prebuilts/sdk/%/public/android.jar")
                }
            }

    /** Directory of prebuilt extension SDK jars that contribute to the API */
    val sdkJarRoot: File? by
        option(
                ARG_SDK_JAR_ROOT,
                metavar = "<sdk-jar-root>",
                help =
                    """
                        Points to root of prebuilt extension SDK jars, if any. This directory is
                        expected to contain snapshots of historical extension SDK versions in the
                        form of stub jars. The paths should be on the format
                        \"<int>/public/<module-name>.jar\", where <int> corresponds to the extension
                        SDK version, and <module-name> to the name of the mainline module.
                    """
                        .trimIndent(),
            )
            .existingDir()

    /**
     * Rules to filter out some extension SDK APIs from the API, and assign extensions to the APIs
     * that are kept
     */
    val sdkInfoFile: File? by
        option(
                ARG_SDK_INFO_FILE,
                metavar = "<sdk-info-file>",
                help =
                    """
                        Points to map of extension SDK APIs to include, if any. The file is a plain
                        text file and describes, per extension SDK, what APIs from that extension
                        to include in the file created via $ARG_GENERATE_API_LEVELS. The format of
                        each line is one of the following:
                        \"<module-name> <pattern> <ext-name> [<ext-name> [...]]\", where
                        <module-name> is the name of the mainline module this line refers to,
                        <pattern> is a common Java name prefix of the APIs this line refers to, and
                        <ext-name> is a list of extension SDK names in which these SDKs first
                        appeared, or \"<ext-name> <ext-id> <type>\", where <ext-name> is the name of
                        an SDK, <ext-id> its numerical ID and <type> is one of \"platform\" (the
                        Android platform SDK), \"platform-ext\" (an extension to the Android
                        platform SDK), \"standalone\" (a separate SDK). Fields are separated by
                        whitespace. A mainline module may be listed multiple times.
                        The special pattern \"*\" refers to all APIs in the given mainline module.
                        Lines beginning with # are comments.
                    """
                        .trimIndent(),
            )
            .existingFile()

    /**
     * Get label for [level].
     *
     * If a codename has been specified and [level] is greater than the current API level (which
     * defaults to `-1` when not set) then use the codename as the label, otherwise use the number
     * itself.
     */
    fun getApiLevelLabel(level: Int): String {
        val codename = currentCodeName
        val current = currentApiLevel
        return if (current == null || codename == null || level <= current) level.toString()
        else codename
    }

    /**
     * Check whether [level] should be included in documentation.
     *
     * If [isDeveloperPreviewBuild] is `true` then allow any API level as the documentation is not
     * going to be published outside Android, so it is safe to include all API levels, including the
     * next one.
     *
     * If no [currentApiLevel] has been provided then allow any API level as there is no way to
     * determine whether the API level is a future API or not.
     *
     * Otherwise, it is a release build so ignore any API levels after the current one.
     */
    fun includeApiLevelInDocumentation(level: Int): Boolean {
        if (isDeveloperPreviewBuild) return true
        val current = currentApiLevel ?: return true
        return level <= current
    }
}
