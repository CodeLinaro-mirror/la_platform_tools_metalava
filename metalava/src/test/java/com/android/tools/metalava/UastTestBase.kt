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

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.FilterAction.EXCLUDE
import com.android.tools.metalava.model.testing.FilterByProvider
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.stripBlankLines
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.defaultJvmPlatforms
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

/** Base class to collect test inputs whose behaviors (API/lint) vary depending on UAST versions. */
@RequiresCapabilities(Capability.KOTLIN)
abstract class UastTestBase : DriverTest() {

    @Test
    fun `Test RequiresOptIn and OptIn`() {
        // See http://b/248341155 for more details
        val klass = if (isK2) "Class" else "kotlin.reflect.KClass"
        check(
            sourceFiles =
                arrayOf(
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
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package androidx.annotation.experimental {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE, kotlin.annotation.AnnotationTarget.VALUE_PARAMETER, kotlin.annotation.AnnotationTarget.CONSTRUCTOR, kotlin.annotation.AnnotationTarget.FUNCTION, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER, kotlin.annotation.AnnotationTarget.FILE, kotlin.annotation.AnnotationTarget.TYPEALIAS}) public @interface UseExperimental {
                    ctor @KotlinOnly public UseExperimental(kotlin.reflect.KClass<? extends java.lang.annotation.Annotation>... markerClass);
                    method public abstract $klass<? extends java.lang.annotation.Annotation>[] markerClass();
                    property public abstract kotlin.reflect.KClass<? extends java.lang.annotation.Annotation>[] markerClass;
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

    @Test
    fun `renamed via @JvmName`() {
        val api =
            if (isK2) {
                // NB: getInterpolated -> isInterpolated
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class ColorRamp {
                        ctor public ColorRamp(int[] colors, boolean interpolated);
                        method public int[] getColors();
                        method public int[] getOtherColors();
                        method public boolean isInitiallyEnabled();
                        method public boolean isInterpolated();
                        method public void updateOtherColors(int[]);
                        property public int[] colors;
                        property public boolean initiallyEnabled;
                        property public boolean interpolated;
                        property public int[] otherColors;
                      }
                    }
                """
            } else {
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public final class ColorRamp {
                        ctor public ColorRamp(int[] colors, boolean interpolated);
                        method public int[] getColors();
                        method public boolean getInterpolated();
                        method public int[] getOtherColors();
                        method public boolean isInitiallyEnabled();
                        method public void updateOtherColors(int[]);
                        property public int[] colors;
                        property public boolean initiallyEnabled;
                        property public boolean interpolated;
                        property public int[] otherColors;
                      }
                    }
                """
            }
        // Regression test from http://b/257444932: @get:JvmName on constructor property
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class ColorRamp(
                            val colors: IntArray,
                            @get:JvmName("isInterpolated")
                            val interpolated: Boolean,
                        ) {
                            @get:JvmName("isInitiallyEnabled")
                            val initiallyEnabled: Boolean = false

                            @set:JvmName("updateOtherColors")
                            var otherColors: IntArray = arrayOf()
                        }
                    """
                    )
                ),
            format = FileFormat.V4,
            api = api,
        )
    }

    @Test
    fun `Annotation on parameters of data class synthetic copy`() {
        // https://youtrack.jetbrains.com/issue/KT-57003
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class MyAnnotation
                        // No use-site target specified, so the annotation applies to the parameter.
                        data class Foo(@MyAnnotation val p1: Int, val p2: String)
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(@test.pkg.MyAnnotation int p1, String p2);
                    method public int component1();
                    method public String component2();
                    method public test.pkg.Foo copy(optional @test.pkg.MyAnnotation int p1, optional String p2);
                    method public int getP1();
                    method public String getP2();
                    property @test.pkg.MyAnnotation public int p1;
                    property public String p2;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                  }
                }
            """
        )
    }

    @Test
    fun `declarations with value class in its signature`() {
        // https://youtrack.jetbrains.com/issue/KT-57546
        // https://youtrack.jetbrains.com/issue/KT-57577
        // https://youtrack.jetbrains.com/issue/KT-72078
        check(
            sourceFiles =
                arrayOf(
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
                        class User(
                          val p : AnchorType,
                          var q : AnchorType,
                        ) {
                          fun foo() = p
                          fun bar(): () -> AnchorType = { foo() }
                        }

                        class Alignment(val horizontal: Horizontal, val vertical: Vertical) {
                          @kotlin.jvm.JvmInline
                          value class Horizontal private constructor(private val value: Int) {
                            companion object {
                              val Start: Horizontal = Horizontal(0)
                              val CenterHorizontally: Horizontal = Horizontal(1)
                              val End: Horizontal = Horizontal(2)
                            }
                          }

                          @kotlin.jvm.JvmInline
                          value class Vertical private constructor(private val value: Int) {
                            companion object {
                              val Top: Vertical = Vertical(0)
                              val CenterVertically: Vertical = Vertical(1)
                              val Bottom: Vertical = Vertical(2)
                            }
                          }

                          companion object {
                            val TopStart: Alignment = Alignment(Horizontal.Start, Vertical.Top)
                            val Top: Vertical = Vertical.Top
                            val Start: Horizontal = Horizontal.Start
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Alignment {
                    ctor @KotlinOnly public Alignment(test.pkg.Alignment.Horizontal horizontal, test.pkg.Alignment.Vertical vertical);
                    property public test.pkg.Alignment.Horizontal horizontal;
                    property public test.pkg.Alignment.Vertical vertical;
                    field public static final test.pkg.Alignment.Companion Companion;
                  }
                  public static final class Alignment.Companion {
                    method public test.pkg.Alignment getTopStart();
                    property public test.pkg.Alignment.Horizontal Start;
                    property public test.pkg.Alignment.Vertical Top;
                    property public test.pkg.Alignment TopStart;
                  }
                  @kotlin.jvm.JvmInline public static final value class Alignment.Horizontal {
                    field public static final test.pkg.Alignment.Horizontal.Companion Companion;
                  }
                  public static final class Alignment.Horizontal.Companion {
                    property public test.pkg.Alignment.Horizontal CenterHorizontally;
                    property public test.pkg.Alignment.Horizontal End;
                    property public test.pkg.Alignment.Horizontal Start;
                  }
                  @kotlin.jvm.JvmInline public static final value class Alignment.Vertical {
                    field public static final test.pkg.Alignment.Vertical.Companion Companion;
                  }
                  public static final class Alignment.Vertical.Companion {
                    property public test.pkg.Alignment.Vertical Bottom;
                    property public test.pkg.Alignment.Vertical CenterVertically;
                    property public test.pkg.Alignment.Vertical Top;
                  }
                  @kotlin.jvm.JvmInline public final value class AnchorType {
                    field public static final test.pkg.AnchorType.Companion Companion;
                  }
                  public static final class AnchorType.Companion {
                    property public test.pkg.AnchorType Center;
                    property public test.pkg.AnchorType End;
                    property public test.pkg.AnchorType Start;
                  }
                  public final class User {
                    ctor @KotlinOnly public User(test.pkg.AnchorType p, test.pkg.AnchorType q);
                    method public kotlin.jvm.functions.Function0<test.pkg.AnchorType> bar();
                    method public float foo();
                    property public test.pkg.AnchorType p;
                    property public test.pkg.AnchorType q;
                  }
                }
        """
        )
    }

    @FilterByProvider("psi", "k2", action = EXCLUDE)
    @Test
    fun `internal setter with delegation`() {
        // https://youtrack.jetbrains.com/issue/KT-70458
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Test {
                          var prop = "zzz"
                            internal set
                          var lazyProp by lazy { setOf("zzz") }
                            internal set
                        }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Test {
                    ctor public Test();
                    method public java.util.Set<java.lang.String> getLazyProp();
                    method public String getProp();
                    property public java.util.Set<java.lang.String> lazyProp;
                    property public String prop;
                  }
                }
                """
        )
    }

    @Test
    fun `non-last vararg type`() {
        // https://youtrack.jetbrains.com/issue/KT-57547
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        fun foo(vararg vs: String, b: Boolean = true) {
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class TestKt {
                    method public static void foo(String[] vs, optional boolean b);
                  }
                }
            """
        )
    }

    @Test
    fun `implements Comparator`() {
        // https://youtrack.jetbrains.com/issue/KT-57548
        check(
            sourceFiles =
                arrayOf(
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
            api =
                """
                package test.pkg {
                  public final class Foo {
                    ctor public Foo(int x);
                    method public int getX();
                    property public int x;
                  }
                  public final class FooComparator implements java.util.Comparator<test.pkg.Foo> {
                    ctor public FooComparator();
                    method public int compare(test.pkg.Foo firstFoo, test.pkg.Foo secondFoo);
                  }
                }
            """
        )
    }

    @Test
    fun `constant in file-level annotation`() {
        // https://youtrack.jetbrains.com/issue/KT-57550
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        @file:RequiresApi(31)
                        package test.pkg
                        import androidx.annotation.RequiresApi

                        @RequiresApi(31)
                        fun foo(p: Int) {}
                    """
                    ),
                    requiresApiSource,
                ),
            api =
                """
                package test.pkg {
                  @RequiresApi(31) public final class TestKt {
                    method @RequiresApi(31) public static void foo(int p);
                  }
                }
            """
        )
    }

