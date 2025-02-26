/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.metalava.config.Config
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests to verify that config files are read correctly by the main command and that errors are
 * surface correctly.
 */
class ConfigFilesTest : DriverTest() {
    @Test
    fun `Empty config file`() {
        check(
            configFiles =
                arrayOf(
                    xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    ),
                ),
        ) {
            assertThat(options.configFileOptions.config).isEqualTo(Config())
        }
    }

    @Test
    fun `Invalid config file`() {
        check(
            configFiles =
                arrayOf(
                    xml(
                        "config.xml",
                        """
                            <invalid xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    ),
                ),
            expectedFail =
                """
                    Aborting: Errors found while parsing configuration file(s):
                        file:TESTROOT/project/config.xml:1: cvc-elt.1.a: Cannot find the declaration of element 'invalid'.
                """,
        )
    }

    @Test
    fun `Multiple config files`() {
        check(
            configFiles =
                arrayOf(
                    xml(
                        "config1.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    ),
                    xml(
                        "config2.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    ),
                ),
        ) {
            assertThat(options.configFileOptions.config).isEqualTo(Config())
        }
    }
}
