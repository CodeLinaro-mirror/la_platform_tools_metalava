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
import com.android.tools.metalava.androidxNonNullSource
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.cli.lint.ARG_API_LINT
import com.android.tools.metalava.libcoreNonNullSource
import com.android.tools.metalava.libcoreNullableSource
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class NullabilityLintTest : DriverTest() {
    @Test
    fun `Test fields, parameters and returns require nullability`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/android/pkg/Foo.java:11: error: Missing nullability on parameter `name` in method `Foo` [MissingNullability]
                src/android/pkg/Foo.java:12: error: Missing nullability on parameter `value` in method `setBadValue` [MissingNullability]
                src/android/pkg/Foo.java:13: error: Missing nullability on method `getBadValue` return [MissingNullability]
                src/android/pkg/Foo.java:20: error: Missing nullability on parameter `duration` in method `methodMissingParamAnnotations` [MissingNullability]
                src/android/pkg/Foo.java:7: error: Missing nullability on field `badField` in class `class android.pkg.Foo` [MissingNullability]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        public class Foo<T> {
                            public final Foo badField;
                            @Nullable
                            public final Foo goodField;

                            public Foo(String name, int number) { }
                            public void setBadValue(Foo value) { }
                            public Foo getBadValue(int number) { throw UnsupportedOperationExceptions(); }
                            public void setGoodValue(@Nullable Foo value) { }
                            public void setGoodIgnoredGenericValue(T value) { }
                            @NonNull
                            public Foo getGoodValue(int number) { throw UnsupportedOperationExceptions(); }

                            @NonNull
                            public Foo methodMissingParamAnnotations(java.time.Duration duration) {
                                throw UnsupportedOperationException();
                            }
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test no missing nullability errors for enums`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "Enum"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings("ALL")
                    public enum Foo {
                        A, B;
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    enum class Language {
                        KOTLIN,
                        JAVA
                    }
                    """
                    ),
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test no missing nullability errors for kotlin constructs`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_HIDE, "StaticUtils"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package android.pkg

                    object Bar

                    class FooBar {
                        companion object
                    }

                    class FooBarNamed {
                        companion object Named
                    }

                    class Foo {
                        val a = 3
                        var b: String? = null
                    }
                    """
                    ),
                ),
        )
    }

    @Test
    fun `Test type variable array requires nullability`() {
        check(
            apiLint = "", // enabled
            extraArguments = arrayOf(ARG_API_LINT, ARG_HIDE, "ArrayReturn"),
            expectedIssues =
                """
                src/test/pkg/Foo.java:4: error: Missing nullability on method `badTypeVarArrayReturn` return [MissingNullability]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Foo<T> {
                            public T goodTypeVarReturn() { return null; }
                            public T[] badTypeVarArrayReturn() { return null; }
                        }
                    """
                            .trimIndent()
                    )
                )
        )
    }

    @Test
    fun `Test equals, toString, non-null constants, enums and annotation members don't require nullability`() {
        check(
            apiLint = "", // enabled
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;

                        import android.annotation.SuppressLint;

                        public class Foo<T> {
                            public static final String FOO_CONSTANT = "test";

                            public boolean equals(Object other) {
                                return other == this;
                            }

                            public int hashCode() {
                                return 0;
                            }

                            public String toString() {
                                return "Foo";
                            }

                            @SuppressLint("Enum")
                            public enum FooEnum {
                                FOO, BAR
                            }

                            public @interface FooAnnotation {
                                String value() default "";
                            }
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Nullability check for generic methods referencing parent type parameter`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                src/test/pkg/MyClass.java:14: error: Missing nullability on method `method4` return [MissingNullability]
                src/test/pkg/MyClass.java:14: error: Missing nullability on parameter `input` in method `method4` [MissingNullability]
            """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;
                    import androidx.annotation.Nullable;

                    public class MyClass extends HiddenParent<String> {
                        public void method1() { }

                        @NonNull
                        @Override
                        public String method3(@Nullable String input) { return null; }

                        @Override
                        public String method4(String input) { return null; }
                    }
                    """
                    ),
                    java(
                        """
                    package test.pkg;

                    class HiddenParent<T> {
                        public T method2(T t) { }
                        public T method3(T t) { }
                        public T method4(T t) { }
                    }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No warnings about nullability on private constructor getters`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class MyClass private constructor(
                            val myParameter: Set<Int>
                        )
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No error for nullability on synthetic methods`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Foo {
                            @JvmSynthetic
                            fun bar(): String {}
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Constructors return types don't require nullability`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        import androidx.annotation.NonNull;
                        public class Foo {
                            // Doesn't require nullability
                            public Foo(@NonNull String bar);
                            // Requires nullability
                            public @NonNull String baz(@NonNull String whatever);
                        }
                    """
                    ),
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `No nullability allowed on overrides of unannotated methods or parameters`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:16: error: Invalid nullability on type java.lang.String in method `bar` return. Method override cannot use a nullable type when the corresponding type from the super method is platform-nullness. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:16: error: Invalid nullability on type java.lang.String in parameter `baz` in method `bar`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is platform-nullness. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:19: error: Invalid nullability on type java.lang.String in parameter `y` in method `x`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is platform-nullness. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:8: error: Missing nullability on method `bar` return [MissingNullability]
                src/test/pkg/Foo.java:8: error: Missing nullability on parameter `baz` in method `bar` [MissingNullability]
                src/test/pkg/Foo.java:11: error: Missing nullability on parameter `y` in method `x` [MissingNullability]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        public class Foo {
                            // Not annotated
                            public String bar(String baz);

                            // Partially annotated
                            @Nullable public String x(String y);
                        }
                        public class Bar extends Foo {
                            // Not allowed to mark override method Nullable if parent is not annotated
                            // Not allowed to mark override parameter NonNull if parent is not annotated
                            @Nullable @Override public String bar(@NonNull String baz);
                            // It is allowed to mark the override method Nullable if the parent is Nullable.
                            // Not allowed to mark override parameter if parent is not annotated.
                            @Nullable @Override public String x(@NonNull String y);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Override enforcement on kotlin sourced child class`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Bar.kt:5: error: Invalid nullability on type java.lang.String in parameter `baz` in method `bar`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is platform-nullness. [InvalidNullabilityOverride]
                src/test/pkg/Foo.java:5: error: Missing nullability on method `bar` return [MissingNullability]
                src/test/pkg/Foo.java:5: error: Missing nullability on parameter `baz` in method `bar` [MissingNullability]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        public class Foo {
                            // Not annotated
                            public String bar(String baz);
                        }
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        // Not allowed to mark override method Nullable if parent is not annotated
                        // Not allowed to mark override parameter NonNull if parent is not annotated
                        class Bar : Foo {
                            override fun bar(baz: String): String
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Overrides of non-null methods cannot be nullable`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:12: error: Invalid nullability on type java.lang.String method `bar` return. Method override cannot use a nullable type when the corresponding type from the super method is non-null. [InvalidNullabilityOverride]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        public class Foo {
                            @NonNull public String bar(@Nullable String baz);
                        }

                        // Not allowed to mark override method Nullable if parent is nonNull
                        public class Bar extends Foo {
                            @Nullable @Override public String bar(@Nullable String baz);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @Test
    fun `Overrides of nullable parameters cannot be non-null`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:13: error: Invalid nullability on type java.lang.String in parameter `baz` in method `bar`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is nullable. [InvalidNullabilityOverride]
                """,
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        public class Foo {
                            // Not annotated
                            @NonNull public String bar(@Nullable String baz);
                        }

                        // Not allowed to mark override parameter NonNull if parent is Nullable
                        public class Bar extends Foo {
                            @NonNull @Override public String bar(@NonNull String baz);
                        }
                    """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics should be allowed`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg;

                        interface Base<T> {
                            fun method1(input: T): T
                        }

                        class Subject1 : Base<String> {
                            override fun method1(input: String): String {
                                TODO()
                            }
                        }

                        class Subject2 : Base<String?> {
                            override fun method1(input: String?): String? {
                                TODO()
                            }
                        }
                    """
                    )
                )
        )
    }

    @Test
    fun `Invalid nullability override in function with generic parameter`() {
        check(
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/StringProperty.java:5: error: Invalid nullability on type java.lang.String in parameter `arg2` in method `foo`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is platform-nullness. [InvalidNullabilityOverride]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public abstract class Property<V> {
                                public abstract void foo(V arg1, @SuppressWarnings("MissingNullability") String arg2);
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.NonNull;
                            public class StringProperty extends Property<String> {
                                @Override
                                public void foo(@NonNull String arg1, @NonNull String arg2) {}
                            }
                        """
                    ),
                    androidxNonNullSource,
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics (Object to generic and back)`() {
        check(
            apiLint = "",
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                "src/test/pkg/ArrayMap.java:11: error: Invalid nullability on type java.lang.Object in parameter `key` in method `get`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is nullable. [InvalidNullabilityOverride]",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        open class SimpleArrayMap<K, V> {
                            open operator fun get(key: K): V? {
                                TODO()
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        import java.util.Map;
                        import androidx.annotation.NonNull;
                        import androidx.annotation.Nullable;

                        // The android version of [Map.get] has a @Nullable parameter
                        public class ArrayMap<K, V> extends SimpleArrayMap<K, V> implements Map<K, V> {
                            @Override
                            @Nullable
                            public V get(@NonNull Object key) {
                                return super.get((K) key);
                            }
                        }

                    """
                    ),
                    androidxNonNullSource,
                    androidxNullableSource,
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability overrides in unbounded generics (one super method lacks nullness info)`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        open class SimpleArrayMap<K, V> {
                            open operator fun get(key: K): V? {
                                TODO()
                            }
                        }
                    """
                    ),
                    java(
                        """
                        package test.pkg;

                        import java.util.Map;
                        import androidx.annotation.Nullable;

                        // The android version of [Map.get] has a @Nullable parameter
                        public class ArrayMap<K, V> extends SimpleArrayMap<K, V> implements Map<K, V> {
                            @Override
                            @Nullable
                            public V get(@Nullable Object key) {
                                return super.get((K) key);
                            }
                        }
                    """
                    ),
                    androidxNullableSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Nullability on vararg with inherited generic type`() {
        check(
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package androidx.collection;

                    import java.util.Collection;
                    import java.util.HashSet;
                    import java.util.Set;

                    public class ArraySet<E> extends HashSet<E> implements Set<E> {
                        public ArraySet() {
                        }
                    }
                        """
                    ),
                    kotlin(
                        "src/main/java/androidx/collection/ArraySet.kt",
                        """
                    package androidx.collection

                    inline fun <T> arraySetOf(): ArraySet<T> = ArraySet()

                    fun <T> arraySetOf(vararg values: T): ArraySet<T> {
                        val set = ArraySet<T>(values.size)
                        for (value in values) {
                            set.add(value)
                        }
                        return set
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Missing inner nullability`() {
        check(
            apiLint = "",
            extraArguments = arrayOf(ARG_ERROR, "MissingInnerNullability"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: Missing nullability on method `getArray` return [MissingNullability]
                    src/test/pkg/Foo.java:5: error: Missing nullability on inner type java.lang.String in method `getArray` return [MissingInnerNullability]
                    src/test/pkg/Foo.java:6: error: Missing nullability on method `getMap` return [MissingNullability]
                    src/test/pkg/Foo.java:6: error: Missing nullability on inner type java.lang.Number in method `getMap` return [MissingInnerNullability]
                    src/test/pkg/Foo.java:6: error: Missing nullability on inner type java.lang.String in method `getMap` return [MissingInnerNullability]
                    src/test/pkg/Foo.java:7: error: Missing nullability on method `getWildcardMap` return [MissingNullability]
                    src/test/pkg/Foo.java:7: error: Missing nullability on inner type java.lang.Number in method `getWildcardMap` return [MissingInnerNullability]
                    src/test/pkg/Foo.java:7: error: Missing nullability on inner type java.lang.String in method `getWildcardMap` return [MissingInnerNullability]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            import java.util.Map;
                            public class Foo {
                                public String[] getArray() { return null; }
                                public Map<Number, String> getMap() { return null; }
                                public Map<? extends Number, ? super String> getWildcardMap() { return null; }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test inner type nullness overrides of defined nullness`() {
        check(
            apiLint = "",
            extraArguments = arrayOf(ARG_HIDE, "NullableCollectionElement"),
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:7: error: Invalid nullability on type java.lang.String method `foo` return. Method override cannot use a nullable type when the corresponding type from the super method is non-null. [InvalidNullabilityOverride]
                    src/test/pkg/Foo.java:7: error: Invalid nullability on type java.lang.String in parameter `arg` in method `foo`. Parameter in method override cannot use a non-null type when the corresponding type from the super method is nullable. [InvalidNullabilityOverride]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            import libcore.util.NonNull;
                            import libcore.util.Nullable;
                            public class Superclass {
                                public @NonNull String @NonNull [] foo(@NonNull List<@Nullable String> arg) { return null; }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import java.util.List;
                            import libcore.util.NonNull;
                            import libcore.util.Nullable;
                            public class Foo extends Superclass {
                                @Override
                                public @Nullable String @NonNull [] foo(@NonNull List<@NonNull String> arg) { return null; }
                            }
                        """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource,
                )
        )
    }

    @Test
    fun `Test inner type nullness overrides of platform nullness`() {
        // TODO (b/344859664): this case is ignored for now
        check(
            apiLint = "",
            extraArguments = arrayOf(ARG_HIDE, "NullableCollectionElement"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import libcore.util.NonNull;
                            import libcore.util.Nullable;
                            public class Superclass {
                                public String @NonNull [] foo(@NonNull List<String> arg) { return null; }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import libcore.util.NonNull;
                            import libcore.util.Nullable;
                            public class Foo extends Superclass {
                                @Override
                                public @Nullable String @NonNull [] foo(@NonNull List<@NonNull String> arg) { return null; }
                            }
                        """
                    ),
                    libcoreNonNullSource,
                    libcoreNullableSource,
                )
        )
    }
}
