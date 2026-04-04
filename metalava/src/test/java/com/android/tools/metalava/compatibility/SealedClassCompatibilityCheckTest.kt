/*
 * Copyright (C) 2026 The Android Open Source Project
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

class SealedClassCompatibilityCheckTest : DriverTest() {

    @Test
    fun `Should raise issue when adding abstract method to explicitly sealed non-effectively final class when a grandchild implements the abstract method`() {
        check(
            // there should be an issue raised here because MyAbstractChildClass is publicly
            // extensible and doesn't implement the abstract method from MySealedClass,
            // so a client implementing it could break
            expectedIssues =
                """
                    load-api.txt:11: error: Binary breaking change: Added method test.pkg.MySealedClass.myFun() [AddedAbstractMethod]
                """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClass();
                  }
                  public abstract class MyAbstractGrandchildClass extends test.pkg.MyAbstractChildClass {
                    ctor public MyAbstractGrandchildClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClass();
                  }
                  public abstract class MyAbstractGrandchildClass extends test.pkg.MyAbstractChildClass {
                    ctor public MyAbstractGrandchildClass();
                    method public void myFun();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract void myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should not raise issue when adding abstract method to explicitly sealed non-effectively final class when a subclass implements the abstract method`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractClass();
                    method public void myFun();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract void myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding abstract method to explicitly sealed non-effectively final class when a subclass has an abstract override of the abstract method`() {
        // There should be an issue here because, although the abstract child class overrides
        // myFun(), it doesn't implement it (it is still abstract), which can create a breaking
        // change for clients
        check(
            expectedIssues =
                """
                load-api.txt:7: error: Binary breaking change: Added method test.pkg.MySealedClass.myFun() [AddedAbstractMethod]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract String myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding abstract method to sealed non-effectively final class when not all subclasses implement the method`() {
        // MyAbstractChildClassA is the only one that implements myFun()
        check(
            expectedIssues =
                """
                load-api.txt:11: error: Binary breaking change: Added method test.pkg.MySealedClass.myFun() [AddedAbstractMethod]
            """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClassA extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassA();
                  }
                  public abstract class MyAbstractChildClassB extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassB();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClassA extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassA();
                    method public String myFun();
                  }
                  public abstract class MyAbstractChildClassB extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassB();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract String myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should not raise issue when adding abstract method to sealed non-effectively final class when all subclasses implement the method`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClassA extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassA();
                  }
                  public abstract class MyAbstractChildClassB extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassB();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClassA extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassA();
                    method public String myFun();
                  }
                  public abstract class MyAbstractChildClassB extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClassB();
                    method public String myFun();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract String myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should not raise issue when adding abstract method to sealed non-effectively final class when immediate subclasses implements the method but grandchild class doesn't`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClass();
                  }
                  public abstract class MyAbstractGrandChildClass extends test.pkg.MyAbstractChildClass {
                    ctor public MyAbstractGrandChildClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract class MyAbstractChildClass extends test.pkg.MySealedClass {
                    ctor public MyAbstractChildClass();
                    method public String myFun();
                  }
                  public abstract class MyAbstractGrandChildClass extends test.pkg.MyAbstractChildClass {
                    ctor public MyAbstractGrandChildClass();
                  }
                  public abstract sealed exhaustive class MySealedClass {
                    method public abstract String myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding abstract method to non-effectively final explicitly sealed class`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Binary breaking change: Added method test.pkg.MyNonFinalSealedClassWithNonSealedChild.myFun() [AddedAbstractMethod]
                    load-api.txt:9: error: Binary breaking change: Added method test.pkg.MyNonFinalSealedClassWithNonSealedGrandchild.myFun() [AddedAbstractMethod]
                """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract sealed exhaustive class DirectChildClass extends test.pkg.MyNonFinalSealedClassWithNonSealedGrandchild {
                  }
                  public abstract sealed exhaustive class MyNonFinalSealedClassWithNonSealedChild {
                  }
                  public abstract sealed exhaustive class MyNonFinalSealedClassWithNonSealedGrandchild {
                  }
                  public abstract class NonSealedGrandchildClass extends test.pkg.DirectChildClass {
                    ctor public NonSealedGrandchildClass();
                  }
                  public abstract class NotEffectivelySealedChildClass extends test.pkg.MyNonFinalSealedClassWithNonSealedChild {
                    ctor public NotEffectivelySealedChildClass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract sealed exhaustive class DirectChildClass extends test.pkg.MyNonFinalSealedClassWithNonSealedGrandchild {
                  }
                  public abstract sealed exhaustive class MyNonFinalSealedClassWithNonSealedChild {
                    method public abstract void myFun();
                  }
                  public abstract sealed exhaustive class MyNonFinalSealedClassWithNonSealedGrandchild {
                    method public abstract void myFun();
                  }
                  public abstract class NonSealedGrandchildClass extends test.pkg.DirectChildClass {
                    ctor public NonSealedGrandchildClass();
                  }
                  public abstract class NotEffectivelySealedChildClass extends test.pkg.MyNonFinalSealedClassWithNonSealedChild {
                    ctor public NotEffectivelySealedChildClass();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding abstract method to non-effectively final effectively sealed class`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Binary breaking change: Added method test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedChild.myFun() [AddedAbstractMethod]
                    load-api.txt:9: error: Binary breaking change: Added method test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedGrandchild.myFun() [AddedAbstractMethod]
                """
                    .trimIndent(),
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract sealed exhaustive class DirectChildClass extends test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedGrandchild {
                  }
                  public abstract class NonFinalAbstractEffectivelySealedClassWithNonSealedChild {
                  }
                  public abstract class NonFinalAbstractEffectivelySealedClassWithNonSealedGrandchild {
                  }
                  public abstract class NonSealedGrandchildClass extends test.pkg.DirectChildClass {
                    ctor public NonSealedGrandchildClass();
                  }
                  public abstract class NotEffectivelySealedChildClass extends test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedChild {
                    ctor public NotEffectivelySealedChildClass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public abstract sealed exhaustive class DirectChildClass extends test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedGrandchild {
                  }
                  public abstract class NonFinalAbstractEffectivelySealedClassWithNonSealedChild {
                    method public abstract void myFun();
                  }
                  public abstract class NonFinalAbstractEffectivelySealedClassWithNonSealedGrandchild {
                    method public abstract void myFun();
                  }
                  public abstract class NonSealedGrandchildClass extends test.pkg.DirectChildClass {
                    ctor public NonSealedGrandchildClass();
                  }
                  public abstract class NotEffectivelySealedChildClass extends test.pkg.NonFinalAbstractEffectivelySealedClassWithNonSealedChild {
                    ctor public NotEffectivelySealedChildClass();
                  }
                }
                """
        )
    }

    @Test
    fun `Should not raise issue when adding abstract method to non-effectively final effectively sealed interface`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public sealed exhaustive interface SealedEffectivelyFinalInterface {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public sealed exhaustive interface SealedEffectivelyFinalInterface {
                    method public void myFun();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding private subclass to exhaustive sealed interface (sealed interface changes from exhaustive to nonexhaustive)`() {
        check(
            expectedIssues =
                """
                    load-api.txt:6: error: Sealed interface can no longer be exhaustively matched because an inaccessible subclass was added. [SealedClassExhaustivityChanged]
                """
                    .trimIndent(),
            expectedFail = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed exhaustive interface SealedInterface {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed nonexhaustive interface SealedInterface {
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding private subclass to exhaustive sealed class (sealed class changes from exhaustive to nonexhaustive)`() {
        check(
            expectedIssues =
                """
                load-api.txt:6: error: Sealed class can no longer be exhaustively matched because an inaccessible subclass was added. [SealedClassExhaustivityChanged]
            """
                    .trimIndent(),
            expectedFail = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class MySubClass extends test.pkg.SealedClass {
                    ctor public MySubClass();
                  }
                  public abstract sealed exhaustive class SealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class MySubClass extends test.pkg.SealedClass {
                    ctor public MySubClass();
                  }
                  public abstract sealed nonexhaustive class SealedClass {
                  }
                }
                """
        )
    }

    @Test
    fun `Don't raise issue when adding subclass to nonexhaustive sealed class`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                  public abstract sealed nonexhaustive class SealedClass {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class NewSubclass extends test.pkg.SealedClass {
                    ctor public NewSubclass();
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                  public abstract sealed nonexhaustive class SealedClass {
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding implementor to exhaustive sealed interface`() {
        check(
            expectedIssues =
                """
                    load-api.txt:3: error: Added a subclass to a sealed interface that can be exhaustively matched [AddedSubclassToSealedClass]
                """
                    .trimIndent(),
            expectedFail = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed exhaustive interface SealedInterface {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class NewInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public NewInterfaceImplementor();
                  }
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed exhaustive interface SealedInterface {
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding non-experimental subclass to exhaustive sealed class`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Added a subclass to a sealed class that can be exhaustively matched [AddedSubclassToSealedClass]
            """
                    .trimIndent(),
            expectedFail = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract sealed exhaustive class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class NewSubclass extends test.pkg.SealedClass {
                    ctor public NewSubclass();
                  }
                  public abstract sealed exhaustive class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """
        )
    }

    @Test
    fun `Should raise issue when adding experimental subclass to exhaustive sealed class`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Added a subclass to a sealed class that can be exhaustively matched [AddedSubclassToSealedClass]
            """
                    .trimIndent(),
            expectedFail = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract sealed exhaustive class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public final class NewSubclass extends test.pkg.SealedClass {
                    ctor public NewSubclass();
                  }
                  public abstract sealed exhaustive class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations =
                arrayOf("androidx.annotation.SuppressCompatibility")
        )
    }

    @Test
    fun `Should not raise issue when comparing against sealed interface without any exhaustivity modifier`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed interface SealedInterface {
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  public final class NewInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public NewInterfaceImplementor();
                  }
                  public final class OriginalInterfaceImplementor implements test.pkg.SealedInterface {
                    ctor public OriginalInterfaceImplementor();
                  }
                  public sealed exhaustive interface SealedInterface {
                  }
                }
                """
        )
    }

    @Test
    fun `Should not raise issue when comparing against sealed class without any exhaustivity modifier`() {
        check(
            expectedIssues = "",
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public abstract sealed class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """,
            signatureSource =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public final class NewSubclass extends test.pkg.SealedClass {
                    ctor public NewSubclass();
                  }
                  public abstract sealed exhaustive class SealedClass {
                  }
                  public final class OriginalSubclass extends test.pkg.SealedClass {
                    ctor public OriginalSubclass();
                  }
                }
                """,
            suppressCompatibilityMetaAnnotations =
                arrayOf("androidx.annotation.SuppressCompatibility")
        )
    }
}
