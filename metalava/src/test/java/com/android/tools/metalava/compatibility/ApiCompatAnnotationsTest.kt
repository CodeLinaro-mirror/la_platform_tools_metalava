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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class ApiCompatAnnotationsTest : DriverTest() {

    @Test
    fun `Test adding annotation to class not passed in apiCompatAnnotations`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @test.pkg.MyAnnotation public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test removing annotation from function not passed in apiCompatAnnotations`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method @test.pkg.MyAnnotation @test.pkg.MySecondAnnotation public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MySecondAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method @test.pkg.MyAnnotation public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MySecondAnnotation {
                      }
                    }
                """,
            apiCompatAnnotations = setOf("test.pkg.MyAnnotation"),
        )
    }

    @Test
    fun `Test adding annotation from class passed in apiCompatAnnotations`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Cannot add @test.pkg.MyAnnotation annotation to class test.pkg.Foo: Incompatible change [AddedAnnotation]
                """,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @test.pkg.MyAnnotation public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            apiCompatAnnotations = setOf("test.pkg.MyAnnotation"),
        )
    }

    @Test
    fun `Test removing annotation from class passed in apiCompatAnnotations`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Cannot remove @test.pkg.MyAnnotation annotation from class test.pkg.Foo: Incompatible change [RemovedAnnotation]
                """,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @test.pkg.MyAnnotation public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            apiCompatAnnotations = setOf("test.pkg.MyAnnotation"),
        )
    }

    @Test
    fun `Test adding annotation from function passed in apiCompatAnnotations`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Cannot add @test.pkg.MyAnnotation annotation to method test.pkg.Foo.bar(): Incompatible change [AddedAnnotation]
                """,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method @test.pkg.MyAnnotation public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            apiCompatAnnotations = setOf("test.pkg.MyAnnotation"),
        )
    }

    @Test
    fun `Test removing annotation from function passed in apiCompatAnnotations`() {
        check(
            expectedIssues =
                """
                load-api.txt:4: error: Cannot remove @test.pkg.MyAnnotation annotation from method test.pkg.Foo.bar(): Incompatible change [RemovedAnnotation]
                """,
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method @test.pkg.MyAnnotation public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            signatureSource =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public final class Foo {
                        method public void bar();
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                      }
                    }
                """,
            apiCompatAnnotations = setOf("test.pkg.MyAnnotation"),
        )
    }
}
