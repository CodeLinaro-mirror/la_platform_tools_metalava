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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

class CommonRecordClassTest : BaseModelTest() {
    @Test
    fun `Test simple record class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a, String b) {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                        ctor public Test(int a, String b);
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(ClassKind.RECORD, testClass.classKind)

            assertEquals(
                listOf(ModifierKeyword.PUBLIC_KEYWORD, ModifierKeyword.FINAL_KEYWORD),
                testClass.modifiers.keywordList
            )
        }
    }

    @Test
    fun `Test record class without explicit Object method overrides`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a) {
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodNames = testClass.methods().map { it.name() }.sorted()

            assertEquals(listOf("a"), methodNames)
        }
    }

    @Test
    fun `Test record class with explicit Object method overrides`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a) {
                        @Override public boolean equals(Object obj) {
                            return false;
                        }
                        @Override public int hashCode() {
                            return 0;
                        }
                        @Override public @NonNull String toString() {
                            return "";
                        }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodNames = testClass.methods().map { it.name() }.sorted()

            assertEquals(listOf("a", "equals", "hashCode", "toString"), methodNames)
        }
    }

    @Test
    fun `Test record with compact constructor and implicit constructor parameters`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a, String b) {
                        public Test {
                            if (a < 0 || b.length() > 5) {
                                throw IllegalArgumentException("blah");
                            }
                        }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertConstructor(listOf("int", "java.lang.String"))
        }
    }
}
