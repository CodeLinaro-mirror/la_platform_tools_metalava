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

import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.DocCommentPredicate

/**
 * A [JavadocContentVisitor] that will search [JavadocContent] for any text that satisfies
 * [predicate].
 */
internal open class TextContainsAnyVisitor(private val predicate: (String) -> Boolean) :
    DocCommentPredicate {
    /**
     * Checks to see whether [predicate] returns `true` for any text in [content].
     *
     * @return `true` if it does, `false` otherwise.
     */
    fun contains(content: JavadocContent) = content.accept(this)

    override fun visit(list: JavadocContentList) = list.contents.any { it.accept(this) }

    override fun visit(inlineTag: JavadocInlineTag) = inlineTag.contentMatches()

    override fun visit(text: JavadocText) = predicate(text.contents)

    override fun visit(blockTagSection: BlockTagSection) = blockTagSection.contentMatches()

    /** Check to see if the content matches [predicate]. */
    private fun DocTag.contentMatches() =
        // First, use the predicate to check the content, if any.
        content?.accept(this@TextContainsAnyVisitor) == true ||
            // Then, use this visitor to check the tag data, if any.
            tagData?.textMatches(predicate) == true
}
