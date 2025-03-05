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

package com.android.tools.metalava.config

import com.android.tools.metalava.config.ApiFlagConfig.Mutability.IMMUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Mutability.MUTABLE
import com.android.tools.metalava.config.ApiFlagConfig.Status.DISABLED
import com.android.tools.metalava.config.ApiFlagConfig.Status.ENABLED
import com.android.tools.metalava.testing.xml
import org.junit.Test

class ApiFlagsConfigTest : BaseConfigParserTest() {
    @Test
    fun `Test simple`() {
        roundTrip(
            Config(
                apiFlags =
                    ApiFlagsConfig(
                        flags =
                            listOf(
                                ApiFlagConfig(
                                    pkg = "test.pkg",
                                    name = "flag_name",
                                    mutability = IMMUTABLE,
                                    status = ENABLED,
                                ),
                                ApiFlagConfig(
                                    pkg = "test.pkg",
                                    name = "other_flag_name",
                                    mutability = MUTABLE,
                                    status = DISABLED,
                                ),
                            )
                    )
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <api-flags>
                    <api-flag package="test.pkg" name="flag_name" mutability="immutable" status="enabled"/>
                    <api-flag package="test.pkg" name="other_flag_name" mutability="mutable" status="disabled"/>
                  </api-flags>
                </config>
            """
        )
    }

    @Test
    fun `Test duplicate`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-flags>
                        <api-flag package="test.pkg" name="flag_name" mutability="immutable" status="enabled"/>
                        <api-flag package="test.pkg" name="flag_name" mutability="mutable" status="disabled"/>
                      </api-flags>
                    </config>
                """
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: cvc-identity-constraint.4.2.2: Duplicate key value [test.pkg,flag_name] declared for identity constraint "ApiFlagByQualifiedName" of element "config".
                """,
        )
    }
}
