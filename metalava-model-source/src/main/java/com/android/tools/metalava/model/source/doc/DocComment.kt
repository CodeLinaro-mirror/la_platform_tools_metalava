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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocContentRewriter
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.TextContainsAnyVisitor
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Optional
import kotlin.collections.plus

/**
 * A Javadoc or KDoc comment associated with an API element.
 *
 * Implementations of these are immutable.
 */
internal interface DocComment {
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

    /** Return a copy of this with [other] appended to the main [description]. */
    fun append(other: DocContent): DocComment

    /** Return a copy of this with [text] appended to the main [description]. */
    fun append(text: String): DocComment

    /**
     * Return a copy of this with a [BlockTagSection] of [tagTypeName] with [description] added to
     * the list.
     */
    fun addBlockTagSection(tagTypeName: String, description: JavadocContent?): DocComment

    /**
     * Return a copy of this with [other] appended to the description of the first [BlockTagSection]
     * of [tagTypeName], creating and adding one if none exists.
     */
    fun appendBlockTagDescription(tagTypeName: String, other: DocContent): DocComment

    /**
     * Return a copy of this with [text] appended to the description of the first [BlockTagSection]
     * of [tagTypeName], creating and adding one if none exists.
     */
    fun appendBlockTagDescription(tagTypeName: String, text: String): DocComment

    /**
     * Return a copy of this with [other] appended to the description of the `@param`
     * [BlockTagSection] for [name], creating and adding one if none exists.
     */
    fun appendParamTagDescription(name: String, other: DocContent): DocComment

    /**
     * Return a copy of this with [text] appended to the description of the `@param`
     * [BlockTagSection] for [name], creating and adding one if none exists.
     */
    fun appendParamTagDescription(name: String, text: String): DocComment

    /**
     * Return a copy of this with any [BlockTagSection] for which [predicate] returns `true`
     * removed, or `this` if no [BlockTagSection] matched [predicate].
     */
    fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean): DocComment

    /**
     * Check if [predicate] matches this documentation, checks [description] and all the
     * [blockTagSections] descriptions.
     */
    fun check(predicate: DocCommentPredicate): Boolean

    /**
     * Check to see if this requires a source comment.
     *
     * This returns `true` if it would need to be written as a comment if this was generated in the
     * sources. That can either be because it was created from a comment in the original sources, or
     * it has been mutated since creation.
     */
    fun requiresSourceComment(): Boolean

    /**
     * Print this as a Javadoc comment to [writer].
     *
     * If [mainDescriptionRewriter] is provided then it is applied to the [description] after fully
     * qualifying but before printing.
     */
    fun printAsJavadocComment(
        writer: PrintWriter,
        mainDescriptionRewriter: JavadocContentRewriter? = null
    )

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

private enum class RequiredSpace {
    EMPTY,
    SINGLE_LINE,
    MULTI_LINE,
    ;

    operator fun plus(other: RequiredSpace): RequiredSpace {
        return entries[(ordinal + other.ordinal).coerceAtMost(MULTI_LINE.ordinal)]
    }
}

/**
 * Checks to see whether the content will occupy multiple lines.
 *
 * @return `true` if it does, `false` otherwise.
 */
private fun JavadocContent.isMultiLine() = matches(MULTI_LINE_CHECKER)

/** Visitor that will search the content to see if it contains any newline characters. */
internal val MULTI_LINE_CHECKER = TextContainsAnyVisitor { string -> string.contains('\n') }

/** Determines how much vertical space this [JavadocContent] requires when printed. */
private fun JavadocContent?.requiredSpace(): RequiredSpace =
    when {
        this == null -> RequiredSpace.EMPTY
        isMultiLine() -> RequiredSpace.MULTI_LINE
        else -> RequiredSpace.SINGLE_LINE
    }

