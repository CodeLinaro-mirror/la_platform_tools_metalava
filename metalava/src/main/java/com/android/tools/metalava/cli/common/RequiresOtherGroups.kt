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

import com.github.ajalt.clikt.parameters.groups.OptionGroup

/**
 * Interface for [OptionGroup] implementations that depend on, and access command line options from
 * other [OptionGroup]s and so need to make sure that those other groups are initialized (by being
 * parsed by Clikt).
 *
 * At the moment this is only used for testing purposes.
 */
interface RequiresOtherGroups {
    /** The list of [OptionGroup]s required by this. */
    val requiredGroups: List<OptionGroup>
}
