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

package com.android.tools.metalava.compatibility

import com.android.tools.lint.checks.infrastructure.TestFiles.base64gzip
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.cli.common.ARG_ERROR_CATEGORY
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import kotlin.test.Test

// These tests do not include property accessors in the signature files to specifically test the
// compatibility issues for properties.
class PropertyCompatibilityTest : DriverTest() {
    @Test
    fun `Unchanged property`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            expectedIssues = "",
        )
    }

    @Test
    fun `Added property`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            expectedIssues =
                "load-api.txt:4: error: Added property test.pkg.Foo#foo [AddedProperty]",
        )
    }

    @Test
    fun `Added abstract property`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public abstract class AbstractClass {
                    ctor public AbstractClass();
                  }
                  public abstract sealed exhaustive class SealedClass {
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public abstract class AbstractClass {
                    ctor public AbstractClass();
                    property public abstract int needsExternalOverride;
                  }
                  public abstract sealed exhaustive class SealedClass {
                    property public abstract int cannotHaveExternalOverride;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:5: error: Source breaking change: Added property test.pkg.AbstractClass#needsExternalOverride [AddedAbstractProperty]
                load-api.txt:8: error: Added property test.pkg.SealedClass#cannotHaveExternalOverride [AddedProperty]
                """,
        )
    }

    @Test
    fun `Change in whether inherited property is listed`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends test.pkg.Parent {
                    property public int removeFromFoo;
                  }
                  public class Parent {
                    property public int addToFoo;
                    property public int removeFromFoo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends test.pkg.Parent {
                    property public int addToFoo;
                  }
                  public class Parent {
                    property public int addToFoo;
                    property public int removeFromFoo;
                  }
                }
                """,
            expectedIssues = "",
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Change in whether inherited property from classpath is listed`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends other.pkg.Parent {
                    property public int removeFromFoo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo extends other.pkg.Parent {
                    property public int addToFoo;
                  }
                }
                """,
            classpath =
                arrayOf(
                    /*
                    Generated from the following Kotlin file:
                    package other.pkg
                    open class Parent {
                        open val addToFoo: Int = 0
                        open val removeFromFoo: Int = 0
                    }
                     */
                    base64gzip(
                        "classpath.jar",
                        // kotlinc version info: kotlinc-jvm 1.9.23 (JRE 21.0.8+9-LTS)
                        "" +
                            "H4sIAAAAAAAA/wvwZmYRYeDg4GBgYFBkQAYiDCwMvq4hjrqefm76vo5+nm6u" +
                            "wSF6vm7/TjEwfPY9c9rHW1fvIq+3rta5M+c3BxlcMX7wtEjPy1fH0/di6aot" +
                            "QR+8dAu1vM6c0Q77cE7/5Mkzj58+esrEEODNzrFeWHO9JdACcyAOwGm9GBDn" +
                            "l2SkFukXZKfrByQWpeaV6CXnJBYX5wb69l8KELF9v3VK0AHNDuXm+Y+36+n4" +
                            "3cn0ceP1FP7D4dfBuZq9NvEew9qX1QLT/3DVN2Sbq6mp6fHMXGp/qiK6JzCl" +
                            "+N3e5/cuTy7/9fj59vcMco/MmufPSJ7J9l7t8YOtHqk5XQlH5nW0cixTkmNf" +
                            "36Ze5LLm6AObsI1G/hJrrMt/i4o8qNgQmLLh7MZLPF/fPTsuZzjl+CUZt4uB" +
                            "lzwnxUga7XjEf/AQ1yqLYKbjfD8vFfqfm9T6ZslJHfsVy+rPi165fjxdMuuu" +
                            "Z9IyLb44D2WlRJadH28eWrR8I4+XZIOO2pH94TevLNOrOmAQoSstl1/U/3v1" +
                            "0uN31xSuvPUur3irzYPcb1sKfl18aPfetGb10dvfbl/mP99ZuPfqptTdrPcX" +
                            "un783/raeuZRxtM93q8F3vnzuH7R6VQ57Bd+9NPTGrMpLydK+KXK3fLw+a0U" +
                            "evPP7C/3VD6XqFhrSX+r+nxjxcw32mszqiWOdwq6qW48ZqeaeHm3xVEnzaDw" +
                            "80yLsiba/1U4lG3CGskTwv1936G/c7jXCVtZnixs3rDJzoEzbl6UZfo/0frG" +
                            "/5nOObmi17d1Sc9Oqu6e8Tit7/cO163Ou4u33JY9wFVo52J8RC7qyiOnju4d" +
                            "JgyXd/RoM08xseldLdXnZpp/Skq0d5fbW3FQ6nAxNmm4xcjA8IkJX+qQBmJ4" +
                            "4sxNzMzTy84vycnMi8/NTynNSU1OSEhIA2KWJD82jYCkC0kM4JT3VWnPXmGg" +
                            "TglwymNkEmFAmI6cKkFJHxXgygjopiC7XgzFhHqc6RndDGRXSqOYMZcJr68D" +
                            "vFnZQMqYgfAykP7JBOIBAHyVkPHiAwAA"
                    )
                ),
            expectedIssues = ""
        )
    }

    @Test
    fun `Removed property`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                  }
                }
                """,
            expectedIssues =
                "released-api.txt:4: error: Source breaking change: Removed property test.pkg.Foo#foo [RemovedProperty]",
        )
    }

    @Test
    fun `Changed property type`() {
        // TODO(b/300126192): this should fail
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final boolean foo;
                  }
                }
                """,
            expectedIssues = ""
        )
    }

    @Test
    fun `Added property receiver`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int String.foo;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Added property test.pkg.Foo#java.lang.String.foo [AddedProperty]
                released-api.txt:4: error: Source breaking change: Removed property test.pkg.Foo#foo [RemovedProperty]
                """,
        )
    }

    @Test
    fun `Removed property receiver`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int String.foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int foo;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Added property test.pkg.Foo#foo [AddedProperty]
                released-api.txt:4: error: Source breaking change: Removed property test.pkg.Foo#java.lang.String.foo [RemovedProperty]
                """,
        )
    }

    @Test
    fun `Changed property receiver type`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int String.foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int Number.foo;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Added property test.pkg.Foo#java.lang.Number.foo [AddedProperty]
                released-api.txt:4: error: Source breaking change: Removed property test.pkg.Foo#java.lang.String.foo [RemovedProperty]
                """,
        )
    }

    @Test
    fun `Changed property type nullability`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final String foo;
                    property public final String? bar;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final String? foo;
                    property public final String bar;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Source breaking change: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in property test.pkg.Foo#foo [InvalidNullConversion]
                load-api.txt:5: error: Source breaking change: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in property test.pkg.Foo#bar [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Changed property receiver type nullability`() {
        // TODO(b/300126192): this should fail
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int String.foo;
                    property public final int String?.bar;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int String?.foo;
                    property public final int String.bar;
                  }
                }
                """,
            expectedIssues = "",
        )
    }

    @Test
    fun `Changed bounds of property type parameter`() {
        // TODO(b/300126192): this should fail
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final <T> int T.foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final <T extends java.lang.String> int T.foo;
                  }
                }
                """,
            expectedIssues = "",
        )
    }

    @Test
    fun `Changed name of property type parameter`() {
        // TODO(b/279394828): this should not fail
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final <T> int T.foo;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final <N> int N.foo;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Added property test.pkg.Foo#N.foo [AddedProperty]
                released-api.txt:4: error: Source breaking change: Removed property test.pkg.Foo#T.foo [RemovedProperty]
                """,
        )
    }

    @Test
    fun `Change property visibility`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int changeToProtected;
                    property protected final int changeToPublic;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property protected final int changeToProtected;
                    property public final int changeToPublic;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Source breaking change: property test.pkg.Foo#changeToProtected changed visibility from PUBLIC to PROTECTED [ChangedScope]
                load-api.txt:5: error: Source breaking change: property test.pkg.Foo#changeToPublic changed visibility from PROTECTED to PUBLIC [ChangedScope]
                """,
        )
    }

    @Test
    fun `Change property between open and final`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public int changeToFinal;
                    property public final int changeToOpen;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Foo {
                    property public final int changeToFinal;
                    property public int changeToOpen;
                  }
                }
                """,
            expectedIssues =
                "load-api.txt:4: error: Source breaking change: property test.pkg.Foo#changeToFinal has added 'final' qualifier [AddedFinal]",
        )
    }

    @Test
    fun `Change property between final and abstract`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public abstract class Foo {
                    property public final int changeToAbstract;
                    property public abstract int changeToFinal;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public abstract class Foo {
                    property public abstract int changeToAbstract;
                    property public final int changeToFinal;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Source breaking change: property test.pkg.Foo#changeToAbstract has changed 'abstract' qualifier [ChangedAbstract]
                load-api.txt:5: error: Source breaking change: property test.pkg.Foo#changeToFinal has added 'final' qualifier [AddedFinal]
                """,
        )
    }

    @Test
    fun `Change property between default and abstract`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    property public default int changeToAbstract;
                    property public abstract int changeToDefault;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public interface Foo {
                    property public abstract int changeToAbstract;
                    property public default int changeToDefault;
                  }
                }
                """,
            expectedIssues =
                "load-api.txt:4: error: Source breaking change: property test.pkg.Foo#changeToAbstract has changed 'default' qualifier [ChangedDefault]",
        )
    }

    @Test
    fun `Change property deprecation`() {
        check(
            extraArguments = arrayOf(ARG_ERROR_CATEGORY, "Compatibility"),
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    property public int changeToDeprecated;
                    property @Deprecated public int changeToNotDeprecated;
                  }
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public final class Foo {
                    property @Deprecated public int changeToDeprecated;
                    property public int changeToNotDeprecated;
                  }
                }
                """,
            expectedIssues =
                """
                load-api.txt:4: error: Source breaking change: property test.pkg.Foo#changeToDeprecated has changed deprecation state false --> true [ChangedDeprecated]
                load-api.txt:5: error: Source breaking change: property test.pkg.Foo#changeToNotDeprecated has changed deprecation state true --> false [ChangedDeprecated]
                """,
        )
    }
}
