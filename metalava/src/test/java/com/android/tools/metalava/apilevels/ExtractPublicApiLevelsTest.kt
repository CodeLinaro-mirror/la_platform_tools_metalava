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

import com.android.tools.metalava.cli.signature.ARG_API_VERSION_FOR_SDK_EXTENSION
import com.android.tools.metalava.cli.signature.ARG_SDK_INFO_FILE
import com.android.tools.metalava.doc.getApiLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractPublicApiLevelsTest : ApiGeneratorIntegrationTestBase() {
    @Test
    fun `Extract API levels`() {
        val extensionSignaturePattern =
            "${extensionSdkJars.path}/{version:extension}/public/api/{module}.txt"

        commandTest {
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputPath,
                    "--historical-versions",
                    "26:34",
                    "--pattern",
                    androidPublicSignaturePattern,
                    "--pattern",
                    extensionSignaturePattern,
                    "--historical-files",
                    findHistoricalFiles(
                        androidPublicSignaturePattern,
                        extensionSignaturePattern,
                    ),
                    ARG_SDK_INFO_FILE,
                    createSdkExtensionInfoFile().path,
                    ARG_API_VERSION_FOR_SDK_EXTENSION,
                    "35",
                )
        }

        assertTrue(output.isFile)

        val xml = output.readText(Charsets.UTF_8)

        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"26\">"))
        assertTrue(
            xml.contains(
                "<field name=\"SMS_FINANCIAL_TRANSACTIONS\" since=\"29\" deprecated=\"31\"/>"
            )
        )
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        @Suppress("DEPRECATION")
        assertEquals(
            28,
            apiLookup.getFieldVersion("android.Manifest\$permission", "FOREGROUND_SERVICE")
        )

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(26, methodVersion)

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
