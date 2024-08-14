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

package com.android.tools.metalava.model.testing.transformer

import com.android.tools.metalava.model.Codebase
import java.util.ServiceLoader

interface CodebaseTransformer {

    /** Transform the [codebase] into a different [Codebase] */
    fun transform(codebase: Codebase): Codebase

    companion object {

        /**
         * Find all the [CodebaseTransformer]s and apply as follows:
         * 1. If none are available return [codebase].
         * 2. If more than one are available fail.
         * 3. Load the available one and apply to [codebase] returning the result.
         */
        fun transformIfAvailable(codebase: Codebase): Codebase {
            // Try and load CodebaseTransformers.
            val loader = ServiceLoader.load(CodebaseTransformer::class.java)

            // If there are none then return the codebase unchanged.
            if (loader.none()) return codebase

            // Get the sole transformer.
            val transformer = loader.toList().single()

            // Apply it.
            return transformer.transform(codebase)
        }
    }
}
