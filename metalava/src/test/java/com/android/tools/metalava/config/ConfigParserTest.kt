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

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests of the [ConfigParser]. */
class ConfigParserTest : BaseConfigParserTest() {
    @Test
    fun `Empty config file`() {
        roundTrip(
            Config(),
            """
                <config xmlns="http://www.google.com/tools/metalava/config"/>
            """
        )
    }

    @Test
    fun `Config file with xsi schemaLocation`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../resources/schemas/config.xsd"/>
                """,
            ),
        ) {
            assertThat(config).isEqualTo(Config())
        }
    }

    @Test
    fun `Invalid config file`() {
        runTest(
            xml(
                "config.xml",
                """
                    <invalid xmlns="http://www.google.com/tools/metalava/config"/>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:1: cvc-elt.1.a: Cannot find the declaration of element 'invalid'.
                """,
        )
    }

    @Test
    fun `Syntactically incorrect config file`() {
        runTest(
            xml(
                "config.xml",
                """
                    <!-- Comment -->
                    <config xmlns="http://www.google.com/tools/metalava/config"></other>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:2: The element type "config" must be terminated by the matching end-tag "</config>".
                """,
        )
    }

    @Test
    fun `Multiple config files`() {
        runTest(
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
        ) {
            assertThat(config).isEqualTo(Config())
        }
    }

    @Test
    fun `Unexpected character content in element-only element`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      foo
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:2: cvc-complex-type.2.3: Element 'config' cannot have character [children], because the type's content type is element-only.
                """,
        )
    }

    @Test
    fun `Unexpected character content in empty element`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <issues>
                        <issue name="Issue1" severity="error">bar</issue>
                      </issues>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:3: cvc-complex-type.2.1: Element 'issue' must have no character or element information item [children], because the type's content type is empty.
                """,
        )
    }

    @Test
    fun `Trailing content after closing config tag`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"/>
                    <extra/>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:2: The markup in the document following the root element must be well-formed.
                """,
        )
    }

    @Test
    fun `DTD entity reference is rejected`() {
        runTest(
            xml(
                "config.xml",
                """
                    <!DOCTYPE config [
                      <!ENTITY ext "Issue1">
                    ]>
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <issues>
                        <issue name="&ext;" severity="error"/>
                      </issues>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:6: The entity "ext" was referenced, but not declared.
                """,
        )
    }

    @Test
    fun `Config file with multiple sections`() {
        roundTrip(
            Config(
                buildProperties =
                    BuildPropertiesConfig(
                        properties =
                            listOf(
                                BuildPropertyConfig(name = "PROP", value = "val"),
                            ),
                    ),
                issues =
                    IssuesConfig(
                        issues =
                            listOf(
                                IssueConfig(
                                    name = "Issue1",
                                    severity = IssueConfig.SeverityConfig.ERROR,
                                ),
                            ),
                    ),
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <build-properties>
                    <build-property name="PROP" value="val"/>
                  </build-properties>
                  <issues>
                    <issue name="Issue1" severity="error"/>
                  </issues>
                </config>
            """,
        )
    }
}
