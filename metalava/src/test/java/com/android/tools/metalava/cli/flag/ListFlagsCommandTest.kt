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

package com.android.tools.metalava.cli.flag

import com.android.tools.metalava.cli.common.BaseCommandTest
import kotlin.test.assertEquals
import org.junit.Test

class ListFlagsCommandTest : BaseCommandTest<ListFlagsCommand>({ ListFlagsCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("list-flags", "--help")

            expectedStdout =
                """
Usage: metalava list-flags [options] <files>...

  List flags referenced in signature files.

  Reads signature files and produces a sorted, unique list of the flag names referenced in `@FlaggedApi` annotations.

Options:
  --output-file <file>                       The output file into which the list of flags will be written. If not
                                             specified then the flags are written to stdout.
  -h, -?, --help                             Show this message and exit

Arguments:
  <files>                                    Signature files from which flag names will be extracted.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test single signature file with various flagged elements`() {
        commandTest {
            args +=
                listOf(
                    "list-flags",
                    unindentedInputFile(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.on.class") public class Foo {
                                ctor @FlaggedApi("flag.on.constructor") public Foo();
                                method @FlaggedApi("flag.on.method") public void methodA();
                                method @FlaggedApi("flag.on.class") public void methodWithDuplicateFlag();
                                field @FlaggedApi("flag.on.field") public static final int FIELD_A = 1;
                              }
                              public class UnflaggedClass {
                                method @FlaggedApi("flag.another") public void bar();
                              }
                            }
                        """
                    ),
                )

            expectedStdout =
                """
                    flag.another
                    flag.on.class
                    flag.on.constructor
                    flag.on.field
                    flag.on.method
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test multiple signature files with duplicate and unique flags`() {
        commandTest {
            args +=
                listOf(
                    "list-flags",
                    unindentedInputFile(
                        "api1.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.zebra") public class Zebra {
                              }
                              @FlaggedApi("flag.shared") public class Shared1 {
                              }
                            }
                        """
                    ),
                    unindentedInputFile(
                        "api2.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.apple") public class Apple {
                              }
                              @FlaggedApi("flag.shared") public class Shared2 {
                              }
                            }
                        """
                    ),
                )

            expectedStdout =
                """
                    flag.apple
                    flag.shared
                    flag.zebra
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test delta signature file`() {
        commandTest {
            args +=
                listOf(
                    "list-flags",
                    unindentedInputFile(
                        "delta.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class DeltaClass extends test.pkg.BaseClass {
                                method @FlaggedApi("flag.in.delta") public void newMethod();
                              }
                            }
                        """
                    ),
                )

            expectedStdout =
                """
                    flag.in.delta
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test flagged annotations on nested classes, enum constants, and interface methods`() {
        commandTest {
            args +=
                listOf(
                    "list-flags",
                    unindentedInputFile(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.enum") public enum MyEnum {
                                enum_constant @FlaggedApi("flag.enum.constant") public static final test.pkg.MyEnum CONSTANT;
                              }
                              public interface MyInterface {
                                method @FlaggedApi("flag.interface.method") public void ifaceMethod();
                              }
                              @FlaggedApi("flag.nested.class") public static class Outer.Inner {
                              }
                            }
                        """
                    ),
                )

            expectedStdout =
                """
                    flag.enum
                    flag.enum.constant
                    flag.interface.method
                    flag.nested.class
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test output to --output-file`() {
        commandTest {
            val outputFile = outputFile("flags.txt")
            args +=
                listOf(
                    "list-flags",
                    "--output-file",
                    outputFile,
                    unindentedInputFile(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.two") public class Two {}
                              @FlaggedApi("flag.one") public class One {}
                            }
                        """
                    ),
                )

            verify {
                assertEquals(
                    """
                        flag.one
                        flag.two

                    """
                        .trimIndent(),
                    outputFile.readText()
                )
            }
        }
    }

    @Test
    fun `Test output to --output-file creates parent directories`() {
        commandTest {
            val outputFile = temporaryFolder.root.resolve("nested/dir/flags.txt")
            args +=
                listOf(
                    "list-flags",
                    "--output-file",
                    outputFile,
                    unindentedInputFile(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              @FlaggedApi("flag.in.nested.dir") public class Foo {}
                            }
                        """
                    ),
                )

            verify {
                assertEquals(
                    """
                        flag.in.nested.dir

                    """
                        .trimIndent(),
                    outputFile.readText()
                )
            }
        }
    }

    @Test
    fun `Test empty output when no flags`() {
        commandTest {
            args +=
                listOf(
                    "list-flags",
                    unindentedInputFile(
                        "api.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public class Foo {
                                method public void bar();
                              }
                            }
                        """
                    ),
                )

            expectedStdout = ""
        }
    }
}
