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

package com.android.tools.metalava.model.testsuite.annotationitem.binding

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@SupportedInputFormats(InputFormat.JAVA)
class CommonParameterizedAnnotationBindingTest : BaseModelTest() {

    @Parameterized.Parameter(0) internal lateinit var params: TestParams<*>

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class BoundProviderContext(
        val codebase: Codebase,
        val annotatedItem: Item,
    )

    data class TestParams<T : Any>
    @EntryPoint
    constructor(
        val name: String,
        val sourceFiles: List<TestFile>,
        val annotation: String,
        val kClass: KClass<T>,
        val expectedIssues: String = "",
        val expectedBoundProvider: BoundProviderContext.() -> T?,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    /**
     * Binding for any annotation that shows that a binding does not have to process all attributes.
     *
     * Only implements [equals] and [hashCode] for testing purposes, this not required for use with
     * [AnnotationItem.bindTo].
     */
    class Empty {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode() = javaClass.hashCode()
    }

    /** Class that cannot be instantiated, used for testing error handling. */
    class InvalidClass(val invalid: Runnable)

    /** Binding for [requiredBooleanAnnotation]. */
    data class BooleanAnno(
        val value: Boolean,
    )

    /** Binding for [requiredIntAnnotation]. */
    data class IntAnno(
        val value: Int,
    )

    /** Binding for [requiredLongAnnotation]. */
    data class LongAnno(
        val value: Long,
    )

    /**
     * Binding for [requiredIntAnnotation].
     *
     * Used for testing that the parameter default will only be used as a last resort.
     */
    data class IntAnnoWithDefault(
        val value: Int = 9,
    )

    /**
     * Binding for [requiredLongAnnotation].
     *
     * Used for testing that the parameter default will only be used as a last resort.
     */
    data class LongAnnoWithDefault(
        val value: Long = 9L,
    )

    /** Binding for [requiredStringAnnotation]. */
    data class StringAnno(
        val value: String,
    )

    /** Binding for [requiredStringAnnotation]. */
    data class NullableStringAnno(
        val value: String?,
    )

    /** Binding for [requiredStringAnnotation]. */
    data class StringWithItemAnno(
        val item: Item,
        val value: String,
    )

    /** Binding for [requiredStringListAnnotation]. */
    data class StringListAnno(
        val s: List<String>,
    )

    /** Binding for [requiredContainerAnnotation]. */
    data class ContainerAnno(val nested: StringAnno)

    /** Binding for [requiredStringAnnotation] and similar. */
    data class ArrayElementValueStringAnno(val s: List<ArrayElementValue>)

    /** Binding for [requiredClassReferenceAnnotation] and similar. */
    data class ClassItemAnno(val c: ClassItem)

    companion object : Assertions {
        /** See [BooleanAnno]. */
        private val requiredBooleanAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface BooleanAnno {
                        boolean value();
                    }
                """
            )

        /** See [IntAnno]. */
        private val requiredIntAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface IntAnno {
                        int value();
                    }
                """
            )

        /** See [LongAnno]. */
        private val requiredLongAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface LongAnno {
                        long value();
                    }
                """
            )

        /** See [IntAnno]. */
        private val optionalIntAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface IntAnno {
                        int value() default 17;
                    }
                """
            )

