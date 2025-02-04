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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests of the [ConfigParser]. */
class ConfigParserTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    /**
     * Run the test.
     *
     * @param configFiles The config files to parse.
     * @param expectedFail The expected failure.
     * @param body the body of the test which checks the state of the [Config] object which is made
     *   available as [TestContext.config].
     */
    private fun runTest(
        vararg configFiles: TestFile,
        expectedFail: String = "",
        body: (TestContext.() -> Unit)? = null,
    ) {
        val dir = temporaryFolder.newFolder()
        val expectingFailure = expectedFail != ""
        val hasBody = body != null

        // If expecting a failure then it should not provide a body and if it is not expecting a
        // failure then it must provide a body.
        if (expectingFailure == hasBody) {
            if (expectingFailure) error("Should not provide a body when expecting a failure")
            else error("Must provide a body when not expecting a failure")
        }

        var errors = ""
        try {
            val files = configFiles.map { it.indented().createFile(dir) }.toList()
            val config = ConfigParser.parse(files)
            val context = TestContext(config = config)
            if (body != null) context.body()
        } catch (e: Exception) {
            errors = cleanupString(e.message ?: "", project = dir)
        }
        assertThat(errors.trimIndent()).isEqualTo(expectedFail.trimIndent())
    }

    /** Context for the tests. */
    private data class TestContext(
        /** The created [Config] being tested. */
        val config: Config,
    )

    /**
     * Round trip [config], i.e. write it to XML, check it matches [xml], read it back in, check
     * that it matches [config].
     *
     * Writing configuration to XML is not something that Metalava needs at runtime, but it is
     * useful to test what is written as that is what can be read.
     */
    private fun roundTrip(config: Config, @Language("xml") xml: String) {
        val xmlMapper = ConfigParser.configXmlMapper()

        val writtenXml = xmlMapper.writeValueAsString(config)
        assertThat(writtenXml.trimEnd()).isEqualTo(xml.trimIndent())

        val readConfig = xmlMapper.readValue(writtenXml, Config::class.java)
        assertThat(readConfig).isEqualTo(config)
    }

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
            assertThat(config)
                .isEqualTo(
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
                    )
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
            assertThat(config.apiSurfaces?.orderedSurfaces?.map { it.name })
                .isEqualTo(listOf("public", "system", "module-lib", "test"))
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
            assertThat(config.apiSurfaces?.orderedSurfaces?.map { it.name })
                .isEqualTo(listOf("public", "system", "test", "module-lib"))
        }
    }
}
