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

import com.android.tools.metalava.model.source.doc.DocDescription

/**
 * A component of a Javadoc [DocDescription].
 *
 * Currently, just a placeholder but will be expanded in the future.
 */
internal sealed interface JavadocContent {
    /**
     * Checks to see whether the content will occupy multiple lines.
     *
     * @return `true` if it does, `false` otherwise.
     */
    fun isMultiLine(): Boolean

    /** Check to see whether this starts with a newline character. */
    fun startsWithNewline(): Boolean

    /**
     * Call type specific method in [JavadocContentVisitor] corresponding to the implement of this.
     */
    fun accept(visitor: JavadocContentVisitor)
}

/** Visitor of [JavadocContent] subclasses. */
internal interface JavadocContentVisitor {
    fun visit(list: JavadocContentList) {}

    fun visit(inlineTag: JavadocInlineTag) {}

    fun visit(text: JavadocText) {}
}

/** A [JavadocContent] that encapsulates a number of other [JavadocContent] instances. */
internal class JavadocContentList(val contents: List<JavadocContent>) : JavadocContent {
    /** A list of [JavadocContent] occupies multiple lines if any of them occupy multiple lines. */
    override fun isMultiLine() = contents.any { it.isMultiLine() }

    /** A list of [JavadocContent] starts with newline if the first item starts with newline. */
    override fun startsWithNewline() = contents.first().startsWithNewline()

    /** Visit the contents of this in turn. */
    fun visitContents(visitor: JavadocContentVisitor) {
        contents.forEach { content -> content.accept(visitor) }
    }

    override fun accept(visitor: JavadocContentVisitor) {
        visitor.visit(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocContentList

        return contents == other.contents
    }

    override fun hashCode() = contents.hashCode()

    override fun toString() = buildString {
        append("JavadocContentList(")
        contents.joinTo(this)
        append(")")
    }
}
