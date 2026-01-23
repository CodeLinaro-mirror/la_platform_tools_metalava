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

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

const val ARG_MULTIPLATFORM_ENABLED = "--multiplatform-enabled"

class MultiplatformOptions :
    OptionGroup(
        name = "Multiplatform API Options",
        help = "Options controlling the handling of multiplatform API operations"
    ) {

    val enabled: Boolean by
        option(ARG_MULTIPLATFORM_ENABLED, help = "Flag to enable Multiplatform API operation.")
            .flag()
}
