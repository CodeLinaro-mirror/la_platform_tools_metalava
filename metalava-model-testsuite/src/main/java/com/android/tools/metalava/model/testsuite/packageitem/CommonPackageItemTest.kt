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

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles.nonNullSource
import com.android.tools.metalava.testing.html
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

class CommonPackageItemTest : BaseModelTest() {

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
                        .trimIndent()
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

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
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
            ),
        ) {
            val packageItem = codebase.assertPackage("test.pkg")
            assertEquals(true, packageItem.originallyHidden)
        }
    }

    @Test
    fun `Test @hide in package info processed last`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
                java(
                    """
                        /**
                         * @hide
                         */
                        package test.pkg;
                    """
                        .trimIndent()
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
                        .trimIndent()
                ),
                java(
                    """
                        package test.pkg;

                        public class Foo {}
                    """
                        .trimIndent()
                ),
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

            assertEquals("TESTROOT/api.txt", removeTestSpecificDirectories(packageLocation))
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
                "TESTROOT/src/test/pkg/package-info.java",
                removeTestSpecificDirectories(packageLocation)
            )
        }
    }

    @Test
    fun `Test package documentation (package-info)`() {
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

            assertEquals(
                "/** Some text. */",
                packageItem.documentation.text.trim(),
            )
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
                "TESTROOT/src/test/pkg/package.html",
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

            assertEquals(
                """
                    /**
                     * Some text.
                     */
                """
                    .trimIndent(),
                packageItem.documentation.text.trim(),
            )
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
                packageItem.overviewDocumentation?.trim(),
            )
        }
    }
}
