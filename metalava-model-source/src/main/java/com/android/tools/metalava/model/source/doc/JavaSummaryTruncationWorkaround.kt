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
import com.android.tools.metalava.model.source.javadoc.JavadocContentRewriter
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
import com.android.tools.metalava.model.source.javadoc.JavadocText
import kotlin.text.indexOf
import kotlin.text.regionMatches
import kotlin.text.substring

/**
 * Work around javadoc cutting off the summary line after "e.g. " by replacing the ` ` with
 * `&nbsp;`.
 */
internal class JavaSummaryTruncationWorkaround : JavadocContentRewriter {

    /** Rewrite [content] to apply the workaround, if needed. */
    override fun rewrite(content: JavadocContent?) =
        // Apply this to the content, if it returns `null` then it means that the content is
        // unchanged so return the original content.
        content?.accept(this) ?: content

    override fun visit(list: JavadocContentList): JavadocContent? {
        val contents = list.contents
        for ((index, content) in contents.withIndex()) {
            // Visit the content. If it returns `null` then it means that the workaround was not
            // needed so return immediately.
            val rewritten = content.accept(this) ?: return null

            // If the content was rewritten then the workaround was applied so create a new
            // [JavadocContentList] with the rewritten content in place of the original content and
            // then return.
            if (rewritten !== content) {
                val newContents = contents.toMutableList()
                newContents[index] = rewritten
                return JavadocContentList(newContents)
            }
        }

        // No changes were made and no `.` was found at all so return the original unchanged.
        return list
    }

    override fun visit(text: JavadocText): JavadocContent? {
        var contents = text.contents

        val firstDot = contents.indexOf(".")
        return when {
            firstDot == -1 ->
                // No `.` was found so return [text] to indicate that it should continue looking.
                text
            firstDot > 0 && contents.regionMatches(firstDot - 1, "e.g. ", 0, 5, false) -> {
                // A '.' was found which did require fixing up so fix it and return the modified
                // [JavadocText] to indicate the that the work was done and a change was made.
                JavadocText(
                    contents.substring(0, firstDot) + ".g.&nbsp;" + contents.substring(firstDot + 4)
                )
            }
            else -> {
                // A `.` was found but did not require fixing up so return null to indicate that the
                // work is done but not change was needed.
                null
            }
        }
    }

    override fun visit(inlineTag: JavadocInlineTag) =
        // No changes were made and no `.` was found at all so return the original unchanged.
        inlineTag
}
