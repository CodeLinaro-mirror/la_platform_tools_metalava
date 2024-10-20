/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.metalava.cli.common.executionEnvironment
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.cli.common.stderr
import com.android.tools.metalava.reporter.BasicReporter
import com.github.ajalt.clikt.parameters.arguments.argument

/** Generates a JDiff XML file from a jar. */
class JarToJDiffCommand :
    MetalavaSubCommand(
        help =
            """
                Convert a jar file into a file in the JDiff XML format.

                This is intended for use by the coverage team to extract information needed to
                determine test coverage of the API from the stubs jars. Any other use is
                unsupported.
            """
                .trimIndent()
    ) {

    private val jarFile by
        argument(
                name = "<jar-file>",
                help =
                    """
                        Jar file to convert to the JDiff XML format.
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
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        StandaloneJarCodebaseLoader.create(
                executionEnvironment,
                progressTracker,
                BasicReporter(stderr)
            )
            .use { jarCodebaseLoader ->
                val codebase = jarCodebaseLoader.loadFromJarFile(jarFile)

                val apiType = ApiType.PUBLIC_API
                val apiPredicateConfig = ApiPredicate.Config()
                val apiFilters = apiType.getApiFilters(apiPredicateConfig)

                createReportFile(progressTracker, codebase, xmlFile, "JDiff File") { printWriter ->
                    JDiffXmlWriter(
                            writer = printWriter,
                        )
                        .createFilteringVisitor(
                            apiFilters = apiFilters,
                            preFiltered = false,
                            showUnannotated = false,
                        )
                }
            }
    }
}
