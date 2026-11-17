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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.item.DefaultCodebase
import com.google.turbine.diag.SourceFile
import com.google.turbine.tree.Tree.CompUnit

/**
 * Creates [TurbineSourceFile]s on demand for a [SourceFile] and caches the result for reuse.
 *
 * @param codebase the [DefaultCodebase] of which any created [TurbineSourceFile]s are part.
 * @param units the [CompUnit]s from which the [TurbineSourceFile]s will be created.
 */
internal class TurbineSourceFileCache(
    private val codebase: DefaultCodebase,
    units: List<CompUnit>,
) {
    /** Map from [SourceFile.path] to [CompUnit]. */
    private val pathToCompUnit = units.associateBy { it.source().path() }

    /** Map from file path to the [TurbineSourceFile]. */
    private val turbineSourceFiles = mutableMapOf<String, TurbineSourceFile>()

    /**
     * Get the [TurbineSourceFile] for a [SourceFile].
     *
     * If none exists then find the [CompUnit] for [sourceFile] by [SourceFile.path], failing if it
     * could not be found. Then create a [TurbineSourceFile] from that, cache it for future use and
     * return it.
     */
    internal fun turbineSourceFile(sourceFile: SourceFile): TurbineSourceFile =
        turbineSourceFiles.computeIfAbsent(sourceFile.path()) { path ->
            val unit = pathToCompUnit[path] ?: error("cannot find CompUnit for $path")
            TurbineSourceFile(codebase, unit)
        }
}
