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

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.config.Config
import com.android.tools.metalava.config.ConfigParser
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option

const val ARG_CONFIG_FILE = "--config-file"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val CONFIG_FILE_OPTIONS_GROUP = "Config Files"

class ConfigFileOptions :
    OptionGroup(
        name = CONFIG_FILE_OPTIONS_GROUP,
        help =
            """
                Options that control the configuration files.
            """
                .trimIndent(),
    ) {

    private val configFiles by
        option(
                ARG_CONFIG_FILE,
                help =
                    """
                        A configuration file that can be consumed by Metalava. This can be specified
                        multiple times in which case later config files will override/merge with
                        earlier ones.
                    """,
                metavar = "<file>",
            )
            .existingFile()
            .multiple(required = false)

    /** The [Config] loaded from [configFiles]. */
    val config by
        lazy(LazyThreadSafetyMode.NONE) {
            try {
                ConfigParser.parse(configFiles)
            } catch (e: Exception) {
                throw MetalavaCliException(e.message!!, cause = e)
            }
        }
}
