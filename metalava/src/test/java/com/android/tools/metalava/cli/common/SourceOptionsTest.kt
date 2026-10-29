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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

val SOURCE_OPTIONS_HELP =
    """
Sources:

  Options that control which source files will be processed.

  --source-model-provider [psi|turbine]      (default: psi)
  --source-path <path>                       A : separated list of directories containing source files (organized in a
                                             standard Java package hierarchy).
  --source-files <files>                     A comma separated list of source files to be parsed. Can also be @ followed
                                             by a path to a text file containing paths to the full set of files to
                                             parse.,
  --java-source <level>                      Sets the source level for Java source files. (default: 1.8)
  --kotlin-source <level>                    Sets the source level for Kotlin source files. (default: 1.9)
  --classpath <paths>                        One or more directories or jars (separated by `:`) containing classes that
                                             should be on the classpath when parsing the source files.
  --project <xmlfile>                        Project description written in XML according to Lint's project model.
  --stub-packages <package-list>             List of packages (separated by :) which will be used to filter out
                                             irrelevant classes. If specified, only classes in these packages will be
                                             included in signature files, stubs, etc.. This is not limited to just the
                                             stubs; the --stub-packages name is historical.

                                             See `metalava help package-filters` for more information.
  --compiled-sources <path>                  Jar file with the compiled version of --source-files, loaded in addition to
                                             the source files. Used to include the bytecode version of Kotlin source
                                             APIs.
  --jdk-home <dir>                           If set, add the Java APIs from the given JDK to the classpath.
  --sdk-home <dir>                           If set, locate the `android.jar` file from the given Android SDK.
  --compile-sdk-version <api>                Use the given API level.
  --merge-qualifier-annotations <file-or-dir>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations intended for
                                             inclusion in the API to be written out, e.g. nullability. Formats supported
                                             are: IntelliJ's external annotations database format, .jar or .zip files
                                             containing those, Android signature files, and Java stub files.
  --merge-inclusion-annotations <file-or-dir>
                                             An external annotations file to merge and overlay the sources, or a
                                             directory of such files. Should be used for annotations which determine
                                             inclusion in the API to be written out, i.e. show and hide. The only format
                                             supported is Java stub files.
  --ignore-comments                          Ignore any comments in source files.
    """
        .trimIndent()

class SourceOptionsTest :
    BaseOptionGroupTest<SourceOptions>(
        SOURCE_OPTIONS_HELP,
    ) {

    override fun createOptions() = SourceOptions()

    @Test
    fun `Test source model provider - psi`() {
        runTest(ARG_SOURCE_MODEL_PROVIDER, "psi") {
            assertThat(options.sourceModelProvider.providerName).isEqualTo("psi")
        }
    }

    @Test
    fun `Test source model provider - turbine`() {
        runTest(ARG_SOURCE_MODEL_PROVIDER, "turbine") {
            assertThat(options.sourceModelProvider.providerName).isEqualTo("turbine")
        }
    }
}
