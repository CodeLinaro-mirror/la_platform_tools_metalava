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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ApiVariantSelectors.TestableSelectorsState
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for [ApiVariantSelectors]. */
class CommonApiVariantSelectorsTest : BaseModelTest() {

    /**
     * Test this [ApiVariantSelectors] against an [ApiVariantSelectors] with the same state as
     * [expectedState]
     */
    fun ApiVariantSelectors.assertEquals(expectedState: TestableSelectorsState, message: String) {
        assertEquals(expectedState.createSelectorsforTesting(), this, message = message)

        // The preceding check will verify that val properties like originallyHidden are either set
        // or not set as expected. The following checks test to make that they have the expected
        // value.
        expectedState.originallyHidden?.let { expectedOriginallyHidden ->
            assertEquals(
                expectedOriginallyHidden,
                originallyHidden,
                message = "$message (originallyHidden)"
            )
        }
    }

    @Test
    fun `Test toString`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            assertEquals(
                """
                    class test.pkg.Foo {
                        originallyHidden=<not-set>,
                        inheritableHidden=<not-set>,
                        hidden=<not-set>,
                        docOnly=<not-set>,
                        removed=<not-set>,
                        inheritIntoWasCalled=<not-set>,
                        showability=<not-set>,
                    }
                """
                    .trimIndent(),
                selectors.toString(),
                message = "before initializing"
            )

            // Initialize the properties.
            selectors.hidden
            selectors.docOnly
            selectors.removed
            selectors.showability

            assertEquals(
                """
                    class test.pkg.Foo {
                        originallyHidden=false,
                        inheritableHidden=false,
                        hidden=false,
                        docOnly=false,
                        removed=false,
                        inheritIntoWasCalled=true,
                        showability=Showability(show=NO_EFFECT, recursive=NO_EFFECT, forStubsOnly=NO_EFFECT, revertItem=null),
                    }
                """
                    .trimIndent(),
                selectors.toString(),
                message = "after initializing"
            )
        }
    }

    @Test
    fun `Test originallyHidden not hidden`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            assertEquals(false, selectors.originallyHidden, message = "originallyHidden")
        }
    }

    @Test
    fun `Test originallyHidden hidden`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    /** @hide */
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            var testableSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the state before initializing any property.
            selectors.assertEquals(testableSelectorsState, message = "initial")

            assertEquals(true, selectors.originallyHidden, message = "originallyHidden")

            // Check the state after initializing `originallyHidden`.
            testableSelectorsState =
                testableSelectorsState.copy(
                    originallyHidden = true,
                )
            selectors.assertEquals(
                testableSelectorsState,
                message = "after `originallyHidden` initialized"
            )
        }
    }

    @Test
    fun `Test not hidden`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            var testableSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the state before initializing any property.
            selectors.assertEquals(testableSelectorsState, message = "initial")

            // Get the `showability` property.
            assertEquals(Showability.NO_EFFECT, selectors.showability, message = "showability")

            // Check the state after initializing `showability`.
            testableSelectorsState =
                testableSelectorsState.copy(showability = Showability.NO_EFFECT)
            selectors.assertEquals(
                testableSelectorsState,
                message = "after `showability` initialized"
            )

            // Get the `hidden` property.
            assertEquals(false, selectors.hidden, message = "hidden")

            // Check the state after initializing `hidden`.
            testableSelectorsState =
                testableSelectorsState.copy(
                    originallyHidden = false,
                    inheritIntoWasCalled = true,
                    inheritableHidden = false,
                    hidden = false,
                )
            selectors.assertEquals(testableSelectorsState, message = "after `hidden` initialized")

            // Get the `docOnly` property.
            assertEquals(false, selectors.docOnly, message = "docOnly")

            // Check the state after initializing `docOnly`.
            testableSelectorsState = testableSelectorsState.copy(docOnly = false)
            selectors.assertEquals(testableSelectorsState, message = "after `docOnly` initialized")

            // Get the `removed` property.
            assertEquals(false, selectors.removed, message = "removed")

            // Check the state after initializing `removed`.
            testableSelectorsState = testableSelectorsState.copy(removed = false)
            selectors.assertEquals(testableSelectorsState, message = "after `removed` initialized")
        }
    }

    @Test
    fun `Test not docOnly`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            var testableSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the state before initializing any property.
            selectors.assertEquals(testableSelectorsState, message = "initial")

            // Get the `docOnly` property.
            assertEquals(false, selectors.docOnly, message = "docOnly")

            // Check the state after initializing `docOnly`.
            testableSelectorsState =
                testableSelectorsState.copy(
                    inheritIntoWasCalled = true,
                    showability = Showability.NO_EFFECT,
                    docOnly = false,
                )
            selectors.assertEquals(testableSelectorsState, message = "after `docOnly` initialized")
        }
    }

    @Test
    fun `Test not removed`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val selectors = fooClass.variantSelectors

            var testableSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the state before initializing any property.
            selectors.assertEquals(testableSelectorsState, message = "initial")

            // Get the `removed` property.
            assertEquals(false, selectors.removed, message = "removed")

            // Check the state after initializing `removed`.
            testableSelectorsState =
                testableSelectorsState.copy(
                    inheritIntoWasCalled = true,
                    showability = Showability.NO_EFFECT,
                    removed = false,
                )
            selectors.assertEquals(testableSelectorsState, message = "after `removed` initialized")
        }
    }
}
