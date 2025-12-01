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

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_VERSION_FOR_SOURCES
import com.android.tools.metalava.ARG_API_VERSION_RANGE
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.doc.getApiLookup
import com.android.tools.metalava.testing.java
import kotlin.test.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectApiLevelForNonReleaseTest : ApiGeneratorIntegrationTestBase() {

    @Test
    fun `Correct API Level for non-release using current version arg`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "ZZZ", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR // not real api level
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        // As api-version-for-sources is not set, fall back to default value
        val nextVersion = 10_000
        val xml = output.readText(Charsets.UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for non-release using api version for sources arg`() {
        val nextVersion = MAGIC_VERSION_INT + 1
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "ZZZ", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR, // not real api level
                    ARG_API_VERSION_FOR_SOURCES,
                    nextVersion.toString()
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(Charsets.UTF_8)
        // Sources will always be included with the api level of --api-for-sources when
        // --api-for-sources is set with a valid value regardless of the codename
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Throw error when current version is less than 27`() {
        val currentApiVersion = 27 - 1
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "ZZZ", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    currentApiVersion.toString()
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                ),
            expectedFail =
                "Aborting: Suspicious $ARG_CURRENT_VERSION $currentApiVersion, expected at least 27"
        )
    }

    @Test
    fun `Do not include sources for release (REL) with current version arg less than last finalized version`() {
        val lastFinalizedVersion = 35
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "REL", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    lastFinalizedVersion.toString()
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(Charsets.UTF_8)
        assertFalse(xml.contains("<class name=\"android/pkg/MyTest\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION") assertEquals(-1, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    // TODO : b/454050901 remove this test once --current-version is deprecated
    @Test
    fun `Correct API Level for release (REL) with current version arg greater than last finalized version`() {
        val lastFinalizedVersion = 35
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "REL", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    (lastFinalizedVersion + 1).toString(),
                    ARG_API_VERSION_RANGE,
                    "1:35"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)

        val xml = output.readText(Charsets.UTF_8)
        assertTrue(
            xml.contains("<class name=\"android/pkg/MyTest\" since=\"${lastFinalizedVersion + 1}\"")
        )
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(lastFinalizedVersion + 1, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for release (REL) using api version for sources arg`() {
        val apiVersionForSources = 40
        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_CURRENT_CODENAME,
                    "REL", // not just Z, but very ZZZ
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR, // not real api level,
                    ARG_API_VERSION_FOR_SOURCES,
                    apiVersionForSources.toString()
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(Charsets.UTF_8)

        // Sources will always be included with the api level of --api-for-sources when
        // --api-for-sources is set with a valid value regardless of the codename
        assertTrue(
            xml.contains("<class name=\"android/pkg/MyTest\" since=\"$apiVersionForSources\"")
        )
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(apiVersionForSources, apiLookup.getClassVersion("android.pkg.MyTest"))
    }
}
