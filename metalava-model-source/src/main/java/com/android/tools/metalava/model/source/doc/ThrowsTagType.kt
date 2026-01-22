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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.reporter.LocationSpecificReporter

/** [TagType] for `@throws` block tag. */
internal class ThrowsTagType() : TagType<ThrowsTagData>("throws", TagTypeForm.BLOCK) {
    override fun extractData(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        text: CharSequence
    ): ExtractDataResult<ThrowsTagData>? {
        val throwsName = text.findLeadingIdentifier() ?: return null

        // Resolve the class name to a fully qualified class reference or type parameter. If it
        // could not be found then fake one.
        val throwableReference =
            resolveThrowableType(context, reporter, throwsName) ?: ClassReference(throwsName)

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

    /**
     * Resolve [typeName] (which may be a reference to a class or a type parameter) to a
     * [TypeReference], if possible.
     */
    private fun resolveThrowableType(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        typeName: String
    ): TypeReference? {
        val resolved = context.resolveItemReference(typeName, NameClassification.TYPE)
        // TODO(b/447588621): Ensure that the resolved type is a Throwable.
        return when (resolved) {
            is ClassItem -> resolved.toResolvedReference()
            is TypeParameterItem -> resolved.toResolvedReference()
            is InvalidReferencableItem -> {
                resolved.reportIssue(reporter)
                null
            }
            // This should never happen as passing in NameClassification.TYPE above should limit the
            // returned types to ClassItem, TypeParameterItem or InvalidReferencableItem
            else -> error("type '$typeName' was resolved to an unknown type $resolved")
        }
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
        writer.print(throwableType.fullyQualifiedForm)

        // Print the remaining content. Always preceded by a space as any leading whitespace has
        // been trimmed from it.
        content?.printWithLeadingSpaceTo(contentPrinter)
    }

    override fun textMatches(predicate: (String) -> Boolean) =
        predicate(throwableType.fullyQualifiedForm)
}
