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

package com.android.tools.metalava.model.source.doc

@Suppress("KotlinConstantConditions") // Needed to suppress unnecessary warning for this == '_'
private fun Char.isWordChar() = isLetterOrDigit() || this == '_'

/**
 * Check to see if `this` [String] contains [word] as a separate word.
 *
 * It is a word if the following conditions are both `true`:
 * * it is either at the start of `this` or is immediately preceded by a non-word character.
 * * it is either at the end of `this` or is immediately followed by a non-word character.
 *
 * Scans `this` for every occurrence of [word] stopping as soon as it finds one for which the above
 * two conditions hold (in which case it returns `true`), or until it has checked all of them (in
 * which case it returns `false`).
 *
 * This attempts to emulate the behavior of `Regex("""\b\Q$word\E\b""")`, while avoiding the
 * overhead that comes with it.
 */
fun String.containsWord(word: String): Boolean {
    val wordLength = word.length
    var start = 0
    while (true) {
        val index = indexOf(word, start)
        if (index < 0) return false
        val end = index + wordLength
        if (index == 0 || !this[index - 1].isWordChar()) {
            if (end == length || !this[end].isWordChar()) {
                return true
            }
        }
        start = end
    }
}

/**
 * Collapse consecutive runs of whitespace characters (as matched by [Char.isWhitespace]) into a
 * single `' '` space, returning `this` unchanged if no multi-character or non-space whitespace run
 * is present.
 *
 * This emulates `replace(Regex("""\s+"""), " ")` while avoiding regular expression overhead.
 */
fun String.collapseSpaces(): String {
    var needsCollapse = false
    var prevWasSpace = false
    for (i in indices) {
        val c = this[i]
        if (c.isWhitespace()) {
            if (c != ' ' || prevWasSpace) {
                needsCollapse = true
                break
            }
            prevWasSpace = true
        } else {
            prevWasSpace = false
        }
    }
    if (!needsCollapse) return this

    return buildString(length) {
        var inWhitespace = false
        for (i in this@collapseSpaces.indices) {
            val c = this@collapseSpaces[i]
            if (c.isWhitespace()) {
                if (!inWhitespace) {
                    append(' ')
                    inWhitespace = true
                }
            } else {
                append(c)
                inWhitespace = false
            }
        }
    }
}
