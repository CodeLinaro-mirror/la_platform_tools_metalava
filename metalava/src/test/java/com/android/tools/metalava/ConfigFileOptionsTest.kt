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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val CONFIG_FILE_OPTIONS_HELP =
    """
Config Files:

  Options that control the configuration files.

  --config-file <file>                       A configuration file that can be consumed by Metalava. This can be
                                             specified multiple times in which case later config files will
                                             override/merge with earlier ones.
    """
        .trimIndent()

class ConfigFileOptionsTest : BaseOptionGroupTest<ConfigFileOptions>(CONFIG_FILE_OPTIONS_HELP) {
    override fun createOptions() = ConfigFileOptions()
}
