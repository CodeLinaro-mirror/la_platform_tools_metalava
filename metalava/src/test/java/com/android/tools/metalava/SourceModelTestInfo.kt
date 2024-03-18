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

package com.android.tools.metalava

import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.source.SourceModelProvider

/**
 * Encapsulates information about the [SourceModelProvider] that is to be used for running a test.
 */
class SourceModelTestInfo(
    /**
     * The [SourceModelProvider] that will be used to generate a [Codebase] from source files, if
     * needed.
     */
    val sourceModelProvider: SourceModelProvider,

    /**
     * The [ModelOptions] that will be passed to the [sourceModelProvider] when creating a new
     * source parser.
     */
    val modelOptions: ModelOptions = ModelOptions.empty
) {
    /** Override this to return the string that will be used in the test name. */
    override fun toString(): String = buildString {
        append(sourceModelProvider.providerName)
        if (modelOptions != ModelOptions.empty) {
            append(",")
            append(modelOptions)
        }
    }
}
