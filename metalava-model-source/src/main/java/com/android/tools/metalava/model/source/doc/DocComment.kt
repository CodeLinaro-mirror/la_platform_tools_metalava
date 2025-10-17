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

import java.io.PrintWriter
import java.io.StringWriter

/**
 * A Javadoc or KDoc comment associated with an API element.
 *
 * Implementations of these are mutable.
 */
interface DocComment {
    /** The main description, i.e. the part before any block tags. */
    val description: DocDescription

    /**
     * The block tag sections, i.e. the parts that start `@<block-tag-type> ...`.
     *
     * There can be more than one block tag section of some types, e.g. `@param`, `@see`.
     */
    val blockTagSections: List<BlockTagSection>

    /** Check to see whether there are any block tags of type [blockTagType]. */
    fun hasBlockTagOfType(blockTagType: String): Boolean

    /**
     * Removes any [BlockTagSection] for which [predicate] returns `true`.
     *
     * @return `true` if any [BlockTagSection]s were removed, `false` if it had no effect.
     */
    fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean): Boolean

    /** Print this as a Javadoc comment to [writer]. */
    fun printAsJavadocComment(writer: PrintWriter)

    /** Get the output of [printAsJavadocComment] as a [String]. */
    fun asJavadocCommentString(): String {
        val writer = StringWriter()
        PrintWriter(writer).use { printWriter -> printAsJavadocComment(printWriter) }
        return writer.toString()
    }

    companion object {
        /** Create a [DocComment] from [text], reporting any issues to [reporter]. */
        internal fun createDocComment(
            text: String,
            reporter: DocumentationIssueReporter
        ): DocComment {
            return DocCommentParser.parseText(text, reporter)
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

internal class DefaultDocComment(
    override val description: DocDescription,
    override var blockTagSections: List<BlockTagSection>
) : DocComment {
    override fun hasBlockTagOfType(blockTagType: String) =
        blockTagSections.any { it.tagType == blockTagType }

    override fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean): Boolean {
        val filtered = blockTagSections.filter { !predicate(it) }
        return if (filtered.size == blockTagSections.size) {
            // No changes.
            false
        } else {
            // Something was removed.
            blockTagSections = filtered
            true
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
            description.printAsJavadocComment(writer)
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
            for (section in blockTagSections) {
                if (multiLine) {
                    writer.print(" *")
                }
                writer.print(" @${section.tagType}")
                val sectionDescription = section.description
                if (sectionDescription.isNotEmpty()) {
                    writer.print(" ")
                    sectionDescription.printAsJavadocComment(writer)
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
        append(description)
        for (section in blockTagSections) {
            append("\n@")
            append(section.tagType)
            append(" ")
            append(section.description)
        }
    }
}
