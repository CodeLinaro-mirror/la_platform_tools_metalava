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

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val FLAG_REPORT_OPTIONS_HELP =
    """
Flag Report:

  Options that control the flag report file.

  --output-file <file>                       A file into which Metalava will output a report about how flags provided by
                                             a --config-file affect the signature files passed to this command.

                                             The extension of the file determines the output. Currently, only `csv` is
                                             supported and it will output a CSV file with three columns:

                                             1. The qualified flag name.
                                             2. Either "known" or "unknown".
                                             3. Either "kept", "finalized", reverted"
                                             (required)
    """
        .trimIndent()

class FlagReportOptionsTest : BaseOptionGroupTest<FlagReportOptions>(FLAG_REPORT_OPTIONS_HELP) {
    override fun createOptions() = FlagReportOptions()
}
