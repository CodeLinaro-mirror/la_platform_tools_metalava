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

import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.requiredSpace
import java.io.PrintWriter
import java.io.StringWriter

/**
 * A Javadoc or KDoc comment associated with an API element.
 *
 * Implementations of these are mutable.
 */
internal interface DocComment : DocContentOwner {
    /** The main description, i.e. the part before any block tags. */
    val description: JavadocContent?

    /**
     * The block tag sections, i.e. the parts that start `@<block-tag-type> ...`.
     *
     * There can be more than one block tag section of some types, e.g. `@param`, `@see`.
     */
    val blockTagSections: List<BlockTagSection>

    /** Check to see whether there are any block tags of type [tagTypeName]. */
    fun hasBlockTagOfType(tagTypeName: String): Boolean

    /** Add a [BlockTagSection] of [tagTypeName] with [description] to the list. */
    fun addBlockTagSection(tagTypeName: String, description: JavadocContent?)

    /** Removes any [BlockTagSection] for which [predicate] returns `true`. */
    fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean)

    /** Print this as a Javadoc comment to [writer]. */
    fun printAsJavadocComment(writer: PrintWriter)

    /** Get the output of [printAsJavadocComment] as a [String]. */
    fun asJavadocCommentString(): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printWriter -> printAsJavadocComment(printWriter) }
        return writer.toString()
    }

    companion object {
        /**
         * Create a [DocComment] from [text], with [context], reporting any issues to [reporter].
         */
        internal fun createDocComment(
            context: DocCommentContext,
            text: String,
            reporter: DocumentationIssueReporter,
        ): DocComment {
            return DocCommentParser.parseText(context, text, reporter)
        }
    }
}

enum class RequiredSpace {
    EMPTY,
    SINGLE_LINE,
    MULTI_LINE,
    ;

    operator fun plus(other: RequiredSpace): RequiredSpace {
        return entries[(ordinal + other.ordinal).coerceAtMost(MULTI_LINE.ordinal)]
    }
}

/**
 * Interface that must be implemented by classes that need to respond to changes in a [DocComment].
 */
interface DocCommentMutationListener {
    /** Invoked when [DocComment] is mutated. */
    fun docCommentMutated()
}

internal class DefaultDocComment(
    context: DocCommentContext,
    descriptionSupplier: ContentSupplier,
    override var blockTagSections: List<BlockTagSection>,
) : DescriptionOwner(context, descriptionSupplier), DocComment {

    override fun hasBlockTagOfType(tagTypeName: String) =
        blockTagSections.any { it.tagType.name == tagTypeName }

    override fun addBlockTagSection(tagTypeName: String, description: JavadocContent?) {
        val tagType = BlockTagTypes.tagTypeOf(tagTypeName)
        val blockTagSection =
            DefaultBlockTagSection(
                context,
                tagType,
                DefaultContentSupplier(description),
            )
        blockTagSections = blockTagSections + blockTagSection

        // Notify any listener.
        context.mutationListener.docCommentMutated()
    }

    override fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean) {
        val filtered = blockTagSections.filter { !predicate(it) }
        if (filtered.size != blockTagSections.size) {
            // Something was removed.
            blockTagSections = filtered

            // Notify any listener.
            context.mutationListener.docCommentMutated()
        }
    }

    /** Get the [RequiredSpace] for the block tag sections. */
    private fun requiredSpaceForBlockTagSections(): RequiredSpace =
        when (blockTagSections.size) {
            // If the block tag section is empty then the required space is empty.
            0 -> RequiredSpace.EMPTY
            // If the block tag section has a single tag then the block tag section requires at
            // least a single line (even if the description is empty) but is otherwise determined by
            // the space required for the description.
            1 ->
                blockTagSections
                    .single()
                    .description
                    .requiredSpace()
                    .coerceAtLeast(RequiredSpace.SINGLE_LINE)
            // If the block tag section has multiple tags then it requires multiple lines.
            else -> RequiredSpace.MULTI_LINE
        }

    override fun printAsJavadocComment(writer: PrintWriter) {
        // Compute require space for the main description and block tag sections.
        val mainDescriptionRequiredSpace = description.requiredSpace()
        val blockTagSectionRequiredSpace = requiredSpaceForBlockTagSections()
        val overallRequiredSpace = mainDescriptionRequiredSpace + blockTagSectionRequiredSpace

        // Create a printer for [JavadocContent].
        val contentPrinter = JavadocContentPrinter(writer)

        // Check to see whether this is multi-line comment. If is then output it on multiple lines,
        // e.g.
        // ```
        // /**
        //  * ...
        //  */
        // ```
        // if it is not then output it all on one line, e.g. `/** ... */`.
        val multiLine = overallRequiredSpace == RequiredSpace.MULTI_LINE

        // Start the doc comment.
        writer.print("/**")
        if (multiLine) {
            writer.println()
        }

        // Print the main description, if it is not empty.
        if (mainDescriptionRequiredSpace != RequiredSpace.EMPTY) {
            if (multiLine) {
                writer.print(" *")
            }
            // Add leading space as all leading whitespace was removed from description.
            writer.print(" ")
            contentPrinter.print(description)
            if (multiLine) {
                writer.println()
            }
        }

        // Print the tag sections if they are not empty.
        if (blockTagSections.isNotEmpty()) {
            // If the block tag section requires multiple lines and the main description was added
            // then add a blank line between the main description and the block tag section.
            if (
                blockTagSectionRequiredSpace == RequiredSpace.MULTI_LINE &&
                    mainDescriptionRequiredSpace != RequiredSpace.EMPTY
            ) {
                writer.println(" *")
            }
            for (section in blockTagSections.sortedWith(BlockTagSection.comparator)) {
                if (multiLine) {
                    writer.print(" *")
                }
                writer.print(" @${section.tagType}")
                section.tagData?.printAfterTagType(writer)
                section.description?.let { content ->
                    writer.print(" ")
                    contentPrinter.print(content)
                }
                if (multiLine) {
                    writer.println()
                }
            }
        }

        // End the doc comment.
        writer.println(" */")
    }

    override fun toString() = buildString {
        append("description: ")
        // Use descriptionSupplier's toString not description's as accessing the latter changes the
        // state of this which is not recommended in toString() methods that may be used for
        // debugging as that can change the behavior. It also requires lots of work and could result
        // in performance degradation while debugging which can also affect behavior.
        append(descriptionSupplier)
        for (section in blockTagSections) {
            append("\n")
            // Delegate to the BlockTagSection implementation's toString() for similar reasons to
            // above.
            append(section)
        }
    }
}
