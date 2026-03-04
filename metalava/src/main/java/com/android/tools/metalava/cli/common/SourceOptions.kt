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
import com.android.SdkConstants.FN_FRAMEWORK_LIBRARY
import com.android.tools.lint.detector.api.isJdkFolder
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.psi.PsiModelOptions
import com.android.tools.metalava.model.source.DEFAULT_JAVA_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.DEFAULT_KOTLIN_LANGUAGE_LEVEL
import com.android.tools.metalava.model.source.SourceModelProvider
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File
import org.jetbrains.jps.model.java.impl.JavaSdkUtil

const val ARG_SOURCE_MODEL_PROVIDER = "--source-model-provider"

const val ARG_SOURCE_PATH = "--source-path"
const val ARG_SOURCE_FILES = "--source-files"

const val ARG_JAVA_SOURCE = "--java-source"
const val ARG_KOTLIN_SOURCE = "--kotlin-source"

const val ARG_CLASS_PATH = "--classpath"
const val ARG_API_CLASS_RESOLUTION = "--api-class-resolution"

const val ARG_PROJECT = "--project"

const val ARG_STUB_PACKAGES = "--stub-packages"

const val ARG_COMPILED_SOURCES = "--compiled-sources"

const val ARG_MERGE_QUALIFIER_ANNOTATIONS = "--merge-qualifier-annotations"
const val ARG_MERGE_INCLUSION_ANNOTATIONS = "--merge-inclusion-annotations"

const val ARG_SKIP_READING_COMMENTS = "--ignore-comments"

const val ARG_USE_K1_UAST = "--Xuse-k1-uast"
const val ARG_USE_K2_UAST = "--Xuse-k2-uast"

const val ARG_JDK_HOME = "--jdk-home"
const val ARG_SDK_HOME = "--sdk-home"
const val ARG_COMPILE_SDK_VERSION = "--compile-sdk-version"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SOURCE_OPTIONS_GROUP = "Sources"

class SourceOptions(
    private val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),

    /** Provider of additional source files, i.e. those supplied as command line arguments. */
    private val additionalSourceFilesProvider: () -> List<File> = { emptyList() },
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
            )
            .choice("psi", "turbine")
            .default("psi")

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

    val sourceFiles by
        option(
                ARG_SOURCE_FILES,
                metavar = "<files>",
                help =
                    """
                A comma separated list of source files to be parsed. Can also be
                    @ followed by a path to a text file containing paths to the full set of files to parse.,
        """
                        .trimIndent()
            )
            .existingFile()
            .splitMultiple(",")
            // Append any additional source files to the list of sources.
            .map { it + additionalSourceFilesProvider() }

    /** The language level to use for Java files, set with [ARG_JAVA_SOURCE] */
    val javaLanguageLevelAsString by
        option(
                ARG_JAVA_SOURCE,
                metavar = "<level>",
                help = "Sets the source level for Java source files.",
            )
            .default(DEFAULT_JAVA_LANGUAGE_LEVEL)

    /** The language level to use for Kotlin files, set with [ARG_KOTLIN_SOURCE] */
    val kotlinLanguageLevelAsString by
        option(
                ARG_KOTLIN_SOURCE,
                metavar = "<level>",
                help = "Sets the source level for Kotlin source files."
            )
            .default(DEFAULT_KOTLIN_LANGUAGE_LEVEL)

    // For now, we don't distinguish between bootclasspath and classpath
    val classpath: List<File> by
        option(
                ARG_CLASS_PATH,
                metavar = "<paths>",
                help =
                    """
                        One or more directories or jars (separated by `${File.pathSeparator}`)
                        containing classes that should be on the classpath when parsing the source
                        files.
                    """
                        .trimIndent()
            )
            .existingDirOrJar()
            .splitMultiple(File.pathSeparator)
            .map { addSdkOrJdkJarsIfNeeded(it) }

    /** Update [classpath] to insert android.jar or JDK classpath elements if necessary. */
    private fun addSdkOrJdkJarsIfNeeded(classpath: List<File>): List<File> {
        val sdkHome = sdkHome
        val jdkHome = jdkHome
        if (sdkHome == null && jdkHome == null) {
            // Nothing to do.
            return classpath
        } else if (sdkHome != null && jdkHome != null) {
            cliError("Do not specify both $ARG_SDK_HOME and $ARG_JDK_HOME")
        }

        val compileSdkVersion = compileSdkVersion
        if (
            sdkHome != null &&
                compileSdkVersion != null &&
                classpath.none { it.name == FN_FRAMEWORK_LIBRARY }
        ) {
            val jar = File(sdkHome, "platforms/android-$compileSdkVersion")
            if (jar.isFile) {
                return classpath + jar
            } else {
                cliError(
                    "Could not find android.jar for API level $compileSdkVersion in SDK $sdkHome: $jar does not exist"
                )
            }
        } else if (jdkHome != null) {
            val isJre = !isJdkFolder(jdkHome)
            val roots = JavaSdkUtil.getJdkClassesRoots(jdkHome.toPath(), isJre).map { it.toFile() }
            return classpath + roots
        }

        return classpath
    }

    /** Lint project description that describes project's module structure in details */
    val projectDescription by
        option(
                ARG_PROJECT,
                metavar = "<xmlfile>",
                help = "Project description written in XML according to Lint's project model.",
            )
            .existingFile()

    val apiPackageFilter by
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
    private val sdkHome by
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

    /**
     * The compileSdkVersion, set by [ARG_COMPILE_SDK_VERSION]. For example, for R it would be "29".
     * For R preview, it would be "R".
     */
    private val compileSdkVersion: String? by
        option(ARG_COMPILE_SDK_VERSION, metavar = "<api>", help = "Use the given API level.")

    /** Existing external annotation files to merge in */
    val mergeQualifierAnnotations by
        option(
                ARG_MERGE_QUALIFIER_ANNOTATIONS,
                metavar = "<file-or-dir>",
                help =
                    """
                An external annotations file to merge and overlay the sources, or a directory of
                such files. Should be used for annotations intended for inclusion in the API to be
                written out, e.g. nullability. Formats supported are: IntelliJ's external
                annotations database format, .jar or .zip files containing those, Android signature
                files, and Java stub files.
            """
                        .trimIndent(),
            )
            .existingDirOrFile()
            .multiple()

    val mergeInclusionAnnotations by
        option(
                ARG_MERGE_INCLUSION_ANNOTATIONS,
                metavar = "<file-or-dir>",
                help =
                    """
                An external annotations file to merge and overlay the sources, or a directory of
                such files. Should be used for annotations which determine inclusion in the API to
                be written out, i.e. show and hide. The only format supported is Java stub files.
            """
                        .trimIndent(),
            )
            .existingDirOrFile()
            .multiple()

    /** Determines whether comments are read when processing sources. */
    private val skipReadingComments by
        option(
                ARG_SKIP_READING_COMMENTS,
                help = "Ignore any comments in source files.",
            )
            .flag()

    /**
     * Whether to allow reading comments from the sources.
     *
     * If `true` then source comments will be read and [SelectableItem.documentation] will not be
     * `null` (unless the [SelectableItem] is `private`). If `false` then
     * [SelectableItem.documentation] will always be `null`.
     */
    val allowReadingComments
        get() = !skipReadingComments

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
