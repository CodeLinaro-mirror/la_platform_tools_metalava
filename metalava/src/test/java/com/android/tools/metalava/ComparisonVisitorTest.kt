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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiSurfacePredicate
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.testing.BaseTemporaryFolderOwner
import com.android.tools.metalava.testing.signature
import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonVisitorTest : BaseTemporaryFolderOwner(), Assertions {
    @Test
    fun `prefer first's real children even when first is only implied`() {
        val newSignatureFiles =
            listOf(
                SignatureFile.fromText(
                    "first.txt",
                    """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer.Inner {
                                method public pkg.TypeInFirst foobar();
                            }
                            public class TypeInFirst {
                            }
                        }
                    """
                ),
                SignatureFile.fromText(
                    "second.txt",
                    """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer {
                            }
                            public class Outer.Inner {
                                method public pkg.TypeInSecond foobar();
                            }
                            public class TypeInSecond {
                            }
                        }
                    """
                ),
            )
        val new =
            MergedCodebase(
                // Parse each signature file on its own.
                newSignatureFiles.map { ApiFile.parseApi(listOf(it)) }
            )

        val oldSignatureFile =
            SignatureFile.fromText(
                "old.txt",
                """
                    // Signature format: 2.0
                    package pkg {
                        public class Outer {
                        }
                        public class Outer.Inner {
                        }
                    }
                """
            )
        val old = MergedCodebase(listOf(ApiFile.parseApi(listOf(oldSignatureFile))))
        var methodType: String? = null
        CodebaseComparator.compare(
            object : ComparisonVisitor() {
                override fun addedMethodItem(new: MethodItem) {
                    methodType = new.type().toSimpleTypeString()
                }
            },
            old,
            new
        )
        assertEquals("pkg.TypeInFirst", methodType)
    }

    @Test
    fun `Test make sure that method with emit=false is ignored during comparison`() {

        fun TestFile.readCodebase(): Codebase {
            val signatureFiles = SignatureFile.fromFiles(toFile())
            return ApiFile.parseApi(signatureFiles, Codebase.Config.NOOP)
        }

        val signatureFile =
            signature(
                "old.txt",
                """
                        // Signature format: 2.0
                        package test.pkg {
                            public class Foo {
                                method public void foo();
                            }
                        }
                    """
            )

        // Create a codebase with a method that has emit = false
        val oldCodebase =
            signatureFile.readCodebase().apply {
                // Mark the method as emit=false
                assertClass("test.pkg.Foo").methods().forEach { it.emit = false }
            }

        // Create an identical codebase except that the method has emit = true, the default.
        val newCodebase = signatureFile.readCodebase()

        // Compare the two.
        val differences = mutableListOf<String>()
        CodebaseComparator.compare(
            object : ComparisonVisitor() {
                override fun compareMethodItems(old: MethodItem, new: MethodItem) {
                    differences += "$old was changed"
                }

                override fun addedMethodItem(new: MethodItem) {
                    differences += "$new was added"
                }

                override fun removedMethodItem(old: MethodItem, from: ClassItem) {
                    differences += "$old was removed"
                }
            },
            oldCodebase,
            newCodebase
        )
        // TODO(b/347885819): The method should be treated as being added not changed.
        assertEquals("method test.pkg.Foo.foo() was changed", differences.joinToString("\n"))
    }

    @Test
    fun `Comparing multiplatform codebases`() {
        fun parseMultiplatform(vararg files: TestFile): MultiplatformCodebase {
            val signatureFiles = SignatureFile.fromFiles(files.map { it.toFile() })
            return ApiFile.parseMultiplatformApi(signatureFiles, Codebase.Config.NOOP)
        }

        val oldCodebase =
            parseMultiplatform(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Common {
                        method public void changed();
                        method public void removed();
                      }
                    }
                    """
                ),
                signature(
                    "androidMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Android {
                      }
                    }
                    """
                ),
            )

        val newCodebase =
            parseMultiplatform(
                signature(
                    "commonMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Common {
                        method public void added();
                        method @test.pkg.Anno public void changed();
                      }
                    }
                    """
                ),
                signature(
                    "nativeMain.txt",
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Native {
                      }
                    }
                    """
                )
            )

        var differences = mutableListOf<String>()
        CodebaseComparator.compareMultiplatform(
            object : ComparisonVisitor() {
                override fun addedCodebase(new: Codebase) {
                    differences += "$new was added"
                }

                override fun removedCodebase(old: Codebase) {
                    differences += "$old was removed"
                }

                override fun compareMethodItems(old: MethodItem, new: MethodItem) {
                    differences += "$old was changed"
                }

                override fun addedMethodItem(new: MethodItem) {
                    differences += "$new was added"
                }

                override fun removedMethodItem(old: MethodItem, from: ClassItem) {
                    differences += "$old was removed"
                }
            },
            old = oldCodebase,
            new = newCodebase,
        )

        assertEquals(
            """
            Codebase for source set androidMain was removed
            Codebase for source set nativeMain was added
            method test.pkg.Common.added() was added
            method test.pkg.Common.changed() was changed
            method test.pkg.Common.removed() was removed
            """
                .trimIndent(),
            differences.sorted().joinToString("\n")
        )
    }

    /**
     * Reads a [Codebase] from a [base] and [current] signature file using [apiSurfaces].
     *
     * The [current] file is treated as the main API surface delta, extending the [base] surface.
     */
    private fun readCodebase(
        apiSurfaces: ApiSurfaces,
        base: TestFile,
        current: TestFile,
    ): Codebase {
        val signatureFiles =
            SignatureFile.fromFiles(
                listOf(base.toFile(), current.toFile()),
                forMainApiSurfacePredicate = { _, file -> file.name.endsWith("-current.txt") },
            )
        return ApiFile.parseApi(
            signatureFiles,
            codebaseConfig = Codebase.Config(apiSurfaces = apiSurfaces),
        )
    }

    @Test
    fun `Test surfaceFilter filters remaining items in old and new`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = true)

        val oldCodebase =
            readCodebase(
                apiSurfaces,
                base =
                    signature(
                        "old-base.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                                public class Foo {
                                    method public void bar();
                                    method public void baseMethod();
                                }
                            }
                        """
                    ),
                current =
                    signature(
                        "old-current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                                public class Foo {
                                    method public void bar();
                                }
                            }
                        """
                    ),
            )

        val newCodebase =
            readCodebase(
                apiSurfaces,
                base =
                    signature(
                        "new-base.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                    method public void baseMethod();
                                }
                                public class Foo {
                                    method public void bar();
                                }
                            }
                        """
                    ),
                current =
                    signature(
                        "new-current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                                public class Foo {
                                    method public void bar();
                                }
                            }
                        """
                    ),
            )

        val surfaceFilter =
            ApiSurfacePredicate.forDelta(
                ApiType.CORE,
                apiSurfaces.main,
            )
        val referenceFilter =
            ApiSurfacePredicate.referenceFilter(
                ApiType.CORE,
                apiSurfaces.main,
            )

        val differences = mutableListOf<String>()
        CodebaseComparator.compare(
            object : ComparisonVisitor() {
                override fun addedMethodItem(new: MethodItem) {
                    differences += "$new was added"
                }

                override fun removedMethodItem(old: MethodItem, from: ClassItem) {
                    differences += "$old was removed"
                }
            },
            old = oldCodebase,
            new = newCodebase,
            surfaceFilter = surfaceFilter,
            referenceFilter = referenceFilter,
        )

        assertEquals("", differences.sorted().joinToString("\n"))
    }

    @Test
    fun `Test items moved to base surface are not reported as removed`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = true)

        val oldCodebase =
            readCodebase(
                apiSurfaces,
                base =
                    signature(
                        "old-base.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                            }
                        """
                    ),
                current =
                    signature(
                        "old-current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                                public class Foo {
                                    method public void foo();
                                }
                            }
                        """
                    ),
            )

        val newCodebase =
            readCodebase(
                apiSurfaces,
                base =
                    signature(
                        "new-base.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                                public class Foo {
                                    method public void foo();
                                }
                            }
                        """
                    ),
                current =
                    signature(
                        "new-current.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Bar {
                                    method public void bar();
                                }
                            }
                        """
                    ),
            )

        val surfaceFilter =
            ApiSurfacePredicate.forDelta(
                ApiType.CORE,
                apiSurfaces.main,
            )
        val referenceFilter =
            ApiSurfacePredicate.referenceFilter(
                ApiType.CORE,
                apiSurfaces.main,
            )

        val differences = mutableListOf<String>()
        CodebaseComparator.compare(
            object : ComparisonVisitor() {
                override fun addedClassItem(new: ClassItem) {
                    differences += "$new was added"
                }

                override fun removedClassItem(old: ClassItem, from: SelectableItem) {
                    differences += "$old was removed"
                }

                override fun addedMethodItem(new: MethodItem) {
                    differences += "$new was added"
                }

                override fun removedMethodItem(old: MethodItem, from: ClassItem) {
                    differences += "$old was removed"
                }
            },
            old = oldCodebase,
            new = newCodebase,
            surfaceFilter = surfaceFilter,
            referenceFilter = referenceFilter,
        )

        assertEquals("", differences.sorted().joinToString("\n"))
    }
}
