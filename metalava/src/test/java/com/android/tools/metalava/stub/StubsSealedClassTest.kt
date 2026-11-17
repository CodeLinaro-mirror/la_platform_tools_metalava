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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_SEALED_CLASSES
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsSealedClassTest : AbstractStubsTest() {

    companion object {
        /** A switch over an exhaustive `Sealed` class that needs no default. */
        private val switchNoDefault =
            java(
                """
                    package other;

                    import test.pkg.*;

                    public class Other {
                        public int method(Sealed sealed) {
                            return switch(sealed) {
                                case SubclassA a -> 0;
                                case SubclassB b -> 1;
                                // No default case needed as Sealed is an exhaustive, sealed class,
                                // i.e. all of its subclasses are part of its API surface.
                            };
                        }
                    }
                """
            )

        /**
         * A switch over a non-exhaustive `Sealed` class that should have a default but does not yet
         * need one.
         */
        private val switchWithDefault =
            java(
                """
                    package other;

                    import test.pkg.*;

                    public class Other {
                        public int method(Sealed sealed) {
                            return switch(sealed) {
                                case SubclassA a -> 0;
                                case SubclassB b -> 1;
                                default -> 2;
                            };
                        }
                    }
                """
            )

        /**
         * A switch over a concrete `Sealed` class that needs a `case Sealed s` that will match all
         * subclasses of `Sealed` not matched by one of the preceding cases. This works whether
         * `Sealed` is exhaustive or not.
         */
        val switchWithSealed =
            java(
                """
                    package other;

                    import test.pkg.*;

                    public class Other {
                        public int method(Sealed sealed) {
                            return switch(sealed) {
                                case SubclassA a -> 0;
                                case SubclassB b -> 1;
                                case Sealed s -> 2;
                            };
                        }
                    }
                """
            )
    }

    /**
     * Get [CompilationCheck]s for an exhaustive `abstract sealed` class.
     *
     * Only needs one as there is an exhaustive class does not need a default case.
     */
    private fun compilationChecksForExhaustiveAbstractSealedClass() =
        listOf(
            CompilationCheck(
                label = "pass",
                additionalFiles = listOf(switchNoDefault),
            )
        )

    /**
     * Get [CompilationCheck]s for a non-exhaustive `abstract sealed` class.
     *
     * Has two, one without a default that should fail and one with a default that should pass.
     */
    private fun compilationChecksForNonExhaustiveAbstractSealedClass() =
        listOf(
            CompilationCheck(
                label = "fail",
                additionalFiles = listOf(switchNoDefault),
                expectedFailure =
                    """
                        ADDITIONAL/src/other/Other.java:7: error: the switch expression does not cover all possible input values
                                return switch(sealed) {
                                       ^
                        1 error
                    """,
            ),
            CompilationCheck(
                label = "pass",
                additionalFiles = listOf(switchWithDefault),
            )
        )

    /**
     * Get [CompilationCheck]s for a concrete `sealed` class.
     *
     * Only needs one as it requires a `case Sealed s` and so does not need a default case.
     *
     * This works for both exhaustive and non-exhaustive cases.
     */
    private fun compilationChecksForConcreteSealedClass() =
        listOf(
            CompilationCheck(
                label = "pass",
                additionalFiles = listOf(switchWithSealed),
            )
        )

    @Test
    fun `Test exhaustive abstract sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public abstract sealed exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            Sealed() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForExhaustiveAbstractSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test non-exhaustive abstract sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed class SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public abstract sealed non-exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public abstract sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB, test.pkg.Sealed._Private_ {
                            Sealed() { throw new RuntimeException("Stub!"); }
                            static abstract non-sealed class _Private_ extends test.pkg.Sealed {
                            private _Private_() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForNonExhaustiveAbstractSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test exhaustive concrete sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                        ctor public Sealed(int);
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            public Sealed(int a) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForConcreteSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test non-exhaustive concrete sealed class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Sealed {
                                public Sealed(int a) {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed class SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed class SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed non-exhaustive class Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                        ctor public Sealed(int);
                      }
                      public final class SubclassA extends test.pkg.Sealed {
                        ctor public SubclassA();
                      }
                      public non-sealed class SubclassB extends test.pkg.Sealed {
                        ctor public SubclassB();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed class Sealed permits test.pkg.SubclassA, test.pkg.SubclassB, test.pkg.Sealed._Private_ {
                            public Sealed(int a) { throw new RuntimeException("Stub!"); }
                            static abstract non-sealed class _Private_ extends test.pkg.Sealed {
                            private _Private_() { super(0); throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class SubclassA extends test.pkg.Sealed {
                            public SubclassA() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed class SubclassB extends test.pkg.Sealed {
                            public SubclassB() { super(0); throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForConcreteSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test exhaustive sealed interface with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassB extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed exhaustive interface Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public non-sealed interface SubclassA extends test.pkg.Sealed {
                      }
                      public non-sealed interface SubclassB extends test.pkg.Sealed {
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed interface Sealed permits test.pkg.SubclassA, test.pkg.SubclassB {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassA extends test.pkg.Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassB extends test.pkg.Sealed {
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForExhaustiveAbstractSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test non-exhaustive sealed interface with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassA extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public non-sealed interface SubclassB extends Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            non-sealed interface SubclassPrivate extends Sealed {
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public sealed non-exhaustive interface Sealed permits test.pkg.SubclassA test.pkg.SubclassB {
                      }
                      public non-sealed interface SubclassA extends test.pkg.Sealed {
                      }
                      public non-sealed interface SubclassB extends test.pkg.Sealed {
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public sealed interface Sealed permits test.pkg.SubclassA, test.pkg.SubclassB, test.pkg.Sealed__Private_ {
                            }
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            abstract non-sealed class Sealed__Private_ implements test.pkg.Sealed {
                            private Sealed__Private_() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassB extends test.pkg.Sealed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public non-sealed interface SubclassA extends test.pkg.Sealed {
                            }
                        """
                    ),
                ),
            compilationChecks = compilationChecksForNonExhaustiveAbstractSealedClass(),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test non-exhaustive sealed interface nested in class with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Outer {
                                private Outer() {}
                                // The interface is protected to make sure it cannot be occessed
                                // outside subclasses of Outer.
                                protected sealed interface Sealed {
                                    final class Subclass implements Sealed {}
                                }
                                private static final class Private implements Sealed {}
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public class Outer {
                      }
                      protected static sealed non-exhaustive interface Outer.Sealed permits test.pkg.Outer.Sealed.Subclass {
                      }
                      public static final class Outer.Sealed.Subclass implements test.pkg.Outer.Sealed {
                        ctor public Outer.Sealed.Subclass();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Outer {
                            Outer() { throw new RuntimeException("Stub!"); }
                            protected static sealed interface Sealed permits test.pkg.Outer.Sealed.Subclass, test.pkg.Outer.Sealed__Private_ {
                            public static final class Subclass implements test.pkg.Outer.Sealed {
                            public Subclass() { throw new RuntimeException("Stub!"); }
                            }
                            }
                            private static abstract non-sealed class Sealed__Private_ implements test.pkg.Outer.Sealed {
                            private Sealed__Private_() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `Test non-exhaustive sealed interface nested in interface with java-sealed-classes=yes`() {
        checkStubs(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public interface Outer {
                                sealed interface Sealed {
                                    final class Subclass implements Sealed {}
                                }
                            }

                            final class Private implements Outer.Sealed {}
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 6.0
                    // - style=java
                    // - java-sealed-classes=yes
                    package test.pkg {
                      public interface Outer {
                      }
                      public static sealed non-exhaustive interface Outer.Sealed permits test.pkg.Outer.Sealed.Subclass {
                      }
                      public static final class Outer.Sealed.Subclass implements test.pkg.Outer.Sealed {
                        ctor public Outer.Sealed.Subclass();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public interface Outer {
                            public static sealed interface Sealed permits test.pkg.Outer.Sealed.Subclass, test.pkg.Outer_Sealed__Private_ {
                            public static final class Subclass implements test.pkg.Outer.Sealed {
                            public Subclass() { throw new RuntimeException("Stub!"); }
                            }
                            }
                            }
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            abstract non-sealed class Outer_Sealed__Private_ implements test.pkg.Outer.Sealed {
                            private Outer_Sealed__Private_() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            javaLanguageLevel = "17", // required for sealed classes
        )
    }
}
