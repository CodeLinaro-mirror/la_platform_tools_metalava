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
import com.android.tools.metalava.cli.common.ARG_HIDE
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
            expectedIssues =
                """
                released-api.txt:4: error: Source breaking change: method test.pkg.Foo.fooMethod(int) can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:4: error: Source breaking change: method test.pkg.Foo.fooMethod(int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                """,
        )
    }

    @Test
    fun `Test switching items from all target languages to kotlin only`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void fooMethod();
                    field public int fooField;
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @KotlinOnly public class Foo {
                    ctor @KotlinOnly public Foo();
                    method @KotlinOnly public void fooMethod();
                    field @KotlinOnly public int fooField;
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:3: error: Binary breaking change: class test.pkg.Foo has been removed from bytecode [RemovedFromBytecode]
                released-api.txt:3: error: Source breaking change: class test.pkg.Foo can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:4: error: Binary breaking change: constructor test.pkg.Foo() has been removed from bytecode [RemovedFromBytecode]
                released-api.txt:4: error: Source breaking change: constructor test.pkg.Foo() can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:5: error: Binary breaking change: method test.pkg.Foo.fooMethod() has been removed from bytecode [RemovedFromBytecode]
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.fooMethod() can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:6: error: Binary breaking change: field test.pkg.Foo.fooField has been removed from bytecode [RemovedFromBytecode]
                released-api.txt:6: error: Source breaking change: field test.pkg.Foo.fooField can no longer be resolved from Java source [RemovedFromJava]
                """,
        )
    }

    @Test
    fun `Test switching items from all target languages to bytecode only`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public void fooMethod();
                    field public int fooField;
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @BytecodeOnly public class Foo {
                    ctor @BytecodeOnly public Foo();
                    method @BytecodeOnly public void fooMethod();
                    field @BytecodeOnly public int fooField;
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:3: error: Source breaking change: class test.pkg.Foo can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:3: error: Source breaking change: class test.pkg.Foo can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:4: error: Source breaking change: constructor test.pkg.Foo() can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:4: error: Source breaking change: constructor test.pkg.Foo() can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.fooMethod() can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.fooMethod() can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:6: error: Source breaking change: field test.pkg.Foo.fooField can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:6: error: Source breaking change: field test.pkg.Foo.fooField can no longer be resolved from Kotlin source [RemovedFromKotlin]
                """,
        )
    }

    @Test
    fun `Test switching items from inaccessible from one source language to bytecode only`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    ctor @InaccessibleFromJava public Foo();
                    method @InaccessibleFromKotlin public void fooMethod();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    ctor @BytecodeOnly public Foo();
                    method @BytecodeOnly public void fooMethod();
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:4: error: Source breaking change: constructor test.pkg.Foo() can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.fooMethod() can no longer be resolved from Java source [RemovedFromJava]
                """,
        )
    }

    @Test
    fun `Test expanding number of target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {
                    ctor @BytecodeOnly public Foo();
                    method @KotlinOnly public void fooMethod();
                    field @InaccessibleFromJava public int fooField;
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public class Foo {
                    ctor @InaccessibleFromKotlin public Foo();
                    method @InaccessibleFromJava public void fooMethod();
                    field public int fooField;
                  }
                }
                """,
            expectedIssues = "",
        )
    }

    @Test
    fun `Test both adding and removing target languages`() {
        check(
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @KotlinOnly public class Foo {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @InaccessibleFromKotlin public class Foo {
                  }
                }
                """,
            expectedIssues =
                """
                released-api.txt:3: error: Source breaking change: class test.pkg.Foo can no longer be resolved from Kotlin source [RemovedFromKotlin]
                """,
        )
    }

    @Test
    fun `Test making a method deprecated level hidden impact on kotlin`() {
        check(
            // Impact on java source tested separately below.
            extraArguments = arrayOf(ARG_HIDE, "RemovedFromJava"),
            expectedIssues =
                """
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadDoesNotTargetKotlin(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:6: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadHasFewerParameters(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:7: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadChangesParameterType(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:8: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadChangesParameterNullability(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:11: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadMakesParameterNonOptional(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:12: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadHasAdditionalNonOptionalParameter(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:13: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadChangesReturnType(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:15: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadChangesReturnNullability(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:17: error: Source breaking change: method test.pkg.Foo.incompatibleOverloadLessVisible(String,int) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public String compatibleBasicCase(String s, optional int i);
                    method public String incompatibleOverloadDoesNotTargetKotlin(String s, optional int i);
                    method public String incompatibleOverloadHasFewerParameters(String s, optional int i);
                    method public String incompatibleOverloadChangesParameterType(String s, optional int i);
                    method public String incompatibleOverloadChangesParameterNullability(String? s, optional int i);
                    method public String compatibleOverloadChangesParameterNullability(String s, optional int i);
                    method public String compatibleOverloadMakesParameterOptional(String s, optional int i);
                    method public String incompatibleOverloadMakesParameterNonOptional(String s, optional int i);
                    method public String incompatibleOverloadHasAdditionalNonOptionalParameter(String s, optional int i);
                    method public String incompatibleOverloadChangesReturnType(String s, optional int i);
                    method public String? compatibleOverloadChangesReturnNullability(String s, optional int i);
                    method public String incompatibleOverloadChangesReturnNullability(String s, optional int i);
                    method internal String compatibleOverloadMoreVisible(String s, optional int i);
                    method public String incompatibleOverloadLessVisible(String s, optional int i);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @BytecodeOnly @Deprecated public String compatibleBasicCase(String!, int);
                    method public String compatibleBasicCase(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadDoesNotTargetKotlin(String!, int);
                    method @InaccessibleFromKotlin public String incompatibleOverloadDoesNotTargetKotlin(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadHasFewerParameters(String!, int);
                    method public String incompatibleOverloadHasFewerParameters(String s);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadChangesParameterType(String!, int);
                    method public String incompatibleOverloadChangesParameterType(String s, optional double i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadChangesParameterNullability(String!, int);
                    method public String incompatibleOverloadChangesParameterNullability(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String compatibleOverloadChangesParameterNullability(String!, int);
                    method public String compatibleOverloadChangesParameterNullability(String? s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String compatibleOverloadMakesParameterOptional(String!, int);
                    method public String compatibleOverloadMakesParameterOptional(optional String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadMakesParameterNonOptional(String!, int);
                    method public String incompatibleOverloadMakesParameterNonOptional(String s, int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadHasAdditionalNonOptionalParameter(String!, int);
                    method public String incompatibleOverloadHasAdditionalNonOptionalParameter(String s, optional int i, String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadChangesReturnType(String!, int);
                    method public void incompatibleOverloadChangesReturnType(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String compatibleOverloadChangesReturnNullability(String!, int);
                    method public String compatibleOverloadChangesReturnNullability(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadChangesReturnNullability(String!, int);
                    method public String? incompatibleOverloadChangesReturnNullability(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated internal String compatibleOverloadMoreVisible(String!, int);
                    method public String compatibleOverloadMoreVisible(String s, optional int i, optional String s2, optional int i2);
                    method @BytecodeOnly @Deprecated public String incompatibleOverloadLessVisible(String!, int);
                    method internal String incompatibleOverloadLessVisible(String s, optional int i, optional String s2, optional int i2);
                  }
                }
                """,
        )
    }

    @Test
    fun `Test making a method deprecated level hidden impact on java`() {
        check(
            // Even though the new overload works for kotlin callers, java callers can't use
            // optional parameters so this is still a breaking change for them.
            expectedIssues =
                """
                released-api.txt:4: error: Source breaking change: method test.pkg.Foo.foo(String,int) can no longer be resolved from Java source [RemovedFromJava]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public String foo(String s, optional int i);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @BytecodeOnly @Deprecated public String foo(String!, int);
                    method public String foo(String s, optional int i, optional String s2, optional int i2);
                  }
                }
                """,
        )
    }

    @Test
    fun `Test making a constructor deprecated level hidden`() {
        check(
            // Even though the new overload works for kotlin callers, java callers can't use
            // optional parameters so this is still a breaking change for them.
            expectedIssues =
                """
                released-api.txt:4: error: Source breaking change: constructor test.pkg.Foo(String,int) can no longer be resolved from Java source [RemovedFromJava]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(String s, optional int i);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    ctor @BytecodeOnly @Deprecated public Foo(String!, int);
                    ctor public Foo(String s, optional int i, optional String s2, optional int i2);
                  }
                }
                """,
        )
    }

    @Test
    fun `Test making a method deprecated level hidden with overload on superclass`() {
        check(
            // Even though the new overload works for kotlin callers, java callers can't use
            // optional parameters so this is still a breaking change for them.
            expectedIssues =
                """
                released-api.txt:6: error: Source breaking change: method test.pkg.Foo.foo(String,int) can no longer be resolved from Java source [RemovedFromJava]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Bar {
                  }
                  public final class Foo extends test.pkg.Bar {
                    method public String foo(String s, optional int i);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Bar {
                    method public String foo(String s, optional int i, optional String s2, optional int i2);
                  }
                  public final class Foo extends test.pkg.Bar {
                    method @BytecodeOnly @Deprecated public String foo(String!, int);
                  }
                }
                """,
        )
    }

    @Test
    fun `Test adding optional parameter to a reified inline function`() {
        check(
            expectedIssues =
                """
                released-api.txt:5: error: Source breaking change: Removed method test.pkg.Foo.incompatibleOverloadNonOptionalParameter(T,String) [RemovedMethod]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public inline <reified T> void compatibleOverload(T t, optional String s);
                    method @KotlinOnly public inline <reified T> void incompatibleOverloadNonOptionalParameter(T t, optional String s);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public inline <reified T> void compatibleOverload(T t, optional String s, optional int i);
                    method @KotlinOnly public inline <reified T> void incompatibleOverloadNonOptionalParameter(T t, optional String s, int i);
                  }
                }
                """,
        )
    }
}
