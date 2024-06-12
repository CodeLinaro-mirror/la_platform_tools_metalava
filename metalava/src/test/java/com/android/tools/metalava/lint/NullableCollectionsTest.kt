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
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.testing.java
import org.junit.Test

class NullableCollectionsTest : DriverTest() {
    @Test
    fun `Check nullable collection as method return, parameter, and field`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyClass.java:8: warning: Return type of method android.pkg.MyClass.getList(java.util.List<java.lang.String>) uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                src/android/pkg/MyClass.java:11: warning: Type of field android.pkg.MyClass.STRINGS uses a nullable collection (`java.lang.String[]`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import androidx.annotation.Nullable;

                        public class MyClass {
                            // Nullable collection parameter is allowed on non-callback method
                            @Nullable
                            public java.util.List<String> getList(@Nullable java.util.List<String> list) {
                                return null;
                            }
                            public static final String @Nullable [] STRINGS = null;
                        }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check nullable collection as parameter of callback method`() {
        // Nullable collections are allowed as parameters except for in callback methods
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/MyCallback.java:6: warning: Type of parameter list in android.pkg.MyCallback.onFoo(java.util.List<java.lang.String> list) uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import androidx.annotation.Nullable;

                        public class MyCallback {
                            public void onFoo(@Nullable java.util.List<String> list) {
                            }
                        }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check nullable collection on deprecated method`() {
        check(
            apiLint = "", // enabled
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            import androidx.annotation.Nullable;
                            public class MyCallback {
                                 /** @deprecated don't use this. */
                                @Deprecated
                                @Nullable
                                public String[] ignoredBecauseDeprecated(@Nullable String[] ignored) {
                                    return null;
                                }
                            }
                        """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check nullable collection on overridden method`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "HiddenSuperclass"),
            expectedIssues =
                """
                src/android/pkg/MyClass.java:7: warning: Return type of method android.pkg.MyClass.getList(java.util.List<java.lang.String>) uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                src/android/pkg/MySubClass.java:14: warning: Return type of method android.pkg.MySubClass.getOtherList(java.util.List<java.lang.String>) uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class MyClass {
                                @Nullable
                                public java.util.List<String> getList(@Nullable java.util.List<String> list) {
                                    return null;
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            /** @hide */
                            public interface MyHiddenInterface {
                                @Nullable
                                java.util.List<String> getOtherList(@Nullable java.util.List<String> list);
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class MySubClass extends MyClass implements MyHiddenInterface {
                                @Nullable
                                public java.util.List<String> getList(@Nullable java.util.List<String> list) {
                                    // Ignored because it has the same nullability as its super method
                                    return null;
                                }

                                @Override
                                @Nullable
                                public java.util.List<String> getOtherList(@Nullable java.util.List<String> list) {
                                    // Reported because the super method is hidden.
                                    return null;
                                }
                            }
                        """
                    ),
                    androidxNullableSource
                )
        )
    }

    @Test
    fun `Check nullable primitive arrays`() {
        // Allowed for legacy reasons, b/343748165
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;

                            import androidx.annotation.Nullable;

                            public class MyClass {
                                @Nullable
                                public int[] getInts() { return null; }
                                @Nullable
                                public int[][] getMoreInts() { return null; }
                            }
                        """
                    ),
                    androidxNullableSource
                )
        )
    }
}
