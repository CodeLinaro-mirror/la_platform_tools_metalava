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

package com.android.tools.metalava.cli.flag

import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newOrExistingFile
import com.android.tools.metalava.cli.common.stdout
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.api.flags.optionalFlagName
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option

class ListFlagsCommand :
    MetalavaSubCommand(
        help =
            """
                List flags referenced in signature files.

                Reads signature files and produces a sorted, unique list of the flag names referenced in
                `@FlaggedApi` annotations.
            """
                .trimIndent(),
    ) {

    private val outputFile by
        option(
                "--output-file",
                help =
                    """
                        The output file into which the list of flags will be written. If not
                        specified then the flags are written to stdout.
                    """
                        .trimIndent(),
            )
            .newOrExistingFile()

    private val files by
        argument(
                name = "<files>",
                help =
                    """
                        Signature files from which flag names will be extracted.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        val codebaseConfig = Codebase.Config.NOOP
        val signatureFileLoader = DefaultSignatureFileLoader(codebaseConfig)
        val flags = mutableSetOf<String>()

        val visitor =
            object : BaseItemVisitor(visitParameterItems = true) {
                override fun visitItem(item: Item) {
                    for (annotation in item.modifiers.annotations()) {
                        val flagName = annotation.optionalFlagName ?: continue
                        flags += flagName
                    }
                }
            }

        for (file in files) {
            val signatureFiles = SignatureFile.fromFiles(file)
            val codebase = signatureFileLoader.load(signatureFiles)
            codebase.accept(visitor)
        }

        (outputFile?.printWriter() ?: stdout).use { writer ->
            for (flag in flags.sorted()) {
                writer.println(flag)
            }
        }
    }
}
