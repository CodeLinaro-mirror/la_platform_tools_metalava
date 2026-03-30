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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.cli.common.ARG_ERROR
import com.android.tools.metalava.cli.common.ARG_HIDE
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.KnownSourceFiles
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
                    KnownSourceFiles.androidxNonNullJavaSource,
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
                    KnownSourceFiles.androidxNullableJavaSource,
                    KnownSourceFiles.androidxNonNullJavaSource
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
                src/test/pkg/Bar.kt:15: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
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
                        fun error1(int: Int = 0, int2: Int = 0) { }
                        fun String.ok4(int: Int = 0, int2: Int = 0) { }
                        inline fun ok5(int: Int, int2: Int) { }
                        fun error2(int: Int = 0) = Unit
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
    fun `Check methods and properties in a named companion object aren't required to be annotated JvmStatic`() {
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

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test file location for error on parameter within multifile class`() {
        check(
            apiLint = "", // Enabled
            // TODO b(450539561): all [KotlinDefaultParameterOrder] issues have file location
            //  `test/pkg/Foo1.kt:4`, but two of them are from `test/pkg/Foo2.kt`.
            expectedIssues =
                """
                test/pkg/Foo1.kt:4: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
                test/pkg/Foo1.kt:4: error: Parameter `i1` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
                test/pkg/Foo1.kt:4: error: Parameter `i2` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
                test/pkg/Foo1.kt:4: error: Parameter `i3` has a default value and should come after all parameters without default values (except for a trailing lambda parameter) [KotlinDefaultParameterOrder]
                test/pkg/Foo2.kt:4: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
                test/pkg/Foo2.kt:5: warning: A Kotlin method with default parameter values should be annotated with @JvmOverloads for better Java interoperability; see https://android.github.io/kotlin-guides/interop.html#function-overloads-for-defaults [MissingJvmstatic]
                """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        "test/pkg/Foo1.kt",
                        """
                        @file:JvmName("Foo")
                        @file:JvmMultifileClass
                        package test.pkg
                        fun foo1(i1: Int = 0, s1: String)
                        """
                    ),
                    kotlin(
                        "test/pkg/Foo2.kt",
                        """
                        @file:JvmName("Foo")
                        @file:JvmMultifileClass
                        package test.pkg
                        fun foo2(i2: Int = 0, s2: String)
                        fun foo3(i3: Int = 0, s3: String)
                        """
                    ),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    method public static void foo1(optional int i1, String s1);
                    method public static void foo2(optional int i2, String s2);
                    method public static void foo3(optional int i3, String s3);
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of value class type without JvmName for method return`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:4: error: Method withoutJvmName returning value class type should use JvmName to be usable for Java clients [ValueClassUsageWithoutJvmName]
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ValueClassDefinition",
                    ARG_ERROR,
                    "ValueClassUsageWithoutJvmName",
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int) {
                                fun withoutJvmName(value: Int) = IntValue(value)
                                @JvmName("withJvmName")
                                fun withJvmName(value: Int) = IntValue(value)
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of value class type without JvmName for method parameters`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:4: error: Method oneParamWithoutJvmName with parameter intValue of value class type should use JvmName to be usable for Java clients [ValueClassUsageWithoutJvmName]
                src/test/pkg/IntValue.kt:7: error: Method manyParamsWithoutJvmName with parameter arg1 of value class type should use JvmName to be usable for Java clients [ValueClassUsageWithoutJvmName]
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ValueClassDefinition",
                    ARG_ERROR,
                    "ValueClassUsageWithoutJvmName",
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int) {
                                fun oneParamWithoutJvmName(intValue: IntValue) = Unit
                                @JvmName("oneParamWithJvmName")
                                fun oneParamWithJvmName(intValue: IntValue) = Unit
                                fun manyParamsWithoutJvmName(arg0: Int, arg1: IntValue, arg2: IntValue, arg3: String) = Unit
                                @JvmName("manyParamsWithJvmName")
                                fun manyParamsWithJvmName(arg0: Int, arg1: IntValue, arg2: IntValue, arg3: String) = Unit
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of value class type without JvmName for properties`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:6: error: Property withoutJvmName with value class type should use `@get:JvmName` to have a usable getter for Java clients [ValueClassUsageWithoutJvmName]
                src/test/pkg/IntValue.kt:6: error: Property withoutJvmName with value class type should use `@set:JvmName` to have a usable setter for Java clients [ValueClassUsageWithoutJvmName]
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ValueClassDefinition",
                    ARG_ERROR,
                    "ValueClassUsageWithoutJvmName",
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int) {
                                companion object {
                                    @JvmStatic
                                    var withoutJvmName = IntValue(0)
                                    @JvmStatic
                                    @get:JvmName("getWithJvmName")
                                    @set:JvmName("setWithJvmName")
                                    var withJvmName = IntValue(0)
                                }
                            }
                        """
                    ),
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/41WdzAc6hbf4MpiEbIssSJYiRK76oroxOqr10hYvbN6i0SN" +
                        "Fr0TvfeoEWKvsiRqiChB9Brt6gR5ct/Me8mdd++8883vj2/m+35nzpk55/dT" +
                        "UyIkAgOAQCAAAGAG/BxgABFARVZLilsBjUKoSKEVULKaWnAV1EUPALCn0ter" +
                        "rMQNHyJX4uYc6Bus1eAZ4Z9dcoYrqtxVUBlyK63T2FHkxnIq9vVx6ewMIN6/" +
                        "71tYml8iAKgpXQVWXeeoEr5MIHQJtb9ND7mEq7mLK8LJ1hKh4OCqg7FzM4eb" +
                        "2mFcXF7ofNVk0AZ/n+63rdU39k+Kk6yntCNMIK4eNViNN2imK5ZWKSjepG7A" +
                        "KNQo1KY/c/TtWzWavYpbPZaV2GlrII18ia3223N0hg5OT8NiOqP4zQafZLj4" +
                        "rVwMNK3sjkngzilmQa1mq3VuvWhwbWqQzWcObt1RGWNQT6nOELVwfx/MAV6x" +
                        "bi1CxBSYMvSIkdjIV80cqDVFr2kedCuE+VbfbKMuKCJHZGLRJyCnH943ydbR" +
                        "F0l3YhI1Z2zIqDw6NCU2LDLy2r3ebjHpQAPICGpT6BDYiEDTcdC/XI3Lzk7P" +
                        "yQsHe7A035q/ZW2Lz3WA80d2psemtYllk9A//9B7jqI/fSjbpPsqmuvQBV9i" +
                        "ySXeTZN1B14mygMNbTSW7FKHLrzpDlOrSKBPHApxD6MTl4JVWo32aGH0j+T3" +
                        "NT0TogbeQOalBp0aQoClpdwLYGI2kOkDVEY0EYyO5TSwJqlWVFcMKziRchbW" +
                        "JgLSmDDkLBcx4Hv8IOZhVg41+ziXd1hO2UR4isejvbuNzpymoIWpQa8jIAeJ" +
                        "X6Ys2yMe7mYt9GRwHCRJD+/OArVTN2gB3W6q5dKKpiH2GnyF4hh4XbprCsTz" +
                        "XtBbhmoIziA3EqUnMisXLetbxPmSDW40N4t2XMvkXIxIswYrh3T0GxPW2wdo" +
                        "8/aTfXcLabVkD+FhXSAusqcVIpmyIVCtWBlDjdXyfVlcScFDZaziRlBw9ZxG" +
                        "nzbvHi2B93ZtA/QWJvVxqp5lna9nYBwL3JBVe766Q/qe1wveRvlO9a4GjeX+" +
                        "t28SD8vyGU423l4QOwqYqWBAvmzj3Zjedt/WMbN3hFmPzTlPd9ogqe58FN6u" +
                        "icYfZU5kkyJSDeBYFVxVosuh1lsLizqNpMr0yRs6x9bk9JDpgEE+qCqtYmpn" +
                        "qQMiUaCjjjrNmtauvxCmHzYRIPZwYCn0fSZxgHb87Yz5gfWuSAfb3rSKobSY" +
                        "quPiAr4Lkj3KYiCJop7QceS6ql7hm2/WHgspsH3SuOwNB/n9O+y5sNArcVRx" +
                        "UnGbsCfzH6vgR1sO0LWbENSE1+iT+jyuYiAvZuRo/3m20T6X/1oKy7CoKJxT" +
                        "o2+mFzXxQgKH9WANF61gtzdMH02pHUXThg9L2SraV2iupX0t96LhyEgxAnHm" +
                        "FBnx+3cr6QahqUqSdtfKVVKzuNIbYse0a030ujTQOI2VqPBoIXqbdgUWm4YM" +
                        "o2GlRoHhuf6cmFFnjgqznabGcaSj7dpGpM7+co1Nmrc1Z5F+Enog/0S6KWG0" +
                        "isEXsT5XmKZWEgwcEZTEsePCwbtyAmRxffEb89vUCmHJVawfo0TlE6mc6sQL" +
                        "9QOKIsyMVyHy6QLXdsJWiHbmNE97KglYBHKhexYb9Djlz76IU6KediSF64Ed" +
                        "M+1kO8bciUyugWenVpPC7Y56y2zn754RRxkZOyzXvIhE7k1YsY4+K8H6ILvL" +
                        "1Lvdnbxr226pEQpri1gQvrPKU2EMOq2xdIlHdyvdQ55dDXKk2rGn2JYeb1eC" +
                        "mJzP/FEys2Qb2MoMnMMNbb3ZyJHl22ufzat9QEM5C338R8beFLaCh9LK8+r1" +
                        "g1DDvWoIFZPwcvjvky2WfkP8ySENWEzxtq485cPlgQO2T74KaHW15BWVtj23" +
                        "zgVUqGANy2u2/AdYkmSxzfjF1O3gLEm9UPsFG9PW9hTnyPsnmhMpVTaJr2gT" +
                        "afCCLZ+dWuXLt32CziTat8viGW/VILaXOAO+3vxE1bKcgehhgg7qNgZKC3sH" +
                        "LdJ+GXe9rX4xOhLewW0aCy72XmFC0nRzdz9JkIDnT264trmdxl6/IhPu18g9" +
                        "HLUr1F1rJem1glO7kTHFfDhT8bJgJvBAol0sudu767fhEZvnpY82xk2nbO9S" +
                        "YJjOG1tgiPqAoYdtU44ked8p6U7NiwWbuixffpOFLJJYQMaROkysa5NSUSzu" +
                        "wRfVxANG1cCTzdxYyixhrsgvQv4GRuAsESumebI4/RupMLIgfcMgVTwhDRsN" +
                        "rd2N3PLcgHpZHrKvnk+prt59Rytefc25FcINMgDqAQ3urZRnFKBtMVPtHGRY" +
                        "MrmXeGTEBe7ZKeKHjjzlRU0TEgMA6qB/0hHm/6UjMBlHeyeMg7Wjw78VJVF7" +
                        "5lJR6MSOLGon7KT1gMOABkVy0vqYZ8Xa1QUq3WhoMf9I2MDOhya7SleFmt6N" +
                        "tySifOKrMzlVp+3xkZQU12fO8qC46SAkrC4vdeLbwrdWly1hn+3ps/NBpqcE" +
                        "enLBGLRSgZLci0CNT2OCQ3a9JmxWjCQzZGdgukAX9vu5I+ALqbWv5zJr62Bq" +
                        "0HLhis+puIwJtiRJlrqQnJCDWAa3A+7o3xTaceDpg8qfVxQZEdmfaxcdaNUt" +
                        "7jrYPz3wenKnsJxPOfJaPg/KO/irYkE463GuTwL+Xg3S2cnqimCN5Ga+m6G4" +
                        "dUwXfl1jk+aTvLmyYLDvgwNaGZt4ziSmGH3UG9rHZS3svhIfpONlvAk/Tr6Q" +
                        "FBLDuY5bJxbv6idg33tXqn6Q41cm10apl/Kf3Xv42sZTbb4ic9IjVJMcS1vp" +
                        "zfBgeoveJxrjHroXfvEJL6WfWWW+GmGx9urscV36G2nB+3bN94EeD8Zj5ro0" +
                        "+omj0ZKtq62K4o54/ZDE792ob09JxmMNRoJpwQsV6AAKeESa68izwyBkQpbn" +
                        "ULly3ilnRxWkSBOJcEoXgRQhGz0dTczLt6LD2vFrNxC1chwRHk95mzo8xl4u" +
                        "3kUw2BbGh576qDZk+o6dcxyNCTffKwhRnxBQN79N3ut6XI4pgH5twBQw+hxV" +
                        "xr9gXk46uf9OD/QeH4Pt3rr4nd99eP3Ct6xPftgX5vVCFrWbpxty1zLqaqOg" +
                        "h+/SIm++0WNOzrdBFQICjFmKakUBf9gPJqtOVjyh92M9TAenTlEYDpWHfYhY" +
                        "Ps8+17sZPExjMLq2tdHwZLPerF/zpjkTxc2IJr/KdY/K9xZlrYgORU12Nmsb" +
                        "CbLPOVy4/TTqMtf7kVa9xkOh4nRmXoEWBVaUJapfThhH6cnvIUsEF7L9EcX9" +
                        "h6QxISrnRHxYGEHVfluX8G0/hvY0Qr0FNnfqE/FD/nWvThGPqLW5YJ/ZtcdS" +
                        "TNAXDtmA5XxeGZqwFAvQWyaRiOZv2G/ZqgIMVcJynMfVXzyeH3weW53OvSMT" +
                        "27jWVBJqRbBn9iyajp2CoXrGzDjFIxPKfWYpt++mMn9+8dCwFSw1edSm78ta" +
                        "ZeRJpZSgNIu5CydYetcGuolvl4iGjOcdM5aeO1GGcW7pvDIs0NXJZe7Rvi9l" +
                        "q5W9QzV6NMs+TsrElUIqBBZXP9bC+D+OlU4CcSzSkJIegc+v38rjOflznkdm" +
                        "RZT8CQGAA+J/mmeGS/zHltpjrB3gto6udtYORvaOZm525qbGxsYWlyAyQROz" +
                        "q5l8MAH86TkPWFreXr/8Sfen57xCAAb8l/1nP/rD9P4af2eB/8ry8zaC/MLw" +
                        "9O+d7F9Jfm7Br2V3E/8fa+yvdD9XzfALXTLpP3ZRTek34h/PiC4P65VLv0/6" +
                        "4/YvaWo9aSwMAAA="
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of value class type without JvmName for extensions`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:5: error: Method withoutJvmName with parameter receiver of value class type should use JvmName to be usable for Java clients [ValueClassUsageWithoutJvmName]
                src/test/pkg/IntValue.kt:9: error: Property withoutJvmName with value class receiver type should use `@get:JvmName` to have a usable getter for Java clients [ValueClassUsageWithoutJvmName]
                src/test/pkg/IntValue.kt:9: error: Property withoutJvmName with value class receiver type should use `@set:JvmName` to have a usable setter for Java clients [ValueClassUsageWithoutJvmName]
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ValueClassDefinition",
                    ARG_ERROR,
                    "ValueClassUsageWithoutJvmName",
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int)

                            fun IntValue.withoutJvmName() = value
                            @JvmName("withJvmName")
                            fun IntValue.withJvmName() = value

                            var IntValue.withoutJvmName
                                get() = value
                                set(value) = Unit
                            @get:JvmName("getWithJvmName")
                            @set:JvmName("setWithJvmName")
                            var IntValue.withJvmName
                                get() = value
                                set(value) = Unit
                        """
                    ),
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/32WeTTU/xrHvzQMXcZurD9GkcEwjVxLqUb2naQh2zQMGjMm" +
                        "hoYoMvwQkvwsWfpJyJAsZcmEFtnGboxERHT9speJhKvfPefe6tz6fM7z3+e8" +
                        "nvN5zvO834+91R6QOMDHxwcAAAz49ogDIMDGxAmNsLA11bJB21qYmpx00rQx" +
                        "3e4EgA82zC5rK4Rmv6AVQq2H2VvjiBzSnpwJ0rS00bCw6Q+hP3BctkScV7Nk" +
                        "MtWdl3u0OjqY0zNTM9yAvRWY774Y/L7+bgLd3bD/aXroblB8gilaZIKvlgWJ" +
                        "4owNCPHRxAVgg4MjnbqDXzkJ7zx+p+XiLPfSfxDD3yfktxAxRFItSlW5YwE/" +
                        "RZYxT4QJQlv79SwDXmKqS9rGJrUmW3h4IW5BUdLmKEoc/1xycwJ25EBH6IsV" +
                        "ZGHY0kLT+MTiduit7e1P145xjbVmCsDZlLEIXHMam1jn7XQGL5CEHtceNW06" +
                        "N/LHl1FOvksoju+gucRgHZbPJ9jgxJ0FK+vTwjHiYLNc2PyiK+SZ+Qo3R+5Z" +
                        "6pkJck6Ge3bVuWS1ZzPl+eqc2xsXvC8UedYNVdMP47XjsHH/2tNVO8ajlZx2" +
                        "RXwA5ZSEFLig5POYOavspkIo7EcuyMdlz7eaQ2PA0bPibk+yWrqR1gKdq4w3" +
                        "9chc4biuncD1R8iImPr55RYy/HAUFw+b7D7yWtEoGV/0pxefRbZUk8X+RZme" +
                        "xaDINgFzgZPhQylOQrFUpPqVexZ7WVXOyOTcJDhTtT1RzCwfhr3EQtFtAZ2b" +
                        "rKtb3be6DH1Tz3BCt2J9iyaJxrGf3GWiHzUS/ik9AH0cH6bgSgkTvnvlcJaE" +
                        "3tKmcro3zQTKChoPHcOXzQ/noF0dD9RmYkktfgkHqYotvw0cqZVOD/aRUO0t" +
                        "r7iKok6nYPD8JfWeF8oy8iYI7FX1hZukG4qmh1gNGB13XBtZcGDF9bBxYFyC" +
                        "sqakfXrkQHMlzZhrHcK1Qwknu8/i3Gb/mou2VjitnG0lMir2wMzfbqCnqOY+" +
                        "tM1WCB8WjRAsKVRjU2EUDu6QRiyrPbHYcn+br/Kp4ae48pZ8xufhZpu7nYl0" +
                        "CD9h4KBd5n6hU/IqeaOsoqGP4fSrag4qrphEelVNoBS01HHD6fjtE68XIjMN" +
                        "FE1p+fbXMdRROjGn2/dFKEv83r3M5HMdfq5GkPQTshUOrz11ZKI1AofOdFdv" +
                        "jIga4R/7NDp3a7ikmNQJERsXZSpM3ItSgs9Vx+dLNgsYJLwwEVa2SbjDUMzg" +
                        "tL8NTBWaa1nRv+dgJ/0Umh/jGwXKuys75Q7qWUp7WN/FoD0KS7Ka69/nNyri" +
                        "cTLYrD6cpnt5RenTcRS1qys6gS0ZNSaASjXmPVDPWtoXsmdQdm8EiVI9bEve" +
                        "rOyFDQiixhE1WuYFEyVQWr75ZI0pKwE7WLzNvQO53vdmCL4u8jqbrdIEPTvy" +
                        "jnBjWIr6RWF/ZLuDY9qZ1Di98X10Y+5PKx6A5mel8imlglIx5hpqdXwabTr1" +
                        "tqHMjVHfEgkjZx64dvCY/vbht55gTk9bWiOmWheUhIQ+8vYQrH/jekx9TEFa" +
                        "J0aoUgozoeR9HnqZv6lUHlfa7Hle46ItfIX919Jp386RqI7OSZGj82VaBck7" +
                        "UZh1X9oJw+p9xpypsApRqpx9XouZR6Rcr+GH3kqTTc28/L6KG+87JY0Mjg47" +
                        "DFUlllCe30yxW56TzIoJCQjAPo+emsyOyarT1GvXj9eLN6hf8l/sbwxCvvca" +
                        "RQ8/0bHTf6u4ofBVfZquhH3UBwHAe/Cv1Ef6/6mPFeU/+pPs2Gsnhha/pL0Q" +
                        "mkn0L3ng7CosXib5pd+oL4v6JDXqqoSoDusOuKZPMODVuA6TMle1MrlTcEDI" +
                        "gyEtVFyqu6M6nePD9ep20Iiu9sRmk+7ExZpjlyM3IUAZOee4KcYtfaKW0xWn" +
                        "fg7fiDOUUBPmLvf/jG8gPtqcyRsvfvi7H+34Yrsk2MMsYKOndg5zgeD+7u4A" +
                        "USQ+I8kkpiXCQW6EoccMH3TxzxXlr55CKajwe5ch7B4Q68Y+5gXFH32aSErN" +
                        "MyyjejR8aVL22Go9yTmirfoGlwZ+uHaq2QX2Z7oAriquTMsMrSidcUyipG1e" +
                        "MeFamq1BOJ6/BrtX7FVBisKTAjFpFW8g18SKpKrSWDtWwDWKUVkuncCFN5ec" +
                        "LObpOsQqLZYYSJL0i9Xot3ybYLAuVWgN984OvbK1bvnZkFbomgv/R5H2qmXS" +
                        "2sXVZXIw8cPaZKSoRYKR01UlBL01K9GbmMuoql0BhykpvreWZxTSK/xihIIl" +
                        "lLYqhF9aFhtw9mEHO/pfttmlfXLlvkUdJyhm0UcrHo6fD733LHOWJHu9lF7n" +
                        "W0uYLUD0wvQ+MKb3vqoSYNc1JPfh2Gs0eEDvDOgj1OMduXe0UllHwZGAM8bw" +
                        "7nXQvxSP/k08CUSanEuJENqQie1FPe2OgonwkLDWcr5oHkH2EWuvjG2zUghc" +
                        "LLqa5iknIt+s14YKy2JE8UfedUIdus4tqbYE1mt6nimCPSI8m3KjwH5xoP4Z" +
                        "+skQz2dzQfd8/fvX4Bp/mOh+nCCNpOYw43Ou33S5g+DlSaykvYc8zVzWDwqJ" +
                        "lyK/jPLfE4iQQu6p4XWLbjAQhnwgP4TwQ/whbHKTnGSf/O9Sw9Mtem7vkB2w" +
                        "VvSG4NdmLmY6jsRxAwAfz6+aWXY3/uvkRKw/SZMQSAnwJ3kSA71DAnxwXl5e" +
                        "+N0AnbXlVZ0UnZ5Stdh1dvV2W+jUKlOd34oJt1nTdDjbdxb42777y7tI6rtE" +
                        "5N/2zcUtDvwv67fW/nV/+P78bJv4kfLtiEK/I1z++VLwI+Tb0kh/B9kC/WK2" +
                        "f8R8+1vZ7zCd4F9W1d6Kh/frM9DuleUCgKNfecC/ARYUrtJvCQAA"
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check usage of value class type in constructor parameters`() {
        check(
            apiLint = "",
            expectedIssues =
                """
                src/test/pkg/IntValue.kt:5: error: Constructor of class WithValueClass has parameter valueClassType of value class type which makes it unusable for Java clients [ValueClassUsageFromConstructor]
                src/test/pkg/IntValue.kt:6: error: Constructor of class WithValueClassAndAdditional has parameter arg1 of value class type which makes it unusable for Java clients [ValueClassUsageFromConstructor]
                """,
            extraArguments =
                arrayOf(
                    ARG_HIDE,
                    "ValueClassDefinition",
                    ARG_ERROR,
                    "ValueClassUsageFromConstructor",
                    // Enable lint for methods to make sure it doesn't appear here.
                    ARG_ERROR,
                    "ValueClassUsageWithoutJvmName",
                ),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int)

                            class WithValueClass(valueClassType: IntValue)
                            class WithValueClassAndAdditional(arg0: Int, arg1: IntValue, arg2: IntValue, arg3: String)
                            class WithoutValueClass(intType: Int)
                        """
                    ),
                ),
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                    "" +
                        "H4sIAAAAAAAA/5WXd1CTzRbGA9KrEDQUQaqEDlGUjoFQAgYC0iQgREIn1FCC" +
                        "8kkXUIoUKUoUIXSR8iEoCihIr6F3RKogRTSgILnovXOvOqPf3N05f7wz7/52" +
                        "Z86zz56DNDxCwwVgYGAAAABCgB8HF4AGgNAxg8rAjXTlEFAjuK7ORTNZhO5B" +
                        "BwCwjejqvGAoI9vPaigj2dPVW2UqP3h6dsFX1gAhDUf0+xf/bbppIOMjadDV" +
                        "JWWx2SPX3t71dmFugRqANKRneAyUeKx8uMG5w0D+dnvQYeAc/XBy3u7OcnBP" +
                        "nAXaw99R1sED7ecXYtbtN2F2lPJiSe6SxYkx1wErxj52l/fXBj3BxKRTeXAJ" +
                        "c29e/TghVtDrfiUDjzGryoLWyVm52UZaOjYb31AefQgumnElviEWPSreHtCy" +
                        "JZ+L33hfPzWzfhBw/+BgJ1GTavJ1OovECG7ymkND8gi2BmOGcmK5BZ06Pa5b" +
                        "7zaatj9OJlwKcGBQ0D82UINmcPRT0c57b3jB8mgEF73ePaG1dWu2Jv0tavKJ" +
                        "piTUjPfdO7aZFW7xkk0LpQQp8sPPgZhAol3NYGWxqtPpaHT08pHOJ5O0cvHJ" +
                        "4VwkiNkteZZAYccXXYtiNqfcc/vl3/NHZ6691gdF0Ictctm8zGjslr/A0vHh" +
                        "+Zta+XtHozspXrvP5K9F1K5tNnpLqIZS0Y54245OC2rFOxEf2DPAM7nr4aLr" +
                        "vD3rviGtLPosF4MHE8zYo4LkpcIfwZmGKizk4+/dkugCt8UB9QhC6L+GIMVG" +
                        "AMWsoZtfu+93qjsnocgBX6OcibNYWNSOLW/Yszr3szwk0IsY/ElrHP5oYbhq" +
                        "xjGljT2xVEykDmjIdypg0qlkbfgu1NpU/Ek62rPRJVYhSLBRgKT2hCfVz/EY" +
                        "uLe07CYk6G2ClRNjQa1dYMmd7Bn3kQ9S77M8UwR1zww9tVK0dWj1ZiVtWavC" +
                        "vKJjxWSPI1NDSA3lkTCqXTYqCi7Y23bRwWbx3UrYhZOWYpmGHOPAv/VcjUk9" +
                        "xKrHoFYjdid8mAxrQa7kSJAQjuxwRjpqqC0u30C01VnMfPiVQ2kj4fmX4QZE" +
                        "YUdcMRujO0nBOF2U3Zz/VPb4EHHwY3DxTUmTU9ZWccUVVV7coCLTz2bnH2pP" +
                        "vw9JVxHUjSQgb1sFjRdj73Y7twQMcT16lB7v1u5ircWWqs1XZjJtp8gbJu01" +
                        "iOqu/DzKqeX0wrHOolv6UoJODTu2bp23TMeWmODnVhlDON7AohLbonNUDBGb" +
                        "91zwDrlt3iuJfaVxS/mRiTHPKxAhwjmUJruQb86Wpmcjubq283nkM/wtw5V+" +
                        "EZdxjssX/fRqgyPPXd8S3jkPCersDIsdOR46yQJJgtGJ1w5tiPgfGeBjuuaJ" +
                        "qxw28t4r7xUisUKmZKrk9HNmCkCRBP3ZKt2hWPRA/gE1he1235tBiV2O6cyR" +
                        "U/WgK6NL7inD3EH7J0VD2kxMk1FJ0UpTIsUw6p2tywDZL8Klc8I5RcCuT5AP" +
                        "U2+hunPzT0tsntc2hgh5p4snKmgqH6jO29GTe1qT66wqz9Hckgc9w1xmrX1j" +
                        "rSk1eZJHMYK9nNtqRhjjA7rOWF/E71DUYOcjfdVIYmvk3Yalc8doaHvHLIfG" +
                        "WolcTjwl1GrXOVJbvVIERp7Dl3EGnUBmN+pdDjnRq77dW66zJ5tN6CtLWe04" +
                        "rqWiMWwyWBFXgGvOSjDeXDmeEeHv4YFuDpubzYzIqJFValOOUYpRqd1wXe+v" +
                        "85VftR+HDr9UNFaeF/x88pv71IfjPyrTAACr9H9yH/4f3cfSFefy3X60v7nP" +
                        "vz0oDdnpOXCeS/2tKJs/aRCRg7AWoGUykDIp4GB+wMAYniRN1AmXlK48n4Jx" +
                        "Ed/K/XBLZHQBtRtRJllxnsNQg8mndcXhNbUrbuPpksbS/sH2W/HQgqCu8vwo" +
                        "0vTK7uie6Cd4On2RC71NkTB3hOoccJ1WTYU8vaN35cHNNoVROUfRT1LR15pm" +
                        "1iHzvBNTECawSttbuv4vMMno4KWTuTs8kikly8sTRfb2GtolbwJbKtgw8/Gn" +
                        "csRFZJJj6KdRW1EFYZ6tt0lwlT4f38aUCn3ykjuW/vgdbosndCPH7jeVRqJ3" +
                        "KLoQabuzJxi6b9j4zZV6J4u8zFBgbgmVFQBSzLMK4S61rZuYs/78JU7Kd1Tv" +
                        "qnxkISDcq1w73SrP3TSfqB5+5LQ+Ejjhu51f9LqrMM2duK/vrNVPIolN87tz" +
                        "VvOzwwuICbea0bxkHd/mPSqFISO1WWi6OKkZlLetYJQSFiSOQFKSdK2jH0ql" +
                        "5a2tguMUR5IPEtSTZb0OtKch1iTFHYlgo2lhu1Hl1leX7vx1fjgvtlm7smSb" +
                        "WgnseIQJ7MDR5RgpCXIgmMRztE2PKd24emfFKfjc0pr5Y/WN7j5I8ZgCamwY" +
                        "oSZMeRUD8/MiJ237dLYWJyoOMjxcd+DnU5ePmqgd0Ibm5Hlb5pR6G+d8AH1T" +
                        "Te99w85VKgBgjfpPqpH8vWqgnhgoBuOKc/XyRHv8R0IXUcZAKNeBE+qZYxHS" +
                        "WlbmnJcNkySqr4aXsznJw7SYwxQIP9MUIKNdU+fkbHBtvmaXPejVrn1M4gH4" +
                        "baZEKgzfh/HrCey597kHS1567DVDpdZE1/SQKc9X0398HZWTcFdtjVZVKN1X" +
                        "1Q1bY60VqzSXw9qSpPmmYJIVPxhgx59NU195+0qbGug2EsVCjFvCf7qbEZIW" +
                        "U2iLwkLy8KS/sqK6y/tizcHCO/N/r8bp0zLerrucp03cJMajo+EUpU3VeCvF" +
                        "YPRulvhzPqRgkwHxwJm2euLDgnz4vSP0gdymx7Ds6C4oL74Yxs/cXm5DSS9z" +
                        "/9pRwtK12hRfP44B72dnonfIHINe7l8cN2/vaFUD9UzF1O1etwsYq0BNV0H3" +
                        "fOzR0Tm1yzOwa8tZR6M/UXIx3A315SDw9FxcSu87Qv7yMXmZ6GqA4rgQxTlH" +
                        "xm3CzaSJPt2ZOcG88oTUdfxin/NCla6rpUWdXru0LQFrbU6+UpTUjjptoaij" +
                        "ggiOwS0aTORVadZjSvfLF/MlTFvmuiWX+TMQSKzISNCLXJdFVR8pe+6Sm2CX" +
                        "j8/0oL6Nia/OewmR8aPzsX3ktIGxxKuxJ9eYsKkzH4XFnkEv6kMLJky4pMpG" +
                        "pCqgQKPVJ3BU8LxZt0PlNB9zW9pA1JrlrTJb+/1JmLpQUa5H8uoiPcQH/MAl" +
                        "yDejUTTb/YxOG8QlC9khyCeWt3KMLeda2mp2O7OSe5Xf9sJHWqfeboEzexYU" +
                        "TOM6y2g2Vd1p+8Ava3TdaEKQTG3+O73CZPLyIjV+Q0B44Q3hxp6AwKwQVDrY" +
                        "6tUHoYxmJskb3qJxnCWcQF0jeCn8guFoizSF7puauZU39wIOlaxB8yc1C/6q" +
                        "Zi9/3K826I9EGIpAuUrX4U44zm5uy/it7ULmVdjYzTIGSYYjJlBHtflBOC/2" +
                        "9MptXOKyTIMj5SWBhp2TvJzIOQN7rHVxw/xFcOLDmWm7y1T7LSraUwYOTXvT" +
                        "iKBHa9isMSvRTDWjSAUBbm1vcQFYBkMIkFCzgWkN9uaeDCGmqduDPWB3FSKq" +
                        "KnjSTSKXckMZk98/grB0dm88pqA3U59YGu8kh+mwnO6+7AYkNl101bG0grJN" +
                        "X/cGS3SiIfRJNXpfcjsuRTxbP9v0uF9+Ex+/p8bNGhKslvHAlI81MCiCH2E+" +
                        "FTqCb/pMGVmACtmNoz8XmeZpArGfpKM0jOpSiSQza4Lj1ycnbGIDtAyqMwNg" +
                        "nDfDOj6nL9vljz0QMTYzOi4HwZrbtXSAswTcsq3R5sWsT16uGNc/HAA9jTRs" +
                        "DijyLX59vzdjX4+1FYLWoFMrPdpznXc294wGEsZz8W31UEVDgmLx1bwLA6Wk" +
                        "cKBQjDwwJ+6S25bIqxOLKHs65sJq5BkwjzRvauvyX98ras1tfHz4oTsx/tGd" +
                        "+A7jvwU9Fu3qKevuhfNw9bTDemH8PRwd7O3tnQ6D5ooRHRh5pe8K4Dv7k/Dz" +
                        "F8DDldzfq3Uqai7A/+g/VvLf2oWfx++ah18pP77IoJ8I13/fA/wK+dGg+X+C" +
                        "fKX5h6f8V9SPt0PyJ9Rp+v/H33/l/pglwZ+4VEz/fNN+pf2YF76faP3Mf8wz" +
                        "0pCW7ttvdIfz9eFxZFi+ff0Lt3GMRggOAAA="
                )
        )
    }
}
