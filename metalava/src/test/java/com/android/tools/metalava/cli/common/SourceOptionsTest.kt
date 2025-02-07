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

val SOURCE_OPTIONS_HELP =
    """
Sources:

  Options that control which source files will be processed.

  --source-path <path>                       A : separated list of directories containing source files (organized in a
                                             standard Java package hierarchy).
  --stub-packages <package-list>             List of packages (separated by :) which will be used to filter out
                                             irrelevant classes. If specified, only classes in these packages will be
                                             included in signature files, stubs, etc.. This is not limited to just the
                                             stubs; the --stub-packages name is historical.

                                             See `metalava help package-filters` for more information.
    """
        .trimIndent()

class SourceOptionsTest :
    BaseOptionGroupTest<SourceOptions>(
        SOURCE_OPTIONS_HELP,
    ) {

    override fun createOptions() = SourceOptions()
}
