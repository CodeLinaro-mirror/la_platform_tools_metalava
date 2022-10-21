/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.ARG_SDK_JAR_ROOT
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.getApiLookup
import com.android.tools.metalava.java
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.text.Charsets.UTF_8

class ApiGeneratorTest : DriverTest() {
    @Test
    fun `Extract API levels`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "Z",
                ARG_CURRENT_VERSION,
                "89" // not real api level of Z
            ),
            sourceFiles = arrayOf(
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

        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/Manifest\$permission\" since=\"1\">"))
        assertTrue(xml.contains("<field name=\"BIND_CARRIER_MESSAGING_SERVICE\" since=\"22\" deprecated=\"23\"/>"))
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"90\""))
        assertFalse(xml.contains("<implements name=\"java/lang/annotation/Annotation\" removed=\""))
        assertFalse(xml.contains("<extends name=\"java/lang/Enum\" removed=\""))
        assertFalse(xml.contains("<method name=\"append(C)Ljava/lang/AbstractStringBuilder;\""))

        // Also make sure package private super classes are pruned
        assertFalse(xml.contains("<extends name=\"android/icu/util/CECalendar\""))

        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())

        // Make sure we're really using the correct database, not the SDK one. (This placeholder
        // class is provided as a source file above.)
        assertEquals(90, apiLookup.getClassVersion("android.pkg.MyTest"))

        apiLookup.getClassVersion("android.v")
        assertEquals(5, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)
    }

    @Test
    fun `Extract System API`() {
        // These are the wrong jar paths but this test doesn't actually care what the
        // content of the jar files, just checking the logic of starting the database
        // at some higher number than 1
        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }

        var extensionSdkJars = File("prebuilts/sdk/extensions")
        if (!extensionSdkJars.isDirectory) {
            extensionSdkJars = File("../../prebuilts/sdk/extensions")
            if (!extensionSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $extensionSdkJars")
                return
            }
        }

        val filter = File.createTempFile("filter", "txt")
        filter.deleteOnExit()
        filter.writeText(
            """
            # Definitions
            R    30
            S    31
            T    33

            # Rules
            android.net.ipsec.ike              *    R
            art.module.public.api              *    R
            conscrypt.module.intra.core.api    *    R
            conscrypt.module.platform.api      *    R
            conscrypt.module.public.api        *    R
            framework-mediaprovider            *    R
            framework-mediaprovider            android.provider.MediaStore#canManageMedia    T
            framework-permission-s             *    R
            framework-permission               *    R
            framework-sdkextensions            *    R
            framework-scheduling               *    R
            framework-statsd                   *    R
            framework-tethering                *    R
            legacy.art.module.platform.api     *    R
            service-media-s                    *    R
            service-permission                 *    R

            # framework-connectivity: only getAllExtensionVersions should have the 'from' attribute
            framework-connectivity             android.net.CaptivePortal    R

            # framework-media explicitly omitted: nothing in this module should have the 'from' attribute
            """.trimIndent()
        )

        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_SDK_JAR_ROOT,
                "$extensionSdkJars",
                ARG_SDK_INFO_FILE,
                filter.path,
                ARG_FIRST_VERSION,
                "21"
            )
        )

        assertTrue(output.isFile)
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<api version=\"3\" min=\"21\">"))
        assertTrue(xml.contains("<sdk id=\"30\" name=\"R\"/>"))
        assertTrue(xml.contains("<sdk id=\"31\" name=\"S\"/>"))
        assertTrue(xml.contains("<sdk id=\"33\" name=\"T\"/>"))
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        // top level class marked as since=21 and R=1, implemented in the framework-mediaprovider mainline module
        assertTrue(xml.contains("<class name=\"android/provider/MediaStore\" module=\"framework-mediaprovider\" since=\"21\" from=\"0:21,30:1\">"))

        // method with identical from attribute as containing class: from should be omitted
        assertTrue(xml.contains("<method name=\"getMediaScannerUri()Landroid/net/Uri;\"/>"))

        // method with different from attribute than containing class
        assertTrue(xml.contains("<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" from=\"0:31,33:1\"/>"))

        val apiLookup = getApiLookup(output)
        apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        assertEquals(21, apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS"))

        val methodVersion = apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)

        // The filter says 'framework-permission-s             *    R' so RoleManager should exist and should have a module/from attributes
        assertTrue(apiLookup.containsClass("android/app/role/RoleManager"))
        assertTrue(xml.contains("<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" from=\"0:31,33:1\"/>"))

        // The filter doesn't mention framework-media, so no class in that module should have a module/from attributes
        assertTrue(xml.contains("<class name=\"android/media/MediaFeature\" since=\"31\">"))

        // The filter only defines a single API in framework-connectivity: verify that only that API has the module/from attributes
        assertTrue(xml.contains("<class name=\"android/net/CaptivePortal\" module=\"framework-connectivity\" since=\"23\" from=\"0:23,30:1\">"))
        assertTrue(xml.contains("<class name=\"android/net/ConnectivityDiagnosticsManager\" since=\"30\">"))
    }

    @Test
    fun `Correct API Level for release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "REL",
                ARG_CURRENT_VERSION,
                "89" // not real api level
            ),
            sourceFiles = arrayOf(
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
        // Anything with a REL codename is in the current API level
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"89\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        assertEquals(89, apiLookup.getClassVersion("android.pkg.MyTest"))
    }

    @Test
    fun `Correct API Level for non-release`() {
        var oldSdkJars = File("prebuilts/tools/common/api-versions")
        if (!oldSdkJars.isDirectory) {
            oldSdkJars = File("../../prebuilts/tools/common/api-versions")
            if (!oldSdkJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found - is \$PWD set to an Android source tree?")
                return
            }
        }

        var platformJars = File("prebuilts/sdk")
        if (!platformJars.isDirectory) {
            platformJars = File("../../prebuilts/sdk")
            if (!platformJars.isDirectory) {
                println("Ignoring ${ApiGeneratorTest::class.java}: prebuilts not found: $platformJars")
                return
            }
        }
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments = arrayOf(
                ARG_GENERATE_API_LEVELS,
                outputPath,
                ARG_ANDROID_JAR_PATTERN,
                "${oldSdkJars.path}/android-%/android.jar",
                ARG_ANDROID_JAR_PATTERN,
                "${platformJars.path}/%/public/android.jar",
                ARG_CURRENT_CODENAME,
                "ZZZ", // not just Z, but very ZZZ
                ARG_CURRENT_VERSION,
                "89" // not real api level
            ),
            sourceFiles = arrayOf(
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
        // Metalava should understand that a codename means "current api + 1"
        val xml = output.readText(UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"90\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        assertEquals(90, apiLookup.getClassVersion("android.pkg.MyTest"))
    }
}
