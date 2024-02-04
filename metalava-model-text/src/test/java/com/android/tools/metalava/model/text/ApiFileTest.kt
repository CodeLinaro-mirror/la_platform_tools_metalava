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

package com.android.tools.metalava.model.text

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ApiFileTest : BaseTextCodebaseTest() {

    @Test
    fun `Test mixture of kotlinStyleNulls settings`() {
        val exception =
            assertThrows(ApiParseException::class.java) {
                runCodebaseTest(
                    inputSet(
                        signature(
                            "file1.txt",
                            """
                                // Signature format: 5.0
                                // - kotlin-style-nulls=yes
                                package test.pkg {
                                    public class Foo {
                                        method void foo(Object);
                                    }
                                }
                            """
                        ),
                        signature(
                            "file2.txt",
                            """
                                // Signature format: 5.0
                                // - kotlin-style-nulls=no
                                package test.pkg {
                                    public class Bar {
                                        method void bar(Object);
                                    }
                                }
                            """
                        )
                    ),
                ) {}
            }

        assertThat(exception.message)
            .contains("Cannot mix signature files with different settings of kotlinStyleNulls")
    }

    @Test
    fun `Test parse from InputStream`() {
        val fileName = "test-api.txt"
        val codebase =
            javaClass.getResourceAsStream(fileName)!!.use { inputStream ->
                ApiFile.parseApi(fileName, inputStream)
            }
        codebase.assertClass("test.pkg.Foo")
    }

    @Test
    fun `Test known Throwable`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package java.lang {
                        public class Throwable {
                        }
                    }
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws java.lang.Throwable;
                        }
                    }
                """
            ),
        ) {
            val throwable = codebase.assertClass("java.lang.Throwable")

            // Get the super class to force it to be loaded.
            val throwableSuperClass = throwable.superClass()

            // Now get the object class.
            val objectClass = codebase.assertClass("java.lang.Object")

            assertSame(objectClass, throwableSuperClass)

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(throwable, exception.classItem)
        }
    }

    @Test
    fun `Test known Throwable subclass`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package java.lang {
                        public class Error extends java.lang.Throwable {
                        }
                    }
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws java.lang.Error;
                        }
                    }
                """
            ),
        ) {
            val error = codebase.assertClass("java.lang.Error")

            // Get the super class to force it to be loaded.
            val errorSuperClass = error.superClassType()?.asClass()

            // Now get the throwable class.
            val throwable = codebase.assertClass("java.lang.Throwable")

            assertSame(throwable, errorSuperClass)

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(error, exception.classItem)
        }
    }

    @Test
    fun `Test unknown Throwable`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws java.lang.Throwable;
                        }
                    }
                """
            ),
        ) {
            val throwable = codebase.assertClass("java.lang.Throwable")
            // This should probably be Object.
            assertNull(throwable.superClass())

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(throwable, exception.classItem)
        }
    }

    @Test
    fun `Test unknown Throwable subclass`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws other.UnknownException;
                        }
                    }
                """
            ),
        ) {
            val throwable = codebase.assertClass("java.lang.Throwable")
            val unknownExceptionClass = codebase.assertClass("other.UnknownException")
            // Make sure the stub UnknownException is initialized correctly.
            assertSame(throwable, unknownExceptionClass.superClass())

            // Make sure the stub UnknownException is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(unknownExceptionClass, exception.classItem)
        }
    }

    @Test
    fun `Test unknown custom exception from other codebase`() {
        val testClassResolver =
            TestClassResolver.create(
                "other.UnknownException",
                "java.lang.Throwable",
            )
        val codebase =
            ApiFile.parseApi(
                "api.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws other.UnknownException;
                        }
                    }
                """
                    .trimIndent(),
                classResolver = testClassResolver,
            )

        val unknownExceptionClass = testClassResolver.resolveClass("other.UnknownException")!!

        // Make sure the UnknownException retrieved from the other codebase is used in the throws
        // types.
        val exception =
            codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
        assertSame(unknownExceptionClass, exception.classItem)
    }

    @Test
    fun `Test parse multiple files correctly updates super class`() {
        val testFiles =
            listOf(
                signature(
                    "first.txt",
                    """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                        }
                    }
                """
                ),
                signature(
                    "second.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Bar {
                            }
                            public class Foo extends test.pkg.Bar {
                            }
                        }
                    """
                ),
                signature(
                    "third.txt",
                    """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Bar {
                        }
                        public class Baz {
                        }
                        public class Foo extends test.pkg.Baz {
                        }
                    }
                """
                ),
            )

        fun checkSuperClass(files: List<TestFile>, order: String, expectedSuperClass: String) {
            runCodebaseTest(
                inputSet(files),
            ) {
                val fooClass = codebase.assertClass("test.pkg.Foo")
                assertSame(
                    codebase.assertClass(expectedSuperClass),
                    fooClass.superClass(),
                    message = "incorrect super class from $order"
                )
            }
        }

        // Order matters, the last, non-null super class wins.
        checkSuperClass(testFiles, "narrowest to widest", "test.pkg.Baz")
        checkSuperClass(testFiles.reversed(), "widest to narrowest", "test.pkg.Bar")
    }

    @Test
    fun `Test matching package annotations are allowed`() {
        runCodebaseTest(
            inputSet(
                signature(
                    "file1.txt",
                    """
                        // Signature format: 2.0
                        package @PackageAnnotation test.pkg {
                            public class Foo {
                            }
                        }
                    """
                ),
                signature(
                    "file2.txt",
                    """
                        // Signature format: 2.0
                        package @PackageAnnotation test.pkg {
                            public class Foo {
                            }
                        }
                    """
                ),
            ),
        ) {}
    }

    @Test
    fun `Test different package annotations are not allowed`() {
        val exception =
            assertThrows(ApiParseException::class.java) {
                runCodebaseTest(
                    inputSet(
                        signature(
                            "file1.txt",
                            """
                        // Signature format: 2.0
                        package @PackageAnnotation1 test.pkg {
                            public class Foo {
                            }
                        }
                    """
                        ),
                        signature(
                            "file2.txt",
                            """
                        // Signature format: 2.0
                        package @PackageAnnotation2 test.pkg {
                            public class Foo {
                            }
                        }
                    """
                        ),
                    ),
                ) {}
            }
        assertThat(exception.message).contains("Contradicting declaration of package test.pkg")
    }

    /** Dump the package structure of [codebase] to a string for easy comparison. */
    private fun dumpPackageStructure(codebase: Codebase) = buildString {
        codebase.getPackages().packages.map { packageItem ->
            append("${packageItem.qualifiedName()}\n")
            for (classItem in packageItem.allClasses()) {
                append("    ${classItem.qualifiedName()}\n")
            }
        }
    }

    /** Check that the package structure created from the [sources] matches what is expected. */
    private fun checkPackageStructureCreatedCorrectly(vararg sources: TestFile) {
        runCodebaseTest(
            inputSet(*sources),
        ) {
            val data = dumpPackageStructure(codebase)

            assertEquals(
                """
                        test.pkg
                            test.pkg.Outer
                            test.pkg.Outer.Middle
                            test.pkg.Outer.Middle.Inner
                    """
                    .trimIndent(),
                data.trimEnd()
            )
        }
    }

    @Test
    fun `Test missing all containing classes`() {
        checkPackageStructureCreatedCorrectly(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer.Middle.Inner {
                        }
                    }
                """
            ),
        )
    }

    @Test
    fun `Test missing outer class`() {
        checkPackageStructureCreatedCorrectly(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer.Middle {
                        }
                        public class Outer.Middle.Inner {
                        }
                    }
                """
            ),
        )
    }

    @Test
    fun `Test missing middle class`() {
        checkPackageStructureCreatedCorrectly(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer {
                        }
                        public class Outer.Middle.Inner {
                        }
                    }
                """
            ),
        )
    }

    @Test
    fun `Test split across multiple files, middle missing`() {
        checkPackageStructureCreatedCorrectly(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer {
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer.Middle.Inner {
                        }
                    }
                """
            ),
        )
    }

    @Test
    fun `Test split across multiple files`() {
        checkPackageStructureCreatedCorrectly(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer {
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer.Middle {
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Outer.Middle.Inner {
                        }
                    }
                """
            ),
        )
    }

    @Test
    fun testTypeParameterNames() {
        assertThat(ApiFile.extractTypeParameterBoundsStringList(null).toString()).isEqualTo("[]")
        assertThat(ApiFile.extractTypeParameterBoundsStringList("").toString()).isEqualTo("[]")
        assertThat(ApiFile.extractTypeParameterBoundsStringList("X").toString()).isEqualTo("[]")
        assertThat(ApiFile.extractTypeParameterBoundsStringList("DEF extends T").toString())
            .isEqualTo("[T]")
        assertThat(
                ApiFile.extractTypeParameterBoundsStringList(
                        "T extends java.lang.Comparable<? super T>"
                    )
                    .toString()
            )
            .isEqualTo("[java.lang.Comparable<? super T>]")
        assertThat(
                ApiFile.extractTypeParameterBoundsStringList(
                        "T extends java.util.List<Number> & java.util.RandomAccess"
                    )
                    .toString()
            )
            .isEqualTo("[java.util.List<Number>, java.util.RandomAccess]")
    }

    class TestClassItem private constructor(delegate: ClassItem) : ClassItem by delegate {
        companion object {
            fun create(name: String): TestClassItem {
                val codebase =
                    ApiFile.parseApi("other.txt", "// Signature format: 2.0") as TextCodebase
                val delegate = codebase.getOrCreateClass(name)
                return TestClassItem(delegate)
            }
        }
    }

    class TestClassResolver(val map: Map<String, ClassItem>) : ClassResolver {
        override fun resolveClass(erasedName: String): ClassItem? = map[erasedName]

        companion object {
            fun create(vararg names: String): ClassResolver {
                return TestClassResolver(
                    names.map { TestClassItem.create(it) }.associateBy { it.qualifiedName() }
                )
            }
        }
    }
}
