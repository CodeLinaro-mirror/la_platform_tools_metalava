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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.createReportFile
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

const val ARG_OUT = "--out"

class SignatureToDexCommand :
    MetalavaSubCommand(
        help = "Convert API signature files into a file containing a list of DEX signatures.",
    ) {

    private val apiFiles by
        argument(
                name = "<api-file>",
                help = "API signature files to convert to DEX signatures.",
            )
            .existingFile()
            .multiple(required = true)

    private val outFile by
        option(
                ARG_OUT,
                help = "Output DEX signatures file.",
            )
            .newFile()
            .required()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val signatureFileLoader = SignatureFileLoader(annotationManager = noOpAnnotationManager)
        val signatureApi = signatureFileLoader.loadFiles(SignatureFile.fromFiles(apiFiles))

        val apiVisitorConfig = ApiVisitor.Config()
        val apiPredicateConfig = apiVisitorConfig.apiPredicateConfig
        val apiType = ApiType.ALL
        val apiEmit = apiType.getEmitFilter(apiPredicateConfig)
        val apiReference = apiType.getReferenceFilter(apiPredicateConfig)

        createReportFile(progressTracker, signatureApi, outFile, "DEX API") { printWriter ->
            DexApiWriter(
                printWriter,
                apiEmit,
                apiReference,
                apiVisitorConfig,
            )
        }
    }
}
