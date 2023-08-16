/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Base class for tests of [OptionGroup] classes. */
abstract class BaseOptionGroupTest<O : OptionGroup>(
    private val factory: () -> O,
    private val expectedHelp: String
) : TemporaryFolderOwner {

    @get:Rule override val temporaryFolder = TemporaryFolder()

    /** Run a test on the [OptionGroup] of type [O]. */
    protected fun runTest(vararg args: String, test: (O) -> Unit) {
        val command = MockCommand(factory, test)
        command.parse(args.toList())
    }

    @Test
    fun `Test help`() {
        val e = Assert.assertThrows(PrintHelpMessage::class.java) { runTest {} }

        val formattedHelp =
            e.command
                .getFormattedHelp()
                .removePrefix(
                    """
Usage: mock [options]

Options:
  -h, --help                                 Show this message and exit

        """
                        .trimIndent()
                )
                .trim()
        Assert.assertEquals(expectedHelp, formattedHelp)
    }
}

private class MockCommand<O : OptionGroup>(factory: () -> O, val test: (O) -> Unit) :
    CliktCommand(printHelpOnEmptyArgs = true) {
    val options by factory()

    init {
        context {
            localization = MetalavaLocalization()
            helpFormatter = MetalavaHelpFormatter(::plainTerminal, localization)
        }
    }

    override fun run() {
        test(options)
    }
}
