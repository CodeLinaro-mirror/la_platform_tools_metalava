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
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.SelectableItem
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

        expectedState.docOnly?.let { expected ->
            assertEquals(expected, docOnly, message = "$message (docOnly)")
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
                        accessible=<not-set>,
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
            selectors.accessible
            selectors.docOnly
            selectors.removed
            selectors.showability

            assertEquals(
                """
                    class test.pkg.Foo {
                        originallyHidden=false,
                        inheritableHidden=false,
                        hidden=false,
                        accessible=true,
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
            testableSelectorsState = testableSelectorsState.copy(docOnly = false)
            selectors.assertEquals(testableSelectorsState, message = "after `docOnly` initialized")
        }
    }

    @Test
    fun `Test docOnly`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        /** @doconly */
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Foo {
                        }
                    """
                ),
            ),
        ) {
            val pkgItem = codebase.assertPackage("test.pkg")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val pkgSelectors = pkgItem.variantSelectors
            val fooSelectors = fooClass.variantSelectors

            var pkgSelectorsState = TestableSelectorsState(item = pkgItem)
            var fooSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the states before initializing any property.
            pkgSelectors.assertEquals(pkgSelectorsState, message = "initial pkg")
            fooSelectors.assertEquals(fooSelectorsState, message = "initial foo")

            // Get the `docOnly` property, do foo first to show it can inherit properly.
            assertEquals(true, fooSelectors.docOnly, message = "foo docOnly")

            // Check the states after initializing `docOnly`.
            pkgSelectorsState = pkgSelectorsState.copy(docOnly = true)
            pkgSelectors.assertEquals(pkgSelectorsState, message = "after pkg")

            fooSelectorsState = fooSelectorsState.copy(docOnly = true)
            fooSelectors.assertEquals(fooSelectorsState, message = "after foo")
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
            testableSelectorsState = testableSelectorsState.copy(removed = false)
            selectors.assertEquals(testableSelectorsState, message = "after `removed` initialized")
        }
    }

    @Test
    fun `Test removed`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        /** @removed */
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Foo {
                        }
                    """
                ),
            ),
        ) {
            val pkgItem = codebase.assertPackage("test.pkg")
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val pkgSelectors = pkgItem.variantSelectors
            val fooSelectors = fooClass.variantSelectors

            var pkgSelectorsState = TestableSelectorsState(item = pkgItem)
            var fooSelectorsState = TestableSelectorsState(item = fooClass)

            // Check the states before initializing any property.
            pkgSelectors.assertEquals(pkgSelectorsState, message = "initial pkg")
            fooSelectors.assertEquals(fooSelectorsState, message = "initial foo")

            // Get the `removed` property, do foo first to show it can inherit properly.
            assertEquals(true, fooSelectors.removed, message = "foo removed")

            // Check the states after initializing `removed`.
            pkgSelectorsState = pkgSelectorsState.copy(removed = true)
            pkgSelectors.assertEquals(pkgSelectorsState, message = "after pkg")

            fooSelectorsState = fooSelectorsState.copy(removed = true)
            fooSelectors.assertEquals(fooSelectorsState, message = "after foo")
        }
    }

    @Test
    fun `Test accessible`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Outer {
                            class PackagePrivateInaccessible {
                                public class PublicInsideInaccessible {}
                            }
                            protected class Protected {
                                public static final int FIELD = 0;
                            }
                            private void methodPrivateInaccessible() {}
                        }
                    """
                ),
            ),
        ) {
            // Get the `accessible` property for the pkg, is always `true`.
            val pkgItem = codebase.assertPackage("test.pkg")
            assertEquals(true, pkgItem.variantSelectors.accessible, message = "pkg accessible")

            var count = 0
            pkgItem.accept(
                object :
                    BaseItemVisitor(
                        // [ParameterItem]s are not [SelectableItem]s so there is no point in
                        // visiting them.
                        visitParameterItems = false,
                    ) {
                    override fun visitSelectableItem(item: SelectableItem) {
                        val name =
                            when (item) {
                                is ClassItem -> item.simpleName()
                                is MemberItem -> item.name()
                                else -> return
                            }

                        val expectedAccessible = !name.endsWith("Inaccessible")
                        assertEquals(
                            expectedAccessible,
                            item.variantSelectors.accessible,
                            message = "$item accessible"
                        )
                        count += 1
                    }
                }
            )

            // Make sure it actually did something.
            assertEquals(10, count, message = "item count")
        }
    }
}
