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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.assertSignatureFilesMatch
import org.junit.Assert.*
import org.junit.Test

private val signatureCatHelp =
    """
Usage: metalava signature-cat [options] [<files>]...

  Cats signature files.

  Reads signature files either provided on the command line, or in stdin into a combined API surface and then writes it
  out to either the output file provided on the command line or to stdout according to the format options. The resulting
  output will be different to the input if the input does not already conform to the selected format.

Options:
  --output-file <file>                       File to write the signature output to. If not specified stdout will be
                                             used.
  -h, -?, --help                             Show this message and exit

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <files>                                    Signature files to read, if not specified then they stdin is read instead.
    """
        .trimIndent()

class SignatureCatCommandTest : BaseCommandTest<SignatureCatCommand>({ SignatureCatCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-cat", "--help")

            expectedStdout = signatureCatHelp
        }
    }

    @Test
    fun `Cat from stdin to stdout`() {
        commandTest {
            args +=
                listOf(
                    "signature-cat",
                    "--format",
                    "2.0",
                    "--format-defaults",
                    // Strip java.lang. prefixes just to show that it does transform the input.
                    "strip-java-lang-prefix=always",
                )

            stdin =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo extends java.lang.Comparable<test.pkg.Foo> {
                      }
                    }
                """
                    .trimIndent()

            expectedStdout =
                """
                    // Signature format: 2.0
                    package test.pkg {

                      public interface Foo extends Comparable<test.pkg.Foo> {
                      }

                    }
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Cat from files to stdout`() {
        commandTest {
            args +=
                listOf(
                    "signature-cat",
                    unindentedInputFile(
                        "foo.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface Foo {
                              }
                            }
                        """
                    ),
                    unindentedInputFile(
                        "bar.txt",
                        """
                            // Signature format: 2.0
                            package test.pkg {
                              public interface Bar {
                              }
                            }
                        """
                    ),
                )

            // Stdin should be ignored when files are provided on the command line.
            stdin = "Stdin should be ignored when files are provided on the command line."

            expectedStdout =
                """
                    // Signature format: 2.0
                    package test.pkg {

                      public interface Foo {
                      }

                      public interface Bar {
                      }

                    }
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Cat signature file with missing type parameters`() {
        commandTest {
            args +=
                listOf(
                    "signature-cat",
                    "--format-defaults",
                    // Do not strip java.lang. prefixes to show whether unknown type parameters are
                    // currently prefixed with "java.lang." or not.
                    "strip-java-lang-prefix=never",
                )

            stdin =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public interface Foo {
                        method public void foo(T t);
                      }
                    }
                """
                    .trimIndent()

            expectedStderr =
                """
                    <stdin>:4: hidden: Unqualified type 'T' is not in 'java.lang' and is not a type parameter in scope [UnqualifiedTypeError]
                """
                    .trimIndent()

            expectedStdout =
                // TODO(b/394789173): Stop prefixing T with java.lang..
                """
                    // Signature format: 2.0
                    package test.pkg {

                      public interface Foo {
                        method public void foo(T t);
                      }

                    }
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Cat from file to file`() {
        val signature =
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface Foo {
                  }
                }
            """

        commandTest {
            val outputFile = outputFile("cat.txt")
            args +=
                listOf(
                    "signature-cat",
                    unindentedInputFile("current.txt", signature),
                    "--output-file",
                    outputFile,
                )

            verify {
                assertSignatureFilesMatch(
                    signature,
                    outputFile.readText(Charsets.UTF_8),
                    expectedFormat = FileFormat.V2
                )
            }
        }
    }
}
