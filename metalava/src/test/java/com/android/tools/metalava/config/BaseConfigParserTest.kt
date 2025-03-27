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
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Base for tests for objects that are loaded from a configuration file. */
open class BaseConfigParserTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    /** Context for the tests. */
    data class TestContext(
        /** The created [Config] being tested. */
        val config: Config,
    )

    /**
     * Run the test.
     *
     * @param configFiles The config files to parse.
     * @param expectedFail The expected failure.
     * @param body the body of the test which checks the state of the [Config] object which is made
     *   available as [TestContext.config].
     */
    protected fun runTest(
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

    /**
     * Round trip [config], i.e. write it to XML, check it matches [xml], read it back in, check
     * that it matches [config].
     *
     * Writing configuration to XML is not something that Metalava needs at runtime, but it is
     * useful to test what is written to a file as that is what can be read from the file.
     */
    protected fun roundTrip(config: Config, @Language("xml") xml: String) {
        val configFile = temporaryFolder.newFile("round-trip-config.xml")

        config.writeTo(configFile)
        assertThat(configFile.readText().trimEnd()).isEqualTo(xml.trimIndent())

        val readConfig = ConfigParser.parse(listOf(configFile))
        assertThat(readConfig).isEqualTo(config)
    }
}