    @Test
    fun `final modifier in enum members`() {
        // https://youtrack.jetbrains.com/issue/KT-57567
        check(
            sourceFiles =
                arrayOf(
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
            api =
                """
                package test.pkg {
                  public enum Event {
                    method public static test.pkg.Event? upTo(test.pkg.State state);
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
                    method public boolean isAtLeast(test.pkg.State state);
                    method public boolean isFinished();
                    property public boolean isFinished;
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

    @Test
    fun `lateinit var as mutable bare field`() {
        // https://youtrack.jetbrains.com/issue/KT-57569
        check(
            sourceFiles =
                arrayOf(
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
            api =
                """
                package test.pkg {
                  public final class Bar {
                    ctor public Bar();
                  }
                  public final class Foo {
                    ctor public Foo();
                    method public java.util.List<test.pkg.Bar> getBars();
                    property public java.util.List<test.pkg.Bar> bars;
                  }
                }
            """
        )
    }

    @Test
    fun `Upper bound wildcards -- enum members`() {
        // https://youtrack.jetbrains.com/issue/KT-57578
        val upperBound = "? extends "
        check(
            sourceFiles =
                arrayOf(
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
            api =
                """
                package test.pkg {
                  public enum PowerCategory {
                    enum_constant public static final test.pkg.PowerCategory CPU;
                    enum_constant public static final test.pkg.PowerCategory MEMORY;
                  }
                  public enum PowerCategoryDisplayLevel {
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
                    ctor public PowerMetric.Type.Energy();
                    ctor public PowerMetric.Type.Energy(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> energyCategories);
                  }
                  public static final class PowerMetric.Type.Power extends test.pkg.PowerMetric.Type {
                    ctor public PowerMetric.Type.Power();
                    ctor public PowerMetric.Type.Power(optional java.util.Map<test.pkg.PowerCategory,${upperBound}test.pkg.PowerCategoryDisplayLevel> powerCategories);
                  }
                }
            """
        )
    }

    @Test
    fun `Upper bound wildcards -- type alias`() {
        // https://youtrack.jetbrains.com/issue/KT-61460
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class PerfettoSdkHandshake(
                          private val targetPackage: String,
                          private val parseJsonMap: (jsonString: String) -> Map<String, String>,
                          private val executeShellCommand: ShellCommandExecutor,
                        )

                        internal typealias ShellCommandExecutor = (command: String) -> String
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PerfettoSdkHandshake {
                    ctor public PerfettoSdkHandshake(String targetPackage, kotlin.jvm.functions.Function1<? super java.lang.String,? extends java.util.Map<java.lang.String,java.lang.String>> parseJsonMap, kotlin.jvm.functions.Function1<? super java.lang.String,java.lang.String> executeShellCommand);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- extension function type`() {
        // https://youtrack.jetbrains.com/issue/KT-61734
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface NavGraphBuilder

                        interface AnimatedContentTransitionScope<S>

                        interface NavBackStackEntry

                        interface EnterTransition

                        fun NavGraphBuilder.compose(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ) = TODO()
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public interface AnimatedContentTransitionScope<S> {
                  }
                  public interface EnterTransition {
                  }
                  public interface NavBackStackEntry {
                  }
                  public interface NavGraphBuilder {
                  }
                  public final class NavGraphBuilderKt {
                    method public static Void compose(test.pkg.NavGraphBuilder, optional kotlin.jvm.functions.Function1<test.pkg.AnimatedContentTransitionScope<test.pkg.NavBackStackEntry>,test.pkg.EnterTransition?>? enterTransition);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- extension function type -- deprecated`() {
        // https://youtrack.jetbrains.com/issue/KT-61734
        val wildcard1 = if (isK2) "" else "? super "
        val wildcard2 = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface NavGraphBuilder

                        interface AnimatedContentTransitionScope<S>

                        interface NavBackStackEntry

                        interface EnterTransition

                        fun NavGraphBuilder.after(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ) = TODO()

                        @Deprecated("no more composable", level = DeprecationLevel.HIDDEN)
                        fun NavGraphBuilder.before(
                          enterTransition: (@JvmSuppressWildcards
                              AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
                        ) = TODO()
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public interface AnimatedContentTransitionScope<S> {
                  }
                  public interface EnterTransition {
                  }
                  public interface NavBackStackEntry {
                  }
                  public interface NavGraphBuilder {
                  }
                  public final class NavGraphBuilderKt {
                    method public static Void after(test.pkg.NavGraphBuilder, optional kotlin.jvm.functions.Function1<test.pkg.AnimatedContentTransitionScope<test.pkg.NavBackStackEntry>,test.pkg.EnterTransition?>? enterTransition);
                    method @Deprecated public static Void before(test.pkg.NavGraphBuilder, optional kotlin.jvm.functions.Function1<${wildcard1}test.pkg.AnimatedContentTransitionScope<test.pkg.NavBackStackEntry>,${wildcard2}test.pkg.EnterTransition?>? enterTransition);
                  }
                }
                """
        )
    }

    @Test
    fun `Upper bound wildcards -- suspend continuation with generic collection`() {
        val wildcard = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class Test {
                          suspend fun foo(): Set<String> {
                            return setOf("blah")
                          }
                        }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class Test {
                    ctor public Test();
                    method public suspend Object? foo(kotlin.coroutines.Continuation<? super java.util.Set<${wildcard}java.lang.String>>);
                  }
                }
                """
        )
    }

    @Test
    fun `boxed type argument as method return type`() {
        // https://youtrack.jetbrains.com/issue/KT-57579
        check(
            sourceFiles =
                arrayOf(
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
            api =
                """
                package test.pkg {
                  public abstract class ActivityResultContract<I, O> {
                    ctor public ActivityResultContract();
                    method public abstract O parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                  public interface Intent {
                  }
                  public final class StartActivityForResult extends test.pkg.ActivityResultContract<test.pkg.Intent,java.lang.Boolean> {
                    ctor public StartActivityForResult();
                    method public Boolean parseResult(int resultCode, test.pkg.Intent? intent);
                  }
                }
            """
        )
    }

    @Test
    fun `setter returns this with type cast`() {
        // https://youtrack.jetbrains.com/issue/KT-61459
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface Alarm {
                          interface Builder<Self : Builder<Self>> {
                            fun build(): Alarm
                          }
                        }

                        abstract class AbstractAlarm<
                          Self : AbstractAlarm<Self, Builder>, Builder : AbstractAlarm.Builder<Builder, Self>>
                        internal constructor(
                          val identifier: String,
                        ) : Alarm {
                          abstract class Builder<Self : Builder<Self, Built>, Built : AbstractAlarm<Built, Self>> : Alarm.Builder<Self> {
                            private var identifier: String = ""

                            fun setIdentifier(text: String): Self {
                              this.identifier = text
                              return this as Self
                            }

                            final override fun build(): Built = TODO()
                          }
                        }
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  public abstract class AbstractAlarm<Self extends test.pkg.AbstractAlarm<Self, Builder>, Builder extends test.pkg.AbstractAlarm.Builder<Builder, Self>> implements test.pkg.Alarm {
                    method public final String getIdentifier();
                    property public final String identifier;
                  }
                  public abstract static class AbstractAlarm.Builder<Self extends test.pkg.AbstractAlarm.Builder<Self, Built>, Built extends test.pkg.AbstractAlarm<Built, Self>> implements test.pkg.Alarm.Builder<Self> {
                    ctor public AbstractAlarm.Builder();
                    method public final Built build();
                    method public final Self setIdentifier(String text);
                  }
                  public interface Alarm {
                  }
                  public static interface Alarm.Builder<Self extends test.pkg.Alarm.Builder<Self>> {
                    method public test.pkg.Alarm build();
                  }
                }
            """
        )
    }

