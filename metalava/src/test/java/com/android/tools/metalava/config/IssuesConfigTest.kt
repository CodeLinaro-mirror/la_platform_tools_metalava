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

package com.android.tools.metalava.config

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.metalava.config.IssueConfig.SeverityConfig
import com.android.tools.metalava.reporter.Issues
import kotlin.test.assertEquals
import org.junit.Test

class IssuesConfigTest : BaseConfigParserTest() {
    @Test
    fun `Empty issues config`() {
        roundTrip(
            Config(issues = IssuesConfig()),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <issues/>
                </config>
            """,
        )
    }

    @Test
    fun `Multiple issues config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <issues>
                        <issue name="Issue1" severity="error"/>
                      </issues>
                    </config>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <issues>
                        <issue name="Issue2" severity="warning"/>
                      </issues>
                    </config>
                """,
            ),
        ) {
            assertEquals(
                Config(
                    issues =
                        IssuesConfig(
                            issues =
                                listOf(
                                    IssueConfig(
                                        name = "Issue1",
                                        severity = SeverityConfig.ERROR,
                                    ),
                                    IssueConfig(
                                        name = "Issue2",
                                        severity = SeverityConfig.WARNING,
                                    ),
                                ),
                        ),
                ),
                config
            )
        }
    }

    @Test
    fun `Test issue severity config`() {
        roundTrip(
            Config(
                issues =
                    IssuesConfig(
                        issues =
                            buildList {
                                for (severityConfig in SeverityConfig.entries) {
                                    add(
                                        IssueConfig(
                                            "Issue${severityConfig.ordinal + 1}",
                                            severityConfig
                                        )
                                    )
                                }
                            },
                    )
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <issues>
                    <issue name="Issue1" severity="hidden"/>
                    <issue name="Issue2" severity="info"/>
                    <issue name="Issue3" severity="warning"/>
                    <issue name="Issue4" severity="error-when-new"/>
                    <issue name="Issue5" severity="error"/>
                  </issues>
                </config>
            """,
        )
    }

    @Test
    fun `Test issue name restriction`() {
        roundTrip(
            Config(
                issues =
                    IssuesConfig(
                        issues =
                            buildList {
                                for (issue in Issues.all) {
                                    add(IssueConfig(issue.name, SeverityConfig.HIDDEN))
                                }
                            },
                    )
            ),
            // This test is checking to make sure that the issue name restriction in the
            // configuration file will accept any of the supported issue names so there is no need
            // to check the generated XML.
            xml = null,
        )
    }
}
