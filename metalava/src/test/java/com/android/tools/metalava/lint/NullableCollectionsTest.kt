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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
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

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check inner nullable collections`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn,NullableCollectionElement"),
            expectedIssues =
                """
                    src/test/pkg/Foo.kt:4: warning: Return type of method test.pkg.Foo.foo() uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                    src/test/pkg/Foo.kt:4: warning: Return type of method test.pkg.Foo.foo() uses a nullable collection (`java.util.Map`); must be non-null [NullableCollection]
                    src/test/pkg/Foo.kt:5: warning: Return type of method test.pkg.Foo.bar() uses a nullable collection (`java.lang.String[]`); must be non-null [NullableCollection]
                    src/test/pkg/Foo.kt:6: warning: Return type of method test.pkg.Foo.baz() uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            import java.util.Optional
                            class Foo {
                                fun foo(): Pair<List<String>?, Map<String, Int>?>? = null
                                fun bar(): Array<Array<String>?> = emptyArray()
                                fun baz(): Optional<out List<String>?> = Optional.empty()
                            }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check inner nullable collections matching super method`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "HiddenSuperclass,NullableCollectionElement"),
            expectedIssues =
                """
                    src/test/pkg/Bar.kt:4: warning: Return type of method test.pkg.Bar.bar() uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                    src/test/pkg/VisibleSuperclass.kt:3: warning: Return type of method test.pkg.VisibleSuperclass.foo() uses a nullable collection (`java.util.List`); must be non-null [NullableCollection]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            open class VisibleSuperclass {
                                open fun foo(): List<List<String>?> = emptyList()
                            }
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            class Foo : VisibleSuperclass() {
                                // The warning shouldn't appear on this definition, only on the superclass
                                override fun foo(): List<List<String>?> = emptyList()
                            }
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            /** @hide */
                            open class HiddenSuperclass {
                                open fun bar(): List<List<String>?> = emptyList()
                            }
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            class Bar : HiddenSuperclass() {
                                // The superclass is hidden, so the warning will appear for this definition
                                override fun bar(): List<List<String>?> = emptyList()
                            }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test nullable collection elements`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                    src/test/pkg/Foo.kt:3: warning: Collection java.lang.String[] should not have a nullable element type (java.lang.String) in method test.pkg.Foo.foo() [NullableCollectionElement]
                    src/test/pkg/Foo.kt:4: warning: Collection java.util.List<java.lang.String> should not have a nullable element type (java.lang.String) in method test.pkg.Foo.bar() [NullableCollectionElement]
                    src/test/pkg/Foo.kt:5: warning: Collection java.util.Map<java.lang.String,java.lang.Integer> should not have a nullable element type (java.lang.String) in method test.pkg.Foo.baz() [NullableCollectionElement]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            class Foo {
                                fun foo(): Array<String?> = emptyArray()
                                fun bar(): List<String?> = emptyList()
                                fun baz(): Map<String?, Int?> = emptyMap()
                            }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test nullable collection elements matching super method`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                    src/test/pkg/Parent.kt:3: warning: Collection java.lang.String[] should not have a nullable element type (java.lang.String) in method test.pkg.Parent.foo() [NullableCollectionElement]
                    src/test/pkg/Parent.kt:4: warning: Collection java.util.List<java.lang.String> should not have a nullable element type (java.lang.String) in method test.pkg.Parent.bar() [NullableCollectionElement]
                    src/test/pkg/Parent.kt:5: warning: Collection java.util.Map<java.lang.String,java.lang.Integer> should not have a nullable element type (java.lang.String) in method test.pkg.Parent.baz() [NullableCollectionElement]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            open class Parent {
                                open fun foo(): Array<String?> = emptyArray()
                                open fun bar(): List<String?> = emptyList()
                                open fun baz(): Map<String?, Int?> = emptyMap()
                            }
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            class Foo : Parent() {
                                // Warnings will only appear for the parent types
                                override fun foo(): Array<String?> = emptyArray()
                                override fun bar(): List<String?> = emptyList()
                                override fun baz(): Map<String?, Int?> = emptyMap()
                            }
                        """
                    )
                )
        )
    }
}
