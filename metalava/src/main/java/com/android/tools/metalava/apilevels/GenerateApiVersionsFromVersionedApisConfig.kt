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

package com.android.tools.metalava.apilevels

import java.io.File

/**
 * Properties for the [ApiGenerator.generateFromVersionedApis] method that come from command line
 * options.
 */
data class GenerateApiVersionsFromVersionedApisConfig(
    /** A list of versioned APIs, ordered from the oldest API version to newest. */
    val versionedApis: List<VersionedApi>,

    /** The api versions file that will be generated. */
    val outputFile: File,

    /** The [ApiPrinter] to use to write the API versions to [outputFile]. */
    val printer: ApiPrinter,
)
