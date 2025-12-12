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

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Ensure that breaking change is detected on element with experimental ancestor meta-annotation, but not itself experimental`() {
        check(
            expectedIssues =
                """
                src/test/pkg/SuppressCompatHasRequiresOptIn.kt:18: error: Binary breaking change: Method test.pkg.MyClass.myIncompatiblyChangedFunA has changed return type from int to java.lang.String [ChangedType]
                src/test/pkg/SuppressCompatHasRequiresOptIn.kt:21: error: Binary breaking change: Method test.pkg.MyClass.myIncompatiblyChangedFunB has changed return type from int to java.lang.String [ChangedType]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class MyClass {
                    ctor public MyClass();
                    method @test.pkg.NotSuppressCompatHasOptIn public int myIncompatiblyChangedFunA();
                    method @test.pkg.NotSuppressCompat public int myIncompatiblyChangedFunB();
                  }
                  @test.pkg.NotSuppressCompatHasOptIn public @interface NotSuppressCompat {
                  }
                  @test.pkg.SuppressCompatNoRequiresOptIn public @interface NotSuppressCompatHasOptIn {
                  }
                  @SuppressCompatibility @kotlin.RequiresOptIn public @interface SuppressCompatHasRequiresOptIn {
                  }
                  @SuppressCompatibility @test.pkg.SuppressCompatHasRequiresOptIn public @interface SuppressCompatNoRequiresOptIn {
                  }
                }
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn
                        annotation class SuppressCompatHasRequiresOptIn

                        @SuppressCompatHasRequiresOptIn
                        annotation class SuppressCompatNoRequiresOptIn

                        @OptIn(SuppressCompatHasRequiresOptIn::class)
                        @SuppressCompatNoRequiresOptIn
                        annotation class NotSuppressCompatHasOptIn

                        @NotSuppressCompatHasOptIn
                        annotation class NotSuppressCompat

                        class MyClass {
                            @NotSuppressCompatHasOptIn
                            fun myIncompatiblyChangedFunA(): String { return "1" }

                            @NotSuppressCompat
                            fun myIncompatiblyChangedFunB(): String { return "1" }
                        }
                        """
                            .trimIndent()
                    )
                )
        )
    }

    @Test
    fun `Should raise compatibility error on added abstract to experimental method in non-experimental class`() {
        check(
            /*
             * There is a compatibility error raised on finalFunToBeAbstract because
             * it is changing from a closed non-abstract method which clients can't override to
             * an abstract method, which is a breaking change for clients implementing the class.
             *
             * There also is a compatibility error raised on openFunToBeAbstract because
             * it is changing from an open non-abstract method which clients aren't forced to
             * override to an abstract method, which can be a breaking change if clients aren't
             * already overriding it.
             */
            expectedIssues =
                """
                    load-api.txt:5: error: Binary breaking change: Method test.pkg.AbstractClass.finalFunToBeAbstract has changed 'abstract' qualifier [ChangedAbstract]
                    load-api.txt:6: error: Binary breaking change: Method test.pkg.AbstractClass.openFunToBeAbstract has changed 'abstract' qualifier [ChangedAbstract]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class AbstractClass {
                    ctor public AbstractClass();
                    method @test.pkg.Experimental public final void finalFunToBeAbstract();
                    method @test.pkg.Experimental public void openFunToBeAbstract();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class AbstractClass {
                    ctor public AbstractClass();
                    method @test.pkg.Experimental public abstract void finalFunToBeAbstract();
                    method @test.pkg.Experimental public abstract void openFunToBeAbstract();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @Test
    fun `Should raise compatibility error on changed default on method in interface`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Binary breaking change: Method test.pkg.MyInterface.myFun has changed 'default' qualifier [ChangedDefault]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public interface MyInterface {
                    method @test.pkg.ExperimentalAnnotation public default void myFun();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public interface MyInterface {
                    method @test.pkg.ExperimentalAnnotation public void myFun();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Should raise compatibility error on changed method return type in abstract class or interface`() {
        check(
            /*
             * The changed return type in the experimental method in MyClass should not trigger
             * a compatibility error because overriding or using that method is not required
             * and needs to be opted into. Also, the changed return types in the non-abstract and
             * final methods in the abstract class and interface should not trigger errors
             * because they either don't have to or can't be overridden.
             */
            expectedIssues =
                """
                    load-api.txt:7: error: Binary breaking change: Method test.pkg.MyAbstractClass.myExperimentalAbstractFun has changed return type from java.lang.String to int [ChangedType]
                    load-api.txt:18: error: Binary breaking change: Method test.pkg.MyInterface.myExperimentalAbstractFun has changed return type from java.lang.String to int [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyAbstractClass {
                    ctor public MyAbstractClass();
                    method @test.pkg.ExperimentalAnnotation public abstract String myExperimentalAbstractFun();
                    method @test.pkg.ExperimentalAnnotation public final String myExperimentalNonAbstractClosedFun();
                    method @test.pkg.ExperimentalAnnotation public String myExperimentalNonAbstractFun();
                    method public abstract void myFun();
                  }
                  public final class MyClass {
                    ctor public MyClass();
                    method @test.pkg.ExperimentalAnnotation public String myExperimentalFun();
                    method public void myFun();
                  }
                  public interface MyInterface {
                    method @test.pkg.ExperimentalAnnotation public String myExperimentalAbstractFun();
                    method @test.pkg.ExperimentalAnnotation public default String myExperimentalNonAbstractFun();
                    method public void myFun();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyAbstractClass {
                    ctor public MyAbstractClass();
                    method @test.pkg.ExperimentalAnnotation public abstract int myExperimentalAbstractFun();
                    method @test.pkg.ExperimentalAnnotation public final int myExperimentalNonAbstractClosedFun();
                    method @test.pkg.ExperimentalAnnotation public int myExperimentalNonAbstractFun();
                    method public abstract void myFun();
                  }
                  public final class MyClass {
                    ctor public MyClass();
                    method @test.pkg.ExperimentalAnnotation public int myExperimentalFun();
                    method public void myFun();
                  }
                  public interface MyInterface {
                    method @test.pkg.ExperimentalAnnotation public int myExperimentalAbstractFun();
                    method @test.pkg.ExperimentalAnnotation public default int myExperimentalNonAbstractFun();
                    method public void myFun();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Should raise compatibility error on added final to method in abstract class`() {
        check(
            /*
             * Adding 'final' to the experimental method in MyClass shouldn't have a compatibility
             * error raised because overriding the method is optional and must be opted into.
             * Same for myNonAbstractExperimentalFun in MyAbstractClass.
             */
            expectedIssues =
                """
                    load-api.txt:7: error: Binary breaking change: Method test.pkg.MyAbstractClass.myAbstractExperimentalFun has added 'final' qualifier [AddedFinal]
                """,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyAbstractClass {
                    ctor public MyAbstractClass();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public abstract void myAbstractExperimentalFun();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public void myNonAbstractExperimentalFun();
                    method public abstract void myNonExperimentalFun();
                  }
                  public class MyClass {
                    ctor public MyClass();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public void myFun();
                    method public final void myNonExperimentalFun();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalAnnotation {
                  }
                  public abstract class MyAbstractClass {
                    ctor public MyAbstractClass();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public final void myAbstractExperimentalFun();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public final void myNonAbstractExperimentalFun();
                    method public abstract void myNonExperimentalFun();
                  }
                  public class MyClass {
                    ctor public MyClass();
                    method @SuppressCompatibility @test.pkg.ExperimentalAnnotation public final void myFun();
                    method public final void myNonExperimentalFun();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalAnnotation")
        )
    }

    @Test
    fun `Should raise error when experimental method is removed from extensible class`() {
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
