/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.Driver
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.signature.ARG_SDK_INFO_FILE
import com.android.tools.metalava.testing.signature
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for how `api-history` handles `@SdkExtensionApi` in signature files. */
class SdkExtensionApiHistoryTest : DriverTest() {

    /**
     * Run the `api-history` sub-command with [args].
     *
     * If [expectedError] is `null` then this checks that it succeeds, otherwise it checks that it
     * fails with [expectedError].
     */
    private fun runApiHistory(vararg args: String, expectedError: String? = null) {
        val (executionEnvironment, _, stderr) = ExecutionEnvironment.forTest()
        val exitCode = Driver.run(executionEnvironment, arrayOf("api-history", *args))
        if (expectedError == null) {
            assertEquals(stderr.toString(), 0, exitCode)
        } else {
            assertEquals(expectedError.trimIndent(), stderr.toString().trim())
            assertTrue("expected a non-zero exit code", exitCode != 0)
        }
    }

    /** Find all `.txt` files under [dir] and return them joined by [File.pathSeparator]. */
    private fun findTxtFiles(dir: File): String =
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .sortedBy { it.path }
            .joinToString(File.pathSeparator) { it.path }

    /** Check this `api-versions.xml` file has the correct content. */
    private fun File.checkApiVersionsXmlContent(expectedContent: String) {
        assertTrue("$this was expected to be a plain file but is not", isFile)
        // The generated XML is indented using tabs, replace them with 4 spaces.
        val indentedWithSpaces = readText().replace("\t", "    ").trim()
        assertEquals(expectedContent.trimIndent(), indentedWithSpaces)
    }

