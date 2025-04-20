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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommonTargetLanguageTest : BaseModelTest() {
    @Test
    fun `Test regular items which can be used from any language`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class FooClass {
                        public void fooMethod() {}
                        public int fooField = 0;
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        fun fooMethod() = Unit
                        companion object {
                            @JvmField
                            val fooField: Int = 0
                        }
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        ctor public FooClass();
                        method public void fooMethod();
                        field public int fooField;
                      }
                    }
                """
            )
        ) {
            val cls = codebase.assertClass("test.pkg.FooClass")
            val items =
                listOf(
                    codebase.assertPackage("test.pkg"),
                    cls,
                    cls.assertConstructor(""),
                    cls.assertMethod("fooMethod", ""),
                    cls.assertField("fooField"),
                )

            for (item in items) {
                assertThat(item.targetLanguages)
                    .containsExactly(
                        TargetLanguage.JAVA,
                        TargetLanguage.KOTLIN,
                        TargetLanguage.BYTECODE,
                    )
            }
        }
    }

    @Test
    fun `Test properties can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    class FooClass {
                        val fooProperty = 0
                    }
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class FooClass {
                        property public int fooProperty;
                      }
                    }
                """
            )
        ) {
            val prop = codebase.assertClass("test.pkg.FooClass").assertProperty("fooProperty")
            assertThat(prop.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }

    @Test
    fun `Test type aliases can only be used from Kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
            signature(
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = String;
                    }
                """
            ),
        ) {
            val alias = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(alias.targetLanguages).containsExactly(TargetLanguage.KOTLIN)
        }
    }
}
