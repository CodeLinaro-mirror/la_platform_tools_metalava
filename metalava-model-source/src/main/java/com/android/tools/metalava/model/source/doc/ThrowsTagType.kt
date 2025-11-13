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

/** [TagType] for `@throws` block tag. */
internal class ThrowsTagType() : TagType<ThrowsTagData>("throws") {
    override fun extractData(
        context: DocCommentContext,
        text: CharSequence
    ): ExtractDataResult<ThrowsTagData>? {
        val throwsName = text.findLeadingIdentifier() ?: return null

        // Resolve the class name to a fully qualified class reference or type parameter. If it
        // could not be found then fake one.
        val throwableReference =
            context.resolveThrowableType(throwsName) ?: ClassReference(throwsName)

        return ExtractDataResult(
            tagData =
                ThrowsTagData(
                    throwableReference,
                ),
            // The throwable name and any following whitespace must be removed from the content as
            // they are part of [ThrowsTagData].
            consumedContent = text.skipForwardsOverLeadingWhitespace(throwsName.length),
        )
    }
}

/** Tag specific data for the `@throws` block tag. */
internal data class ThrowsTagData(
    /** The reference to the throwable type, could be a class or a type parameter. */
    val throwableType: TypeReference,
) : TagData {
    override fun compareTo(other: TagData): Int {
        other as ThrowsTagData
        return compareValues(throwableType, other.throwableType)
    }

    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        writer.print(" ")
        writer.print(throwableType.displayName)

        // Print the remaining content. Always preceded by a space as any leading whitespace has
        // been trimmed from it.
        content?.printWithLeadingSpaceTo(contentPrinter)
    }

    override fun textMatches(predicate: (String) -> Boolean) = predicate(throwableType.displayName)
}
