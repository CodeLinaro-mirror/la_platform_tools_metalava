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

package com.android.tools.metalava.cli.multiplatform

import com.android.tools.metalava.cli.common.MetalavaOptionGroup
import com.android.tools.metalava.cli.common.existingDir
import com.android.tools.metalava.cli.common.newDir
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

const val ARG_MULTIPLATFORM_ENABLED = "--multiplatform-enabled"

const val ARG_MULTIPLATFORM_API_DIR = "--multiplatform-api-directory"

const val ARG_MULTIPLATFORM_API_SOURCES = "--multiplatform-api-sources"

const val ARG_MULTIPLATFORM_CHECK_COMPATIBILITY = "--multiplatform-compatibility-api"

class MultiplatformOptions :
    MetalavaOptionGroup(
        name = "Multiplatform API Options",
        help = "Options controlling the handling of multiplatform API operations"
    ) {

    val enabled: Boolean by
        option(ARG_MULTIPLATFORM_ENABLED, help = "Flag to enable Multiplatform API operation.")
            .flag()

    val sourceApiDirectory by
        option(
                ARG_MULTIPLATFORM_API_SOURCES,
                help = "Directory containing multiplatform API signature files to parse as source."
            )
            .existingDir()

    val apiDirectory by
        option(
                ARG_MULTIPLATFORM_API_DIR,
                help =
                    """
                    Directory to put multiplatform API signature files.
                    """
                        .trimIndent()
            )
            .newDir()

    val checkReleasedApi by
        option(
                ARG_MULTIPLATFORM_CHECK_COMPATIBILITY,
                help =
                    """
                    Check compatibility of the previously released multiplatform API. The provided
                    file should be a directory containing multiplatform API signature files of the
                    previously released API surface.
                    """
                        .trimIndent()
            )
            .existingDir()
}
