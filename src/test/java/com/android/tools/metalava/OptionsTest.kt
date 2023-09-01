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

import com.android.tools.metalava.cli.common.ARG_NO_COLOR
import com.android.tools.metalava.cli.common.REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("PrivatePropertyName")
class OptionsTest : DriverTest() {
    private val FLAGS =
        """
API sources:
--source-files <files>
                                             A comma separated list of source files to be parsed. Can also be @ followed
                                             by a path to a text file containing paths to the full set of files to
                                             parse.
--source-path <paths>
                                             One or more directories (separated by `:`) containing source files (within
                                             a package hierarchy).
--classpath <paths>
                                             One or more directories or jars (separated by `:`) containing classes that
                                             should be on the classpath when parsing the source files
--merge-qualifier-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations intended for
                                             inclusion in the API to be written out, e.g. nullability. Formats supported
                                             are: IntelliJ's external annotations database format, .jar or .zip files
                                             containing those, Android signature files, and Java stub files.
--merge-inclusion-annotations <file>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations which determine
                                             inclusion in the API to be written out, i.e. show and hide. The only format
                                             supported is Java stub files.
--validate-nullability-from-merged-stubs
                                             Triggers validation of nullability annotations for any class where
                                             --merge-qualifier-annotations includes a Java stub file.
--validate-nullability-from-list
                                             Triggers validation of nullability annotations for any class listed in the
                                             named file (one top-level class per line, # prefix for comment line).
--nullability-warnings-txt <file>
                                             Specifies where to write warnings encountered during validation of
                                             nullability annotations. (Does not trigger validation by itself.)
--nullability-errors-non-fatal
                                             Specifies that errors encountered during validation of nullability
                                             annotations should not be treated as errors. They will be written out to
                                             the file specified in --nullability-warnings-txt instead.
--input-api-jar <file>
                                             A .jar file to read APIs from directly
--hide-package <package>
                                             Remove the given packages from the API even if they have not been marked
                                             with @hide
--show-annotation <annotation class>
                                             Unhide any hidden elements that are also annotated with the given
                                             annotation
--show-single-annotation <annotation>
                                             Like --show-annotation, but does not apply to members; these must also be
                                             explicitly annotated
--show-for-stub-purposes-annotation <annotation class>
                                             Like --show-annotation, but elements annotated with it are assumed to be
                                             "implicitly" included in the API surface, and they'll be included in
                                             certain kinds of output such as stubs, but not in others, such as the
                                             signature file and API lint
--hide-annotation <annotation class>
                                             Treat any elements annotated with the given annotation as hidden
--show-unannotated
                                             Include un-annotated public APIs in the signature file as well
--java-source <level>
                                             Sets the source level for Java source files; default is 1.8.
--kotlin-source <level>
                                             Sets the source level for Kotlin source files; default is 1.9.
--sdk-home <dir>
                                             If set, locate the `android.jar` file from the given Android SDK
--compile-sdk-version <api>
                                             Use the given API level
--jdk-home <dir>
                                             If set, add the Java APIs from the given JDK to the classpath
--stub-packages <package-list>
                                             List of packages (separated by :) which will be used to filter out
                                             irrelevant code. If specified, only code in these packages will be included
                                             in signature files, stubs, etc. (This is not limited to just the stubs; the
                                             name is historical.) You can also use ".*" at the end to match subpackages,
                                             so `foo.*` will match both `foo` and `foo.bar`.
--subtract-api <api file>
                                             Subtracts the API in the given signature or jar file from the current API
                                             being emitted via --api, --stubs, --doc-stubs, etc. Note that the
                                             subtraction only applies to classes; it does not subtract members.
--ignore-classes-on-classpath
                                             Prevents references to classes on the classpath from being added to the
                                             generated stub files.


Extracting Signature Files:
--dex-api <file>
                                             Generate a DEX signature descriptor file listing the APIs
--proguard <file>
                                             Write a ProGuard keep file for the API
--sdk-values <dir>
                                             Write SDK values files to the given directory


Generating Stubs:
--doc-stubs <dir>
                                             Generate documentation stub source files for the API. Documentation stub
                                             files are similar to regular stub files, but there are some differences.
                                             For example, in the stub files, we'll use special annotations like
                                             @RecentlyNonNull instead of @NonNull to indicate that an element is
                                             recently marked as non null, whereas in the documentation stubs we'll just
                                             list this as @NonNull. Another difference is that @doconly elements are
                                             included in documentation stubs, but not regular stubs, etc.
--kotlin-stubs
                                             [CURRENTLY EXPERIMENTAL] If specified, stubs generated from Kotlin source
                                             code will be written in Kotlin rather than the Java programming language.
--pass-through-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be passed through unchanged.
--exclude-annotation <annotation classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be stripped from metalava's outputs.
--enhance-documentation
                                             Enhance documentation in various ways, for example auto-generating
                                             documentation based on source annotations present in the code. This is
                                             implied by --doc-stubs.
--exclude-documentation-from-stubs
                                             Exclude element documentation (javadoc and kdoc) from the generated stubs.
                                             (Copyright notices are not affected by this, they are always included.
                                             Documentation stubs (--doc-stubs) are not affected.)
--write-stubs-source-list <file>
                                             Write the list of generated stub files into the given source list file. If
                                             generating documentation stubs and you haven't also specified
                                             --write-doc-stubs-source-list, this list will refer to the documentation
                                             stubs; otherwise it's the non-documentation stubs.
--write-doc-stubs-source-list <file>
                                             Write the list of generated doc stub files into the given source list file


Diffs and Checks:
--check-compatibility:type:released <file>
                                             Check compatibility. Type is one of 'api' and 'removed', which checks
                                             either the public api or the removed api.
--check-compatibility:base <file>
                                             When performing a compat check, use the provided signature file as a base
                                             api, which is treated as part of the API being checked. This allows us to
                                             compute the full API surface from a partial API surface (e.g. the current
                                             @SystemApi txt file), which allows us to recognize when an API is moved
                                             from the partial API to the base API and avoid incorrectly flagging this as
                                             an API removal.
--api-lint [api file]
                                             Check API for Android API best practices. If a signature file is provided,
                                             only the APIs that are new since the API will be checked.
--api-lint-ignore-prefix [prefix]
                                             A list of package prefixes to ignore API issues in when running with
                                             --api-lint.
--migrate-nullness <api file>
                                             Compare nullness information with the previous stable API and mark newly
                                             annotated APIs as under migration.
--warnings-as-errors
                                             Promote all warnings to errors
--lints-as-errors
                                             Promote all API lint warnings to errors
--report-even-if-suppressed <file>
                                             Write all issues into the given file, even if suppressed (via annotation or
                                             baseline) but not if hidden (by '--hide' or '--hide-category')
--baseline <file>
                                             Filter out any errors already reported in the given baseline file, or
                                             create if it does not already exist
--update-baseline [file]
                                             Rewrite the existing baseline file with the current set of warnings. If
                                             some warnings have been fixed, this will delete them from the baseline
                                             files. If a file is provided, the updated baseline is written to the given
                                             file; otherwise the original source baseline file is updated.
--baseline:api-lint <file> --update-baseline:api-lint [file]
                                             Same as --baseline and --update-baseline respectively, but used
                                             specifically for API lint issues performed by --api-lint.
--baseline:compatibility:released <file> --update-baseline:compatibility:released [file]
                                             Same as --baseline and --update-baseline respectively, but used
                                             specifically for API compatibility issues performed by
                                             --check-compatibility:api:released and
                                             --check-compatibility:removed:released.
--merge-baseline [file]
                                             Like --update-baseline, but instead of always replacing entries in the
                                             baseline, it will merge the existing baseline with the new baseline. This
                                             is useful if metalava runs multiple times on the same source tree with
                                             different flags at different times, such as occasionally with --api-lint.
--pass-baseline-updates
                                             Normally, encountering error will fail the build, even when updating
                                             baselines. This flag allows you to tell metalava to continue without
                                             errors, such that all the baselines in the source tree can be updated in
                                             one go.
--delete-empty-baselines
                                             Whether to delete baseline files if they are updated and there is nothing
                                             to include.
--error-message:api-lint <message>
                                             If set, metalava shows it when errors are detected in --api-lint.
--error-message:compatibility:released <message>
                                             If set, metalava shows it when errors are detected in
                                             --check-compatibility:api:released and
                                             --check-compatibility:removed:released.


JDiff:
--api-xml <file>
                                             Like --api, but emits the API in the JDiff XML format instead
--convert-to-jdiff <sig> <xml>
                                             Reads in the given signature file, and writes it out in the JDiff XML
                                             format. Can be specified multiple times.
--convert-new-to-jdiff <old> <new> <xml>
                                             Reads in the given old and new api files, computes the difference, and
                                             writes out only the new parts of the API in the JDiff XML format.


Extracting Annotations:
--extract-annotations <zipfile>
                                             Extracts source annotations from the source files and writes them into the
                                             given zip file
--copy-annotations <source> <dest>
                                             For a source folder full of annotation sources, generates corresponding
                                             package private versions of the same annotations.
--include-source-retention
                                             If true, include source-retention annotations in the stub files. Does not
                                             apply to signature files. Source retention annotations are extracted into
                                             the external annotations files instead.


Injecting API Levels:
--apply-api-levels <api-versions.xml>
                                             Reads an XML file containing API level descriptions and merges the
                                             information into the documentation


Extracting API Levels:
--generate-api-levels <xmlfile>
                                             Reads android.jar SDK files and generates an XML file recording the API
                                             level for each class, method and field
--remove-missing-class-references-in-api-levels
                                             Removes references to missing classes when generating the API levels XML
                                             file. This can happen when generating the XML file for the non-updatable
                                             portions of the module-lib sdk, as those non-updatable portions can
                                             reference classes that are part of an updatable apex.
--android-jar-pattern <pattern>
                                             Patterns to use to locate Android JAR files. The default is
                                             ${"$"}ANDROID_HOME/platforms/android-%/android.jar.
--first-version
                                             Sets the first API level to generate an API database from; usually 1
--current-version
                                             Sets the current API level of the current source code
--current-codename
                                             Sets the code name for the current source code
--current-jar
                                             Points to the current API jar, if any
--sdk-extensions-root
                                             Points to root of prebuilt extension SDK jars, if any. This directory is
                                             expected to contain snapshots of historical extension SDK versions in the
                                             form of stub jars. The paths should be on the format
                                             "<int>/public/<module-name>.jar", where <int> corresponds to the extension
                                             SDK version, and <module-name> to the name of the mainline module.
--sdk-extensions-info
                                             Points to map of extension SDK APIs to include, if any. The file is a plain
                                             text file and describes, per extension SDK, what APIs from that extension
                                             to include in the file created via --generate-api-levels. The format of
                                             each line is one of the following: "<module-name> <pattern> <ext-name>
                                             [<ext-name> [...]]", where <module-name> is the name of the mainline module
                                             this line refers to, <pattern> is a common Java name prefix of the APIs
                                             this line refers to, and <ext-name> is a list of extension SDK names in
                                             which these SDKs first appeared, or "<ext-name> <ext-id> <type>", where
                                             <ext-name> is the name of an SDK, <ext-id> its numerical ID and <type> is
                                             one of "platform" (the Android platform SDK), "platform-ext" (an extension
                                             to the Android platform SDK), "standalone" (a separate SDK). Fields are
                                             separated by whitespace. A mainline module may be listed multiple times.
                                             The special pattern "*" refers to all APIs in the given mainline module.
                                             Lines beginning with # are comments.


Generating API version history:
--generate-api-version-history <jsonfile>
                                             Reads API signature files and generates a JSON file recording the API
                                             version each class, method, and field was added in and (if applicable)
                                             deprecated in. Required to generate API version JSON.
--api-version-signature-files <files>
                                             An ordered list of text API signature files. The oldest API version should
                                             be first, the newest last. This should not include a signature file for the
                                             current API version, which will be parsed from the provided source files.
                                             Not required to generate API version JSON if the current version is the
                                             only version.
--api-version-names <strings>
                                             An ordered list of strings with the names to use for the API versions from
                                             --api-version-signature-files, and the name of the current API version.
                                             Required to generate API version JSON.


Environment Variables:
METALAVA_DUMP_ARGV
                                             Set to true to have metalava emit all the arguments it was invoked with.
                                             Helpful when debugging or reproducing under a debugger what the build
                                             system is doing.
METALAVA_PREPEND_ARGS
                                             One or more arguments (concatenated by space) to insert into the command
                                             line, before the documentation flags.
METALAVA_APPEND_ARGS
                                             One or more arguments (concatenated by space) to append to the end of the
                                             command line, after the generate documentation flags.

    """
            .trimIndent()

