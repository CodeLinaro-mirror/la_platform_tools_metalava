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

package com.android.tools.metalava.cli.flag

import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.xml
import kotlin.test.assertEquals
import org.junit.Test

class FlagReportCommandTest : BaseCommandTest<FlagReportCommand>({ FlagReportCommand() }) {
    @Test
    fun `Test basic report`() {
        commandTest {
            args += "flag-report"

            args +=
                ARG_CONFIG_FILE to
                    xml(
                        "config-empty-api-flags.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../../resources/schemas/config.xsd">
                                <api-flags>
                                    <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="disabled"/>
                                    <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                                    <api-flag package="test.pkg" name="reverted_flag" mutability="immutable" status="disabled"/>
                                </api-flags>
                            </config>
                        """
                    )

            val outputFile = outputFile("flag-report.csv")
            args += "--output-file" to outputFile

            args +=
                signature(
                    """
                    // Signature format: 2.0
                    package test.pkg {
                      @FlaggedApi("test.pkg.unknown_flag") public class FooWithUnknownFlag {
                      }
                      @FlaggedApi("test.pkg.kept_flag") public class FooWithKeptFlag {
                      }
                      @FlaggedApi("test.pkg.finalized_flag") public class FooWithFinalizedFlag {
                      }
                      @FlaggedApi("test.pkg.reverted_flag") public class FooWithRevertedFlag {
                      }
                    }
                """
                )

            verify {
                assertEquals(
                    """
                        test.pkg.finalized_flag,known,finalized
                        test.pkg.kept_flag,known,kept
                        test.pkg.reverted_flag,known,reverted
                        test.pkg.unknown_flag,unknown,reverted
                    """
                        .trimIndent(),
                    outputFile.readText().trimEnd()
                )
            }
        }
    }
}
