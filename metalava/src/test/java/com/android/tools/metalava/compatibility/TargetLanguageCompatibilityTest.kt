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
import com.android.tools.metalava.reporter.Issues
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
                load-api.txt:4: error: Source breaking change: Attempted to remove parameter name from parameter arg1 in test.pkg.Foo.allLanguages(int arg1) [ParameterNameChange]
                load-api.txt:6: error: Source breaking change: Attempted to remove parameter name from parameter arg1 in test.pkg.Foo.kotlinOnly(int arg1) [ParameterNameChange]
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
            extraArguments =
                hiddenIssues(
                    Issues.REMOVED_FROM_JAVA,
                ),
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
    fun `Test making a suspend method deprecated level hidden`() {
        // `withCompatibleKotlinOverload` can still be used from Kotlin in the same way it was
        // before because the extra continuation parameter isn't used from source, so the new
        // overload with an optional parameter will work.
        check(
            expectedIssues =
                """
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.noCompatibleKotlinOverload(int,kotlin.coroutines.Continuation<? super kotlin.Unit>) can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:5: error: Source breaking change: method test.pkg.Foo.noCompatibleKotlinOverload(int,kotlin.coroutines.Continuation<? super kotlin.Unit>) can no longer be resolved from Kotlin source [RemovedFromKotlin]
                released-api.txt:6: error: Source breaking change: method test.pkg.Foo.withCompatibleKotlinOverload(int,kotlin.coroutines.Continuation<? super kotlin.Unit>) can no longer be resolved from Java source [RemovedFromJava]
            """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method public suspend Object? noCompatibleKotlinOverload(int i, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                    method public suspend Object? withCompatibleKotlinOverload(int i, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    ctor public Foo();
                    method @BytecodeOnly @Deprecated public suspend Object? noCompatibleKotlinOverload(int, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                    method @BytecodeOnly @Deprecated public suspend Object? withCompatibleKotlinOverload(int, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                    method public suspend Object? withCompatibleKotlinOverload(int i, optional String s, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                  }
                }
                """
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

    @Test
    fun `Test comparing by super methods for different target languages`() {
        check(
            // When it is no longer listed for Foo, differentTargetLanguagesDisjoint appears removed
            // because the ParentClass version has a disjoint target language set.
            // For differentTargetLanguagesWithOverlap, the old Foo version is compared to the
            // ParentClass version, and seen as removing a target language because the ParentClass
            // version has an overlapping but not identical target language set.
            expectedIssues =
                """
                released-api.txt:7: error: Binary breaking change: Removed method test.pkg.Foo.differentTargetLanguagesDisjoint() [RemovedMethod]
                released-api.txt:8: error: Source breaking change: method test.pkg.Foo.differentTargetLanguagesWithOverlap() can no longer be resolved from Java source [RemovedFromJava]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends test.pkg.ParentClass {
                    method public void sameAllTargetLanguages();
                    method @BytecodeOnly public void sameJustBytecode();
                    method @KotlinOnly public void sameJustKotlin();
                    method @BytecodeOnly public void differentTargetLanguagesDisjoint();
                    method public void differentTargetLanguagesWithOverlap();
                  }
                  public class ParentClass {
                    method public void sameAllTargetLanguages();
                    method @BytecodeOnly public void sameJustBytecode();
                    method @KotlinOnly public void sameJustKotlin();
                    method @KotlinOnly public void differentTargetLanguagesDisjoint();
                    method @InaccessibleFromJava public void differentTargetLanguagesWithOverlap();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends test.pkg.ParentClass {
                  }
                  public class ParentClass {
                    method public void sameAllTargetLanguages();
                    method @BytecodeOnly public void sameJustBytecode();
                    method @KotlinOnly public void sameJustKotlin();
                    method @KotlinOnly public void differentTargetLanguagesDisjoint();
                    method @InaccessibleFromJava public void differentTargetLanguagesWithOverlap();
                  }
                }
                """
        )
    }

    @Test
    fun `Split method into separate bytecode and kotlin entries`() {
        check(
            expectedIssues =
                """
                load-api.txt:7: error: Binary breaking change: Method test.pkg.Foo.incompatibleSplitReturnTypeChange has changed return type from void to int [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public void compatibleSplit();
                    method public void incompatibleSplitReturnTypeChange();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public void compatibleSplit();
                    method @InaccessibleFromKotlin public void compatibleSplit();
                    method @KotlinOnly public void incompatibleSplitReturnTypeChange();
                    method @InaccessibleFromKotlin public int incompatibleSplitReturnTypeChange();
                  }
                }
                """,
        )
    }

    @Test
    fun `Remove one language for method with separate bytecode and kotlin entries`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Source breaking change: Removed method test.pkg.Foo.removeKotlin() [RemovedMethod]
                released-api.txt:7: error: Binary breaking change: Removed method test.pkg.Foo.removeBytecode() [RemovedMethod]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public int removeKotlin();
                    method @BytecodeOnly public int removeKotlin();
                    method @KotlinOnly public int removeBytecode();
                    method @BytecodeOnly public int removeBytecode();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @BytecodeOnly public int removeKotlin();
                    method @KotlinOnly public int removeBytecode();
                  }
                }
                """
        )
    }

    @Test
    fun `Merge method which had separate bytecode and kotlin entries`() {
        check(
            expectedIssues =
                """
                load-api.txt:5: error: Binary breaking change: Method test.pkg.Foo.incompatibleMergeChangeReturnType has changed return type from void to int [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public int compatibleMerge();
                    method @BytecodeOnly public int compatibleMerge();
                    method @KotlinOnly public int incompatibleMergeChangeReturnType();
                    method @BytecodeOnly public void incompatibleMergeChangeReturnType();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public int compatibleMerge();
                    method public int incompatibleMergeChangeReturnType();
                  }
                }
                """
        )
    }

    @Test
    fun `Changes to one copy of a method with separate bytecode and kotlin entries`() {
        check(
            expectedIssues =
                """
                load-api.txt:5: error: Binary breaking change: Method test.pkg.Foo.changeToBytecodeVersion has changed return type from int to void [ChangedType]
                load-api.txt:6: error: Source breaking change: Method test.pkg.Foo.changeToKotlinVersion has changed return type from int to void [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public int changeToBytecodeVersion();
                    method @BytecodeOnly public int changeToBytecodeVersion();
                    method @KotlinOnly public int changeToKotlinVersion();
                    method @BytecodeOnly public int changeToKotlinVersion();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @KotlinOnly public int changeToBytecodeVersion();
                    method @BytecodeOnly public void changeToBytecodeVersion();
                    method @KotlinOnly public void changeToKotlinVersion();
                    method @BytecodeOnly public int changeToKotlinVersion();
                  }
                }
                """,
        )
    }

    @Test
    fun `Test erasure of type arguments when a method becomes bytecode-only`() {
        // When a bytecode-only method is loaded from a jar, the types will be erased.
        // Changing a return type to remove type arguments when the method isn't bytecode-only is
        // a source-breaking change, but reported as binary-breaking here.
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public java.util.List<String> toBytecodeOnlyMethod();
                    method public java.util.List<String> sourceMethod();
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method @BytecodeOnly @Deprecated public java.util.List toBytecodeOnlyMethod();
                    method public java.util.List sourceMethod();
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:5: error: Binary breaking change: Method test.pkg.Foo.sourceMethod has changed return type from java.util.List<java.lang.String> to java.util.List [ChangedType]
                released-api.txt:4: error: Source breaking change: method test.pkg.Foo.toBytecodeOnlyMethod() can no longer be resolved from Java source [RemovedFromJava]
                released-api.txt:4: error: Source breaking change: method test.pkg.Foo.toBytecodeOnlyMethod() can no longer be resolved from Kotlin source [RemovedFromKotlin]
                """,
        )
    }
}
