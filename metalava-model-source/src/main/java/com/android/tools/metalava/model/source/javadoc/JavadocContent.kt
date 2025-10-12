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

    companion object {
        val EMPTY: JavadocContent = EmptyJavadocContent()
    }
}

/** An empty [JavadocContent]. */
private class EmptyJavadocContent : JavadocContent {
    /** Empty content does not occupy multiple lines. */
    override fun isMultiLine() = false

    /** Empty content does not start with a newline. */
    override fun startsWithNewline() = false

    override fun accept(visitor: JavadocContentVisitor) {
        // Do nothing.
    }
}

/** Visitor of [JavadocContent] subclasses. */
internal interface JavadocContentVisitor {
    fun visit(list: JavadocContentList) {}

    fun visit(inlineTag: JavadocInlineTag) {}

    fun visit(text: JavadocText) {}
}

/** A [JavadocContent] that encapsulates a number of other [JavadocContent] instances. */
internal class JavadocContentList(private val list: List<JavadocContent>) : JavadocContent {
    /** A list of [JavadocContent] occupies multiple lines if any of them occupy multiple lines. */
    override fun isMultiLine() = list.any { it.isMultiLine() }

    /** A list of [JavadocContent] starts with newline if the first item starts with newline. */
    override fun startsWithNewline() = list.first().startsWithNewline()

    /** Visit the contents of this in turn. */
    fun visitContents(visitor: JavadocContentVisitor) {
        list.forEach { content -> content.accept(visitor) }
    }

    override fun accept(visitor: JavadocContentVisitor) {
        visitor.visit(this)
    }
}
