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
import com.android.tools.metalava.apilevels.VersionedApi
import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

val API_LEVELS_GENERATION_OPTIONS_HELP =
    """
Api Levels Generation:

  Options controlling the API levels file, e.g. `api-versions.xml` file.

  --generate-api-levels <xmlfile>            Reads android.jar SDK files and generates an XML file recording the API
                                             level for each class, method and field. if --api-version-for-sources is
                                             provided, sources will also be included in the file with the api level
                                             --api-version-for-sources
  --api-version-for-sources <api-version>    Sets the API version of unfinalized apis in the current source code. This
                                             supports a single integer level, `major.minor`, `major.minor.patch` and
                                             `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                                             all non-negative integers and `quality` is an alphanumeric string.
  --remove-missing-class-references-in-api-levels
                                             Removes references to missing classes when generating the API levels XML
                                             file. This can happen when generating the XML file for the non-updatable
                                             portions of the module-lib sdk, as those non-updatable portions can
                                             reference classes that are part of an updatable apex.
  --api-version-range <api-version>:<api-version>
                                             The optional range of historical versions that can be included in the API
                                             version history. The `from` and `to` parts of the range are separated by a
                                             `:` and are both inclusive. See --api-version-for-sources for acceptable
                                             `<api-version>`s.

                                             If unspecified, version history will default to include every historical
                                             version.
  --sdk-extension-version-range <api-version>:<api-version>
                                             The optional range of historical sdk extensions versions that can be
                                             included in the API version history. The `from` and `to` parts of the range
                                             are separated by a `:` and are both inclusive. See
                                             --api-version-for-sources for acceptable `<api-version>`s.

                                             If unspecified then allow every historical version.
  --android-jar-pattern <historical-api-pattern>
                                             Pattern to use to locate Android JAR files. Must end with `.jar`.

                                             See `metalava help historical-api-patterns` for more information.
  --api-version-signature-pattern <historical-api-pattern>
                                             Pattern to use to locate signature files. Typically ends with `.txt`.

                                             See `metalava help historical-api-patterns` for more information.
  --sdk-extensions-info <sdk-info-file>      Points to map of extension SDK APIs to include, if any. The file is a plain
                                             text file and describes, per extension SDK, what APIs from that extension
                                             to include in the file created via --generate-api-levels. The format of
                                             each line is one of the following: \"<module-name> <pattern> <ext-name>
                                             [<ext-name> [...]]\", where <module-name> is the name of the mainline
                                             module this line refers to, <pattern> is a common Java name prefix of the
                                             APIs this line refers to, and <ext-name> is a list of extension SDK names
                                             in which these SDKs first appeared, or \"<ext-name> <ext-id> <type>\",
                                             where <ext-name> is the name of an SDK, <ext-id> its numerical ID and
                                             <type> is one of \"platform\" (the Android platform SDK), \"platform-ext\"
                                             (an extension to the Android platform SDK), \"standalone\" (a separate
                                             SDK). Fields are separated by whitespace. A mainline module may be listed
                                             multiple times. The special pattern \"*\" refers to all APIs in the given
                                             mainline module. Lines beginning with # are comments.

                                             If specified then the --android-jar-pattern must include at least one
                                             pattern that uses `{version:extension}` and `{module}` placeholders and
                                             that pattern must match at least one file.
  --api-version-for-sdk-extension <api-version>
                                             SDK extension APIs can be added between SDK versions and they do not become
                                             available in an SDK version until the next SDK version is released.
                                             However, when generating an API history it is required that every API is in
                                             an API version, even those added as part of an SDK extension.

                                             If an SDK extension is being prepared for inclusion in an SDK version then
                                             this should be the SDK version. If an SDK extension is being prepared
                                             between SDK versions than this should be a magic version number that
                                             indicates that it the newly added SDK extension APIs are not yet present in
                                             any SDK version.

                                             In the latter case the --api-version-label should be used when generating
                                             documentation from that `api-versions.xml` file to give the magic version a
                                             meaningful name in the documentation.
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

    /**
     * Get an optional [GenerateApiHistoryConfig] from
     * [ApiLevelsGenerationOptions.forAndroidConfig].
     */
    private fun ApiLevelsGenerationOptions.testForAndroidConfig() =
        forAndroidConfig(SignatureFileLoader.THROWING) { error("no codebase fragment") }

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
            assertThat(cleanupString(exception.message!!))
                .isEqualTo(
                    """
                        --api-version-signature-files: The following files were unmatched by a signature pattern:
                            TESTROOT/will/not/match/api.txt
                    """
                        .trimIndent()
                )
        }
    }

    /** Create a simple `sdk-extension-info.xml` for testing. */
    private fun createSdkExtensionsInfoXml() =
        temporaryFolder.newFile("sdk-extensions-info.xml").apply {
            writeText(
                """
                        <?xml version="1.0" encoding="utf-8"?>
                        <sdk-extensions-info>
                        <sdk id="7"
                             shortname="seven"
                             name="Seven Extensions"
                             reference="Seven" />
                        <symbol jar="bar" pattern="foo" sdks="seven" />
                        <symbol jar="baz" pattern="foo" sdks="seven" />
                        <symbol jar="foo" pattern="foo" sdks="seven" />
                        </sdk-extensions-info>
                    """
                    .trimIndent()
            )
        }

    /** Dump the contents of this list to a string. */
    private fun List<VersionedApi>.dump() = cleanupString(joinToString("\n"))

    @Test
    fun `Test multiple jar files for version forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                }
            }
            dir("2") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                    emptyFile("baz.jar")
                }
            }
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_ANDROID_JAR_PATTERN,
            "$root/{version:level}/*/{library}.jar",
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedJarApi(jar=TESTROOT/1/public/{bar,foo}.jar, updater=ApiVersionUpdater(version=1))
                        VersionedJarApi(jar=TESTROOT/2/public/{bar,baz,foo}.jar, updater=ApiVersionUpdater(version=2))
                        VersionedSourceApi(version=30)
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test extension jar files in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                }
            }
            dir("2") {
                dir("public") {
                    emptyFile("foo.jar")
                    emptyFile("bar.jar")
                    emptyFile("baz.jar")
                }
            }
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_ANDROID_JAR_PATTERN,
            "$root/{version:extension}/*/{module}.jar",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedSourceApi(version=30)
                        VersionedJarApi(jar=TESTROOT/1/public/bar.jar, updater=ExtensionUpdater(extVersion=1, module=bar, nextSdkVersion=30))
                        VersionedJarApi(jar=TESTROOT/2/public/bar.jar, updater=ExtensionUpdater(extVersion=2, module=bar, nextSdkVersion=30))
                        VersionedJarApi(jar=TESTROOT/2/public/baz.jar, updater=ExtensionUpdater(extVersion=2, module=baz, nextSdkVersion=30))
                        VersionedJarApi(jar=TESTROOT/1/public/foo.jar, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=30))
                        VersionedJarApi(jar=TESTROOT/2/public/foo.jar, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=30))
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test signature files in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("1.1") {
                dir("public") { emptyFile("api.txt") }
                dir("system") { emptyFile("api.txt") }
            }
            dir("2") {
                dir("public") { emptyFile("api.txt") }
                dir("system") { emptyFile("api.txt") }
            }
            dir("extensions") {
                dir("1") {
                    dir("public") {
                        emptyFile("foo.txt")
                        emptyFile("bar.txt")
                    }
                    dir("system") { emptyFile("foo.txt") }
                }
                dir("2") {
                    dir("public") {
                        emptyFile("foo.txt")
                        emptyFile("bar.txt")
                        emptyFile("baz.txt")
                    }
                    dir("system") { emptyFile("foo.txt") }
                }
            }
        }

        val apiSurfaces =
            ApiSurfaces.build {
                createSurface("public")
                createSurface("system", isMain = true)
            }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:major.minor?}/{surface}/api.txt",
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/extensions/{version:extension}/{surface}/{module}.txt",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
            optionGroup = ApiLevelsGenerationOptions(apiSurfacesProvider = { apiSurfaces }),
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSignatureApi(files=TESTROOT/1.1/{public,system}/api.txt, updater=ApiVersionUpdater(version=1.1))
                        VersionedSignatureApi(files=TESTROOT/2/{public,system}/api.txt, updater=ApiVersionUpdater(version=2))
                        VersionedSourceApi(version=30)
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/bar.txt, updater=ExtensionUpdater(extVersion=1, module=bar, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/bar.txt, updater=ExtensionUpdater(extVersion=2, module=bar, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/baz.txt, updater=ExtensionUpdater(extVersion=2, module=baz, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/1/{public,system}/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/{public,system}/foo.txt, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=30))
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test invalid --api-version-range`() {
        val root = temporaryFolder.root

        val apiSurfaces = ApiSurfaces.build { createSurface("public", isMain = true) }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        runTest(
            ARG_API_VERSION_RANGE,
            "9",
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:major.minor?}/{surface}/api.txt",
            optionGroup = ApiLevelsGenerationOptions(apiSurfacesProvider = { apiSurfaces }),
        ) {
            assertEquals("", stdout)
            assertEquals(
                """Invalid value for "--api-version-range": Must be of the form <version>:<version> but found '9'""",
                stderr
            )
        }
    }

    @Test
    fun `Test --api-version-range in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("1.1") { dir("public") { emptyFile("api.txt") } }
            // This should be ignored by the --api-version-range.
            dir("2") { dir("public") { emptyFile("api.txt") } }
        }

        val apiSurfaces = ApiSurfaces.build { createSurface("public", isMain = true) }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        runTest(
            ARG_API_VERSION_RANGE,
            "1:1.1",
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:major.minor?}/{surface}/api.txt",
            optionGroup = ApiLevelsGenerationOptions(apiSurfacesProvider = { apiSurfaces }),
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSignatureApi(files=TESTROOT/1.1/public/api.txt, updater=ApiVersionUpdater(version=1.1))
                        VersionedSourceApi(version=30)
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test --sdk-extension-version-range in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("28") { dir("public") { emptyFile("api.txt") } }
            dir("29") { dir("public") { emptyFile("api.txt") } }

            dir("extensions") {
                dir("1") { dir("public") { emptyFile("foo.txt") } }
                dir("2") { dir("public") { emptyFile("foo.txt") } }
                dir("3") { dir("public") { emptyFile("foo.txt") } }
            }
        }

        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()

        val apiSurfaces = ApiSurfaces.build { createSurface("public", isMain = true) }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        runTest(
            ARG_SDK_EXTENSION_VERSION_RANGE,
            "1:2",
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:major.minor?}/{surface}/api.txt",
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/extensions/{version:extension}/{surface}/{module}.txt",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
            optionGroup = ApiLevelsGenerationOptions(apiSurfacesProvider = { apiSurfaces }),
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedSignatureApi(files=TESTROOT/28/public/api.txt, updater=ApiVersionUpdater(version=28))
                        VersionedSignatureApi(files=TESTROOT/29/public/api.txt, updater=ApiVersionUpdater(version=29))
                        VersionedSourceApi(version=30)
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/foo.txt, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=30))
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test nextSdkVersion in forAndroidConfig`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("extensions") { dir("1") { dir("public") { emptyFile("foo.txt") } } }
        }

        val apiSurfaces = ApiSurfaces.build { createSurface("public", isMain = true) }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SDK_EXTENSION,
            "123456789",
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:major.minor?}/{surface}/api.txt",
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/extensions/{version:extension}/{surface}/{module}.txt",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
            optionGroup = ApiLevelsGenerationOptions(apiSurfacesProvider = { apiSurfaces }),
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            // Compute the list of versioned files.
            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSourceApi(version=30)
                        VersionedSourceApi(version=123456789)
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=123456789))
                    """
                        .trimIndent()
                )
        }
    }

    @Test
    fun `Test no extension jar files found in forAndroidConfig`() {
        val root = getOrCreateFolder()

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_ANDROID_JAR_PATTERN,
            "$root/{version:extension}/*/{module}.jar",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
        ) {
            val exception =
                assertThrows(IllegalArgumentException::class.java) {
                    options.testForAndroidConfig()
                }

            assertThat(exception.message)
                .isEqualTo(
                    "no extension api files found by $root/{version:extension}/*/{module}.jar"
                )
        }
    }

    @Test
    fun `Test do not mix signature and android jar patterns in forAndroidConfig`() {
        val root = getOrCreateFolder()

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "30",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_ANDROID_JAR_PATTERN,
            "$root/{version:extension}/*/{module}.jar",
            ARG_API_VERSION_SIGNATURE_PATTERN,
            "$root/{version:extension}/*/{module}.txt",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
        ) {
            val exception =
                assertThrows(MetalavaCliException::class.java) { options.testForAndroidConfig() }

            assertThat(exception.message)
                .isEqualTo(
                    "Cannot combine --api-version-signature-pattern with --android-jar-pattern"
                )
        }
    }

    @Test
    fun `Test generating api versions during finalization`() {
        val root = buildFileStructure {
            dir("31") { dir("public") { emptyFile("android.jar") } }
            dir("32") { dir("public") { emptyFile("android.jar") } }
            dir("extensions") {
                dir("1") { dir("public") { emptyFile("bar.jar") } }
                dir("2") {
                    dir("public") {
                        emptyFile("bar.jar")
                        emptyFile("foo.jar")
                    }
                }
            }
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()
        runTest(
            ARG_API_VERSION_FOR_SOURCES,
            "33",
            ARG_GENERATE_API_LEVELS,
            apiVersionsXml.path,
            ARG_ANDROID_JAR_PATTERN,
            "$root/{version:level}/*/android.jar",
            ARG_ANDROID_JAR_PATTERN,
            "$root/extensions/{version:extension}/*/{module}.jar",
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
        ) {
            val apiHistoryConfig = options.testForAndroidConfig()
            assertThat(apiHistoryConfig).isNotNull()

            assertThat(apiHistoryConfig!!.versionedApis.dump())
                .isEqualTo(
                    """
                        VersionedJarApi(jar=TESTROOT/31/public/android.jar, updater=ApiVersionUpdater(version=31))
                        VersionedJarApi(jar=TESTROOT/32/public/android.jar, updater=ApiVersionUpdater(version=32))
                        VersionedSourceApi(version=33)
                        VersionedJarApi(jar=TESTROOT/extensions/1/public/bar.jar, updater=ExtensionUpdater(extVersion=1, module=bar, nextSdkVersion=33))
                        VersionedJarApi(jar=TESTROOT/extensions/2/public/bar.jar, updater=ExtensionUpdater(extVersion=2, module=bar, nextSdkVersion=33))
                        VersionedJarApi(jar=TESTROOT/extensions/2/public/foo.jar, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=33))
                    """
                        .trimIndent()
                )
        }
    }
}
