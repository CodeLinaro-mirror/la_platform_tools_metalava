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

package com.android.tools.metalava.model.testsuite.callableitem

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.kotlin
import java.util.EnumSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

@RequiresCapabilities(Capability.KOTLIN)
class CommonParameterizedCreateOverloadTest : BaseModelTest() {

    /** Set of tags that handle the references. */
    enum class CallableKind(
        val kotlinFactory: TestParams.() -> TestFile,
        val callableGetter: ClassItem.() -> CallableItem,
        val parametersExtractor: (String) -> String,
        val customValidator: ValidationContext.() -> Unit = {},
    ) {
        METHOD(
            kotlinFactory = {
                kotlin(
                    """
                        package test.pkg
                        class Foo<F> {
                            fun <C>foo($sourceParameters) {}
                        }
                    """
                )
            },
            callableGetter = { methods().single() },
            parametersExtractor = { it.removePrefix("method test.pkg.Foo.foo(").removeSuffix(")") },
        ),
        CONSTRUCTOR(
            kotlinFactory = {
                kotlin(
                    """
                        package test.pkg
                        class Foo<F>($sourceParameters)
                    """
                )
            },
            callableGetter = { constructors().single() },
            parametersExtractor = {
                it.removePrefix("constructor test.pkg.Foo.Foo(").removeSuffix(")")
            },
            customValidator = {
                overload as ConstructorItem
                // Overloaded constructors cannot be primary.
                assertFalse(overload.isPrimary, message = "not primary")
            }
        ),
        ;

        override fun toString() = name.lowercase()
    }

    @Parameterized.Parameter(0) lateinit var callableKind: CallableKind

    @Parameterized.Parameter(1) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        /** The source parameters. */
        val sourceParameters: String,

        /** A list of indices of parameters from the original list to include in the overrides. */
        val overloadParameters: List<Int>,
        val expectedParametersString: String,
        val supportedCallableKinds: Set<CallableKind> = EnumSet.allOf(CallableKind::class.java),
        val customValidator: ValidationContext.() -> Unit = {},
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

    data class ValidationContext(
        val testClass: ClassItem,
        val original: CallableItem,
        val overload: CallableItem,
    )

    companion object : Assertions {
        private val params =
            listOf(
                TestParams(
                    name = "varargs not last",
                    sourceParameters = "vararg str: String, bool: Boolean = true, int: Int = 1",
                    overloadParameters = listOf(0, 2),
                    expectedParametersString = "String[], int",
                ),
                TestParams(
                    name = "varargs last",
                    sourceParameters = "vararg str: String, bool: Boolean = true, int: Int = 1",
                    overloadParameters = listOf(0),
                    expectedParametersString = "java.lang.String...",
                ),
                TestParams(
                    // Tests what happens when creating an overload that uses a class's type
                    // parameter (i.e. F).
                    name = "generic - class parameter",
                    sourceParameters = "foo: F, int: Int = 1",
                    overloadParameters = listOf(0),
                    expectedParametersString = "F",
                    customValidator = {
                        // Make sure that the parameter type refers to the class's type parameter.
                        val parameter = overload.parameters().single()
                        parameter.type().assertVariableTypeItem {
                            assertSame(testClass.typeParameterList[0], asTypeParameter)
                        }
                    },
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        /**
         * Compute the cross product of [CallableKind] and [params].
         *
         * Ignores any combination that is not valid.
         */
        fun params() =
            CallableKind.entries.flatMap { callableKind ->
                params.mapNotNull { p ->
                    if (callableKind in p.supportedCallableKinds) arrayOf(callableKind, p) else null
                }
            }
    }

    @Test
    fun `Test createOverload`() {
        val kotlinFactory = callableKind.kotlinFactory
        runCodebaseTest(
            params.kotlinFactory(),
        ) {
            val testClass = codebase.assertClass("test.pkg.Foo")
            val callableGetter = callableKind.callableGetter
            val original = testClass.callableGetter()

            val overloadParameters = buildList {
                val originalParameters = original.parameters()
                for (index in params.overloadParameters) {
                    add(originalParameters[index])
                }
            }

            // When dropping the second parameter the varargs is not last so should be treated as a
            // plain array.
            original.createOverload(overloadParameters).let { overload ->
                val parametersString = callableKind.parametersExtractor(overload.toString())
                assertEquals(
                    params.expectedParametersString,
                    parametersString,
                    message = "drop one parameter; varargs not last"
                )
                // Make sure that the parameters have the correct index.
                for ((i, parameterItem) in overload.parameters().withIndex()) {
                    assertEquals(i, parameterItem.parameterIndex, message = "parameter $i")
                }

                // Create context for use by custom validators.
                val validationContext =
                    ValidationContext(
                        testClass,
                        original,
                        overload,
                    )

                // Run any [CallableKind] specific validation.
                validationContext.(callableKind.customValidator)()

                // Run any [TestParams] specific validation.
                validationContext.(params.customValidator)()
            }
        }
    }
}
