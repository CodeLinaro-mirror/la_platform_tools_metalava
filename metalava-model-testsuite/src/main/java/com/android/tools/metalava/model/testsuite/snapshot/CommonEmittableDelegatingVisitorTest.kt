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

package com.android.tools.metalava.model.testsuite.snapshot

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.callableitem.CallableParameterDescriptorUsingDotsTest.Companion.assertClass
import com.android.tools.metalava.model.testsuite.callableitem.CallableParameterDescriptorUsingDotsTest.Companion.assertPackage
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for [EmittableDelegatingVisitor]. */
class CommonEmittableDelegatingVisitorTest : BaseModelTest() {
    @Parameterized.Parameter(0) lateinit var testData: TestParams

    data class TestParams(
        val name: String,
        val getter: CodebaseContext.() -> SelectableItem,
    ) {
        override fun toString(): String {
            return name
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun params() =
            listOf(
                TestParams(
                    name = "package",
                    getter = { codebase.assertPackage("test.pkg") },
                ),
                TestParams(
                    name = "class",
                    getter = { codebase.assertClass("test.pkg.Foo") },
                ),
                TestParams(
                    name = "constructor",
                    getter = { codebase.assertClass("test.pkg.Foo").constructors().single() },
                ),
                TestParams(
                    name = "method",
                    getter = { codebase.assertClass("test.pkg.Foo").methods().single() },
                ),
                TestParams(
                    name = "field",
                    getter = { codebase.assertClass("test.pkg.Foo").fields().single() },
                ),
                TestParams(
                    name = "property",
                    getter = { codebase.assertClass("test.pkg.Foo").properties().single() },
                ),
                TestParams(
                    name = "nested class",
                    getter = { codebase.assertClass("test.pkg.Foo.Nested") },
                ),
            )
    }

    @Test
    fun `Test filters correctly`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                          ctor public Foo();
                          method public void method();
                          field public int field;
                          property public int property;
                      }
                      public class Foo.Nested {
                      }
                    }
                """
            ),
        ) {
            // Visit all the items, unfiltered.
            val unfilteredItems = buildSet {
                codebase.accept(NonFilteringDelegatingVisitor(CollateItems(this)))
            }

            // Get the test item.
            val testItem = testData.getter(this)

            // Make sure the test item is in the unfiltered list.
            assertWithMessage("when emit=true").that(unfilteredItems).contains(testItem)

            // Mark the item as not being emittable.
            testItem.emit = false

            // Visit all the emittable items which should not include the test item.
            val filteredItems = buildSet {
                codebase.accept(EmittableDelegatingVisitor(CollateItems(this)))
            }

            // Make sure the test item is NOT in the filtered list.
            assertWithMessage("when emit=false").that(filteredItems).doesNotContain(testItem)
        }
    }

    private class CollateItems(private val collection: MutableCollection<SelectableItem>) :
        DelegatedVisitor {
        override fun visitPackage(pkg: PackageItem) {
            collection.add(pkg)
        }

        override fun visitClass(cls: ClassItem) {
            collection.add(cls)
        }

        override fun visitConstructor(constructor: ConstructorItem) {
            collection.add(constructor)
        }

        override fun visitMethod(method: MethodItem) {
            collection.add(method)
        }

        override fun visitField(field: FieldItem) {
            collection.add(field)
        }

        override fun visitProperty(property: PropertyItem) {
            collection.add(property)
        }
    }
}
