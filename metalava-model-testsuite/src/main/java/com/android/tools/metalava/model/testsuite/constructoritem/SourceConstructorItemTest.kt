/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.constructoritem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Common tests for implementations of [ConstructorItem] for source based models. */
class SourceConstructorItemTest : BaseModelTest() {

    @Test
    fun `test implicit default constructor`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Foo {}
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val constructorItem = fooClass.assertConstructor("")

            assertTrue(
                fooClass.hasImplicitDefaultConstructor(),
                message = "hasImplicitDefaultConstructor"
            )
            assertTrue(constructorItem.isImplicitConstructor(), message = "isImplicitConstructor")
        }
    }

    @Test
    fun `test explicit no-args public constructor`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public Foo() {}
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val constructorItem = fooClass.assertConstructor("")

            assertFalse(
                fooClass.hasImplicitDefaultConstructor(),
                message = "!hasImplicitDefaultConstructor"
            )
            assertFalse(constructorItem.isImplicitConstructor(), message = "!isImplicitConstructor")
        }
    }
}
