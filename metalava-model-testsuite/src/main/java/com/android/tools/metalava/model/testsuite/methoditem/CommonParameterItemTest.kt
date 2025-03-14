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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Common tests for implementations of [ParameterItem]. */
class CommonParameterItemTest : BaseModelTest() {

    @Test
    fun `Test deprecated parameter by annotation`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(@Deprecated int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(@Deprecated int i) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        fun foo(@Deprecated i: Int) {}
                    }
                """
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            val annotation = parameterItem.modifiers.annotations().single()
            if (inputFormat == InputFormat.KOTLIN) {
                assertEquals("kotlin.Deprecated", annotation.qualifiedName)
            } else {
                assertEquals("java.lang.Deprecated", annotation.qualifiedName)
            }
            assertEquals("originallyDeprecated", true, parameterItem.originallyDeprecated)
            assertEquals("effectivelyDeprecated", true, parameterItem.effectivelyDeprecated)
        }
    }

    @Test
    fun `Test not deprecated parameter`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(int i) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Bar {
                        fun foo(i: Int) {}
                    }
                """
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertEquals("originallyDeprecated", false, parameterItem.originallyDeprecated)
            assertEquals("effectivelyDeprecated", false, parameterItem.effectivelyDeprecated)
        }
    }

    @Test
    fun `Test publicName reports correct name when specified`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Bar {
                            method public void foo(int baz);
                          }
                        }
                    """
                ),
            ),
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        class Bar {
                            fun foo(baz: Int) {}
                        }
                    """
                ),
            ),
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertEquals("name()", "baz", parameterItem.name())
            assertEquals("publicName()", "baz", parameterItem.publicName())
        }
    }

    @Test
    fun `Test publicName reports correct name when not specified`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Bar {
                        method public void foo(int);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Bar {
                        public void foo(int baz) {}
                    }
                """
            ),
            // Kotlin treats all parameter names as public.
        ) {
            val parameterItem =
                codebase.assertClass("test.pkg.Bar").methods().single().parameters().single()
            assertNull("publicName()", parameterItem.publicName())
        }
    }

    @Test
    fun `Test publicName reports correct name when called on binary class - Object#equals`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public abstract class Bar {
                    }
                """
            ),
            // No need to check any other sources as the source is not being tested, only used to
            // trigger the test run.
        ) {
            val parameterItem =
                codebase
                    .assertResolvedClass("java.lang.Object")
                    .assertMethod("equals", "java.lang.Object")
                    .parameters()
                    .single()
            // The parameter name of the Object.equals(Object obj) method is stored in the Object
            // class but `publicName()` should still return null because the parameter name is not
            // part of the API of java classes.
            assertEquals("name()", "obj", parameterItem.name())
            assertNull("publicName()", parameterItem.publicName())
        }
    }

    @Test
    fun `Test publicName reports correct name when called on binary class - ViewGroup#onLayout`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    public abstract class Bar extends android.view.ViewGroup {
                    }
                """
            ),
            // No need to check any other sources as the source is not being tested, only used to
            // trigger the test run.
        ) {
            val parameterItems =
                codebase
                    .assertResolvedClass("android.view.ViewGroup")
                    .assertMethod("onLayout", "boolean, int, int, int, int")
                    .parameters()
            // For some reason ViewGroup.onLayout(boolean, int, int, int, int) does not provide the
            // actual parameter name. Probably, because it was compiled with an older version of
            // javac, and/or without the appropriate options to record the parameter name.
            val expectedNames = listOf("p", "p1", "p2", "p3", "p4")
            assertEquals("parameter count", parameterItems.size, expectedNames.size)
            for (i in parameterItems.indices) {
                val parameterItem = parameterItems[i]
                val expectedName = expectedNames[i]
                assertEquals("$i:name()", expectedName, parameterItem.name())
                assertNull("$i:publicName()$parameterItem", parameterItem.publicName())
            }
        }
    }

    @Test
    fun `Test nullability of parameter annotated with @not-type-use-NonNull`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.notTypeUseNonNullSource,
                java(
                    """
                        package test.pkg;
                        import java.util.Map;
                        import not.type.use.NonNull;

                        public class Foo<T> {
                            public void method1(@NonNull String p);
                            public void method2(@NonNull String[] p);
                            public void method3(@NonNull String[][] p);
                            public void method4(@NonNull T p);
                            public void method5(@NonNull Map.Entry<T, String> p);
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo<T> {
                            method public void method1(@NonNull String);
                            method public void method2(@NonNull String[]);
                            method public void method3(@NonNull String[][]);
                            method public void method4(@NonNull T);
                            method public void method5(@NonNull java.util.Map.Entry<T, String>);
                          }
                        }
                    """
                ),
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "method1" to "java.lang.String",
                    "method2" to "java.lang.String![]",
                    "method3" to "java.lang.String![]![]",
                    "method4" to "T",
                    "method5" to "java.util.Map.Entry<T!,java.lang.String!>",
                )
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val expectedType = expectedTypes[name]!!
                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = method.parameters().single().type()
                assertWithMessage(name)
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of parameter annotated with @not-type-use-Nullable`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.notTypeUseNullableSource,
                java(
                    """
                        package test.pkg;
                        import java.util.Map;
                        import not.type.use.Nullable;

                        public class Foo<T> {
                            public void method1(@Nullable String p);
                            public void method2(@Nullable String[] p);
                            public void method3(@Nullable String[][] p);
                            public void method4(@Nullable T p);
                            public void method5(@Nullable Map.Entry<T, String> p);
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo<T> {
                            method public void method1(@Nullable String);
                            method public void method2(@Nullable String[]);
                            method public void method3(@Nullable String[][]);
                            method public void method4(@Nullable T);
                            method public void method5(@Nullable java.util.Map.Entry<T, String>);
                          }
                        }
                    """
                ),
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "method1" to "java.lang.String?",
                    "method2" to "java.lang.String![]?",
                    "method3" to "java.lang.String![]![]?",
                    "method4" to "T?",
                    "method5" to "java.util.Map.Entry<T!,java.lang.String!>?",
                )
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val expectedType = expectedTypes[name]!!
                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = method.parameters().single().type()
                assertWithMessage(name)
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of non-Kotlin varargs`() {
        runCodebaseTest(
            inputSet(
                KnownSourceFiles.notTypeUseNonNullSource,
                KnownSourceFiles.notTypeUseNullableSource,
                java(
                    """
                        package test.pkg;
                        import not.type.use.NonNull;
                        import not.type.use.Nullable;

                        public class Foo {
                            public void nullable(@Nullable String... p);
                            public void nonNull(@NonNull String... p);
                            public void platform(String... p);
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void nullable(@Nullable String... p);
                            method public void nonNull(@NonNull String... p);
                            method public void platform(String... p);
                          }
                        }
                    """
                ),
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "nullable" to "java.lang.String!...?",
                    "nonNull" to "java.lang.String!...",
                    "platform" to "java.lang.String!...!",
                )
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val expectedType = expectedTypes[name]!!
                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = method.parameters().single().type()
                assertWithMessage(name)
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of Kotlin varargs last`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        fun nullable(vararg p: String?)
                        fun nonNull(vararg p: String)
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        method public void nullable(String?... p);
                        method public void nonNull(String... p);
                      }
                    }
                """
            ),
            // Kotlin does not care about different nullability annotations.
        ) {
            val expectedTypes =
                mapOf(
                    "nullable" to "java.lang.String?...",
                    "nonNull" to "java.lang.String...",
                )
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val parameterItem = method.parameters().single()

                // Make sure that it is modelled as a varargs parameter.
                assertWithMessage("$name isVarArgs").that(parameterItem.isVarArgs()).isTrue()

                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = parameterItem.type()
                val expectedType = expectedTypes[name]!!
                assertWithMessage("$name type")
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of Kotlin varargs not-last`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    fun nullable(vararg p: Any?, i: String = "") {}
                    fun nonNull(vararg p: Any, i: String = "") {}
                """
            ),
        ) {
            val expectedTypes =
                mapOf(
                    "nullable" to "java.lang.Object?[]",
                    "nonNull" to "java.lang.Object[]",
                )
            val methods = codebase.assertClass("test.pkg.TestKt").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val parameterItem = method.parameters().first()

                // Make sure that it is modelled as a varargs parameter.
                assertWithMessage("$name isVarArgs").that(parameterItem.isVarArgs()).isTrue()

                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = parameterItem.type()
                val expectedType = expectedTypes[name]!!
                assertWithMessage(name)
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test nullability of Kotlin varargs last in inline reified fun`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    inline fun <reified T> nullable(vararg elements: T?) = listOf(*elements)
                    inline fun <reified T> nonNull(vararg elements: T) = listOf(*elements)
                """
            ),
        ) {
            val expectedTypes =
                mapOf(
                    "nullable" to "T?...",
                    "nonNull" to "T...",
                )
            val methods = codebase.assertClass("test.pkg.TestKt").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val parameterItem = method.parameters().single()

                // Make sure that it is modelled as a varargs parameter.
                assertWithMessage("$name isVarArgs").that(parameterItem.isVarArgs()).isTrue()

                // Compare the kotlin style format of the parameter to ensure that only the
                // outermost type is affected by the not-type-use nullability annotation.
                val type = parameterItem.type()
                val expectedType = expectedTypes[name]!!
                assertWithMessage("$name type")
                    .that(type.testTypeString(kotlinStyleNulls = true))
                    .isEqualTo(expectedType)
            }
        }
    }

    @Test
    fun `Test parameter isVarArgs`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void varArgsMethod(String... p);
                        method public void nonVarArgsMethod(String[] p);
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public void varArgsMethod(String... p) {}
                        public void nonVarArgsMethod(String[] p) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        fun varArgsMethod(vararg p: String) {}
                        fun nonVarArgsMethod(p: Array<String>) {}
                    }
                """
            ),
        ) {
            val expectedTypes =
                mapOf(
                    "varArgsMethod" to true,
                    "nonVarArgsMethod" to false,
                )
            val methods = codebase.assertClass("test.pkg.Foo").methods()
            assertEquals("method count", expectedTypes.size, methods.size)
            for (method in methods) {
                val name = method.name()
                val parameterItem = method.parameters().single()

                // Make sure that it is modelled as a varargs parameter if expected
                val expectedVarArgs = expectedTypes[name]
                assertWithMessage("$name isVarArgs")
                    .that(parameterItem.isVarArgs())
                    .isEqualTo(expectedVarArgs)
            }
        }
    }

    @Test
    fun `Test no default value`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    // - language=kotlin
                    // - concise-default-values=no
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public method(s: String?): void;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Foo {
                        public void method(String s) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        fun method(s: String?) {}
                    }
                """
            ),
        ) {
            val parameter =
                codebase.assertClass("test.pkg.Foo").methods().single().parameters().single()
            assertEquals("hasDefaultValue", false, parameter.hasDefaultValue())
            assertEquals("isDefaultValueKnown", false, parameter.isDefaultValueKnown())
            // TODO: Improve consistency of the following.
            when (parameter.itemLanguage) {
                ItemLanguage.KOTLIN -> {
                    assertEquals(
                        "defaultValue",
                        "__invalid_value__",
                        parameter.defaultValueAsString()
                    )
                }
                else -> {
                    val exception =
                        assertThrows(IllegalStateException::class.java) {
                            parameter.defaultValueAsString()
                        }
                    assertEquals(
                        "defaultValue",
                        "cannot call on NONE DefaultValue",
                        exception.message
                    )
                }
            }
        }
    }

    @Test
    fun `Test null default value`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 5.0
                    // - language=kotlin
                    // - concise-default-values=no
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public method(s: String? = null): void;
                      }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg

                    class Foo {
                        fun method(s: String? = null) {}
                    }
                """
            ),
        ) {
            val parameter =
                codebase.assertClass("test.pkg.Foo").methods().single().parameters().single()
            assertEquals("hasDefaultValue", true, parameter.hasDefaultValue())
            assertEquals("isDefaultValueKnown", true, parameter.isDefaultValueKnown())
            assertEquals("defaultValue", "null", parameter.defaultValueAsString())
        }
    }

    @Test
    fun `Test unknown default value`() {
        runCodebaseTest(
            // Neither Kotlin nor Java has a way to specify an unknown default property. Kotlin and
            // Java both treat no default and unknown default as the same. Only signature file can
            // specify that it has a default but does not know what its value is. It does that by
            // using the `optional` pseudo modifier.
            signature(
                """
                    // Signature format: 5.0
                    // - language=kotlin
                    // - concise-default-values=yes
                    // - kotlin-name-type-order=yes
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public method(optional s: String?): void;
                      }
                    }
                """
            ),
        ) {
            val parameter =
                codebase.assertClass("test.pkg.Foo").methods().single().parameters().single()
            assertEquals("hasDefaultValue", true, parameter.hasDefaultValue())

            assertEquals("isDefaultValueKnown", false, parameter.isDefaultValueKnown())
            val exception =
                assertThrows(IllegalStateException::class.java) { parameter.defaultValueAsString() }
            assertEquals("defaultValue", "cannot call on UNKNOWN DefaultValue", exception.message)
        }
    }
}
