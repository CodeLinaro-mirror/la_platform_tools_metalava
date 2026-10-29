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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_API_VERSION_FOR_SDK_EXTENSION
import com.android.tools.metalava.ARG_API_VERSION_FOR_SOURCES
import com.android.tools.metalava.ARG_API_VERSION_LABEL
import com.android.tools.metalava.ARG_API_VERSION_SIGNATURE_FILES
import com.android.tools.metalava.ARG_API_VERSION_SIGNATURE_PATTERN
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_GENERATE_API_VERSION_HISTORY
import com.android.tools.metalava.ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownConfigFiles
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import com.google.common.truth.Truth.assertThat
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.ClassRule
import org.junit.Test

// Constants to avoid having to quote $ in expected XML contents.
const val VERSION_CODES = "${'\$'}VERSION_CODES"
const val R = "${'\$'}R"
const val S = "${'\$'}S"

class ApiGeneratorTest : DriverTest() {
    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private val doublyNestedTestJar =
            jarFromSources(
                    "doubly-nested-test.jar",
                    java(
                        """
                            package test.pkg;
                            public class Test {
                                private Test() {}
                                public class Nested {
                                    private Nested() {}
                                    public class DoublyNested {
                                        private DoublyNested() {}
                                    }
                                }
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)
    }

    /** Check this `api-versions.xml` file has the correct content. */
    private fun File.checkApiVersionsXmlContent(expectedContent: String) {
        assertTrue("$this was expected to be a plain file but is not", isFile)
        val xml = readText()

        // The generated XML is indented using tabs which do not work well in a raw string as
        // editors can replace them with normal spaces. So, this replaces all tabs with 4 spaces.
        val indentedWithSpaces = xml.replace("\t", "    ").trim()
        assertEquals(expectedContent.trimIndent(), indentedWithSpaces)
    }

    /** Check this `api-versions.json` file has the correct content. */
    private fun File.checkApiVersionsJsonContent(expectedContent: String) {
        assertTrue("$this was expected to be a plain file but is not", isFile)
        val json = readText()

        // Read output and reprint with pretty printing enabled to make test failures easier to read
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(json, JsonElement::class.java)
        val prettyOutput = gson.toJson(outputJson)

        assertEquals(expectedContent.trimIndent(), prettyOutput)
    }

    /** Check `--generate-api-levels` from test files in the metalava directory. */
    private fun checkGenerateFromTestFiles(
        sdkExtensionsInfo: String,
        extraArguments: Array<String>,
    ) {
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            configFiles = arrayOf(KnownConfigFiles.configPublicSurface),
            extraArguments =
                arrayOf(
                    ARG_API_SURFACE,
                    "public",
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfo,
                    ARG_API_VERSION_FOR_SOURCES,
                    "32",
                    ARG_API_VERSION_LABEL,
                    "32:Foo"
                ) + extraArguments,
            // Apply the api-versions.xml file that is being generated by this.
            applyApiLevelsXml = apiVersionsXml.path,
            // Do not prevent java.lang.Object from being included in the API.
            skipEmitPackages = emptyList(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.test;
                            public class ClassAddedInApi31AndExt2 {
                                private ClassAddedInApi31AndExt2() {}
                                public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 1;
                                public static final int FIELD_ADDED_IN_EXT_3 = 2;
                                public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                                public void methodAddedInExt3() { throw new RuntimeException("Stub!"); }
                                public void methodNotFinalized() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package java.lang;
                            public class Object {
                            }
                        """
                    ),
                ),
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.test;
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class ClassAddedInApi31AndExt2 {
                            ClassAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            public void methodAddedInApi31AndExt2() { throw new RuntimeException("Stub!"); }
                            /** @sdkExtSince R Extensions 3 */
                            public void methodAddedInExt3() { throw new RuntimeException("Stub!"); }
                            /** @apiSince Foo */
                            public void methodNotFinalized() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 2
                             */
                            public static final int FIELD_ADDED_IN_API_31_AND_EXT_2 = 1;
                            /** @sdkExtSince R Extensions 3 */
                            public static final int FIELD_ADDED_IN_EXT_3 = 2;
                            }
                        """
                    ),
                ),
        )

        val expected =
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="30">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build$VERSION_CODES$R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build$VERSION_CODES$S"/>
                    <class name="android/test/ClassAddedAndDeprecatedInApi30" since="30" deprecated="30" removed="32">
                        <extends name="java/lang/Object"/>
                        <method name="&lt;init>(F)V"/>
                        <method name="&lt;init>(I)V"/>
                        <method name="methodExplicitlyDeprecated()V"/>
                        <method name="methodImplicitlyDeprecated()V"/>
                        <field name="FIELD_EXPLICITLY_DEPRECATED"/>
                        <field name="FIELD_IMPLICITLY_DEPRECATED"/>
                    </class>
                    <class name="android/test/ClassAddedInApi30" module="framework-ext" since="30" sdks="30:2,0:30">
                        <extends name="android/test/MarkerSuperClass" since="32" sdks="30:2,31:2"/>
                        <extends name="java/lang/Object" removed="32"/>
                        <implements name="android/test/MarkerInterface" since="32" sdks="30:2,31:2"/>
                        <method name="methodAddedInApi30()V"/>
                        <method name="methodAddedInApi31()V" since="31" sdks="30:2,31:2,0:31"/>
                    </class>
                    <class name="android/test/ClassAddedInApi31AndExt2" module="framework-ext" since="31" sdks="30:2,31:2,0:31">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInApi31AndExt2()V"/>
                        <method name="methodAddedInExt3()V" since="32" sdks="30:3,31:3"/>
                        <method name="methodNotFinalized()V" since="32" sdks="0:32"/>
                        <field name="FIELD_ADDED_IN_API_31_AND_EXT_2"/>
                        <field name="FIELD_ADDED_IN_EXT_3" since="32" sdks="30:3,31:3"/>
                    </class>
                    <class name="android/test/ClassAddedInExt1" module="framework-ext" since="31" sdks="30:1,31:1,0:31">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInApi31AndExt2()V" sdks="30:2,31:2,0:31"/>
                        <method name="methodAddedInExt1()V"/>
                        <method name="methodAddedInExt3()V" since="32" sdks="30:3,31:3"/>
                        <field name="FIELD_ADDED_IN_API_31_AND_EXT_2" sdks="30:2,31:2,0:31"/>
                        <field name="FIELD_ADDED_IN_EXT_1"/>
                        <field name="FIELD_ADDED_IN_EXT_3" since="32" sdks="30:3,31:3"/>
                    </class>
                    <class name="android/test/ClassAddedInExt3" module="framework-ext" since="32" sdks="30:3,31:3">
                        <extends name="java/lang/Object"/>
                        <method name="methodAddedInExt3()V"/>
                        <field name="FIELD_ADDED_IN_EXT_3"/>
                    </class>
                    <class name="android/test/MarkerInterface" module="framework-ext" since="32" sdks="30:2,31:2">
                        <extends name="java/lang/Object"/>
                    </class>
                    <class name="android/test/MarkerSuperClass" module="framework-ext" since="32" sdks="30:2,31:2">
                        <extends name="java/lang/Object"/>
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="java/lang/Object" since="30">
                        <method name="&lt;init>()V"/>
                    </class>
                </api>
            """

