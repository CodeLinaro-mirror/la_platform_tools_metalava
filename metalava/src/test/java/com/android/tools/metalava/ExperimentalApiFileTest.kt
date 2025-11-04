/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class ExperimentalApiFileTest : DriverTest() {

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't annotate package as suppress compatibility when it contains a non-experimental class and an experimental package`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        class MyNonExperimentalClass {}
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg.sub

                        @RequiresOptIn
                        class MyExperimentalClass {}
                        """
                            .trimIndent()
                    ),
                ),
            api =
                """
                package test.pkg {
                  public final class MyNonExperimentalClass {
                    ctor public MyNonExperimentalClass();
                  }
                }
                package @SuppressCompatibility test.pkg.sub {
                  @SuppressCompatibility @kotlin.RequiresOptIn public final class MyExperimentalClass {
                    ctor public MyExperimentalClass();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Annotate package as suppress compatibility when it contains an experimental package and experimental class`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn
                        annotation class ExperimentalAnnotation
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg.sub

                        @RequiresOptIn
                        class MyExperimentalClass {}
                        """
                            .trimIndent()
                    ),
                ),
            api =
                """
                package @SuppressCompatibility test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public @interface ExperimentalAnnotation {
                  }
                }
                package @SuppressCompatibility test.pkg.sub {
                  @SuppressCompatibility @kotlin.RequiresOptIn public final class MyExperimentalClass {
                    ctor public MyExperimentalClass();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't annotate package as suppress compatibility when it contains a non-experimental package`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn
                        annotation class ExperimentalAnnotation
                        """
                    ),
                    kotlin(
                        """
                        package test.pkg.sub

                        class MyNonExperimentalClass {}
                        """
                            .trimIndent()
                    ),
                ),
            api =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn public @interface ExperimentalAnnotation {
                  }
                }
                package test.pkg.sub {
                  public final class MyNonExperimentalClass {
                    ctor public MyNonExperimentalClass();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Annotate package as suppress compatibility when it contains just an experimental file facade class`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
                        @Retention(AnnotationRetention.BINARY)
                        annotation class Experimental

                        @Experimental
                        fun myFunA() {}

                        @Experimental
                        fun myFunB() {}
                        """
                    ),
                ),
            api =
                """
                package @SuppressCompatibility test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn(level=kotlin.RequiresOptIn.Level.ERROR) @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface Experimental {
                  }
                  @SuppressCompatibility public final class ExperimentalKt {
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunA();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Annotate package as suppress compatibility when all classes in the API surface are marked as experimental, even if non-public classes are not marked experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
                        @Retention(AnnotationRetention.BINARY)
                        annotation class Experimental

                        @Experimental
                        class MyClassA {}

                        private class MyClassB {}
                        """
                    ),
                ),
            api =
                """
                package @SuppressCompatibility test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn(level=kotlin.RequiresOptIn.Level.ERROR) @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface Experimental {
                  }
                  @SuppressCompatibility @test.pkg.Experimental public final class MyClassA {
                    ctor public MyClassA();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Annotate package as suppress compatibility when all members are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
                        @Retention(AnnotationRetention.BINARY)
                        annotation class Experimental

                        @Experimental
                        class MyClassA {}

                        @Experimental
                        class MyClassB {}
                        """
                    ),
                ),
            api =
                """
                package @SuppressCompatibility test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn(level=kotlin.RequiresOptIn.Level.ERROR) @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface Experimental {
                  }
                  @SuppressCompatibility @test.pkg.Experimental public final class MyClassA {
                    ctor public MyClassA();
                  }
                  @SuppressCompatibility @test.pkg.Experimental public final class MyClassB {
                    ctor public MyClassB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't annotate package as suppress compatibility when not all members are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
                        @Retention(AnnotationRetention.BINARY)
                        annotation class Experimental

                        class MyClassA {}

                        @Experimental
                        class MyClassB {}
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @SuppressCompatibility @kotlin.RequiresOptIn(level=kotlin.RequiresOptIn.Level.ERROR) @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface Experimental {
                  }
                  public final class MyClassA {
                    ctor public MyClassA();
                  }
                  @SuppressCompatibility @test.pkg.Experimental public final class MyClassB {
                    ctor public MyClassB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("kotlin.RequiresOptIn")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava marks class as experimental if all top-level functions are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class Experimental

                        @Experimental
                        fun myFunA() {}

                        @Experimental
                        fun myFunB() {}
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                  @SuppressCompatibility public final class ExperimentalKt {
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunA();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava marks class as experimental even if some non-public items are not experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class Experimental

                        @Experimental
                        fun myFunA() {}

                        @Experimental
                        fun myFunB() {}

                        private fun myFunC() {}

                        internal fun myFunD() {}

                        internal const val a: Int = 1
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                  @SuppressCompatibility public final class ExperimentalKt {
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunA();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Mark file facade as experimental if fields and properties are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class Experimental

                        @Experimental
                        const val a: Int = 1
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                  @SuppressCompatibility public final class ExperimentalKt {
                    property @SuppressCompatibility @test.pkg.Experimental public static int a;
                    field @SuppressCompatibility @test.pkg.Experimental public static final int a = 1; // 0x1
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't mark file facade as experimental if not all fields and properties are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class Experimental

                        @Experimental
                        const val a: Int = 1

                        val b: Int = 2

                        @Experimental
                        fun myFun() {}
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                  public final class ExperimentalKt {
                    method @InaccessibleFromKotlin public static int getB();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFun();
                    property @SuppressCompatibility @test.pkg.Experimental public static int a;
                    property public static int b;
                    field @SuppressCompatibility @test.pkg.Experimental public static final int a = 1; // 0x1
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not mark class as experimental if not all top-level functions are experimental`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class Experimental

                        fun myFunA() {}

                        @Experimental
                        fun myFunB() {}

                        @Experimental
                        fun myFunC() {}
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Experimental {
                  }
                  public final class ExperimentalKt {
                    method public static void myFunA();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunB();
                    method @SuppressCompatibility @test.pkg.Experimental public static void myFunC();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.Experimental")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not propagate annotations to object declarations`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        object MyObject {
                            @ExperimentalFeature
                            val a: Int = 1

                            @ExperimentalFeature
                            fun myFun() {}
                        }

                        class MyOuterClass {
                            const val b: MyObject = MyObject()
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class MyObject {
                    method @InaccessibleFromKotlin @SuppressCompatibility @test.pkg.ExperimentalFeature public int getA();
                    method @SuppressCompatibility @test.pkg.ExperimentalFeature public void myFun();
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public int a;
                    field public static final test.pkg.MyObject INSTANCE;
                  }
                  public final class MyOuterClass {
                    ctor public MyOuterClass();
                    property public static test.pkg.MyObject b;
                    field public final test.pkg.MyObject b;
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava propagates desired annotation companion object as field`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        class MyOuterClass {
                            @ExperimentalFeature
                            const val a: Int = 0

                            @ExperimentalFeature
                            companion object {
                                @ExperimentalFeature
                                const val b: Int = 0
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  public final class MyOuterClass {
                    ctor public MyOuterClass();
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static int a;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final test.pkg.MyOuterClass.Companion Companion;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public final int a = 0; // 0x0
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final int b = 0; // 0x0
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.Companion {
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static int b;
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not propagate annotation to inner class as field`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        class MyClassField {}

                        class MyOuterClass {
                            @ExperimentalFeature
                            const val a: Int = 0

                            @ExperimentalFeature
                            class MyInnerClass { }

                            const val c: MyInnerClass = null

                            @ExperimentalFeature
                            const val myField: MyClassField = null

                            @ExperimentalFeature
                            companion object MyCompObjectWithNonDefaultName {
                                @ExperimentalFeature
                                const val b: Int = 0
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  public final class MyClassField {
                    ctor public MyClassField();
                  }
                  public final class MyOuterClass {
                    ctor public MyOuterClass();
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static int a;
                    property public static test.pkg.MyOuterClass.MyInnerClass c;
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static test.pkg.MyClassField myField;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final test.pkg.MyOuterClass.MyCompObjectWithNonDefaultName MyCompObjectWithNonDefaultName;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public final int a = 0; // 0x0
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final int b = 0; // 0x0
                    field public final test.pkg.MyOuterClass.MyInnerClass c;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public final test.pkg.MyClassField myField;
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyCompObjectWithNonDefaultName {
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static int b;
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyInnerClass {
                    ctor public MyOuterClass.MyInnerClass();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check for no unintended behavior when having class as member inside of companion object`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        class MyClassField {}

                        class MyOuterClass {

                            @ExperimentalFeature
                            companion object MyCompObjectWithNonDefaultName {
                                @ExperimentalFeature
                                const val myClassFieldA: MyClassField
                                const val myClassFieldB: MyClassField
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  public final class MyClassField {
                    ctor public MyClassField();
                  }
                  public final class MyOuterClass {
                    ctor public MyOuterClass();
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final test.pkg.MyOuterClass.MyCompObjectWithNonDefaultName MyCompObjectWithNonDefaultName;
                    field @SuppressCompatibility @test.pkg.ExperimentalFeature public static final test.pkg.MyClassField myClassFieldA;
                    field public static final test.pkg.MyClassField myClassFieldB;
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyCompObjectWithNonDefaultName {
                    property @SuppressCompatibility @test.pkg.ExperimentalFeature public static test.pkg.MyClassField myClassFieldA;
                    property public static test.pkg.MyClassField myClassFieldB;
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava propagates desired annotation to inner classes`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
                        @Retention(AnnotationRetention.BINARY)
                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        class MyOuterClass {
                            class MyNestedClassA { }
                            class MyNestedClassB { }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @kotlin.RequiresOptIn(level=kotlin.RequiresOptIn.Level.ERROR) @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ExperimentalFeature {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyNestedClassA {
                    ctor public MyOuterClass.MyNestedClassA();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyNestedClassB {
                    ctor public MyOuterClass.MyNestedClassB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava propagates desired annotation to enums`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        class MyOuterClass {
                            enum class Day {
                                MONDAY,
                                TUESDAY,
                                WEDNESDAY,
                                THURSDAY,
                                FRIDAY,
                                SATURDAY,
                                SUNDAY
                            }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public enum MyOuterClass.Day {
                    enum_constant public static final test.pkg.MyOuterClass.Day FRIDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day MONDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day SATURDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day SUNDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day THURSDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day TUESDAY;
                    enum_constant public static final test.pkg.MyOuterClass.Day WEDNESDAY;
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava propagates desired annotation to interfaces`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        class MyOuterClass {
                            interface MyInterface {}
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static interface MyOuterClass.MyInterface {
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not propagate undesired annotations to inner classes`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature
                        annotation class MySampleAnnotation

                        @MySampleAnnotation
                        class MyOuterClass {
                            class MyNestedClassA { }
                            class MyNestedClassB { }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @test.pkg.MySampleAnnotation public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  public static final class MyOuterClass.MyNestedClassA {
                    ctor public MyOuterClass.MyNestedClassA();
                  }
                  public static final class MyOuterClass.MyNestedClassB {
                    ctor public MyOuterClass.MyNestedClassB();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MySampleAnnotation {
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava propagates multiple desired annotations to inner classes`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature
                        annotation class MyAnnotation

                        @ExperimentalFeature
                        @MyAnnotation
                        class MyOuterClass {
                            class MyNestedClassA { }
                            class MyNestedClassB { }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyAnnotation {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature @test.pkg.MyAnnotation public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature @test.pkg.MyAnnotation public static final class MyOuterClass.MyNestedClassA {
                    ctor public MyOuterClass.MyNestedClassA();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature @test.pkg.MyAnnotation public static final class MyOuterClass.MyNestedClassB {
                    ctor public MyOuterClass.MyNestedClassB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations =
                arrayOf("test.pkg.ExperimentalFeature", "test.pkg.MyAnnotation")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not propagate duplicate annotations`() {
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        class MyOuterClass {
                            @ExperimentalFeature
                            class MyNestedClassA { }
                            class MyNestedClassB { }
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class MyOuterClass {
                    ctor public MyOuterClass();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyNestedClassA {
                    ctor public MyOuterClass.MyNestedClassA();
                  }
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public static final class MyOuterClass.MyNestedClassB {
                    ctor public MyOuterClass.MyNestedClassB();
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Check that Metalava does not propagate annotations to decorators`() {
        // TODO: this test should probably be modified or deleted once
        //   passing down annotation classes is handled: b/292090022
        check(
            format = FileFormat.V4,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class ExperimentalFeature

                        @ExperimentalFeature
                        class ClassA {

                            annotation class MyInnerAnnotation

                            @MyInnerAnnotation fun myMethodA() {}
                        }

                        class ClassB {
                            @ClassA.MyInnerAnnotation fun myMethodB() {}
                        }
                        """
                    ),
                ),
            api =
                """
                package test.pkg {
                  @SuppressCompatibility @test.pkg.ExperimentalFeature public final class ClassA {
                    ctor public ClassA();
                    method @test.pkg.ClassA.MyInnerAnnotation public void myMethodA();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public static @interface ClassA.MyInnerAnnotation {
                  }
                  public final class ClassB {
                    ctor public ClassB();
                    method @test.pkg.ClassA.MyInnerAnnotation public void myMethodB();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExperimentalFeature {
                  }
                }
                    """,
            suppressCompatibilityMetaAnnotations = arrayOf("test.pkg.ExperimentalFeature")
        )
    }
}
