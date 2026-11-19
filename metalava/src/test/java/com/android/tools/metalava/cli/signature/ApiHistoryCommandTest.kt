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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.KnownConfigFiles
import com.android.tools.metalava.apilevels.VersionedApi
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.xml
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

/** Expected help text for the `api-history` sub-command. */
private val apiHistoryHelp =
    """
Usage: metalava api-history [options]

  Generate an API versions XML or JSON file from API signature files.

Options:
  --output-file <file>                       Output XML or JSON API versions file to generate. The format is determined
                                             from the file extension, which must be `.xml` or `.json` respectively.
                                             (required)
  --pattern <historical-api-pattern>         Pattern to use to locate historical signature files. Typically ends with
                                             `.txt`.

                                             See `metalava help historical-api-patterns` for more information.
  --historical-files <files>                 Historical signature files (separated by `${File.pathSeparator}`) to scan using --pattern.
  --historical-versions <api-version>:<api-version>
                                             The optional range of historical versions that can be included in the API
                                             version history. The `from` and `to` parts of the range are separated by a
                                             `:` and are both inclusive. See --current-version for acceptable
                                             `<api-version>`s.

                                             If unspecified, version history will default to include every historical
                                             version.
  --current-version <api-version>            Sets the API version for the current API signature files specified by
                                             --current-files. This supports a single integer level, `major.minor`,
                                             `major.minor.patch` and `major.minor.patch-quality` formats. Where `major`,
                                             `minor` and `patch` are all non-negative integers and `quality` is an
                                             alphanumeric string.
  --current-files <files>                    Current API signature files (separated by `${File.pathSeparator}`) for the API version
                                             specified by --current-version.
  --api-surface <surface>                    The API surface currently being generated. Must correspond to an
                                             <api-surface> element in a --config-file.
  --sdk-extensions-info <sdk-info-file>      Points to map of extension SDK APIs to include, if any. The file is an XML
                                             file and describes, per extension SDK, what APIs from that extension to
                                             include in the file created via --output-file.

                                             If specified then --pattern must include at least one pattern that uses
                                             `{version:extension}` and `{module}` placeholders and that pattern must
                                             match at least one file in --historical-files.
  --sdk-extension-versions <api-version>:<api-version>
                                             The optional range of historical SDK extension versions that can be
                                             included in the API version history. The `from` and `to` parts of the range
                                             are separated by a `:` and are both inclusive. See --current-version for
                                             acceptable `<api-version>`s.

                                             If unspecified then allow every historical version.
  --api-version-for-sdk-extension <api-version>
                                             SDK extension APIs can be added between SDK versions and they do not become
                                             available in an SDK version until the next SDK version is released.
                                             However, when generating an API history it is required that every API is in
                                             an API version, even those added as part of an SDK extension.

                                             If an SDK extension is being prepared for inclusion in an SDK version then
                                             this should be the SDK version. If an SDK extension is being prepared
                                             between SDK versions then this should be a magic version number that
                                             indicates that the newly added SDK extension APIs are not yet present in
                                             any SDK version.

                                             In the latter case the --api-version-label should be used when generating
                                             documentation from that `api-versions.xml` file to give the magic version a
                                             meaningful name in the documentation.
  --missing-class-action [keep|remove|report]
                                             Determines what to do when generating an XML API versions file and a class
                                             extends or implements a class that is not defined within any of the API
                                             versions loaded. Ignored when generating a JSON API versions file.

                                             keep - Keep references to missing classes.

                                             remove - Remove references to missing classes.

                                             report (default) - Report references to missing classes as errors.
  -h, -?, --help                             Show this message and exit

$CONFIG_FILE_OPTIONS_HELP
    """
        .trimIndent()

/** Tests for [ApiHistoryCommand]. */
class ApiHistoryCommandTest : BaseCommandTest<ApiHistoryCommand>({ ApiHistoryCommand() }) {

    private fun File.checkApiVersionsJsonContent(expectedContent: String) {
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val outputJson = gson.fromJson(readText(), JsonElement::class.java)
        assertEquals(expectedContent.trimIndent(), gson.toJson(outputJson))
    }

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("api-history", "--help")

