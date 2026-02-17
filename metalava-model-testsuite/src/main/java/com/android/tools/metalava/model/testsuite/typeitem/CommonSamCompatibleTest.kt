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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonSamCompatibleTest : BaseModelTest() {
    @Test
    fun `Primitive types are not SAM-compatible`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public interface Foo {
                    void foo(int i, boolean b, float f);
                }
                """
            ),
            kotlin(
                """
                package test.pkg
                interface Foo {
                    fun foo(i: Int, b: Boolean, f: Float)
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    method public void foo(int, boolean, float);
                  }
                }
                """
            )
        ) {
            val fooMethod =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod("foo", listOf("int", "boolean", "float"))
            for (parameter in fooMethod.parameters()) {
                assertThat(parameter.type().isSamCompatibleOrKotlinLambda(codebase)).isFalse()
            }
            assertThat(fooMethod.returnType().isSamCompatibleOrKotlinLambda(codebase)).isFalse()
        }
    }

    @Test
    fun `Kotlin lambda types are SAM-compatible`() {
        runCodebaseTest(
            // No java source since this test is for kotlin types
            kotlin(
                """
                package test.pkg
                interface Foo {
                    fun foo(f0: () -> Unit, f1: (Int) -> Int, f2: (Int, String) -> String)
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    method public void foo(kotlin.jvm.functions.Function0<kotlin.Unit> f0, kotlin.jvm.functions.Function1<? super java.lang.Integer,java.lang.Integer> f1, kotlin.jvm.functions.Function2<? super java.lang.Integer,? super java.lang.String,java.lang.String> f2);
                  }
                }
                """
            )
        ) {
            val fooMethod =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod(
                        "foo",
                        listOf(
                            "kotlin.jvm.functions.Function0<kotlin.Unit>",
                            "kotlin.jvm.functions.Function1<? super java.lang.Integer,java.lang.Integer>",
                            "kotlin.jvm.functions.Function2<? super java.lang.Integer,? super java.lang.String,java.lang.String>"
                        )
                    )
            for (parameter in fooMethod.parameters()) {
                assertThat(parameter.type().isSamCompatibleOrKotlinLambda(codebase)).isTrue()
            }
        }
    }

    @Test
    fun `Kotlin suspend lambda types are SAM-compatible`() {
        runCodebaseTest(
            // No java source since this test is for kotlin types
            kotlin(
                """
                package test.pkg
                interface Foo {
                    fun foo(f0: suspend () -> Unit, f1: suspend (Int) -> Int, f2: suspend (Int, String) -> String)
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    method public void foo(kotlin.jvm.functions.Function1<? super kotlin.coroutines.Continuation<? super kotlin.Unit>,? extends java.lang.Object?> f0, kotlin.jvm.functions.Function2<? super java.lang.Integer,? super kotlin.coroutines.Continuation<? super java.lang.Integer>,? extends java.lang.Object?> f1, kotlin.jvm.functions.Function3<? super java.lang.Integer,? super java.lang.String,? super kotlin.coroutines.Continuation<? super java.lang.String>,? extends java.lang.Object?> f2);
                  }
                }
                """
            )
        ) {
            val fooMethod =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod(
                        "foo",
                        listOf(
                            "kotlin.jvm.functions.Function1<? super kotlin.coroutines.Continuation<? super kotlin.Unit>,?>",
                            "kotlin.jvm.functions.Function2<? super java.lang.Integer,? super kotlin.coroutines.Continuation<? super java.lang.Integer>,?>",
                            "kotlin.jvm.functions.Function3<? super java.lang.Integer,? super java.lang.String,? super kotlin.coroutines.Continuation<? super java.lang.String>,?>"
                        )
                    )
            for (parameter in fooMethod.parameters()) {
                assertThat(parameter.type().isSamCompatibleOrKotlinLambda(codebase)).isTrue()
            }
        }
    }

    @Test
    fun `Java SAM interface types are SAM-compatible`() {
        val samInterfaceDefinition =
            java(
                """
                package test.pkg;
                public interface SamInterface {
                    void invoke();
                }
                """
            )
        runCodebaseTest(
            inputSet(
                samInterfaceDefinition,
                java(
                    """
                    package test.pkg;
                    public interface Foo {
                        void foo(SamInterface samInterface);
                    }
                    """
                )
            ),
            inputSet(
                samInterfaceDefinition,
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        fun foo(samInterface: SamInterface)
                    }
                    """
                )
            ),
            inputSet(
                signature(
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public interface Foo {
                        method public void foo(test.pkg.SamInterface samInterface);
                      }
                      public interface SamInterface {
                        method public void invoke();
                      }
                    }
                    """
                )
            )
        ) {
            val fooParameter =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod("foo", listOf("test.pkg.SamInterface"))
                    .parameters()
                    .single()
            assertThat(fooParameter.type().isSamCompatibleOrKotlinLambda(codebase)).isTrue()
        }
    }

    @Test
    fun `Kotlin SAM interface types are not SAM-compatible`() {
        val samInterfaceDefinition =
            kotlin(
                """
                package test.pkg
                interface SamInterface {
                    fun invoke()
                }
                """
            )
        runCodebaseTest(
            inputSet(
                samInterfaceDefinition,
                java(
                    """
                    package test.pkg;
                    public interface Foo {
                        void foo(SamInterface samInterface);
                    }
                    """
                )
            ),
            inputSet(
                samInterfaceDefinition,
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        fun foo(samInterface: SamInterface)
                    }
                    """
                )
            ),
            // No signature input: it isn't possible to tell in the signature file if the source was
            // Java or Kotlin.
        ) {
            val fooParameter =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod("foo", listOf("test.pkg.SamInterface"))
                    .parameters()
                    .single()
            assertThat(fooParameter.type().isSamCompatibleOrKotlinLambda(codebase)).isFalse()
        }
    }

    @Test
    fun `Kotlin fun interface types are SAM-compatible`() {
        val funInterfaceDefinition =
            kotlin(
                """
                package test.pkg
                fun interface FunInterface {
                    fun invoke()
                }
                """
            )
        runCodebaseTest(
            inputSet(
                funInterfaceDefinition,
                java(
                    """
                    package test.pkg;
                    public interface Foo {
                        void foo(FunInterface funInterface);
                    }
                    """
                )
            ),
            inputSet(
                funInterfaceDefinition,
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        fun foo(funInterface: FunInterface)
                    }
                    """
                )
            ),
            inputSet(
                signature(
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public interface Foo {
                        method public void foo(test.pkg.FunInterface funInterface);
                      }
                      public interface FunInterface {
                        method public void invoke();
                      }
                    }
                    """
                )
            )
        ) {
            val fooParameter =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod("foo", listOf("test.pkg.FunInterface"))
                    .parameters()
                    .single()
            assertThat(fooParameter.type().isSamCompatibleOrKotlinLambda(codebase)).isTrue()
        }
    }

    @Test
    fun `Variable type with function bound is SAM-compatible`() {
        runCodebaseTest(
            // No java source since this test is for kotlin types
            kotlin(
                """
                package test.pkg
                interface Foo {
                    fun <T: () -> Unit> foo(t: T)
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    method public <T extends kotlin.jvm.functions.Function0<? extends kotlin.Unit>> void foo(T t);
                  }
                }
                """
            )
        ) {
            val fooParameter =
                codebase
                    .assertClass("test.pkg.Foo")
                    .assertMethod("foo", listOf("T"))
                    .parameters()
                    .single()
            assertThat(fooParameter.type().isSamCompatibleOrKotlinLambda(codebase)).isTrue()
        }
    }

    @Test
    fun `Variable types with non-function SAM-compatible bound is not SAM-compatible`() {
        val funInterfaceDefinition =
            kotlin(
                """
                package test.pkg
                fun interface FunInterface {
                    fun invoke()
                }
                """
            )
        val samInterfaceDefinition =
            java(
                """
                package test.pkg;
                public interface SamInterface {
                    void invoke();
                }
                """
            )
        runCodebaseTest(
            inputSet(
                funInterfaceDefinition,
                samInterfaceDefinition,
                java(
                    """
                    package test.pkg;
                    public interface Foo {
                        <T1 extends FunInterface, T2 extends SamInterface> void foo(T1 funInterface, T2 samInterface);
                    }
                    """
                )
            ),
            inputSet(
                funInterfaceDefinition,
                samInterfaceDefinition,
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        fun <T1: FunInterface, T2: SamInterface> foo(funInterface: T1, samInterface: T2)
                    }
                    """
                )
            ),
            inputSet(
                signature(
                    """
                    // Signature format: 5.0
                    package test.pkg {
                      public interface Foo {
                        method public <T1 extends test.pkg.FunInterface, T2 extends test.pkg.SamInterface> void foo(T1 funInterface, T2 samInterface);
                      }
                      public fun interface FunInterface {
                        method public void invoke();
                      }
                      public interface SamInterface {
                        method public void invoke();
                      }
                    }
                    """
                )
            )
        ) {
            val fooMethod =
                codebase.assertClass("test.pkg.Foo").assertMethod("foo", listOf("T1", "T2"))
            for (parameter in fooMethod.parameters()) {
                assertThat(parameter.type().isSamCompatibleOrKotlinLambda(codebase)).isFalse()
            }
        }
    }
}
