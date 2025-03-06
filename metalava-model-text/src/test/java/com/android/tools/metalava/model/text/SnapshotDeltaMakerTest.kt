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
            val visitor =
                createFilteringVisitorForSignatures(
                    signatureWriter,
                    fileFormat,
                    ApiType.ALL,
                    preFiltered = true,
                    showUnannotated = true,
                    apiPredicateConfig = ApiPredicate.Config()
                )
            deltaCodebase.accept(visitor)
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
                    CodebaseFragment.create(combinedCodebase, ::NonFilteringDelegatingVisitor)
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
