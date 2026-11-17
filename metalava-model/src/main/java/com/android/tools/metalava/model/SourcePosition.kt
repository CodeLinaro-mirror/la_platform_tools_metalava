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

package com.android.tools.metalava.model

/**
 * A 0-based character offset within a [SourceFile] or signature file, or [UNKNOWN] (`-1`) if no
 * character offset is available.
 *
 * Because this is a non-nullable `@JvmInline value class` wrapping [Int], the Kotlin compiler
 * erases it to a primitive `int` in JVM bytecode with zero boxing overhead.
 */
@JvmInline
value class SourcePosition(val offset: Int) {
    /** True if this represents a valid non-negative character offset. */
    val isKnown: Boolean
        get() = offset >= 0

    companion object {
        /** Sentinel value (`-1`) used when no character offset is available. */
        val UNKNOWN = SourcePosition(-1)
    }
}
