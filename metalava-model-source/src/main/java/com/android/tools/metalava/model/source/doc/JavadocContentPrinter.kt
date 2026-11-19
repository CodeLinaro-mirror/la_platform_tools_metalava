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

import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocContentList
import com.android.tools.metalava.model.source.javadoc.JavadocContentVisitor
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.TextStartsWithVisitor
import java.io.PrintWriter
import kotlin.text.iterator

/** Prints [JavadocContent] instances to [writer]. */
internal class JavadocContentPrinter(
    /** The [PrintWriter] to which the content is written. */
    internal val writer: PrintWriter,

    /** Provides context about where the documentation will be printed. */
    private val context: DocCommentContext,
) : JavadocContentVisitor<Unit> {
    /**
     * Prints [content] as part of a Javadoc comment to [writer].
     *
     * @param content the content to print, if `null` this returns immediately.
     * @param addLeadingSpaceIfNeeded determines whether a leading space should be written before
     *   the content is written. This has no effect if [content] is `null` or if it starts with a
     *   newline. Otherwise, this will cause a space to be printed before the content.
     */
    fun print(content: JavadocContent?, addLeadingSpaceIfNeeded: Boolean = false) {
        content ?: return

        if (addLeadingSpaceIfNeeded) {
            if (!content.matches(STARTS_WITH_NEWLINE_CHECKER)) {
                writer.print(" ")
            }
        }

        content.accept(this)
    }

    override fun visit(list: JavadocContentList) {
        list.visitContents(this)
    }

    override fun visit(inlineTag: JavadocInlineTag) {
        writer.print("{@")
        writer.print(inlineTag.tagType)
        inlineTag.printTagContents(this)
        writer.print("}")
    }

    override fun visit(text: JavadocText) {
        var previousChar = '\u0000'
        for (c in text.contents) {
            if (previousChar == '\n' && c != '/') {
                writer.print(" *")
            }
            writer.print(c)
            previousChar = c
        }

        if (previousChar == '\n') {
            writer.print(" *")
        }
    }

    val containingClassName
        get() = context.containingClassItem?.qualifiedName()

    companion object {
        /** Check to see whether [JavadocContent] starts with a newline character. */
        private val STARTS_WITH_NEWLINE_CHECKER = TextStartsWithVisitor { string ->
            string[0] == '\n'
        }
    }
}
