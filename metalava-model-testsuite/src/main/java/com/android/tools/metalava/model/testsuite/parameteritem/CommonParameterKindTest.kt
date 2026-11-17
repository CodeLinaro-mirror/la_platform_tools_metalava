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

package com.android.tools.metalava.model.testsuite.parameteritem

import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Common tests for usages of [ParameterKind]. */
class CommonParameterKindTest : BaseModelTest() {
    @Test
    fun `Kind for value parameters on function`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public class Foo {
                    public void foo(String s, int i) {}
                }
                """
            ),
            kotlin(
                """
                package test.pkg
                class Foo {
                    fun foo(s: String, i: Int) = Unit
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    method public void foo(String s, int i);
                  }
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("java.lang.String", "int"))
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.VALUE)
            assertThat(fooMethod.parameters()[1].kind).isEqualTo(ParameterKind.VALUE)
        }
    }

    @Test
    fun `Kind for value parameters on constructor`() {
        runCodebaseTest(
            java(
                """
                package test.pkg;
                public class Foo {
                    public Foo(String s, int i) {}
                }
                """
            ),
            kotlin(
                """
                package test.pkg
                class Foo(s: String, i: Int)
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    ctor public Foo(String s, int i);
                  }
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooCtor = fooClass.assertConstructor(listOf("java.lang.String", "int"))
            assertThat(fooCtor.parameters()[0].kind).isEqualTo(ParameterKind.VALUE)
            assertThat(fooCtor.parameters()[1].kind).isEqualTo(ParameterKind.VALUE)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Kind for receiver parameters`() {
        runCodebaseTest(
            kotlin(
                """
                @file:JvmName("Foo")
                package test.pkg
                fun String.foo() = Unit
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public static void foo(receiver String);
                  }
                }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("java.lang.String"))
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.RECEIVER)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Kind for receiver parameters on kotlin-only function`() {
        runCodebaseTest(
            kotlin(
                """
                @file:JvmName("Foo")
                package test.pkg
                @JvmInline value class IntValue(val value: Int)
                // This function is kotlin-only because of the value class receiver type
                fun IntValue.foo() = Unit
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("test.pkg.IntValue"))
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.RECEIVER)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Kind for continuation parameter`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    suspend fun foo() = Unit
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public suspend Object? foo(kotlin.coroutines.Continuation<? super kotlin.Unit>);
                  }
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod =
                fooClass.assertMethod(
                    "foo",
                    listOf("kotlin.coroutines.Continuation<? super kotlin.Unit>")
                )
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.CONTINUATION)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Kind for continuation parameter on kotlin-only function`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline value class IntValue(val value: Int) {
                    // This function is kotlin-only because it is defined in a value class
                    suspend fun foo() = Unit
                }
                """
            ),
        ) {
            val intValueClass = codebase.assertClass("test.pkg.IntValue")
            val fooMethod =
                intValueClass.assertMethod(
                    "foo",
                    listOf("kotlin.coroutines.Continuation<? super java.lang.Void>")
                )
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.CONTINUATION)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Kind for context parameter on function`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    context(s: String)
                    fun foo() = Unit
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public void foo(context String s);
                  }
                }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("java.lang.String"))
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.CONTEXT)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Kind for context parameter on kotlin-only function`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline value class IntValue(val value: Int)
                class Foo {
                    // This function is kotlin-only because of the value class context type
                    context(iv: IntValue)
                    fun foo() = Unit
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method @KotlinOnly public void foo(context test.pkg.IntValue iv);
                    method @BytecodeOnly public void foo-Vxmw0xk(int);
                  }
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method @BytecodeOnly public static test.pkg.IntValue! box-impl(int);
                    method @BytecodeOnly public static int constructor-impl(int);
                    method @InaccessibleFromKotlin public int getValue();
                    method @BytecodeOnly public int unbox-impl();
                    property public int value;
                  }
                }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("test.pkg.IntValue"))
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.CONTEXT)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Kind for parameters on function with all kinds of parameters`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                context(c: String)
                suspend fun Int.foo(b: Boolean) = Unit
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    method public suspend Object? foo(context String c, receiver int, boolean b, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                  }
                }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod =
                fooClass.assertMethod(
                    "foo",
                    listOf(
                        "java.lang.String",
                        "int",
                        "boolean",
                        "kotlin.coroutines.Continuation<? super kotlin.Unit>"
                    )
                )
            assertThat(fooMethod.parameters()[0].kind).isEqualTo(ParameterKind.CONTEXT)
            assertThat(fooMethod.parameters()[1].kind).isEqualTo(ParameterKind.RECEIVER)
            assertThat(fooMethod.parameters()[2].kind).isEqualTo(ParameterKind.VALUE)
            assertThat(fooMethod.parameters()[3].kind).isEqualTo(ParameterKind.CONTINUATION)
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN, InputFormat.SIGNATURE)
    @Test
    fun `Kind for context parameter on property`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                class Foo {
                    context(s: String)
                    val foo: Int
                        get() = 0
                }
                """
            ),
            signature(
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public int foo(context String s);
                  }
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooVal =
                fooClass.assertProperty(
                    "foo",
                    contextParameterTypeStrings = listOf("java.lang.String")
                )
            assertThat(fooVal.contextParameters[0].kind).isEqualTo(ParameterKind.CONTEXT)
        }
    }
}
