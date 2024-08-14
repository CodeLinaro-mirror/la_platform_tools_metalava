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

package com.android.tools.metalava.model.source.utils

import org.intellij.lang.annotations.Language

/** Converts from package.html content to a package-info.java javadoc string. */
@Language("JAVA")
fun packageHtmlToJavadoc(@Language("HTML") packageHtml: String?): String {
    packageHtml ?: return ""
    if (packageHtml.isBlank()) {
        return ""
    }

    val body = getBodyContents(packageHtml).trim()
    if (body.isBlank()) {
        return ""
    }
    // Combine into comment lines prefixed by asterisk, ,and make sure we don't
    // have end-comment markers in the HTML that will escape out of the javadoc comment
    val comment = body.lines().joinToString(separator = "\n") { " * $it" }.replace("*/", "&#42;/")
    @Suppress("DanglingJavadoc") return "/**\n$comment\n */\n"
}

/**
 * Returns the body content from the given HTML document. Attempts to tokenize the HTML properly
 * such that it doesn't get confused by comments or text that looks like tags.
 */
@Suppress("LocalVariableName")
private fun getBodyContents(html: String): String {
    val length = html.length
    val STATE_TEXT = 1
    val STATE_SLASH = 2
    val STATE_ATTRIBUTE_NAME = 3
    val STATE_IN_TAG = 4
    val STATE_BEFORE_ATTRIBUTE = 5
    val STATE_ATTRIBUTE_BEFORE_EQUALS = 6
    val STATE_ATTRIBUTE_AFTER_EQUALS = 7
    val STATE_ATTRIBUTE_VALUE_NONE = 8
    val STATE_ATTRIBUTE_VALUE_SINGLE = 9
    val STATE_ATTRIBUTE_VALUE_DOUBLE = 10
    val STATE_CLOSE_TAG = 11
    val STATE_ENDING_TAG = 12

    var bodyStart = -1
    var htmlStart = -1

    var state = STATE_TEXT
    var offset = 0
    var tagStart = -1
    var tagEndStart = -1
    var prev = -1
    loop@ while (offset < length) {
        if (offset == prev) {
            // Purely here to prevent potential bugs in the state machine from looping
            // infinitely
            offset++
            if (offset == length) {
                break
            }
        }
        prev = offset

        val c = html[offset]
        when (state) {
            STATE_TEXT -> {
                if (c == '<') {
                    state = STATE_SLASH
                    offset++
                    continue@loop
                }

                // Other text is just ignored
                offset++
            }
            STATE_SLASH -> {
                if (c == '!') {
                    if (html.startsWith("!--", offset)) {
                        // Comment
                        val end = html.indexOf("-->", offset + 3)
                        if (end == -1) {
                            offset = length
                        } else {
                            offset = end + 3
                            state = STATE_TEXT
                        }
                        continue@loop
                    } else if (html.startsWith("![CDATA[", offset)) {
                        val end = html.indexOf("]]>", offset + 8)
                        if (end == -1) {
                            offset = length
                        } else {
                            state = STATE_TEXT
                            offset = end + 3
                        }
                        continue@loop
                    } else {
                        val end = html.indexOf('>', offset + 2)
                        if (end == -1) {
                            offset = length
                            state = STATE_TEXT
                        } else {
                            offset = end + 1
                            state = STATE_TEXT
                        }
                        continue@loop
                    }
                } else if (c == '/') {
                    state = STATE_CLOSE_TAG
                    offset++
                    tagEndStart = offset
                    continue@loop
                } else if (c == '?') {
                    // XML Prologue
                    val end = html.indexOf('>', offset + 2)
                    if (end == -1) {
                        offset = length
                        state = STATE_TEXT
                    } else {
                        offset = end + 1
                        state = STATE_TEXT
                    }
                    continue@loop
                }
                state = STATE_IN_TAG
                tagStart = offset
            }
            STATE_CLOSE_TAG -> {
                if (c == '>') {
                    state = STATE_TEXT
                    if (html.startsWith("body", tagEndStart, true)) {
                        val bodyEnd = tagEndStart - 2 // </
                        if (bodyStart != -1) {
                            return html.substring(bodyStart, bodyEnd)
                        }
                    }
                    if (html.startsWith("html", tagEndStart, true)) {
                        val htmlEnd = tagEndStart - 2
                        if (htmlEnd != -1) {
                            return html.substring(htmlStart, htmlEnd)
                        }
                    }
                }
                offset++
            }
            STATE_IN_TAG -> {
                val whitespace = Character.isWhitespace(c)
                if (whitespace || c == '>') {
                    if (html.startsWith("body", tagStart, true)) {
                        bodyStart = html.indexOf('>', offset) + 1
                    }
                    if (html.startsWith("html", tagStart, true)) {
                        htmlStart = html.indexOf('>', offset) + 1
                    }
                }

                when {
                    whitespace -> state = STATE_BEFORE_ATTRIBUTE
                    c == '>' -> {
                        state = STATE_TEXT
                    }
                    c == '/' -> state = STATE_ENDING_TAG
                }
                offset++
            }
            STATE_ENDING_TAG -> {
                if (c == '>') {
                    if (html.startsWith("body", tagEndStart, true)) {
                        val bodyEnd = tagEndStart - 1
                        if (bodyStart != -1) {
                            return html.substring(bodyStart, bodyEnd)
                        }
                    }
                    if (html.startsWith("html", tagEndStart, true)) {
                        val htmlEnd = tagEndStart - 1
                        if (htmlEnd != -1) {
                            return html.substring(htmlStart, htmlEnd)
                        }
                    }
                    offset++
                    state = STATE_TEXT
                }
            }
            STATE_BEFORE_ATTRIBUTE -> {
                if (c == '>') {
                    state = STATE_TEXT
                } else if (c == '/') {
                    // we expect an '>' next to close the tag
                } else if (!Character.isWhitespace(c)) {
                    state = STATE_ATTRIBUTE_NAME
                }
                offset++
            }
            STATE_ATTRIBUTE_NAME -> {
                when {
                    c == '>' -> state = STATE_TEXT
                    c == '=' -> state = STATE_ATTRIBUTE_AFTER_EQUALS
                    Character.isWhitespace(c) -> state = STATE_ATTRIBUTE_BEFORE_EQUALS
                    c == ':' -> {}
                }
                offset++
            }
            STATE_ATTRIBUTE_BEFORE_EQUALS -> {
                if (c == '=') {
                    state = STATE_ATTRIBUTE_AFTER_EQUALS
                } else if (c == '>') {
                    state = STATE_TEXT
                } else if (!Character.isWhitespace(c)) {
                    // Attribute value not specified (used for some boolean attributes)
                    state = STATE_ATTRIBUTE_NAME
                }
                offset++
            }
            STATE_ATTRIBUTE_AFTER_EQUALS -> {
                if (c == '\'') {
                    // a='b'
                    state = STATE_ATTRIBUTE_VALUE_SINGLE
                } else if (c == '"') {
                    // a="b"
                    state = STATE_ATTRIBUTE_VALUE_DOUBLE
                } else if (!Character.isWhitespace(c)) {
                    // a=b
                    state = STATE_ATTRIBUTE_VALUE_NONE
                }
                offset++
            }
            STATE_ATTRIBUTE_VALUE_SINGLE -> {
                if (c == '\'') {
                    state = STATE_BEFORE_ATTRIBUTE
                }
                offset++
            }
            STATE_ATTRIBUTE_VALUE_DOUBLE -> {
                if (c == '"') {
                    state = STATE_BEFORE_ATTRIBUTE
                }
                offset++
            }
            STATE_ATTRIBUTE_VALUE_NONE -> {
                if (c == '>') {
                    state = STATE_TEXT
                } else if (Character.isWhitespace(c)) {
                    state = STATE_BEFORE_ATTRIBUTE
                }
                offset++
            }
            else -> assert(false) { state }
        }
    }

    return html
}
