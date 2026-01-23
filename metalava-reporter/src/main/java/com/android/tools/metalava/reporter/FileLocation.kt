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

package com.android.tools.metalava.reporter

import java.io.File
import java.nio.file.Path
import java.util.Objects

/**
 * Identifies a specific line within an input file.
 *
 * The file location is optional as it is not always available. An unavailable source location is
 * indicated by a null [path]. Even when the [path] is available the [line] may be unknown, which is
 * indicated by a non-positive value. Even when [line] is available then [characterPosition] may be
 * unknown.
 */
abstract class FileLocation {
    /** The absolute path to the location, or `null` if it could not be found. */
    abstract val path: Path?

    /**
     * The 1-base line number.
     *
     * If this is non-positive then it indicates that it could not be found or was not provided.
     */
    abstract val line: Int

    /**
     * The 1-based character position from the start of the line.
     *
     * If this is non-positive then it indicates that it could not be found or was not provided.
     */
    open val characterPosition: Int
        get() = -1

    /** The optional [BaselineKey] for the [path]. */
    open val baselineKey: BaselineKey?
        get() = path?.let { BaselineKey.forPath(it) }

    /** Append the string representation of this to the [builder]. */
    fun appendTo(builder: StringBuilder) {
        builder.append(path)
        if (line > 0) builder.append(":").append(line)
    }

    /**
     * Return a [FileLocation] for the line in the [path] that is [lineOffset] from this line and
     * [charOffset] from the beginning of that line.
     *
     * This will only adjust valid values in [FileLocation]. e.g. if [line] is less than 1 then it
     * is invalid so [lineOffset] and [charOffset] will be ignored (as character position without a
     * line does not make sense). Similarly, if [characterPosition] is less than 1 then it is
     * invalid so [charOffset] will be ignored.
     *
     * If [lineOffset] and [charOffset] are both either ignored or set to '0' then they will have no
     * effect so `this` [FileLocation] is returned to avoid creating any unnecessary
     * [FileLocation]s.
     */
    fun adjustForLineAndCharOffset(lineOffset: Int, charOffset: Int): FileLocation {
        return if (line < 1) {
            // No line numbers so just reuse this as the offsets cannot be applied.
            this
        } else {
            if (characterPosition < 1) {
                // No character position so ignore charOffset as it cannot be applied.
                if (lineOffset == 0) {
                    // Line is unchanged so reuse this.
                    this
                } else {
                    // Just correct the line.
                    FixedFileLocation(path, line + lineOffset)
                }
            } else {
                // Character position and line are both included in this location.
                if (lineOffset == 0 && charOffset == 0) {
                    // Line and char position will be unchanged by applying their offsets so just
                    // reuse this.
                    this
                } else {
                    val correctedLine = line + lineOffset
                    val correctedChar =
                        if (lineOffset == 0) {
                            // The [characterPosition] records the indentation of the first
                            // character on the first line and [charOffset] is the offset from that
                            // first character so the [characterPosition] needs incrementing by
                            // [charOffset].
                            characterPosition + charOffset
                        } else {
                            // The [characterPosition] has no effect on other lines so [charOffset]
                            // is the offset from the first character in the line so is incremented
                            // by `1`.
                            charOffset + 1
                        }
                    FixedFileLocation(path, correctedLine, correctedChar)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileLocation) return false
        return other.path == this.path &&
            other.line == this.line &&
            other.characterPosition == this.characterPosition
    }

    override fun hashCode(): Int {
        return Objects.hash(path, line, characterPosition)
    }

    override fun toString() =
        when {
            line < 1 -> path.toString()
            characterPosition < 1 -> "$path:$line"
            else -> "$path:$line:$characterPosition"
        }

    /** A fixed location, known at construction time. */
    private data class FixedFileLocation(
        override val path: Path?,
        override val line: Int = 0,
        override val characterPosition: Int = 0,
    ) : FileLocation() {
        // Delegate to the super class as otherwise this would be replaced with a version generated
        // for the data class.
        override fun toString() = super.toString()
    }

    companion object {
        /** The unknown location. */
        val UNKNOWN: FileLocation = FixedFileLocation(null, 0)

        /**
         * Create a [FileLocation] for a [path] and optional [line] number and [characterPosition].
         */
        fun createLocation(path: Path, line: Int = 0, characterPosition: Int = 0): FileLocation =
            FixedFileLocation(path, line, characterPosition)

        fun forFile(file: File?): FileLocation {
            file ?: return UNKNOWN
            return createLocation(file.toPath(), 0)
        }
    }
}
