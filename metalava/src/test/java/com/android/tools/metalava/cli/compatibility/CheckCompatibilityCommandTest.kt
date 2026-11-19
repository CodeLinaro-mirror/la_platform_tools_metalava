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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.ARG_BASELINE
import com.android.tools.metalava.ARG_UPDATE_BASELINE
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.source
import kotlin.test.assertEquals
import org.junit.Test

/** Expected help text for the `check-compatibility` sub-command. */
private val checkCompatibilityHelp =
    """
Usage: metalava check-compatibility [options] <files>...

  Check compatibility between two APIs.

Options:
  --api-released <file>                      Previously released API signature file against which compatibility will be
                                             checked. Can be specified multiple times; when multiple files are provided
                                             they must be in order from the narrowest API to the widest API. (required)
  --baseline <file>                          An optional baseline file that contains a list of known compatibility
                                             issues which should be ignored.
  --update-baseline <file>                   An optional file into which a list of the latest compatibility issues found
                                             will be written. If --baseline is specified and is a different file then
                                             any issues listed in there will be copied into this file; that minimizes
                                             the amount of churn in the baseline file when updating by not removing
                                             legacy issues that have been fixed, and any unbaselined issues will still
                                             be reported as errors. If --update-baseline and --baseline refer to the
                                             same file then the baseline will be updated in place from scratch and no
                                             issues will be reported as errors. If --delete-empty-baselines is specified
                                             and this baseline is empty then the file will be deleted.
  -h, -?, --help                             Show this message and exit

Arguments:
  <files>                                    Signature files defining the current API to check for compatibility. When
                                             multiple files are provided they must be in order from the narrowest API to
                                             the widest API.
    """
        .trimIndent()

/** Tests for [CheckCompatibilityCommand]. */
class CheckCompatibilityCommandTest :
    BaseCommandTest<CheckCompatibilityCommand>({ CheckCompatibilityCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("check-compatibility", "--help")

            expectedStdout = checkCompatibilityHelp
        }
    }

    @Test
    fun `Test compatible APIs pass`() {
        commandTest {
            args += "check-compatibility"
            args +=
                ARG_API_RELEASED to
                    signature(
                        "released.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void bar();
                              }
                            }
                        """,
                    )
            args +=
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void bar();
                            method public void baz();
                          }
                        }
                    """,
                )
        }
    }

    @Test
    fun `Test incompatible APIs fail`() {
        commandTest {
            args += "check-compatibility"
            args +=
                ARG_API_RELEASED to
                    signature(
                        "released.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void removedMethod();
                              }
                            }
                        """,
                    )
            args +=
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                          }
                        }
                    """,
                )

            expectedStderr =
                """
                    TESTROOT/released.txt:5: error: Binary breaking change: Removed method test.pkg.Foo.removedMethod() [RemovedMethod]

                    Aborting: Found compatibility problems checking the public API (TESTROOT/current.txt) against the API in TESTROOT/released.txt
                """
                    .trimIndent()

            verify { assertEquals(-1, exitCode) }
        }
    }

    @Test
    fun `Test multiple current signature files checked against merged released file`() {
        commandTest {
            args += "check-compatibility"
            args +=
                ARG_API_RELEASED to
                    signature(
                        "system-released.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void publicMethod();
                                method public void systemMethod();
                              }
                            }
                        """,
                    )
            args +=
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                            method public void publicMethod();
                          }
                        }
                    """,
                )
            args +=
                signature(
                    "system-current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            method public void systemMethod();
                          }
                        }
                    """,
                )
        }
    }

    @Test
    fun `Test baseline suppresses known compatibility issue`() {
        commandTest {
            args += "check-compatibility"
            args +=
                ARG_API_RELEASED to
                    signature(
                        "released.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void removedMethod();
                              }
                            }
                        """,
                    )
            args +=
                ARG_BASELINE to
                    source(
                        "baseline.txt",
                        """
                            // Baseline format: 1.0
                            RemovedMethod: test.pkg.Foo#removedMethod():
                                Binary breaking change: Removed method test.pkg.Foo.removedMethod()
                        """,
                    )
            val updatedBaseline = outputFile("updated-baseline.txt")
            args += ARG_UPDATE_BASELINE to updatedBaseline
            args +=
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                          }
                        }
                    """,
                )

            verify {
                assertEquals(
                    """
                        // Baseline format: 1.0
                        RemovedMethod: test.pkg.Foo#removedMethod():
                            Binary breaking change: Removed method test.pkg.Foo.removedMethod()
                    """
                        .trimIndent(),
                    updatedBaseline.readText().trim(),
                )
            }
        }
    }

    @Test
    fun `Test update-baseline writes newly found compatibility issue`() {
        commandTest {
            args += "check-compatibility"
            args +=
                ARG_API_RELEASED to
                    signature(
                        "released.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                ctor public Foo();
                                method public void removedMethod();
                              }
                            }
                        """,
                    )
            args +=
                ARG_BASELINE to
                    source(
                        "baseline.txt",
                        """
                            // Baseline format: 1.0
                        """,
                    )
            val updatedBaseline = outputFile("updated-baseline.txt")
            args += ARG_UPDATE_BASELINE to updatedBaseline
            args +=
                signature(
                    "current.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Foo {
                            ctor public Foo();
                          }
                        }
                    """,
                )

            expectedStdout = "metalava wrote updated baseline to TESTROOT/updated-baseline.txt"
            expectedStderr =
                """
                    TESTROOT/released.txt:5: error: Binary breaking change: Removed method test.pkg.Foo.removedMethod() [RemovedMethod]

                    Aborting: Found compatibility problems checking the public API (TESTROOT/current.txt) against the API in TESTROOT/released.txt
                """
                    .trimIndent()

            verify {
                assertEquals(-1, exitCode)
                assertEquals(
                    """
                        // Baseline format: 1.0
                        RemovedMethod: test.pkg.Foo#removedMethod():
                            Binary breaking change: Removed method test.pkg.Foo.removedMethod()
                    """
                        .trimIndent(),
                    updatedBaseline.readText().trim(),
                )
            }
        }
    }
}
