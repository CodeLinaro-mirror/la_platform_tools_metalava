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
import com.android.tools.metalava.ARG_COMPILE_SDK_VERSION
import com.android.tools.metalava.ARG_SOURCE_FILES
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.psi.PsiModelOptions
import com.android.tools.metalava.model.source.SourceModelProvider
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.deprecated
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File

const val ARG_SOURCE_MODEL_PROVIDER = "--source-model-provider"

const val ARG_SOURCE_PATH = "--source-path"

const val ARG_STUB_PACKAGES = "--stub-packages"

const val ARG_COMPILED_SOURCES = "--compiled-sources"

const val ARG_USE_K1_UAST = "--Xuse-k1-uast"
const val ARG_USE_K2_UAST = "--Xuse-k2-uast"

const val ARG_JDK_HOME = "--jdk-home"
const val ARG_SDK_HOME = "--sdk-home"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SOURCE_OPTIONS_GROUP = "Sources"

class SourceOptions(
    private val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
) :
    OptionGroup(
        name = SOURCE_OPTIONS_GROUP,
        help =
            """
            Options that control which source files will be processed.
        """
                .trimIndent()
    ) {
    /** The name of the source model provider specified on the command line. */
    private val sourceModelProviderName by
        option(
                ARG_SOURCE_MODEL_PROVIDER,
                // Hidden from command line help for now.
                hidden = true,
            )
            .choice("psi", "turbine")
            .default("psi")
            .deprecated(
                """WARNING: The turbine model is under work and not usable for now. Eventually this option can be used to set the source model provider to either turbine or psi. The default is psi. """
                    .trimIndent()
            )

    /** Get the [SourceModelProvider] corresponding to [sourceModelProviderName]. */
    val sourceModelProvider: SourceModelProvider
        get() = SourceModelProvider.getImplementation(sourceModelProviderName)

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

    val compiledSourceJar by
        option(
                ARG_COMPILED_SOURCES,
                metavar = "<path>",
                help =
                    """
                        Jar file with the compiled version of $ARG_SOURCE_FILES, loaded in addition
                        to the source files. Used to include the bytecode version of Kotlin source
                        APIs.
                    """
                        .trimIndent(),
            )
            .existingFile()

    /**
     * The JDK to use as a platform, if set with [ARG_JDK_HOME]. This is only set when metalava is
     * used for non-Android projects.
     */
    val jdkHome by
        option(
                ARG_JDK_HOME,
                metavar = "<dir>",
                help =
                    """
                        If set, add the Java APIs from the given JDK to the classpath.
                    """
                        .trimIndent(),
            )
            .existingDir()

    /**
     * The JDK to use as a platform, if set with [ARG_SDK_HOME]. If this is set along with
     * [ARG_COMPILE_SDK_VERSION], metalava will automatically add the platform's android.jar file to
     * the classpath if it does not already find the android.jar file in the classpath.
     */
    val sdkHome by
        option(
                ARG_SDK_HOME,
                metavar = "<dir>",
                help =
                    """
                        If set, locate the `android.jar` file from the given Android SDK.
                    """
                        .trimIndent()
            )
            .existingDir()

    /** Whether to use the K1 compiler. */
    private val useK1UastOption by
        option(
                ARG_USE_K1_UAST,
                help = "Specifies whether the K1 compiler is used.",
            )
            .flag(default = false, defaultForHelp = "K1")

    /** Whether to use the K2 compiler. */
    private val useK2UastOption by
        option(
                ARG_USE_K2_UAST,
                help = "Specifies whether the K2 compiler is used.",
            )
            .flag(default = false, defaultForHelp = "K1")

    val modelOptions: ModelOptions by
        lazy(LazyThreadSafetyMode.NONE) {
            val useK2Uast =
                when {
                    useK1UastOption && useK2UastOption ->
                        cliError("Cannot specify both $ARG_USE_K1_UAST and $ARG_USE_K2_UAST")
                    useK1UastOption -> false
                    useK2UastOption -> true
                    else -> null
                }

            // If the option was specified on the command line then use [ModelOptions] created from
            // that
            useK2Uast?.let { useK2Uast ->
                ModelOptions.build("from command line") {
                    this[PsiModelOptions.useK2Uast] = useK2Uast
                }
            }
                // Otherwise, use the [ModelOptions] specified in the [TestEnvironment] if any.
                ?: executionEnvironment.testEnvironment?.modelOptions
                // Otherwise, use the default
                ?: ModelOptions.empty
        }
}
