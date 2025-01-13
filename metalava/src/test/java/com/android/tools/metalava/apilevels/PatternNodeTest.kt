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

import com.android.tools.metalava.testing.getAndroidDir
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternNodeTest {

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
            "Pattern 'prebuilts/sdk/3/public/android.jar' does not contain '%' or {version:level}",
            exception.message
        )
    }

    @Test
    fun `Invalid multiple API placeholders`() {
        val patterns =
            listOf(
                "prebuilts/sdk/%/public/android-%.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/%/public/android-%.jar' contains more than one '%' or {version:level}",
            exception.message
        )
    }

    @Test
    fun `Unknown placeholder`() {
        val patterns =
            listOf(
                "prebuilts/sdk/{unknown}/public/android-%.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/{unknown}/public/android-%.jar' contains an unknown placeholder '{unknown}'",
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
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
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
                "prebuilts/sdk/%/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/system/android.jar",
                "prebuilts/sdk/%/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
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
                "prebuilts/sdk/%/module-lib/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/module-lib/android.jar",
                "prebuilts/sdk/%/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/system/android.jar",
                "prebuilts/sdk/%/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
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
                "prebuilts/sdk/%/public/android.jar",
                "prebuilts/tools/common/api-versions/android-%/android.jar",
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
                "prebuilts/sdk/%/system/android.jar",
                "prebuilts/sdk/%/public/android.jar",
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
                "prebuilts/sdk/%/public/android.jar",
                "prebuilts/sdk/%/system/android.jar",
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
                "prebuilts/sdk/%",
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
}
