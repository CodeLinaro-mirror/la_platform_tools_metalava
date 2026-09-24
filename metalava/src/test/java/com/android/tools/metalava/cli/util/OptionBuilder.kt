/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.cli.util

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.ARG_CONFIG_FILE
import com.android.tools.metalava.testing.TemporaryFolderOwner

/** Returns the CLI args needed to specify the input [configFiles]. */
fun TemporaryFolderOwner.configFileOptions(vararg configFiles: TestFile?): Array<String> {
    if (configFiles.none { it != null }) return emptyArray()
    val configFolder = getOrCreateFolder("config")
    return buildList {
            for (configFile in configFiles) {
                if (configFile != null) {
                    add(ARG_CONFIG_FILE)
                    add(configFile.indented().createFile(configFolder).path)
                }
            }
        }
        .toTypedArray()
}
