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

/** Abstract for executing an external command. */
interface CommandExecutor {
    /** Execute [command] piping all its output to [System.out]. */
    fun executeCommand(command: List<String>)
}

/** Default implementation of [CommandExecutor]. */
internal class DefaultCommandExecutor() : CommandExecutor {
    override fun executeCommand(command: List<String>) {
        // Execute the command, piping output to stdout.
        val processBuilder = ProcessBuilder(command).redirectErrorStream(true).start()
        processBuilder.inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                println(line)
            }
        }

        // Wait for the command to finish.
        processBuilder.waitFor()
    }
}
