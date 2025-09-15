/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.lint.DefaultLintErrorMessage
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class KotlinInteropChecksTest : DriverTest() {
    @Test
    fun `Hard Kotlin keywords`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                    src/test/pkg/Test.java:6: error: Avoid method names that are Kotlin hard keywords ("fun"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                    src/test/pkg/Test.java:9: error: Avoid field names that are Kotlin hard keywords ("object"); see https://android.github.io/kotlin-guides/interop.html#no-hard-keywords [KotlinKeyword]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.NonNull;

                    public class Test {
                        public void fun() { }
                        public void foo(int fun) { }
                        @NonNull
                        public final Object object = null;
                    }
                    """
                    ),
                    androidxNonNullSource,
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Sam-compatible parameters should be last`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Test.java:20: warning: SAM-compatible parameters (such as parameter 1, "run", in test.pkg.Test.error1) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:23: warning: SAM-compatible parameters (such as parameter 2, "callback", in test.pkg.Test.error2) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:30: warning: SAM-compatible parameters (such as parameter 1, "lambda", in test.pkg.Test.error3) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:31: warning: SAM-compatible parameters (such as parameter 1, "lambda", in test.pkg.Test.error4) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                src/test/pkg/Test.java:35: warning: SAM-compatible parameters (such as parameter 1, "kotlinFunInterface", in test.pkg.Test.error5) should be last to improve Kotlin interoperability; see https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions [SamShouldBeLast]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import androidx.annotation.Nullable;
                    import androidx.annotation.NonNull;
                    import java.lang.Runnable;
                    import java.util.concurrent.Executor;
                    import java.util.function.Consumer;

                    public class Test {
                        public void ok1() { }
                        public void ok1(int x) { }
                        public void ok2(int x, int y) { }
                        public void ok3(@Nullable Runnable run) { }
                        public void ok4(int x, @Nullable Runnable run) { }
                        public void ok5(@Nullable Runnable run1, @Nullable Runnable run2) { }
                        public void ok6(@NonNull java.util.List<String> list, boolean b) { }
                        // Consumer declares exactly one non-default method (accept), other methods are default.
                        public void ok7(@NonNull String packageName, @NonNull Executor executor,
                            @NonNull Consumer<Boolean> callback) {}
                        public void error1(@NonNull Runnable run, int x) { }
                        // Executors, while they have a single method are not considered to be SAM that we want to be
                        // the last argument
                        public void error2(@NonNull String packageName, @NonNull Consumer<Boolean> callback,
                            @NonNull Executor executor) {}
                        // Iterables, while they have a single method are not considered to be SAM that we want to be
                        // the last argument
                        public void ok8(@Nullable Iterable<String> iterable, int x) { }
                        // Kotlin lambdas
                        public void ok9(int x, @NonNull kotlin.jvm.functions.Function0<Boolean> lambda) {}
                        public void error3(@NonNull kotlin.jvm.functions.Function0<Boolean> lambda, int x) {}
                        public void error4(@NonNull kotlin.jvm.functions.Function1<Boolean, Boolean> lambda, int x) {}
                        // Kotlin interface
                        public void ok10(@NonNull KotlinInterface kotlinInterface, int x) {}
                        // Kotlin fun interface
                        public void error5(@NonNull KotlinFunInterface kotlinFunInterface, int x) {}
                        public void ok11(int x, @NonNull KotlinFunInterface kotlinFunInterface) {}
                    }
                    """
                    ),
                    kotlin(
                        """
                    package test.pkg

                    interface KotlinInterface {
                        fun foo()
                    }

                    fun interface KotlinFunInterface {
                        fun foo()
                    }

                    // Check only runs on Java source
                    fun ok(bar: () -> Int, foo: Int) { }
                """
                    ),
                    androidxNullableSource,
                    androidxNonNullSource
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Companion object methods should be marked with JvmStatic`() {
        check(
            apiLint = "",
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "AllUpper",
                    ARG_HIDE,
                    "AcronymName",
                    ARG_HIDE,
                    "CompileTimeConstant"
                ),
            expectedIssues =
                """
                src/test/pkg/Foo.kt:8: warning: Companion object constants like BIG_INTEGER_ONE should be marked @JvmField for Java interoperability; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:10: warning: Companion object methods like getWrongNeedsJvmStatic should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]
                src/test/pkg/Foo.kt:10: warning: Companion object methods like setWrongNeedsJvmStatic should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]
                src/test/pkg/Foo.kt:12: warning: Companion object constants like WRONG should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:13: warning: Companion object constants like WRONG2 should be using @JvmField, not @JvmStatic; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]
                src/test/pkg/Foo.kt:16: warning: Companion object methods like missing should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    import java.math.BigInteger
                    @SuppressWarnings("all")
                    class Foo {
                        fun ok1() { }
                        companion object {
                            const val INTEGER_ONE = 1
                            val BIG_INTEGER_ONE: BigInteger = BigInteger.ONE // type specified to define nullability
                            private val PRIVATE_BIG_INTEGER: BigInteger = BigInteger.ONE
                            var wrongNeedsJvmStatic = 1
                            @JvmStatic var ok = 1.5
                            @JvmStatic val WRONG = 2
                            @JvmStatic @JvmField val WRONG2 = 2
                            @JvmField val ok3 = 3

                            fun missing() { }

                            @JvmStatic
                            fun ok2() { }
                        }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Methods with default parameters should specify JvmOverloads`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/Bar.kt:12: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg

                    interface Bar {
                        fun ok(int: Int = 0, int2: Int = 0) { }
                    }

                    class Foo(string: String = "default", long: Long = 0) {
                        fun ok1() { }
                        fun ok2(int: Int) { }
                        fun ok3(int: Int, int2: Int) { }
                        @JvmOverloads fun ok4(int: Int = 0, int2: Int = 0) { }
                        fun error(int: Int = 0, int2: Int = 0) { }
                        fun String.ok4(int: Int = 0, int2: Int = 0) { }
                        inline fun ok5(int: Int, int2: Int) { }
                    }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Methods annotated @JvmSynthetic with default parameters don't require @JvmOverloads`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface Bar
                        interface Baz

                        @JvmSynthetic
                        fun foo(bar: Bar, baz: Baz? = null) {
                        }
                    """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check value classes are banned`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                    src/test/pkg/Container.kt:4: error: Value classes should not be public in APIs targeting Java clients. [ValueClassDefinition]
                    src/test/pkg/PublicValueClass.kt:3: error: Value classes should not be public in APIs targeting Java clients. [ValueClassDefinition]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class PublicValueClass(val value: Int)
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            class Container {
                                @JvmInline
                                value class PublicNestedValueClass(val value: Int)
                            }
                        """
                    ),
                    kotlin(
                        """
                            package test.pkg
                            // This is okay, it isn't public API.
                            @JvmInline
                            internal value class InternalValueClass(val value: Int)
                        """
                    )
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of JvmStatic on hidden property`() {
        // Regression test for b/401569415 -- MissingJvmstatic should not apply to hidden properties
        check(
            apiLint = "",
            expectedIssues = "",
            extraArguments = arrayOf(ARG_HIDE, "StaticUtils"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            class Foo {
                                companion object {
                                    /** @hide */
                                    @JvmStatic
                                    val hiddenProperty = 0
                                }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of JvmStatic on property of value class type`() {
        // b/401569415 -- JvmField cannot be used on properties of value class type
        check(
            apiLint = "",
            expectedIssues =
                "src/test/pkg/IntValue.kt:13: warning: Companion object methods like getValueClassTypePropertyJvmNameNoStatic should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]",
            extraArguments = arrayOf(ARG_HIDE, "ValueClassDefinition"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int) {
                                companion object {
                                    @JvmStatic
                                    val valueClassTypePropertyJvmStatic = IntValue(0)

                                    // No error for this property, because there is not an accessor
                                    // that can be used from Java.
                                    val valueClassTypePropertyNoAnnotation = IntValue(0)

                                    @get:JvmName("getValueClassTypePropertyJvmNameNoStatic")
                                    val valueClassTypePropertyJvmNameNoStatic = IntValue(0)
                                }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check methods and properties in a named companion object should be annotated JvmStatic`() {
        check(
            apiLint = "",
            // TODO: this is inconsistent between methods and properties
            expectedIssues =
                "src/test/pkg/Foo.kt:9: warning: Companion object constants like missingJvmField should be marked @JvmField for Java interoperability; see https://developer.android.com/kotlin/interop#companion_constants [MissingJvmstatic]",
            extraArguments = arrayOf(ARG_HIDE, "StaticUtils"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            class Foo {
                                companion object FooCompanion {
                                    fun missingJvmStatic() = Unit

                                    @JvmStatic
                                    fun ok() = Unit

                                    val missingJvmField = 0

                                    @JvmField
                                    val ok = 0
                                }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check interface companion properties`() {
        check(
            apiLint = "",
            expectedIssues =
                "src/test/pkg/Foo.kt:10: warning: Companion object methods like getUnannotatedProperty should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            interface Foo {
                                companion object {
                                    // Cannot use @JvmField here, causes a compiler error:
                                    // JvmField could be applied only if all interface companion
                                    // properties are 'public final val' with '@JvmField' annotation
                                    @JvmStatic
                                    val jvmStaticProperty = 0

                                    val unannotatedProperty = 0

                                    private val privateProperty = 0
                                }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check companion property without backing field`() {
        check(
            apiLint = "",
            expectedIssues =
                "src/test/pkg/Foo.kt:11: warning: Companion object methods like getUnannotatedPropertyWithoutBackingField should be marked @JvmStatic for Java interoperability; see https://developer.android.com/kotlin/interop#companion_functions [MissingJvmstatic]",
            extraArguments = arrayOf(ARG_HIDE, "StaticUtils"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            class Foo {
                                companion object {
                                    // Cannot use @JvmField here: this annotation is not applicable
                                    // to target 'member property without backing field or delegate'
                                    @JvmStatic
                                    val jvmStaticPropertyWithoutBackingField
                                        get() = 0

                                    val unannotatedPropertyWithoutBackingField
                                        get() = 0
                                }
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check no JvmOverloads warning for data class copy method`() {
        check(
            apiLint = "", // Enabled
            expectedIssues =
                // Line 3 is where notCopy is defined. The copy method would get line 2 (where the
                // class/constructor is defined).
                "src/test/pkg/Foo.kt:3: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        data class Foo(val p0: Int = 0, val p1: String = "") {
                            fun notCopy(p0: Int = 0, p1: String = "") = Unit
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check no JvmOverloads warning for suspend function`() {
        check(
            apiLint = "", // Enabled
            expectedIssues =
                // Line 3 is where regularFun is defined
                "src/test/pkg/Foo.kt:3: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Foo {
                            fun regularFun(p0: Int = 0, p1: Int = 0) = Unit
                            suspend fun suspendFun(p0: Int = 0, p1: Int = 0) = Unit
                        }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check JvmName for file facade classes`() {
        check(
            apiLint = "", // Enabled
            expectedFail = DefaultLintErrorMessage,
            expectedIssues =
                """
                test/pkg/ErrorNeedsJvmName.kt:1: error: Use `@file:JvmName` to provide a name for this file facade class for Java callers [FacadeClassJvmName]
                """,
            hideAnnotations = arrayOf("test.pkg.Hide"),
            extraArguments = arrayOf(ARG_ERROR, "FacadeClassJvmName"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        "test/pkg/ErrorNeedsJvmName.kt",
                        """
                        package test.pkg
                        fun foo() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkUsesJvmName.kt",
                        """
                        @file:JvmName("OkUsesJvmName")
                        package test.pkg
                        fun foo() = Unit
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        annotation class Hide
                        """
                    ),
                    kotlin(
                        "test/pkg/OkOnlyHasHidden.kt",
                        """
                        package test.pkg
                        @Hide
                        fun foo() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkOnlyHasKotlinOnly.kt",
                        """
                        package test.pkg
                        inline fun <reified T> foo() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkOnlyHasSuspend.kt",
                        """
                        package test.pkg
                        suspend fun foo() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkSuppressesError.kt",
                        """
                        @file:Suppress("FacadeClassJvmName")
                        package test.pkg
                        fun foo() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkMultiFile1.kt",
                        """
                        @file:JvmMultifileClass
                        @file:JvmName("OkMultiFile")
                        package test.pkg
                        fun multiFile1() = Unit
                        """
                    ),
                    kotlin(
                        "test/pkg/OkMultiFile2.kt",
                        """
                        @file:JvmMultifileClass
                        @file:JvmName("OkMultiFile")
                        package test.pkg
                        fun multiFile2() = Unit
                        """
                    ),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class ErrorNeedsJvmNameKt {
                    method public static void foo();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Hide {
                  }
                  public final class OkMultiFile {
                    method public static void multiFile1();
                    method public static void multiFile2();
                  }
                  public final class OkOnlyHasKotlinOnlyKt {
                    method @KotlinOnly public static inline <reified T> void foo();
                  }
                  public final class OkOnlyHasSuspendKt {
                    method public static suspend Object? foo(kotlin.coroutines.Continuation<? super kotlin.Unit>);
                  }
                  public final class OkSuppressesErrorKt {
                    method public static void foo();
                  }
                  public final class OkUsesJvmName {
                    method public static void foo();
                  }
                }
                """,
        )
    }
}
