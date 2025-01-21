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
import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.ARG_SDK_JAR_ROOT
import com.android.tools.metalava.doc.getApiLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractSystemApiLevelsTest : ApiGeneratorIntegrationTestBase() {
    @Test
    fun `Extract System API`() {
        val androidJarPattern = "${platformJars.path}/{version:level}/system/android.jar"

        check(
            extraArguments =
                arrayOf(
                    // Make sure to only use system extension jars.
                    ARG_API_SURFACE,
                    "system",
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidJarPattern,
                    ARG_SDK_JAR_ROOT,
                    extensionSdkJars.path,
                    ARG_SDK_INFO_FILE,
                    createSdkExtensionInfoFile().path,
                    ARG_FIRST_VERSION,
                    "21",
                    ARG_CURRENT_VERSION,
                    "33"
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(Charsets.UTF_8)
        assertTrue(xml.contains("<api version=\"3\" min=\"21\">"))
        assertTrue(
            xml.contains(
                "<sdk id=\"30\" shortname=\"R\" name=\"R Extensions\" reference=\"android/os/Build\$VERSION_CODES\$R\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"31\" shortname=\"S\" name=\"S Extensions\" reference=\"android/os/Build\$VERSION_CODES\$S\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"33\" shortname=\"T\" name=\"T Extensions\" reference=\"android/os/Build\$VERSION_CODES\$T\"/>"
            )
        )
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        // top level class marked as since=34 and R=7, implemented in the framework-statsd
        // mainline module
        assertTrue(
            xml.contains(
                "<class name=\"android/app/StatsCursor\" module=\"framework-statsd\" since=\"34\" sdks=\"30:7\">"
            )
        )

        // method with identical sdks attribute as containing class: sdks attribute should be
        // omitted
        assertTrue(xml.contains("<method name=\"getMediaScannerUri()Landroid/net/Uri;\"/>"))

        // method with different sdks attribute than containing class
        assertTrue(
            xml.contains(
                "<method name=\"isBypassingRoleQualification()Z\" since=\"31\" sdks=\"30:1,0:31\"/>"
            )
        )

        val apiLookup = getApiLookup(output)
        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        @Suppress("DEPRECATION")
        (assertEquals(
            21,
            apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS")
        ))

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)

        // The filter says 'framework-permission-s             *    R' so RoleManager should exist
        // and should have a module/sdks attributes
        assertTrue(apiLookup.containsClass("android/app/role/RoleManager"))

        // The filter doesn't mention framework-media, so no class in that module should have a
        // module/sdks attributes
        assertTrue(xml.contains("<class name=\"android/media/MediaFeature\" since=\"31\">"))

        // The filter only defines a single API in framework-connectivity: verify that only that API
        // has the module/sdks attributes
        assertTrue(
            xml.contains(
                "<class name=\"android/net/CaptivePortalData\" module=\"framework-connectivity\" since=\"30\" sdks=\"30:1,0:30\">"
            )
        )
        assertTrue(
            xml.contains("<class name=\"android/net/ConnectivityDiagnosticsManager\" since=\"30\">")
        )

        // The order of the SDKs should be respected
        // android.net.eap.EapAkaInfo    R S T -> 0,30,31,33
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapAkaInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"30:3,31:3,33:3,0:33\">"
            )
        )
        // android.net.eap.EapInfo       T S R -> 0,33,31,30
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"33:3,31:3,30:3,0:33\">"
            )
        )

        // Verify historical backfill by checking the section for android/os/ext/SdkExtensions
        xml.checkClass(
            "android/os/ext/SdkExtensions",
            """
                <class name="android/os/ext/SdkExtensions" since="30">
                    <extends name="java/lang/Object"/>
                    <method name="getAllExtensionVersions()Ljava/util/Map;" since="31"/>
                    <method name="getExtensionVersion(I)I"/>
                    <field name="AD_SERVICES" since="34" sdks="30:4"/>
                </class>
            """
        )
    }
}
