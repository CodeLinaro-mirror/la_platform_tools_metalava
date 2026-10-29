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

package com.android.tools.metalava.model.testsuite.visitors

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

@SupportedInputFormats(InputFormat.JAVA)
class CommonBaseItemVisitorTest : BaseModelTest() {

    /**
     * Traverses this [Codebase] using a [BaseItemVisitor] configured with [orderClassesByName] and
     * [preserveClassNesting], and produces a textual representation of the visited [ClassItem]s
     * with indentation reflecting the visit hierarchy.
     */
    private fun Codebase.dump(
        orderClassesByName: Boolean,
        preserveClassNesting: Boolean,
    ): String {
        val dumper = SelectableItemDumper()
        accept(
            object :
                BaseItemVisitor(
                    preserveClassNesting = preserveClassNesting,
                    orderClassesByName = orderClassesByName,
                ) {
                override fun visitClass(cls: ClassItem) {
                    dumper.visitSelectableItem(cls)
                }

                override fun afterVisitClass(cls: ClassItem) {
                    dumper.afterVisitSelectableItem()
                }
            }
        )
        return dumper.toString()
    }

    /**
     * Java sources with top-level and nested classes declared in non-alphabetical order to verify
     * that [BaseItemVisitor.orderClassesByName] orders classes by name rather than declaration
     * order.
     */
    private val javaSources: List<TestFile> =
        listOf(
            java(
                """
                    package test.pkg;

                    public class Zebra {
                        public class InnerZebra {}
                        public class InnerAlpha {}
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Alpha {}
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Beta {}
                """
            ),
        )

    @Test
    fun `test not sorted, preserving class nesting`() {
        runCodebaseTest(inputSet(javaSources)) {
            val dumped =
                codebase.dump(
                    orderClassesByName = false,
                    preserveClassNesting = true,
                )
            assertEquals(
                """
                    class test.pkg.Zebra
                      class test.pkg.Zebra.InnerZebra
                      class test.pkg.Zebra.InnerAlpha
                    class test.pkg.Alpha
                    class test.pkg.Beta
                """
                    .trimIndent(),
                dumped.trim(),
            )
        }
    }

    @Test
    fun `test sorted, preserving class nesting`() {
        runCodebaseTest(inputSet(javaSources)) {
            val dumped =
                codebase.dump(
                    orderClassesByName = true,
                    preserveClassNesting = true,
                )
            assertEquals(
                """
                    class test.pkg.Alpha
                    class test.pkg.Beta
                    class test.pkg.Zebra
                      class test.pkg.Zebra.InnerAlpha
                      class test.pkg.Zebra.InnerZebra
                """
                    .trimIndent(),
                dumped.trim(),
            )
        }
    }

    @Test
    fun `test not sorted, not preserving class nesting`() {
        runCodebaseTest(inputSet(javaSources)) {
            val dumped =
                codebase.dump(
                    orderClassesByName = false,
                    preserveClassNesting = false,
                )
            assertEquals(
                """
                    class test.pkg.Zebra
                    class test.pkg.Zebra.InnerZebra
                    class test.pkg.Zebra.InnerAlpha
                    class test.pkg.Alpha
                    class test.pkg.Beta
                """
                    .trimIndent(),
                dumped.trim(),
            )
        }
    }

    @Test
    fun `test sorted, not preserving class nesting`() {
        runCodebaseTest(inputSet(javaSources)) {
            val dumped =
                codebase.dump(
                    orderClassesByName = true,
                    preserveClassNesting = false,
                )
            assertEquals(
                """
                    class test.pkg.Alpha
                    class test.pkg.Beta
                    class test.pkg.Zebra
                    class test.pkg.Zebra.InnerAlpha
                    class test.pkg.Zebra.InnerZebra
                """
                    .trimIndent(),
                dumped.trim(),
            )
        }
    }
}
