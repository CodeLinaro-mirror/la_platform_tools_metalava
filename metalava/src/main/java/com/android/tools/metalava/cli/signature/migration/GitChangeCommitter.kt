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

/** Commit a change to a `git` repository. */
internal class GitChangeCommitter(
    private val commandExecutor: CommandExecutor = DefaultCommandExecutor()
) : ChangeCommitter {
    override fun commit(
        description: ChangeDescription,
        files: List<File>,
    ) {
        // Construct the commit message by combining title and detail.
        val message = buildString {
            append(description.title)
            append("\n\n")

            append(description.detail)
        }

        // Build git command to commit the files with the message.
        val command = buildList {
            add("git")
            add("commit")
            add("-m")
            add(message)
            files.mapTo(this) { it.path }
        }

        // Execute the command, piping output to stdout.
        commandExecutor.executeCommand(command)
    }
}
