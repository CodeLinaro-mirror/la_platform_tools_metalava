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

package com.android.tools.metalava.model.testsuite.scope

import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for [ReferencableNameScope] implementations. */
class CommonParameterizedReferencableNameScopeTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        val imports: String = "",
        /** Getter for the [ReferencableNameScope] from which the name will be resolved. */
        val scopeGetter: CodebaseContext.() -> ReferencableNameScope,
        /** The name to resolve. */
        val referencableName: String,
        /**
         * Getter for the expected [ReferencableItem] to which [referencableName] name will be
         * resolved.
         */
        val expectedItemGetter: CodebaseContext.() -> ReferencableItem?,
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

    companion object : Assertions {
        private val params =
            listOf(
                // PackageItem related tests.
                TestParams(
                    name = "PackageItem - implicit String",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "String",
                    expectedItemGetter = { codebase.assertResolvedClass("java.lang.String") }
                ),
                TestParams(
                    name = "PackageItem - not relative package",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "annotation",
                    expectedItemGetter = { null }
                ),
                TestParams(
                    name = "PackageItem - absolute class",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "java.io.IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "PackageItem - absolute package",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "java.util",
                    expectedItemGetter = { codebase.assertResolvedPackage("java.util") }
                ),
                // SourceFile related tests.
                TestParams(
                    name = "SourceFile - absolute class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "java.io.IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "SourceFile - resolve imported",
                    imports =
                        """
                            import java.io.IOException;
                        """,
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "SourceFile - resolve static imported",
                    imports =
                        """
                            import static java.util.Map.Entry;
                        """,
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "Entry",
                    expectedItemGetter = { codebase.assertResolvedClass("java.util.Map.Entry") }
                ),
                TestParams(
                    name = "SourceFile - resolve class in same file",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "Hidden",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Hidden") }
                ),
                TestParams(
                    name = "SourceFile - resolve class in same package",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "Other",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Other") }
                ),
                // ClassItem related tests.
                TestParams(
                    name = "ClassItem - absolute class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").sourceFile()!! },
                    referencableName = "java.io.IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "ClassItem - resolve imported",
                    imports =
                        """
                            import java.io.IOException;
                        """,
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "ClassItem - resolve class in same package",
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "Other",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Other") }
                ),
                TestParams(
                    name = "ClassItem - resolve self",
                    scopeGetter = { codebase.assertResolvedClass("java.util.Map") },
                    referencableName = "Map",
                    expectedItemGetter = { codebase.assertResolvedClass("java.util.Map") }
                ),
                // ClassItem - Nested classes
                TestParams(
                    name = "ClassItem - resolve nested class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "Nested",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Test.Nested") }
                ),
                TestParams(
                    name = "ClassItem - resolve qualified nested class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "Test.Nested",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Test.Nested") }
                ),
                TestParams(
                    name = "ClassItem - nested class resolve outer",
                    scopeGetter = { codebase.assertClass("test.pkg.Test.Nested") },
                    referencableName = "Test",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Test") }
                ),
                TestParams(
                    name = "ClassItem - nested class resolve absolute class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test.Nested") },
                    referencableName = "java.io.IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                // ClassItem - TypeParameters
                TestParams(
                    name = "ClassItem - TypeParameter - O",
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "O",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test").assertTypeParameter("O")
                    }
                ),
                TestParams(
                    name = "ClassItem - TypeParameter - S",
                    scopeGetter = { codebase.assertClass("test.pkg.Test") },
                    referencableName = "S",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test").assertTypeParameter("S")
                    }
                ),
                TestParams(
                    name = "ClassItem - nested access own TypeParameter - N",
                    scopeGetter = { codebase.assertClass("test.pkg.Test.Nested") },
                    referencableName = "N",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test.Nested").assertTypeParameter("N")
                    }
                ),
                TestParams(
                    name = "ClassItem - nested access outer class TypeParameter - O",
                    scopeGetter = { codebase.assertClass("test.pkg.Test.Nested") },
                    referencableName = "O",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test").assertTypeParameter("O")
                    }
                ),
                TestParams(
                    name = "ClassItem - nested access shadowing TypeParameter - S",
                    scopeGetter = { codebase.assertClass("test.pkg.Test.Nested") },
                    referencableName = "S",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test.Nested").assertTypeParameter("S")
                    }
                ),

                // ConstructorItem related tests.
                TestParams(
                    name = "ConstructorItem - resolve nested class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").constructors().single() },
                    referencableName = "Nested",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Test.Nested") }
                ),
                TestParams(
                    name = "ConstructorItem - access TypeParameter - C",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").constructors().single() },
                    referencableName = "C",
                    expectedItemGetter = {
                        codebase
                            .assertClass("test.pkg.Test")
                            .constructors()
                            .single()
                            .assertTypeParameter("C")
                    }
                ),
                TestParams(
                    name = "ConstructorItem - access outer class TypeParameter - O",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").constructors().single() },
                    referencableName = "O",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test").assertTypeParameter("O")
                    }
                ),
                TestParams(
                    name = "ConstructorItem - access shadowing TypeParameter - S",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").constructors().single() },
                    referencableName = "S",
                    expectedItemGetter = {
                        codebase
                            .assertClass("test.pkg.Test")
                            .constructors()
                            .single()
                            .assertTypeParameter("S")
                    }
                ),

                // MethodItem related tests.
                TestParams(
                    name = "MethodItem - resolve nested class",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").methods().single() },
                    referencableName = "Nested",
                    expectedItemGetter = { codebase.assertClass("test.pkg.Test.Nested") }
                ),
                TestParams(
                    name = "MethodItem - access TypeParameter - M",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").methods().single() },
                    referencableName = "M",
                    expectedItemGetter = {
                        codebase
                            .assertClass("test.pkg.Test")
                            .methods()
                            .single()
                            .assertTypeParameter("M")
                    }
                ),
                TestParams(
                    name = "MethodItem - access outer class TypeParameter - O",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").methods().single() },
                    referencableName = "O",
                    expectedItemGetter = {
                        codebase.assertClass("test.pkg.Test").assertTypeParameter("O")
                    }
                ),
                TestParams(
                    name = "MethodItem - access shadowing TypeParameter - S",
                    scopeGetter = { codebase.assertClass("test.pkg.Test").methods().single() },
                    referencableName = "S",
                    expectedItemGetter = {
                        codebase
                            .assertClass("test.pkg.Test")
                            .methods()
                            .single()
                            .assertTypeParameter("S")
                    }
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test resolve`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        ${params.imports.trimIndent()}
                        public class Test<O,S> {
                            public class Nested<N,S> {
                            }

                            public <C, S> Test(C m, S s) {}

                            public <M, S> void method(M m, S s) {}
                        }

                        public class Hidden {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Other {}
                    """
                ),
            ),
        ) {
            val scopeGetter = params.scopeGetter
            val scope = scopeGetter()

            val resolvedItem = scope.resolveReferencableItem(params.referencableName)

            // Defer getting the expected Item until after resolving to ensure that resolving works
            // even on packages and classes that are not in the sources which have not yet been
            // resolved.
            val expectedItemGetter = params.expectedItemGetter
            val expectedItem = expectedItemGetter()

            assertSame(expectedItem, resolvedItem)
        }
    }
}
