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

package com.android.tools.metalava.model.source.javadoc

/**
 * A block of text in a Javadoc comment.
 *
 * @param text a non-empty string.
 */
internal class JavadocText(val contents: String) : JavadocContent {
    init {
        require(contents.isNotEmpty()) { "contents must contain at least one character" }
    }

    override fun <R> accept(visitor: JavadocContentVisitor<R>) = visitor.visit(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocText

        return contents == other.contents
    }

    override fun hashCode() = contents.hashCode()

    override fun toString() =
        "JavadocText(\"${contents.replace("\n", "\\n").replace("\"", "\\\"")}\")"
}
