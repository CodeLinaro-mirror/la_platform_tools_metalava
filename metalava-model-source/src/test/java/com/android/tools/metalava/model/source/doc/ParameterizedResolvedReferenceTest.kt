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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedResolvedReferenceTest {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        /** The name of the test. */
        val name: String = reference.fullyQualifiedForm,

        /** The [ResolvedReference] being tested. */
        val reference: ResolvedReference,

        /** The expected value of [ResolvedReference.fullyQualifiedForm]. */
        val expectedFullyQualifiedForm: String,

        /**
         * The expected value of [ResolvedReference.formatForTagReference] when passed
         * `containingClassName = "test.pkg.Class"`.
         */
        val expectedForClass: String = expectedFullyQualifiedForm,

        /**
         * The expected value of [ResolvedReference.formatForTagReference] when passed
         * `containingClassName = "test.pkg.Class.Nested"`.
         */
        val expectedForClassNested: String = expectedFullyQualifiedForm,
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
                    reference = PackageReference("test.pkg"),
                    expectedFullyQualifiedForm = "test.pkg",
                ),
                TestParams(
                    reference = ClassReference("test.pkg.Class"),
                    expectedFullyQualifiedForm = "test.pkg.Class",
                ),
                TestParams(
                    reference = ClassReference("test.pkg.Class.Nested"),
                    expectedFullyQualifiedForm = "test.pkg.Class.Nested",
                ),
                TestParams(
                    reference = TypeParameterReference("A"),
                    expectedFullyQualifiedForm = "A",
                ),
                TestParams(
                    reference = FieldReference("test.pkg.Class", "field"),
                    expectedFullyQualifiedForm = "test.pkg.Class#field",
                    expectedForClass = "#field",
                ),
                TestParams(
                    reference = FieldReference("test.pkg.Class.Nested", "field"),
                    expectedFullyQualifiedForm = "test.pkg.Class.Nested#field",
                    expectedForClassNested = "#field",
                ),
                TestParams(
                    reference = FieldReference("test.pkg.Other", "field"),
                    expectedFullyQualifiedForm = "test.pkg.Other#field",
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params
    }

    @Test
    fun `Test fullyQualifiedForm`() {
        assertEquals(params.expectedFullyQualifiedForm, params.reference.fullyQualifiedForm)
    }

    @Test
    fun `Test format for test_pkg_Class`() {
        val formatted = params.reference.formatForTagReference("test.pkg.Class")
        assertEquals(params.expectedForClass, formatted)
    }

    @Test
    fun `Test format for test_pkg_Class_Nested`() {
        val formatted = params.reference.formatForTagReference("test.pkg.Class.Nested")
        assertEquals(params.expectedForClassNested, formatted)
    }
}