            expectedStdout = apiHistoryHelp
        }
    }

    @Test
    fun `Test XML generation from historical and current signature files`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val v1 =
                signature(
                        "sdk/1/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void v1Method();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "sdk/2/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @Deprecated public void v1Method();
                                method public void v2Method();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            // Create an extra file on disk that matches --pattern but is NOT in --historical-files
            // to verify LimitedFileSystemProvider prevents undeclared file access.
            signature(
                    "sdk/3/public/api/android.txt",
                    """
                        // Signature format: 2.0
                        package java.lang {
                          public class Object {
                            ctor public Object();
                          }
                        }
                        package test.pkg {
                          public class Undeclared {
                            ctor public Undeclared();
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

            val currentBase =
                signature(
                        "current/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method @Deprecated public void v1Method();
                                method public void v2Method();
                                method public void currentMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val currentExtra =
                signature(
                        "current/extra.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Bar {
                                ctor public Bar();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/public/api/android.txt",
                    "--historical-files",
                    listOf(v1, v2).joinToString(File.pathSeparator) { it.path },
                    "--current-version",
                    "4",
                    "--current-files",
                    listOf(currentBase, currentExtra).joinToString(File.pathSeparator) { it.path },
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Bar" since="4">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        		<method name="currentMethod()V" since="4"/>
                        		<method name="v1Method()V" deprecated="2"/>
                        		<method name="v2Method()V" since="2"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test JSON generation from historical and current signature files`() {
        commandTest {
            args += "api-history"

            val outputJson = outputFile("apiLevels.json")
            args += "--output-file" to outputJson

            val v1 =
                signature(
                        "api/1.0.0.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method public void methodV1(String);
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "api/1.1.0.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @Deprecated public void methodV1(String);
                                method public void methodV2(int);
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val current =
                signature(
                        "api/current.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                                method @Deprecated public void methodV1(String);
                                method public void methodV2(int);
                                method public void methodCurrent();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/api/{version:major.minor.patch}.txt",
                    "--historical-files",
                    listOf(v1, v2).joinToString(File.pathSeparator) { it.path },
                    "--current-version",
                    "1.2.0-alpha01",
                    "--current-files",
                    current.path,
                )

            verify {
                outputJson.checkApiVersionsJsonContent(
                    """
                        [
                          {
                            "class": "test.pkg.Foo",
                            "addedIn": "1.0.0",
                            "methods": [
                              {
                                "method": "Foo()",
                                "addedIn": "1.0.0"
                              },
                              {
                                "method": "methodCurrent()",
                                "addedIn": "1.2.0-alpha01"
                              },
                              {
                                "method": "methodV1(java.lang.String)",
                                "addedIn": "1.0.0",
                                "deprecatedIn": "1.1.0"
                              },
                              {
                                "method": "methodV2(int)",
                                "addedIn": "1.1.0"
                              }
                            ],
                            "fields": []
                          }
                        ]
                    """
                )
            }
        }
    }

    @Test
    fun `Test JSON generation from current signature files only`() {
        commandTest {
            args += "api-history"

            val outputJson = outputFile("apiLevels.json")
            args += "--output-file" to outputJson

            val current =
                signature(
                        "api/current.txt",
                        """
                            // Signature format: 4.0
                            package test.pkg {
                              public final class Foo {
                                ctor public Foo();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--current-version",
                    "1.0.0-alpha01",
                    "--current-files",
                    current.path,
                )

            verify {
                outputJson.checkApiVersionsJsonContent(
                    """
                        [
                          {
                            "class": "test.pkg.Foo",
                            "addedIn": "1.0.0-alpha01",
                            "methods": [
                              {
                                "method": "Foo()",
                                "addedIn": "1.0.0-alpha01"
                              }
                            ],
                            "fields": []
                          }
                        ]
                    """
                )
            }
        }
    }

    @Test
    fun `Test --historical-versions filtering`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val v1 =
                signature(
                        "sdk/1/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V1Class {
                                ctor public V1Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v2 =
                signature(
                        "sdk/2/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V2Class {
                                ctor public V2Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v3 =
                signature(
                        "sdk/3/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class V2Class {
                                ctor public V2Class();
                              }
                              public class V3Class {
                                ctor public V3Class();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    listOf(v1, v2, v3).joinToString(File.pathSeparator) { it.path },
                    "--historical-versions",
                    "2:2",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3" min="2">
                        	<class name="java/lang/Object" since="2">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/V2Class" since="2">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test --missing-class-action remove and keep`() {
        val sig =
            signature(
                    "sdk/1/android.txt",
                    """
                        // Signature format: 2.0
                        package java.lang {
                          public class Object {
                            ctor public Object();
                          }
                        }
                        package test.pkg {
                          public class Foo implements test.pkg.MissingInterface {
                            ctor public Foo();
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

        commandTest {
            val outputXml = outputFile("remove-api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--missing-class-action",
                    "remove",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }

        commandTest {
            val outputXml = outputFile("keep-api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--missing-class-action",
                    "keep",
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<implements name="test/pkg/MissingInterface"/>
                        		<method name="&lt;init>()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test error cases`() {
        val sig =
            signature(
                    "sdk/1/android.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                          }
                        }
                    """,
                )
                .createFile(temporaryFolder.root)

        // Invalid output extension
        commandTest {
            val outputTxt = outputFile("api-versions.txt")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputTxt.path,
                    "--current-version",
                    "1",
                    "--current-files",
                    sig.path,
                )

            expectedStderr =
                "Aborting: --output-file (TESTROOT/api-versions.txt) has unexpected extension 'txt', must be 'xml' or 'json'"
        }

        // Neither --historical-files nor --current-files
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                )

            expectedStderr =
                "Aborting: Must specify at least one of --historical-files or --current-files"
        }

        // --historical-files without --pattern
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--historical-files",
                    sig.path,
                )

            expectedStderr = "Aborting: Must specify --pattern with --historical-files"
        }

        // --current-version without --current-files
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/android.txt",
                    "--historical-files",
                    sig.path,
                    "--current-version",
                    "2",
                )

            expectedStderr = "Aborting: Must specify --current-files with --current-version"
        }

        // --current-files without --current-version
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--current-files",
                    sig.path,
                )

            expectedStderr = "Aborting: Must specify --current-version with --current-files"
        }

        // --api-surface without --config-file
        commandTest {
            val outputXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--output-file",
                    outputXml.path,
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/{surface}/android.txt",
                    "--historical-files",
                    sig.path,
                    ARG_API_SURFACE,
                    "system",
                )

            expectedStderr =
                "Aborting: --api-surface requires at least one <api-surface> to have been configured in a --config-file"
        }
    }

    @Test
    fun `Test --config-file and --api-surface`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val configFile =
                KnownConfigFiles.configKnownTestSurfaces.createFile(temporaryFolder.root)

            val v1Public =
                signature(
                        "sdk/1/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void publicMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val v1System =
                signature(
                        "sdk/1/system/api/android.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void systemMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            // module-lib should not be included when --api-surface is "system".
            val v1ModuleLib =
                signature(
                        "sdk/1/module-lib/api/android.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void moduleLibMethod();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "system",
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/{surface}/api/android.txt",
                    "--historical-files",
                    listOf(v1Public, v1System, v1ModuleLib).joinToString(File.pathSeparator) {
                        it.path
                    },
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3">
                        	<class name="java/lang/Object" since="1">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/Foo" since="1">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        		<method name="publicMethod()V"/>
                        		<method name="systemMethod()V"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test SDK extensions`() {
        commandTest {
            args += "api-history"

            val outputXml = outputFile("api-versions.xml")
            args += "--output-file" to outputXml

            val sdkInfoXml =
                xml(
                        "sdk-extensions-info.xml",
                        """
                            <?xml version="1.0" encoding="utf-8"?>
                            <sdk-extensions-info>
                              <sdk
                                id="30"
                                shortname="R-ext"
                                name="R Extensions"
                                reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                              <symbol
                                jar="framework-ext"
                                pattern="*"
                                sdks="R-ext" />
                            </sdk-extensions-info>
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val sdk30 =
                signature(
                        "sdk/30/public/api/android.txt",
                        """
                            // Signature format: 2.0
                            package java.lang {
                              public class Object {
                                ctor public Object();
                              }
                            }
                            package test.pkg {
                              public class ExtClass {
                                ctor public ExtClass();
                                method public void methodIn30();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val ext1 =
                signature(
                        "sdk/extensions/1/public/api/framework-ext.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class ExtClass {
                                ctor public ExtClass();
                                method public void methodIn30();
                                method public void methodInExt1();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            val ext2 =
                signature(
                        "sdk/extensions/2/public/api/framework-ext.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class ExtClass {
                                ctor public ExtClass();
                                method public void methodIn30();
                                method public void methodInExt1();
                                method public void methodInExt2();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            // Extension 3 should be excluded by --sdk-extension-versions 1:2.
            val ext3 =
                signature(
                        "sdk/extensions/3/public/api/framework-ext.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class ExtClass {
                                ctor public ExtClass();
                                method public void methodIn30();
                                method public void methodInExt1();
                                method public void methodInExt2();
                                method public void methodInExt3();
                              }
                            }
                        """,
                    )
                    .createFile(temporaryFolder.root)

            args +=
                listOf(
                    ARG_SDK_INFO_FILE,
                    sdkInfoXml.path,
                    "--sdk-extension-versions",
                    "1:2",
                    ARG_API_VERSION_FOR_SDK_EXTENSION,
                    "10000",
                    "--pattern",
                    "${temporaryFolder.root}/sdk/{version:level}/public/api/android.txt",
                    "--pattern",
                    "${temporaryFolder.root}/sdk/extensions/{version:extension}/public/api/{module}.txt",
                    "--historical-files",
                    listOf(sdk30, ext1, ext2, ext3).joinToString(File.pathSeparator) { it.path },
                )

            verify {
                assertEquals(
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <api version="3" min="30">
                        	<sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                        	<class name="java/lang/Object" since="30">
                        		<method name="&lt;init>()V"/>
                        	</class>
                        	<class name="test/pkg/ExtClass" module="framework-ext" since="30" sdks="30:1,0:30">
                        		<extends name="java/lang/Object"/>
                        		<method name="&lt;init>()V"/>
                        		<method name="methodIn30()V"/>
                        		<method name="methodInExt1()V" since="10000" sdks="30:1"/>
                        		<method name="methodInExt2()V" since="10000" sdks="30:2"/>
                        	</class>
                        </api>
                    """
                        .trimIndent(),
                    outputXml.readText().trim(),
                )
            }
        }
    }

    /** Create a simple `sdk-extension-info.xml` for testing. */
    private fun createSdkExtensionsInfoXml() =
        temporaryFolder.newFile("sdk-extensions-info.xml").apply {
            writeText(
                """
                        <?xml version="1.0" encoding="utf-8"?>
                        <sdk-extensions-info>
                        <sdk id="7"
                             shortname="seven"
                             name="Seven Extensions"
                             reference="Seven" />
                        <symbol jar="bar" pattern="foo" sdks="seven" />
                        <symbol jar="baz" pattern="foo" sdks="seven" />
                        <symbol jar="foo" pattern="foo" sdks="seven" />
                        </sdk-extensions-info>
                    """
                    .trimIndent()
            )
        }

    /** Find all `.txt` files under [dir] and join their paths with [File.pathSeparator]. */
    private fun findTxtFiles(dir: File): String =
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .joinToString(File.pathSeparator) { it.path }

    /** Dump the contents of this list to a string. */
    private fun List<VersionedApi>.dump() = removeTestSpecificDirectories(joinToString("\n"))

    @Test
    fun `Test signature files in config`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("1.1") {
                dir("public") { emptyFile("api.txt") }
                dir("system") { emptyFile("api.txt") }
            }
            dir("2") {
                dir("public") { emptyFile("api.txt") }
                dir("system") { emptyFile("api.txt") }
            }
            dir("extensions") {
                dir("1") {
                    dir("public") {
                        emptyFile("foo.txt")
                        emptyFile("foo-removed.txt")
                        emptyFile("bar.txt")
                    }
                    dir("system") { emptyFile("foo.txt") }
                }
                dir("2") {
                    dir("public") {
                        emptyFile("foo.txt")
                        emptyFile("foo-incompatibilities.txt")
                        emptyFile("bar.txt")
                        emptyFile("baz.txt")
                    }
                    dir("system") { emptyFile("foo.txt") }
                }
            }
        }

        val configFile =
            KnownConfigFiles.configPublicAndSystemSurfaces.createFile(temporaryFolder.root)
        val currentApi = temporaryFolder.newFile("current.txt")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()

        commandTest {
            val apiVersionsXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "system",
                    "--current-version",
                    "30",
                    "--current-files",
                    currentApi.path,
                    "--output-file",
                    apiVersionsXml.path,
                    "--pattern",
                    "!**/*-incompatibilities.txt",
                    "--pattern",
                    "!**/*-removed.txt",
                    "--pattern",
                    "$root/{version:major.minor?}/{surface}/api.txt",
                    "--pattern",
                    "$root/extensions/{version:extension}/{surface}/{module}.txt",
                    "--historical-files",
                    findTxtFiles(root),
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfoXml.path,
                )

            verify {
                assertEquals(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSignatureApi(files=TESTROOT/1.1/{public,system}/api.txt, updater=ApiVersionUpdater(version=1.1))
                        VersionedSignatureApi(files=TESTROOT/2/{public,system}/api.txt, updater=ApiVersionUpdater(version=2))
                        VersionedSignatureApi(files=TESTROOT/current.txt, updater=ApiVersionUpdater(version=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/bar.txt, updater=ExtensionUpdater(extVersion=1, module=bar, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/bar.txt, updater=ExtensionUpdater(extVersion=2, module=bar, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/baz.txt, updater=ExtensionUpdater(extVersion=2, module=baz, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/1/{public,system}/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/{public,system}/foo.txt, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=30))
                    """
                        .trimIndent(),
                    command.config.versionedApis.dump(),
                )
            }
        }
    }

    @Test
    fun `Test invalid --historical-versions`() {
        val root = temporaryFolder.root
        val currentApi = temporaryFolder.newFile("current.txt")

        commandTest {
            val apiVersionsXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    "--historical-versions",
                    "9",
                    "--current-version",
                    "30",
                    "--current-files",
                    currentApi.path,
                    "--output-file",
                    apiVersionsXml.path,
                    "--pattern",
                    "$root/{version:major.minor?}/{surface}/api.txt",
                )

            expectedStderr =
                """
                    Aborting: Usage: metalava api-history [options]

                    Error: Invalid value for "--historical-versions": Must be of the form <version>:<version> but found '9'
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test --historical-versions in config`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("1.1") { dir("public") { emptyFile("api.txt") } }
            // This should be ignored by --historical-versions.
            dir("2") { dir("public") { emptyFile("api.txt") } }
        }

        val configFile = KnownConfigFiles.configPublicSurface.createFile(temporaryFolder.root)
        val currentApi = temporaryFolder.newFile("current.txt")

        commandTest {
            val apiVersionsXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "public",
                    "--historical-versions",
                    "1:1.1",
                    "--current-version",
                    "30",
                    "--current-files",
                    currentApi.path,
                    "--output-file",
                    apiVersionsXml.path,
                    "--pattern",
                    "$root/{version:major.minor?}/{surface}/api.txt",
                    "--historical-files",
                    findTxtFiles(root),
                )

            verify {
                assertEquals(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSignatureApi(files=TESTROOT/1.1/public/api.txt, updater=ApiVersionUpdater(version=1.1))
                        VersionedSignatureApi(files=TESTROOT/current.txt, updater=ApiVersionUpdater(version=30))
                    """
                        .trimIndent(),
                    command.config.versionedApis.dump(),
                )
            }
        }
    }

    @Test
    fun `Test --sdk-extension-versions in config`() {
        val root = buildFileStructure {
            dir("28") { dir("public") { emptyFile("api.txt") } }
            dir("29") { dir("public") { emptyFile("api.txt") } }

            dir("extensions") {
                dir("1") { dir("public") { emptyFile("foo.txt") } }
                dir("2") { dir("public") { emptyFile("foo.txt") } }
                dir("3") { dir("public") { emptyFile("foo.txt") } }
            }
        }

        val configFile = KnownConfigFiles.configPublicSurface.createFile(temporaryFolder.root)
        val currentApi = temporaryFolder.newFile("current.txt")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()

        commandTest {
            val apiVersionsXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "public",
                    "--sdk-extension-versions",
                    "1:2",
                    "--current-version",
                    "30",
                    "--current-files",
                    currentApi.path,
                    "--output-file",
                    apiVersionsXml.path,
                    "--pattern",
                    "$root/{version:major.minor?}/{surface}/api.txt",
                    "--pattern",
                    "$root/extensions/{version:extension}/{surface}/{module}.txt",
                    "--historical-files",
                    findTxtFiles(root),
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfoXml.path,
                )

            verify {
                assertEquals(
                    """
                        VersionedSignatureApi(files=TESTROOT/28/public/api.txt, updater=ApiVersionUpdater(version=28))
                        VersionedSignatureApi(files=TESTROOT/29/public/api.txt, updater=ApiVersionUpdater(version=29))
                        VersionedSignatureApi(files=TESTROOT/current.txt, updater=ApiVersionUpdater(version=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=30))
                        VersionedSignatureApi(files=TESTROOT/extensions/2/public/foo.txt, updater=ExtensionUpdater(extVersion=2, module=foo, nextSdkVersion=30))
                    """
                        .trimIndent(),
                    command.config.versionedApis.dump(),
                )
            }
        }
    }

    @Test
    fun `Test nextSdkVersion in config`() {
        val root = buildFileStructure {
            dir("1") { dir("public") { emptyFile("api.txt") } }
            dir("extensions") { dir("1") { dir("public") { emptyFile("foo.txt") } } }
        }

        val configFile = KnownConfigFiles.configPublicSurface.createFile(temporaryFolder.root)
        val currentApi = temporaryFolder.newFile("current.txt")
        val sdkExtensionsInfoXml = createSdkExtensionsInfoXml()

        commandTest {
            val apiVersionsXml = outputFile("api-versions.xml")
            args +=
                listOf(
                    "api-history",
                    ARG_CONFIG_FILE,
                    configFile.path,
                    ARG_API_SURFACE,
                    "public",
                    ARG_API_VERSION_FOR_SDK_EXTENSION,
                    "123456789",
                    "--current-version",
                    "30",
                    "--current-files",
                    currentApi.path,
                    "--output-file",
                    apiVersionsXml.path,
                    "--pattern",
                    "$root/{version:major.minor?}/{surface}/api.txt",
                    "--pattern",
                    "$root/extensions/{version:extension}/{surface}/{module}.txt",
                    "--historical-files",
                    findTxtFiles(root),
                    ARG_SDK_INFO_FILE,
                    sdkExtensionsInfoXml.path,
                )

            verify {
                assertEquals(
                    """
                        VersionedSignatureApi(files=TESTROOT/1/public/api.txt, updater=ApiVersionUpdater(version=1))
                        VersionedSignatureApi(files=TESTROOT/current.txt, updater=ApiVersionUpdater(version=30))
                        VersionedSignatureApi(files=TESTROOT/current.txt, updater=ApiVersionUpdater(version=123456789))
                        VersionedSignatureApi(files=TESTROOT/extensions/1/public/foo.txt, updater=ExtensionUpdater(extVersion=1, module=foo, nextSdkVersion=123456789))
                    """
                        .trimIndent(),
                    command.config.versionedApis.dump(),
                )
            }
        }
    }
}
