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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.cli.multiplatform.MultiplatformOptions

val MULTIPLATFORM_OPTIONS_HELP =
    """
    Multiplatform API Options:

      Options controlling the handling of multiplatform API operations

      --multiplatform-enabled                    Flag to enable Multiplatform API operation.
      --multiplatform-api-sources <file>         Directory containing multiplatform API signature files to parse as source.
      --multiplatform-api-directory <file>       Directory to put multiplatform API signature files.
      --multiplatform-compatibility-api <file>   Check compatibility of the previously released multiplatform API. The
                                                 provided file should be a directory containing multiplatform API signature
                                                 files of the previously released API surface.
    """
        .trimIndent()

class MultiplatformOptionsTest :
    BaseOptionGroupTest<MultiplatformOptions>(MULTIPLATFORM_OPTIONS_HELP) {
    override fun createOptions() = MultiplatformOptions()
}
