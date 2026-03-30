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
import com.android.tools.metalava.model.text.assertSignatureContents
import org.junit.Test

private val signatureCatHelp =
    """
Usage: metalava signature-reformat [options] <files>...

  Reformats signature files.

  Reformats each signature file to use the specified format.

  The purpose of this is, by working in conjunction with the --use-same-format-as option, to simplify the process for
  updating signature files from one version to the next. It assumes a number of things:

  1. That API signature files are checked into some version control system and need to be updated to reflect changes to
  the API. If they are not then this is not needed.

  2. The build uses the --use-same-format-as to pass the checked in API signature file so that its format will be used
  as the output for the file that the build generates to replace it.

  If those assumptions are met then updating the format version of the API file (and its corresponding removed API file
  if needed) simply involves:

  1. Running this command on the API file specifying the required format. That will reformat it according to the new
  format.

  2. If this changes the format to one that will include new information that was previously not recorded in the
  signature file then the signature file must be regenerated from the sources in order to include the new information.

Options:
  --preserve-structure                       Preserve the structure of the file while changing the format.

                                             Changes the format of the signature file while preserving the properties
                                             from the previous version of the signature file preserving the signature
                                             file structure.

                                             `--format-defaults` should have the same value as that used when the
                                             signature files was last updated to ensure that the structure is preserved.
  -h, -?, --help                             Show this message and exit

$SIGNATURE_FORMAT_OPTIONS_HELP

Arguments:
  <files>                                    Signature files to reformat.
    """
        .trimIndent()

class SignatureReformatCommandTest :
    BaseCommandTest<SignatureReformatCommand>({ SignatureReformatCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-reformat", "--help")

            expectedStdout = signatureCatHelp
        }
    }

    @Test
    fun `Test no args`() {
        commandTest {
            args += listOf("signature-reformat")

            expectedStderr =
                """
                    Aborting: Usage: metalava signature-reformat [options] <files>...

                    Error: Missing argument "<files>"
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Reformat empty to v4`() {
        commandTest {
            args +=
                listOf(
                    "signature-reformat",
                    "--format",
                    "4.0",
                )

            val inputFile = inputFile("api.txt", "")
            args += inputFile

            verify { inputFile.assertSignatureContents("") }
        }
    }

    @Test
    fun `Reformat v2 to v4`() {
        commandTest {
            args +=
                listOf(
                    "signature-reformat",
                    "--format",
                    "4.0",
                )

            val inputFile = inputFile("api.txt", "// Signature format: 2.0")
            args += inputFile

            verify { inputFile.assertSignatureContents("// Signature format: 4.0") }
        }
    }

    @Test
    fun `Reformat v2 to v5 plus properties`() {
        commandTest {
            args +=
                listOf(
                    "signature-reformat",
                    "--format",
                    "5.0:add-additional-overrides=yes",
                )

            val inputFile = inputFile("api.txt", "// Signature format: 2.0")
            args += inputFile

            verify {
                inputFile.assertSignatureContents(
                    """
                        // Signature format: 5.0
                        // - add-additional-overrides=yes
                    """
                )
            }
        }
    }

    @Test
    fun `Reformat v5 to v5 override properties`() {
        commandTest {
            args +=
                listOf(
                    "signature-reformat",
                )

            val inputFile =
                inputFile(
                    "api.txt",
                    """
                        // Signature format: 5.0
                        // - add-additional-overrides=no
                    """
                        .trimIndent()
                )

            // Use the input file's format.
            args += ARG_USE_SAME_FORMAT_AS to inputFile

            // Default some properties.
            args += "--format-overrides"
            args += "name=fred,surface=public,add-additional-overrides=yes"

            args += inputFile

            verify {
                inputFile.assertSignatureContents(
                    """
                        // Signature format: 5.0
                        // - name=fred
                        // - surface=public
                        // - add-additional-overrides=yes
                    """
                )
            }
        }
    }

    @Test
    fun `Reformat signature - v2 to v6 --preserve-structure with --format-defaults and --format-overrides`() {
        commandTest {
            args +=
                listOf(
                    "signature-reformat",
                )

            val inputFile =
                inputFile(
                    "api.txt",
                    """
                        // Signature format: 2.0
                        package pkg {
                            public class Test {
                                method public java.util.Map<java.lang.String,java.lang.String> method();
                            }
                        }
                    """
                        .trimIndent()
                )

            // Preserve the structure of the existing file.
            args += "--preserve-structure"

            // Apply defaults that were applied when the input signature file was created. Shows
            // that these are applied correctly and reflected in the resulting file output.
            args += "--format-defaults"
            args += "add-additional-overrides=yes,overloaded-method-order=source"

            // Provide additional properties. Shows that this can be used to override some
            // properties while still preserving the structure.
            args += "--format"
            args += "6.0:name=fred,surface=public"

            args += inputFile

            verify {
                inputFile.assertSignatureContents(
                    """
                        // Signature format: 6.0
                        // - name=fred
                        // - surface=public
                        // - include-default-parameter-values=no
                        // - kotlin-style-nulls=no
                        // - strip-java-lang-prefix=legacy
                        // - type-argument-spacing=legacy
                        package pkg {
                          public class Test {
                            method public java.util.Map<java.lang.String,java.lang.String> method();
                          }
                        }
                    """
                )
            }
        }
    }
}