        /** See [LongAnno]. */
        private val optionalLongAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface LongAnno {
                        long value() default 17L;
                    }
                """
            )

        /** See [StringAnno], [NullableStringAnno]. */
        private val requiredStringAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface StringAnno {
                        String value();
                    }
                """
            )

        /** See [StringAnno], [NullableStringAnno]. */
        private val optionalStringAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface StringAnno {
                        String value() default "unspecified";
                    }
                """
            )

        private val requiredStringListAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface StringListAnno {
                        String[] s();
                    }
                """
            )

        private val requiredContainerAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface ContainerAnno {
                        StringAnno nested();
                    }
                """
            )

        private val requiredClassReferenceAnnotation =
            java(
                """
                    package test.pkg;
                    public @interface ClassAnno {
                        Class<?> c();
                    }
                """
            )

        @EntryPoint
        inline fun <reified T : Any> testParams(
            name: String,
            sourceFiles: List<TestFile>,
            annotation: String,
            expectedBound: T?,
            expectedIssues: String = "",
        ) =
            TestParams(
                name,
                sourceFiles,
                annotation,
                kClass = T::class,
                expectedIssues,
            ) {
                expectedBound
            }

        @EntryPoint
        inline fun <reified T : Any> testParams(
            name: String,
            sourceFiles: List<TestFile>,
            annotation: String,
            expectedIssues: String = "",
            noinline expectedBoundProvider: BoundProviderContext.() -> T?,
        ) =
            TestParams(
                name,
                sourceFiles,
                annotation,
                kClass = T::class,
                expectedIssues,
                expectedBoundProvider,
            )

        private val params =
            listOf(
                // No parameters test
                testParams(
                    name = "no parameters class",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = true)",
                    expectedBound = Empty(),
                ),

                // Invalid parameters test
                testParams<InvalidClass>(
                    name = "invalid parameters",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = true)",
                    expectedBound = null,
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: internal error: could not bind com.android.tools.metalava.model.testsuite.annotationitem.binding.CommonParameterizedAnnotationBindingTest.InvalidClass to test.pkg.BooleanAnno: No argument provided for a required parameter: parameter #0 invalid of fun `<init>`(java.lang.Runnable): com.android.tools.metalava.model.testsuite.annotationitem.binding.CommonParameterizedAnnotationBindingTest.InvalidClass [InvalidAnnotationBinding]",
                ),

                // Invalid value test.
                testParams<StringAnno>(
                    name = "invalid value",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = false)",
                    expectedBound = null,
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Attribute 'value' is invalid: `false` cannot be converted to kotlin.String [InvalidAnnotationBinding]",
                ),

                // Single value types tests.
                testParams(
                    name = "boolean",
                    sourceFiles = listOf(requiredBooleanAnnotation),
                    annotation = "@BooleanAnno(value = false)",
                    expectedBound = BooleanAnno(value = false),
                ),
                testParams(
                    name = "int",
                    sourceFiles = listOf(requiredIntAnnotation),
                    annotation = "@IntAnno(value = 12)",
                    expectedBound = IntAnno(value = 12),
                ),
                testParams(
                    name = "long",
                    sourceFiles = listOf(requiredLongAnnotation),
                    annotation = "@LongAnno(value = 12L)",
                    expectedBound = LongAnno(value = 12L),
                ),
                testParams(
                    name = "String",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno(value = "a")""",
                    expectedBound = StringAnno(value = "a"),
                ),

                // Annotations/classes with defaults tests
                testParams(
                    name = "int annotation with default",
                    sourceFiles = listOf(optionalIntAnnotation),
                    annotation = "@IntAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = IntAnno(value = 17),
                ),
                testParams(
                    name = "long annotation with default",
                    sourceFiles = listOf(optionalLongAnnotation),
                    annotation = "@LongAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = LongAnno(value = 17L),
                ),
                testParams(
                    name = "int annotation and class with defaults",
                    sourceFiles = listOf(optionalIntAnnotation),
                    annotation = "@IntAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = IntAnnoWithDefault(value = 17),
                ),
                testParams(
                    name = "long annotation and class with defaults",
                    sourceFiles = listOf(optionalLongAnnotation),
                    annotation = "@LongAnno()",
                    // Uses the default from the annotation definition.
                    expectedBound = LongAnnoWithDefault(value = 17L),
                ),

                // Missing attribute for required annotation tests.
                testParams(
                    name = "int class with default",
                    sourceFiles = listOf(requiredIntAnnotation),
                    // This is not strictly valid.
                    annotation = "@IntAnno()",
                    // Uses the default from the class definition.
                    expectedBound = IntAnnoWithDefault(),
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Required attribute 'value' is missing on @test.pkg.IntAnno [MissingRequiredAttribute]",
                ),
                testParams(
                    name = "long class with default",
                    sourceFiles = listOf(requiredLongAnnotation),
                    // This is not strictly valid.
                    annotation = "@LongAnno()",
                    // Uses the default from the class definition.
                    expectedBound = LongAnnoWithDefault(),
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Required attribute 'value' is missing on @test.pkg.LongAnno [MissingRequiredAttribute]",
                ),
                testParams(
                    name = "String with no value provided",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno()""",
                    expectedBound = StringAnno(value = ""),
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Required attribute 'value' is missing on @test.pkg.StringAnno [MissingRequiredAttribute]",
                ),

                // Nullable/non-nullable tests
                testParams(
                    name = "nullable String with required annotation",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno(value = "a")""",
                    expectedBound = NullableStringAnno(value = "a"),
                ),
                testParams(
                    name = "non-nullable String with optional annotation",
                    sourceFiles = listOf(optionalStringAnnotation),
                    annotation = """@StringAnno()""",
                    expectedBound = StringAnno(value = "unspecified"),
                ),
                testParams(
                    name = "nullable String with optional annotation",
                    sourceFiles = listOf(optionalStringAnnotation),
                    annotation = """@StringAnno()""",
                    expectedBound = NullableStringAnno(value = null),
                ),

                // List tests
                testParams(
                    name = "String list of single string value",
                    sourceFiles = listOf(optionalStringAnnotation),
                    annotation = """@StringAnno(s = "a")""",
                    expectedBound = StringListAnno(s = listOf("a")),
                ),
                testParams(
                    name = "String list of string array value",
                    sourceFiles = listOf(requiredStringListAnnotation),
                    annotation = """@StringListAnno(s = {"a", "b"})""",
                    expectedBound = StringListAnno(s = listOf("a", "b")),
                ),
                testParams(
                    name = "String list missing attribute",
                    sourceFiles = listOf(requiredStringListAnnotation),
                    annotation = """@StringListAnno()""",
                    expectedBound = StringListAnno(s = emptyList()),
                    expectedIssues =
                        "MAIN_SRC/src/test/pkg/Test.java:2: error: Required attribute 's' is missing on @test.pkg.StringListAnno [MissingRequiredAttribute]",
                ),

                // Nested binding tests
                testParams(
                    name = "Container of StringAnno",
                    sourceFiles = listOf(requiredContainerAnnotation),
                    annotation = """@ContainerAnno(nested = @StringAnno(value = "a"))""",
                    expectedBound = ContainerAnno(nested = StringAnno(value = "a")),
                ),

                // ArrayElementValue binding tests
                testParams(
                    name = "ArrayElementValue list of single string value",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno(s = "a")""",
                    expectedBound = ArrayElementValueStringAnno(s = listOf(literalValue("a"))),
                ),
                testParams(
                    name = "ArrayElementValue list of string array value",
                    sourceFiles = listOf(requiredStringListAnnotation),
                    annotation =
                        """@StringListAnno(s = {test.pkg.Constants.STRING_CONSTANT, "b"})""",
                    expectedBound =
                        ArrayElementValueStringAnno(
                            s =
                                listOf(
                                    fieldReferenceValue("test.pkg.Constants", "STRING_CONSTANT"),
                                    literalValue("b"),
                                )
                        ),
                ),

                // ClassItem binding tests
                testParams(
                    name = "ClassItem reference to String class",
                    sourceFiles = listOf(requiredClassReferenceAnnotation),
                    annotation = """@ClassAnno(c = String.class)""",
                ) {
                    ClassItemAnno(c = codebase.assertResolvedClass("java.lang.String"))
                },
                testParams<ClassItemAnno>(
                    name = "ClassItem reference to Unknown class",
                    sourceFiles = listOf(requiredClassReferenceAnnotation),
                    annotation = """@ClassAnno(c = Unknown.class)""",
                    expectedBound = null,
                    expectedIssues =
                        """
                            MAIN_SRC/src/test/pkg/Test.java:2: error: Attribute 'c' is invalid: `Unknown.class` cannot be converted to com.android.tools.metalava.model.ClassItem [InvalidAnnotationBinding]
                        """,
                ),

                // Item binding tests
                testParams(
                    name = "Item binding",
                    sourceFiles = listOf(requiredStringAnnotation),
                    annotation = """@StringAnno(value = "string")""",
                ) {
                    StringWithItemAnno(item = annotatedItem, value = "string")
                },
                testParams<StringWithItemAnno>(
                    name = "Attribute cannot be bound to Item parameter",
                    sourceFiles = emptyList(),
                    annotation = """@StringAnno(item = "item", value = "value")""",
                    expectedBound = null,
                    expectedIssues =
                        """MAIN_SRC/src/test/pkg/Test.java:2: error: Attribute 'item' is invalid: `"item"` cannot be converted to com.android.tools.metalava.model.Item [InvalidAnnotationBinding]""",
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @Test
    fun testBinding() {
        val sources = buildList {
            addAll(params.sourceFiles)
            add(
                java(
                    """
                        package test.pkg;
                        public class Test {
                        }
                    """
                )
            )
            add(
                java(
                    """
                        package test.pkg;
                        public interface Constants {
                            String STRING_CONSTANT = "string constant";
                        }
                    """
                )
            )
        }
        runCodebaseTest(
            inputSet(sources),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val annotationSource = params.annotation.replace("@", "@test.pkg.")

            // Metalava generally assumes that the sources that it processes can be compiled.
            // However, that does not prevent Metalava from processing invalid files. e.g. Metalava
            // is used to break dependency cycles  which mean that it can be given files before they
            // are compiled so they could contain invalid content. In those cases Metalava must not
            // abort processing and instead should report as many issues as it can.
            //
            // This creates annotations from sources directly rather than add them into the source
            // so that it can test some of those invalid but common mistakes, e.g. missing required
            // attributes.
            val annotation =
                AnnotationItem.createFromSource(codebase, annotationSource)
                    ?: error("could not create annotation from $annotationSource")
            val bound = annotation.bindTo(params.kClass, testClass)

            // Check expected issues first.
            assertAndRemoveReportedIssues(params.expectedIssues)

            val expectedBoundProvider = params.expectedBoundProvider
            val context =
                BoundProviderContext(
                    codebase,
                    annotatedItem = testClass,
                )
            val expectedBound = context.expectedBoundProvider()
            assertEquals(expectedBound, bound)
        }
    }
}
