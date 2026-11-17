/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.nullableSource
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [Issues.VISIBLY_SYNCHRONIZED] issue. */
@Suppress(
    "ConstantConditionIf",
    "ConstantValue",
    "EmptySynchronizedStatement",
)
class VisiblySynchronizedTest : DriverTest() {

    @Test
    fun `Api methods should not be synchronized in their signature`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/CheckSynchronization.java:12: error: Internal locks must not be exposed: method android.pkg.CheckSynchronization.errorMethod1(Runnable) [VisiblySynchronized]
                """,
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            VisiblySynchronized: android.pkg.CheckSynchronization#errorMethod1(Runnable):
                                Internal locks must not be exposed: method android.pkg.CheckSynchronization.errorMethod1(Runnable)
                        """,
                    silentUpdate = false,
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class CheckSynchronization {
                                public void okMethod1(@Nullable Runnable r) { }
                                private static final Object LOCK = new Object();
                                public void okMethod2() {
                                    synchronized(LOCK) {
                                    }
                                }
                                public synchronized void errorMethod1(@Nullable Runnable r) { } // ERROR
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNullableJavaSource,
                    nullableSource
                )
        )
    }

    @Test
    fun `Suppression of issues with previously released APIs`() {
        check(
            apiLint =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Foo {
                        method public void fooSynchronized();
                      }
                    }
                """, // enabled
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: Internal locks must not be exposed: method test.pkg.Foo.newSynchronized() [VisiblySynchronized]
                """,
            baselineApiLintTestInfo =
                BaselineTestInfo(
                    inputContents = "",
                    expectedOutputContents =
                        """
                            // Baseline format: 1.0
                            VisiblySynchronized: test.pkg.Foo#newSynchronized():
                                Internal locks must not be exposed: method test.pkg.Foo.newSynchronized()
                        """,
                    silentUpdate = false,
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public synchronized void fooSynchronized() {}
                                public synchronized void newSynchronized() {}
                            }
                        """
                    ),
                )
        )
    }
}
