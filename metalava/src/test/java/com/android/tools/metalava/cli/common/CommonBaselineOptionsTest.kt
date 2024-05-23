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

package com.android.tools.metalava.cli.common

val COMMON_BASELINE_OPTIONS_HELP =
    """
Baseline Files:

  Options that provide general control over baseline files.

  --delete-empty-baselines                   If true, if after updating a baseline file is empty then it will be
                                             deleted.
  --pass-baseline-updates                    Normally, encountering errors will fail the build, even when updating
                                             baselines. This flag will record issues in baseline files but otherwise
                                             ignore them so that all the baselines in the source tree can be updated in
                                             one go.
    """
        .trimIndent()

class CommonBaselineOptionsTest :
    BaseOptionGroupTest<CommonBaselineOptions>(
        COMMON_BASELINE_OPTIONS_HELP,
    ) {

    override fun createOptions() = CommonBaselineOptions()
}
