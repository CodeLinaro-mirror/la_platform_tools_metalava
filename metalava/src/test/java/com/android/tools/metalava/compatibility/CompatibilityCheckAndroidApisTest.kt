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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.common.ARG_WARNING
import com.android.tools.metalava.testing.getAndroidTxt
import org.junit.AssumptionViolatedException
import org.junit.Test

abstract class CompatibilityCheckAndroidApisTest(
    private val apiLevelCheck: ApiLevelCheck,
) : DriverTest() {

    data class ApiLevelCheck(
        val apiLevel: Int,
        val expectedIssues: String,
        val extraArgs: List<String>,
        val disabled: Boolean = false,
    ) {
        override fun toString(): String = "${apiLevel - 1} to $apiLevel"
    }

    companion object {
        private val DEFAULT_HIDDEN_ISSUES =
            listOf(
                "AddedClass",
                "AddedField",
                "AddedInterface",
                "AddedMethod",
                "AddedPackage",
                "ChangedDeprecated",
                "RemovedClass",
                "RemovedDeprecatedClass",
                "RemovedField",
            )
        private val DEFAULT_HIDDEN_ISSUES_STRING = DEFAULT_HIDDEN_ISSUES.joinToString(",")

        private fun joinIssues(issues: Array<out String>): String = issues.joinToString(",")

        fun hide(vararg issues: String): List<String> {
            return listOf(ARG_HIDE, joinIssues(issues))
        }

        fun warning(vararg issues: String): List<String> {
            return listOf(ARG_WARNING, issues.joinToString(","))
        }

        /** Data for each api version to check. */
        private val data =
            listOf(
                ApiLevelCheck(
                    5,
                    """
                        load-api.txt:14736: warning: Method android.view.Surface.lockCanvas added thrown exception java.lang.IllegalArgumentException [ChangedThrows]
                    """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) + warning("ChangedThrows"),
                ),
                ApiLevelCheck(
                    6,
                    """
                        load-api.txt:1321: warning: Method android.accounts.AbstractAccountAuthenticator.confirmCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                        load-api.txt:1328: warning: Method android.accounts.AbstractAccountAuthenticator.updateCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                        load-api.txt:15728: warning: Field android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL has changed value from 2008 to 2014 [ChangedValue]
                    """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                    ) +
                        warning(
                            "ChangedThrows",
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    7,
                    """
                        released-api.txt:15404: error: Removed field android.view.ViewGroup.FLAG_USE_CHILD_DRAWING_ORDER [RemovedField]
                    """,
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedDeprecated",
                    ),
                ),
                ApiLevelCheck(
                    8,
                    """
                        load-api.txt:2901: warning: Method android.content.ComponentName.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:2901: error: Method android.content.ComponentName.clone has changed return type from java.lang.Object to android.content.ComponentName [ChangedType]
                        load-api.txt:5169: warning: Method android.gesture.Gesture.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:5281: warning: Method android.gesture.GesturePoint.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:5313: warning: Method android.gesture.GestureStroke.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:8395: warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.KeyManagementException [ChangedThrows]
                        load-api.txt:8395: warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.NoSuchAlgorithmException [ChangedThrows]
                        load-api.txt:24974: warning: Constructor java.nio.charset.Charset no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                        load-api.txt:24987: warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                        load-api.txt:24987: warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.UnsupportedCharsetException [ChangedThrows]
                        load-api.txt:24990: warning: Method java.nio.charset.Charset.isSupported no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                        load-api.txt:30437: warning: Method java.util.regex.Matcher.appendReplacement no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                        load-api.txt:30462: warning: Method java.util.regex.Matcher.start no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                        load-api.txt:30471: warning: Method java.util.regex.Pattern.compile no longer throws exception java.util.regex.PatternSyntaxException [ChangedThrows]
                        load-api.txt:32652: warning: Class javax.xml.XMLConstants added 'final' qualifier [AddedFinal]
                        load-api.txt:32849: warning: Method javax.xml.parsers.DocumentBuilder.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                        load-api.txt:32874: warning: Method javax.xml.parsers.DocumentBuilderFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                        load-api.txt:32908: warning: Method javax.xml.parsers.SAXParser.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                        load-api.txt:32930: warning: Method javax.xml.parsers.SAXParserFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                        load-api.txt:37246: warning: Method org.w3c.dom.Element.getAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37248: warning: Method org.w3c.dom.Element.getAttributeNodeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37250: warning: Method org.w3c.dom.Element.getElementsByTagNameNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37254: warning: Method org.w3c.dom.Element.hasAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37290: warning: Method org.w3c.dom.NamedNodeMap.getNamedItemNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        released-api.txt:31151: error: Removed constructor javax.xml.XMLConstants() [RemovedMethod]
                    """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "AddedFinal",
                            "ChangedThrows",
                        ),
                ),
                ApiLevelCheck(
                    18,
                    """
                        load-api.txt:6911: error: Added method android.content.pm.PackageManager.getPackagesHoldingPermissions(String[],int) [AddedAbstractMethod]
                        load-api.txt:29748: error: Added method android.widget.MediaController.MediaPlayerControl.getAudioSessionId() [AddedAbstractMethod]
                        released-api.txt:16415: error: Removed field android.os.Process.BLUETOOTH_GID [RemovedField]
                        released-api.txt:19682: error: Removed class android.renderscript.Program [RemovedClass]
                        released-api.txt:19764: error: Removed class android.renderscript.ProgramStore [RemovedClass]
                    """,
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "ChangedType",
                        "RemovedDeprecatedClass",
                        "RemovedMethod",
                    ),
                ),
                ApiLevelCheck(
                    19,
                    """
                        load-api.txt:29411: warning: Field android.view.animation.Transformation.TYPE_ALPHA has added 'final' qualifier [AddedFinal]
                        load-api.txt:29411: warning: Field android.view.animation.Transformation.TYPE_ALPHA has changed value from nothing/not constant to 1 [ChangedValue]
                        load-api.txt:29412: warning: Field android.view.animation.Transformation.TYPE_BOTH has added 'final' qualifier [AddedFinal]
                        load-api.txt:29412: warning: Field android.view.animation.Transformation.TYPE_BOTH has changed value from nothing/not constant to 3 [ChangedValue]
                        load-api.txt:29413: warning: Field android.view.animation.Transformation.TYPE_IDENTITY has added 'final' qualifier [AddedFinal]
                        load-api.txt:29413: warning: Field android.view.animation.Transformation.TYPE_IDENTITY has changed value from nothing/not constant to 0 [ChangedValue]
                        load-api.txt:29414: warning: Field android.view.animation.Transformation.TYPE_MATRIX has added 'final' qualifier [AddedFinal]
                        load-api.txt:29414: warning: Field android.view.animation.Transformation.TYPE_MATRIX has changed value from nothing/not constant to 2 [ChangedValue]
                        load-api.txt:37262: warning: Method java.nio.CharBuffer.subSequence has changed return type from java.lang.CharSequence to java.nio.CharBuffer [ChangedType]
                        released-api.txt:16987: error: Removed method android.os.Debug.MemoryInfo.getOtherLabel(int) [RemovedMethod]
                        released-api.txt:16988: error: Removed method android.os.Debug.MemoryInfo.getOtherPrivateDirty(int) [RemovedMethod]
                        released-api.txt:16989: error: Removed method android.os.Debug.MemoryInfo.getOtherPss(int) [RemovedMethod]
                        released-api.txt:16990: error: Removed method android.os.Debug.MemoryInfo.getOtherSharedDirty(int) [RemovedMethod]
                    """,
                    // The last warning above is not right; seems to be a PSI jar loading bug. It
                    // returns the wrong return type!
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "AddedFinal",
                            "ChangedType",
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    20,
                    """
                        load-api.txt:51013: warning: Method org.json.JSONObject.keys has changed return type from java.util.Iterator to java.util.Iterator<java.lang.String> [ChangedType]
                        load-api.txt:52005: warning: Field org.xmlpull.v1.XmlPullParserFactory.features has changed type from java.util.HashMap to java.util.HashMap<java.lang.String,java.lang.Boolean> [ChangedType]
                        released-api.txt:26150: error: Removed method android.util.TypedValue.complexToDimensionNoisy(int,android.util.DisplayMetrics) [RemovedMethod]
                    """,
                    hide(
                        DEFAULT_HIDDEN_ISSUES_STRING,
                        "AddedAbstractMethod",
                    ) +
                        warning(
                            "ChangedType",
                        ),
                ),
                ApiLevelCheck(
                    26,
                    """
                        load-api.txt:3941: warning: Field android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE has changed value from 130 to 230 [ChangedValue]
                        load-api.txt:10849: warning: Field android.content.pm.PermissionInfo.PROTECTION_MASK_FLAGS has changed value from 4080 to 65520 [ChangedValue]
                    """,
                    hide(
                        "AddedAbstractMethod",
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedAbstract",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "ChangedType",
                        "RemovedClass",
                        "RemovedDeprecatedClass",
                        "RemovedMethod",
                    ) +
                        warning(
                            "ChangedValue",
                        ),
                ),
                ApiLevelCheck(
                    27,
                    "",
                    hide(
                        "AddedClass",
                        "AddedField",
                        "AddedFinal",
                        "AddedInterface",
                        "AddedMethod",
                        "AddedPackage",
                        "ChangedAbstract",
                        "ChangedDeprecated",
                        "ChangedThrows",
                        "RemovedMethod",
                    ),
                ),
            )

        @JvmStatic
        protected fun shardTestParameters(apiLevelRange: IntRange) =
            data.filter { it.apiLevel in apiLevelRange }
    }

    @Test
    fun `Test All Android API levels`() {
        // Checks API across Android SDK versions and makes sure the results are
        // intentional (to help shake out bugs in the API compatibility checker)

        // Temporary let block to keep the same indent as before when this was looping over the data
        // itself.
        let {
            if (apiLevelCheck.disabled) {
                throw AssumptionViolatedException("Test disabled")
            }

            val apiLevel = apiLevelCheck.apiLevel
            val expectedIssues = apiLevelCheck.expectedIssues
            val expectedFail =
                if (expectedIssues.contains("error: ")) "Aborting: Found compatibility problems"
                else ""
            val extraArgs = apiLevelCheck.extraArgs.toTypedArray()

            val current = getAndroidTxt(apiLevel)
            val previous = getAndroidTxt(apiLevel - 1)

            check(
                extraArguments = extraArgs,
                expectedIssues = expectedIssues,
                expectedFail = expectedFail,
                checkCompatibilityApiReleased = previous.readText(),
                signatureSource = current.readText(),
                skipEmitPackages = emptyList(),
            )
        }
    }
}
