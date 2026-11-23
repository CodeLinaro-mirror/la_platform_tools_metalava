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
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_SEALED_CLASSES
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class SealedLintTest : DriverTest() {
    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `members in sealed class are not hidden abstract`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            sealed class ModifierLocalMap() {
                                internal abstract operator fun <T> set(key: ModifierLocal<T>, value: T)
                                internal abstract operator fun <T> get(key: ModifierLocal<T>): T?
                                internal abstract operator fun contains(key: ModifierLocal<*>): Boolean
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No parameter ordering for sealed class constructor`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            sealed class Foo(
                                default: Int = 0,
                                required: () -> Unit,
                            )
                        """
                    ),
                ),
        )
    }

    @Test
    fun `exhaustive sealed interface`() {
        check(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            expectedIssues =
                "src/test/pkg/Foo.java:3: warning: `exhaustive` sealed classes cannot be extended without breaking source compatibility; add a subclass that is not in the API to make it `non-exhaustive` [ExhaustiveSealedClass]",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed interface Foo {
                                final class Subclass implements Foo {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public sealed exhaustive interface Foo permits test.pkg.Foo.Subclass {
                      }
                      public static final class Foo.Subclass implements test.pkg.Foo {
                        ctor public Foo.Subclass();
                      }
                    }
                """,
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `non-exhaustive sealed class`() {
        check(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public abstract sealed class Foo {
                                private Foo() {}
                                public static final class Subclass extends Foo {
                                    private Subclass() {}
                                }
                                private static final class Private extends Foo {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public abstract sealed non-exhaustive class Foo permits test.pkg.Foo.Subclass {
                      }
                      public static final class Foo.Subclass extends test.pkg.Foo {
                      }
                    }
                """,
            javaLanguageLevel = "17", // required for sealed classes
        )
    }

    @Test
    fun `non-exhaustive concrete sealed class`() {
        check(
            format = FORMAT_V6_WITH_JAVA_SEALED_CLASSES,
            expectedIssues =
                "src/test/pkg/Foo.java:3: warning: Concrete sealed classes are harder to use; make it `abstract` instead [ConcreteSealedClass]",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public sealed class Foo {
                                private Foo() {}
                                public static final class Subclass extends Foo {
                                    private Subclass() {}
                                }
                                private static final class Private extends Foo {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public sealed non-exhaustive class Foo permits test.pkg.Foo.Subclass {
                      }
                      public static final class Foo.Subclass extends test.pkg.Foo {
                      }
                    }
                """,
            javaLanguageLevel = "17", // required for sealed classes
        )
    }
}
