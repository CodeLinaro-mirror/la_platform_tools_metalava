/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.TextCodebase
import java.io.File

@Suppress("DEPRECATION")
object SignatureFileLoader {
    private val map = mutableMapOf<File, TextCodebase>()

    /** Used by java file. */
    fun load(file: File): TextCodebase {
        return load(file, null)
    }

    fun load(
        file: File,
        classResolver: ClassResolver? = null,
        annotationManager: AnnotationManager = options.annotationManager,
    ): TextCodebase {
        return map[file]
            ?: run {
                val loaded = loadFiles(listOf(file), classResolver, annotationManager)
                map[file] = loaded
                loaded
            }
    }

    fun loadFiles(
        files: List<File>,
        classResolver: ClassResolver? = null,
        annotationManager: AnnotationManager = options.annotationManager,
    ): TextCodebase {
        require(files.isNotEmpty()) { "files must not be empty" }

        try {
            return ApiFile.parseApi(files, classResolver, annotationManager)
        } catch (ex: ApiParseException) {
            throw MetalavaCliException("Unable to parse signature file: ${ex.message}")
        }
    }
}
