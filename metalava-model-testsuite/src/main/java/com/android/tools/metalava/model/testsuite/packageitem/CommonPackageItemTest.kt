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

package com.android.tools.metalava.model.testsuite.packageitem

import com.android.tools.lint.checks.infrastructure.TestFiles.source
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles.nonNullSource
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.html
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.ClassRule
import org.junit.Test

class CommonPackageItemTest : BaseModelTest() {
    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private val otherJarFile =
            jarFromSources(
                    "other-package.jar",
                    java(
                        """
                            @PkgAnno
                            package other.pkg;
                        """
                    ),
                    java(
                        """
                            package other.pkg;
                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;
                            /** Annotation comment. */
                            @Retention(RetentionPolicy.RUNTIME)
                            public @interface PkgAnno {
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)
    }

    @RequiresCapabilities(Capability.PACKAGE_HTML_FILES)
    @Test
    fun `Test @hide in package html`() {
        runSourceCodebaseTest(
            inputSet(
                html(
                    "src/test/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        @hide
                        </BODY>
                        </HTML>
                    """
                        .trimIndent(),
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @RequiresCapabilities(Capability.HIDDEN_ITEMS)
    @Test
    fun `Test @hide in package info processed first`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * @hide
                         */
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @RequiresCapabilities(Capability.HIDDEN_ITEMS)
    @Test
    fun `Test @hide in package info processed last`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                ),
                java(
                    """
                        /**
                         * @hide
                         */
                        package test.pkg;
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @Test
    fun `Test nullability annotation in package info`() {
        runSourceCodebaseTest(
            inputSet(
                nonNullSource,
                java(
                    """
                        @android.annotation.NonNull
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    // Use the noOpAnnotationManager to avoid annotation name normalizing as the
                    // annotation names are important for this test.
                    annotationManager = noOpAnnotationManager,
                ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(
                "@android.annotation.NonNull",
                packageItem.modifiers.annotations().single().toString()
            )
        }
    }

    private fun dumpPackageContainment(start: Item): String {
        return buildString {
            val packageContainment = generateSequence(start) { it.containingPackage() }
            for (item in packageContainment) {
                if (isNotEmpty()) append("-> ")
                append(item.describe())
                append("\n")
            }
        }
    }

    @Test
    fun `Test package containment`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package java.lang.invoke.mine {
                        public class Foo {
                        }
                    }
                """
            ),
            java(
                """
                    package java.lang.invoke.mine;

                    public class Foo {
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("java.lang.invoke.mine.Foo")

            assertEquals(
                """
                    class java.lang.invoke.mine.Foo
                    -> package java.lang.invoke.mine
                    -> package java.lang.invoke
                    -> package java.lang
                    -> package java
                    -> package <root>
                """
                    .trimIndent(),
                dumpPackageContainment(classItem).trim()
            )
        }
    }

    @Test
    fun `Test package location (signature)`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                        }
                    }
                """
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val packageLocation = packageItem.fileLocation.path.toString()

            assertEquals("MAIN_SRC/api.txt", removeTestSpecificDirectories(packageLocation))
        }
    }

    @Test
    fun `Test package location (package-info)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                java(
                    """
                        /** Some text. */
                        package test.pkg;
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val packageLocation = packageItem.fileLocation.path.toString()

            assertEquals(
                "MAIN_SRC/src/test/pkg/package-info.java",
                removeTestSpecificDirectories(packageLocation)
            )
        }
    }

    @Test
    fun `Test package documentation (package-info) without header comment`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                java(
                    """
                        /** Some text. */
                        package test.pkg;
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            packageItem.assertDocumentationText("/** Some text. */")
        }
    }

    @Test
    fun `Test package documentation (package-info) with header comment`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                java(
                    """
                        /* Header comment */

                        /** Package comment. */
                        package test.pkg;
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            packageItem.assertDocumentationText("/** Package comment. */")
        }
    }

    @Test
    fun `Test package location (package-html)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                html(
                    "src/test/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        Some text.
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            val packageLocation = packageItem.fileLocation.path.toString()

            assertEquals(
                "MAIN_SRC/src/test/pkg/package.html",
                removeTestSpecificDirectories(packageLocation)
            )
        }
    }

    @Test
    fun `Test package documentation (package-html)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                html(
                    "src/test/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        Some text.
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")

            packageItem.assertDocumentationText(expectedOutput = "/** Some text. */")
        }
    }

    @Test
    fun `Test invalid package (package-html)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                html(
                    "src/other/pkg/package.html",
                    """
                        <HTML>
                        <BODY>
                        Some text.
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            val packageItem = codebase.findPackage("other.pkg")
            assertNull(packageItem)
        }
    }

    @Test
    fun `Test package documentation (overview-html)`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
                html(
                    "src/test/pkg/overview.html",
                    """
                        <HTML>
                        <BODY>
                        Overview.
                        </BODY>
                        </HTML>
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")

            assertEquals(
                """
                    <HTML>
                    <BODY>
                    Overview.
                    </BODY>
                    </HTML>
                """
                    .trimIndent(),
                packageItem.overviewDocumentation?.content?.trim(),
            )
        }
    }

    @Test
    fun `Test mismatching between package and directory`() {
        runCodebaseTest(
            java(
                "src/test/other/Foo.java",
                """
                    package test.pkg;

                    public class Foo {
                    }
                """
            ),
        ) {
            codebase.assertClass("test.pkg.Foo")
            // Make sure that if any errors are reported that they are included in this list of
            // known errors. This is needed because K1 produces both errors, but K2 only produces
            // the first error. This test is currently broken for Turbine.
            assertContains(
                """
                    MAIN_SRC/src/test/other/Foo.java: error: Unable to determine the package name. This usually means that a source file was where the directory does not seem to match the package declaration; we expected the path MAIN_SRC/src/test/other/Foo.java to end with /test/pkg/Foo.java [IoError]
                    MAIN_SRC/src/test/other/Foo.java:3: error: Could not find package test.pkg for class test.pkg.Foo. This is most likely due to a mismatch between the package statement and the directory MAIN_SRC/src/test/other [InvalidPackage]
                """
                    .trimIndent(),
                removeReportedIssues()
            )
        }
    }

    @Test
    fun `Test documentation on empty packages`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * Some documentation.
                         */
                        package test;
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {
                        }
                    """
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test")
            packageItem.assertDocumentationText(expectedOutput = "/** Some documentation. */")
        }
    }

    @Test
    fun `Test resolving package from jar`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Foo {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(otherJarFile.createFile(temporaryFolder.root))
                ),
        ) {
            val packageItem = codebase.assertResolvedPackage("other.pkg")

            assertEquals(
                "ModifierList(flags = [public], annotations = [@other.pkg.PkgAnno])",
                packageItem.modifiers.toString()
            )
        }
    }

    @RequiresCapabilities(Capability.PACKAGE_HTML_FILES)
    @Test
    fun `Test conflicting comments in package-info java and package html`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        /**
                         * A package comment.
                         */
                        package test.pkg;
                    """
                ),
                source(
                        "src/test/pkg/package.html",
                        """
                        <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
                        <html>
                        <body bgcolor="white">
                        An HTML package comment
                        </BODY>
                        </html>
                    """
                    )
                    .indented(),
            ),
        ) {
            val testPackage = codebase.assertPackage("test.pkg")

            testPackage.assertPrintedDocumentation(expectedOutput = "/** A package comment. */\n")

            assertAndRemoveReportedIssues(
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/package-info.java: warning: It is illegal to provide both a package-info.java file and a package.html file for the same package [BothPackageInfoAndHtml]
                    """
            )
        }
    }
}
