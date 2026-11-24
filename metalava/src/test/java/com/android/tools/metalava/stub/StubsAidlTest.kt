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

package com.android.tools.metalava.stub

import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsAidlTest : AbstractStubsTest() {
    @Test
    fun `Check AIDL interface on classpath is treated as hidden in stubs`() {
        val iInterface =
            java(
                """
                    package android.os;
                    public interface IInterface {
                    }
                """
            )
        checkStubs(
            classpath =
                arrayOf(
                    jarFromSources(
                        "aidl.jar",
                        iInterface,
                        java(
                            """
                                package test.pkg.aidl;
                                public interface IMyAidlInterface extends android.os.IInterface {
                                    void doSomething();
                                }
                            """
                        ),
                    )
                ),
            compilationChecks =
                listOf(
                    CompilationCheck(
                        label = "pass",
                        additionalFiles = listOf(iInterface),
                    )
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import test.pkg.aidl.IMyAidlInterface;
                            public class MyClass implements IMyAidlInterface {
                                @Override
                                public void doSomething() {}
                            }
                        """
                    )
                ),
            warnings =
                """
                    src/test/pkg/MyClass.java:3: warning: Public class test.pkg.MyClass stripped of unavailable superclass test.pkg.aidl.IMyAidlInterface [HiddenSuperclass]
                """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass implements android.os.IInterface {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void doSomething() { throw new RuntimeException("Stub!"); }
                    }
                """,
        )
    }

    @Test
    fun `Check extending AIDL Stub on classpath is treated as hidden in stubs`() {
        val iInterface =
            java(
                """
                    package android.os;
                    public interface IInterface {
                    }
                """
            )
        val binder =
            java(
                """
                    package android.os;
                    public class Binder implements IInterface {
                        public Binder() {}
                    }
                """
            )
        checkStubs(
            classpath =
                arrayOf(
                    jarFromSources(
                        "aidl.jar",
                        iInterface,
                        binder,
                        java(
                            """
                                package test.pkg.aidl;
                                public interface IMyAidlInterface extends android.os.IInterface {
                                    public static abstract class Stub extends android.os.Binder implements test.pkg.aidl.IMyAidlInterface {
                                        public Stub() {}
                                    }
                                    void doSomething();
                                }
                            """
                        ),
                    )
                ),
            compilationChecks =
                listOf(
                    CompilationCheck(
                        label = "pass",
                        additionalFiles = listOf(iInterface, binder),
                    )
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import test.pkg.aidl.IMyAidlInterface;
                            public class MyClass extends IMyAidlInterface.Stub {
                                @Override
                                public void doSomething() {}
                            }
                        """
                    )
                ),
            warnings =
                """
                    src/test/pkg/MyClass.java:3: warning: Public class test.pkg.MyClass stripped of unavailable superclass test.pkg.aidl.IMyAidlInterface.Stub [HiddenSuperclass]
                """,
            source =
                """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class MyClass extends android.os.Binder implements android.os.IInterface {
                    public MyClass() { throw new RuntimeException("Stub!"); }
                    public void doSomething() { throw new RuntimeException("Stub!"); }
                    }
                """,
        )
    }
}
