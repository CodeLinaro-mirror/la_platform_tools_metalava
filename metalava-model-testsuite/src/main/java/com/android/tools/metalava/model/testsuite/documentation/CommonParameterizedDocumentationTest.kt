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

package com.android.tools.metalava.model.testsuite.documentation

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import java.util.EnumSet
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/** Common tests for implementations of [ItemDocumentation] */
class CommonParameterizedDocumentationTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        val name: String,
        val inputFormats: Set<InputFormat> = EnumSet.allOf(InputFormat::class.java),
        val imports: List<String> = emptyList(),
        val comment: String,
        val expectedText: String = comment,
        val expectedFullyQualified: String = expectedText,
    ) {
        fun skipForInputFormat(inputFormat: InputFormat?) = inputFormat !in inputFormats

        override fun toString(): String {
            return name
        }
    }

    companion object {
        private val javaOnly = EnumSet.of(InputFormat.JAVA)
        private val kotlinOnly = EnumSet.of(InputFormat.KOTLIN)

        private val params =
            listOf(
                TestParams(
                    name = "inline comment",
                    comment = "// inline comment",
                    expectedText = "",
                ),
                TestParams(
                    name = "inline comment - link tag",
                    comment = "// inline comment - {@link}",
                    expectedText = "",
                ),
                TestParams(
                    name = "block comment",
                    comment = "/* block comment */",
                    expectedText = "",
                ),
                TestParams(
                    name = "block comment - link tag",
                    comment = "/* block comment - {@link} */",
                    expectedText = "",
                ),
                TestParams(
                    name = "doc comment - plain text",
                    comment = "/** doc comment */",
                ),
                TestParams(
                    name = "doc comment with link - java",
                    inputFormats = javaOnly,
                    imports = listOf("java.util.List"),
                    comment = "/** {@link List} */",
                    expectedFullyQualified = "/** {@link java.util.List List} */",
                ),
                TestParams(
                    name = "doc comment with link - kotlin",
                    inputFormats = kotlinOnly,
                    imports = listOf("kotlin.random.Random"),
                    comment = "/** {@link Random} */",
                    // Doc comments in Kotlin are not fully qualified as that is only needed for
                    // java stubs due to an issue with doclava. Kotlin stubs are not supported.
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    /**
     * [TestRule] that ignores tests whose [TestParams] are not suitable for the current
     * [inputFormat].
     */
    @get:Rule
    val filter =
        object : TestRule {
            override fun apply(base: Statement, description: Description): Statement {
                return object : Statement() {
                    override fun evaluate() {
                        if (params.skipForInputFormat(inputFormat)) return
                        base.evaluate()
                    }
                }
            }
        }

    private fun imports(): String =
        if (params.imports.isEmpty()) ""
        else {
            val terminator = if (inputFormat == InputFormat.JAVA) ";" else "\n"
            params.imports.joinToString { "                    import $it$terminator" }
        }

    @Test
    fun `Documentation text`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    ${imports()}
                    ${params.comment}
                    public class Test {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    ${imports()}

                    ${params.comment}
                    class Test
                """
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.documentation

            assertEquals(params.expectedText, documentation.text)
        }
    }

    @Test
    fun `Documentation fully qualified`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;
                    ${imports()}

                    ${params.comment}
                    public class Test {
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    ${imports()}

                    ${params.comment}
                    class Test
                """
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val documentation = testClass.documentation

            assertEquals(params.expectedFullyQualified, documentation.fullyQualifiedDocumentation())
        }
    }
}
