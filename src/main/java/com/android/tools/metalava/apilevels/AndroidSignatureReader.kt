/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.SignatureFileLoader
import java.io.File

/**
 * Creates an [Api] from a list of past API signature files. In the generated [Api], the oldest API
 * version will be represented as level 1, the next as level 2, etc.
 *
 * @param previousApiFiles A list of API signature files, one for each version of the API, in order
 * from oldest to newest API version.
 * @param kotlinStyleNulls Whether to assume the inputs are formatted as Kotlin-style nulls.
 */
class AndroidSignatureReader(
    previousApiFiles: List<File>,
    kotlinStyleNulls: Boolean
) {
    val api: Api

    init {
        api = Api(1)
        previousApiFiles.forEachIndexed { index, apiFile ->
            val codebase = SignatureFileLoader.load(apiFile, kotlinStyleNulls)
            // Uses index + 1 as the apiLevel because 0 is not a valid API level
            addApisFromCodebase(api, index + 1, codebase, useInternalNames = false)
        }
        api.clean()
    }
}