        apiVersionsXml.checkApiVersionsXmlContent(expected)
    }

    // TODO(b/378479241): Fix this test to make it more realistic by including definitions of the
    //  current API or stopping it from including the current API.
    @Test
    fun `Generate API for test prebuilts`() {
        val testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        checkGenerateFromTestFiles(
            sdkExtensionsInfo = "${testPrebuiltsRoot.path}/sdk-extensions-info.xml",
            extraArguments =
                arrayOf(
                    ARG_ANDROID_JAR_PATTERN,
                    "${testPrebuiltsRoot.path}/{version:level}/public/android.jar",
                    ARG_ANDROID_JAR_PATTERN,
                    "${testPrebuiltsRoot.path}/extensions/{version:extension}/*/{module}.jar",
                ),
        )
    }

    @Test
    fun `Generate API from signature files using --generate-api-levels`() {
        val testdataDir = File(System.getenv("METALAVA_TESTDATA_DIR"))
        if (!testdataDir.isDirectory) {
            fail("testdata directory not found: $testdataDir")
        }

        val prebuiltsSdkDir = testdataDir.resolve("prebuilts/sdk")

        checkGenerateFromTestFiles(
            sdkExtensionsInfo =
                testdataDir.resolve("prebuilts-sdk-test/sdk-extensions-info.xml").path,
            extraArguments =
                arrayOf(
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$prebuiltsSdkDir/{version:level}/{surface}/api.txt",
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$prebuiltsSdkDir/extensions/{version:extension}/{surface}/{module}.txt",
                ),
        )
    }

    @Test
    fun `Generate API while removing missing class references`() {
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                    ARG_API_VERSION_FOR_SOURCES,
                    "32",
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.test;
                    public class ClassThatImplementsMethodFromApex implements ClassFromApex {
                    }
                    """
                    )
                )
        )

        val expected =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <api version="3" min="32">
                <class name="android/test/ClassThatImplementsMethodFromApex" since="32">
                    <method name="&lt;init>()V"/>
                </class>
            </api>
        """

        apiVersionsXml.checkApiVersionsXmlContent(expected)
    }

    @RequiresCapabilities(Capability.LOAD_JAR)
    @Test
    fun `Generate API for system surface from jar`() {
        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            configFiles = arrayOf(KnownConfigFiles.configPublicAndSystemSurfaces),
            extraArguments =
                arrayOf(
                    ARG_API_SURFACE,
                    "system",
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_API_VERSION_FOR_SOURCES,
                    "32",
                ),
            sourceFiles = arrayOf(doublyNestedTestJar),
        )

        val expected =
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="32">
                    <class name="test/pkg/Test" since="32">
                    </class>
                    <class name="test/pkg/Test${'$'}Nested" since="32">
                    </class>
                    <class name="test/pkg/Test${'$'}Nested${'$'}DoublyNested" since="32">
                    </class>
                </api>
            """

        apiVersionsXml.checkApiVersionsXmlContent(expected)
    }

    @Test
    fun `Generate API finds missing class references`() {
        val testPrebuiltsRoot = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!testPrebuiltsRoot.isDirectory) {
            fail("test prebuilts not found: $testPrebuiltsRoot")
        }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        var exception: IllegalStateException? = null
        try {
            check(
                extraArguments =
                    arrayOf(
                        ARG_GENERATE_API_LEVELS,
                        apiVersionsXml.path,
                        ARG_API_VERSION_FOR_SOURCES,
                        "32",
                    ),
                sourceFiles =
                    arrayOf(
                        java(
                            """
                        package android.test;
                        // Really this class should implement some interface that doesn't exist,
                        // but that's hard to set up in the test harness, so just verify that
                        // metalava complains about java/lang/Object not existing because we didn't
                        // include the testdata prebuilt jars.
                        public class ClassThatImplementsMethodFromApex {
                        }
                        """
                        )
                    )
            )
        } catch (e: IllegalStateException) {
            exception = e
        }

        assertNotNull(exception)
        assertThat(exception?.message ?: "")
            .contains(
                "There are classes in this API that reference other classes that do not exist in this API."
            )
        assertThat(exception?.message ?: "")
            .contains(
                "java/lang/Object referenced by:\n    android/test/ClassThatImplementsMethodFromApex"
            )
    }

    @Test
    fun `Create JSON and XML API versions from signature files`() {
        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public <T extends java.lang.String> void methodV1(T);
                            field public int fieldV1;
                          }
                          public class Foo.Bar {
                          }
                        }
                    """
                ),
                createTextFile(
                    "1.2.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public <T extends java.lang.String> void methodV1(T);
                            method @Deprecated public <T> void methodV2(String);
                            method @Deprecated public <T> void methodV2(String, int);
                            field public int fieldV1;
                            field public int fieldV2;
                          }
                          public class Foo.Bar {
                          }
                        }
                    """
                ),
                createTextFile(
                    "1.3.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method @Deprecated public <T extends java.lang.String> void methodV1(T);
                            method public <T> void methodV2(String);
                            method public void methodV3();
                            field public int fieldV1;
                            field public int fieldV2;
                          }
                          @Deprecated public class Foo.Bar {
                          }
                        }
                    """
                ),
            )
        val currentVersion =
            """
                package test.pkg {
                  public class Foo {
                    method @Deprecated public <T extends java.lang.String> void methodV1(T);
                    method public void methodV3();
                    method public void methodV4();
                    field public int fieldV1;
                    field public int fieldV2;
                  }
                  @Deprecated public class Foo.Bar {
                  }
                }
            """

        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        fun createExtraArguments(output: File) =
            arrayOf(
                ARG_GENERATE_API_VERSION_HISTORY,
                output.path,
                ARG_API_VERSION_SIGNATURE_FILES,
                pastVersions.joinToString(":") { it.absolutePath },
                ARG_API_VERSION_SIGNATURE_PATTERN,
                "${temporaryFolder.root}/{version:major.minor.patch}",
                ARG_API_VERSION_FOR_SOURCES,
                "1.4.0",
            )

        check(
            signatureSource = currentVersion,
            extraArguments = createExtraArguments(apiVersionsJson),
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Foo.Bar",
                    "addedIn": "1.1.0",
                    "deprecatedIn": "1.3.0",
                    "methods": [],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "methodV1<T extends java.lang.String>(T)",
                        "addedIn": "1.1.0",
                        "deprecatedIn": "1.3.0"
                      },
                      {
                        "method": "methodV2<T>(java.lang.String)",
                        "addedIn": "1.2.0"
                      },
                      {
                        "method": "methodV2<T>(java.lang.String,int)",
                        "addedIn": "1.2.0",
                        "deprecatedIn": "1.2.0"
                      },
                      {
                        "method": "methodV3()",
                        "addedIn": "1.3.0"
                      },
                      {
                        "method": "methodV4()",
                        "addedIn": "1.4.0"
                      }
                    ],
                    "fields": [
                      {
                        "field": "fieldV1",
                        "addedIn": "1.1.0"
                      },
                      {
                        "field": "fieldV2",
                        "addedIn": "1.2.0"
                      }
                    ]
                  }
                ]
            """
        )

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            signatureSource = currentVersion,
            extraArguments = createExtraArguments(apiVersionsXml),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="4" min="1.1.0">
                    <class name="test.pkg.Foo" since="1.1.0">
                        <extends name="java.lang.Object"/>
                        <method name="methodV1&lt;T extends java.lang.String>(T)" deprecated="1.3.0"/>
                        <method name="methodV2&lt;T>(java.lang.String)" since="1.2.0" removed="1.4.0"/>
                        <method name="methodV2&lt;T>(java.lang.String,int)" since="1.2.0" deprecated="1.2.0" removed="1.3.0"/>
                        <method name="methodV3()" since="1.3.0"/>
                        <method name="methodV4()" since="1.4.0"/>
                        <field name="fieldV1"/>
                        <field name="fieldV2" since="1.2.0"/>
                    </class>
                    <class name="test.pkg.Foo.Bar" since="1.1.0" deprecated="1.3.0">
                        <extends name="java.lang.Object"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Ensure class missing in current codebase is marked as removed`() {
        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                            field public int barField;
                          }
                          public class Foo extends test.pkg.Bar {
                            field public int fooField;
                          }
                        }
                    """
                ),
            )
        val currentVersion =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Foo {
                  }
                }
            """

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            signatureSource = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsXml.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "${temporaryFolder.root}/{version:major.minor.patch}",
                    ARG_API_VERSION_FOR_SOURCES,
                    "1.2.0",
                ),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="4" min="1.1.0">
                    <class name="test.pkg.Bar" since="1.1.0" removed="1.2.0">
                        <extends name="java.lang.Object"/>
                        <field name="barField"/>
                    </class>
                    <class name="test.pkg.Foo" since="1.1.0">
                        <extends name="java.lang.Object" since="1.2.0"/>
                        <extends name="test.pkg.Bar" removed="1.2.0"/>
                        <field name="fooField" removed="1.2.0"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `API levels can be generated from just the current codebase`() {
        val output = temporaryFolder.newFile("api-info.json")

        val api =
            """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String?);
                  }
                }
            """

        check(
            signatureSource = api,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    output.path,
                    ARG_API_VERSION_FOR_SOURCES,
                    "0.0.0-alpha01"
                )
        )

        val expectedJson =
            "[{\"class\":\"test.pkg.Foo\",\"addedIn\":\"0.0.0-alpha01\",\"methods\":[{\"method\":\"foo(java.lang.String)\",\"addedIn\":\"0.0.0-alpha01\"}],\"fields\":[]}]"
        assertEquals(expectedJson, output.readText())
    }

    @Test
    fun `API levels can be generated from just the current codebase using --current-version`() {
        val output = temporaryFolder.newFile("api-info.json")

        val api =
            """
                // Signature format: 4.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String?);
                  }
                }
            """

        check(
            signatureSource = api,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    output.path,
                    ARG_API_VERSION_FOR_SOURCES,
                    "0.0.0-alpha01"
                )
        )

        val expectedJson =
            "[{\"class\":\"test.pkg.Foo\",\"addedIn\":\"0.0.0-alpha01\",\"methods\":[{\"method\":\"foo(java.lang.String)\",\"addedIn\":\"0.0.0-alpha01\"}],\"fields\":[]}]"
        assertEquals(expectedJson, output.readText())
    }

    @Test
    fun `API levels using source as current version does not include inherited methods excluded from signatures`() {
        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0",
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public class Bar extends test.pkg.Foo {
                            ctor public Bar();
                          }
                          public class Foo {
                            ctor public Foo();
                            method public void inherited();
                          }
                        }
                    """
                )
            )
        val currentVersion =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                            public void inherited() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Bar extends Foo {
                            @Override
                            public void inherited() {}
                        }
                    """
                )
            )

        check(
            sourceFiles = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsJson.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "${temporaryFolder.root}/{version:major.minor.patch}",
                    ARG_API_VERSION_FOR_SOURCES,
                    "1.2.0",
                )
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Bar",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Bar()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  },
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "inherited()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `APIs annotated with suppress-compatibility-meta-annotations appear in output`() {
        val apiVersionsJson = temporaryFolder.newFile("api-info.json")

        val pastVersions =
            listOf(
                createTextFile(
                    "1.1.0.txt",
                    """
                        // Signature format: 4.0
                        package test.pkg {
                          public final class Foo {
                            ctor public Foo();
                            method @SuppressCompatibility @kotlin.RequiresOptIn public void experimentalFunction();
                            method public void regularFunction();
                          }
                        }
                    """
                )
            )
        val currentVersion =
            arrayOf(
                kotlin(
                    """
                        package test.pkg
                        class Foo {
                            @RequiresOptIn fun experimentalFunction() {}
                            @RequiresOptIn fun newExperimentalFunction() {}
                            fun regularFunction() {}
                        }
                    """
                )
            )

        check(
            sourceFiles = currentVersion,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn"),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsJson.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "${temporaryFolder.root}/{version:major.minor.patch}.txt",
                    ARG_API_VERSION_FOR_SOURCES,
                    "1.2.0",
                )
        )

        apiVersionsJson.checkApiVersionsJsonContent(
            """
                [
                  {
                    "class": "test.pkg.Foo",
                    "addedIn": "1.1.0",
                    "methods": [
                      {
                        "method": "Foo()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "experimentalFunction()",
                        "addedIn": "1.1.0"
                      },
                      {
                        "method": "newExperimentalFunction()",
                        "addedIn": "1.2.0"
                      },
                      {
                        "method": "regularFunction()",
                        "addedIn": "1.1.0"
                      }
                    ],
                    "fields": []
                  }
                ]
            """
        )
    }

    private fun createTextFile(name: String, contents: String) = signature(name, contents).toFile()

    @Test
    fun `Support major minor versions in generated api-versions xml file`() {
        val pastVersions =
            listOf(
                createTextFile(
                    "1.2",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                          }
                        }
                    """
                ),
                createTextFile(
                    "1.3",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                          }
                          public class Foo extends test.pkg.Bar {
                          }
                        }
                    """
                ),
            )
        val currentVersion =
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Bar {
                  }
                  public class Foo extends test.pkg.Bar {
                    field public int field;
                  }
                }
            """

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            signatureSource = currentVersion,
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_VERSION_HISTORY,
                    apiVersionsXml.path,
                    ARG_API_VERSION_SIGNATURE_FILES,
                    pastVersions.joinToString(":") { it.absolutePath },
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "${temporaryFolder.root}/{version:major.minor?}",
                    ARG_API_VERSION_FOR_SOURCES,
                    "2.0",
                ),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="4" min="1.2">
                    <class name="test.pkg.Bar" since="1.2">
                        <extends name="java.lang.Object"/>
                    </class>
                    <class name="test.pkg.Foo" since="1.3">
                        <extends name="test.pkg.Bar"/>
                        <field name="field" since="2.0"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test deprecating SDK extension API`() {
        val root = buildFileStructure {
            dir("39") {
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                          }
                        }
                    """
                )
            }
            dir("40") {
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @Deprecated public class Foo {
                          }
                        }
                    """
                )
            }
            dir("extensions") {
                dir("102") {
                    signature(
                        "module.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                              }
                            }
                        """
                    )
                }
                dir("103") {
                    signature(
                        "module.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @Deprecated public class Foo {
                              }
                            }
                        """
                    )
                }
            }
        }

        val sdkExtensionsInfoXml =
            temporaryFolder.newFile("sdk-extensions-info.xml").apply {
                writeText(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <sdk-extensions-info>
                        <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                        <sdk id="40" shortname="K-ext" name="K Extensions" reference="VersionCode.K"/>
                        <symbol jar="module" pattern="*" sdks="J-ext,K-ext" />
                        </sdk-extensions-info>
                    """
                        .trimIndent()
                )
            }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * @deprecated Deprecated
                             */
                            @Deprecated public class Foo {
                                private Foo() {}
                            }
                        """
                    ),
                ),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfoXml.path,
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$root/{version:major.minor?}/api.txt",
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$root/extensions/{version:extension}/{module}.txt",
                    ARG_API_VERSION_FOR_SOURCES,
                    "41",
                    ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                ),
        )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="39">
                    <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                    <sdk id="40" shortname="K-ext" name="K Extensions" reference="VersionCode.K"/>
                    <class name="test/pkg/Foo" module="module" since="39" sdks="39:102,0:39" deprecated="40">
                    </class>
                </api>
            """
        )
    }

    /**
     * Generate an api-versions.xml file for some SDK extensions.
     *
     * @param includeCurrentSource true if the current source should be included, false otherwise.
     * @param apiVersionForSdkExtension the API version that should be used for SDK extension APIs
     *   that have been added since the last dessert release. If the SDK extension is being released
     *   standalone then this will be some magic number, otherwise it will be the version of the
     *   next dessert release.
     */
    private fun generateApiVersionsForSdkExtensions(
        includeCurrentSource: Boolean,
        apiVersionForSdkExtension: String
    ): File {
        val root = buildFileStructure {
            dir("39") {
                signature(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                          }
                        }
                    """
                )
            }
            dir("extensions") {
                dir("102") {
                    signature(
                        "module.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void addedInExtension102();
                              }
                            }
                        """
                    )
                }
            }
        }

        val sdkExtensionsInfoXml =
            temporaryFolder.newFile("sdk-extensions-info.xml").apply {
                writeText(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <sdk-extensions-info>
                        <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                        <symbol jar="module" pattern="*" sdks="J-ext" />
                        </sdk-extensions-info>
                    """
                        .trimIndent()
                )
            }

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")
        // current sources are only included if --api-versions-for-sources is provided
        val prospectiveApiVersion = "40"
        val prospectiveSdkArgs =
            if (includeCurrentSource)
                arrayOf(
                    ARG_API_VERSION_FOR_SOURCES,
                    prospectiveApiVersion,
                )
            else arrayOf()

        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                private Foo() {}
                                public void addedInExtension102() {}
                                public void addedInLatest() {}
                            }
                        """
                    ),
                ),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfoXml.path,
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$root/{version:major.minor?}/api.txt",
                    ARG_API_VERSION_SIGNATURE_PATTERN,
                    "$root/extensions/{version:extension}/{module}.txt",
                    ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                    ARG_API_VERSION_FOR_SDK_EXTENSION,
                    apiVersionForSdkExtension,
                    *prospectiveSdkArgs
                )
        )

        return apiVersionsXml
    }

    @Test
    fun `Test api-versions for new SDK version including corresponding SDK extension version`() {
        // Scenario: Finalizing a new dessert release.
        // * The SDK extension 102 has already been finalized and dropped into
        //   `prebuilts/sdk/extensions`.
        // * Dessert release 40 is being finalized and will include SDK extension 102.

        val apiVersionsXml =
            generateApiVersionsForSdkExtensions(
                includeCurrentSource = true,
                // The api-versions.xml is being produced for the next SDK version in which the
                // SDK extension will appear so use that version.
                apiVersionForSdkExtension = "40",
            )

        // TODO(b/424440294): The `addedInExtension102()` method should have `0:40` in its `sdks`
        //  attribute as it was added in SDK version 40.
        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="39">
                    <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                    <class name="test/pkg/Foo" module="module" since="39" sdks="39:102,0:39">
                        <method name="addedInExtension102()V" since="40" sdks="39:102"/>
                        <method name="addedInLatest()V" since="40" sdks="0:40"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for standalone SDK extension version without current source`() {
        // This tests the finalizing a new standalone SDK extension scenario version 103.

        val apiVersionsXml =
            generateApiVersionsForSdkExtensions(
                includeCurrentSource = false,
                // Use a special value for the API version in which APIs added in extension
                // 102 will appear at some point in the future.
                apiVersionForSdkExtension = "999999",
            )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="39">
                    <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                    <class name="test/pkg/Foo" module="module" since="39" sdks="39:102,0:39">
                        <method name="addedInExtension102()V" since="999999" sdks="39:102"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Test api-versions for standalone SDK extension version with current source`() {
        val apiVersionsXml =
            generateApiVersionsForSdkExtensions(
                includeCurrentSource = true,
                // Use a special value for the API version in which APIs added in extension
                // 102 will appear at some point in the future.
                apiVersionForSdkExtension = "999999",
            )

        apiVersionsXml.checkApiVersionsXmlContent(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="39">
                    <sdk id="39" shortname="J-ext" name="J Extensions" reference="VersionCode.J"/>
                    <class name="test/pkg/Foo" module="module" since="39" sdks="39:102,0:39">
                        <method name="addedInExtension102()V" since="40" sdks="39:102,0:40"/>
                        <method name="addedInLatest()V" since="40"/>
                    </class>
                </api>
            """
        )
    }

    /**
     * Checks consistency between historical and latest code when generating API levels and stubs.
     *
     * It creates an initial release (API 1.0) in bytecode (`android.jar`) from [sourceFiles] and a
     * current release (API 2.0) from source code, then asserts that the generated stubs match
     * [expectedStubs] and the generated `api-versions.xml` matches [expectedApiVersionsXml].
     */
    private fun checkConsistencyBetweenHistoricalAndLatestCode(
        vararg sourceFiles: TestFile,
        currentVersionSourceFiles: List<TestFile>? = null,
        expectedStubs: List<TestFile>,
        expectedApiVersionsXml: String,
        expectedApiSignature: String? = null,
    ) {
        val root = buildFileStructure {
            dir("1.0") {
                jar(
                    "android.jar",
                    *sourceFiles,
                )
            }
        }

        val currentVersion =
            arrayOf(
                *(currentVersionSourceFiles?.toTypedArray() ?: sourceFiles),
                java(
                    """
                        package java.lang;
                        public class Object {
                            public Object() {}
                        }
                    """
                ),
            )

        val apiVersionsXml = temporaryFolder.newFile("api-versions.xml")

        check(
            sourceFiles = currentVersion,
            docStubs = true,
            applyApiLevelsXml = apiVersionsXml.path,
            skipEmitPackages = emptyList(),
            expectedStubFiles = expectedStubs.toTypedArray(),
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    apiVersionsXml.path,
                    ARG_ANDROID_JAR_PATTERN,
                    "$root/{version:major.minor?}/android.jar",
                    ARG_API_VERSION_FOR_SOURCES,
                    "2.0",
                ),
            expectedApiSignature = expectedApiSignature,
        )

        apiVersionsXml.checkApiVersionsXmlContent(expectedApiVersionsXml)
    }

    /**
     * Verifies the behavior when a hidden superclass (HiddenSuper) has a public constructor with
     * different parameters to the subclass's (Foo) constructor. When reading android.jar from
     * bytecode, Foo does not inline the constructor `<init>(I)V` from HiddenSuper.
     */
    @Test
    fun `Hidden super class has public constructor with different parameters to subclass`() {
        checkConsistencyBetweenHistoricalAndLatestCode(
            java(
                """
                    package test.pkg;
                    class HiddenSuper {
                        public HiddenSuper(int x) {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo extends HiddenSuper {
                        public Foo() {
                            super(0);
                        }
                    }
                """
            ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 1 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/Foo" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                        </class>
                    </api>
                """,
        )
    }

    /**
     * Verifies the behavior when inheriting fields from a hidden super class (HiddenSuper). Only
     * public constant (public static final) fields are inherited into the subclass (Foo).
     */
    @Test
    fun `Inheriting fields from a hidden super class`() {
        checkConsistencyBetweenHistoricalAndLatestCode(
            java(
                """
                    package test.pkg;
                    class HiddenSuper {
                        int packageField = 1;
                        static int packageStaticField = 2;
                        static final int PACKAGE_STATIC_FINAL_FIELD = 3;
                        protected int protectedField = 4;
                        protected static int protectedStaticField = 5;
                        protected static final int PROTECTED_STATIC_FINAL_FIELD = 6;
                        public int publicField = 7;
                        public static int publicStaticField = 8;
                        public static final int PUBLIC_STATIC_FINAL_FIELD = 9;
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo extends HiddenSuper {
                        public Foo() {}
                    }
                """
            ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 1 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public static final int PUBLIC_STATIC_FINAL_FIELD = 9;
                            }
                        """
                    )
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/Foo" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <field name="PUBLIC_STATIC_FINAL_FIELD"/>
                        </class>
                    </api>
                """,
        )
    }

    /**
     * Verifies the behavior when a method exists in a class in one version and is moved up to a
     * super class in a later version. It is not marked as removed in the later version because it
     * is still accessible through the super class.
     */
    @Test
    fun `Method moved up to a super class in a later version`() {
        checkConsistencyBetweenHistoricalAndLatestCode(
            java(
                """
                    package test.pkg;
                    public class Super {
                        public Super() {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo extends Super {
                        public Foo() {}
                        public void foo() {}
                    }
                """
            ),
            currentVersionSourceFiles =
                listOf(
                    java(
                        """
                            package test.pkg;
                            public class Super {
                                public Super() {}
                                public void foo() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Foo extends Super {
                                public Foo() {}
                            }
                        """
                    ),
                ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo extends test.pkg.Super {
                            /** @apiSince 1 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Super {
                            /** @apiSince 1 */
                            public Super() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 2 */
                            public void foo() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/Foo" since="1.0">
                            <extends name="test/pkg/Super"/>
                            <method name="&lt;init>()V"/>
                            <method name="foo()V"/>
                        </class>
                        <class name="test/pkg/Super" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="foo()V" since="2.0"/>
                        </class>
                    </api>
                """,
        )
    }

    @Test
    fun `Method moved up to an interface in a later version`() {
        checkConsistencyBetweenHistoricalAndLatestCode(
            java(
                """
                    package test.pkg;
                    public interface CharSequence {}
                """
            ),
            java(
                """
                    package test.pkg;
                    class AbstractStringBuilder implements CharSequence {
                        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class StringBuffer extends AbstractStringBuilder implements CharSequence {
                        public StringBuffer() {}
                        public synchronized void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class StringBuilder extends AbstractStringBuilder implements CharSequence {
                        public StringBuilder() {}
                    }
                """
            ),
            currentVersionSourceFiles =
                listOf(
                    java(
                        """
                            package test.pkg;
                            public interface CharSequence {
                                public default void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class StringBuffer implements CharSequence {
                                public StringBuffer() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class StringBuilder implements CharSequence {
                                public StringBuilder() {}
                            }
                        """
                    ),
                ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public interface CharSequence {
                            /** @apiSince 2 */
                            public default void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class StringBuffer implements test.pkg.CharSequence {
                            /** @apiSince 1 */
                            public StringBuffer() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class StringBuilder implements test.pkg.CharSequence {
                            /** @apiSince 1 */
                            public StringBuilder() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/CharSequence" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="getChars(II[CI)V" since="2.0"/>
                        </class>
                        <class name="test/pkg/StringBuffer" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="getChars(II[CI)V"/>
                        </class>
                        <class name="test/pkg/StringBuilder" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="getChars(II[CI)V"/>
                        </class>
                    </api>
                """,
        )
    }

    /** Verifies the behavior when inheriting methods from a hidden super class (HiddenSuper). */
    @Test
    fun `Inheriting methods from a hidden super class`() {
        checkConsistencyBetweenHistoricalAndLatestCode(
            java(
                """
                    package test.pkg;
                    class HiddenSuper {
                        void packageMethod() {}
                        static void packageStaticMethod() {}
                        final void packageFinalMethod() {}
                        protected void protectedMethod() {}
                        protected static void protectedStaticMethod() {}
                        protected final void protectedFinalMethod() {}
                        public void publicMethod() {}
                        public static void publicStaticMethod() {}
                        public final void publicFinalMethod() {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    public class Foo extends HiddenSuper {
                        public Foo() {}
                    }
                """
            ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 1 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public final void publicFinalMethod() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public void publicMethod() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public static void publicStaticMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/Foo" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="publicFinalMethod()V"/>
                            <method name="publicMethod()V"/>
                            <method name="publicStaticMethod()V"/>
                        </class>
                    </api>
                """,
        )
    }

    /** Verifies special handling of methods that return an AbstractStringBuilder. */
    @Test
    fun `Inheriting methods from AbstractStringBuilder hidden super class`() {
        val abstractStringBuilderSource =
            java(
                """
                    package test.pkg;
                    class AbstractStringBuilder implements CharSequence {
                        public AbstractStringBuilder append(char c) { return this; }
                        public AbstractStringBuilder append(CharSequence s) { return this; }
                        public AbstractStringBuilder append(String str) { return this; }
                        public AbstractStringBuilder reverse() { return this; }
                        public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                        public void setLength(int newLength) {}
                        public int length() { return 0; }
                        public char charAt(int index) { return ' '; }
                        public CharSequence subSequence(int start, int end) { return null; }
                    }
                """
            )
        val stringBufferSource =
            java(
                """
                    package test.pkg;
                    public class StringBuffer extends AbstractStringBuilder {
                        public StringBuffer() {}
                        public synchronized StringBuffer append(char c) { return this; }
                        public synchronized StringBuffer append(CharSequence s) { return this; }
                        public synchronized StringBuffer append(String str) { return this; }
                        public synchronized StringBuffer reverse() { return this; }
                        public synchronized void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                        public synchronized void setLength(int newLength) {}
                        public synchronized int length() { return 0; }
                        public synchronized char charAt(int index) { return ' '; }
                        public synchronized CharSequence subSequence(int start, int end) { return null; }
                    }
                """
            )
        val stringBuilderSource =
            java(
                """
                    package test.pkg;
                    public class StringBuilder extends AbstractStringBuilder {
                        public StringBuilder() {}
                        public StringBuilder append(char c) { return this; }
                        public StringBuilder append(CharSequence s) { return this; }
                        public StringBuilder append(String str) { return this; }
                        public StringBuilder reverse() { return this; }
                    }
                """
            )
        checkConsistencyBetweenHistoricalAndLatestCode(
            abstractStringBuilderSource,
            stringBufferSource,
            stringBuilderSource,
            java(
                """
                    package test.pkg;
                    public interface CharSequence {
                        int length();
                        char charAt(int index);
                        CharSequence subSequence(int start, int end);
                    }
                """
            ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package java.lang {
                      public class Object {
                        ctor public Object();
                      }
                    }
                    package test.pkg {
                      public interface CharSequence {
                        method public char charAt(int);
                        method public default void getChars(int, int, char[]!, int);
                        method public int length();
                        method public test.pkg.CharSequence! subSequence(int, int);
                      }
                      public class StringBuffer implements test.pkg.CharSequence {
                        ctor public StringBuffer();
                        method public test.pkg.StringBuffer! append(char);
                        method public test.pkg.StringBuffer! append(String!);
                        method public test.pkg.StringBuffer! append(test.pkg.CharSequence!);
                        method public char charAt(int);
                        method public int length();
                        method public test.pkg.StringBuffer! reverse();
                        method public void setLength(int);
                        method public test.pkg.CharSequence! subSequence(int, int);
                      }
                      public class StringBuilder implements test.pkg.CharSequence {
                        ctor public StringBuilder();
                        method public test.pkg.StringBuilder! append(char);
                        method public test.pkg.StringBuilder! append(String!);
                        method public test.pkg.StringBuilder! append(test.pkg.CharSequence!);
                        method public char charAt(int);
                        method public int length();
                        method public test.pkg.StringBuilder! reverse();
                        method public void setLength(int);
                        method public test.pkg.CharSequence! subSequence(int, int);
                      }
                    }
                """,
            currentVersionSourceFiles =
                listOf(
                    abstractStringBuilderSource,
                    stringBufferSource,
                    stringBuilderSource,
                    java(
                        """
                            package test.pkg;
                            public interface CharSequence {
                                int length();
                                char charAt(int index);
                                CharSequence subSequence(int start, int end);
                                default void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {}
                            }
                        """
                    ),
                ),
            expectedStubs =
                listOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public interface CharSequence {
                            /** @apiSince 1 */
                            public char charAt(int index);
                            /** @apiSince 2 */
                            public default void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public int length();
                            /** @apiSince 1 */
                            public test.pkg.CharSequence subSequence(int start, int end);
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class StringBuffer implements test.pkg.CharSequence {
                            /** @apiSince 1 */
                            public StringBuffer() { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized test.pkg.StringBuffer append(char c) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized test.pkg.StringBuffer append(java.lang.String str) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized test.pkg.StringBuffer append(test.pkg.CharSequence s) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized char charAt(int index) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized int length() { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized test.pkg.StringBuffer reverse() { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized void setLength(int newLength) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public synchronized test.pkg.CharSequence subSequence(int start, int end) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class StringBuilder implements test.pkg.CharSequence {
                            /** @apiSince 1 */
                            public StringBuilder() { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public test.pkg.StringBuilder append(char c) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public test.pkg.StringBuilder append(java.lang.String str) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public test.pkg.StringBuilder append(test.pkg.CharSequence s) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public char charAt(int index) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public int length() { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public test.pkg.StringBuilder reverse() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 1 */
                            public void setLength(int newLength) { throw new RuntimeException("Stub!"); }
                            /**
                             * {@inheritDoc}
                             * @apiSince 1
                             */
                            public test.pkg.CharSequence subSequence(int start, int end) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            expectedApiVersionsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4" min="1.0">
                        <class name="java/lang/Object" since="2.0">
                            <method name="&lt;init>()V"/>
                        </class>
                        <class name="test/pkg/CharSequence" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="charAt(I)C"/>
                            <method name="getChars(II[CI)V" since="2.0"/>
                            <method name="length()I"/>
                            <method name="subSequence(II)Ltest/pkg/CharSequence;"/>
                        </class>
                        <class name="test/pkg/StringBuffer" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="append(C)Ltest/pkg/StringBuffer;"/>
                            <method name="append(Ljava/lang/String;)Ltest/pkg/StringBuffer;"/>
                            <method name="append(Ltest/pkg/CharSequence;)Ltest/pkg/StringBuffer;"/>
                            <method name="getChars(II[CI)V"/>
                            <method name="reverse()Ltest/pkg/StringBuffer;"/>
                            <method name="setLength(I)V"/>
                        </class>
                        <class name="test/pkg/StringBuilder" since="1.0">
                            <extends name="java/lang/Object"/>
                            <method name="&lt;init>()V"/>
                            <method name="append(C)Ltest/pkg/StringBuilder;"/>
                            <method name="append(Ljava/lang/String;)Ltest/pkg/StringBuilder;"/>
                            <method name="append(Ltest/pkg/CharSequence;)Ltest/pkg/StringBuilder;"/>
                            <method name="getChars(II[CI)V"/>
                            <method name="reverse()Ltest/pkg/StringBuilder;"/>
                            <method name="setLength(I)V"/>
                        </class>
                    </api>
                """,
        )
    }
}
