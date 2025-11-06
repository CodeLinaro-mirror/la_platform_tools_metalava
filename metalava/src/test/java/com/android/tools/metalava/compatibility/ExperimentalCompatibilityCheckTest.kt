/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ExperimentalCompatibilityCheckTest : DriverTest() {

    @Test
    fun `Don't raise error when experimental method is removed from extensible class or interface`() {
        check(
            expectedIssues =
                """
                released-api.txt:7: error: Binary breaking change: Removed method test.pkg.MyNonExperimentalAbstractClass.myExperimentalAbstractFun() [RemovedMethod]
                released-api.txt:18: error: Binary breaking change: Removed method test.pkg.MyNonExperimentalInterface.myExperimentalAbstractMethod() [RemovedMethod]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public @interface MyExperimentalAnnotation {
                  }
                  public abstract class MyNonExperimentalAbstractClass {
                    ctor public MyNonExperimentalAbstractClass();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public abstract void myExperimentalAbstractFun();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public final void myExperimentalClosedFun();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public void myExperimentalOpenFun();
                    method public abstract void myNonExperimentalAbstractFun();
                  }
                  public final class MyNonExperimentalFinalClass {
                    ctor public MyNonExperimentalFinalClass();
                    method public void myFunA();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public void myFunB();
                  }
                  public interface MyNonExperimentalInterface {
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public void myExperimentalAbstractMethod();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public default void myExperimentalNonAbstractMethod();
                    method public void myFunA();
                  }
                  public class MyNonExperimentalOpenClassWithFinalExperimentalMethod {
                    ctor public MyNonExperimentalOpenClassWithFinalExperimentalMethod();
                    method public final void myFunA();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public final void myFunB();
                  }
                  public class MyNonExperimentalOpenClassWithOpenExperimentalMethod {
                    ctor public MyNonExperimentalOpenClassWithOpenExperimentalMethod();
                    method public final void myFunA();
                    method @SuppressCompatibility @test.pkg.MyExperimentalAnnotation public final void myFunB();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public @interface MyExperimentalAnnotation {
                  }
                  public abstract class MyNonExperimentalAbstractClass {
                    ctor public MyNonExperimentalAbstractClass();
                    method public abstract void myNonExperimentalAbstractFun();
                  }
                  public final class MyNonExperimentalFinalClass {
                    ctor public MyNonExperimentalFinalClass();
                    method public void myFunA();
                  }
                  public interface MyNonExperimentalInterface {
                    method public void myFunA();
                  }
                  public class MyNonExperimentalOpenClassWithFinalExperimentalMethod {
                    ctor public MyNonExperimentalOpenClassWithFinalExperimentalMethod();
                    method public final void myFunA();
                  }
                  public class MyNonExperimentalOpenClassWithOpenExperimentalMethod {
                    ctor public MyNonExperimentalOpenClassWithOpenExperimentalMethod();
                    method public final void myFunA();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Raise error when experimental abstract method is added to non-experimental class`() {
        check(
            expectedIssues =
                """
                load-api.txt:8: error: Binary breaking change: Added method test.pkg.MyClass.myFunB() [AddedAbstractMethod]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract void myFunB();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Don't raise error when experimental abstract method is added to experimental class`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract void myFunB();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Don't raise error when non-experimental abstract method is added to experimental class`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract class MyClass {
                    ctor public MyClass();
                    method public abstract void myFunA();
                    method public abstract void myFunB();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't raise error when experimental abstract method is added to non-extensible class`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyService {
                    method public abstract int myFun(int a);
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    annotation class ExperimentalAnnotation

                    abstract class MyService private constructor() {
                        abstract fun myFun(a: Int): Int

                        @ExperimentalAnnotation
                        abstract fun mySecondFun()
                    }
                    """
                    )
                ),
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }
}