    private val USAGE =
        """
Usage: metalava [options] [flags]... <sub-command>? ...
        """
            .trimIndent()

    /**
     * The options from [com.android.tools.metalava.cli.common.CommonOptions] plus Clikt defined
     * options from [Options].
     */
    private val CLIKT_OPTIONS =
        """
Options:
  --version                                  Show the version and exit
  --color, --no-color                        Determine whether to use terminal capabilities to colorize and otherwise
                                             style the output. (default: true if ${"$"}TERM starts with `xterm` or ${"$"}COLORTERM
                                             is set)
  --no-banner                                A banner is never output so this has no effect (deprecated: please remove)
  --quiet, --verbose                         Set the verbosity of the output.
                                             --quiet - Only include vital output.
                                             --verbose - Include extra diagnostic output.
                                             (default: Neither --quiet or --verbose)
  -h, --help                                 Show this message and exit
  --api-class-resolution [api|api:classpath]
                                             Determines how class resolution is performed when loading API signature
                                             files. Any classes that cannot be found will be treated as empty.",

                                             api - will only look for classes in the API signature files.

                                             api:classpath (default) - will look for classes in the API signature files
                                             first and then in the classpath.
  --suppress-compatibility-meta-annotation <meta-annotation class>
                                             Suppress compatibility checks for any elements within the scope of an
                                             annotation which is itself annotated with the given meta-annotation.
  --manifest <file>                          A manifest file, used to check permissions to cross check APIs and retrieve
                                             min_sdk_version. (default: no manifest)
  --typedefs-in-signatures [none|ref|inline]
                                             Whether to include typedef annotations in signature files.

                                             none (default) - will not include typedef annotations in signature.

                                             ref - will include just a reference to the typedef class, which is not
                                             itself part of the API and is not included as a class

                                             inline - will include the constants themselves into each usage site

$REPORTING_OPTIONS_HELP

Signature File Output:

  Options controlling the signature file output. The format of the generated file is determined by the options in the
  `Signature Format Output` section.

  --api <file>                               Output file into which the API signature will be generated. If this is not
                                             specified then no API signature file will be created.
  --removed-api <file>                       Output file into which the API signatures for removed APIs will be
                                             generated. If this is not specified then no removed API signature file will
                                             be created.

$SIGNATURE_FORMAT_OPTIONS_HELP

$STUB_GENERATION_OPTIONS_HELP
"""
            .trimIndent()

