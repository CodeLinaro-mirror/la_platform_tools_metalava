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

import com.android.tools.metalava.testing.xml
import org.junit.Test

class BuildPropertiesConfigTest : BaseConfigParserTest() {
    @Test
    fun `Empty build-properties config should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <build-properties>
                      </build-properties>
                    </config>
                """
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:3: cvc-complex-type.2.4.b: The content of element 'build-properties' is not complete. One of '{"http://www.google.com/tools/metalava/config":build-property}' is expected.
                """,
        )
    }

    @Test
    fun `Simple build-properties config`() {
        roundTrip(
            Config(
                buildProperties =
                    BuildPropertiesConfig(
                        properties =
                            listOf(BuildPropertyConfig(name = "BUILD_NUMBER", value = "51234"))
                    )
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <build-properties>
                    <build-property name="BUILD_NUMBER" value="51234"/>
                  </build-properties>
                </config>
            """,
        )
    }

    @Test
    fun `Multiple properties build-properties config`() {
        roundTrip(
            Config(
                buildProperties =
                    BuildPropertiesConfig(
                        properties =
                            listOf(
                                BuildPropertyConfig(name = "BUILD_NUMBER", value = "51234"),
                                BuildPropertyConfig(name = "TARGET", value = "sdk"),
                                BuildPropertyConfig(name = "MAKE_FILE", value = "Android.bp")
                            )
                    )
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <build-properties>
                    <build-property name="BUILD_NUMBER" value="51234"/>
                    <build-property name="TARGET" value="sdk"/>
                    <build-property name="MAKE_FILE" value="Android.bp"/>
                  </build-properties>
                </config>
            """,
        )
    }

    @Test
    fun `Duplicate property should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <build-properties>
                        <build-property name="BUILD_NUMBER" value="51234"/>
                        <build-property name="BUILD_NUMBER" value="61234"/>
                      </build-properties>
                    </config>
                """
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: cvc-identity-constraint.4.2.2: Duplicate key value [BUILD_NUMBER] declared for identity constraint "BuildPropertyName" of element "config".
                """,
        )
    }

    @Test
    fun `Non-alphanumeric build-property name should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <build-properties>
                        <build-property name="BUILD_NUMBER_2" value="51234"/>
                        <build-property name="<BUILD_NUMBER>" value="61234"/>
                      </build-properties>
                    </config>
                """
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: The value of attribute "name" associated with an element type "build-property" must not contain the '<' character.
                """,
        )
    }
}