    @Test
    fun `suspend fun in interface`() {
        // https://youtrack.jetbrains.com/issue/KT-61544
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        interface MyInterface

                        interface GattClientScope {
                          suspend fun await(block: () -> Unit)
                          suspend fun readCharacteristic(p: MyInterface): Result<ByteArray>
                          suspend fun writeCharacteristic(p: MyInterface, value: ByteArray): Result<Unit>
                        }
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public interface GattClientScope {
                    method public suspend Object? await(kotlin.jvm.functions.Function0<kotlin.Unit> block, kotlin.coroutines.Continuation<? super kotlin.Unit>);
                    method public suspend Object? readCharacteristic(test.pkg.MyInterface p, kotlin.coroutines.Continuation<? super kotlin.Result<? extends byte[]>>);
                    method public suspend Object? writeCharacteristic(test.pkg.MyInterface p, byte[] value, kotlin.coroutines.Continuation<? super kotlin.Result<? extends kotlin.Unit>>);
                  }
                  public interface MyInterface {
                  }
                }
                """
        )
    }

    @Test
    fun `nullable return type via type alias`() {
        // https://youtrack.jetbrains.com/issue/KT-61460
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        typealias HasAuthenticationResultsDelegate = () -> Boolean

                        class PrepareGetCredentialResponse private constructor(
                          val hasAuthResultsDelegate: HasAuthenticationResultsDelegate?,
                        )
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PrepareGetCredentialResponse {
                    method public kotlin.jvm.functions.Function0<java.lang.Boolean>? getHasAuthResultsDelegate();
                    property public kotlin.jvm.functions.Function0<java.lang.Boolean>? hasAuthResultsDelegate;
                  }
                }
            """
        )
    }

    @Test
    fun `IntDef with constant in companion object`() {
        // https://youtrack.jetbrains.com/issue/KT-61497
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @Retention(AnnotationRetention.SOURCE)
                        @Target(AnnotationTarget.ANNOTATION_CLASS)
                        annotation class MyIntDef(
                          vararg val value: Int = [],
                          val flag: Boolean = false,
                        )

                        class RemoteAuthClient internal constructor(
                          private val packageName: String,
                        ) {
                          companion object {
                            const val NO_ERROR: Int = -1
                            const val ERROR_UNSUPPORTED: Int = 0
                            const val ERROR_PHONE_UNAVAILABLE: Int = 1

                            @MyIntDef(NO_ERROR, ERROR_UNSUPPORTED, ERROR_PHONE_UNAVAILABLE)
                            @Retention(AnnotationRetention.SOURCE)
                            annotation class ErrorCode
                          }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) @kotlin.annotation.Target(allowedTargets=kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS) public @interface MyIntDef {
                    ctor @KotlinOnly public MyIntDef(optional int... value, optional boolean flag);
                    method public abstract boolean flag() default false;
                    method public abstract int[] value();
                    property public abstract boolean flag;
                    property public abstract int[] value;
                  }
                  public final class RemoteAuthClient {
                    field public static final test.pkg.RemoteAuthClient.Companion Companion;
                    field public static final int ERROR_PHONE_UNAVAILABLE = 1; // 0x1
                    field public static final int ERROR_UNSUPPORTED = 0; // 0x0
                    field public static final int NO_ERROR = -1; // 0xffffffff
                  }
                  public static final class RemoteAuthClient.Companion {
                    property public static int ERROR_PHONE_UNAVAILABLE;
                    property public static int ERROR_UNSUPPORTED;
                    property public static int NO_ERROR;
                  }
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) @test.pkg.MyIntDef({test.pkg.RemoteAuthClient.NO_ERROR, test.pkg.RemoteAuthClient.ERROR_UNSUPPORTED, test.pkg.RemoteAuthClient.ERROR_PHONE_UNAVAILABLE}) public static @interface RemoteAuthClient.Companion.ErrorCode {
                  }
                }
                """
        )
    }

    @Test
    fun `APIs before and after @Deprecated(HIDDEN) on properties or accessors`() {
        val api =
            if (isK2) {
                // NB: better tracking non-deprecated accessors (thanks to better use-site handling)
                """
                    package test.pkg {
                      @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER}) public @interface MyAnnotation {
                      }
                      public interface TestInterface {
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter_myAnnoOnSetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty_myAnnoOnSetter();
                        method public int getPOld_deprecatedOnSetter();
                        method @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnBoth();
                        method @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnGetter();
                        method public int getPOld_deprecatedOnSetter_myAnnoOnSetter();
                        method @Deprecated public void setPOld_deprecatedOnGetter(int);
                        method @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnBoth(int);
                        method @Deprecated public void setPOld_deprecatedOnGetter_myAnnoOnGetter(int);
                        method @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnSetter(int);
                        property @Deprecated public abstract int pOld_deprecatedOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnSetter;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnSetter;
                        property public abstract int pOld_deprecatedOnSetter;
                        property @test.pkg.MyAnnotation public abstract int pOld_deprecatedOnSetter_myAnnoOnBoth;
                        property @test.pkg.MyAnnotation public abstract int pOld_deprecatedOnSetter_myAnnoOnGetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnSetter;
                      }
                      public final class Test_accessors {
                        ctor public Test_accessors();
                        method public String? getPNew_accessors();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnProperty();
                        method public String? getPOld_accessors_deprecatedOnSetter();
                        method public void setPNew_accessors(String?);
                        method @Deprecated public void setPOld_accessors_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnSetter(String!);
                        property public String? pNew_accessors;
                        property @Deprecated public String? pOld_accessors_deprecatedOnGetter;
                        property @Deprecated public String? pOld_accessors_deprecatedOnProperty;
                        property public String? pOld_accessors_deprecatedOnSetter;
                      }
                      public final class Test_getter {
                        ctor public Test_getter();
                        method public String? getPNew_getter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnProperty();
                        method public String? getPOld_getter_deprecatedOnSetter();
                        method public void setPNew_getter(String?);
                        method @Deprecated public void setPOld_getter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnSetter(String!);
                        property public String? pNew_getter;
                        property @Deprecated public String? pOld_getter_deprecatedOnGetter;
                        property @Deprecated public String? pOld_getter_deprecatedOnProperty;
                        property public String? pOld_getter_deprecatedOnSetter;
                      }
                      public final class Test_noAccessor {
                        ctor public Test_noAccessor();
                        method public String getPNew_noAccessor();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnProperty();
                        method public String getPOld_noAccessor_deprecatedOnSetter();
                        method public void setPNew_noAccessor(String);
                        method @Deprecated public void setPOld_noAccessor_deprecatedOnGetter(String);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnSetter(String!);
                        property public String pNew_noAccessor;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnGetter;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnProperty;
                        property public String pOld_noAccessor_deprecatedOnSetter;
                      }
                      public final class Test_setter {
                        ctor public Test_setter();
                        method public String? getPNew_setter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnProperty();
                        method public String? getPOld_setter_deprecatedOnSetter();
                        method public void setPNew_setter(String?);
                        method @Deprecated public void setPOld_setter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnSetter(String!);
                        property public String? pNew_setter;
                        property @Deprecated public String? pOld_setter_deprecatedOnGetter;
                        property @Deprecated public String? pOld_setter_deprecatedOnProperty;
                        property public String? pOld_setter_deprecatedOnSetter;
                      }
                    }
                """
            } else {
                """
                    package test.pkg {
                      @kotlin.annotation.Target(allowedTargets={kotlin.annotation.AnnotationTarget.PROPERTY, kotlin.annotation.AnnotationTarget.PROPERTY_GETTER, kotlin.annotation.AnnotationTarget.PROPERTY_SETTER}) public @interface MyAnnotation {
                      }
                      public interface TestInterface {
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnGetter_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnGetter_myAnnoOnSetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnBoth();
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public int getPOld_deprecatedOnProperty_myAnnoOnGetter();
                        method @BytecodeOnly @Deprecated public int getPOld_deprecatedOnProperty_myAnnoOnSetter();
                        method @BytecodeOnly public int getPOld_deprecatedOnSetter();
                        method @BytecodeOnly @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnBoth();
                        method @BytecodeOnly @test.pkg.MyAnnotation public int getPOld_deprecatedOnSetter_myAnnoOnGetter();
                        method @BytecodeOnly public int getPOld_deprecatedOnSetter_myAnnoOnSetter();
                        method @BytecodeOnly public void setPOld_deprecatedOnGetter(int);
                        method @BytecodeOnly @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnBoth(int);
                        method @BytecodeOnly public void setPOld_deprecatedOnGetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @test.pkg.MyAnnotation public void setPOld_deprecatedOnGetter_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnProperty_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnProperty_myAnnoOnSetter(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnBoth(int);
                        method @BytecodeOnly @Deprecated public void setPOld_deprecatedOnSetter_myAnnoOnGetter(int);
                        method @BytecodeOnly @Deprecated @test.pkg.MyAnnotation public void setPOld_deprecatedOnSetter_myAnnoOnSetter(int);
                        property @Deprecated public abstract int pOld_deprecatedOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnGetter_myAnnoOnSetter;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnBoth;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnGetter;
                        property @Deprecated public abstract int pOld_deprecatedOnProperty_myAnnoOnSetter;
                        property public abstract int pOld_deprecatedOnSetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnBoth;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnGetter;
                        property public abstract int pOld_deprecatedOnSetter_myAnnoOnSetter;
                      }
                      public final class Test_accessors {
                        ctor public Test_accessors();
                        method public String? getPNew_accessors();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_accessors_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_accessors_deprecatedOnSetter();
                        method public void setPNew_accessors(String?);
                        method @BytecodeOnly public void setPOld_accessors_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_accessors_deprecatedOnSetter(String!);
                        property public String? pNew_accessors;
                        property @Deprecated public String? pOld_accessors_deprecatedOnGetter;
                        property @Deprecated public String? pOld_accessors_deprecatedOnProperty;
                        property public String? pOld_accessors_deprecatedOnSetter;
                      }
                      public final class Test_getter {
                        ctor public Test_getter();
                        method public String? getPNew_getter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_getter_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_getter_deprecatedOnSetter();
                        method public void setPNew_getter(String?);
                        method @BytecodeOnly public void setPOld_getter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_getter_deprecatedOnSetter(String!);
                        property public String? pNew_getter;
                        property @Deprecated public String? pOld_getter_deprecatedOnGetter;
                        property @Deprecated public String? pOld_getter_deprecatedOnProperty;
                        property public String? pOld_getter_deprecatedOnSetter;
                      }
                      public final class Test_noAccessor {
                        ctor public Test_noAccessor();
                        method public String getPNew_noAccessor();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_noAccessor_deprecatedOnProperty();
                        method @BytecodeOnly public String getPOld_noAccessor_deprecatedOnSetter();
                        method public void setPNew_noAccessor(String);
                        method @BytecodeOnly public void setPOld_noAccessor_deprecatedOnGetter(String);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_noAccessor_deprecatedOnSetter(String!);
                        property public String pNew_noAccessor;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnGetter;
                        property @Deprecated public String pOld_noAccessor_deprecatedOnProperty;
                        property public String pOld_noAccessor_deprecatedOnSetter;
                      }
                      public final class Test_setter {
                        ctor public Test_setter();
                        method public String? getPNew_setter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnGetter();
                        method @BytecodeOnly @Deprecated public String! getPOld_setter_deprecatedOnProperty();
                        method @BytecodeOnly public String? getPOld_setter_deprecatedOnSetter();
                        method public void setPNew_setter(String?);
                        method @BytecodeOnly public void setPOld_setter_deprecatedOnGetter(String?);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnProperty(String!);
                        method @BytecodeOnly @Deprecated public void setPOld_setter_deprecatedOnSetter(String!);
                        property public String? pNew_setter;
                        property @Deprecated public String? pOld_setter_deprecatedOnGetter;
                        property @Deprecated public String? pOld_setter_deprecatedOnProperty;
                        property public String? pOld_setter_deprecatedOnSetter;
                      }
                    }
                """
            }
        // TODO: https://youtrack.jetbrains.com/issue/KTIJ-27244
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class Test_noAccessor {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnProperty: String = "42"

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnGetter: String = "42"

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_noAccessor_deprecatedOnSetter: String = "42"

                            var pNew_noAccessor: String = "42"
                        }

                        class Test_getter {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnProperty: String? = null
                                get() = field ?: "null?"

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnGetter: String? = null
                                get() = field ?: "null?"

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_getter_deprecatedOnSetter: String? = null
                                get() = field ?: "null?"

                            var pNew_getter: String? = null
                                get() = field ?: "null?"
                        }

                        class Test_setter {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnProperty: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnGetter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_setter_deprecatedOnSetter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            var pNew_setter: String? = null
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }
                        }

                        class Test_accessors {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnProperty: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnGetter: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_accessors_deprecatedOnSetter: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }

                            var pNew_accessors: String? = null
                                get() = field ?: "null?"
                                set(value) {
                                    if (field == null) {
                                        field = value
                                    }
                                }
                        }

                        @Target(
                          AnnotationTarget.PROPERTY,
                          AnnotationTarget.PROPERTY_GETTER,
                          AnnotationTarget.PROPERTY_SETTER
                        )
                        annotation class MyAnnotation

                        interface TestInterface {
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty: Int

                            @get:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @Deprecated("no more property", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnProperty_myAnnoOnBoth: Int

                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter: Int

                            @get:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @get:Deprecated("no more getter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnGetter_myAnnoOnBoth: Int

                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter: Int

                            @get:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnGetter: Int

                            @set:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnSetter: Int

                            @get:MyAnnotation
                            @set:MyAnnotation
                            @set:Deprecated("no more setter", level = DeprecationLevel.HIDDEN)
                            var pOld_deprecatedOnSetter_myAnnoOnBoth: Int
                        }
                        """
                    )
                ),
            compiledSourceJar =
                // Compiled from the source above with [generateBase64gzipFromKotlin]
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/5WZBVDU0b7HYelGcukS6RQB6e6lW2ppWEIaJCSWlFZCUqSk" +
                        "BaRLWpCQFnCpBZZmCQlBeHrvu++pb6533n/nzPx35r+fM/89Zz7ne85PWx0N" +
                        "nQwFGxsbBQWFCeXXiwwFHQWkqC/Lo6qpxAeS1VRVUtTT5wUp3QyjoJyARj5q" +
                        "qPPwThCo83COjYzX6/JP31/Z8ORVA3GrgiZ8yt/pItV4PDjVRka4DJFjfEND" +
                        "I/CNtQ0AirY6FnYNKUfNwx8diPxo2v+2e/ofzdvOy5vvMcSBT//HjaWbu6yN" +
                        "jZ2Xl7snr40L2MsrQf+RFo0yULK+ce7ZkZ3ryCixGs5dMzUK2smSSBIVlZhH" +
                        "lMQK7CREE9ZJORiBcwsmjlcDelfS3/DipsszonGPPjMYfF3Es6JAa1xvCfLa" +
                        "HHm4HCR+e3N+Lo1CgeTpDe2NLHxABFkQpw7ir5pRi8QDz3vwY36VkZ5taHWb" +
                        "fTf6Hc3CYy2G/4N6lJPoR/yUoTLYXetk0hjmMUYeMnJvgXgRkd0Zimoig6V3" +
                        "AYp+/j5TX+qq7jXlMlB5bnB9oxsUHlmwJoGp+MrKxYH3eNN9DkS/aDLDe2cC" +
                        "V8RRJRC9gUDBUYKbYDyJWIunVUlMO0FC6PIFQY88Kajo55rrZ1JGr9enQYUK" +
                        "jGKsW7ntLU+dM5g6Fcjq+dLJ4/njaU01YfwNEfAo+0zb2eld0yHviZ43MaJK" +
                        "sW+OU4wIRbgd4G8sliddO7pxVHRNiZB8T2phKarxq2BAvnXOqlP0zaDP1Dta" +
                        "mAD3c90FPULbanM5DvIjzP05SA7u2DslmvRR5eyMAxufkJFd/IjVqj5j34wY" +
                        "mZBHpo8CO7r5PZI4u1yAk5SI6fD3fZJNHrVH4Pk6yAKq35lrrmBFQ/NUeB/v" +
                        "Ep9Naa+eX4+nIvGHFiwzPivcOOHuPcWFTniixQxd7rpqAhxbEmXEtfULJv5e" +
                        "8po08MAlUWkmIKprTPMYx0lGTWVnXlVO94ts1reR9/OqOlk2fDaIrg+pnK6U" +
                        "0yqyWc86pW85QCnea3tFxAbW5RIUBmvldZKVvi0fx+dq0EjoX2WjwJ0n0T5w" +
                        "HV+d5Jur5DG2vEnVZQWUSFoNoNmbW+kYGjRZ4ghsSlgllTdjxo00pOEFt2gR" +
                        "0kX1GXwVwHdleJk4scJ68pl4ICS3ppHC5TTNoLh15AXBc3faB2ct3zXZ4q/8" +
                        "Tcg7OisWo8SJwiNQ59xoSo6QDzMYVh4YWw+K1XFdJ+ggMksR7GqUJMV5S4rO" +
                        "li0ZkQ++fbR1S3wyFAxwCpJMh5DudtxR12Y4sa9xalQGDlPRa9yi2ollXNA5" +
                        "1Yw+cV2CXGlJlotMUziyEDN5CDDrvkrtkyri16j1MVX9Zgt8z/jKHkdaQRwW" +
                        "IdT05U0S6DbqtCYDWsfgMk97InJVHWuduRUUR77DSiny/GpdS0NjPPnzFETD" +
                        "8/00Ag645ye0VnuVY+6v8CFm8GX4rU2pBaL0G893L/SnGkLPAXqZm5ndDnft" +
                        "068V3C6Gor6zN13i6EnjvElh7REPsu+VShMPTnekdeiOdCgrWoDJSVW2tiXs" +
                        "pA8zXSlOAltANlKVtVc3bdgI+LaBFMVC3AHRM00YT1ZMHizt883bLX1EbBoy" +
                        "Ya7z1Gya/dtD1QtrohSSvAykHY4lOCXKmHMVwn0YhtP+fpj+GBABqADUydSI" +
                        "GBMDSfzvIWUYWfi5XuF440mQsGPj48VjP9kW4NO+ZvjpGST1d0xadBSUIZy/" +
                        "eYb6/3jGwc7b2+5fjjFY1KQxIjN3CGJwuS9shS1EiENIyWaLD1p8p40NlakS" +
                        "DV2LKVlDJThLEbcaN/bCXOg8mr5R7nqVYmqwIx/pHzudFNeKsOZttHEZQtR1" +
                        "nIlB9oMJr8+P8p+iSvRT9L4djawSToLsPDKlnW529eICDLyidSrNMp2Lz2r1" +
                        "8u/j8kVHjULWuW2d4cpb+brxJRL3CIbH8KPvM/h0J3P4sJhZZ76eOdDs2FEm" +
                        "LG93OoqzP9lJrIW8BLnNdyXnzLdMzrTYLU4mULNqCbuPavht9qfICGLiROAz" +
                        "wYssLBOYfXozseKtYk5jQ04TRnzp7zcNEpuUnF4alD9biB7PsQeWVM23OmkP" +
                        "RakNlpjPEfjASdTSWQh4oYbwojl+NpqSfrbsDEE5n5DhXXy/Bb3Y2vVrBfTV" +
                        "wzgaGr4mqoNz7XcbUcDUqvvOIYxXZXGBVB5fztWuQi8C9av787leGoIk184K" +
                        "irejtGvIZWAlY47RPp3Tj5wvhe3sLOfjS10am+o827wmcEWpGy+1sKp08YVq" +
                        "fD1GAve0sNkXQssMRSy8HlU4NyolFg04hS5Re0Vl3g8No9O7doDNkzsamFtd" +
                        "PA4R5la3SeXmQDjUkflQqGZhDY6NUxQDHhGZX9Onk22DDHHisQaNmS5cTmD9" +
                        "Napx9zZO6o+shIqgDOiFd1jVkjOJ5ltxUsw/oMiWpPk/2wi4gPW7Ezpmymxb" +
                        "CRWooINaP8mGb6a9wDKP5xy206XxWogk4tAZHGP20iiBIdwzIWm45rZTTzxd" +
                        "7n8wMsoBr43GgncJLIQ0fQqmMIzbbjjD+UY4zW+6U92Q+88HmJqWxDL6Arbs" +
                        "FSsOndIP9r5bvXaBpMHnTKeWl49azlv5EQvjsBnuqu6ySBErz3mJWs4gvRov" +
                        "bu3BiOlqxtFTkcgB8tI042L3zBGpIhPPPPQq9wH71r5NcWvBUSa/8xzfci9l" +
                        "R8KNW6XTjuy6zQhUjF2hAUkU6xe4rJGocSpux2KjY+5H1/naIRehyPPtU4Z3" +
                        "RCuRg0ec4auQIPm5izX6fIdEY+XkIxMjx/ScnOvCrldmTqkwx6wX/hJURBsn" +
                        "ohU6c5WDwF6gIoDXBrAU3eHHVpNHw0vVkOQ4gHEPSTVobqs6LElCucUvOhPd" +
                        "PaFF5SLtOilNwLHeSpPvLJLHBC+UTOgq8oFeumqCWyH5m9/eCkz0DzRwKbAO" +
                        "P4+RKd1q8Z2A332ydubWkIGYMAig9/MpOFtlXlzE8KBszbBw9fPIMLO94uuA" +
                        "gvyuZxsMCrcAd7gPWLT7EIoSPGt4QarqQQZ994M6iO4hCdLPBLgZ8bjxoBib" +
                        "sH6bsGYZWUaWLWn8A5x1dBKADQANwAIoBNwHTAEub1F/uoOcuLbuFA0Fxfn/" +
                        "6Q6vX91h+DOfkEnVWyxTLFYmyLcIda8a0rBVyme9wPRkKihIp5URlUvzl3km" +
                        "6UlZ41eVW5vdfvTiJukmXMnZoIyIfFi9+MeXhHIH3znUWUqZY7PErpPcQ/H7" +
                        "EdfjT5/eYKBY9nIBTDSp5hZGo84oKAfjPTVz2YkBO2VLMw/0mqgXloY4qo7Y" +
                        "odgKwgQfV8axoceRvehQpteh+DoAwdsJK/R7XBHYItjcQuzLy/d3Q9abNRcJ" +
                        "q7ZzZs8rgwfd9KK7lB9eym28dksTj5p5v252tTCi8dQCkJocF4EON86DFV1X" +
                        "BUmrrybLNFdk06nJtIXFKsLqXKq9ehvfMpCmlYyCeicbpeUntN4pUk3RfHhj" +
                        "6lttJy40d8PN7DLJ+wBHA0svpZa5Uy78w/nG/v6hL84XugLHQYI7Kl+dha7f" +
                        "54L3TwVJHi/uLtKtM0w00d9a9/tSL3BssCW6GWu0TaO4NtlkfYtVFVFSr7Io" +
                        "rdzheoR8wZNoaEgg5tAR+umt+lOxA4yBBco2dDKJM3L0OhR+mHiCyItdn9zd" +
                        "LRVnH/mL5BjgmjUKoCj2yC/qc9pjg6cyK8sn9ZXMINW4orx8Dl0Nbl3RJK3c" +
                        "/CpV1mPB6+2MmCI1rIAHEixaKantFHrySzv9uwxSwSqr5xwb1y5KiYrTW+20" +
                        "z8qOIsgNcMLn/MKRApR5Mv6qoLNuFsBNfajDnbwUGw6DniIPEdX+ovasA9J3" +
                        "Xg1yAxhabzzAvgkxFLZfMtznHzVQW3gtmrh6Aob70Rj5Az/VNk+8tquA8KPV" +
                        "DM5uiLB01e1eCA1wRSn6iVeR7rSTG6m0e7SVZEe32sR0MGgYtlhAOicWb0IW" +
                        "EFK079uBlW014k+eraFahnEYtMGntyC9RUiUw4j4aA54/K3m4yBmQmphJKls" +
                        "vZ9Hel9E99eZzz2hxgV8JSu0vn7ZzVN9BD0P24+x+qRaObSC+RmvqRJxiFA8" +
                        "NDAeENgAUwBDXWHfIIwBBdLzXuFkTh8uyLBE1UXZMPqXiR9x3BCWf7oTjjhQ" +
                        "IeF1wO3DsAxn52ZxWNrIOxUMdqvNegx7YrCWrMjK1KRG0i59PJHbHtX5Xgtz" +
                        "tSnZPrWbkl8RTG2vvcSdjv31YQ6eGDehF7DoZrJUabklbAwRhPCSz11WRlQY" +
                        "VwhNXlJUF/ZJbwmNEEmUFb9KikYLMmsV+Jxsp0+3o9exXmFBXf7tTnlI/Mju" +
                        "XL7RWwdL81Nu6U9KAo7wIUcaFW93q5NnnQlr2jTZjnAOQemugBNW/+3E5dZO" +
                        "amBH0mvDJEZ9zCr8YsHOkwdmPb2CN7xPvNGh4NLOh+HcaJHdYhHYETh+OlbJ" +
                        "DslWj5Nko7pdMevxPfFTNUIHgUVUNwuhAZgfb9F+yiNKB4/46w95OP1VHnT/" +
                        "Rx7g/97eeP3LH//KHiKNVNniTjTYQlb/jB5Fqoxma7OocSyTL9S2rBha0OBU" +
                        "kDGZRZG8hdstSrIyg2IpfXa+lG9s32PLA3m/uKBhBpxJLQbWdMyJIfCvT5Yt" +
                        "n6L5xxhEMSmrKEMO5hqM1veQzgYqTvOCmAOvDo7kq6m+VEJ22wJf7/QRW8dI" +
                        "zYJgAT3EWWPuxcSyQrH81vhgRKC5YPyLmvc5Ds2PKMcGWnXEiQ6mFaYoDA5i" +
                        "FlpbwI9AYHFpgVMtxEcEjCuxKtunPN/RVy8HwgdiStAA4D6hYt4MA0sksATG" +
                        "FuQD5RlAHG05nlatHXohz8RoPeEy/CM+yn6p2SUbj5WbNCZratrdH0a5Oas7" +
                        "lPVJzDKiWLBpz7wboE7nGchy8FLA92DHN/qWrivH3WZ5R+ZU0cnJKSABfPyt" +
                        "wDASjp3N5LVQQo8+UDOxxKcG/kJ5SejTYtE7xfvc7mM1JLBnvqHFTW0/nGSB" +
                        "2pV0+UHfdcJ9o1ZgLUm86EaSaDA8Js2QyZb4Q2tvZSzD2C57WQBHck/88iZq" +
                        "8cez071tpQ4ywhqPaFLlYg/ga8jQF+We4NCwfb1rZZg5+VDFqOB1oOd0vn5U" +
                        "WtHrPFrWtOZd2w9nhPm0oxyKjcCAK4i+TtpoODUVAVG9tT9vdVTQ4cRwfZr7" +
                        "0k1OQbzG8bqjEG1Sea34qr8ZSPtJHU9ww4OOQXUtnSc6ID+l237QYempmWie" +
                        "Djnnztc4/RrlRvmJnQ36h3wdWqBmKCKTS34sMCVzVu7VSkRwNFNAiDTQzZU+" +
                        "M3GJmPVEO0OMLivymRHn9ZfyidFtnt0SY3HHPdyrtMC3X0QiDTYGKJp8/OjQ" +
                        "p8ijjc4tj+3f0uO8bRJj1Go8g60Iu2OLsWVMppLvvq+OI4cRZxbb3CkhIHhe" +
                        "nEEimd+cnVB6GksyTzTEeQBivvV0KZd3KeFJv0tzZDjuWNxSGuNShGRbG7QY" +
                        "kkvG557kSz4mTX6l+8bkVFvhKI5yqHW5GLdLPviEcGKu5Yv7O8LVlqQjA/zv" +
                        "lMh0vpXiIIl82gJCK34Z2N2KZFDbm0Nivi9XaDtHVC7Y3Btg+5W8K4M+ZPin" +
                        "ztO3WN+HURa/RkmgXvPi4E/dZapRp74hEj+LrzkiCfZ8ISXHX0jShMx7f3UR" +
                        "OtAG4pFzLWJrZezwJd+edbORFbEVmNeJj5WJj+Xq/8wo0FnCmwbpN74nPRAO" +
                        "T+6HJV69PAxf2CWc5qV9kFolIw9diFlHY77kim1nYl4m2fQTOXSuiD8fs2tf" +
                        "Tnk5i1Gfu/vyjm8tnMIJ9f6+mn1O5j6JfdUVj+7bNcku5JtZDjH1OQ83mgIS" +
                        "Kc5VMA4DnNTzyAknCTkvnNmHyFxW6lLQXatV0Lf+4GV/90C96G4cSQQJnKSJ" +
                        "9XuBvKm18WtlaxlrtcmnouzXRD81UyEzMvNzf4OF+zfN0PyqGVCArJubuzfY" +
                        "28nd7Z+SidSV1SKVBQYvz07qkuPGvGMMVxGtU2kOCG/euVflmj4DPZUpWVSt" +
                        "925VF5tydu84ypfIHGCRHr5IUFvsvWfRGXzSeb4UdGl/+DT4FnUFPxoKYmnw" +
                        "cA80b5KS0kqdxiYtVUVVYF+OSXmcptLqVhjMwqInk/hWxKFf45DX9AS6b8r3" +
                        "gFOTgrUA6F0coG58R63Y0g7BV4ATTbUTsDJi5IKZUEIJNYLOYPl/78aT5zO2" +
                        "5b0VuGoYUUVzsrW+w7apLEfAurolPWlfvuY4EiWHvNTbmpPscWu0E96T9V3W" +
                        "qxt/uXphWOdYdhDJQHOIDSGieaPOItaD7Sb2XSfRMtbFpBEDfUfglqexlFQp" +
                        "VwS6463L/ZC8dP2FX5z0Yc6wt+dOzqd+b2uMUmiV6SbpCQFtM73noZjqeKGK" +
                        "cy5hYmCUdqtGdIRKZxL5YQk1fgBW8CXrrMejoYBQqTU8ZIx8+t7jeKKbaSP/" +
                        "0WVhvws+udrInD5BtUaJxg2cPMEu5to4wVlul7C7s7VhDh9snhNI5V19PtaR" +
                        "TWNqDN5bwagQ8j2qw1C20bK5Ffk50gP4otMFqCgog4C/jTTtnwuKqtuPLGoP" +
                        "trH751CnZcB0Fw2JB29TE1zjUcHYwBOCgc/E/AWqz4WdADg4OrisEeIa9s1W" +
                        "ZFcrYp8F4eQaXoP1p6+DIzX5xPAzgrOkZEsr0ABPP8iNhCoh9txvA8ecfQxv" +
                        "EF1dN5hIoDQHsBHdpOU5XPfAoXMCpD6dFB+OLa/zKlhgLN6sn1+9BTZuqdCN" +
                        "y/LKqv1uZVJb2hpYJ2dasfutgsfctXFNbkzrVwNZxuczVMxa7JkT9Ve46e1F" +
                        "hBWWIhr1jRA3nGdGxVa2V/ZV4pO2VIYt9lIOdDWOrmI3rzfpFjIa5R2Cnbqq" +
                        "Tbr4d9Uv40Ct22I01F+kqk3S6l4utgt46vYYyNq+gIZL1QubV830e28vFFQw" +
                        "jykodjtWsJR6LQrXBm51dLZcpnPd3W4c4an5bkG1eHzQuqxZSQS7lvSm8+2O" +
                        "PjAYoAx680SChu0V5IwWPhrBVOOKOEwZ+/GI+bi3r5X51MvzXE0jz/ccw3Rg" +
                        "KsVewWbgzI5JqBZ3f6f3IiW7dmLAzVydpOlG2xqL4uX5mgS9Aamz/gzvWOF1" +
                        "qXPKG2qTOFpW3rG5aQM7vrIyVZ8HyMrT7ePzzo/LcrW2/CTTrfYRKckUS+pO" +
                        "sEYfniLF6jdflXg5W+g3U00rQcVGpldFal3RcfdaUDftG5rflc5N5fPevt0/" +
                        "8ucXfEMg8FVGt1eDx5ll31nEW26yMFVTbbQWUq7jZaZLmpr1IksPlLV9b9RJ" +
                        "dVzliaHz2INJUJuqqrPFgdh+7S7GkN3djK8rFKxNdwul9oA+Jrpcpp9DDiC4" +
                        "JIsH5vFc0Xh260VT2+Qh3QYt1zvF4fpYJcXCU37gxOPcVyeYCY1hTeaC8BkN" +
                        "3QEQ1t15n3PYa19HNq2gprJN/pdtaKJU/k+SlkIOGS8DMYba0J34cJXrO8jM" +
                        "ksrOzPxEAw/Rc997HSjmLJApDzj1T1awkePCc8ON4fHhzQkMWQ0JcmHiIVWd" +
                        "ni4cMUsheI9T9nOzDFs4zz7q35jdoUmIjdku4dhx58RMWQELWYpXUfa1eSpC" +
                        "L/blPkm5pADXkjChF3bfrIgCyJSKwNOjwl+doQyXy/cKJyen+vqzhJ7Mbx7M" +
                        "zOnUdBUmza6ROnxEGe/DiQCS+IeuMkr7nTVtK+BJjrDMehwBO85Uvk0yPL9y" +
                        "tZhvPjQmrVqSEBk6bxzW/zhGs6a1zDmiMr55Ud8pgaiGF5lupc8I+4+09ald" +
                        "nSihLErQ2HXxNLYjuA+RxpG8HTfrlxjVzSOoALE6MQof7lWmZWuTQVxPLG3s" +
                        "Cn/vUFpwApJgtbbdGUoMhhIzSTAFqV/roZNCS6Ho4BfI+ItsfBvoIfYyaR5p" +
                        "Kza4B6+HCColJ73KukpekP1J3pjNhdgaatED6bkLjkNG8WupkArj3Iea9Dzq" +
                        "oWX6Kv1TKurZy9p4P5aPRwR/kwrHv5XKXQU7e7CPi7eq62OX/06suSkg9ykZ" +
                        "4qjlhqksKNkkGe5qIbQx2rObMUz0MS4BAxe57kQ5JeHEbB6myK75x7bYrfG1" +
                        "4TCG90ly8b1Gzzj1+1MdHCSu4pdfXp59/2iEovWW9+3q0GUzMGkt2LfoSJ++" +
                        "DCp85V10b+Fj+yfecGJHuoqyuAbBQv/H/hqxScS4LgkfDgm9xTTK2Dl08ibD" +
                        "5nlcZYul5/UHjGuC7BNIsZvoabEr9+Kr5l7GXj0NRQB0NMfK7hy5x+9vNha5" +
                        "HVXEvA8v4XAKG+tin4OV6ZpZHSXsnVt2kvqfScv2XhcNpmJuPghwO63sfJYS" +
                        "iEjW4lPofHsDN3G79JN3H6rQD+hPQL8UWofFDE9zoaEKVNPm+SI5KtqVARWI" +
                        "Gh455VcBT/wA+zsDnz5bpCKTXS8f72E4LwpvOIncuIUhrkKsg9KnZDfr5EEd" +
                        "U7rZk0DOpgbPRFGXvXnVl58qNEjpWWSqSOJMKxIxuJn7zBPTkSd38h0GF0aW" +
                        "4E2jPIr45p5vCmWb4+9FqJ+EG21i1IxhCE5uaETcR3VbFRZrHvcIFb9vndDV" +
                        "dDF2jAUlcY0lj4U089bG0ZCIPP1HLYZlm/919Y+VJRDtP2WI/ykFuYKd3Hgh" +
                        "7t4uTm6Wru62Pi52NlZWVvY/Grq1Jia7tvUna5R/sL8yt3eQ/vgl8B91HlQA" +
                        "Gcr/0n+tAf0sNP1+/buy05+UX0946X8jPP2P1aM/Wb+e+FD/xkKg/+2E+E/O" +
                        "r5u/3zksOH87LfqT82u6o/uNY0XwHzaOf6J+jQ80v6EG7/w1HP4J+lUZtL+B" +
                        "akn/nj3+JP067zh+I2EC/x/C+RP765T7/U3xqP86hbXVMTB/Pobz4/Pqx9/k" +
                        "8I+h+y/TO8ltHR0AAA=="
                ),
            api = api,
        )
    }

    @Test
    fun `actual typealias -- without value class`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/PointerEvent.kt",
                """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/PointerEvent.android.kt",
                """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    method public test.pkg.PointerKeyboardModifiers getKeyboardModifiers();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  public final class PointerKeyboardModifiers {
                    ctor public PointerKeyboardModifiers(int packedValue);
                  }
                }
                """
        )
    }

    @Test
    fun `actual typealias -- without common split`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        "androidMain/src/test/pkg/PointerEvent.android.kt",
                        """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
                    ),
                    kotlin(
                        "commonMain/src/test/pkg/PointerEvent.kt",
                        """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        @kotlin.jvm.JvmInline
                        value class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  @kotlin.jvm.JvmInline public final value class PointerKeyboardModifiers {
                    ctor @KotlinOnly public PointerKeyboardModifiers(int packedValue);
                  }
                }
                """
        )
    }

    // b/324521456: need to set kotlin-stdlib-common for common module
    @FilterByProvider("psi", "k2", action = EXCLUDE)
    @Test
    fun `actual typealias`() {
        // https://youtrack.jetbrains.com/issue/KT-55085
        // TODO: https://youtrack.jetbrains.com/issue/KTIJ-26853
        val typeAliasExpanded = if (isK2) "test.pkg.NativePointerKeyboardModifiers" else "int"
        val targetLanguages = if (isK2) "" else "@KotlinOnly "
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/PointerEvent.kt",
                """
                        package test.pkg

                        expect class PointerEvent {
                            val keyboardModifiers: PointerKeyboardModifiers
                        }

                        expect class NativePointerKeyboardModifiers

                        @kotlin.jvm.JvmInline
                        value class PointerKeyboardModifiers(internal val packedValue: NativePointerKeyboardModifiers)
                        """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/PointerEvent.android.kt",
                """
                        package test.pkg

                        actual class PointerEvent {
                            actual val keyboardModifiers = PointerKeyboardModifiers(42)
                        }

                        internal actual typealias NativePointerKeyboardModifiers = Int
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package test.pkg {
                  public final class PointerEvent {
                    ctor public PointerEvent();
                    property public test.pkg.PointerKeyboardModifiers keyboardModifiers;
                  }
                  @kotlin.jvm.JvmInline public final value class PointerKeyboardModifiers {
                    ctor ${targetLanguages}public PointerKeyboardModifiers($typeAliasExpanded packedValue);
                  }
                }
                """
        )
    }

    @Test
    fun `actual inline`() {
        // b/336816056
        val commonSource =
            kotlin(
                "commonMain/src/pkg/TestClass.kt",
                """
                    package pkg
                    public expect class TestClass {
                      public fun test1(a: Int = 0)
                    }
                    public expect inline fun TestClass.test2(a: Int = 0)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/TestClass.kt",
                """
                            package pkg
                            public actual class TestClass {
                              public actual fun test1(a: Int) {}
                            }
                            public actual inline fun TestClass.test2(a: Int) {
                            }
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                package pkg {
                  public final class TestClass {
                    ctor public TestClass();
                    method public void test1(optional int a);
                  }
                  public final class TestClassKt {
                    method public static inline void test2(pkg.TestClass, optional int a);
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility()
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            internal actual typealias JvmDefaultWithCompatibility = kotlin.jvm.JvmDefaultWithCompatibility
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual using renamed import`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            import kotlin.jvm.JvmDefaultWithCompatibility as Compat
                            internal actual typealias JvmDefaultWithCompatibility = Compat
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `JvmDefaultWithCompatibility as typealias actual using chained typealiases`() {
        val commonSources =
            arrayOf(
                kotlin(
                    "commonMain/src/pkg/JvmDefaultWithCompatibility.kt",
                    """
                    package pkg
                    internal expect annotation class JvmDefaultWithCompatibility()
                """
                ),
                kotlin(
                    "commonMain/src/pkg2/TestInterface.kt",
                    """
                    package pkg2

                    import pkg.JvmDefaultWithCompatibility

                    @JvmDefaultWithCompatibility
                    interface TestInterface {
                      fun foo()
                    }
                """
                ),
            )
        val androidSource =
            kotlin(
                "androidMain/src/pkg/JvmDefaultWithCompatibility.kt",
                """
                            package pkg
                            private typealias Compat = kotlin.jvm.JvmDefaultWithCompatibility
                            internal actual typealias JvmDefaultWithCompatibility = Compat
                        """
            )
        check(
            sourceFiles = arrayOf(androidSource, *commonSources),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(commonSources),
                ),
            api =
                """
                package pkg2 {
                  @kotlin.jvm.JvmDefaultWithCompatibility public interface TestInterface {
                    method public void foo();
                  }
                }
                """
        )
    }

    @Test
    fun `internal value class extension property`() {
        // b/385148821
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg
                            @JvmInline
                            value class IntValue(val value: Int)
                            internal var IntValue.isValid
                                get() = this.value != 0
                                set(newValue) = Unit
                        """
                    )
                ),
            api =
                """
            package test.pkg {
              @kotlin.jvm.JvmInline public final value class IntValue {
                ctor @KotlinOnly public IntValue(int value);
                method public int getValue();
                property public int value;
              }
            }
            """
        )
    }

    @Test
    fun `default parameter value from common, without jvm platform set for common`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class Foo {
                        expect fun foo(i: Int = 0): Int
                    }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/JvmDefaultWithCompatibility.kt",
                """
                    package test.pkg
                    actual class Foo {
                        actual fun foo(i: Int) = i
                    }
                """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
            api =
                """
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public int foo(optional int i);
                      }
                    }
                """
        )
    }

    @Test
    fun `default parameter value from common, with jvm platform set for common`() {
        // Verifies that expect/actual linking works when only the JVM platform is used.
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class Foo {
                        expect fun foo(i: Int = 0): Int
                    }
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/JvmDefaultWithCompatibility.kt",
                """
                    package test.pkg
                    actual class Foo {
                        actual fun foo(i: Int) = i
                    }
                """
            )
        check(
            sourceFiles = arrayOf(androidSource, commonSource),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createModuleDescription(
                        moduleName = "commonMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(commonSource),
                        dependsOn = emptyList(),
                    ),
                ),
            api =
                """
                    package test.pkg {
                      public final class Foo {
                        ctor public Foo();
                        method public int foo(optional int i);
                      }
                    }
                """
        )
    }

    @Test
    fun `Vararg parameter followed by value class type parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        fun foo(vararg varargParam: String, valueParam: IntValue) = Unit
                        @JvmInline
                        value class IntValue(val value: Int)
                    """
                    )
                ),
            api =
                """
                package test.pkg {
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method public int getValue();
                    property public int value;
                  }
                  public final class IntValueKt {
                    method public static void foo(String[] varargParam, int valueParam);
                  }
                }
            """
        )
    }

    @Test
    fun `Data class with value class type`() {
        // For K2, no UElement created for a method using a value class type (b/388244267).
        // This will be resolved through b/406833486.
        val copyEntry =
            if (isK2) {
                ""
            } else {
                "method public test.pkg.IntValueData copy-Vxmw0xk(int intValue);"
            }
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        @JvmInline
                        value class IntValue(val value: Int)
                        data class IntValueData(private val intValue: IntValue)
                        """
                    )
                ),
            api =
                """
                package test.pkg {
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method public int getValue();
                    property public int value;
                  }
                  public final class IntValueData {
                    ctor @KotlinOnly public IntValueData(test.pkg.IntValue intValue);
                    $copyEntry
                  }
                }
                """
                    // The copyEntry might be blank, remove it if so.
                    .stripBlankLines()
        )
    }

    @Test
    fun `Private property with defined getter of value class type`() {
        // b/388494377
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg.main
                        class Foo {
                            private var privateVar: IntValue
                                get() = IntValue(0)
                                set(newValue) = Unit
                        }
                        @JvmInline
                        internal value class IntValue(val value: Int)
                    """
                    ),
                ),
            api =
                """
                package test.pkg.main {
                  public final class Foo {
                    ctor public Foo();
                  }
                }
                """
        )
    }

    @Test
    fun `Repeatable annotation with expect actual`() {
        // b/399105459
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/AnnotationCanRepeat.kt",
                """
                    package test.pkg
                    @Repeatable
                    expect annotation class AnnotationCanRepeat(val value: Int)
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/AnnotationCanRepeat.android.kt",
                """
                    package test.pkg
                    @JvmRepeatable(AnnotationCanRepeat.Entries::class)
                    actual annotation class AnnotationCanRepeat
                    actual constructor(actual val value: Int) {
                        annotation class Entries(vararg val value: AnnotationCanRepeat)
                    }
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    createCommonModuleDescription(arrayOf(commonSource)),
                    createAndroidModuleDescription(arrayOf(androidSource))
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Repeatable(AnnotationCanRepeat.Entries::class) public @interface AnnotationCanRepeat {
                    ctor @KotlinOnly public AnnotationCanRepeat(int value);
                    method public abstract int value();
                    property public abstract int value;
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public static @interface AnnotationCanRepeat.Entries {
                    ctor @KotlinOnly public AnnotationCanRepeat.Entries(test.pkg.AnnotationCanRepeat... value);
                    method public abstract test.pkg.AnnotationCanRepeat[] value();
                    property public abstract test.pkg.AnnotationCanRepeat[] value;
                  }
                }
                """
        )
    }

    @Test
    fun `Data class value with type argument`() {
        // Added to test that the nullability of the type argument in the copy method is correct.
        // A K2 update to the source psi for the copy method changed how the kotlin context for the
        // method parameters is computed.
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        data class Foo<T: Any>(val items: List<T>)
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo<T> {
                    ctor public Foo(java.util.List<? extends T> items);
                    method public java.util.List<T> component1();
                    method public test.pkg.Foo<T> copy(optional java.util.List<? extends T> items);
                    method public java.util.List<T> getItems();
                    property public java.util.List<T> items;
                  }
                }
                """
        )
    }

    @Test
    fun `Annotations on property of value class type`() {
        // b/417181888 -- the accessor representation depends on if the type is specified in source
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class Anno
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg
                        @JvmInline value class IntValue(val value: Int) {
                            companion object {
                                @Anno val withValueClassTypeSpecified: IntValue = IntValue(0)
                                @Anno val withValueClassTypeUnspecified = IntValue(0)
                                @Anno val withNonValueClassTypeSpecified: Int = 0
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Anno {
                  }
                  @kotlin.jvm.JvmInline public final value class IntValue {
                    ctor @KotlinOnly public IntValue(int value);
                    method public int getValue();
                    property public int value;
                    field public static final test.pkg.IntValue.Companion Companion;
                  }
                  public static final class IntValue.Companion {
                    method public int getWithNonValueClassTypeSpecified();
                    property @test.pkg.Anno public int withNonValueClassTypeSpecified;
                    property @test.pkg.Anno public test.pkg.IntValue withValueClassTypeSpecified;
                    property @test.pkg.Anno public test.pkg.IntValue withValueClassTypeUnspecified;
                  }
                }
                """
        )
    }

    @Test
    fun `Inherited internal constructor property-parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        open class ParentClass(internal open val internalCtorVal: Int) {
                            internal open fun internalFun() = Unit
                            internal open val internalVal: Int = 0
                        }
                        class ChildClass(override val internalCtorVal: Int): ParentClass(internalCtorVal) {
                            override fun internalFun() = Unit
                            override val internalVal: Int = 0
                        }
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class ChildClass extends test.pkg.ParentClass {
                    ctor public ChildClass(int internalCtorVal);
                  }
                  public class ParentClass {
                    ctor public ParentClass(int internalCtorVal);
                  }
                }
                """
        )
    }

    @Test
    fun `Annotation on generated no-args constructor`() {
        // b/417687416 the annotation is dropped from the no-args constructor with K2
        val noArgsAnnotation = if (isK2) "" else "@test.pkg.Anno "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        annotation class Anno
                        class Foo @Anno constructor(i: Int = 0, s: String = "")
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Anno {
                  }
                  public final class Foo {
                    ctor ${noArgsAnnotation}public Foo();
                    ctor @test.pkg.Anno public Foo(optional int i, optional String s);
                  }
                }
                """
        )
    }

    @Test
    fun `JvmMultifileClass with files in common and android`() {
        // b/417699607 the android method is dropped from K2 tracking
        val androidMethod = if (isK2) "" else "method public static void fooAndroid();"
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Foo")
                package test.pkg
                fun fooCommon() = Unit
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.android.kt",
                """
                @file:JvmMultifileClass
                @file:JvmName("Foo")
                package test.pkg
                fun fooAndroid() = Unit
                """
            )
        check(
            sourceFiles = arrayOf(commonSource, androidSource),
            projectDescription =
                createProjectDescription(
                    // Set the common module to only list the jvm platform, since it requires jvm
                    // annotations.
                    createModuleDescription(
                        moduleName = "commonMain",
                        android = false,
                        kotlinPlatforms = defaultJvmPlatforms,
                        sourceFiles = arrayOf(commonSource),
                        dependsOn = emptyList(),
                    ),
                    createAndroidModuleDescription(arrayOf(androidSource)),
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    $androidMethod
                    method public static void fooCommon();
                  }
                }
                """
                    .stripBlankLines()
        )
    }

    @Test
    fun `Test mapped collections methods`() {
        val maybeExtends = if (isK2) "" else "? extends "
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class EmptyStringList: List<String> {
                            override val size = 0
                            override fun get(index: Int) = error("empty")
                            override fun indexOf(element: String) = -1
                            override fun contains(element: String) = false
                            override fun containsAll(elements: Collection<String>) = false
                            override fun isEmpty() = true
                            override fun iterator() = error("empty")
                            override fun listIterator() = error("empty")
                            override fun listIterator(index: Int) = error("empty")
                            override fun subList(fromIndex: Int, toIndex: Int) = this
                            override fun lastIndexOf(element: String): Int = -1
                        }
                        """,
                    )
                ),
            // Compiled from the source above with [generateBase64gzipFromKotlin]
            compiledSourceJar =
                base64gzip(
                    "test.jar",
                    // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 17.0.6+10-b802.1)
                    "" +
                        "H4sIAAAAAAAA/4WWezgT+h/Hl8I4UrkU6cJGmsx9lZAc1xGWudTMnbmNjW1I" +
                        "Uwfhl3KPcq3VInfFkvtxXdrILXJJLsWWmDCHuXR0/vj96jzPOb/P93k/3+f7" +
                        "PN/v6/P5/vdCWOzeIwEAAoEAAEAO8GNJAPYALI1tDaBwKxNVSwMruIkx0lbF" +
                        "0mT7NQCwbMlkXLSAqvTutYAqdTPfVNqoDWhOfApWMbdUhlv2koqqbBbNoUFK" +
                        "5kzmKfvFbtXOTub0p6lPfACEhSCwXBxSrr3T4MxOEP/Y/thOiF4Eoire31vV" +
                        "OABPvIokBvsGel/0JRBVPLBuBEKp/YaDDF1yG8M/kr4fk5IQRSENtInyud98" +
                        "9l5e1mPa1O8AFmvog+2jg6BhBiN2oQ4DVseD+kPTTrMZAf2hvDWIPnV25d35" +
                        "Lu293KStx+HB3iE01rOiyDVgyEL3GCd7I0tn48O3rbkPAKsJjshW2cLL4dcd" +
                        "qNVfa6bGnfEBrehszSMOAh49M4M0S6hjH5P2ZfN1rTO+oD6zyP/uczTUoWAy" +
                        "siC/YOa0crXn/WFuV/CgmtP70zSbFyXDMrXzni61VDul8K+pYbnuiOzq5N7m" +
                        "ivIgGelLsZNH+18jpB5pt2qN+caaYcIgkWdrp1hGUZTWBt+zIrsJ82TF4o+w" +
                        "905qzv3856ylTOsryHzwL0kGLbtQIIXYrcxNvJNEhvJjsz3Wb66sokKcQh2w" +
                        "XWq5JZisjOKaWOMlL0YDGppuNtcZgG607Ruq6uODsA8FSq4ID6UHJVOQszMQ" +
                        "KU8EmqGZNnkB8xK2KkQ+HPcy21pjxQs11HCBfCJxl65xW+yVAVxKpXuOS0C0" +
                        "3B9eDdR7pH47tTb8SeV9V9P8dUyaU9QvGsVkh7u1hq7EY/y9ciWkYzXmzYuG" +
                        "mZd0ZejARA15R8mTYvwM4lXPUamIaEpxvgZSSf0IcyB9Qfv2I6eJAwezPCb9" +
                        "a3rtPQr4L3ydnjx5tjNZ2HsmkmTke0tpSi066mHNQX5QphAky+hWkepx1isr" +
                        "UswQOEY3rQC0oc897EgrDR63LvBft3GAgxtRwWDUMzPpd+Ncr/5Ce6WM+c/t" +
                        "68If7paUnWogiNCj16smqYVR/btADX7Dvo5D8abQSqKOE3DTHi+XuFzfaJNJ" +
                        "vhzI9klVAEF2wxIqSjxHlU9TFXzO6XR57H5YieiDxRMHBhmD+eO2Ftdf17g/" +
                        "J2VFLVBuKkYkOMk+7c9fB8hUc83hp8ImwNyQcntLN9gT00rcNaEK7IHqQ8dK" +
                        "D40ka9sX3dHU5bIibjpuvgMyQKK4IEPKLRFCdStlykcb+yn+xXzb5TXUgo8O" +
                        "AzmJFnrKSkAqbgfzQXSbn6SfFmKeQWseqxHRiwSHxU4PiknR1VWk6/3i5k6X" +
                        "5JqMBIoh0TGPUrFO99yWgsviut8exUTN6q45+MThj0oPMAWiw4Vm918PXFp9" +
                        "gLskH4O8PyNcLX7/K3oDtaxe0u5ZqF1Ki6nwe5Kh4TF6GPb8IkZ7j7gGPbkb" +
                        "ahWr1cNcmgLJsQxmradLhMdl41qsyu5wetjAOtwL0kG9fk9yklutZdoHSpdQ" +
                        "feLxtq8Rm8llXMQDnsZkY+KLOqeRL0sejQxrqXD/D+b3WExJmANYpbBemsLR" +
                        "7Diuvg2ICMP2bEKjm3rVM+LpUnPvazUP3uM9eUnNZ99wQdu8LOmRojxjHN/X" +
                        "Xr3Q8zsLNthE8v4VHiL/JMg3J3eevOZsawOubCjSkE2gn3jkl2PCnmqt68Oa" +
                        "z4sRhGDqCEOiy8WUhfNZHDmn9A4rSnFRA1p0sfCKsWWwFlydpD/0B1e0isW9" +
                        "9VailAtbzMdsvJWAe+JtI1riuNcshkSfHkWHf5p8v/wx6ldRUeLJS3YzkZmu" +
                        "uujOfJlgbHSWDV272Aza6N2txmW8XYoPVPU7pZB7JrT4YXqG1ugEZ/vjBK2I" +
                        "fdhLDxd4ONEAqTnqgheJy3aIxuUI0N5FPcP4Bb490RLIqzqF7fEuajIgNxeN" +
                        "8G8ZRzcdWVnPQK22qJ9rz11RBXfoTX94YMtvKN+vCNW7YY274gzSCkkcM/1D" +
                        "3yx5UTDcpJ16U79PcxLiejzcyPjSMnbXN/EIzplIMMX1czW1vVmTKtf7bktO" +
                        "oExRn7SPvRHGeyyQKsoLSr1bwErDs9VqWhYVXcpdEEiL1UKCVvBNjSym7u/3" +
                        "B3xQd5nDGDs+e56R5LGtTM9M/qppUKJcVQpmhBXoo4jP4esx2hhWQ9XsnYfr" +
                        "ms5xhObFu1beTPPrw9/w4KnnsjSpA15mwhpG99I5HIKn7DXbWHeSPE0ENqNQ" +
                        "rdOyJgoMtTN0qnsqV8sf8VlzpSM7X2vJAPlOLEp1vlSALGb1y3mVXDm4n3ve" +
                        "cBAphJN/p9uGFH41b9HjwLf9s+WKLvVJbyLktV50Tu26MqY4vXb7/HnTNTOZ" +
                        "+lms3nYFp4Qun5JDx05MjN8PM7C4btAkL5W0caduuXTblZlChLBCpJsArWFi" +
                        "jZI0Al56+dqdL5IHhouTmnniLYn+154bttnlym22jH9dcWxjXFuaXg5TWPS4" +
                        "RZ1Lghw7U7F5X2nVjuyWlBcBqWDdvjvr+SombB+KULe3xhKiVyirl3bd5T+J" +
                        "IuOGlJo5M1KH0W6PR42ODuzVUzzd5fPWwSv6lyMO5fZtgB87Xm6Iyx57BNwG" +
                        "5SkPxupKJytuoRrNQyfIMktmCeQI3EeJSDh2D//nGGvKYnq1awxddtLFmQac" +
                        "QQg1jzriQ+PLeWvIRoJ6XmT5C4VF9C3+sraoyhDhanLRbdrIEWmqP5sGfTWJ" +
                        "LiWFn3uK5ByuMXegg5EbXD3Tb18UfQl83xKsyUfKL4DyZdZmpl1B66Qxocaz" +
                        "9b9x75xosgydf3WwUYcH0Oc97HyW91uZIG6ia4J4wyK54VM29yLC2WgZgMNV" +
                        "at2VkGZRwGvFUjfimnpfws9xepq/sTVAVzeVkx7Mfv1lM9VorGvaYQ1Ggwk2" +
                        "NdcxzrIe0ptx7S73oqn7bQUbRDtidD+6tjTD5QraliuiwaJ5oWps1w6D7maI" +
                        "YMHRnHWp77owmmRdMy0IAFQd+jddkNnJf20lwM03UMUfR8T6BroE4DxJWC8P" +
                        "V1dXzE72uFsJnES497gD/lIRLqi+QXznpdRfKrKLTwLwP/qPmvLdhX6ufzKj" +
                        "v1N+nP7YT4Qb/1dw/s76cVqZn1hlwH/9PcKCX+D7td07a3RnFxb6fvoTwPLm" +
                        "GvsJAAA="
                ),
            // The mapped collection APIs shouldn't be bytecode only, but it is better that they are
            // tracked that way than not tracked at all.
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class EmptyStringList implements kotlin.jvm.internal.markers.KMappedMarker java.util.List<java.lang.String> {
                    ctor public EmptyStringList();
                    method @BytecodeOnly public void add(int, String!);
                    method @BytecodeOnly public boolean add(String!);
                    method @BytecodeOnly public boolean addAll(int, java.util.Collection<? extends java.lang.String!>!);
                    method @BytecodeOnly public boolean addAll(java.util.Collection<? extends java.lang.String!>!);
                    method @BytecodeOnly public void clear();
                    method @BytecodeOnly public boolean contains(Object!);
                    method public boolean contains(String element);
                    method public boolean containsAll(java.util.Collection<${maybeExtends}java.lang.String> elements);
                    method public Void get(int index);
                    method public int getSize();
                    method @BytecodeOnly public int indexOf(Object!);
                    method public int indexOf(String element);
                    method public boolean isEmpty();
                    method public Void iterator();
                    method @BytecodeOnly public int lastIndexOf(Object!);
                    method public int lastIndexOf(String element);
                    method public Void listIterator();
                    method public Void listIterator(int index);
                    method @BytecodeOnly public String! remove(int);
                    method @BytecodeOnly public boolean remove(Object!);
                    method @BytecodeOnly public boolean removeAll(java.util.Collection<? extends java.lang.Object!>!);
                    method @BytecodeOnly public void replaceAll(java.util.function.UnaryOperator<java.lang.String!>!);
                    method @BytecodeOnly public boolean retainAll(java.util.Collection<? extends java.lang.Object!>!);
                    method @BytecodeOnly public String! set(int, String!);
                    method @BytecodeOnly public int size();
                    method @BytecodeOnly public void sort(java.util.Comparator<? super java.lang.String!>!);
                    method public test.pkg.EmptyStringList subList(int fromIndex, int toIndex);
                    method @BytecodeOnly public Object![]! toArray();
                    method @BytecodeOnly public <T> T![]! toArray(T![]!);
                    property public int size;
                  }
                }
                """
        )
    }

    @Test
    fun `Inner class with different number of type parameters than outer class`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        class Outer<T> {
                            inner class Middle<K, V> {
                                inner class Inner<A, B, C>
                            }
                        }
                        """
                    )
                ),
            api =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Outer<T> {
                    ctor public Outer();
                  }
                  public final class Outer.Middle<K, V> {
                    ctor public Outer.Middle();
                  }
                  public final class Outer.Middle.Inner<A, B, C> {
                    ctor public Outer.Middle.Inner();
                  }
                }
                """
        )
    }
}
