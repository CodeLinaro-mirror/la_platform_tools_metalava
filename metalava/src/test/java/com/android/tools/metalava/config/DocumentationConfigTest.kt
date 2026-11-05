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

import com.android.tools.metalava.testing.xml
import kotlin.test.assertEquals
import org.junit.Test

class DocumentationConfigTest : BaseConfigParserTest() {
    @Test
    fun `Empty documentation config`() {
        roundTrip(
            Config(documentation = DocumentationConfig()),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <documentation/>
                </config>
            """,
        )
    }

    @Test
    fun `Single external-package config`() {
        roundTrip(
            Config(
                documentation =
                    DocumentationConfig(
                        externalPackages = listOf(ExternalPackageConfig(prefix = "androidx")),
                    ),
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <documentation>
                    <external-package prefix="androidx"/>
                  </documentation>
                </config>
            """,
        )
    }

    @Test
    fun `Multiple external-package config`() {
        roundTrip(
            Config(
                documentation =
                    DocumentationConfig(
                        externalPackages =
                            listOf(
                                ExternalPackageConfig(prefix = "androidx"),
                                ExternalPackageConfig(prefix = "com.google.android.material"),
                            ),
                    ),
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <documentation>
                    <external-package prefix="androidx"/>
                    <external-package prefix="com.google.android.material"/>
                  </documentation>
                </config>
            """,
        )
    }

    @Test
    fun `Multiple documentation config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <documentation>
                        <external-package prefix="androidx"/>
                      </documentation>
                    </config>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <documentation>
                        <external-package prefix="com.google.android.material"/>
                      </documentation>
                    </config>
                """,
            ),
        ) {
            assertEquals(
                Config(
                    documentation =
                        DocumentationConfig(
                            externalPackages =
                                listOf(
                                    ExternalPackageConfig(prefix = "androidx"),
                                    ExternalPackageConfig(prefix = "com.google.android.material"),
                                ),
                        ),
                ),
                config,
            )
        }
    }

    @Test
    fun `Duplicate external-package prefix should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <documentation>
                        <external-package prefix="androidx"/>
                        <external-package prefix="androidx"/>
                      </documentation>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: cvc-identity-constraint.4.2.2: Duplicate key value [androidx] declared for identity constraint "ExternalPackagePrefix" of element "config".
                """,
        )
    }

    @Test
    fun `Invalid external-package prefix should error`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <documentation>
                        <external-package prefix="androidx.*"/>
                      </documentation>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:3: cvc-pattern-valid: Value 'androidx.*' is not facet-valid with respect to pattern '[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*' for type 'PackagePrefixType'.
                        file:TESTROOT/config.xml:3: cvc-attribute.3: The value 'androidx.*' of attribute 'prefix' on element 'external-package' is not valid with respect to its type, 'PackagePrefixType'.
                """,
        )
    }
}
