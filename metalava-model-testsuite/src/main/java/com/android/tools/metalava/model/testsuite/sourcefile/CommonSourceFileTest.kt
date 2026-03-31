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

package com.android.tools.metalava.model.testsuite.sourcefile

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

/** Common tests for implementations of [SourceFile]. */
class CommonSourceFileTest : BaseModelTest() {
    internal class FilterHidden : FilterPredicate {
        override fun test(item: SelectableItem): Boolean = !item.isHiddenOrRemoved()
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test location of class file - java`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(
                "MAIN_SRC/src/test/pkg/Test.java",
                removeTestSpecificDirectories(sourceFile.fileLocation.toString())
            )
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test location of class file - kotlin`() {
        runSourceCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(
                "MAIN_SRC/src/test/pkg/Test.kt",
                removeTestSpecificDirectories(sourceFile.fileLocation.toString())
            )
        }
    }

    @Test
    fun `Test header comments`() {
        runSourceCodebaseTest(
            java(
                """
                    /*
                     * Copyright comment.
                     */

                    // Inline comment before package

                    package test.pkg;

                    // Inline comment before class

                    /*
                     * Multi-line comment
                     * before class.
                     */

                    /**
                     * Main class comment.
                     */
                    public class Test {}

                    // Inline comment after class

                    /*
                     * Multi-line comment
                     * after class.
                     */
                """
            ),
            kotlin(
                """
                    /*
                     * Copyright comment.
                     */

                    // Inline comment before package

                    package test.pkg

                    // Inline comment before class

                    /*
                     * Multi-line comment
                     * before class.
                     */

                    /**
                     * Main class comment.
                     */
                    class Test {}

                    // Inline comment after class

                    /*
                     * Multi-line comment
                     * after class.
                     */
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(
                """
                    /*
                     * Copyright comment.
                     */

                    // Inline comment before package
                """
                    .trimIndent(),
                sourceFile.getHeaderComments()?.trimEnd()
            )
        }
    }

    /**
     * Create a [TestFile] with a relative [path] and [text] contents.
     *
     * [text] is trimmed and then any LF characters are replaced with CR and LF characters. This is
     * necessary as [java] and [kotlin] will trim the string and replace CR and LF with just LF.
     */
    private fun dosFile(path: String, text: String) =
        TestFiles.file().to(path).withSource(text.trimIndent().replace("\n", "\r\n"))

    @Test
    fun `Test dos end-of-line in header comments`() {
        runSourceCodebaseTest(
            dosFile(
                "src/test/pkg/Test.java",
                """
                    /*
                     * Copyright comment.
                     */

                    package test.pkg;

                    public class Test {}
                """
            ),
            dosFile(
                "src/test/pkg/Test.kt",
                """
                    /*
                     * Copyright comment.
                     */

                    package test.pkg

                    class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(
                """
                    /*
                     * Copyright comment.
                     */
                """
                    .trimIndent(),
                sourceFile.getHeaderComments()?.trimEnd()
            )
        }
    }

    @Test
    fun `test sourcefile classes`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}

                    public class Outer {
                        class Inner {}
                    }
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val outerClassItem = codebase.assertClass("test.pkg.Outer")
            val sourceFile = classItem.sourceFile()!!

            assertEquals(listOf(classItem, outerClassItem), sourceFile.classes().toList())
        }
    }

    @Test
    fun `Test codebase and containingPackage`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Test {}
                """
            ),
        ) {
            val classItem = codebase.assertClass("test.pkg.Test")
            val sourceFile = classItem.sourceFile()!!

            assertSame(classItem.codebase, sourceFile.codebase, message = "codebase")
            assertSame(
                classItem.containingPackage(),
                sourceFile.containingPackage,
                message = "containingPackage"
            )
        }
    }
}
