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

package com.android.tools.metalava.model.text

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/** Tests for [ClassMerger]. */
class ClassMergerTest : BaseTextCodebaseTest() {

    @Test
    fun `Test classes split across multiple files`() {
        runSignatureTest(
            signature(
                "current.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo {
                        }
                    }
                """
            ),
            signature(
                "system.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        @test.pkg.Anno(12) public class Foo {
                        }

                        public @interface Anno {
                            method public int value();
                        }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertEquals(
                listOf("@test.pkg.Anno(12)"),
                fooClass.modifiers.annotations().map { it.toString() },
            )

            val annoClass = codebase.assertClass("test.pkg.Anno")
            assertEquals(ClassKind.ANNOTATION_TYPE, annoClass.classKind)
        }
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
            runSignatureTest(*files.toTypedArray()) {
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
    fun `Test parse multiple files correctly updates interfaces`() {
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
                            public interface Bar {
                            }
                            public class Foo implements test.pkg.Bar {
                            }
                        }
                    """
                ),
                signature(
                    "third.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                            public interface Bar {
                            }
                            public interface Baz {
                            }
                            public class Foo implements test.pkg.Baz {
                            }
                        }
                    """
                ),
            )

        fun checkInterfaces(files: List<TestFile>, order: String, expectedInterface: String) {
            runSignatureTest(*files.toTypedArray()) {
                val fooClass = codebase.assertClass("test.pkg.Foo")
                assertEquals(
                    listOf(expectedInterface),
                    fooClass.interfaceTypes().map { it.toTypeString() },
                    message = "interfaces from $order"
                )
            }
        }

        // Order matters, the last non-empty interface list wins.
        checkInterfaces(testFiles, "narrowest to widest", "test.pkg.Baz")
        checkInterfaces(testFiles.reversed(), "widest to narrowest", "test.pkg.Bar")
    }

    @Test
    fun `Test parse multiple files merges annotations when allowClassModifierChanges is false`() {
        runSignatureTest(
            signature(
                "first.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public @interface Anno1 {
                        }
                        @test.pkg.Anno1 public class Foo {
                        }
                    }
                """
            ),
            signature(
                "second.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public @interface Anno2 {
                        }
                        @test.pkg.Anno2 public class Foo {
                        }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertEquals(
                listOf("test.pkg.Anno1", "test.pkg.Anno2"),
                fooClass.annotationNames(),
            )
        }
    }

    @Test
    fun `Test parse multiple files replaces annotations and modifiers when allowClassModifierChanges is true`() {
        val codebase =
            ApiFile.parseApi(
                listOf(
                    SignatureFile.fromText(
                        "first.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public @interface Anno1 {
                                }
                                @test.pkg.Anno1 public final class Foo {
                                }
                            }
                        """
                            .trimIndent()
                    ),
                    SignatureFile.fromText(
                        "second.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public @interface Anno2 {
                                }
                                @test.pkg.Anno2 public abstract class Foo {
                                }
                            }
                        """
                            .trimIndent()
                    ),
                ),
                allowClassModifierChanges = true,
            )

        val fooClass = codebase.assertClass("test.pkg.Foo")
        assertEquals(listOf("test.pkg.Anno2"), fooClass.annotationNames())
        assertFalse(fooClass.modifiers.isFinal())
        assertTrue(fooClass.modifiers.isAbstract())
    }

    @Test
    fun `Test parse multiple files transitions class to typealias`() {
        runSignatureTest(
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
                        public typealias Foo = String;
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertTypeAlias("test.pkg.Foo")
            assertEquals("java.lang.String", fooClass.optionalAliasedType?.toTypeString())
        }
    }

    @Test
    fun `Test incompatible class modifiers throws exception when allowClassModifierChanges is false`() {
        val exception =
            assertFailsWith<ApiParseException> {
                runSignatureTest(
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
                                public final class Foo {
                                }
                            }
                        """
                    ),
                ) {}
            }

        assertContains(
            exception.message.orEmpty(),
            Regex(""".*\Q/second.txt:3: Incompatible class test.pkg.Foo definitions\E"""),
        )
    }

    @Test
    fun `Test incompatible class kind throws exception`() {
        val exception =
            assertFailsWith<ApiParseException> {
                runSignatureTest(
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
                                public interface Foo {
                                }
                            }
                        """
                    ),
                ) {}
            }

        assertContains(
            exception.message.orEmpty(),
            Regex(""".*\Q/second.txt:3: Incompatible class test.pkg.Foo definitions\E"""),
        )
    }

    @Test
    fun `Test class from CLASS_PATH re-defined in COMMAND_LINE is promoted to COMMAND_LINE`() {
        val codebase =
            ApiFile.parseApi(
                listOf(
                    SignatureFile.fromText(
                        "classpath.txt",
                        """
                            // Signature format: 2.0
                            package test.classpath {
                                public class ClassPathOnly {
                                }
                            }
                            package test.pkg {
                                public class Foo {
                                    method public void baseMethod();
                                }
                            }
                        """
                            .trimIndent(),
                        classOrigin = ClassOrigin.CLASS_PATH,
                    ),
                    SignatureFile.fromText(
                        "source.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Foo {
                                    method public void extraMethod();
                                }
                            }
                        """
                            .trimIndent(),
                        classOrigin = ClassOrigin.COMMAND_LINE,
                    ),
                )
            )

        // Classes that only appear in the CLASS_PATH file must not be promoted.
        val classPathOnly =
            codebase.assertClass("test.classpath.ClassPathOnly", expectedEmit = false)
        assertEquals(ClassOrigin.CLASS_PATH, classPathOnly.origin)
        assertFalse(classPathOnly.containingPackage().emit)

        // Classes re-defined in the COMMAND_LINE file must be promoted to COMMAND_LINE and emitted
        // with members from both files.
        val fooClass = codebase.assertClass("test.pkg.Foo", expectedEmit = true)
        assertEquals(ClassOrigin.COMMAND_LINE, fooClass.origin)
        assertTrue(fooClass.containingPackage().emit)
        assertEquals(
            listOf("baseMethod", "extraMethod"),
            fooClass.methods().map { it.name() },
        )
    }

    @Test
    fun `Test class from CLASS_PATH re-defined in CLASS_PATH remains CLASS_PATH`() {
        val codebase =
            ApiFile.parseApi(
                listOf(
                    SignatureFile.fromText(
                        "classpath1.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Foo {
                                    method public void firstMethod();
                                }
                            }
                        """
                            .trimIndent(),
                        classOrigin = ClassOrigin.CLASS_PATH,
                    ),
                    SignatureFile.fromText(
                        "classpath2.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                                public class Foo {
                                    method public void secondMethod();
                                }
                            }
                        """
                            .trimIndent(),
                        classOrigin = ClassOrigin.CLASS_PATH,
                    ),
                )
            )

        val fooClass = codebase.assertClass("test.pkg.Foo", expectedEmit = false)
        assertEquals(ClassOrigin.CLASS_PATH, fooClass.origin)
        assertFalse(fooClass.containingPackage().emit)
        assertEquals(
            listOf("firstMethod", "secondMethod"),
            fooClass.methods().map { it.name() },
        )
    }
}
