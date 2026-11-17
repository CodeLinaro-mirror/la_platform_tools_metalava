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

package com.android.tools.metalava.cli.flag

import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.ApiFlagsCreator
import com.android.tools.metalava.ConfigFileOptions
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.flag.ApiFlagReportProducer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.text.SignatureFile
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate

class FlagReportCommand :
    MetalavaSubCommand(
        help = "Generates a flag report",
    ) {
    private val flagReportOptions by FlagReportOptions()

    private val configFileOptions by ConfigFileOptions()

    private val apiFiles by
        argument(
                name = "<api-file>",
                help = "API signature files for which the flag report is being generated.",
            )
            .existingFile()
            .multiple(required = true)

    override fun run() {
        val apiFlagsConfig =
            configFileOptions.config.apiFlags
                ?: cliError(
                    "Must provide a $ARG_CONFIG_FILE option that specifies a config file containing an `<api-flags/>` entry"
                )

        // Create flags.
        val apiFlags = ApiFlagsCreator.createFromConfig(apiFlagsConfig)!!

        // Load the Codebase from the signature files.
        val codebaseConfig = Codebase.Config.NOOP
        val signatureFileLoader = DefaultSignatureFileLoader(codebaseConfig)
        val signatureApi = signatureFileLoader.load(SignatureFile.fromFiles(apiFiles))

        // Produce a report for how the supplied flags affect the Codebase.
        val report = ApiFlagReportProducer.produceFlagReport(signatureApi, apiFlags)

        // Output the report file.
        val reportFile = flagReportOptions.flagReportFile
        reportFile.printWriter().use { writer ->
            for (apiFlag in report.referencedFlags) {
                val qualifiedName = apiFlag.qualifiedName
                val knownStatus = if (apiFlag.isKnown) "known" else "unknown"
                val actionLabel =
                    when (apiFlag.action) {
                        ApiFlagAction.KEEP -> "kept"
                        ApiFlagAction.FINALIZE -> "finalized"
                        ApiFlagAction.REVERT -> "reverted"
                    }
                val exportedStatus =
                    when {
                        !apiFlag.isKnown -> ""
                        apiFlag.isExported -> ",exported"
                        else -> ",unexported"
                    }
                writer.println("$qualifiedName,$knownStatus,$actionLabel$exportedStatus")
            }
        }
    }
}
