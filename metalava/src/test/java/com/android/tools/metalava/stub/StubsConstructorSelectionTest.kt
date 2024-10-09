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

package com.android.tools.metalava.stub

import com.android.tools.metalava.testing.java
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests which constructors are selected for use by derived stub classes. */
class StubsConstructorSelectionTest : AbstractStubsTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(
        /** Name of the test. */
        val name: String,

        /** List of parameters for each input constructor. */
        val parameters: List<String>,

        /** The arguments to the super call in the input child class. */
        val inputSuperArgs: String,

        /** The arguments to the super call in the output child stub class. */
        val outputSuperArgs: String,
    ) {
        override fun toString() = name

        /** The superCall in the output child stub class. */
        val outputSuperCall = if (outputSuperArgs.isEmpty()) "" else "super($outputSuperArgs); "
    }

    companion object {
        @JvmStatic @Parameterized.Parameters fun params() = params

        private val params =
            listOf(
                    TestParams(
                        name = "different parameter count including empty",
                        parameters = listOf("", "int i"),
                        inputSuperArgs = "",
                        // Choose the constructor with the fewest number of parameters, i.e. 0.
                        outputSuperArgs = "",
                    ),
                    TestParams(
                        name = "different parameter count",
                        parameters = listOf("String s, Number r", "int i"),
                        inputSuperArgs = "",
                        // Choose the constructor with the fewest number of parameters, i.e. 1.
                        outputSuperArgs = "0",
                    ),
                    TestParams(
                        name = "same number of parameters different types",
                        parameters = listOf("Number b", "Short i"),
                        inputSuperArgs = "",
                        // Choose the constructor with the shortest types, i.e. `Short`.
                        outputSuperArgs = "(java.lang.Short)null",
                    ),
                    TestParams(
                        name = "same number of parameters and same type length",
                        parameters = listOf("java.util.Map m", "java.util.Set s"),
                        inputSuperArgs = "(java.util.Set)null",
                        // Choose the constructor that comes earliest in the parent class.
                        outputSuperArgs = "(java.util.Map)null",
                    ),
                )
                .flatMap {
                    // Run the test forwards and backwards, they should be the same.
                    listOf(
                        it.copy(name = "${it.name} - forward"),
                        it.copy(
                            name = "${it.name} - backward",
                            parameters = it.parameters.reversed(),
                        ),
                    )
                }
    }

    @Test
    fun `Stub constructor selection`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        buildString {
                            append("package test.pkg;")
                            append("public class Parent {")
                            params.parameters.joinTo(this, "\n") { "public Parent($it) {}" }
                            append("}")
                        }
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Child extends Parent {
                                public Child() {super(${params.inputSuperArgs});}
                            }
                        """
                    ),
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Child extends test.pkg.Parent {
                            public Child() { ${params.outputSuperCall}throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                )
        )
    }
}
