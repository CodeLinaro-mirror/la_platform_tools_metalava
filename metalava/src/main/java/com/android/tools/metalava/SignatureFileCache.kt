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

import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.text.SignatureFile

private data class CacheKey(
    val signatureFiles: List<SignatureFile>,
    val classPathResolver: ClassPathResolver?
)

/** Loads signature files, caching them for reuse where appropriate. */
class SignatureFileCache(private val signatureFileLoader: SignatureFileLoader) :
    SignatureFileLoader {
    private val map = mutableMapOf<CacheKey, Codebase>()

    override fun load(
        signatureFiles: List<SignatureFile>,
        classPathResolver: ClassPathResolver?,
    ): Codebase {
        val key = CacheKey(signatureFiles, classPathResolver)
        return map.computeIfAbsent(key) { k ->
            signatureFileLoader.load(k.signatureFiles, k.classPathResolver).apply {
                // Freeze the classes before caching to avoid any changes being made to cached and
                // potentially shared objects.
                freezeClasses()
            }
        }
    }

    override fun loadMultiplatform(signatureFiles: List<SignatureFile>): MultiplatformCodebase {
        error("SignatureFileCache not supported for MultiplatformCodebase")
    }
}
