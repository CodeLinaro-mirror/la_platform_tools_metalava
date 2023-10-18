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
import com.android.tools.metalava.testing.getAndroidJar
import java.io.File
import kotlin.text.Charsets.UTF_8
import org.junit.Ignore
import org.junit.Test

class CompatibilityCheckAndroidApisTest : DriverTest() {
    @Ignore(
        "Not currently working: we're getting the wrong PSI results; I suspect caching across the two codebases"
    )
    @Test
    fun `Test All Android API levels`() {
        // Checks API across Android SDK versions and makes sure the results are
        // intentional (to help shake out bugs in the API compatibility checker)

        // Expected migration warnings (the map value) when migrating to the target key level from
        // the previous level
        val expected =
            mapOf(
                5 to
                    "warning: Method android.view.Surface.lockCanvas added thrown exception java.lang.IllegalArgumentException [ChangedThrows]",
                6 to
                    """
                warning: Method android.accounts.AbstractAccountAuthenticator.confirmCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                warning: Method android.accounts.AbstractAccountAuthenticator.updateCredentials added thrown exception android.accounts.NetworkErrorException [ChangedThrows]
                warning: Field android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL has changed value from 2008 to 2014 [ChangedValue]
                """,
                7 to
                    """
                error: Removed field android.view.ViewGroup.FLAG_USE_CHILD_DRAWING_ORDER [RemovedField]
                """,

                // setOption getting removed here is wrong! Seems to be a PSI loading bug.
                8 to
                    """
                warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.KeyManagementException [ChangedThrows]
                warning: Constructor android.net.SSLCertificateSocketFactory no longer throws exception java.security.NoSuchAlgorithmException [ChangedThrows]
                error: Removed method java.net.DatagramSocketImpl.getOption(int) [RemovedMethod]
                error: Removed method java.net.DatagramSocketImpl.setOption(int,Object) [RemovedMethod]
                warning: Constructor java.nio.charset.Charset no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.nio.charset.Charset.forName no longer throws exception java.nio.charset.UnsupportedCharsetException [ChangedThrows]
                warning: Method java.nio.charset.Charset.isSupported no longer throws exception java.nio.charset.IllegalCharsetNameException [ChangedThrows]
                warning: Method java.util.regex.Matcher.appendReplacement no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                warning: Method java.util.regex.Matcher.start no longer throws exception java.lang.IllegalStateException [ChangedThrows]
                warning: Method java.util.regex.Pattern.compile no longer throws exception java.util.regex.PatternSyntaxException [ChangedThrows]
                warning: Class javax.xml.XMLConstants added final qualifier [AddedFinal]
                error: Removed constructor javax.xml.XMLConstants() [RemovedMethod]
                warning: Method javax.xml.parsers.DocumentBuilder.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                warning: Method javax.xml.parsers.DocumentBuilderFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                warning: Method javax.xml.parsers.SAXParser.isXIncludeAware no longer throws exception java.lang.UnsupportedOperationException [ChangedThrows]
                warning: Method javax.xml.parsers.SAXParserFactory.newInstance no longer throws exception javax.xml.parsers.FactoryConfigurationError [ChangedThrows]
                warning: Method org.w3c.dom.Element.getAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.getAttributeNodeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.getElementsByTagNameNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.Element.hasAttributeNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                warning: Method org.w3c.dom.NamedNodeMap.getNamedItemNS added thrown exception org.w3c.dom.DOMException [ChangedThrows]
                """,
                18 to
                    """
                warning: Class android.os.Looper added final qualifier but was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                warning: Class android.os.MessageQueue added final qualifier but was previously uninstantiable and therefore could not be subclassed [AddedFinalUninstantiable]
                error: Removed field android.os.Process.BLUETOOTH_GID [RemovedField]
                error: Removed class android.renderscript.Program [RemovedClass]
                error: Removed class android.renderscript.ProgramStore [RemovedClass]
                """,
                19 to
                    """
                warning: Method android.app.Notification.Style.build has changed 'abstract' qualifier [ChangedAbstract]
                error: Removed method android.os.Debug.MemoryInfo.getOtherLabel(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherPrivateDirty(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherPss(int) [RemovedMethod]
                error: Removed method android.os.Debug.MemoryInfo.getOtherSharedDirty(int) [RemovedMethod]
                warning: Field android.view.animation.Transformation.TYPE_ALPHA has changed value from nothing/not constant to 1 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_ALPHA has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_BOTH has changed value from nothing/not constant to 3 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_BOTH has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_IDENTITY has changed value from nothing/not constant to 0 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_IDENTITY has added 'final' qualifier [AddedFinal]
                warning: Field android.view.animation.Transformation.TYPE_MATRIX has changed value from nothing/not constant to 2 [ChangedValue]
                warning: Field android.view.animation.Transformation.TYPE_MATRIX has added 'final' qualifier [AddedFinal]
                warning: Method java.nio.CharBuffer.subSequence has changed return type from CharSequence to java.nio.CharBuffer [ChangedType]
                """, // The last warning above is not right; seems to be a PSI jar loading bug. It returns the wrong return type!
                20 to
                    """
                error: Removed method android.util.TypedValue.complexToDimensionNoisy(int,android.util.DisplayMetrics) [RemovedMethod]
                warning: Method org.json.JSONObject.keys has changed return type from java.util.Iterator to java.util.Iterator<java.lang.String> [ChangedType]
                warning: Field org.xmlpull.v1.XmlPullParserFactory.features has changed type from java.util.HashMap to java.util.HashMap<java.lang.String, java.lang.Boolean> [ChangedType]
                """,
                26 to
                    """
                warning: Field android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE has changed value from 130 to 230 [ChangedValue]
                warning: Field android.content.pm.PermissionInfo.PROTECTION_MASK_FLAGS has changed value from 4080 to 65520 [ChangedValue]
                """,
                27 to ""
            )

        val suppressLevels =
            mapOf(
                1 to
                    "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,ChangedDeprecated",
                7 to
                    "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,ChangedDeprecated",
                18 to
                    "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,RemovedMethod,ChangedDeprecated,ChangedThrows,AddedFinal,ChangedType,RemovedDeprecatedClass",
                26 to
                    "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,RemovedMethod,ChangedDeprecated,ChangedThrows,AddedFinal,RemovedClass,RemovedDeprecatedClass",
                27 to
                    "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,RemovedMethod,ChangedDeprecated,ChangedThrows,AddedFinal"
            )

        val loadPrevAsSignature = false

        for (apiLevel in 5..27) {
            if (!expected.containsKey(apiLevel)) {
                continue
            }
            println("Checking compatibility from API level ${apiLevel - 1} to $apiLevel...")
            val current = getAndroidJar(apiLevel)
            val previous = getAndroidJar(apiLevel - 1)
            val previousApi = previous.path

            // PSI based check

            check(
                extraArguments =
                    arrayOf(
                        "--omit-locations",
                        ARG_HIDE,
                        suppressLevels[apiLevel]
                            ?: "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,ChangedDeprecated,RemovedField,RemovedClass,RemovedDeprecatedClass" +
                                (if ((apiLevel == 19 || apiLevel == 20) && loadPrevAsSignature)
                                    ",ChangedType"
                                else "")
                    ),
                expectedIssues = expected[apiLevel]?.trimIndent() ?: "",
                checkCompatibilityApiReleased = previousApi,
                apiJar = current
            )

            // Signature based check
            if (apiLevel >= 21) {
                // Check signature file checks. We have .txt files for API level 14 and up, but
                // there are a
                // BUNCH of problems in older signature files that make the comparisons not work --
                // missing type variables in class declarations, missing generics in method
                // signatures, etc.
                val signatureFile =
                    File("../../prebuilts/sdk/${apiLevel - 1}/public/api/android.txt")
                if (!(signatureFile.isFile)) {
                    println(
                        "Couldn't find $signatureFile: Check that pwd for test is correct. Skipping this test."
                    )
                    return
                }
                val previousSignatureApi = signatureFile.readText(UTF_8)

                check(
                    extraArguments =
                        arrayOf(
                            "--omit-locations",
                            ARG_HIDE,
                            suppressLevels[apiLevel]
                                ?: "AddedPackage,AddedClass,AddedMethod,AddedInterface,AddedField,ChangedDeprecated,RemovedField,RemovedClass,RemovedDeprecatedClass"
                        ),
                    expectedIssues = expected[apiLevel]?.trimIndent() ?: "",
                    checkCompatibilityApiReleased = previousSignatureApi,
                    apiJar = current
                )
            }
        }
    }
}
