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
import com.android.tools.metalava.testing.java
import org.junit.Test

class MethodCompatibilityCheckTest : DriverTest() {
    @Test
    fun `Incompatible method change -- modifiers`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.java:5: error: Binary breaking change: Method test.pkg.MyClass.myMethod2 has changed 'abstract' qualifier [ChangedAbstract]
                    src/test/pkg/MyClass.java:6: error: Binary breaking change: Method test.pkg.MyClass.myMethod3 has changed 'static' qualifier [ChangedStatic]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class MyClass {
                          method public void myMethod2();
                          method public void myMethod3();
                          method deprecated public void myMethod4();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract class MyClass {
                                private MyClass() {}
                                public native abstract void myMethod2(); // Note that Errors.CHANGE_NATIVE is hidden by default
                                public static void myMethod3() {}
                                public void myMethod4() {}
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- final`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Outer.java:7: error: Binary breaking change: Method test.pkg.Outer.Class1.method1 has added 'final' qualifier [AddedFinal]
                    src/test/pkg/Outer.java:19: error: Method test.pkg.Outer.Class4.method4 has removed 'final' qualifier [RemovedFinalStrict]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class Outer {
                      }
                      public class Outer.Class1 {
                        ctor public Class1();
                        method public void method1();
                      }
                      public final class Outer.Class2 {
                        method public void method2();
                      }
                      public final class Outer.Class3 {
                        method public void method3();
                      }
                      public class Outer.Class4 {
                        method public final void method4();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract class Outer {
                                private Outer() {}
                                public class Class1 {
                                    public Class1() {}
                                    public final void method1() { } // Added final
                                }
                                public final class Class2 {
                                    private Class2() {}
                                    public final void method2() { } // Added final but class is effectively final so no change
                                }
                                public final class Class3 {
                                    private Class3() {}
                                    public void method3() { } // Removed final but is still effectively final
                                }
                                public class Class4 {
                                    private Class4() {}
                                    public void method4() { } // Removed final
                                }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- visibility`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.java:6: error: Binary breaking change: Method test.pkg.MyClass.myMethod2 changed visibility from public to protected [ChangedScope]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class MyClass {
                          method protected void myMethod1();
                          method public void myMethod2();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract class MyClass {
                                private MyClass() {}
                                public void myMethod1() {}
                                protected void myMethod2() {}
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Incompatible method change -- return types`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/MyClass.java:5: error: Binary breaking change: Method test.pkg.MyClass.method1 has changed return type from float to int [ChangedType]
                    src/test/pkg/MyClass.java:6: error: Binary breaking change: Method test.pkg.MyClass.method2 has changed return type from java.util.List<java.lang.Number> to java.util.List<java.lang.Integer> [ChangedType]
                    src/test/pkg/MyClass.java:7: error: Binary breaking change: Method test.pkg.MyClass.method3 has changed return type from java.util.List<java.lang.Integer> to java.util.List<java.lang.Number> [ChangedType]
                    src/test/pkg/MyClass.java:8: error: Binary breaking change: Method test.pkg.MyClass.method4 has changed return type from java.lang.String to java.lang.String[] [ChangedType]
                    src/test/pkg/MyClass.java:9: error: Binary breaking change: Method test.pkg.MyClass.method5 has changed return type from java.lang.String[] to java.lang.String[][] [ChangedType]
                    src/test/pkg/MyClass.java:11: error: Binary breaking change: Method test.pkg.MyClass.method7 has changed return type from T (extends java.lang.Number) to java.lang.Number [ChangedType]
                    src/test/pkg/MyClass.java:13: error: Binary breaking change: Method test.pkg.MyClass.method9 has changed return type from X (extends java.lang.Throwable) to U (extends java.lang.Number) [ChangedType]
                    src/test/pkg/MyClass.java:14: error: Binary breaking change: Method test.pkg.MyClass.method10 has changed return type from java.lang.Number[] to U (extends java.lang.Number) [ChangedType]
                """,
            checkCompatibilityApiReleased =
                """
                    package test.pkg {
                      public abstract class MyClass<T extends Number> {
                          method public float method1();
                          method public java.util.List<java.lang.Number> method2();
                          method public java.util.List<java.lang.Integer> method3();
                          method public String method4();
                          method public String[] method5();
                          method public <X extends java.lang.Throwable> T method6(java.util.function.Supplier<? extends X>);
                          method public <X extends java.lang.Throwable> T method7(java.util.function.Supplier<? extends X>);
                          method public <X extends java.lang.Throwable> Number method8(java.util.function.Supplier<? extends X>);
                          method public <X extends java.lang.Throwable> X method9(java.util.function.Supplier<? extends X>);
                          method public Number[] method10();
                      }
                    }
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract class MyClass<U extends Number> { // Changing type variable name is fine/compatible
                                private MyClass() {}
                                public int method1() { return 0; }
                                public java.util.List<Integer> method2() { return null; }
                                public java.util.List<Number> method3() { return null; }
                                public String[] method4() { return null; }
                                public String[][] method5() { return null; }
                                public <X extends java.lang.Throwable> U method6(java.util.function.Supplier<? extends X> arg) { return null; }
                                public <X extends java.lang.Throwable> Number method7(java.util.function.Supplier<? extends X> arg) { return null; }
                                public <X extends java.lang.Throwable> U method8(java.util.function.Supplier<? extends X> arg) { return null; }
                                public <X extends java.lang.Throwable> U method9(java.util.function.Supplier<? extends X> arg) { return null; }
                                public U method10();
                            }
                        """
                    )
                )
        )
    }
}
