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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.testing.DirectoryBuilder
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.android.tools.metalava.testing.getAndroidDir
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PatternNodeTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    fun PatternNode.assertStructure(expected: String) {
        assertEquals(expected.trimIndent(), dump().trimIndent())
    }

    @Test
    fun `Invalid no API placeholder`() {
        val patterns =
            listOf(
                "prebuilts/sdk/3/public/android.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/3/public/android.jar' does not contain placeholder for version",
            exception.message
        )
    }

    @Test
    fun `Invalid multiple version placeholders`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/public/android-{version:major.minor?}.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/{version:level}/public/android-{version:major.minor?}.jar' contains multiple placeholders for version; found {version:level}, {version:major.minor?}",
            exception.message
        )
    }

    @Test
    fun `Invalid multiple module placeholders`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/{module}/{module}.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/{version:level}/{module}/{module}.jar' contains multiple placeholders for module; found {module}, {module}",
            exception.message
        )
    }

    @Test
    fun `Unknown placeholder`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{unknown}/public/android-{version:level}.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/{unknown}/public/android-{version:level}.jar' contains an unknown placeholder '{unknown}', expected one of '{version:level}', '{version:major.minor?}', '{version:major.minor.patch}', '{module}'",
            exception.message
        )
    }

    @Test
    fun `Parse common Android public patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-{version:level}/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }

    @Test
    fun `Parse common Android system patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/system/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-{version:level}/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        system/
                          android.jar
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            system/
                              android.jar
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }

    @Test
    fun `Parse common Android module-lib patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/module-lib/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/module-lib/android.jar",
                "prebuilts/sdk/{version:level}/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/system/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/{version:level}/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-{version:level}/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        module-lib/
                          android.jar
                        system/
                          android.jar
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            module-lib/
                              android.jar
                            system/
                              android.jar
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }

    @Test
    fun `Scan public prebuilts`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/public/android.jar",
                "prebuilts/tools/common/api-versions/android-{version:level}/android.jar",
            )
        val node = PatternNode.parsePatterns(patterns)
        val range = ApiVersion.fromLevel(1).rangeTo(ApiVersion.fromLevel(5))
        val files = node.scan(PatternNode.ScanConfig(androidDir, range))
        val expected =
            listOf(
                MatchedPatternFile(
                    File("prebuilts/tools/common/api-versions/android-1/android.jar"),
                    ApiVersion.fromLevel(1)
                ),
                MatchedPatternFile(
                    File("prebuilts/tools/common/api-versions/android-2/android.jar"),
                    ApiVersion.fromLevel(2)
                ),
                MatchedPatternFile(
                    File("prebuilts/tools/common/api-versions/android-3/android.jar"),
                    ApiVersion.fromLevel(3)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/4/public/android.jar"),
                    ApiVersion.fromLevel(4)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/5/public/android.jar"),
                    ApiVersion.fromLevel(5)
                ),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan system prebuilts`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                // Check system first and then fall back to public. As there are both public and
                // system for versions 21 onwards these patterns will match both the public and
                // system versions but only the system one will be used as it would be found first.
                "prebuilts/sdk/{version:level}/system/android.jar",
                "prebuilts/sdk/{version:level}/public/android.jar",
            )
        val node = PatternNode.parsePatterns(patterns)
        val range = ApiVersion.fromLevel(20).rangeTo(ApiVersion.fromLevel(22))
        val files = node.scan(PatternNode.ScanConfig(androidDir, range))
        val expected =
            listOf(
                MatchedPatternFile(
                    // The fallback to public when there was no system work correctly.
                    File("prebuilts/sdk/20/public/android.jar"),
                    ApiVersion.fromLevel(20)
                ),
                MatchedPatternFile(
                    // Selecting system because the pattern came before public worked correctly.
                    File("prebuilts/sdk/21/system/android.jar"),
                    ApiVersion.fromLevel(21)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/22/system/android.jar"),
                    ApiVersion.fromLevel(22)
                ),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan public prebuilts with unnecessary system pattern`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                // Check the public first, this should never fall back to system as it will always
                // find a public jar.
                "prebuilts/sdk/{version:level}/public/android.jar",
                "prebuilts/sdk/{version:level}/system/android.jar",
            )
        val node = PatternNode.parsePatterns(patterns)
        val range = ApiVersion.fromLevel(20).rangeTo(ApiVersion.fromLevel(22))
        val files = node.scan(PatternNode.ScanConfig(androidDir, range))
        val expected =
            listOf(
                MatchedPatternFile(
                    File("prebuilts/sdk/20/public/android.jar"),
                    ApiVersion.fromLevel(20)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/21/public/android.jar"),
                    ApiVersion.fromLevel(21)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/22/public/android.jar"),
                    ApiVersion.fromLevel(22)
                ),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan version specific prebuilt directories`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}",
            )
        val node = PatternNode.parsePatterns(patterns)
        val range = ApiVersion.fromLevel(21).rangeTo(ApiVersion.fromLevel(23))
        val files = node.scan(PatternNode.ScanConfig(androidDir, range))
        val expected =
            listOf(
                MatchedPatternFile(File("prebuilts/sdk/21"), ApiVersion.fromLevel(21)),
                MatchedPatternFile(File("prebuilts/sdk/22"), ApiVersion.fromLevel(22)),
                MatchedPatternFile(File("prebuilts/sdk/23"), ApiVersion.fromLevel(23)),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan explicit list of version specific jars`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/public/android.jar",
            )
        val node = PatternNode.parsePatterns(patterns)

        val limitedFileProvider =
            PatternNode.LimitedFileSystemProvider(
                listOf(
                        "prebuilts/sdk/19/public/android.jar",
                        "prebuilts/sdk/22/public/android.jar",
                        "prebuilts/sdk/32/public/android.jar",
                    )
                    .map { androidDir.resolve(it) }
            )

        val scanConfig =
            PatternNode.ScanConfig(
                dir = androidDir,
                fileProvider = limitedFileProvider,
            )
        val files = node.scan(scanConfig)
        val expected =
            listOf(
                MatchedPatternFile(
                    File("prebuilts/sdk/19/public/android.jar"),
                    ApiVersion.fromLevel(19)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/22/public/android.jar"),
                    ApiVersion.fromLevel(22)
                ),
                MatchedPatternFile(
                    File("prebuilts/sdk/32/public/android.jar"),
                    ApiVersion.fromLevel(32)
                ),
            )
        assertEquals(expected, files)
    }

    /** Create a structure of versioned API files for testing. */
    private fun createApiFileStructure(): File {
        fun DirectoryBuilder.apiFile() = emptyFile("api.txt")
        val rootDir = buildFileStructure {
            dir("1") { apiFile() }
            dir("1.1") { apiFile() }
            dir("1.1.1") { apiFile() }
            dir("1.1.2-beta01") { apiFile() }
            dir("2") { apiFile() }
            dir("2.2") { apiFile() }
            dir("2.2.3") { apiFile() }
        }
        return rootDir
    }

    @Test
    fun `Scan for major minor`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                "{version:major.minor?}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionRange = null))
        val expected =
            listOf(
                MatchedPatternFile(File("1/api.txt"), ApiVersion.fromString("1")),
                MatchedPatternFile(File("1.1/api.txt"), ApiVersion.fromString("1.1")),
                MatchedPatternFile(File("2/api.txt"), ApiVersion.fromString("2")),
                MatchedPatternFile(File("2.2/api.txt"), ApiVersion.fromString("2.2")),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan for major minor patch`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                "{version:major.minor.patch}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionRange = null))
        val expected =
            listOf(
                MatchedPatternFile(File("1.1.1/api.txt"), ApiVersion.fromString("1.1.1")),
                MatchedPatternFile(File("2.2.3/api.txt"), ApiVersion.fromString("2.2.3")),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan for major minor patch plus wildcard`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                // Use a wildcard to allow (and ignore) additional text after the patch, e.g. a
                // pre-release quality tag like -beta01.
                "{version:major.minor.patch}*/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionRange = null))
        val expected =
            listOf(
                MatchedPatternFile(File("1.1.1/api.txt"), ApiVersion.fromString("1.1.1")),
                MatchedPatternFile(File("1.1.2-beta01/api.txt"), ApiVersion.fromString("1.1.2")),
                MatchedPatternFile(File("2.2.3/api.txt"), ApiVersion.fromString("2.2.3")),
            )
        assertEquals(expected, files)
    }

    @Test
    fun `Scan for module`() {
        fun DirectoryBuilder.apiFile(module: String) = emptyFile("$module.txt")
        val rootDir = buildFileStructure {
            dir("extensions") {
                dir("1") { apiFile("module-one") }
                dir("2") {
                    apiFile("module-two")
                    apiFile("module.three")
                }
                dir("3") {
                    apiFile("module-one")
                    apiFile("module-two")
                }
            }
        }

        val patterns =
            listOf(
                "extensions/{version:level}/{module}.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir))
        val expected =
            listOf(
                MatchedPatternFile(
                    File("extensions/1/module-one.txt"),
                    ApiVersion.fromLevel(1),
                    module = "module-one",
                ),
                MatchedPatternFile(
                    File("extensions/2/module-two.txt"),
                    ApiVersion.fromLevel(2),
                    module = "module-two",
                ),
                MatchedPatternFile(
                    File("extensions/2/module.three.txt"),
                    ApiVersion.fromLevel(2),
                    module = "module.three",
                ),
                MatchedPatternFile(
                    File("extensions/3/module-one.txt"),
                    ApiVersion.fromLevel(3),
                    module = "module-one",
                ),
                MatchedPatternFile(
                    File("extensions/3/module-two.txt"),
                    ApiVersion.fromLevel(3),
                    module = "module-two",
                ),
            )
        assertEquals(expected, files)
    }
}