internal class DefaultDocComment(
    context: DocCommentContext,
    descriptionSupplier: ContentSupplier,
    override val blockTagSections: List<BlockTagSection>,
    noComment: Boolean,
    initializedDescription: Optional<JavadocContent>? = null,
) :
    DescriptionOwner<DefaultDocComment>(
        context,
        descriptionSupplier,
        noComment,
        initializedDescription = initializedDescription,
    ),
    DocComment {

    override fun withDescription(newDescription: JavadocContent?) =
        DefaultDocComment(
            context = context,
            descriptionSupplier = newDescription.toSupplier(),
            blockTagSections = blockTagSections,
            noComment = noComment,
            initializedDescription = Optional.ofNullable(newDescription),
        )

    /** Return a copy of this with [blockTagSections] set to [newBlockTagSections]. */
    private fun withBlockTagSections(newBlockTagSections: List<BlockTagSection>) =
        DefaultDocComment(
            context = context,
            descriptionSupplier = descriptionSupplier,
            blockTagSections = newBlockTagSections,
            noComment = noComment,
            initializedDescription = initializedDescription,
        )

    override fun hasBlockTagOfType(tagTypeName: String) =
        blockTagSections.any { it.tagType.name == tagTypeName }

    /** Get the block [TagType] for [tagTypeName]. */
    private fun blockTagTypeFor(tagTypeName: String) =
        TagTypes.tagTypeOf(tagTypeName).apply {
            if (!this.form.supportsBlockTag) {
                error("Cannot use '$tagTypeName' as a block tag")
            }
        }

    override fun addBlockTagSection(tagTypeName: String, description: JavadocContent?): DocComment {
        val tagType = blockTagTypeFor(tagTypeName)
        val blockTagSection =
            DefaultBlockTagSection(
                context,
                tagType,
                description.toSupplier(),
            )

        return addBlockTagSection(blockTagSection)
    }

    /** Return a copy of this with [blockTagSection] added to [blockTagSections]. */
    internal fun addBlockTagSection(blockTagSection: BlockTagSection): DefaultDocComment {
        val updated = withBlockTagSections(blockTagSections + blockTagSection)

        // If this call added the first block tag section, then append `{@inheritDoc}` if necessary.
        // TODO(b/454257440): Investigate whether adding `{@inheritDoc}` to the main description of
        //  a comment in this case is necessary.
        return if (updated.blockTagSections.size == 1) {
            updated.appendInheritDocIfNeeded()
        } else {
            updated
        }
    }

    /**
     * Find the first [BlockTagSection] matching [predicate], or create and add a new one of
     * [tagTypeName] with [initialDescription] if none exists, and apply [updater] to it.
     */
    private inline fun updateOrAddBlockTagSection(
        tagTypeName: String,
        initialDescription: JavadocContent? = null,
        predicate: (BlockTagSection) -> Boolean,
        updater: (BlockTagSection) -> BlockTagSection,
    ): DocComment {
        val index = blockTagSections.indexOfFirst(predicate)
        return if (index != -1) {
            val existing = blockTagSections[index]
            val updated = updater(existing)
            if (updated === existing) {
                this
            } else {
                val newSections =
                    blockTagSections.toMutableList().apply { this[index] = updated }.toList()
                withBlockTagSections(newSections)
            }
        } else {
            val tagType = blockTagTypeFor(tagTypeName)
            val new = DefaultBlockTagSection(context, tagType, initialDescription.toSupplier())
            val updated = updater(new)
            addBlockTagSection(updated)
        }
    }

    override fun appendBlockTagDescription(tagTypeName: String, other: DocContent): DocComment =
        updateOrAddBlockTagSection(
            tagTypeName = tagTypeName,
            predicate = { it.tagType.name == tagTypeName },
            updater = { it.append(other) },
        )

    override fun appendBlockTagDescription(tagTypeName: String, text: String): DocComment =
        updateOrAddBlockTagSection(
            tagTypeName = tagTypeName,
            predicate = { it.tagType.name == tagTypeName },
            updater = { it.append(text) },
        )

    override fun appendParamTagDescription(name: String, other: DocContent): DocComment =
        updateOrAddBlockTagSection(
            tagTypeName = "param",
            initialDescription = JavadocText(name),
            predicate = { it.typeSafeTagData(TagTypes.PARAM)?.name == name },
            updater = { it.append(other) },
        )

    override fun appendParamTagDescription(name: String, text: String): DocComment =
        updateOrAddBlockTagSection(
            tagTypeName = "param",
            initialDescription = JavadocText(name),
            predicate = { it.typeSafeTagData(TagTypes.PARAM)?.name == name },
            updater = { it.append(text) },
        )

    override fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean): DocComment {
        val filtered = blockTagSections.filter { !predicate(it) }
        return if (filtered.size != blockTagSections.size) {
            // Something was removed.
            withBlockTagSections(filtered)
        } else {
            this
        }
    }

    override fun check(predicate: DocCommentPredicate) =
        description?.check(predicate) == true || blockTagSections.any { predicate.visit(it) }

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

    /**
     * Requires a source comment if there was a source comment, there is a non-null main
     * description, at least one block tag.
     */
    override fun requiresSourceComment() =
        !noComment || description != null || blockTagSections.isNotEmpty()

    override fun printAsJavadocComment(
        writer: PrintWriter,
        mainDescriptionRewriter: JavadocContentRewriter?
    ) {
        val mainDescription = mainDescriptionRewriter?.rewrite(description) ?: description

        // Compute require space for the main description and block tag sections.
        val mainDescriptionRequiredSpace = mainDescription.requiredSpace()
        val blockTagSectionRequiredSpace = requiredSpaceForBlockTagSections()
        val overallRequiredSpace = mainDescriptionRequiredSpace + blockTagSectionRequiredSpace

        // If there is no content in the comment and there was no comment in the source then do not
        // print a comment at all.
        if (overallRequiredSpace == RequiredSpace.EMPTY && noComment) return

        // Create a printer for [JavadocContent].
        val contentPrinter = JavadocContentPrinter(writer, context)

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
            contentPrinter.print(mainDescription)
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
                section.printTagContents(contentPrinter)
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
