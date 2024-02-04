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

package com.android.tools.metalava.model.source

import java.io.File

/**
 * An abstraction of source files and root directories.
 *
 * Those are always paired together or computed from one another.
 *
 * @param sources the list of source files
 * @param sourcePath a possibly empty list of root directories within which source files may be
 *   found.
 */
class SourceSet(val sources: List<File>, val sourcePath: List<File>) {

    val absoluteSources: List<File>
        get() {
            return sources.map { it.absoluteFile }
        }

    val absoluteSourcePaths: List<File>
        get() {
            return sourcePath.filter { it.path.isNotBlank() }.map { it.absoluteFile }
        }

    companion object {
        fun empty(): SourceSet = SourceSet(emptyList(), emptyList())
    }
}
