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

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.cli.common.BaseCommandTest
import com.android.tools.metalava.cli.common.COMMON_BASELINE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.common.MULTIPLATFORM_OPTIONS_HELP
import com.android.tools.metalava.cli.common.SOURCE_OPTIONS_HELP
import com.android.tools.metalava.cli.compatibility.COMPATIBILITY_CHECK_OPTIONS_HELP
import com.android.tools.metalava.cli.lint.API_LINT_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import java.io.File
import org.junit.Assert
import org.junit.Test

class MainCommandTest :
    BaseCommandTest<MainCommand>({ executionEnvironment ->
        MainCommand(
            commonOptions = CommonOptions(),
            executionEnvironment = executionEnvironment,
        )
    }) {

    private val EXPECTED_HELP =
        """
Usage: metalava main [options] [source-files]...

  The default sub-command that is run if no sub-command is specified.

Options:
  -h, --help                                 Show this message and exit

$SOURCE_OPTIONS_HELP

$NULLABILITY_VALIDATION_HELP

$ISSUE_REPORTING_OPTIONS_HELP

$COMMON_BASELINE_OPTIONS_HELP

$GENERAL_REPORTING_OPTIONS_HELP

$CONFIG_FILE_OPTIONS_HELP

$API_SELECTION_OPTIONS_HELP

$API_LINT_OPTIONS_HELP

$MULTIPLATFORM_OPTIONS_HELP

$COMPATIBILITY_CHECK_OPTIONS_HELP

$SIGNATURE_FILE_OPTIONS_HELP

$SIGNATURE_FORMAT_OPTIONS_HELP

$STUB_GENERATION_OPTIONS_HELP

$API_LEVELS_GENERATION_OPTIONS_HELP

$MISCELLANEOUS_OPTIONS_HELP

Arguments:
  source-files                               Additional source files to append to --source-files
        """
            .trimIndent()

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("main", "--help")
            expectedStdout = EXPECTED_HELP
        }
    }

    @Test
    fun `Test invalid option`() {
        commandTest {
            args += listOf("main", "--blah-blah-blah")
            expectedStderr =
                """
Aborting: Error: no such option: "--blah-blah-blah"

$EXPECTED_HELP
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test for @file`() {
        val dir = temporaryFolder.newFolder()
        val files =
            (1..4).map {
                TestFiles.source("File$it.java", "public class File$it {}").createFile(dir)
            }
        val fileList =
            TestFiles.source(
                "files.lst",
                """
                    ${files[0]}
                    ${files[1]} ${files[2]}
                    ${files[3]}
                """
                    .trimIndent()
            )

        val file = fileList.createFile(dir)

        commandTest {
            args += listOf("main", "@$file")

            verify {
                fun normalize(f: File): String = f.relativeTo(dir).path
                Assert.assertEquals(
                    files.map { normalize(it) },
                    command.sourceOptions.sourceFiles.map { normalize(it) }
                )
            }
        }
    }
}
