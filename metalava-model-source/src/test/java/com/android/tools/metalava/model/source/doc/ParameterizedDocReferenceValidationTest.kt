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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedDocReferenceValidationTest {

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
        val reference: String = name,
        val valid: Boolean = true,
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
                // Packages
                TestParams(
                    name = "java",
                ),
                TestParams(
                    name = "java.util",
                ),
                // Classes
                TestParams(
                    name = "java.util.Map",
                ),
                TestParams(
                    name = "java.util.Map.Entry",
                ),
                // Fields (or maybe methods, no parentheses is ambiguous).
                TestParams(
                    name = "java.lang.Integer#MAX_VALUE",
                ),
                TestParams(
                    name = "#MAX_VALUE",
                ),
                TestParams(
                    name = "MAX_VALUE",
                ),
                // Methods with no parameters.
                TestParams(
                    name = "java.lang.Character#isWhitespace()",
                ),
                TestParams(
                    name = "#isWhitespace()",
                ),
                TestParams(
                    name = "isWhitespace()",
                ),
                // Methods with one parameter (no name).
                TestParams(
                    name = "java.lang.String#toLowerCase(Locale)",
                ),
                TestParams(
                    name = "#toLowerCase(Locale)",
                ),
                TestParams(
                    name = "toLowerCase(Locale)",
                ),
                // Methods with one parameter (with name).
                TestParams(
                    name = "java.lang.String#toLowerCase(Locale locale)",
                ),
                TestParams(
                    name = "#toLowerCase(Locale locale)",
                ),
                TestParams(
                    name = "toLowerCase(Locale locale)",
                ),
                // Methods with two parameters (no names).
                TestParams(
                    name = "java.util.Map#put(K, V)",
                ),
                TestParams(
                    name = "#put(K, V)",
                ),
                TestParams(
                    name = "#put(K, V)",
                ),
                // Methods with two parameters (with names).
                TestParams(
                    name = "java.util.Map#put(K key, V value)",
                ),
                TestParams(
                    name = "#put(K key, V value)",
                ),
                TestParams(
                    name = "put(K key, V value)",
                ),
                // Methods with generic parameter types.
                TestParams(
                    name = "java.util.Collection#addAll(Collection<? extends E>)",
                ),
                // Fragment reference
                TestParams(
                    name = "java.util.Collection##optional-restrictions",
                ),
                TestParams(
                    name = "##optional-restrictions",
                ),
                // Invalid references
                TestParams(
                    name = "<empty>",
                    reference = "",
                    valid = false,
                ),
                TestParams(
                    name = ".java.util.List",
                    valid = false,
                ),
                TestParams(
                    name = "List<String>",
                    valid = false,
                ),
                TestParams(
                    name = "unbalanced-parentheses",
                    reference = "#method(int",
                    valid = false,
                ),
                TestParams(
                    name = "trailing-junk",
                    reference = "#method(int),",
                    valid = false,
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params
    }

    @Test
    fun `Test validation`() {
        assertEquals(params.valid, LinkTagType.validateReference(params.reference))
    }
}
