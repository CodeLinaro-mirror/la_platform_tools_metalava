/*
 * Copyright (C) 2023 The Android Open Source Project
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
import kotlin.test.assertEquals
import org.junit.Test

private val signatureToDexHelp =
    """
Usage: metalava signature-to-dex [options] <api-file>...

  Convert API signature files into a file containing a list of DEX signatures.

Options:
  --out <file>                               Output DEX signatures file. (required)
  -h, -?, --help                             Show this message and exit

Arguments:
  <api-file>                                 API signature files to convert to DEX signatures.
    """
        .trimIndent()

class SignatureToDexCommandTest :
    BaseCommandTest<SignatureToDexCommand>({ SignatureToDexCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("signature-to-dex", "--help")

            expectedStdout = signatureToDexHelp
        }
    }

    private fun checkDexSignatures(vararg signatures: String, expectedDex: String) {
        commandTest {
            args += listOf("signature-to-dex")

            for ((index, signature) in signatures.withIndex()) {
                args += unindentedInputFile("api$index.txt", signature)
            }

            val outFile = outputFile("out.dex")
            args += ARG_OUT
            args += outFile

            verify { assertEquals(expectedDex.trimIndent(), outFile.readText().trim()) }
        }
    }

    @Test
    fun `Test generate dex signatures`() {
        checkDexSignatures(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                    ctor public Child();
                    method public String toString();
                  }
                  public class Parent {
                    ctor public Parent();
                  }
                }
            """,
            expectedDex =
                """
                    Ltest/pkg/Child;
                    Ltest/pkg/Child;-><init>()V
                    Ltest/pkg/Child;->toString()Ljava/lang/String;
                    Ltest/pkg/Parent;
                    Ltest/pkg/Parent;-><init>()V
                """,
        )
    }

    @Test
    fun `Test generate dex signatures - split across multiple files`() {
        checkDexSignatures(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Parent {
                    ctor public Parent();
                  }
                }
            """,
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Child extends test.pkg.Parent {
                    ctor public Child();
                    method public String toString();
                  }
                }
            """,
            expectedDex =
                """
                    Ltest/pkg/Child;
                    Ltest/pkg/Child;-><init>()V
                    Ltest/pkg/Child;->toString()Ljava/lang/String;
                    Ltest/pkg/Parent;
                    Ltest/pkg/Parent;-><init>()V
                """,
        )
    }

    @Test
    fun `Test generate dex signatures erased types`() {
        checkDexSignatures(
            """
                // Signature format: 2.0
                package test.pkg {
                  public class Child {
                    ctor public Child();
                  }
                  public class Parent {
                    ctor public Parent();
                    method protected <T extends test.pkg.Child> T findChild(String);
                    method protected <T> T findObject(String);
                    method protected java.util.List<String> getNames();
                  }
                }
            """,
            expectedDex =
                """
                    Ltest/pkg/Child;
                    Ltest/pkg/Child;-><init>()V
                    Ltest/pkg/Parent;
                    Ltest/pkg/Parent;-><init>()V
                    Ltest/pkg/Parent;->findChild(Ljava/lang/String;)Ltest/pkg/Child;
                    Ltest/pkg/Parent;->findObject(Ljava/lang/String;)Ljava/lang/Object;
                    Ltest/pkg/Parent;->getNames()Ljava/util/List;
                """,
        )
    }
}
