/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Encapsulates an object from which a [FileLocation] can be obtained for a [SourcePosition].
 *
 * This is a non-nullable `@JvmInline value class` wrapping [Any?] so that it has zero boxing
 * overhead at runtime and occupies a single reference slot on [DefaultItem].
 *
 * Valid underlying [value] types are:
 * - `null` ([NONE])
 * - [FileLocation] (via [fromFileLocation])
 * - [SourceFile] (via [fromSourceFile])
 * - [PathLineMap] (via [fromLineMap])
 */
@JvmInline
value class FileLocationSource private constructor(private val value: Any?) {
    /** Compute the [FileLocation] for [sourcePosition], or return `null` if this is [NONE]. */
    fun fileLocation(sourcePosition: SourcePosition): FileLocation? =
        when (val v = value) {
            null -> null
            is FileLocation -> v
            is SourceFile -> v.fileLocation(sourcePosition)
            is PathLineMap -> v.fileLocation(sourcePosition)
            else -> error("Unexpected FileLocationSource value: $v")
        }

    companion object {
        /** No [FileLocationSource]; [DefaultItem] will fall back to [DefaultItem.sourceFile]. */
        val NONE = FileLocationSource(null)

        /** Create a [FileLocationSource] wrapping an existing [FileLocation]. */
        fun fromFileLocation(fileLocation: FileLocation): FileLocationSource =
            FileLocationSource(fileLocation)

        /** Create a [FileLocationSource] wrapping an optional [SourceFile]. */
        fun fromSourceFile(sourceFile: SourceFile?): FileLocationSource =
            if (sourceFile == null) NONE else FileLocationSource(sourceFile)

        /** Create a [FileLocationSource] wrapping a [Path] and [LineMap]. */
        fun fromLineMap(path: Path, lineMap: LineMap): FileLocationSource =
            FileLocationSource(PathLineMap(path, lineMap))
    }
}

/**
 * Pairs a file [path] and a [lineMap] so that all items in a signature file can share a single
 * instance via [FileLocationSource.fromLineMap].
 */
private class PathLineMap(
    private val path: Path,
    private val lineMap: LineMap,
) {
    private val unknownLineLocation by
        lazy(LazyThreadSafetyMode.NONE) { FileLocation.createLocation(path) }

    fun fileLocation(sourcePosition: SourcePosition): FileLocation =
        if (sourcePosition.isKnown) {
            lineMap.fileLocation(path, sourcePosition.offset)
        } else {
            unknownLineLocation
        }
}
