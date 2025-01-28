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
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.doc.getApiLookup
import com.android.tools.metalava.testing.java
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractPublicApiLevelsTest : ApiGeneratorIntegrationTestBase() {
    // TODO(b/378479241): Fix this test to make it more realistic by including definitions of the
    //  current API or stopping it from including the current API.
    @Test
    fun `Extract API levels`() {
        val currentVersion = 35

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidPublicJarsPattern,
                    ARG_ANDROID_JAR_PATTERN,
                    "${extensionSdkJars.path}/{version:extension}/*/{module}.jar",
                    ARG_SDK_INFO_FILE,
                    createSdkExtensionInfoFile().path,
                    ARG_CURRENT_CODENAME,
                    "Z",
                    ARG_CURRENT_VERSION,
                    currentVersion.toString() // not real api level of Z
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

        val xml =
            output
                .readText(Charsets.UTF_8)
                // As this only provides a single MyTest class in the current codebase which is of
                // version 36, the api-versions.xml generator will assume that all the other classes
                // it has seen have been removed. That is not true so remove those attributes.
                .replace(" removed=\"36\"", "")
        val nextVersion = currentVersion + 1
        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"1\">"))
        assertTrue(
            xml.contains(
                "<field name=\"BIND_CARRIER_MESSAGING_SERVICE\" since=\"22\" deprecated=\"23\"/>"
            )
        )
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$nextVersion\""))
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        // Make sure we're really using the correct database, not the SDK one. (This placeholder
        // class is provided as a source file above.)
        @Suppress("DEPRECATION")
        assertEquals(nextVersion, apiLookup.getClassVersion("android.pkg.MyTest"))

        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        @Suppress("DEPRECATION")
        assertEquals(
            5,
            apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS")
        )

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)

        // Verify historical backfill by checking the section for android/os/ext/SdkExtensions
        xml.checkClass(
            "android/os/ext/SdkExtensions",
            """
                <class name="android/os/ext/SdkExtensions" since="30">
                    <extends name="java/lang/Object"/>
                    <method name="getAllExtensionVersions()Ljava/util/Map;" since="31"/>
                    <method name="getExtensionVersion(I)I"/>
                    <field name="AD_SERVICES" since="34" sdks="30:4,0:34"/>
                </class>
            """
        )
    }
}
