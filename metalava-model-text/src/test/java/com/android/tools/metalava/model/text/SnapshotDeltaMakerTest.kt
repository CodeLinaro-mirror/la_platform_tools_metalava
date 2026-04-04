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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Tests [SnapshotDeltaMaker] by round tripping base, extends and combined signature files.
 *
 * This also tests [ApiFile]s ability to combine base and extends signature files.
 */
class SnapshotDeltaMakerTest : BaseTextCodebaseTest() {

    @Parameterized.Parameter(0) lateinit var testData: TestParams

    data class TestParams(
        val name: String,
        val baseSignature: String,
        val extendsSignature: String,
        val combinedSignature: String,
        val checkMemberItemEquivalence: Boolean = false,
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
                    name = "class",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Extends {
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                              }
                              public class Extends {
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "class - super class type",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class BaseSuper {
                              }
                              public class Foo extends test.pkg.BaseSuper {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class ExtendsSuper extends test.pkg.BaseSuper {
                              }
                              public class Foo extends test.pkg.ExtendsSuper {
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class BaseSuper {
                              }
                              public class ExtendsSuper extends test.pkg.BaseSuper {
                              }
                              public class Foo extends test.pkg.ExtendsSuper {
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "class - interface types",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface BaseInterface {
                              }
                              public class Foo implements test.pkg.BaseInterface {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface ExtendsInterface extends test.pkg.BaseInterface {
                              }
                              public class Foo implements test.pkg.ExtendsInterface {
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface BaseInterface {
                              }
                              public interface ExtendsInterface extends test.pkg.BaseInterface {
                              }
                              public class Foo implements test.pkg.ExtendsInterface {
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "class - different annotations",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              @test.pkg.BaseAnnotation public class Foo {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface ExtendsAnnotation {
                              }
                              @test.pkg.BaseAnnotation @test.pkg.ExtendsAnnotation public class Foo {
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public @interface ExtendsAnnotation {
                              }
                              @test.pkg.BaseAnnotation @test.pkg.ExtendsAnnotation public class Foo {
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "class - changed to typealias",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public typealias Foo = String;
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public typealias Foo = String;
                            }
                        """,
                ),
                TestParams(
                    name = "constructors",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base();
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base(int);
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                ctor public Base();
                                ctor public Base(int);
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "methods",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                method public void baseMethod();
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                method public void extendsMethod();
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                method public void baseMethod();
                                method public void extendsMethod();
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "properties",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                property public int baseProperty;
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                property public int extendsProperty;
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                property public int baseProperty;
                                property public int extendsProperty;
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "fields",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                field public int baseField;
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                field public int extendsField;
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                                field public int baseField;
                                field public int extendsField;
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "nested",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base.Nested {
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Base {
                              }
                              public class Base.Nested {
                              }
                            }
                        """,
                ),
                TestParams(
                    name = "property annotations",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public class Foo {
                                property @test.pkg.BaseAnnotation public int foo;
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                property @test.pkg.ExtendsAnnotation public int foo;
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                property @test.pkg.ExtendsAnnotation public int foo;
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "property modifiers",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                property @Deprecated public int changeDeprecatedFrom;
                                property public int changeDeprecatedTo;
                                property public final int changeFinal;
                                property public int changeInline;
                                property protected int changeVisibility;
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                property public int changeDeprecatedFrom;
                                property @Deprecated public int changeDeprecatedTo;
                                property public int changeFinal;
                                property public inline int changeInline;
                                property public int changeVisibility;
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                property public int changeDeprecatedFrom;
                                property @Deprecated public int changeDeprecatedTo;
                                property public int changeFinal;
                                property public inline int changeInline;
                                property public int changeVisibility;
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "method annotations",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public class Foo {
                                method @test.pkg.BaseAnnotation public void foo();
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                method @test.pkg.ExtendsAnnotation public void foo();
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                method @test.pkg.ExtendsAnnotation public void foo();
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "method modifiers",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method @Deprecated public void changeDeprecatedFrom();
                                method public void changeDeprecatedTo();
                                method public void changeInfix();
                                method public void changeInline();
                                method public void changeOperator();
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void changeDeprecatedFrom();
                                method @Deprecated public void changeDeprecatedTo();
                                method public infix void changeInfix();
                                method public inline void changeInline();
                                method public operator void changeOperator();
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void changeDeprecatedFrom();
                                method @Deprecated public void changeDeprecatedTo();
                                method public infix void changeInfix();
                                method public inline void changeInline();
                                method public operator void changeOperator();
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "constructor annotations",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public class Foo {
                                ctor @test.pkg.BaseAnnotation public Foo();
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                ctor @test.pkg.ExtendsAnnotation public Foo();
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                ctor @test.pkg.ExtendsAnnotation public Foo();
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "constructor modifiers",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor protected Foo();
                                ctor @Deprecated public Foo(int);
                                ctor public Foo(String);
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                ctor public Foo(int);
                                ctor @Deprecated public Foo(String);
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                ctor public Foo(int);
                                ctor @Deprecated public Foo(String);
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "callable parameter annotations",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public class Foo {
                                ctor public Foo(@test.pkg.BaseAnnotation int);
                                method public void foo(@test.pkg.BaseAnnotation int);
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                ctor public Foo(@test.pkg.ExtendsAnnotation int);
                                method public void foo(@test.pkg.ExtendsAnnotation int);
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public @interface BaseAnnotation {
                              }
                              public @interface ExtendsAnnotation {
                              }
                              public class Foo {
                                ctor public Foo(@test.pkg.ExtendsAnnotation int);
                                method public void foo(@test.pkg.ExtendsAnnotation int);
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
                TestParams(
                    name = "type parameter reified",
                    baseSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public inline <reified T> void changeMethodReified(T);
                                property public inline <reified T> int T.changePropertyReified;
                              }
                            }
                        """,
                    extendsSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public inline <T> void changeMethodReified(T);
                                property public inline <T> int T.changePropertyReified;
                              }
                            }
                        """,
                    combinedSignature =
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public inline <T> void changeMethodReified(T);
                                property public inline <T> int T.changePropertyReified;
                              }
                            }
                        """,
                    checkMemberItemEquivalence = true,
                ),
            )
    }

    private fun Codebase.assertSignatureFile(expected: String, message: String? = null) {
        val trimmedExpected = expected.trimIndent()
        val output = writeSignatureFile(this)

        assertEquals(trimmedExpected, output, message)
    }

    private fun writeSignatureFile(deltaCodebase: Codebase): String {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { printWriter ->
            val fileFormat = FileFormat.V2
            val signatureWriter =
                SignatureWriter(
                    writer = printWriter,
                    fileFormat = fileFormat,
                )
            val deltaFragment =
                createCodebaseFragmentForSignatureFile(
                    deltaCodebase,
                    fileFormat,
                    ApiType.ALL,
                    preFiltered = true,
                    showUnannotated = true,
                    apiPredicateConfig = ApiPredicate.Config()
                )
            deltaFragment.accept(signatureWriter)
        }
        val output = stringWriter.toString().replace("\n\n", "\n").trimEnd()
        return output
    }

    /**
     * Check that merging [TestParams.baseSignature] and [TestParams.extendsSignature] together will
     * result in [TestParams.combinedSignature].
     */
    private fun checkMergedCodebase(baseFile: SignatureFile) {
        val extendsFile =
            SignatureFile.fromText("extends.txt", contents = testData.extendsSignature)
        val mergedCodebase = ApiFile.parseApi(listOf(baseFile, extendsFile))
        mergedCodebase.assertSignatureFile(
            expected = testData.combinedSignature,
            message = "merged signature"
        )
    }

    /**
     * Check that computing the delta between [TestParams.baseSignature] and
     * [TestParams.combinedSignature] will result in [TestParams.extendsSignature].
     */
    private fun checkDeltaCodebase(baseFile: SignatureFile) {
        val baseCodebase = ApiFile.parseApi(listOf(baseFile))
        val combinedCodebase =
            ApiFile.parseApi(
                listOf(
                    SignatureFile.fromText("combined.txt", contents = testData.combinedSignature)
                )
            )
        val deltaCodebase =
            SnapshotDeltaMaker.createDelta(
                    baseCodebase,
                    CodebaseFragment.create(
                        combinedCodebase,
                        factory = ::NonFilteringDelegatingVisitor,
                    ),
                    testData.checkMemberItemEquivalence,
                )
                .codebase
        deltaCodebase.assertSignatureFile(
            expected = testData.extendsSignature,
            message = "delta signature"
        )
    }

    @Test
    fun `Round trip`() {
        val baseFile = SignatureFile.fromText("base.txt", contents = testData.baseSignature)
        checkMergedCodebase(baseFile)

        checkDeltaCodebase(baseFile)
    }
}
