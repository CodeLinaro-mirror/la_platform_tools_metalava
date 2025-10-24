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

import com.android.tools.metalava.model.source.javadoc.JavadocContentList
import com.android.tools.metalava.model.source.javadoc.JavadocContentVisitor
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
import com.android.tools.metalava.model.source.javadoc.JavadocText
import java.io.PrintWriter

/** Prints [DocDescription] instances to [writer]. */
internal class DocDescriptionPrinter(private val writer: PrintWriter) : JavadocContentVisitor {
    /** Prints [description] as part of a Javadoc comment to [writer]. */
    fun print(description: DocDescription) {
        description.content?.accept(this)
    }

    override fun visit(list: JavadocContentList) {
        list.visitContents(this)
    }

    override fun visit(inlineTag: JavadocInlineTag) {
        writer.print("{@")
        writer.print(inlineTag.tagType)
        inlineTag.content?.let { nestedContent ->
            if (!nestedContent.startsWithNewline()) {
                writer.print(" ")
            }
            nestedContent.accept(this)
        }
        writer.print("}")
    }

    override fun visit(text: JavadocText) {
        var previousChar = '\u0000'
        for (c in text.text) {
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
}
