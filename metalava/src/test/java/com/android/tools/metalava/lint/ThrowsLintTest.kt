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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.lint.ARG_API_LINT
import com.android.tools.metalava.testing.java
import org.junit.Test

class ThrowsLintTest : DriverTest() {

    @Test
    fun `Check exception related issues`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_API_LINT,
                    // Conflicting advice:
                    ARG_HIDE,
                    "BannedThrow"
                ),
            expectedIssues =
                """
                src/android/pkg/MyClass.java:6: error: Methods must not throw generic exceptions (`java.lang.Exception`) [GenericException]
                src/android/pkg/MyClass.java:7: error: Methods must not throw generic exceptions (`java.lang.Throwable`) [GenericException]
                src/android/pkg/MyClass.java:8: error: Methods must not throw generic exceptions (`java.lang.Error`) [GenericException]
                src/android/pkg/MyClass.java:11: error: Methods calling system APIs should rethrow `RemoteException` as `RuntimeException` (but do not list it in the throws clause) [RethrowRemoteException]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;
                    import android.os.RemoteException;

                    @SuppressWarnings("RedundantThrows")
                    public class MyClass {
                        public void method1() throws Exception { }
                        public void method2() throws Throwable { }
                        public void method3() throws Error { }
                        public void method4() throws IllegalArgumentException { }
                        public void method4() throws NullPointerException { }
                        public void method5() throws RemoteException { }
                        public void ok(int p) throws NullPointerException { }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Unchecked exceptions not allowed`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:22: error: Unchecked exception java.lang.NullPointerException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:23: error: Unchecked exception java.lang.ClassCastException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:24: error: Unchecked exception java.lang.IndexOutOfBoundsException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:25: error: Unchecked exception java.lang.reflect.UndeclaredThrowableException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:26: error: Unchecked exception java.lang.reflect.MalformedParametersException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:27: error: Unchecked exception java.lang.reflect.MalformedParameterizedTypeException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:28: error: Unchecked exception java.lang.invoke.WrongMethodTypeException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:29: error: Unchecked exception java.lang.EnumConstantNotPresentException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:30: error: Unchecked exception java.lang.IllegalMonitorStateException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:31: error: Unchecked exception java.lang.SecurityException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:32: error: Unchecked exception java.lang.UnsupportedOperationException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:33: error: Unchecked exception java.lang.annotation.AnnotationTypeMismatchException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:34: error: Unchecked exception java.lang.annotation.IncompleteAnnotationException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:35: error: Unchecked exception java.lang.TypeNotPresentException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:36: error: Unchecked exception java.lang.IllegalStateException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:37: error: Unchecked exception java.lang.ArithmeticException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:38: error: Unchecked exception java.lang.IllegalArgumentException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:39: error: Unchecked exception java.lang.ArrayStoreException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:40: error: Unchecked exception java.lang.NegativeArraySizeException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:41: error: Unchecked exception java.util.MissingResourceException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:42: error: Unchecked exception java.util.EmptyStackException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:43: error: Unchecked exception java.util.concurrent.CompletionException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:44: error: Unchecked exception java.util.concurrent.RejectedExecutionException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:45: error: Unchecked exception java.util.IllformedLocaleException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:46: error: Unchecked exception java.util.ConcurrentModificationException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:47: error: Unchecked exception java.util.NoSuchElementException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:48: error: Unchecked exception java.io.UncheckedIOException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:49: error: Unchecked exception java.time.DateTimeException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:50: error: Unchecked exception java.security.ProviderException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:51: error: Unchecked exception java.nio.BufferUnderflowException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:52: error: Unchecked exception java.nio.BufferOverflowException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                src/test/pkg/Foo.java:53: error: Unchecked exception java.lang.AssertionError does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import java.lang.reflect.UndeclaredThrowableException;
                        import java.lang.reflect.MalformedParametersException;
                        import java.lang.reflect.MalformedParameterizedTypeException;
                        import java.lang.invoke.WrongMethodTypeException;
                        import java.lang.annotation.AnnotationTypeMismatchException;
                        import java.lang.annotation.IncompleteAnnotationException;
                        import java.util.MissingResourceException;
                        import java.util.EmptyStackException;
                        import java.util.concurrent.CompletionException;
                        import java.util.concurrent.RejectedExecutionException;
                        import java.util.IllformedLocaleException;
                        import java.util.ConcurrentModificationException;
                        import java.util.NoSuchElementException;
                        import java.io.UncheckedIOException;
                        import java.time.DateTimeException;
                        import java.security.ProviderException;
                        import java.nio.BufferUnderflowException;
                        import java.nio.BufferOverflowException;
                        public class Foo {
                            // 32 errors
                            public void a() throws NullPointerException;
                            public void b() throws ClassCastException;
                            public void c() throws IndexOutOfBoundsException;
                            public void d() throws UndeclaredThrowableException;
                            public void e() throws MalformedParametersException;
                            public void f() throws MalformedParameterizedTypeException;
                            public void g() throws WrongMethodTypeException;
                            public void h() throws EnumConstantNotPresentException;
                            public void i() throws IllegalMonitorStateException;
                            public void j() throws SecurityException;
                            public void k() throws UnsupportedOperationException;
                            public void l() throws AnnotationTypeMismatchException;
                            public void m() throws IncompleteAnnotationException;
                            public void n() throws TypeNotPresentException;
                            public void o() throws IllegalStateException;
                            public void p() throws ArithmeticException;
                            public void q() throws IllegalArgumentException;
                            public void r() throws ArrayStoreException;
                            public void s() throws NegativeArraySizeException;
                            public void t() throws MissingResourceException;
                            public void u() throws EmptyStackException;
                            public void v() throws CompletionException;
                            public void w() throws RejectedExecutionException;
                            public void x() throws IllformedLocaleException;
                            public void y() throws ConcurrentModificationException;
                            public void z() throws NoSuchElementException;
                            public void aa() throws UncheckedIOException;
                            public void ab() throws DateTimeException;
                            public void ac() throws ProviderException;
                            public void ad() throws BufferUnderflowException;
                            public void ae() throws BufferOverflowException;
                            public void af() throws AssertionError;
                        }
                    """
                    ),
                )
        )
    }

    @Test
    fun `Hidden Unchecked exceptions allowed`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Foo {
                           public void a() throws NullPointerException;
                        }
                    """
                    ),
                    java(
                        """
                        /**
                        * @hide
                        */
                        package test.pkg;
                    """
                    )
                )
        )
    }

    @Test
    fun `Test throws type parameter`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/Test.java:9: error: Unchecked exception X does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @SuppressWarnings("ALL")
                            public final class Test {
                                private Test() {}
                                public <X extends Throwable> void throwsTypeParameter() throws X {
                                    return null;
                                }
                                public <X extends IllegalStateException> void throwsUncheckedTypeParameter() throws X {
                                    return null;
                                }
                            }
                        """
                    ),
                )
        )
    }

    @Test
    fun `Test throwing an unlisted unchecked exception is allowed`() {
        // It is ok to throw unchecked exceptions, they just don't need to be in the throws clause
        check(
            apiLint = "", // enabled
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                src/test/pkg/Foo.java:3: error: Unchecked exception java.lang.NullPointerException does not need to be listed in the method throws clause (only in documentation) [BannedThrow]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public void errorListedInThrowsClause() throws NullPointerException {
                                    throw NullPointerException();
                                }

                                public void okNotListedInThrowsClause() {
                                    throw ClassCastException();
                                }
                            }
                        """
                    )
                )
        )
    }
}
