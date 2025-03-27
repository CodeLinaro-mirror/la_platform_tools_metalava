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
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiSurfacesConfigTest : BaseConfigParserTest() {
    @Test
    fun `Empty api-surfaces config`() {
        roundTrip(
            Config(apiSurfaces = ApiSurfacesConfig()),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <api-surfaces/>
                </config>
            """,
        )
    }

    @Test
    fun `Simple api-surfaces config`() {
        roundTrip(
            Config(
                apiSurfaces =
                    ApiSurfacesConfig(
                        apiSurfaceList =
                            listOf(
                                ApiSurfaceConfig(
                                    name = "public",
                                ),
                                ApiSurfaceConfig(
                                    name = "system",
                                    extends = "public",
                                ),
                            ),
                    )
            ),
            """
                <config xmlns="http://www.google.com/tools/metalava/config">
                  <api-surfaces>
                    <api-surface name="public"/>
                    <api-surface name="system" extends="public"/>
                  </api-surfaces>
                </config>
            """
        )
    }

    @Test
    fun `Invalid api-surfaces config - invalid name`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-surfaces>
                            <api-surface name="public2"/>
                        </api-surfaces>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:3: cvc-pattern-valid: Value 'public2' is not facet-valid with respect to pattern '[a-z-]+' for type 'ApiSurfaceNameType'.
                        file:TESTROOT/config.xml:3: cvc-attribute.3: The value 'public2' of attribute 'name' on element 'api-surface' is not valid with respect to its type, 'ApiSurfaceNameType'.
                """,
        )
    }

    @Test
    fun `Invalid api-surfaces config - extends non-existent surface`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-surfaces>
                            <api-surface name="system" extends="missing"/>
                        </api-surfaces>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:5: cvc-identity-constraint.4.3: Key 'ApiSurfaceExtendsKeyRef' with value 'missing' not found for identity constraint of element 'config'.
                """,
        )
    }

    @Test
    fun `Invalid api-surfaces config - duplicate surface names`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                        <api-surfaces>
                            <api-surface name="duplicate"/>
                            <api-surface name="duplicate"/>
                        </api-surfaces>
                    </config>
                """,
            ),
            expectedFail =
                """
                    Errors found while parsing configuration file(s):
                        file:TESTROOT/config.xml:4: cvc-identity-constraint.4.2.2: Duplicate key value [duplicate] declared for identity constraint "ApiSurfaceByName" of element "config".
                """,
        )
    }

    @Test
    fun `Multiple api-surfaces config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="public"/>
                      </api-surfaces>
                    </config>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="other"/>
                      </api-surfaces>
                    </config>
                """,
            ),
        ) {
            assertEquals(
                Config(
                    apiSurfaces =
                        ApiSurfacesConfig(
                            apiSurfaceList =
                                listOf(
                                    ApiSurfaceConfig(
                                        name = "public",
                                    ),
                                    ApiSurfaceConfig(
                                        name = "other",
                                    ),
                                ),
                        ),
                ),
                config
            )
        }
    }

    @Test
    fun `Duplicate api-surfaces across config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="public"/>
                      </api-surfaces>
                    </config>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="public"/>
                      </api-surfaces>
                    </config>
                """,
            ),
            expectedFail = "Found duplicate surfaces called `public`"
        )
    }

    @Test
    fun `Cycle in api-surfaces`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="public" extends="module-lib"/>
                        <api-surface name="system" extends="public"/>
                        <api-surface name="module-lib" extends="system"/>
                      </api-surfaces>
                    </config>
                """,
            ),
            expectedFail =
                "Cycle detected in extends relationship: `public` -> `module-lib` -> `system` -> `public`.",
        )
    }

    @Test
    fun `Dag in api-surfaces, module-lib first`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="system" extends="public"/>
                        <api-surface name="module-lib" extends="system"/>
                        <api-surface name="test" extends="system"/>
                        <api-surface name="public"/>
                      </api-surfaces>
                    </config>
                """,
            ),
        ) {
            // The extends property defines a partial order to those surfaces that have an
            // extends relationship but configuration order is preserved otherwise. So, as
            // `module-lib` comes before `test` in the configuration it comes first in the ordered
            // set.
            assertEquals(
                listOf("public", "system", "module-lib", "test"),
                config.apiSurfaces?.orderedSurfaces?.map { it.name }
            )
        }
    }

    @Test
    fun `Dag in api-surfaces, test first`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config">
                      <api-surfaces>
                        <api-surface name="system" extends="public"/>
                        <api-surface name="test" extends="system"/>
                        <api-surface name="module-lib" extends="system"/>
                        <api-surface name="public"/>
                      </api-surfaces>
                    </config>
                """,
            ),
        ) {
            // The extends property defines a partial order to those surfaces that have an
            // extends relationship but configuration order is preserved otherwise. So, as `test`
            // comes before `module-lib` in the configuration it comes first in the ordered set.
            assertEquals(
                listOf("public", "system", "test", "module-lib"),
                config.apiSurfaces?.orderedSurfaces?.map { it.name }
            )
        }
    }

    @Test
    fun `Test invalid extends`() {
        val apiSurfacesConfig =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "other")
                    ),
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { apiSurfacesConfig.orderedSurfaces }
        assertEquals(
            "Surface `system` extends an unknown surface `other`, expected one of `public`, `system`",
            exception.message
        )
    }

    /**
     * Check that [ApiSurfacesConfig.contributesTo] returns the expected result.
     *
     * @param name The name of the surface whose contributes are to be checked.
     * @param expectedSurfaces The list of expected surface names.
     */
    private fun ApiSurfacesConfig.assertContributesTo(
        name: String,
        expectedSurfaces: List<String>
    ) {
        val surfaceConfig = getByNameOrError(name) { "unknown `$it`" }
        assertEquals(
            expectedSurfaces,
            contributesTo(surfaceConfig).map { it.name },
            "contributions to $name"
        )
    }

    @Test
    fun `Test contributesTo`() {
        val apiSurfacesConfig =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "test", extends = "system"),
                        ApiSurfaceConfig(name = "module-lib", extends = "system"),
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "public"),
                    ),
            )

        apiSurfacesConfig.assertContributesTo(
            "public",
            listOf(
                "public",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "system",
            listOf(
                "public",
                "system",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "test",
            listOf(
                "public",
                "system",
                "test",
            )
        )

        apiSurfacesConfig.assertContributesTo(
            "module-lib",
            listOf(
                "public",
                "system",
                "module-lib",
            )
        )
    }
}