    private val SUB_COMMANDS =
        """
Sub-commands:
  android-jars-to-signatures                 Rewrite the signature files in the `prebuilts/sdk` directory in the Android
                                             source tree.
  help                                       Provides help for general metalava concepts
  merge-signatures                           Merge multiple signature files together into a single file.
  signature-to-jdiff                         Convert an API signature file into a file in the JDiff XML format.
  update-signature-header                    Updates the header of signature files to a different format.
  version                                    Show the version
        """
            .trimIndent()

    private val MAIN_HELP_BODY =
        """
$CLIKT_OPTIONS

Arguments:
  flags                                      See below.

$SUB_COMMANDS


$FLAGS
        """
            .trimIndent()

    @Test
    fun `Test invalid arguments`() {
        val args = listOf(ARG_NO_COLOR, "--blah-blah-blah")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        assertEquals("", stdout.toString())
        assertEquals(
            """

Aborting: Error: no such option: "--blah-blah-blah"

$USAGE

$MAIN_HELP_BODY
            """
                .trimIndent(),
            stderr.toString()
        )
    }

    @Test
    fun `Test invalid value`() {
        val args = listOf(ARG_NO_COLOR, "--api-class-resolution", "foo")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        assertEquals("", stdout.toString())
        assertEquals(
            """

Aborting: Usage: metalava [options] [flags]... <sub-command>? ...

Error: Invalid value for "--api-class-resolution": invalid choice: foo. (choose from api, api:classpath)

            """
                .trimIndent(),
            stderr.toString()
        )
    }

    @Test
    fun `Test help`() {
        val args = listOf(ARG_NO_COLOR, "--help")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        assertEquals("", stderr.toString())
        assertEquals(
            """

$USAGE

  Extracts metadata from source code to generate artifacts such as the signature files, the SDK stub files, external
  annotations etc.

$MAIN_HELP_BODY
            """
                .trimIndent(),
            stdout.toString()
        )
    }

    @Test
    fun `Test version`() {
        val args = listOf(ARG_NO_COLOR, "--version")

        val stdout = StringWriter()
        val stderr = StringWriter()
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )
        assertEquals("", stderr.toString())
        assertEquals(
            """

                metalava version: 1.0.0-alpha09

            """
                .trimIndent(),
            stdout.toString()
        )
    }

    @Test
    fun `Test for @ usage on command line`() {
        check(showAnnotations = arrayOf("@foo.Show"))
    }
}