    /**
     * Generate an `api-versions.xml` file from a single platform API version `33` provided by
     * [platformApi], extension versions of the `module` jar provided by [extensionVersions] (map
     * from extension version to the contents of `module.txt`), and [currentApi], if any, as version
     * `34`.
     *
     * The `sdk-extensions-info.xml` file declares `R-ext`, `S-ext`, `T-ext`, `U-ext` and
     * `AD_SERVICES-ext` and contains [symbols] as its `<symbol>` rules.
     *
     * If [expectedError] is not `null` then this checks that `api-history` fails with
     * [expectedError].
     */
    private fun generateApiVersions(
        extensionVersions: Map<Int, String>,
        currentApi: String?,
        symbols: String,
        platformApi: String =
            """
                // Signature format: 2.0
                package test.pkg {
                }
            """,
        expectedError: String? = null,
    ): File {
        val root = buildFileStructure {
            dir("33") { signature("api.txt", platformApi) }
            dir("extensions") {
                for ((extVersion, contents) in extensionVersions) {
                    dir("$extVersion") { signature("module.txt", contents) }
                }
            }
        }

        val sdkExtensionsInfoXml =
            temporaryFolder.newFile("sdk-extensions-info.xml").apply {
                writeText(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <sdk-extensions-info>
                        <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                        <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                        <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                        <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                        <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    """
                        .trimIndent() + "\n" + symbols.trimIndent() + "\n</sdk-extensions-info>\n"
                )
            }

        val currentApiArgs =
            if (currentApi == null) {
                emptyArray()
            } else {
                val currentApiFile = signature("current.txt", currentApi).toFile()
                arrayOf("--current-version", "34", "--current-files", currentApiFile.path)
            }
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        runApiHistory(
            "--output-file",
            apiVersionsXml.path,
            ARG_SDK_INFO_FILE,
            sdkExtensionsInfoXml.path,
            "--pattern",
            "$root/{version:major.minor?}/api.txt",
            "--pattern",
            "$root/extensions/{version:extension}/{module}.txt",
            "--historical-files",
            findTxtFiles(root),
            *currentApiArgs,
            "--missing-class-action",
            "remove",
            expectedError = expectedError,
        )

        return apiVersionsXml
    }

    /**
     * A `<symbol>` rule for a class that does not use `@SdkExtensionApi`.
     *
     * `api-history` currently skips scanning the extension files of any jar that has no `<symbol>`
     * rules, so this rule ensures that the `module` jar is scanned.
     */
    private val legacySymbol =
        """<symbol jar="module" pattern="test.pkg.Legacy" sdks="R-ext,S-ext,T-ext,U-ext" />"""

    @Test
    fun `Test api-versions for @SdkExtensionApi without symbol rules`() {
        val moduleApi =
            """
                // Signature format: 2.0
                package test.pkg {
                  @SdkExtensionApi(1000000) public class AdServices {
                    ctor public AdServices();
                  }
                  @SdkExtensionApi(33) public class Annotated {
                    ctor public Annotated();
                    method public void inheritsClassFloor();
                    method @SdkExtensionApi(34) public void overridesClassFloor();
                  }
                  @SdkExtensionApi(33) public static class Annotated.Nested {
                    ctor public Annotated.Nested();
                  }
                  @SdkExtensionApi(32) public class InvalidFloor {
                    ctor public InvalidFloor();
                  }
                  public class Legacy {
                    ctor public Legacy();
                  }
                }
            """

        val apiVersionsXml =
            generateApiVersions(
                extensionVersions = mapOf(10 to moduleApi),
                currentApi = moduleApi,
                symbols = legacySymbol,
            )

        // The @SdkExtensionApi floor is used to compute the `sdks` attribute:
        //  * `AdServices` is only in `AD_SERVICES-ext` (`1000000:10`).
        //  * `Annotated`, `Annotated#inheritsClassFloor()` (which inherits the floor of its class)
        //    and `Annotated.Nested` are in `T-ext` and `U-ext` (`33:10,34:10`). As
        //    `Annotated#inheritsClassFloor()` has the same `sdks` as its class it is omitted.
        //  * `Annotated#overridesClassFloor()` is only in `U-ext` (`34:10`).
        //  * `InvalidFloor` is in `T-ext` and `U-ext`, without causing an error.
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/AdServices" module="module" since="34" sdks="1000000:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/Annotated" module="module" since="34" sdks="33:10,34:10">
                        <method name="&lt;init>()V"/>
                        <method name="inheritsClassFloor()V"/>
                        <method name="overridesClassFloor()V" sdks="34:10"/>
                    </class>
                    <class name="test/pkg/Annotated${'$'}Nested" module="module" since="34" sdks="33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/InvalidFloor" module="module" since="34" sdks="33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/Legacy" module="module" since="34" sdks="30:10,31:10,33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi floor changing between extension versions`() {
        val apiVersionsXml =
            generateApiVersions(
                extensionVersions =
                    mapOf(
                        10 to
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Legacy {
                                    ctor public Legacy();
                                  }
                                  @SdkExtensionApi(33) public class LoweredFloor {
                                    ctor public LoweredFloor();
                                  }
                                  @SdkExtensionApi(33) public class RaisedFloor {
                                    ctor public RaisedFloor();
                                  }
                                }
                            """,
                        11 to
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  public class Legacy {
                                    ctor public Legacy();
                                  }
                                  @SdkExtensionApi(31) public class LoweredFloor {
                                    ctor public LoweredFloor();
                                  }
                                  @SdkExtensionApi(34) public class RaisedFloor {
                                    ctor public RaisedFloor();
                                  }
                                }
                            """,
                    ),
                currentApi =
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Legacy {
                            ctor public Legacy();
                          }
                          @SdkExtensionApi(31) public class LoweredFloor {
                            ctor public LoweredFloor();
                          }
                          @SdkExtensionApi(34) public class RaisedFloor {
                            ctor public RaisedFloor();
                          }
                        }
                    """,
                symbols = legacySymbol,
            )

        // The floor from the newest signature file is used to compute the `sdks` attribute:
        //  * `LoweredFloor` is in `S-ext`, `T-ext` and `U-ext` (`31:10,33:10,34:10`).
        //  * `RaisedFloor` is only in `U-ext` (`34:10`).
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/Legacy" module="module" since="34" sdks="30:10,31:10,33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/LoweredFloor" module="module" since="34" sdks="31:10,33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/RaisedFloor" module="module" since="34" sdks="34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }

    /**
     * Generate an `api-versions.xml` file for a `module` jar containing [classes] (in package
     * `test.pkg`), which is the same in extension version `10` and the current API, and the
     * `<symbol>` rules [symbols].
     */
    private fun generateApiVersionsForSingleVersion(
        classes: String,
        symbols: String,
        expectedError: String? = null,
    ): File {
        // Build the contents line by line, as substituting the multi-line [classes] into a raw
        // string would prevent trimIndent() from removing the indent from the other lines.
        val moduleApi =
            listOf(
                    "// Signature format: 2.0",
                    "package test.pkg {",
                    classes.trimIndent().prependIndent("  "),
                    "  public class Legacy {",
                    "    ctor public Legacy();",
                    "  }",
                    "}",
                )
                .joinToString("\n")
        return generateApiVersions(
            extensionVersions = mapOf(10 to moduleApi),
            currentApi = moduleApi,
            symbols = symbols.trimIndent() + "\n" + legacySymbol,
            expectedError = expectedError,
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi and an agreeing symbol rule`() {
        // An API can be configured in both @SdkExtensionApi and a `<symbol>` rule as long as they
        // agree.
        val apiVersionsXml =
            generateApiVersionsForSingleVersion(
                classes =
                    """
                        @SdkExtensionApi(33) public class Agreeing {
                          ctor public Agreeing();
                        }
                    """,
                symbols =
                    """
                        <symbol jar="module" pattern="test.pkg.Agreeing" sdks="T-ext,U-ext" />
                    """,
            )

        // The @SdkExtensionApi floor agrees with the `<symbol>` rule, so the output is unchanged.
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/Agreeing" module="module" since="34" sdks="33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/Legacy" module="module" since="34" sdks="30:10,31:10,33:10,34:10">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi and a disagreeing symbol rule`() {
        // An API configured in both @SdkExtensionApi and a `<symbol>` rule fails if they disagree.
        generateApiVersionsForSingleVersion(
            classes =
                """
                    @SdkExtensionApi(34) public class Disagreeing {
                      ctor public Disagreeing();
                    }
                """,
            symbols =
                """
                    <symbol jar="module" pattern="test.pkg.Disagreeing" sdks="R-ext,S-ext" />
                """,
            expectedError =
                """
                    Aborting: test.pkg.Disagreeing has @SdkExtensionApi(34) with sdks="U-ext" but its <symbol> rule in sdk-extensions-info.xml has sdks="R-ext,S-ext"; they must agree
                """,
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi on a member matched by a class symbol rule`() {
        // A member with its own @SdkExtensionApi fails if it disagrees with the `<symbol>` rule for
        // its class.
        generateApiVersionsForSingleVersion(
            classes =
                """
                    public class MemberAnnotated {
                      ctor public MemberAnnotated();
                      method @SdkExtensionApi(34) public void annotated();
                    }
                """,
            symbols =
                """
                    <symbol jar="module" pattern="test.pkg.MemberAnnotated" sdks="T-ext,U-ext" />
                """,
            expectedError =
                """
                    Aborting: test.pkg.MemberAnnotated#annotated has @SdkExtensionApi(34) with sdks="U-ext" but its <symbol> rule in sdk-extensions-info.xml has sdks="T-ext,U-ext"; they must agree
                """,
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi floor changed in an extension version without current API`() {
        // Simulates a finalized release build, which does not use the current API, where an SDK
        // extension version was finalized after the newest historical Android SDK version and
        // changed the floor of `Foo`.
        val apiVersionsXml =
            generateApiVersions(
                platformApi =
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @SdkExtensionApi(34) public class Foo {
                            ctor public Foo();
                          }
                          public class Legacy {
                            ctor public Legacy();
                          }
                        }
                    """,
                extensionVersions =
                    mapOf(
                        10 to
                            """
                                // Signature format: 2.0
                                package test.pkg {
                                  @SdkExtensionApi(33) public class Foo {
                                    ctor public Foo();
                                  }
                                  public class Legacy {
                                    ctor public Legacy();
                                  }
                                }
                            """
                    ),
                currentApi = null,
                symbols = legacySymbol,
            )

        // `Foo` uses the floor from the SDK extension version (33) rather than the floor from the
        // older historical Android SDK version (34), so it is in `T-ext`. `U-ext` is omitted from
        // the `sdks` attribute of both `Foo` and `Legacy` as they were added in Android SDK 33,
        // which `U-ext` does not supersede.
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/Foo" module="module" since="33" sdks="33:10,0:33">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/Legacy" module="module" since="33" sdks="30:10,31:10,33:10,0:33">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for @SdkExtensionApi in a module without symbol rules`() {
        // A module that has been fully migrated to @SdkExtensionApi has no `<symbol>` rules.
        val moduleApi =
            """
                // Signature format: 2.0
                package test.pkg {
                  @SdkExtensionApi(33) public class Migrated {
                    ctor public Migrated();
                  }
                }
            """

        val apiVersionsXml =
            generateApiVersions(
                extensionVersions = mapOf(10 to moduleApi),
                currentApi = moduleApi,
                symbols = "",
            )

        // TODO(b/507875039): The extension versions of a module without any `<symbol>` rules are
        //  not scanned, so `Migrated` has no `module` or `sdks` attribute. It should be in `T-ext`
        //  and `U-ext` (`33:10,34:10`).
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/Migrated" since="34">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for a module without symbol rules or @SdkExtensionApi`() {
        // A module that is not in any SDK extension has neither `<symbol>` rules nor
        // @SdkExtensionApi.
        val moduleApi =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class NotInSdkExtension {
                    ctor public NotInSdkExtension();
                  }
                }
            """

        val apiVersionsXml =
            generateApiVersions(
                extensionVersions = mapOf(10 to moduleApi),
                currentApi = moduleApi,
                symbols = "",
            )

        // The extension versions of the module are not scanned, so `NotInSdkExtension` has no
        // `module` or `sdks` attribute.
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="33">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}TIRAMISU"/>
                    <sdk id="34" shortname="U-ext" name="U Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}UPSIDE_DOWN_CAKE"/>
                    <sdk id="1000000" shortname="AD_SERVICES-ext" name="Ad Services Extensions" reference="android/os/ext/SdkExtensions${'$'}AD_SERVICES"/>
                    <class name="test/pkg/NotInSdkExtension" since="34">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """
        )
    }
}
