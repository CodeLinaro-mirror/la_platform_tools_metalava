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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import org.intellij.lang.annotations.Language

// Base class to collect test inputs whose behaviors (API/lint) vary depending on UAST versions.
abstract class UastTestBase : DriverTest() {

    private fun uastCheck(
        isK2: Boolean,
        format: FileFormat = FileFormat.latest,
        sourceFiles: Array<TestFile> = emptyArray(),
        @Language("TEXT")
        api: String? = null,
        extraArguments: Array<String> = emptyArray(),
    ) {
        check(
            format = format,
            sourceFiles = sourceFiles,
            api = api,
            extraArguments = extraArguments + listOfNotNull(ARG_USE_K2_UAST.takeIf { isK2 })
        )
    }

    protected fun `Kotlin language level`(isK2: Boolean) {
        // static method in interface is not overridable.
        // TODO: SLC in 1.9 will not put `final` on @JvmStatic method in interface
        //  https://github.com/JetBrains/kotlin/commit/9204f8162e69deb6c1362fb67ab59bfc9b0a5fa6
        //  Put this back to Java9LanguageFeaturesTest, before `Basic class signature extraction`
        val f = if (isK2) " final" else ""
        // See https://kotlinlang.org/docs/reference/whatsnew13.html
        uastCheck(
            isK2,
            format = FileFormat.V1,
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg
                    interface Foo {
                        companion object {
                            @JvmField
                            const val answer: Int = 42
                            @JvmStatic
                            fun sayHello() {
                                println("Hello, world!")
                            }
                        }
                    }
                    """
                )
            ),
            api =
            """
                package test.pkg {
                  public interface Foo {
                    method public default static$f void sayHello();
                    field @NonNull public static final test.pkg.Foo.Companion Companion;
                    field public static final int answer = 42; // 0x2a
                  }
                  public static final class Foo.Companion {
                    method public void sayHello();
                  }
                }
                """,
            // The above source uses 1.3 features, though UAST currently
            // seems to still treat it as 1.3 despite being passed 1.2
            extraArguments = arrayOf(ARG_KOTLIN_SOURCE, "1.2")
        )
    }

    protected fun `Test RequiresOptIn and OptIn`(isK2: Boolean) {
        // See http://b/248341155 for more details
        val klass = if (isK2) "Class" else "kotlin.reflect.KClass"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                    package test.pkg

                    @RequiresOptIn
                    @Retention(AnnotationRetention.BINARY)
                    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
                    annotation class ExperimentalBar

                    @ExperimentalBar
                    class FancyBar

                    @OptIn(FancyBar::class) // @OptIn should not be tracked as it is not API
                    class SimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }

                    @androidx.annotation.experimental.UseExperimental(FancyBar::class) // @UseExperimental should not be tracked as it is not API
                    class AnotherSimpleClass {
                        fun methodUsingFancyBar() {
                            val fancyBar = FancyBar()
                        }
                    }
                """
                ),
                kotlin(
                    """
                    package androidx.annotation.experimental

                    import kotlin.annotation.Retention
                    import kotlin.annotation.Target
                    import kotlin.reflect.KClass

                    @Retention(AnnotationRetention.BINARY)
                    @Target(
                        AnnotationTarget.CLASS,
                        AnnotationTarget.PROPERTY,
                        AnnotationTarget.LOCAL_VARIABLE,
                        AnnotationTarget.VALUE_PARAMETER,
                        AnnotationTarget.CONSTRUCTOR,
                        AnnotationTarget.FUNCTION,
                        AnnotationTarget.PROPERTY_GETTER,
                        AnnotationTarget.PROPERTY_SETTER,
                        AnnotationTarget.FILE,
                        AnnotationTarget.TYPEALIAS
                    )
                    annotation class UseExperimental(
                        /**
                         * Defines the experimental API(s) whose usage this annotation allows.
                         */
                        vararg val markerClass: KClass<out Annotation>
                    )
                """
                )
            ),
            format = FileFormat.V3,
            api = """
                // Signature format: 3.0
                package androidx.annotation.experimental {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE, kotlin.annotation.AnnotationTarget.VALUE_PARAMETER, kotlin.annotation.AnnotationTarget.CONSTRUCTOR, kotlin.annotation.AnnotationTarget.FUNCTION, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER, kotlin.annotation.AnnotationTarget.FILE, kotlin.annotation.AnnotationTarget.TYPEALIAS}) public @interface UseExperimental {
                    method public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass();
                    property public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass;
                  }
                }
                package test.pkg {
                  public final class AnotherSimpleClass {
                    ctor public AnotherSimpleClass();
                    method public void methodUsingFancyBar();
                  }
                  @kotlin.RequiresOptIn @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.FUNCTION}) public @interface ExperimentalBar {
                  }
                  @test.pkg.ExperimentalBar public final class FancyBar {
                    ctor public FancyBar();
                  }
                  public final class SimpleClass {
                    ctor public SimpleClass();
                    method public void methodUsingFancyBar();
                  }
                }
            """
        )
    }

    protected fun `renamed via @JvmName`(isK2: Boolean, api: String) {
        // Regression test from http://b/257444932: @get:JvmName on constructor property
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg

                        class ColorRamp(
                            val colors: IntArray,
                            @get:JvmName("isInterpolated")
                            val interpolated: Boolean,
                        ) {
                            @get:JvmName("isInitiallyEnabled")
                            val initiallyEnabled: Boolean

                            @set:JvmName("updateOtherColors")
                            var otherColors: IntArray
                        }
                    """
                )
            ),
            api = api
        )
    }

    protected fun `Kotlin Reified Methods`(isK2: Boolean) {
        // TODO: once fix for https://youtrack.jetbrains.com/issue/KT-39209 is available (231),
        //  FE1.0 UAST will have implicit nullability too.
        //  Put this back to ApiFileTest, before `Kotlin Reified Methods 2`
        val n = if (isK2) " @Nullable" else ""
        uastCheck(
            isK2,
            format = FileFormat.V1,
            sourceFiles = arrayOf(
                java(
                    """
                    package test.pkg;

                    public class Context {
                        @SuppressWarnings("unchecked")
                        public final <T> T getSystemService(Class<T> serviceClass) {
                            return null;
                        }
                    }
                    """
                ),
                kotlin(
                    """
                    package test.pkg

                    inline fun <reified T> Context.systemService1() = getSystemService(T::class.java)
                    inline fun Context.systemService2() = getSystemService(String::class.java)
                    """
                )
            ),
            api = """
                package test.pkg {
                  public class Context {
                    ctor public Context();
                    method public final <T> T getSystemService(Class<T>);
                  }
                  public final class TestKt {
                    method$n public static inline <reified T> T systemService1(@NonNull test.pkg.Context);
                    method public static inline String systemService2(@NonNull test.pkg.Context);
                  }
                }
                """
        )
    }

    protected fun `Nullness in reified signatures`(isK2: Boolean) {
        // TODO: once fix for https://youtrack.jetbrains.com/issue/KT-39209 is available (231),
        //  FE1.0 UAST will have implicit nullability too.
        //  Put this back to ApiFileTest, before `Nullness in varargs`
        val n = if (isK2) "" else "!"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    "src/test/pkg/test.kt",
                    """
                    package test.pkg

                    import androidx.annotation.UiThread
                    import test.pkg2.NavArgs
                    import test.pkg2.NavArgsLazy
                    import test.pkg2.Fragment
                    import test.pkg2.Bundle

                    @UiThread
                    inline fun <reified Args : NavArgs> Fragment.navArgs() = NavArgsLazy(Args::class) {
                        throw IllegalStateException("Fragment $this has null arguments")
                    }
                    """
                ),
                kotlin(
                    """
                    package test.pkg2

                    import kotlin.reflect.KClass

                    interface NavArgs
                    class Fragment
                    class Bundle
                    class NavArgsLazy<Args : NavArgs>(
                        private val navArgsClass: KClass<Args>,
                        private val argumentProducer: () -> Bundle
                    )
                    """
                ),
                uiThreadSource
            ),
            api = """
                // Signature format: 3.0
                package test.pkg {
                  public final class TestKt {
                    method @UiThread public static inline <reified Args extends test.pkg2.NavArgs> test.pkg2.NavArgsLazy<Args>$n navArgs(test.pkg2.Fragment);
                  }
                }
                """,
//            Actual expected API is below. However, due to KT-39209 the nullability information is
//              missing
//            api = """
//                // Signature format: 3.0
//                package test.pkg {
//                  public final class TestKt {
//                    method @UiThread public static inline <reified Args extends test.pkg2.NavArgs> test.pkg2.NavArgsLazy<Args> navArgs(test.pkg2.Fragment);
//                  }
//                }
//                """,
            format = FileFormat.V3,
            extraArguments = arrayOf(
                ARG_HIDE_PACKAGE, "androidx.annotation",
                ARG_HIDE_PACKAGE, "test.pkg2",
                ARG_HIDE, "ReferencesHidden",
                ARG_HIDE, "UnavailableSymbol",
                ARG_HIDE, "HiddenTypeParameter",
                ARG_HIDE, "HiddenSuperclass"
            )
        )
    }

    protected fun `Annotations aren't dropped when DeprecationLevel is HIDDEN`(isK2: Boolean) {
        // Regression test for http://b/219792969
        // TODO: once fix for https://youtrack.jetbrains.com/issue/KTIJ-23807 is available (231),
        //  FE1.0 UAST will emit that too.
        //  Put this back to ApiFileTest, before `Constants in a file scope annotation`
        val n = if (isK2) " @NonNull" else ""
        uastCheck(
            isK2,
            format = FileFormat.V2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        import androidx.annotation.IntRange
                        @Deprecated(
                            message = "So much regret",
                            level = DeprecationLevel.HIDDEN
                        )
                        @IntRange(from=0)
                        fun myMethod() { TODO() }

                        @Deprecated(
                            message = "Not supported anymore",
                            level = DeprecationLevel.HIDDEN
                        )
                        fun returnsNonNull(): String = "42"

                        @Deprecated(
                            message = "Not supported anymore",
                            level = DeprecationLevel.HIDDEN
                        )
                        fun returnsNonNullImplicitly() = "42"
                    """
                ),
                androidxIntRangeSource
            ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            api = """
                // Signature format: 2.0
                package test.pkg {
                  public final class TestKt {
                    method @Deprecated @IntRange(from=0L) public static void myMethod();
                    method @Deprecated @NonNull public static String returnsNonNull();
                    method @Deprecated$n public static String returnsNonNullImplicitly();
                  }
                }
            """
        )
    }

    protected fun `Annotation on parameters of data class synthetic copy`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57003
        val typeAnno = if (isK2) "" else "@test.pkg.MyAnnotation "
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        annotation class MyAnnotation

                        data class Foo(@MyAnnotation val p1: Int, val p2: String)
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(@test.pkg.MyAnnotation int p1, String p2);
                    method public int component1();
                    method public String component2();
                    method public test.pkg.Foo copy(${typeAnno}int p1, String p2);
                    method public int getP1();
                    method public String getP2();
                    property public final int p1;
                    property public final String p2;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                  }
                }
            """
        )
    }

    protected fun `Member of companion object in value class`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57546
        // TODO: https://youtrack.jetbrains.com/issue/KT-57577
        val companionMembers = if (isK2) "" else """
                    method public float getCenter();
                    method public float getEnd();
                    method public float getStart();
                    property public final float Center;
                    property public final float End;
                    property public final float Start;"""
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        @kotlin.jvm.JvmInline
                        value class AnchorType internal constructor(internal val ratio: Float) {
                            companion object {
                                val Start = AnchorType(0f)
                                val Center = AnchorType(0.5f)
                                val End = AnchorType(1f)
                            }
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  @kotlin.jvm.JvmInline public final value class AnchorType {
                    field public static final test.pkg.AnchorType.Companion Companion;
                  }
                  public static final class AnchorType.Companion {$companionMembers
                  }
                }
        """
        )
    }

    protected fun `non-last vararg type`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57547
        val varargType = if (isK2) "java.lang.String..." else "String![]"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        fun foo(vararg vs: String, b: Boolean = true) {
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class TestKt {
                    method public static void foo($varargType vs, optional boolean b);
                  }
                }
            """
        )
    }

    protected fun `implements Comparator`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57548
        val inherit = if (isK2) "extends" else "implements"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        class Foo(val x : Int)
                        class FooComparator : Comparator<Foo> {
                          override fun compare(firstFoo: Foo, secondFoo: Foo): Int =
                            firstFoo.x - secondFoo.x
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(int x);
                    method public int getX();
                    property public final int x;
                  }
                  public final class FooComparator $inherit java.util.Comparator<test.pkg.Foo> {
                    ctor public FooComparator();
                    method public int compare(test.pkg.Foo firstFoo, test.pkg.Foo secondFoo);
                  }
                }
            """
        )
    }

    protected fun `constant in file-level annotation`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57550
        val c = if (isK2) "31L" else "31"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        @file:RequiresApi(31)
                        package test.pkg
                        import androidx.annotation.RequiresApi

                        @RequiresApi(31)
                        fun foo(p: Int) {}
                    """
                ),
                requiresApiSource
            ),
            extraArguments = arrayOf(ARG_HIDE_PACKAGE, "androidx.annotation"),
            api = """
                package test.pkg {
                  @RequiresApi($c) public final class TestKt {
                    method @RequiresApi(31) public static void foo(int p);
                  }
                }
            """
        )
    }

    protected fun `final modifier in enum members`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57567
        val f = if (isK2) "" else " final"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        enum class Event {
                          ON_CREATE, ON_START, ON_STOP, ON_DESTROY;
                          companion object {
                            @JvmStatic
                            fun upTo(state: State): Event? {
                              return when(state) {
                                State.ENQUEUED -> ON_CREATE
                                State.RUNNING -> ON_START
                                State.BLOCKED -> ON_STOP
                                else -> null
                              }
                            }
                          }
                        }
                        enum class State {
                          ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED;
                          val isFinished: Boolean
                            get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
                          fun isAtLeast(state: State): Boolean {
                            return compareTo(state) >= 0
                          }
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public enum Event {
                    method public static$f test.pkg.Event? upTo(test.pkg.State state);
                    method public static test.pkg.Event valueOf(String value) throws java.lang.IllegalArgumentException, java.lang.NullPointerException;
                    method public static test.pkg.Event[] values();
                    enum_constant public static final test.pkg.Event ON_CREATE;
                    enum_constant public static final test.pkg.Event ON_DESTROY;
                    enum_constant public static final test.pkg.Event ON_START;
                    enum_constant public static final test.pkg.Event ON_STOP;
                    field public static final test.pkg.Event.Companion Companion;
                  }
                  public static final class Event.Companion {
                    method public test.pkg.Event? upTo(test.pkg.State state);
                  }
                  public enum State {
                    method public$f boolean isAtLeast(test.pkg.State state);
                    method public$f boolean isFinished();
                    method public static test.pkg.State valueOf(String value) throws java.lang.IllegalArgumentException, java.lang.NullPointerException;
                    method public static test.pkg.State[] values();
                    property public final boolean isFinished;
                    enum_constant public static final test.pkg.State BLOCKED;
                    enum_constant public static final test.pkg.State CANCELLED;
                    enum_constant public static final test.pkg.State ENQUEUED;
                    enum_constant public static final test.pkg.State FAILED;
                    enum_constant public static final test.pkg.State RUNNING;
                    enum_constant public static final test.pkg.State SUCCEEDED;
                  }
                }
            """
        )
    }

    protected fun `lateinit var as mutable bare field`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57569
        val additional = if (isK2) """
                    field public java.util.List<test.pkg.Bar> bars;""" else ""
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        class Bar
                        class Foo {
                          lateinit var bars: List<Bar>
                            private set
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public final class Bar {
                    ctor public Bar();
                  }
                  public final class Foo {
                    ctor public Foo();
                    method public java.util.List<test.pkg.Bar> getBars();
                    property public final java.util.List<test.pkg.Bar> bars;$additional
                  }
                }
            """
        )
    }

    protected fun `Upper bound wildcards`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57578
        val upperBound = if (isK2) "" else "? extends "
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        enum class PowerCategoryDisplayLevel {
                          BREAKDOWN, TOTAL
                        }

                        enum class PowerCategory {
                          CPU, MEMORY
                        }

                        class PowerMetric {
                          companion object {
                            @JvmStatic
                            fun Battery(): Type.Battery {
                              return Type.Battery()
                            }

                            @JvmStatic
                            fun Energy(
                              categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ): Type.Energy {
                              return Type.Energy(categories)
                            }

                            @JvmStatic
                            fun Power(
                              categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ): Type.Power {
                              return Type.Power(categories)
                            }
                          }
                          sealed class Type(var categories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()) {
                            class Power(
                              powerCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ) : Type(powerCategories)

                            class Energy(
                              energyCategories: Map<PowerCategory, PowerCategoryDisplayLevel> = emptyMap()
                            ) : Type(energyCategories)

                            class Battery : Type()
                          }
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public enum PowerCategory {
                    method public static test.pkg.PowerCategory valueOf(String value) throws java.lang.IllegalArgumentException, java.lang.NullPointerException;
                    method public static test.pkg.PowerCategory[] values();
                    enum_constant public static final test.pkg.PowerCategory CPU;
                    enum_constant public static final test.pkg.PowerCategory MEMORY;
                  }
                  public enum PowerCategoryDisplayLevel {
                    method public static test.pkg.PowerCategoryDisplayLevel valueOf(String value) throws java.lang.IllegalArgumentException, java.lang.NullPointerException;
                    method public static test.pkg.PowerCategoryDisplayLevel[] values();
                    enum_constant public static final test.pkg.PowerCategoryDisplayLevel BREAKDOWN;
                    enum_constant public static final test.pkg.PowerCategoryDisplayLevel TOTAL;
                  }
                  public final class PowerMetric {
                    ctor public PowerMetric();
                    method public static test.pkg.PowerMetric.Type.Battery Battery();
                    method public static test.pkg.PowerMetric.Type.Energy Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    method public static test.pkg.PowerMetric.Type.Power Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    field public static final test.pkg.PowerMetric.Companion Companion;
                  }
                  public static final class PowerMetric.Companion {
                    method public test.pkg.PowerMetric.Type.Battery Battery();
                    method public test.pkg.PowerMetric.Type.Energy Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                    method public test.pkg.PowerMetric.Type.Power Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> categories);
                  }
                  public abstract static sealed class PowerMetric.Type {
                    method public final java.util.Map<test.pkg.PowerCategory,test.pkg.PowerCategoryDisplayLevel> getCategories();
                    method public final void setCategories(java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel>);
                    property public final java.util.Map<test.pkg.PowerCategory,test.pkg.PowerCategoryDisplayLevel> categories;
                  }
                  public static final class PowerMetric.Type.Battery extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Battery();
                  }
                  public static final class PowerMetric.Type.Energy extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> energyCategories);
                  }
                  public static final class PowerMetric.Type.Power extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> powerCategories);
                  }
                }
            """
        )
    }

    protected fun `boxed type argument as method return type`(isK2: Boolean) {
        // TODO: https://youtrack.jetbrains.com/issue/KT-57579
        val b = if (isK2) "boolean" else "Boolean"
        uastCheck(
            isK2,
            sourceFiles = arrayOf(
                kotlin(
                    """
                        package test.pkg
                        abstract class ActivityResultContract<I, O> {
                          abstract fun parseResult(resultCode: Int, intent: Intent?): O
                        }

                        interface Intent

                        class StartActivityForResult : ActivityResultContract<Intent, Boolean>() {
                          override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
                            return resultCode == 42
                          }
                        }
                    """
                )
            ),
            api = """
                package test.pkg {
                  public abstract class ActivityResultContract<I, O> {
                    ctor public ActivityResultContract();
                    method public abstract O parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                  public interface Intent {
                  }
                  public final class StartActivityForResult extends test.pkg.ActivityResultContract<test.pkg.Intent,java.lang.Boolean> {
                    ctor public StartActivityForResult();
                    method public $b parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                }
            """
        )
    }
}
