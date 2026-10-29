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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.JAVA_ENUM_VALUES
import com.android.tools.metalava.model.JAVA_ENUM_VALUE_OF
import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Test

/** Common tests for implementations of [ClassItem] that are `enum` classes. */
class CommonEnumTest : BaseModelTest() {
    @Test
    fun `Test enum class super class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public enum Foo {
                        FOO
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    enum class Foo {
                        FOO
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public enum Foo {
                        enum_constant public test.pkg.Foo FOO;
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val enumClass = codebase.assertResolvedClass("java.lang.Enum")

            // Make sure that the enum class is not itself treated as a ClassKind.ENUM
            assertEquals(ClassKind.CLASS, enumClass.classKind)

            assertSame(enumClass, fooClass.superClassType()?.resolveClass(codebase))
            assertSame(enumClass, fooClass.superClass())

            val interfaceList = fooClass.interfaceTypes()
            assertEquals(emptyList(), interfaceList)
        }
    }

    @Test
    fun `Test enum synthetic methods are not included in the enum class`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public enum Foo {
                        FOO1;

                        public void values(String p) {}
                        public void valueOf(int p) {}
                        public void getEntries(String p) {}
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    enum class Foo {
                        FOO1;

                        fun values(p: String) {}
                        fun valueOf(p: Int) {}
                        fun getEntries(p: String) {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public enum Foo {
                        enum_constant public test.pkg.Foo FOO1;
                        method public void values(String);
                        method public void valueOf(int);
                        method public void getEntries(String);
                        // These are present here as they may be present in previously released APIs
                        // and if they are not removed from the model constructed from this then it
                        // will result in RemovedMethod or RemovedDeprecatedMethod errors.
                        method public test.pkg.Foo[] values();
                        method public test.pkg.Foo valueOf(String);
                        method public static kotlin.enums.EnumEntries<test.pkg.Foo> getEntries();
                      }
                    }
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Make sure that only `values(String)` is in the class.
            val values = fooClass.assertMethod(JAVA_ENUM_VALUES, listOf("java.lang.String"))
            assertThat(fooClass.methods().filter { it.name() == JAVA_ENUM_VALUES })
                .isEqualTo(listOf(values))

            // Make sure that only `valueOf(int)` is in the class.
            val valueOf = fooClass.assertMethod(JAVA_ENUM_VALUE_OF, listOf("int"))
            assertThat(fooClass.methods().filter { it.name() == JAVA_ENUM_VALUE_OF })
                .isEqualTo(listOf(valueOf))

            // Make sure that only `getEntries(String)` is in the class.
            val getEntries = fooClass.assertMethod("getEntries", listOf("java.lang.String"))
            assertThat(fooClass.methods().filter { it.name() == "getEntries" })
                .isEqualTo(listOf(getEntries))
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test enum locations - java`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public enum Foo {
                            FOO1,
                            FOO2
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public enum Bar {
                            BAR1,
                            BAR2,
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public enum Baz {
                            BAZ1,
                            BAZ2;
                        }
                    """
                ),
            ),
        ) {
            val classes = codebase.assertPackage("test.pkg").topLevelClasses()

            val fields = classes.sortedBy { it.simpleName() }.flatMap { it.fields() }
            val locations = buildString {
                for (field in fields) {
                    append(field.fileLocation)
                    append(" -> ")
                    append(field)
                    append('\n')
                }
            }

            assertEquals(
                """
                    MAIN_SRC/src/test/pkg/Bar.java:3 -> enum constant test.pkg.Bar.BAR1
                    MAIN_SRC/src/test/pkg/Bar.java:4 -> enum constant test.pkg.Bar.BAR2
                    MAIN_SRC/src/test/pkg/Baz.java:3 -> enum constant test.pkg.Baz.BAZ1
                    MAIN_SRC/src/test/pkg/Baz.java:4 -> enum constant test.pkg.Baz.BAZ2
                    MAIN_SRC/src/test/pkg/Foo.java:3 -> enum constant test.pkg.Foo.FOO1
                    MAIN_SRC/src/test/pkg/Foo.java:4 -> enum constant test.pkg.Foo.FOO2
                """
                    .trimIndent(),
                removeTestSpecificDirectories(locations.trim())
            )
        }
    }

    private fun checkEnumWithAbstractMethods(
        test: CodebaseContext.() -> Unit,
    ) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public enum Foo {
                        FOO {
                            public void method() {}
                        };

                        public abstract void method();
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    enum class Foo {
                        FOO {
                            fun method() {}
                        };

                        abstract fun method()
                    }
                """
            ),
            test = test,
        )
    }

    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test enum with abstract methods - class modifiers`() {
        checkEnumWithAbstractMethods {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            assertEquals(
                listOf(ModifierKeyword.PUBLIC_KEYWORD),
                fooClass.modifiers.keywordList,
                message = "class"
            )
        }
    }

    @SupportedInputFormats(InputFormat.JAVA, InputFormat.KOTLIN)
    @Test
    fun `Test enum with abstract methods - method modifiers`() {
        checkEnumWithAbstractMethods {
            val testMethod = codebase.assertClass("test.pkg.Foo").methods().single()

            assertEquals(
                listOf(ModifierKeyword.PUBLIC_KEYWORD, ModifierKeyword.ABSTRACT_KEYWORD),
                testMethod.modifiers.keywordList,
                message = "method"
            )
        }
    }
}
