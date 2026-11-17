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

package com.android.tools.metalava.cli.signature.migration

import java.io.File
import kotlin.test.assertEquals
import org.junit.Test

class GitChangeCommitterTest {
    @Test
    fun `Test execute command`() {
        val commandExecutor = TestCommandExecutor()
        val committer = GitChangeCommitter(commandExecutor)
        val files = listOf(File("file.txt"), File("dir/file.txt"))
        committer.commit(ChangeDescription("title", "detail"), files)

        assertEquals(
            listOf(
                ExecutedCommand(
                    listOf(
                        "git",
                        "commit",
                        "-m",
                        """
                            title

                            detail
                        """
                            .trimIndent(),
                        "file.txt",
                        "dir/file.txt",
                    )
                )
            ),
            commandExecutor.executedCommands
        )
    }
}

/** A [command] executed by [TestCommandExecutor]. */
private data class ExecutedCommand(
    val command: List<String>,
)

/** A [CommandExecutor] that records the commands that were executed. */
private class TestCommandExecutor : CommandExecutor {
    val executedCommands = mutableListOf<ExecutedCommand>()

    override fun executeCommand(command: List<String>) {
        executedCommands.add(ExecutedCommand(command))
    }
}
