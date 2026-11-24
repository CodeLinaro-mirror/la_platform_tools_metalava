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

/**
 * Returns `true` if [raw] starts with [quote] and ends with a separate unescaped [quote].
 *
 * Counts consecutive backslashes immediately preceding the final character so an unclosed literal
 * ending with an escaped quote (such as `"abc\"` or `'a\'`) is recognized as unclosed.
 */
fun isClosedQuotedLiteral(raw: String, quote: Char = '"'): Boolean {
    if (raw.length < 2 || raw.first() != quote || raw.last() != quote) {
        return false
    }
    var backslashes = 0
    var i = raw.length - 2
    while (i >= 1 && raw[i] == '\\') {
        backslashes++
        i--
    }
    return backslashes % 2 == 0
}

/**
 * Unescapes Java escape sequences (`\\`, `\t`, `\b`, `\r`, `\n`, `\'`, `\"`, `\uXXXX`) in [str].
 *
 * If an invalid or incomplete escape sequence is encountered, calls [onError] with an error message
 * and returns `null` (when [onError] returns normally instead of throwing).
 */
inline fun unescapeJavaString(
    str: String,
    onError: (String) -> Unit,
): String? {
    if ('\\' !in str) {
        return str
    }
    val sb = StringBuilder(str.length)
    var i = 0
    while (i < str.length) {
        val c = str[i++]
        if (c != '\\') {
            sb.append(c)
            continue
        }
        if (i >= str.length) {
            onError("unfinished escape sequence: $str")
            return null
        }
        when (val esc = str[i++]) {
            '\\' -> sb.append('\\')
            't' -> sb.append('\t')
            'b' -> sb.append('\b')
            'r' -> sb.append('\r')
            'n' -> sb.append('\n')
            '\'' -> sb.append('\'')
            '"' -> sb.append('"')
            'u' -> {
                if (i + 4 > str.length) {
                    onError(
                        "invalid unicode escape sequence '\\u${str.substring(i)}' in string literal"
                    )
                    return null
                }
                // Decode 4 hexadecimal digits into a UTF-16 code unit.
                var code = 0
                for (j in 0 until 4) {
                    val hex = str[i + j]
                    val digit =
                        when (hex) {
                            in '0'..'9' -> hex - '0'
                            in 'a'..'f' -> 10 + (hex - 'a')
                            in 'A'..'F' -> 10 + (hex - 'A')
                            else -> -1
                        }
                    if (digit < 0) {
                        onError(
                            "invalid unicode escape sequence '\\u${str.substring(i, i + 4)}' in string literal"
                        )
                        return null
                    }
                    code = (code shl 4) or digit
                }
                i += 4
                sb.append(code.toChar())
            }
            else -> {
                onError("invalid escape sequence '\\$esc' in string literal")
                return null
            }
        }
    }
    return sb.toString()
}

/**
 * Extracts and unescapes the value of a double-quoted string literal [raw].
 *
 * If [raw] is unclosed (for example `"abc` or `"abc\"`) or contains an invalid escape sequence,
 * calls [onError] with an error message and returns `null` (when [onError] returns normally instead
 * of throwing).
 */
inline fun parseStringLiteralValue(
    raw: String,
    onError: (String) -> Unit,
): String? {
    if (!isClosedQuotedLiteral(raw, '"')) {
        onError("unclosed string literal")
        return null
    }
    return unescapeJavaString(raw.substring(1, raw.length - 1), onError)
}
