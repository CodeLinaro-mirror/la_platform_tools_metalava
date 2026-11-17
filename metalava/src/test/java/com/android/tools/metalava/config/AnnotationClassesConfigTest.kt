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
import com.android.tools.metalava.config.AnnotationClassConfig.TargetsConfig
import kotlin.test.assertEquals
import org.junit.Test

class AnnotationClassesConfigTest : BaseConfigParserTest() {
    @Test
    fun `Empty annotation-classes config`() {
        roundTrip(
            Config(annotationClasses = AnnotationClassesConfig()),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <annotation-classes/>
                </config>
            """,
        )
    }

    @Test
    fun `Multiple annotation-classes config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <annotation-classes>
                        <annotation-class name="java.lang.SuppressWarnings" targets="none"/>
                      </annotation-classes>
                    </config>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <annotation-classes>
                        <annotation-class name="kotlin.Suppress" targets="none"/>
                      </annotation-classes>
                    </config>
                """,
            ),
        ) {
            assertEquals(
                Config(
                    annotationClasses =
                        AnnotationClassesConfig(
                            annotationClasses =
                                listOf(
                                    AnnotationClassConfig(
                                        name = "java.lang.SuppressWarnings",
                                        targets = TargetsConfig.NONE,
                                    ),
                                    AnnotationClassConfig(
                                        name = "kotlin.Suppress",
                                        targets = TargetsConfig.NONE,
                                    ),
                                ),
                        ),
                ),
                config,
            )
        }
    }

    @Test
    fun `Test annotation-classes config with targets none`() {
        roundTrip(
            Config(
                annotationClasses =
                    AnnotationClassesConfig(
                        annotationClasses =
                            listOf(
                                AnnotationClassConfig(
                                    name = "java.lang.SuppressWarnings",
                                    targets = TargetsConfig.NONE,
                                ),
                            ),
                    ),
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <annotation-classes>
                    <annotation-class name="java.lang.SuppressWarnings" targets="none"/>
                  </annotation-classes>
                </config>
            """,
        )
    }

    @Test
    fun `Duplicate annotation-class should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <annotation-classes>
                        <annotation-class name="java.lang.SuppressWarnings" targets="none"/>
                        <annotation-class name="java.lang.SuppressWarnings" targets="none"/>
                      </annotation-classes>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: cvc-identity-constraint.4.2.2: Duplicate key value [java.lang.SuppressWarnings] declared for identity constraint "AnnotationClassName" of element "config".
                """,
        )
    }
}
