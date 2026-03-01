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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedInvalidTypeTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,

        /** The type being tested. */
        val testType: TestType = otherNestedTestType,

        /**
         * The field in the BinaryTest class to use in the [`Test invalid binary reference`] test.
         */
        val binaryTestField: String = "otherNested",

        /** The classpath to use in the test. */
        val classpath: List<TestFile>,

        /** The expected type when source and binary match. */
        val expectedType: TypeItem? = null,

        /** The expected type when processing a type in the binary class. */
        val expectedBinaryType: TypeItem? = expectedType,

        /** The expected type when processing a type in the source class. */
        val expectedSourceType: TypeItem? = expectedType,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() = "${testType.name} - $name"
    }

    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /** Jar file containing an `other.pkg.Other` class with a `Nested` class. */
        private val otherWithNestedJar =
            jarFromSources(
                    "other-with-nested.jar",
                    java(
                        """
                            package other.pkg;
                            public class Other {
                                private Other() {}
                                public static class Nested {
                                    private Nested() {}
                                }
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        /** Jar file containing an `other.pkg.Other` class but without a `Nested` class. */
        private val otherWithoutNestedJar =
            jarFromSources(
                    "other-without-nested.jar",
                    java(
                        """
                            package other.pkg;
                            public class Other {
                                private Other() {}
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        /** Jar file containing a `generic.pkg.Generic` class with an `Inner` class. */
        private val genericWithInnerJar =
            jarFromSources(
                    "generic-with-inner.jar",
                    java(
                        """
                            package generic.pkg;
                            public class Generic<O> {
                                private Generic() {}
                                public class Inner<I> {
                                    private Inner() {}
                                }
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        /** Jar file containing a `generic.pkg.Generic` class without an `Inner` class. */
        private val genericWithoutInnerJar =
            jarFromSources(
                    "generic-without-inner.jar",
                    java(
                        """
                            package generic.pkg;
                            public class Generic<O> {
                                private Generic() {}
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        /** Jar file containing an `other.pkg.Another` class. */
        private val anotherClassJar =
            jarFromSources(
                    "another.jar",
                    java(
                        """
                            package other.pkg;
                            public class Another {
                                private Another() {}
                            }
                        """
                    )
                )
                .cacheIn(testFileCacheRule)

        /** Encapsulates information about the type being tested. */
        data class TestType(
            val name: String,

            /** The reference to use in [javaTestFile]. */
            val javaReference: String = "Other.Nested",

            /** The import to use in [javaTestFile]. */
            val javaImport: String = "other.pkg.Other",

            /** The classPath against which [javaTestFile] will be compiled. */
            val classPath: List<TestFile>,
        ) {
            /** The java [TestFile] that encapsulates the type being tested. */
            val javaTestFile =
                java(
                        "$name/test/pkg/Test.java",
                        """
                        package test.pkg;
                        import $javaImport;
                        public class Test {
                            private Test() {}
                            public $javaReference field;
                        }
                    """
                    )
                    .cacheIn(testFileCacheRule)

            /** Jar file containing a [javaTestFile] class compiled against [classPath]. */
            val binaryTestJar =
                jarFromSources(
                        "$name.binary-test.jar",
                        javaTestFile,
                        classPath = classPath,
                    )
                    .cacheIn(testFileCacheRule)
        }

        /** Test the `Other.Nested` type. */
        val otherNestedTestType =
            TestType(
                name = "otherNested",
                javaReference = "Other.Nested",
                javaImport = "other.pkg.Other",
                classPath = listOf(otherWithNestedJar),
            )

        /** Test the `Generic<String>` type. */
        val genericString =
            TestType(
                name = "genericString",
                javaReference = "Generic<String>",
                javaImport = "generic.pkg.Generic",
                classPath = listOf(genericWithInnerJar),
            )

        /** Test the `Generic<Number>.Inner<String>` type. */
        val genericStringInnerNumber =
            TestType(
                name = "genericStringInnerNumber",
                javaReference = "Generic<String>.Inner<Number>",
                javaImport = "generic.pkg.Generic",
                classPath = listOf(genericWithInnerJar),
            )

        private val numberClassType = classTypeItem("java.lang.Number")
        private val stringClassType = classTypeItem("java.lang.String")

        private val params =
            listOf(
                // Test what happens when processing types with a self-consistent set of classes.
                TestParams(
                    name = "other and nested both defined",
                    classpath = listOf(otherWithNestedJar),
                    expectedType =
                        classTypeItem(
                            "other.pkg.Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    "other.pkg.Other",
                                ),
                        ),
                ),

                // Test what happens when processing types with an outer class but a missing nested
                // class.
                TestParams(
                    name = "other defined but nested undefined",
                    classpath = listOf(otherWithoutNestedJar),
                    expectedType =
                        classTypeItem(
                            "other.pkg.Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    "other.pkg.Other",
                                ),
                        ),
                ),

                // Test what happens when processing types without an outer class.
                TestParams(
                    name = "other and nested both undefined",
                    classpath = emptyList(),
                    expectedBinaryType =
                        classTypeItem(
                            "other.pkg.Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    "other.pkg.Other",
                                ),
                        ),
                    expectedSourceType =
                        classTypeItem(
                            "Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    "Other",
                                ),
                        ),
                ),

                // Test what happens when processing types without an outer class but with another
                // class in the same package. This is needed because when processing an unresolvable
                // type the existence of a package can change the behavior. In this case the
                // presence of `other.pkg.Another` ensures that `other.pkg` is treated as a package
                // and not a qualified class.
                TestParams(
                    name = "other and nested undefined but with another class in the same package",
                    classpath =
                        listOf(
                            // Include a jar that contains a class in other.pkg.
                            anotherClassJar,
                        ),
                    expectedType =
                        classTypeItem(
                            "other.pkg.Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    "other.pkg.Other",
                                    // The presence of the `other.pkg.Another` class prevents
                                    // `other.pkg` from being treated as an outer class like it is
                                    // in the preceding test case.
                                ),
                        ),
                    expectedSourceType =
                        classTypeItem(
                            // TODO(b/479907812): This qualified name is wrong, should be
                            //  other.pkg.Other.Nested.
                            "Other.Nested",
                            outerClassType =
                                classTypeItem(
                                    // TODO(b/479907812): This qualified name is wrong, should be
                                    //  other.pkg.Other.
                                    "Other",
                                ),
                        ),
                ),
                TestParams(
                    name = "generic defined",
                    classpath = listOf(genericWithInnerJar),
                    testType = genericString,
                    expectedType =
                        classTypeItem(
                            "generic.pkg.Generic",
                            arguments = listOf(stringClassType),
                        ),
                ),
                TestParams(
                    name = "generic undefined",
                    classpath = emptyList(),
                    testType = genericString,
                    expectedType =
                        classTypeItem(
                            "generic.pkg.Generic",
                            arguments = listOf(stringClassType),
                        ),
                    expectedSourceType =
                        classTypeItem(
                            // TODO(b/479907812): This qualified name is wrong, should be
                            //  generic.pkg.Generic.
                            "Generic",
                            arguments = listOf(stringClassType),
                        ),
                ),
                TestParams(
                    name = "generic and inner both defined",
                    classpath = listOf(genericWithInnerJar),
                    testType = genericStringInnerNumber,
                    expectedType =
                        classTypeItem(
                            "generic.pkg.Generic.Inner",
                            arguments = listOf(numberClassType),
                            outerClassType =
                                classTypeItem(
                                    "generic.pkg.Generic",
                                    arguments = listOf(stringClassType),
                                ),
                        ),
                ),
                TestParams(
                    name = "generic defined but inner undefined",
                    classpath = listOf(genericWithoutInnerJar),
                    testType = genericStringInnerNumber,
                    expectedType =
                        classTypeItem(
                            "generic.pkg.Generic.Inner",
                            arguments = listOf(numberClassType),
                            outerClassType =
                                classTypeItem(
                                    "generic.pkg.Generic",
                                    arguments = listOf(stringClassType),
                                ),
                        ),
                ),
                TestParams(
                    name = "generic and inner both undefined",
                    classpath = emptyList(),
                    testType = genericStringInnerNumber,
                    expectedType =
                        classTypeItem(
                            "generic.pkg.Generic.Inner",
                            arguments = listOf(numberClassType),
                            outerClassType =
                                classTypeItem(
                                    "generic.pkg.Generic",
                                    arguments = listOf(stringClassType),
                                ),
                        ),
                    expectedSourceType =
                        classTypeItem(
                            // TODO(b/479907812): This qualified name is wrong, should be
                            //  generic.pkg.Generic.Inner.
                            "Generic.Inner",
                            arguments = listOf(numberClassType),
                            outerClassType =
                                classTypeItem(
                                    // TODO(b/479907812): This qualified name is wrong, should be
                                    //  generic.pkg.Generic.
                                    "Generic",
                                    arguments = listOf(stringClassType),
                                ),
                        ),
                ),
            )

        @JvmStatic @Parameterized.Parameters fun data() = params
    }

    @Test
    fun `Test invalid binary reference`() {
        runCodebaseTest(
            java(
                """
                    package placeholder;
                    public interface Placeholder {}
                """
            ),
            testFixture =
                TestFixture(
                    additionalClassPath =
                        buildList {
                            add(params.testType.binaryTestJar.toFile())
                            params.classpath.mapTo(this) { it.toFile() }
                        }
                ),
        ) {
            val testClass = codebase.assertResolvedClass("test.pkg.Test")
            val testField = testClass.fields().single()
            val type = testField.type()
            assertEquals(params.expectedBinaryType, type)
        }
    }

    @Test
    fun `Test invalid source reference`() {
        runCodebaseTest(
            params.testType.javaTestFile,
            testFixture =
                TestFixture(
                    additionalClassPath = params.classpath.map { it.toFile() },
                    excludedIssues = setOf(Issues.UNRESOLVED_IMPORT),
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val testField = testClass.fields().single()
            val type = testField.type()
            assertEquals(params.expectedSourceType, type)
        }
    }
}
