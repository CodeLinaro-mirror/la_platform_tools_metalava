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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests how nullability is handled on type variables. */
class CommonParameterizedTypeVariableNullabilityTest : BaseModelTest() {
    /** The [NullabilityForm] being tested by this instance of the test. */
    @Parameterized.Parameter(0) lateinit var nullabilityForm: NullabilityForm

    /** The [TestParams] being tested by this instance of the test. */
    @Parameterized.Parameter(1) lateinit var params: TestParams

    /**
     * Enumeration of the different forms of nullability information.
     *
     * @param validInputFormats the set of [InputFormat]s that support the form.
     */
    enum class NullabilityForm(
        val validInputFormats: Set<InputFormat>,
        val signatureOption: String,
    ) {
        /** Uses kotlin style nulls. */
        KOTLIN_STYLE_NULLS(
            validInputFormats = setOf(InputFormat.KOTLIN, InputFormat.SIGNATURE),
            signatureOption = "kotlin-style-nulls=yes",
        ) {

            override fun typeFromParams(params: TestParams) = params.kotlinStyleNullsVariable
        },

        /** Uses type use annotations. */
        TYPE_USE_ANNOTATIONS(
            validInputFormats = setOf(InputFormat.JAVA, InputFormat.SIGNATURE),
            signatureOption = "include-type-use-annotations=yes",
        ) {
            override fun typeFromParams(params: TestParams) = params.typeUseAnnotationsVariable
        },
        ;

        /**
         * Get the string representation of the type being tested from [TestParams] that uses this
         * [NullabilityForm].
         */
        abstract fun typeFromParams(params: TestParams): String
    }

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    /** The parameters for a test. */
    data class TestParams
    @EntryPoint
    constructor(
        /** The name of the test. */
        val name: String,

        /** The [NullabilityForm.TYPE_USE_ANNOTATIONS] form of the type variable. */
        val typeUseAnnotationsVariable: String,

        /** The [NullabilityForm.KOTLIN_STYLE_NULLS] form of the type variable. */
        val kotlinStyleNullsVariable: String,

        /** The expected [TypeNullability] of the resulting [VariableTypeItem] */
        val expectedNullability: TypeNullability,
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

    companion object {
        private val params =
            listOf(
                TestParams(
                    name = "plain variable",
                    typeUseAnnotationsVariable = "T",
                    kotlinStyleNullsVariable = "T",
                    expectedNullability = TypeNullability.UNDEFINED,
                ),
                TestParams(
                    name = "nullable variable",
                    typeUseAnnotationsVariable = "@Nullable T",
                    kotlinStyleNullsVariable = "T?",
                    expectedNullability = TypeNullability.NULLABLE,
                ),
                TestParams(
                    name = "non-nullable variable",
                    typeUseAnnotationsVariable = "@NonNull T",
                    // This looks strange but creating the intersection type by combining a type
                    // variable with a non-nullable Any is the documented way to make that type
                    // variable non-nullable. This is needed because there is no other way in Kotlin
                    // to explicitly say that a type is non-nullable and unlike other types (which
                    // default to non-nullable0 the default nullability of a type variable is
                    // undefined.
                    kotlinStyleNullsVariable = "T & Any",
                    expectedNullability = TypeNullability.NONNULL,
                ),
            )

        /** Compute the cross product of [NullabilityForm] and [params]. */
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun params() =
            NullabilityForm.entries.flatMap { nullabilityFormat ->
                params.map { p -> arrayOf(nullabilityFormat, p) }
            }

        /**
         * Filter out any tests combinations that are not valid for a specific [InputFormat] and
         * [NullabilityForm] combination.
         */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            nullabilityForm: NullabilityForm,
            @Suppress("unused") params: TestParams,
        ): Boolean {
            val inputFormat = config.inputFormat

            // Ignore any tests that are not valid for the InputFormat/NullabilityForm combination.
            return inputFormat in nullabilityForm.validInputFormats
        }
    }

    /**
     * Run the [params] test with [nullabilityForm].
     *
     * @param wrapper a lambda that will wrap the String representation of the type being tested
     *   inside another type, if necessary, to verify the behavior in different use sites.
     * @param unwrapper a lambda that will take the [TypeItem] produced from the [wrapper] result
     *   and extract the type variable that is then verified against the expectations in [params].
     */
    private fun runTypeItemTest(
        wrapper: (String) -> String,
        unwrapper: (TypeItem) -> TypeItem,
    ) {
        // Get the input type appropriate for the NullabilityForm and wrap it inside another type,
        // if necessary.
        val inputType = wrapper(nullabilityForm.typeFromParams(params))

        // Construct the inpu
        val inputSet =
            when (inputFormat) {
                // Input files for Kotlin. Only used for NullabilityForm.KOTLIN_STYLE_NULLS.
                InputFormat.KOTLIN -> {
                    inputSet(
                        kotlin(
                            """
                                package test.pkg
                                class Outer<O> {
                                    inner class Inner<I>
                                }
                            """
                        ),
                        kotlin(
                            """
                                package test.pkg
                                interface Test<T> {
                                    fun method(): $inputType
                                }
                            """
                        ),
                    )
                }

                // Input files for Java. Only used for NullabilityForm.TYPE_USE_ANNOTATIONS.
                InputFormat.JAVA -> {
                    inputSet(
                        java(
                            """
                                package test.pkg;
                                public class Outer<O> {
                                    public class Inner<I> {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                import type.use.only.NonNull;
                                import type.use.only.Nullable;
                                public interface Test<T> {
                                    $inputType method();
                                }
                            """
                        ),
                        KnownSourceFiles.typeUseOnlyNullableSource,
                        KnownSourceFiles.typeUseOnlyNonNullSource,
                    )
                }

                // Input files for Signature. Used for all NullabilityForms.
                InputFormat.SIGNATURE -> {
                    inputSet(
                        signature(
                            """
                                // Signature format: 5.0
                                // - kotlin-name-type-order=yes
                                // - kotlin-style-nulls=no
                                // - ${nullabilityForm.signatureOption}
                                package test.pkg {
                                  public class Outer<O> {
                                    ctor public Outer();
                                  }
                                  public class Outer.Inner<I> {
                                    ctor public Outer.Inner();
                                  }
                                  public interface Test<T> {
                                    method public method(): $inputType;
                                  }
                                }
                            """
                        ),
                    )
                }
            }

        val testFixture =
            TestFixture(
                // Disable the supported InputFormat check as this test is already parameterized and
                // filtered by InputFormat.
                checkSupportedInputFormats = false,
            )

        runCodebaseTest(
            inputSet,
            testFixture = testFixture,
        ) {
            val methodItem = codebase.assertClass("test.pkg.Test").methods().single()
            val typeItem = unwrapper(methodItem.returnType())

            // Check the nullability directly.
            assertEquals(params.expectedNullability, typeItem.modifiers.nullability)
        }
    }

    @Test
    fun `Test type variable on its own`() {
        runTypeItemTest(
            wrapper = { it },
            unwrapper = { it },
        )
    }

    @Test
    fun `Test type variable as type argument of inner class type`() {
        runTypeItemTest(
            wrapper = { "test.pkg.Outer<String>.Inner<$it>" },
            unwrapper = {
                it as ClassTypeItem
                it.arguments.single()
            },
        )
    }

    @Test
    fun `Test type variable as type argument of outer class type`() {
        runTypeItemTest(
            wrapper = { "test.pkg.Outer<$it>.Inner<String>" },
            unwrapper = {
                it as ClassTypeItem
                it.outerClassType!!.arguments.single()
            },
        )
    }
}
