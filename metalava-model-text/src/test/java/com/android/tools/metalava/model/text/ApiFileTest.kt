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
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.testing.getAndroidJar
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiFileTest : BaseTextCodebaseTest() {

    @Test
    fun `Test mixture of kotlinStyleNulls settings`() {
        runSignatureTest(
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
            ),
        ) {
            assertThat(reportedIssues)
                .isEqualTo(
                    "MAIN_SRC/file2.txt:1: error: Preceding file MAIN_SRC/file1.txt has different setting of kotlin-style-nulls which may cause issues [SignatureFileError]"
                )

            codebase.assertClass("test.pkg.Foo")
            codebase.assertClass("test.pkg.Bar")
        }
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
        runSignatureTest(
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
            val objectClass = codebase.assertClass("java.lang.Object", expectedEmit = false)

            assertSame(objectClass, throwableSuperClass)

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(throwable, exception.erasedClass)
        }
    }

    @Test
    fun `Test known Throwable subclass`() {
        runSignatureTest(
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
            val throwable = codebase.assertClass("java.lang.Throwable", expectedEmit = false)

            assertSame(throwable, errorSuperClass)

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(error, exception.erasedClass)
        }
    }

    @Test
    fun `Test unknown Throwable`() {
        runSignatureTest(
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
            val throwable = codebase.assertResolvedClass("java.lang.Throwable")
            // This should probably be Object.
            assertNull(throwable.superClass())

            // Make sure the stub Throwable is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(throwable, exception.erasedClass)
        }
    }

    @Test
    fun `Test unknown Throwable subclass`() {
        runSignatureTest(
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
            val throwable = codebase.assertResolvedClass("java.lang.Throwable")

            val exceptionType =
                codebase.assertClass("test.pkg.Foo").methods().single().throwsTypes().single()

            // Force the unknown exception class to be resolved, creating a stub in the process. It
            // is checked below.
            exceptionType.erasedClass

            val unknownExceptionClass =
                codebase.assertClass("other.UnknownException", expectedEmit = false)
            // Make sure the stub UnknownException is initialized correctly.
            assertSame(throwable, unknownExceptionClass.superClass())

            // Make sure the stub UnknownException is used in the throws types.
            val exception =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
            assertSame(unknownExceptionClass, exception.erasedClass)
        }
    }

    @Test
    fun `Test unknown custom exception from other codebase`() {
        val testClassResolver =
            TestClassResolver.create(
                "other.UnknownException",
                "java.lang.Throwable",
            )
        val signatureFile =
            SignatureFile.fromText(
                "api.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                            method public void foo() throws other.UnknownException;
                        }
                    }
                """
            )

        val codebase =
            ApiFile.parseApi(
                listOf(signatureFile),
                classResolver = testClassResolver,
            )

        val unknownExceptionClass = testClassResolver.resolveClass("other.UnknownException")!!

        // Make sure the UnknownException retrieved from the other codebase is used in the throws
        // types.
        val exception =
            codebase.assertClass("test.pkg.Foo").assertMethod("foo", "").throwsTypes().first()
        assertSame(unknownExceptionClass, exception.erasedClass)
    }

    @Test
    fun `Test matching package annotations are allowed`() {
        runSignatureTest(
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
        ) {}
    }

    @Test
    fun `Test different package annotations are not allowed`() {
        val exception =
            assertThrows(ApiParseException::class.java) {
                runSignatureTest(
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
                ) {}
            }
        assertThat(exception.message).contains("Contradicting declaration of package test.pkg")
    }

    /** Dump the package structure of [codebase] to a string for easy comparison. */
    private fun dumpPackageStructure(codebase: Codebase) = buildString {
        for (packageItem in codebase.getPackages().packages) {
            // Ignore packages that will not be emitted.
            if (!packageItem.emit) continue
            append("${packageItem.qualifiedName().let {if (it == "") "<root>" else it}}\n")
            for (classItem in packageItem.allClasses()) {
                append("    ${classItem.qualifiedName()}\n")
            }
        }
    }

    /** Check that the package structure created from the [sources] matches what is expected. */
    private fun checkPackageStructureCreatedCorrectly(vararg sources: TestFile) {
        runSignatureTest(*sources) {
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
    fun `Test unknown interface should still be marked as such`() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo implements test.unknown.Interface {
                        }
                    }
                """
            ),
        ) {
            // Resolve the class. Even though it does not exist, the text model will fabricate an
            // instance.
            val unknownInterfaceClass =
                codebase.assertClass("test.pkg.Foo").interfaceTypes().single().asClass()
            assertNotNull(unknownInterfaceClass)

            // Make sure that the fabricated instance is of the correct structure.
            assertThat(unknownInterfaceClass.classKind).isEqualTo(ClassKind.INTERFACE)
        }
    }

    @Test
    fun `Test type parser issues - kotlin-style-nulls=no`() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public abstract class Foo implements Comparable<? blah1> {
                            field public static final int? FIELD1 = 0;
                            method public void foo(Comparable<test.pkg.Foo>blah2);
                            field public static final int? FIELD2 = 0;
                        }
                        public abstract class Bar implements Comparable<? blah1> {
                        }
                    }
                """
            ),
        ) {
            assertThat(reportedIssues)
                .isEqualTo(
                    """
                        MAIN_SRC/api.txt:3: error: Type starts with "?" but doesn't appear to be wildcard: ? blah1 [SignatureFileError]
                        MAIN_SRC/api.txt:4: error: Format does not support Kotlin-style null type syntax: int? [SignatureFileError]
                        MAIN_SRC/api.txt:4: error: Invalid nullability suffix on primitive: int? [SignatureFileError]
                        MAIN_SRC/api.txt:5: error: Could not parse type `Comparable<test.pkg.Foo>blah2`. Found unexpected string after type parameters: blah2 [SignatureFileError]
                        MAIN_SRC/api.txt:6: error: Format does not support Kotlin-style null type syntax: int? [SignatureFileError]
                        MAIN_SRC/api.txt:6: error: Invalid nullability suffix on primitive: int? [SignatureFileError]
                        MAIN_SRC/api.txt:8: error: Type starts with "?" but doesn't appear to be wildcard: ? blah1 [SignatureFileError]
                    """
                        .trimIndent()
                )

            val fooClass = codebase.assertClass("test.pkg.Foo")
            val barClass = codebase.assertClass("test.pkg.Bar")

            // Implements lists should drop blah1 and be an unbounded wildcard.
            assertThat(fooClass.interfaceTypes().map { it.toString() })
                .isEqualTo(listOf("java.lang.Comparable<?>"))
            assertThat(barClass.interfaceTypes().map { it.toString() })
                .isEqualTo(listOf("java.lang.Comparable<?>"))

            // The type of Foo.FIELD1 should just be `int`.
            assertThat(fooClass.assertField("FIELD1").type().toString()).isEqualTo("int")
        }
    }

    @Test
    fun `Test type parser issues - kotlin-style-nulls=yes`() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 3.0
                    package test.pkg {
                        public abstract class Foo {
                            field public static final int? FIELD1 = 0;
                        }
                    }
                """
            ),
        ) {
            assertThat(reportedIssues)
                .isEqualTo(
                    "MAIN_SRC/api.txt:4: error: Invalid nullability suffix on primitive: int? [SignatureFileError]"
                )

            val fooClass = codebase.assertClass("test.pkg.Foo")

            // The type of FIELD1 should just be `int`.
            assertThat(fooClass.assertField("FIELD1").type().toString()).isEqualTo("int")
        }
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

    @Test
    fun `Test for main API surface`() {
        val testFiles =
            listOf(
                signature(
                    "not-current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Foo {
                                ctor public Foo();
                                method public void method(int notCurrent);
                                method public void extensibleMethod(int parameter) throws Throwable;
                                field public int field;
                            }
                            public class Outer {
                            }
                            public class Outer.Middle {
                            }
                            public class Outer.Middle.Inner {
                            }
                        }
                    """
                ),
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Foo {
                                ctor public Foo(int currentCtorParameter);
                                method public void extensibleMethod(int parameter) throws Exception;
                                method public void currentMethod(int currentMethodParameter);
                                field public int currentField;
                            }
                            public class Outer.Middle.Inner {
                                method public void currentInnerMethod();
                            }
                        }
                    """
                )
            )

        val files = testFiles.map { it.createFile(temporaryFolder.newFolder()) }
        val signatureFiles =
            SignatureFile.fromFiles(
                files,
                forMainApiSurfacePredicate = { _, file -> file.name == "current.txt" },
            )

        val apiSurfaces = ApiSurfaces.create(needsBase = true)
        val codebaseConfig =
            Codebase.Config(
                annotationManager = noOpAnnotationManager,
                apiSurfaces = apiSurfaces,
            )
        val classResolver = ClassLoaderBasedClassResolver(listOf(getAndroidJar()))
        val codebase =
            ApiFile.parseApi(
                signatureFiles,
                codebaseConfig = codebaseConfig,
                classResolver = classResolver,
            )

        val current = buildList {
            codebase.accept(
                object : BaseItemVisitor(visitParameterItems = false) {
                    override fun visitSelectableItem(item: SelectableItem) {
                        if (item.emit) {
                            add(item)
                        }
                    }
                }
            )
        }

        assertEquals(
            """
                package test.pkg
                class test.pkg.Foo
                constructor test.pkg.Foo.Foo(int)
                method test.pkg.Foo.extensibleMethod(int)
                method test.pkg.Foo.currentMethod(int)
                field Foo.currentField
                class test.pkg.Outer.Middle.Inner
                method test.pkg.Outer.Middle.Inner.currentInnerMethod()
            """
                .trimIndent(),
            current.joinToString("\n")
        )
    }

    class TestClassItem private constructor(delegate: ClassItem) : ClassItem by delegate {
        companion object {
            fun create(name: String): TestClassItem {
                val signatureFile = SignatureFile.fromText("other.txt", "// Signature format: 2.0")
                val codebase = ApiFile.parseApi(listOf(signatureFile))
                val delegate = codebase.resolveClass(name)!!
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
