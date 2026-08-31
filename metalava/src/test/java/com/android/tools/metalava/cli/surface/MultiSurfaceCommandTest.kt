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

package com.android.tools.metalava.cli.surface

import com.android.tools.metalava.API_LEVELS_GENERATION_OPTIONS_HELP
import com.android.tools.metalava.API_SELECTION_OPTIONS_HELP
import com.android.tools.metalava.CONFIG_FILE_OPTIONS_HELP
import com.android.tools.metalava.GENERAL_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.MISCELLANEOUS_OPTIONS_HELP
import com.android.tools.metalava.NULLABILITY_VALIDATION_HELP
import com.android.tools.metalava.SIGNATURE_FILE_OPTIONS_HELP
import com.android.tools.metalava.STUB_GENERATION_OPTIONS_HELP
import com.android.tools.metalava.cli.common.COMMON_BASELINE_OPTIONS_HELP
import com.android.tools.metalava.cli.common.ISSUE_REPORTING_OPTIONS_HELP
import com.android.tools.metalava.cli.common.MULTIPLATFORM_OPTIONS_HELP
import com.android.tools.metalava.cli.common.SOURCE_OPTIONS_HELP
import com.android.tools.metalava.cli.compatibility.COMPATIBILITY_CHECK_OPTIONS_HELP
import com.android.tools.metalava.cli.lint.API_LINT_OPTIONS_HELP
import com.android.tools.metalava.cli.signature.SIGNATURE_FORMAT_OPTIONS_HELP
import com.android.tools.metalava.cli.util.SingleSurfaceOptions.Companion.optionsForSurface
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Tests for [MultiSurfaceCommand] and [SingleSurfaceCommand] (which is run in the context of a
 * [MultiSurfaceCommand]).
 */
class MultiSurfaceCommandTest : BaseMultiSurfaceCommandTest() {
    @Test
    fun `Test multi-surface help`() {
        commandTest {
            args += listOf("multi-surface", "--help")

            expectedStdout =
                """
                Usage: metalava multi-surface [options] <sub-command>? ...

                  Command that sets up state to run operations for multiple API surfaces.

                  Should be run with one or more `single-surface` subcommands to process the API surfaces.

                Options:
                  -h, -?, --help                             Show this message and exit
                """
                    .trimIndent() +
                    "\n\n$CONFIG_FILE_OPTIONS_HELP\n\n$SOURCE_OPTIONS_HELP\n\n" +
                    """
                Sub-commands:
                  single-surface                             Performs operations on a single API surface.
                """
                        .trimIndent()
        }
    }

    @Test
    fun `Test single-surface help`() {
        commandTest {
            args += listOf("multi-surface", "single-surface", "--help")

            expectedStdout =
                """
                The multi-surface command is currently experimental

                Usage: metalava multi-surface single-surface [options]

                  Performs operations on a single API surface.

                Options:
                  -h, -?, --help                             Show this message and exit
                """
                    .trimIndent() +
                    "\n\n" +
                    listOf(
                            NULLABILITY_VALIDATION_HELP,
                            ISSUE_REPORTING_OPTIONS_HELP,
                            COMMON_BASELINE_OPTIONS_HELP,
                            GENERAL_REPORTING_OPTIONS_HELP,
                            API_SELECTION_OPTIONS_HELP,
                            API_LINT_OPTIONS_HELP,
                            MULTIPLATFORM_OPTIONS_HELP,
                            COMPATIBILITY_CHECK_OPTIONS_HELP,
                            SIGNATURE_FILE_OPTIONS_HELP,
                            SIGNATURE_FORMAT_OPTIONS_HELP,
                            STUB_GENERATION_OPTIONS_HELP,
                            API_LEVELS_GENERATION_OPTIONS_HELP,
                            MISCELLANEOUS_OPTIONS_HELP,
                        )
                        .joinToString("\n\n")
        }
    }

    @Test
    fun `Test running multiple single-surface commands`() {
        runTest(
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        public class Public {}
                        """
                    ),
                    java(
                        """
                        package test.pkg;
                        import test.annotation.SystemApi;
                        @SystemApi
                        public class System {}
                        """,
                    ),
                    java(
                        """
                        package test.pkg;
                        import test.annotation.TestApi;
                        @TestApi
                        public class Test {}
                        """
                    )
                ),
            expectedOutput =
                """
                The multi-surface command is currently experimental
                The single-surface command is currently experimental
                The single-surface command is currently experimental
                The single-surface command is currently experimental
                """
                    .trimIndent(),
            optionsForSurface(
                apiSurface = KnownApiSurface.TEST_PUBLIC_API_SURFACE,
                expectedApiSignature =
                    """
                    package test.pkg {
                      public class Public {
                        ctor public Public();
                      }
                    }
                    """
            ),
            optionsForSurface(
                apiSurface = KnownApiSurface.TEST_SYSTEM_API_SURFACE,
                expectedApiSignature =
                    """
                    package test.pkg {
                      public class System {
                        ctor public System();
                      }
                    }
                    """
            ),
            optionsForSurface(
                apiSurface = KnownApiSurface.TEST_API_SURFACE,
                expectedApiSignature =
                    """
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                    """
            )
        )
    }
}
