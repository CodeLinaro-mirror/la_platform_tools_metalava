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

import com.android.tools.metalava.DriverTest
import kotlin.test.Test

// These tests do not include property accessors in the signature files to specifically test the
// compatibility issues for properties.
class PropertyCompatibilityTest : DriverTest() {
    @Test
    fun `Unchanged property`() {
        check(
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
    fun `Removed property`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Changed property type`() {
        // TODO(b/300126192): this should fail
        check(
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
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Removed property receiver`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Changed property receiver type`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Changed property type nullability`() {
        check(
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
                load-api.txt:4: error: Source breaking change: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in property Foo.foo [InvalidNullConversion]
                load-api.txt:5: error: Source breaking change: Attempted to change nullability of java.lang.String (from NULLABLE to NONNULL) in property Foo.bar [InvalidNullConversion]
                """,
        )
    }

    @Test
    fun `Changed property receiver type nullability`() {
        // TODO(b/300126192): this should fail
        check(
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
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Change property visibility`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Change property between open and final`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Change property between final and abstract`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }

    @Test
    fun `Change property between default and abstract`() {
        // TODO(b/300126192): this should fail
        check(
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
            expectedIssues = "",
        )
    }
}
