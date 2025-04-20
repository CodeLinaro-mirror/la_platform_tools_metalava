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

import com.android.tools.metalava.cli.common.HARD_NEWLINE
import com.android.tools.metalava.cli.common.newFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate

/** The name of the group, can be used in help text to refer to the options in this group. */
const val FLAG_REPORT_OPTIONS_GROUP = "Flag Report"

class FlagReportOptions :
    OptionGroup(
        name = FLAG_REPORT_OPTIONS_GROUP,
        help =
            """
                Options that control the flag report file.
            """
                .trimIndent(),
    ) {

    internal val flagReportFile by
        option(
                "--output-file",
                help =
                    """
                        A file into which Metalava will output a report about how flags provided by
                        a --config-file affect the signature files passed to this command.

                        The extension of the file determines the output. Currently, only `csv` is
                        supported and it will output a CSV file with three columns:

                        1. The qualified flag name.$HARD_NEWLINE
                        2. Either "known" or "unknown".$HARD_NEWLINE
                        3. Either "kept", "finalized", reverted"$HARD_NEWLINE
                    """,
                metavar = "<file>",
            )
            .newFile()
            .required()
            .validate { file ->
                require(file.extension == "csv") {
                    "Extension of flag report file '$file' must be csv but it was '${file.extension}'"
                }
            }
}
