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

import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.metalava.DriverTest
import java.io.File
import java.util.regex.Pattern
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass

abstract class ApiGeneratorIntegrationTestBase : DriverTest() {

    val androidPublicJarsPattern = "${platformJars.path}/%/public/android.jar"

    protected fun createSdkExtensionInfoFile(): File {
        val file = File.createTempFile("filter", "txt")
        file.deleteOnExit()
        file.writeText(
            """
                <sdk-extensions-info>
                <!-- SDK definitions -->
                <sdk shortname="R" name="R Extensions" id="30" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                <sdk shortname="S" name="S Extensions" id="31" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                <sdk shortname="T" name="T Extensions" id="33" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />

                <!-- Rules -->
                <symbol jar="art.module.public.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.intra.core.api " pattern="" sdks="R" />
                <symbol jar="conscrypt.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.public.api" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="android.provider.MediaStore#canManageMedia" sdks="T" />
                <symbol jar="framework-permission-s" pattern="*" sdks="R" />
                <symbol jar="framework-permission" pattern="*" sdks="R" />
                <symbol jar="framework-sdkextensions" pattern="*" sdks="R" />
                <symbol jar="framework-scheduling" pattern="*" sdks="R" />
                <symbol jar="framework-statsd" pattern="*" sdks="R" />
                <symbol jar="framework-tethering" pattern="*" sdks="R" />
                <symbol jar="legacy.art.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="service-media-s" pattern="*" sdks="R" />
                <symbol jar="service-permission" pattern="*" sdks="R" />

                <!-- use framework-permissions-s to test the order of multiple SDKs is respected -->
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapAkaInfo" sdks="R,S,T" />
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapInfo" sdks="T,S,R" />
                <symbol jar="android.net.ipsec.ike" pattern="*" sdks="R" />

                <!-- framework-connectivity: only android.net.CaptivePortal should have the 'sdks' attribute -->
                <symbol jar="framework-connectivity" pattern="android.net.CaptivePortalData" sdks="R" />

                <!-- framework-media explicitly omitted: nothing in this module should have the 'sdks' attribute -->
                </sdk-extensions-info>
            """
                .trimIndent()
        )
        return file
    }

    /**
     * Extracts the section for the class with [internalName] and compares it against [expected].
     *
     * Before comparing it will replace tabs with 4 spaces and trim any indent.
     */
    fun String.checkClass(internalName: String, expected: String) {
        val pattern =
            Pattern.compile(
                "^\\s*<class name=\"${internalName}\".*?</class>",
                Pattern.DOTALL or Pattern.MULTILINE
            )
        val matcher = pattern.matcher(this)
        assertTrue("could not find entry for $internalName", matcher.find())
        val extract = matcher.group().replace("\t", "    ")
        assertEquals(expected.trimIndent(), extract.trimIndent())
    }

    companion object {
        // A version higher than SdkVersionInfo.HIGHEST_KNOWN_API.
        // 57 was chosen because previously ApiConstraint used a bit vector requiring that an API
        // version had to be between 1..61.
        internal const val MAGIC_VERSION_INT = 57
        internal const val MAGIC_VERSION_STR = MAGIC_VERSION_INT.toString()
        private val ABOVE_HIGHEST_API = ApiConstraint.above(SdkVersionInfo.HIGHEST_KNOWN_API)

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            assert(ABOVE_HIGHEST_API.includes(MAGIC_VERSION_INT))
        }

        internal val oldSdkJars by
            lazy(LazyThreadSafetyMode.NONE) {
                File("../../../prebuilts/tools/common/api-versions").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for old sdk jars not found: $this")
                    }
                }
            }

        internal val platformJars by
            lazy(LazyThreadSafetyMode.NONE) {
                File("../../../prebuilts/sdk").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for platform jars not found: $this")
                    }
                }
            }

        internal val extensionSdkJars by
            lazy(LazyThreadSafetyMode.NONE) {
                platformJars.resolve("extensions").apply {
                    if (!isDirectory) {
                        Assert.fail("prebuilts for extension jars not found: $this")
                    }
                }
            }
    }

    /** The output file into which the API version history will be written. */
    protected lateinit var output: File

    /** The path of [output]. */
    protected lateinit var outputPath: String

    @Before
    fun setUp() {
        output = temporaryFolder.newFile("api-info.xml")
        outputPath = output.path
    }
}
