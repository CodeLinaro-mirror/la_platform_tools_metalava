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

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val GENERAL_REPORTING_OPTIONS_HELP =
    """
General Reporting:

  Options that control the reporting of general issues, i.e. not API lint or compatibility check issues.

  --baseline <file>                          An optional baseline file that contains a list of known general issues
                                             which should be ignored.
  --update-baseline <file>                   An optional file into which a list of the latest general issues found will
                                             be written. If --baseline is specified and is a different file then any
                                             issues listed in there will be copied into this file; that minimizes the
                                             amount of churn in the baseline file when updating by not removing legacy
                                             issues that have been fixed, and any unbaselined issues will still be
                                             reported as errors. If --update-baseline and --baseline refer to the same
                                             file then the baseline will be updated in place from scratch and no issues
                                             will be reported as errors. If --delete-empty-baselines is specified and
                                             this baseline is empty then the file will be deleted.
    """
        .trimIndent()

class GeneralReportingOptionsTest :
    BaseOptionGroupTest<GeneralReportingOptions>(GENERAL_REPORTING_OPTIONS_HELP) {
    override fun createOptions() = GeneralReportingOptions()
}
