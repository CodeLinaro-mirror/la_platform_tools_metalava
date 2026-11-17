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

package com.android.tools.metalava.model.parser

import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Maps 0-based character indices in a source text back to line numbers and character positions.
 *
 * Implemented by [CharSequenceLineMap] for raw text/signature files, and adaptable to compiler
 * provider line maps such as Turbine's `com.google.turbine.diag.LineMap` or PSI's `Document`.
 */
interface LineMap {
    /** Return the 1-based line number for the 0-based [charIndex]. */
    fun lineNumber(charIndex: Int): Int

    /** Return the 1-based character position within the line for the 0-based [charIndex]. */
    fun characterPosition(charIndex: Int): Int

    /** Return the 0-based line offset (`lineNumber(charIndex) - 1`) for [charIndex]. */
    fun lineOffset(charIndex: Int): Int = lineNumber(charIndex) - 1

    /** Return the 0-based character offset (`characterPosition(charIndex) - 1`) for [charIndex]. */
    fun characterOffset(charIndex: Int): Int = characterPosition(charIndex) - 1

    /**
     * Create a [FileLocation] for [path] at [charIndex] that resolves its [FileLocation.line] (and
     * optional [FileLocation.characterPosition]) on demand using this [LineMap].
     */
    fun fileLocation(
        path: Path,
        charIndex: Int,
        includeCharacterPosition: Boolean = false,
    ): FileLocation = LineMapFileLocation(path, this, charIndex, includeCharacterPosition)

    companion object {
        /** Create a [LineMap] for [text] that lazily scans for line breaks on first lookup. */
        fun create(text: CharSequence): LineMap = CharSequenceLineMap(text)
    }
}

/** A [LineMap] backed by a lazily computed sorted [IntArray] of line start offsets in [text]. */
private class CharSequenceLineMap(
    private val text: CharSequence,
) : LineMap {
    /** Backing field of [lineStartOffsets]. */
    private lateinit var _lineStartOffsets: IntArray

    /** Sorted [IntArray] of line start offsets in [text], initialized lazily on first access. */
    private val lineStartOffsets: IntArray
        get() {
            if (!::_lineStartOffsets.isInitialized) {
                _lineStartOffsets = buildLineStartOffsets(text)
            }
            return _lineStartOffsets
        }

    private fun lineIndex(charIndex: Int): Int {
        require(charIndex in 0..text.length) {
            "charIndex ($charIndex) out of range [0, ${text.length}]"
        }
        val searchResult = lineStartOffsets.binarySearch(charIndex)
        return if (searchResult >= 0) searchResult else -searchResult - 2
    }

    override fun lineNumber(charIndex: Int): Int = lineIndex(charIndex) + 1

    override fun characterPosition(charIndex: Int): Int {
        val idx = lineIndex(charIndex)
        return (charIndex - lineStartOffsets[idx]) + 1
    }

    companion object {
        /**
         * Scans [text] for line breaks (`\r\n`, `\n`, or `\r`) and returns an array containing the
         * start index of each line.
         */
        private fun buildLineStartOffsets(text: CharSequence): IntArray {
            val endExclusive = text.length
            var capacity = 64
            var offsets = IntArray(capacity)
            var size = 0

            fun add(offset: Int) {
                if (size == capacity) {
                    capacity *= 2
                    offsets = offsets.copyOf(capacity)
                }
                offsets[size++] = offset
            }

            add(0)
            var index = 0
            while (index < endExclusive) {
                val c = text[index]
                if (c == '\r') {
                    index =
                        if (index + 1 < endExclusive && text[index + 1] == '\n') {
                            index + 2
                        } else {
                            index + 1
                        }
                    add(index)
                } else if (c == '\n') {
                    index++
                    add(index)
                } else {
                    index++
                }
            }
            return offsets.copyOf(size)
        }
    }
}

/**
 * A [FileLocation] backed by a [LineMap] and a character [charIndex] that defers resolving [line]
 * and [characterPosition] until they are queried.
 *
 * During signature file parsing, a [FileLocation] is created for every single parsed `Item` (every
 * package, class, constructor, method, field, property, and parameter—roughly 100,000 instances for
 * a full Android framework signature file). However, [line] is only accessed when an issue is
 * actually reported on that item, meaning >99.9% (and often 100%) of created [FileLocation]
 * instances never have their [line] retrieved.
 *
 * Deferring the lookup until [line] or [characterPosition] is accessed avoids:
 * 1. Triggering lazy initialization of [CharSequenceLineMap.lineStartOffsets] (scanning the entire
 *    file for line breaks) for files where no issues are reported.
 * 2. Performing ~100,000 unnecessary binary searches per file during parsing.
 */
private class LineMapFileLocation(
    override val path: Path,
    private val lineMap: LineMap,
    private val charIndex: Int,
    private val includeCharacterPosition: Boolean,
) : FileLocation() {
    override val line: Int
        get() = lineMap.lineNumber(charIndex)

    override val characterPosition: Int
        get() = if (includeCharacterPosition) lineMap.characterPosition(charIndex) else -1
}
