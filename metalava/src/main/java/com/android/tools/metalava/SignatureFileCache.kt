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

package com.android.tools.metalava

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.text.TextCodebase
import java.io.File

/** Loads signature files, caching them for reuse where appropriate. */
@Suppress("DEPRECATION")
class SignatureFileCache {
    private val map = mutableMapOf<File, TextCodebase>()

    fun load(
        file: File,
        classResolver: ClassResolver? = null,
        annotationManager: AnnotationManager = options.annotationManager,
    ): TextCodebase {
        return map[file]
            ?: run {
                val loaded = SignatureFileLoader.load(file, classResolver, annotationManager)
                map[file] = loaded
                loaded
            }
    }
}
