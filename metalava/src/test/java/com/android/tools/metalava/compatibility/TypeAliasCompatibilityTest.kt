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

class TypeAliasCompatibilityTest : DriverTest() {
    @Test
    fun `Unchanged type alias`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
        )
    }

    @Test
    fun `Removed type alias`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                  public typealias Bar = String;
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
            expectedIssues =
                "released-api.txt:4: error: Source breaking change: Removed typealias test.pkg.Bar [RemovedTypeAlias]"
        )
    }

    @Test
    fun `Changed type alias`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = int;
                }
                """,
            expectedIssues =
                "load-api.txt:3: error: Source breaking change: Typealias test.pkg.Foo has changed type from String to int [ChangedType]"
        )
    }

    @Test
    fun `Changed type alias nullability`() {
        check(
            checkCompatibilityApiReleased =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String;
                }
                """,
            signatureSource =
                """
                // Signature format: 5.0
                package test.pkg {
                  public typealias Foo = String?;
                }
                """,
            expectedIssues =
                "load-api.txt:3: error: Source breaking change: Attempted to change nullability of java.lang.String (from NONNULL to NULLABLE) in typealias test.pkg.Foo [InvalidNullConversion]"
        )
    }

    @Test
    fun `Changed type alias to class`() {
        check(
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public typealias Foo = String;
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
                "load-api.txt:3: error: Source breaking change: test.pkg.Foo changed from typealias to class [ChangedClass]",
        )
    }

    @Test
    fun `Changed class to type alias`() {
        check(
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
                      public typealias Foo = String;
                    }
                """,
            expectedIssues =
                """
                    load-api.txt:3: error: Binary breaking change: test.pkg.Foo changed from class to typealias [ChangedClass]
                    released-api.txt:3: error: Binary breaking change: class test.pkg.Foo has been removed from bytecode [RemovedFromBytecode]
                    released-api.txt:3: error: Source breaking change: class test.pkg.Foo can no longer be resolved from Java source [RemovedFromJava]
                """,
        )
    }
}
