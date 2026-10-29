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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.DocCommentVisitor

/**
 * A visitor that extracts only the plain text contents from Javadoc content, completely ignoring
 * inline tags (like `{@code ...}`, `{@link ...}`) and block tag data.
 */
internal class PlainTextExtractor : DocCommentVisitor<Unit> {

    private val builder = StringBuilder()

    fun getResult(): String = builder.toString()

    override fun visit(list: JavadocContentList) {
        list.visitContents(this)
    }

    override fun visit(inlineTag: JavadocInlineTag) {
        // Completely ignore inline tags
    }

    override fun visit(text: JavadocText) {
        builder.append(text.contents)
    }

    override fun visit(blockTagSection: BlockTagSection) {
        // Completely ignore block tag sections
    }
}

/**
 * Returns the Javadoc content as a single plain-text string, with all inline/block tags removed.
 * The returned string is trimmed.
 */
fun DocContent.asPlainText(): String {
    val javadocContent = this as? JavadocContent ?: return ""
    val extractor = PlainTextExtractor()
    javadocContent.accept(extractor)
    return extractor.getResult().trim()
}
