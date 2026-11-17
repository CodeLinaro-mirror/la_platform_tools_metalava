/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

val API_LEVELS_GENERATION_OPTIONS_HELP =
    """
Api Levels Generation:

  Options controlling the API levels file, e.g. `api-versions.xml` file.

  --api-version-for-sources <api-version>    Sets the API version of unfinalized apis in the current source code. This
                                             supports a single integer level, `major.minor`, `major.minor.patch` and
                                             `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                                             all non-negative integers and `quality` is an alphanumeric string.
  --api-version-signature-pattern <historical-api-pattern>
                                             Pattern to use to locate signature files. Typically ends with `.txt`.

                                             See `metalava help historical-api-patterns` for more information.
  --generate-api-version-history <output-file>
                                             Reads API signature files and generates a JSON or XML file depending on the
                                             extension, which must be one of `json` or `xml` respectively. The JSON file
                                             will record the API version in which each class, method, and field. was
                                             added in and (if applicable) deprecated in. The XML file will include that
                                             information and more but will be optimized to exclude information from
                                             class members which is the same as the containing class.
  --api-version-signature-files <files>      An ordered list of text API signature files. The oldest API version should
                                             be first, the newest last. This should not include a signature file for the
                                             current API version, which will be parsed from the provided source files.
                                             Not required to generate API version JSON if the current version is the
                                             only version.
    """
        .trimIndent()

class ApiLevelsGenerationOptionsTest :
    BaseOptionGroupTest<ApiLevelsGenerationOptions>(
        API_LEVELS_GENERATION_OPTIONS_HELP,
    ) {
    override fun createOptions() = ApiLevelsGenerationOptions()

    /** Get an optional [GenerateApiHistoryConfig] for a fake set of signature files. */
    private fun ApiLevelsGenerationOptions.fromFakeSignatureFiles() =
        fromSignatureFilesConfig(
            signatureFileLoader = SignatureFileLoader.THROWING,
            codebaseFragmentProvider = {
                error("Fake CodebaseFragment provider cannot create CodebaseFragment")
            },
        )

    @Test
    fun `Test api version for sources supports major-minor`() {
        runTest(ARG_API_VERSION_FOR_SOURCES, "1.2") {
            assertThat(options.apiVersionForSources.toString()).isEqualTo("1.2")
        }
    }

    @Test
    fun `Test api version for sources supports major-minor-patch`() {
        runTest(ARG_API_VERSION_FOR_SOURCES, "1.2.3") {
            assertThat(options.apiVersionForSources.toString()).isEqualTo("1.2.3")
        }
    }

    @Test
    fun `Test api version for sources supports major-minor-patch-preRelease`() {
        runTest(ARG_API_VERSION_FOR_SOURCES, "1.2.3-beta01") {
            assertThat(options.apiVersionForSources.toString()).isEqualTo("1.2.3-beta01")
        }
    }

    @Test
    fun `Test --current-version used alone with --generate-api-version-history`() {
        val apiVersionsJson = newFile("api-versions.json")
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "1.2.3-beta01",
            ARG_GENERATE_API_VERSION_HISTORY,
            apiVersionsJson.path
        ) {
            val apiHistoryConfig = options.fromFakeSignatureFiles()
            assertThat(apiHistoryConfig).isNotNull()
            val apiVersions = apiHistoryConfig!!.versionedApis.map { it.apiVersion }.joinToString()
            assertThat(apiVersions).isEqualTo("1.2.3-beta01")
        }
    }

    @Test
    fun `Test --current-version used with --generate-api-version-history and --api-version-signature-pattern`() {
        val signatureFile = newFile("1.2.0-alpha01/api.txt")
        val apiVersionsJson = temporaryFolder.newFile("api-versions.json")
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "1.2.3-beta01",
            ARG_GENERATE_API_VERSION_HISTORY,
            apiVersionsJson.path,
            ARG_API_VERSION_SIGNATURE_FILES,
            signatureFile.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "${temporaryFolder.root}/{version:major.minor.patch}*/api.txt",
        ) {
            val apiHistoryConfig = options.fromFakeSignatureFiles()
            assertThat(apiHistoryConfig).isNotNull()
            val apiVersions = apiHistoryConfig!!.versionedApis.map { it.apiVersion }.joinToString()
            assertThat(apiVersions).isEqualTo("1.2.0, 1.2.3-beta01")
        }
    }

    @Test
    fun `Test --api-version-signature-pattern with no matching pattern`() {
        val signatureFiles =
            listOf(
                newFile("1.1.0/api.txt"),
                // This will not be matched by the pattern.
                newFile("will/not/match/api.txt"),
            )
        val apiVersionsJson = temporaryFolder.newFile("api-versions.json")
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "1.2.3-beta01",
            ARG_GENERATE_API_VERSION_HISTORY,
            apiVersionsJson.path,
            ARG_API_VERSION_SIGNATURE_FILES,
            signatureFiles.joinToString(":"),
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "${temporaryFolder.root}/{version:major.minor.patch}/api.txt",
        ) {
            val exception =
                assertThrows(MetalavaCliException::class.java) { options.fromFakeSignatureFiles() }
            assertThat(removeTestSpecificDirectories(exception.message!!))
                .isEqualTo(
                    """
                        --api-version-signature-files: The following files were unmatched by a signature pattern:
                            TESTROOT/will/not/match/api.txt
                    """
                        .trimIndent()
                )
        }
    }
}
