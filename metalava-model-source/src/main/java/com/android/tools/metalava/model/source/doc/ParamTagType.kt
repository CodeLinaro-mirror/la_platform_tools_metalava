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

/** [TagType] for `@param` block tag. */
internal class ParamTagType(name: String) : TagType<ParamTagData>(name) {
    override fun extractData(
        context: DocCommentContext,
        reporter: TagTypeIssueReporter,
        text: CharSequence
    ): ExtractDataResult<ParamTagData>? {
        val paramName = text.findLeadingIdentifier() ?: return null

        val ordinal = context.ordinalInParamsList(paramName)
        return ExtractDataResult(
            tagData = ParamTagData(paramName, ordinal),
            // The parameter name and any following whitespace must be removed from the content as
            // they are part of [ParamTagData].
            consumedContent = text.skipForwardsOverLeadingWhitespace(paramName.length),
        )
    }
}

/** Tag specific data for the `@param` block tag. */
internal data class ParamTagData(
    /** The name of the parameter. */
    val name: String,
    /**
     * The ordinal that defines the order of this in the list of all type parameters and callable
     * parameters.
     */
    val ordinal: Int,
) : TagData {
    override fun compareTo(other: TagData): Int {
        other as ParamTagData
        (ordinal - other.ordinal).let { diff -> if (diff != 0) return diff }
        return name.compareTo(other.name)
    }

    /**
     * Print the parameter [name] which was removed from the content by [ParamTagType.extractData]
     * followed by [content], if any.
     */
    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        writer.print(" ")
        writer.print(name)

        // Print the remaining content. Always preceded by a space as any leading whitespace has
        // been trimmed from it.
        content?.printWithLeadingSpaceTo(contentPrinter)
    }
}
