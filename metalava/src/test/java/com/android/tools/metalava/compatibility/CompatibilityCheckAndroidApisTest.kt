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
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Issue
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
            arrayOf(
                Issues.ADDED_CLASS,
                Issues.ADDED_FIELD,
                Issues.ADDED_INTERFACE,
                Issues.ADDED_METHOD,
                Issues.ADDED_PACKAGE,
                Issues.CHANGED_DEPRECATED,
                Issues.REMOVED_CLASS,
                Issues.REMOVED_DEPRECATED_CLASS,
                Issues.REMOVED_FIELD,
            )

        fun hide(vararg issues: Issue) = hiddenIssues(*issues).toList()

        fun warning(vararg issues: Issue) = warningIssues(*issues).toList()

        /** Data for each api version to check. */
        private val data =
            listOf(
                ApiLevelCheck(
                    5,
                    """
                        load-api.txt:14736: warning: Source breaking change: Method android.view.Surface.lockCanvas added thrown exception java.lang.IllegalArgumentException [ChangedThrows]
                    """,
                    hide(
                        *DEFAULT_HIDDEN_ISSUES,
                        Issues.ADDED_ABSTRACT_METHOD,
                    ) + warning(Issues.CHANGED_THROWS),
                ),
                ApiLevelCheck(
                    6,
                    """
                        load-api.txt:1321: warning: Source breaking change: Method android.accounts.AbstractAccountAuthenticator.confirmCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                        load-api.txt:1328: warning: Source breaking change: Method android.accounts.AbstractAccountAuthenticator.updateCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                        load-api.txt:15728: warning: Binary breaking change: Field android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL has changed value from 2008 to 2014 [ChangedValue]
                    """,
                    hide(
                        *DEFAULT_HIDDEN_ISSUES,
                    ) +
                        warning(
                            Issues.CHANGED_THROWS,
                            Issues.CHANGED_VALUE,
                        ),
                ),
                ApiLevelCheck(
                    7,
                    """
                        released-api.txt:15404: error: Binary breaking change: Removed field android.view.ViewGroup.FLAG_USE_CHILD_DRAWING_ORDER [RemovedField]
                    """,
                    hide(
                        Issues.ADDED_CLASS,
                        Issues.ADDED_FIELD,
                        Issues.ADDED_INTERFACE,
                        Issues.ADDED_METHOD,
                        Issues.ADDED_PACKAGE,
                        Issues.CHANGED_DEPRECATED,
                    ),
                ),
                ApiLevelCheck(
                    8,
                    """
                        load-api.txt:2901: warning: Source breaking change: Method android.content.ComponentName.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:2901: error: Binary breaking change: Method android.content.ComponentName.clone has changed return type from java.lang.Object to android.content.ComponentName [ChangedType]
                        load-api.txt:5169: warning: Source breaking change: Method android.gesture.Gesture.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:5281: warning: Source breaking change: Method android.gesture.GesturePoint.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:5313: warning: Source breaking change: Method android.gesture.GestureStroke.clone no longer throws exception java.lang.CloneNotSupportedException [ChangedThrows]
                        load-api.txt:8395: warning: Source breaking change: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.KeyManagementException [ChangedThrows]
                        load-api.txt:8395: warning: Source breaking change: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.NoSuchAlgorithmException [ChangedThrows]
                        load-api.txt:32652: warning: Binary breaking change: Class javax.xml.XMLConstants added 'final' qualifier [AddedFinal]
                        load-api.txt:37246: warning: Source breaking change: Method org.w3c.dom.Element.getAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37248: warning: Source breaking change: Method org.w3c.dom.Element.getAttributeNodeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37250: warning: Source breaking change: Method org.w3c.dom.Element.getElementsByTagNameNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37254: warning: Source breaking change: Method org.w3c.dom.Element.hasAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        load-api.txt:37290: warning: Source breaking change: Method org.w3c.dom.NamedNodeMap.getNamedItemNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                        released-api.txt:31151: error: Binary breaking change: Removed constructor javax.xml.XMLConstants() [RemovedMethod]
                    """,
                    hide(
                        *DEFAULT_HIDDEN_ISSUES,
                        Issues.ADDED_ABSTRACT_METHOD,
                    ) +
                        warning(
                            Issues.ADDED_FINAL,
                            Issues.CHANGED_THROWS,
                        ),
                ),
                ApiLevelCheck(
                    18,
                    """
                        load-api.txt:6910: error: Binary breaking change: Added method android.content.pm.PackageManager.getPackagesHoldingPermissions(String[],int) [AddedAbstractMethod]
                        load-api.txt:29746: error: Binary breaking change: Added method android.widget.MediaController.MediaPlayerControl.getAudioSessionId() [AddedAbstractMethod]
                        released-api.txt:16414: error: Binary breaking change: Removed field android.os.Process.BLUETOOTH_GID [RemovedField]
                        released-api.txt:19681: error: Binary breaking change: Removed class android.renderscript.Program [RemovedClass]
                        released-api.txt:19763: error: Binary breaking change: Removed class android.renderscript.ProgramStore [RemovedClass]
                    """,
                    hide(
                        Issues.ADDED_CLASS,
                        Issues.ADDED_FIELD,
                        Issues.ADDED_FINAL,
                        Issues.ADDED_INTERFACE,
                        Issues.ADDED_METHOD,
                        Issues.ADDED_PACKAGE,
                        Issues.CHANGED_DEPRECATED,
                        Issues.CHANGED_THROWS,
                        Issues.CHANGED_TYPE,
                        Issues.REMOVED_DEPRECATED_CLASS,
                        Issues.REMOVED_METHOD,
                    ),
                ),
                ApiLevelCheck(
                    19,
                    """
                        load-api.txt:29409: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_ALPHA has added 'final' qualifier [AddedFinal]
                        load-api.txt:29409: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_ALPHA has changed value from nothing/not constant to 1 [ChangedValue]
                        load-api.txt:29410: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_BOTH has added 'final' qualifier [AddedFinal]
                        load-api.txt:29410: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_BOTH has changed value from nothing/not constant to 3 [ChangedValue]
                        load-api.txt:29411: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_IDENTITY has added 'final' qualifier [AddedFinal]
                        load-api.txt:29411: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_IDENTITY has changed value from nothing/not constant to 0 [ChangedValue]
                        load-api.txt:29412: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_MATRIX has added 'final' qualifier [AddedFinal]
                        load-api.txt:29412: warning: Binary breaking change: Field android.view.animation.Transformation.TYPE_MATRIX has changed value from nothing/not constant to 2 [ChangedValue]
                        load-api.txt:37259: warning: Binary breaking change: Method java.nio.CharBuffer.subSequence has changed return type from java.lang.CharSequence to java.nio.CharBuffer [ChangedType]
                        released-api.txt:16986: error: Binary breaking change: Removed method android.os.Debug.MemoryInfo.getOtherLabel(int) [RemovedMethod]
                        released-api.txt:16987: error: Binary breaking change: Removed method android.os.Debug.MemoryInfo.getOtherPrivateDirty(int) [RemovedMethod]
                        released-api.txt:16988: error: Binary breaking change: Removed method android.os.Debug.MemoryInfo.getOtherPss(int) [RemovedMethod]
                        released-api.txt:16989: error: Binary breaking change: Removed method android.os.Debug.MemoryInfo.getOtherSharedDirty(int) [RemovedMethod]
                    """,
                    // The last warning above is not right; seems to be a PSI jar loading bug. It
                    // returns the wrong return type!
                    hide(
                        *DEFAULT_HIDDEN_ISSUES,
                        Issues.ADDED_ABSTRACT_METHOD,
                    ) +
                        warning(
                            Issues.ADDED_FINAL,
                            Issues.CHANGED_TYPE,
                            Issues.CHANGED_VALUE,
                        ),
                ),
                ApiLevelCheck(
                    20,
                    """
                        load-api.txt:51010: warning: Binary breaking change: Method org.json.JSONObject.keys has changed return type from java.util.Iterator to java.util.Iterator<java.lang.String> [ChangedType]
                        load-api.txt:52002: warning: Binary breaking change: Field org.xmlpull.v1.XmlPullParserFactory.features has changed type from java.util.HashMap to java.util.HashMap<java.lang.String,java.lang.Boolean> [ChangedType]
                        released-api.txt:26148: error: Binary breaking change: Removed method android.util.TypedValue.complexToDimensionNoisy(int,android.util.DisplayMetrics) [RemovedMethod]
                    """,
                    hide(
                        *DEFAULT_HIDDEN_ISSUES,
                        Issues.ADDED_ABSTRACT_METHOD,
                    ) +
                        warning(
                            Issues.CHANGED_TYPE,
                        ),
                ),
                ApiLevelCheck(
                    26,
                    """
                        load-api.txt:3941: warning: Binary breaking change: Field android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE has changed value from 130 to 230 [ChangedValue]
                        load-api.txt:10848: warning: Binary breaking change: Field android.content.pm.PermissionInfo.PROTECTION_MASK_FLAGS has changed value from 4080 to 65520 [ChangedValue]
                    """,
                    hide(
                        Issues.ADDED_ABSTRACT_METHOD,
                        Issues.ADDED_CLASS,
                        Issues.ADDED_FIELD,
                        Issues.ADDED_FINAL,
                        Issues.ADDED_INTERFACE,
                        Issues.ADDED_METHOD,
                        Issues.ADDED_PACKAGE,
                        Issues.CHANGED_ABSTRACT,
                        Issues.CHANGED_DEPRECATED,
                        Issues.CHANGED_THROWS,
                        Issues.CHANGED_TYPE,
                        Issues.REMOVED_CLASS,
                        Issues.REMOVED_DEPRECATED_CLASS,
                        Issues.REMOVED_METHOD,
                    ) +
                        warning(
                            Issues.CHANGED_VALUE,
                        ),
                ),
                ApiLevelCheck(
                    27,
                    "",
                    hide(
                        Issues.ADDED_CLASS,
                        Issues.ADDED_FIELD,
                        Issues.ADDED_FINAL,
                        Issues.ADDED_INTERFACE,
                        Issues.ADDED_METHOD,
                        Issues.ADDED_PACKAGE,
                        Issues.CHANGED_ABSTRACT,
                        Issues.CHANGED_DEPRECATED,
                        Issues.CHANGED_THROWS,
                        Issues.REMOVED_METHOD,
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
            val extraArgs = apiLevelCheck.extraArgs.toTypedArray()

            val current = getAndroidTxt(apiLevel)
            val previous = getAndroidTxt(apiLevel - 1)

            check(
                extraArguments = extraArgs,
                expectedIssues = expectedIssues,
                checkCompatibilityApiReleased = previous.readText(),
                signatureSource = current.readText(),
                skipEmitPackages = emptyList(),
            )
        }
    }
}
