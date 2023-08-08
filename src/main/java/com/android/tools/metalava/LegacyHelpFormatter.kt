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

package com.android.tools.metalava

import com.github.ajalt.clikt.output.HelpFormatter
import com.github.ajalt.clikt.output.Localization

/** Extends [MetalavaHelpFormatter] to append information about the legacy flags. */
internal class LegacyHelpFormatter(terminalSupplier: () -> Terminal, localization: Localization) :
    MetalavaHelpFormatter(terminalSupplier, localization) {
    override fun formatHelp(
        prolog: String,
        epilog: String,
        parameters: List<HelpFormatter.ParameterHelp>,
        programName: String
    ): String {
        val extendedEpilog = "```${options.getUsage(terminal)}```$epilog"
        return super.formatHelp(prolog, extendedEpilog, parameters, programName)
    }
}
