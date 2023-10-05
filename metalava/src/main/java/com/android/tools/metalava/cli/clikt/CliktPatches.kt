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

package com.android.tools.metalava.cli.clikt

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.output.HelpFormatter.ParameterHelp
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Get a list of all the parameter related help information for another command.
 *
 * Uses reflection to access the private CliktCommand.allHelpParams method.
 */
fun CliktCommand.allHelpParams(): List<ParameterHelp> {
    // Call the private CliktCommand.allHelpParams method.
    val allHelp =
        CliktCommand::class
            .declaredMemberFunctions
            .first { it.name == "allHelpParams" }
            .apply { isAccessible = true }
            .call(this)

    // The cast is `safe` as the method being called does return that type. If that changes, e.g.
    // after upgrading to a later version then this will be updated.
    @Suppress("UNCHECKED_CAST") return allHelp as List<ParameterHelp>
}
