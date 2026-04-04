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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import org.junit.Test

class ManagerCheckTest : DriverTest() {
    @Test
    fun `Check Manager related issues`() {
        check(
            apiLint = "", // enabled
            // Ignore other issues.
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                    src/android/pkg/MyFirstManager.java:8: error: Managers must always be obtained from Context; no direct constructors [ManagerConstructor]
                    src/android/pkg/MyFirstManager.java:11: error: Managers must always be obtained from Context (`error1`) [ManagerLookup]
                    src/android/pkg/MyFirstManager.java:13: warning: Methods should return `List<? extends Parcelable>` instead of `Parcelable[]` to support `ParceledListSlice` under the hood: method android.pkg.MyFirstManager.error2() [ParcelableList]
                    src/android/pkg/MyFirstManager.java:13: error: Managers must always be obtained from Context (`error2`) [ManagerLookup]
                    src/android/pkg/MyFirstManager.java:15: error: Managers must always be obtained from Context (`error3`) [ManagerLookup]
                    src/android/pkg/MySecondManager.java:8: error: Managers must always be obtained from Context (`error1`) [ManagerLookup]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.NonNull;
                            import androidx.annotation.Nullable;
                            import java.util.List;

                            public class MyFirstManager {
                                public MyFirstManager() {
                                }
                                @Nullable
                                public MyFirstManager error1() { return null; }
                                @NonNull
                                public MyFirstManager[] error2() { return null; }
                                @NonNull
                                public List<MyFirstManager> error3() { return null; }
                                @Nullable
                                public MySecondManager ok() { return null; }
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            public class MySecondManager<T extends MySecondManager<T>> {
                                private MySecondManager() {
                                }

                                @Nullable
                                public T error1() { return null; }
                            }
                        """
                    ),
                    KnownSourceFiles.androidxNonNullJavaSource,
                    KnownSourceFiles.androidxNullableJavaSource,
                ),
        )
    }
}
