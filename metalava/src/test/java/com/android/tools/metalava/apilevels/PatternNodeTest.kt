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

import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfaces
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

    private fun PatternNode.assertStructure(expected: String) {
        assertEquals(expected.trimIndent(), dump().trimIndent())
    }

    /** Assert that the [MatchedPatternFile]s */
    private fun List<MatchedPatternFile>.assertMatchedPatternFiles(expected: String) {
        assertEquals(expected.trimIndent(), joinToString("\n"))
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
            "Pattern 'prebuilts/sdk/{unknown}/public/android-{version:level}.jar' contains an unknown placeholder '{unknown}', expected one of '{version:level}', '{version:major.minor?}', '{version:major.minor.patch}', '{version:extension}', '{module}', '{surface}'",
            exception.message
        )
    }

    @Test
    fun `Parse common Android public patterns`() {
        val patterns =
            listOf(
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
            """
        )
    }

    @Test
    fun `Parse common Android system patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/system/android.jar",
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
            """
        )
    }

    @Test
    fun `Parse common Android module-lib patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/module-lib/android.jar",
                "prebuilts/sdk/{version:level}/system/android.jar",
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
            """
        )
    }

    @Test
    fun `Parse pattern with absolute path`() {
        val patterns =
            listOf(
                "/absolute/path/{version:level}.txt",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  /
                    absolute/
                      path/
                        (\d+)\Q.txt\E
            """
        )
    }

    @Test
    fun `Scan public prebuilts`() {
        val androidDir = getAndroidDir()

        val patterns =
            listOf(
                "prebuilts/sdk/{version:level}/public/android.jar",
            )
        val node = PatternNode.parsePatterns(patterns)
        val range = ApiVersion.fromLevel(1).rangeTo(ApiVersion.fromLevel(5))
        val files = node.scan(PatternNode.ScanConfig(androidDir, range::contains))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=prebuilts/sdk/1/public/android.jar, version=1)
                MatchedPatternFile(file=prebuilts/sdk/2/public/android.jar, version=2)
                MatchedPatternFile(file=prebuilts/sdk/3/public/android.jar, version=3)
                MatchedPatternFile(file=prebuilts/sdk/4/public/android.jar, version=4)
                MatchedPatternFile(file=prebuilts/sdk/5/public/android.jar, version=5)
            """
        )
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
        val files = node.scan(PatternNode.ScanConfig(androidDir, range::contains))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=prebuilts/sdk/20/public/android.jar, version=20)
                MatchedPatternFile(file=prebuilts/sdk/21/system/android.jar, version=21)
                MatchedPatternFile(file=prebuilts/sdk/22/system/android.jar, version=22)
            """
        )
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
        val files = node.scan(PatternNode.ScanConfig(androidDir, range::contains))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=prebuilts/sdk/20/public/android.jar, version=20)
                MatchedPatternFile(file=prebuilts/sdk/21/public/android.jar, version=21)
                MatchedPatternFile(file=prebuilts/sdk/22/public/android.jar, version=22)
            """
        )
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
        val files = node.scan(PatternNode.ScanConfig(androidDir, range::contains))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=prebuilts/sdk/21, version=21)
                MatchedPatternFile(file=prebuilts/sdk/22, version=22)
                MatchedPatternFile(file=prebuilts/sdk/23, version=23)
            """
        )
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
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=prebuilts/sdk/19/public/android.jar, version=19)
                MatchedPatternFile(file=prebuilts/sdk/22/public/android.jar, version=22)
                MatchedPatternFile(file=prebuilts/sdk/32/public/android.jar, version=32)
            """
        )
    }

    /** Create an API file, e.g. [name]`.txt` file in the [DirectoryBuilder]. */
    private fun DirectoryBuilder.apiFile(name: String = "api") = emptyFile("$name.txt")

    /** Create a structure of versioned API files for testing. */
    private fun createApiFileStructure(): File {
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
    fun `Scan with empty patterns`() {
        val rootDir = createApiFileStructure()
        val node = PatternNode.parsePatterns(emptyList())
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionFilter = null))
        assertEquals(emptyList(), files)
    }

    @Test
    fun `Scan for major minor`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                "{version:major.minor?}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionFilter = null))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=1/api.txt, version=1)
                MatchedPatternFile(file=1.1/api.txt, version=1.1)
                MatchedPatternFile(file=2/api.txt, version=2)
                MatchedPatternFile(file=2.2/api.txt, version=2.2)
            """
        )
    }

    @Test
    fun `Scan for major minor patch`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                "{version:major.minor.patch}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionFilter = null))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=1.1.1/api.txt, version=1.1.1)
                MatchedPatternFile(file=2.2.3/api.txt, version=2.2.3)
            """
        )
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
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionFilter = null))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=1.1.1/api.txt, version=1.1.1)
                MatchedPatternFile(file=1.1.2-beta01/api.txt, version=1.1.2)
                MatchedPatternFile(file=2.2.3/api.txt, version=2.2.3)
            """
        )
    }

    @Test
    fun `Check pattern with version extension includes module`() {
        val patterns =
            listOf(
                "extensions/{version:extension}/api.txt",
            )
        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'extensions/{version:extension}/api.txt' contains `{version:extension}` but does not contain `{module}`",
            exception.message
        )
    }

    @Test
    fun `Scan for extension version`() {
        val rootDir = createApiFileStructure()

        val patterns =
            listOf(
                "{version:extension}/{module}.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        // This range should have no effect on extension versions.
        val range = ApiVersion.fromLevel(20).rangeTo(ApiVersion.fromLevel(22))
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiVersionFilter = range::contains))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=1/api.txt, version=1, extension=true, module='api')
                MatchedPatternFile(file=2/api.txt, version=2, extension=true, module='api')
            """
        )
    }

    @Test
    fun `Scan for module`() {
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
                "extensions/{version:extension}/{module}.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val files = node.scan(PatternNode.ScanConfig(rootDir))
        files.assertMatchedPatternFiles(
            """
                MatchedPatternFile(file=extensions/1/module-one.txt, version=1, extension=true, module='module-one')
                MatchedPatternFile(file=extensions/3/module-one.txt, version=3, extension=true, module='module-one')
                MatchedPatternFile(file=extensions/2/module-two.txt, version=2, extension=true, module='module-two')
                MatchedPatternFile(file=extensions/3/module-two.txt, version=3, extension=true, module='module-two')
                MatchedPatternFile(file=extensions/2/module.three.txt, version=2, extension=true, module='module.three')
            """
        )
    }

    @Test
    fun `Test use surface placeholder without surfaces`() {
        val rootDir = buildFileStructure { dir("1") { dir("public") { apiFile() } } }

        val patterns =
            listOf(
                "{version:level}/{surface}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val exception =
            assertThrows(IllegalStateException::class.java) {
                node.scan(PatternNode.ScanConfig(rootDir))
            }
        assertEquals(
            "Must provide ScanConfig.apiSurfaceByName when {surface} is used",
            exception.message
        )
    }

    /**
     * Check scanning for files that contain surfaces.
     *
     * @param apiSurfaces the set of allowable [ApiSurface]s.
     * @param expectedFiles the expected set of matching files.
     */
    private fun checkScanningForSurfaces(apiSurfaces: ApiSurfaces, expectedFiles: String) {
        val rootDir = buildFileStructure {
            dir("1") { dir("public") { apiFile() } }
            dir("2") {
                dir("public") { apiFile() }
                dir("system") { apiFile() }
                // 'test' should not appear in the scanned files as it is not a supported surface.
                dir("test") { apiFile() }
            }
            dir("3") {
                dir("public") { apiFile() }
                dir("system") { apiFile() }
                dir("module-lib") { apiFile() }
                // 'test' should not appear in the scanned files as it is not a supported surface.
                dir("test") { apiFile() }
            }
        }

        val patterns =
            listOf(
                "{version:level}/{surface}/api.txt",
            )
        val node = PatternNode.parsePatterns(patterns)
        val apiSurfaceByName = apiSurfaces.all.associateBy { it.name }
        val files = node.scan(PatternNode.ScanConfig(rootDir, apiSurfaceByName = apiSurfaceByName))
        files.assertMatchedPatternFiles(expectedFiles)
    }

    @Test
    fun `Scan for surface - public`() {
        val apiSurfaces = ApiSurfaces.build { createSurface(name = "public", isMain = true) }

        checkScanningForSurfaces(
            apiSurfaces,
            expectedFiles =
                """
                    MatchedPatternFile(file=1/public/api.txt, version=1, surface='public')
                    MatchedPatternFile(file=2/public/api.txt, version=2, surface='public')
                    MatchedPatternFile(file=3/public/api.txt, version=3, surface='public')
                """,
        )
    }

    @Test
    fun `Scan for surface - system`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface(name = "public")
                createSurface(name = "system", extends = "public", isMain = true)
            }

        checkScanningForSurfaces(
            apiSurfaces,
            expectedFiles =
                """
                    MatchedPatternFile(file=1/public/api.txt, version=1, surface='public')
                    MatchedPatternFile(file=2/public/api.txt, version=2, surface='public')
                    MatchedPatternFile(file=2/system/api.txt, version=2, surface='system')
                    MatchedPatternFile(file=3/public/api.txt, version=3, surface='public')
                    MatchedPatternFile(file=3/system/api.txt, version=3, surface='system')
                """,
        )
    }

    @Test
    fun `Scan for surface - module-lib`() {
        val apiSurfaces =
            ApiSurfaces.build {
                createSurface(name = "public")
                createSurface(name = "system", extends = "public")
                createSurface(name = "module-lib", extends = "public", isMain = true)
            }

        checkScanningForSurfaces(
            apiSurfaces,
            expectedFiles =
                """
                    MatchedPatternFile(file=1/public/api.txt, version=1, surface='public')
                    MatchedPatternFile(file=2/public/api.txt, version=2, surface='public')
                    MatchedPatternFile(file=2/system/api.txt, version=2, surface='system')
                    MatchedPatternFile(file=3/public/api.txt, version=3, surface='public')
                    MatchedPatternFile(file=3/system/api.txt, version=3, surface='system')
                    MatchedPatternFile(file=3/module-lib/api.txt, version=3, surface='module-lib')
                """,
        )
    }
}
