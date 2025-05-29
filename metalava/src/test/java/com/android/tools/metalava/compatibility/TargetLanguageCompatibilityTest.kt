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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class TargetLanguageCompatibilityTest : DriverTest() {
    @Test
    fun `Test binary compatibility only issue with various target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class Foo {
                    method public inline <T> void allLanguages();
                    method @BytecodeOnly public inline <T> void bytecodeOnly();
                    method @KotlinOnly public inline <T> void kotlinOnly();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class Foo {
                    method public inline <reified T> void allLanguages();
                    method @BytecodeOnly public inline <reified T> void bytecodeOnly();
                    method @KotlinOnly public inline <reified T> void kotlinOnly();
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Binary breaking change: Method test.pkg.Foo.allLanguages made type variable T reified: incompatible change [AddedReified]
                load-api.txt:5: error: Binary breaking change: Method test.pkg.Foo.bytecodeOnly made type variable T reified: incompatible change [AddedReified]
                """,
        )
    }

    @Test
    fun `Test source compatibility only issue with various target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    method public void allLanguages(int p0);
                    method @BytecodeOnly public void bytecodeOnly(int p0);
                    method @KotlinOnly public void kotlinOnly(int p0);
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    method public void allLanguages(int);
                    method @BytecodeOnly public void bytecodeOnly(int);
                    method @KotlinOnly public void kotlinOnly(int);
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Source breaking change: Attempted to remove parameter name from parameter arg1 in test.pkg.Foo.allLanguages [ParameterNameChange]
                load-api.txt:6: error: Source breaking change: Attempted to remove parameter name from parameter arg1 in test.pkg.Foo.kotlinOnly [ParameterNameChange]
                """,
        )
    }

    @Test
    fun `Test binary and source compatibility issue with different target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    method public void allLanguages();
                    method @BytecodeOnly public void bytecodeOnly();
                    method @KotlinOnly public void kotlinOnly();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    method public static void allLanguages();
                    method @BytecodeOnly public static void bytecodeOnly();
                    method @KotlinOnly public static void kotlinOnly();
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Binary breaking change: Method test.pkg.Foo.allLanguages has changed 'static' qualifier [ChangedStatic]
                load-api.txt:5: error: Binary breaking change: Method test.pkg.Foo.bytecodeOnly has changed 'static' qualifier [ChangedStatic]
                load-api.txt:6: error: Source breaking change: Method test.pkg.Foo.kotlinOnly has changed 'static' qualifier [ChangedStatic]
                """,
        )
    }

    @Test
    fun `Test removal of elements with different target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    method public void allLanguages();
                    method @BytecodeOnly public void bytecodeOnly();
                    method @KotlinOnly public void kotlinOnly();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:4: error: Binary breaking change: Removed method test.pkg.Foo.allLanguages() [RemovedMethod]
                released-api.txt:5: error: Binary breaking change: Removed method test.pkg.Foo.bytecodeOnly() [RemovedMethod]
                released-api.txt:6: error: Source breaking change: Removed method test.pkg.Foo.kotlinOnly() [RemovedMethod]
                """,
        )
    }

    @Test
    fun `Test switching method to bytecode-only while removing parameter name`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    method public void fooMethod(int p0);
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    method @BytecodeOnly public void fooMethod(int);
                  }
                }
                """,
            // No issue for removing the parameter name because the method is now bytecode-only.
            // The followup will add an issue for changing the target language set.
            expectedIssues = "",
        )
    }
}
