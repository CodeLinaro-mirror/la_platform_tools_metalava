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

import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class SignatureToJDiffCommand :
    MetalavaSubCommand(
        help =
            """
                Convert an API signature file into a file in the JDiff XML format.
            """
                .trimIndent()
    ) {

    private val strip by
        option(
                help =
                    """
                        Determines whether duplicate inherited methods should be stripped from the
                        output or not.
                    """
                        .trimIndent()
            )
            .flag("--no-strip", default = false, defaultForHelp = "false")

    private val baseApiFile by
        option(
                "--base-api",
                metavar = "<base-api-file>",
                help =
                    """
                        Optional base API file. If provided then the output will only include API
                        items that are not in this file.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val apiFile by
        argument(
                name = "<api-file>",
                help =
                    """
                        API signature file to convert to the JDiff XML format.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val xmlFile by
        argument(
                name = "<xml-file>",
                help =
                    """
                        Output JDiff XML format file.
                    """
                        .trimIndent()
            )
            .newFile()

    override fun run() {
        val convertFile = ConvertFile(apiFile, xmlFile, baseApiFile, strip)
        convertFile.process()
    }
}
