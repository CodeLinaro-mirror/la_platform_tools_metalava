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

import com.android.tools.metalava.model.source.doc.BlockTagTypes
import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.source.doc.ExtractDataResult
import com.android.tools.metalava.model.source.doc.InlineTagTypes
import com.android.tools.metalava.model.source.doc.JavadocContentPrinter
import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType
import com.android.tools.metalava.model.source.doc.TagTypeIssueReporter
import com.android.tools.metalava.model.source.doc.skipForwardsOverLeadingWhitespace
import com.android.tools.metalava.reporter.Issues

internal object TestTagTypes {
    val BAR_TAG_TYPE =
        BarTagType().also {
            // Register for use as block and inline tags.
            BlockTagTypes.register(it)
            InlineTagTypes.register(it)
        }
}

internal class BarTagType : TagType<BarTagData>("bar") {
    override fun extractData(
        context: DocCommentContext,
        reporter: TagTypeIssueReporter,
        text: CharSequence
    ): ExtractDataResult<BarTagData>? {
        val identifierStart = text.skipForwardsOverLeadingWhitespace(0)
        val identifier = text.findLeadingIdentifier(identifierStart) ?: return null

        if (identifier.contains('e') || identifier.contains('o')) {
            reporter.report(
                Issues.INVALID_JAVADOC,
                "@bar tag cannot contain 'e' or 'o' in the identifier"
            )
        }

        return ExtractDataResult(
            tagData = BarTagData(identifier),
            // The identifier and any following whitespace must be removed from the content as they
            // are part of [BarTagData].
            consumedContent =
                text.skipForwardsOverLeadingWhitespace(identifierStart + identifier.length),
        )
    }
}

internal data class BarTagData(val identifier: String) : TagData {
    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        writer.print(" ")
        writer.print(identifier)

        // Print the remaining content. Always preceded by a space as any leading whitespace has
        // been trimmed from it.
        content?.printWithLeadingSpaceTo(contentPrinter)
    }
}
