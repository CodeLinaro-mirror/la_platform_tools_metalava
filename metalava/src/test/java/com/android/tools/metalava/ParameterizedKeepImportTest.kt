/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class ParameterizedKeepImportTest : DriverTest() {

    @Parameterized.Parameter(0) lateinit var testData: TestData

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestData] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testData.entryPointCallerTracker }

    data class TestData
    @EntryPoint
    constructor(
        val name: String,
        val classDoc: String = "Class",
        val expectedClassDoc: String = classDoc,
        val methodDoc: String = "Method",
        val expectedMethodDoc: String = methodDoc,
        val shouldKeepImportedName: Boolean,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = name
    }

    companion object {
        private val params =
            listOf(
                TestData(
                    name = "not using the imported name",
                    classDoc = "Not using the imported name in plain text",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in plain text",
                    classDoc = "Using ImportedName in plain text",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @see reference",
                    classDoc = "@see ImportedName",
                    expectedClassDoc = "@see other.pkg.ImportedName",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @see label",
                    classDoc = """@see "Something" use ImportedName in see description""",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @param name",
                    methodDoc = "@param ImportedName",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @param description",
                    methodDoc = "@param p use ImportedName in param description",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @throws reference",
                    methodDoc = "@throws ImportedName",
                    expectedMethodDoc = "@throws other.pkg.ImportedName",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @throws description",
                    methodDoc = "@throws RuntimeException use ImportedName in throws description",
                    expectedMethodDoc =
                        "@throws java.lang.RuntimeException use ImportedName in throws description",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @link reference",
                    classDoc = "{@link ImportedName}",
                    expectedClassDoc = "{@link other.pkg.ImportedName ImportedName}",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @link label",
                    classDoc = "{@link java.util.List use ImportedName in link label}",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @link field reference",
                    classDoc = "{@link ImportedName#FIELD}",
                    expectedClassDoc = "{@link other.pkg.ImportedName#FIELD ImportedName.FIELD}",
                    shouldKeepImportedName = false,
                ),
                TestData(
                    name = "in @link method reference",
                    classDoc = "{@link ImportedName#method()}",
                    expectedClassDoc =
                        "{@link other.pkg.ImportedName#method() ImportedName.method()}",
                    shouldKeepImportedName = false,
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @Test
    fun `Test import kept`() {
        val expectedStubsSource =
            """
                package test.pkg;
                import other.pkg.ImportedName;
                /** ${testData.expectedClassDoc} */
                @SuppressWarnings({"unchecked", "deprecation", "all"})
                public class Test {
                Test() { throw new RuntimeException("Stub!"); }
                /** ${testData.expectedMethodDoc} */
                public fun method() { throw new RuntimeException("Stub!"); }
                }
            """
                .trimIndent()
                .let { source ->
                    if (testData.shouldKeepImportedName) source
                    else source.replace("import other.pkg.ImportedName;\n", "")
                }

        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import other.pkg.ImportedName;
                            /** ${testData.classDoc} */
                            public class Test {
                                private Test() {}
                                /** ${testData.methodDoc} */
                                public fun method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package other.pkg;
                            public class ImportedName {
                                private ImportedName() {}
                                public static final String FIELD = "field";
                                public void method() {}
                            }
                        """
                    ),
                ),
            stubFiles =
                arrayOf(
                    java(expectedStubsSource),
                )
        )
    }
}
