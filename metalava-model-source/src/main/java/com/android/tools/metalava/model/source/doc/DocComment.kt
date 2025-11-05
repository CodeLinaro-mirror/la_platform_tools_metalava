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
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocContentPredicate
import com.android.tools.metalava.model.source.javadoc.TextContainsAnyVisitor
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.collections.plus

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

    /**
     * Prepare a [BlockTagSection] for adding, if it has any content added.
     *
     * Appending content to the returned [DocContentOwner] will cause a [BlockTagSection] for
     * [tagTypeName] with the appended content to be added to this [DocComment].
     */
    fun pendingBlockTagSection(
        tagTypeName: String,
        description: JavadocContent? = null
    ): DocContentOwner

    /** Removes any [BlockTagSection] for which [predicate] returns `true`. */
    fun removeBlockTagSections(predicate: (BlockTagSection) -> Boolean)

    /**
     * Check if [predicate] matches this documentation, checks [description] and all the
     * [blockTagSections] descriptions.
     */
    fun check(predicate: JavadocContentPredicate): Boolean

    /**
     * Check to see if this requires a source comment.
     *
     * This returns `true` if it would need to be written as a comment if this was generated in the
     * sources. That can either be because it was created from a comment in the original sources, or
     * it has been mutated since creation.
     */
    fun requiresSourceComment(): Boolean

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
    blockTagSections: List<BlockTagSection>,
    noComment: Boolean,
) :
    DescriptionOwner(
        context,
        descriptionSupplier,
        noComment,
    ),
    DocComment {
    /** Allow [blockTagSections] to be modified but only within this class. */
    override var blockTagSections = blockTagSections
        private set

    override fun hasBlockTagOfType(tagTypeName: String) =
        blockTagSections.any { it.tagType.name == tagTypeName }

    override fun addBlockTagSection(tagTypeName: String, description: JavadocContent?) {
        val tagType = BlockTagTypes.tagTypeOf(tagTypeName)
        val blockTagSection =
            DefaultBlockTagSection(
                context,
                tagType,
                description.toSupplier(),
            )

        addBlockTagSection(blockTagSection)
    }

    /** Add [blockTagSection] to [blockTagSections] invoking the [DocCommentMutationListener]. */
    internal fun addBlockTagSection(blockTagSection: BlockTagSection) {
        blockTagSections = blockTagSections + blockTagSection

        // If this call added the first block tag section, then append`{@inheritDoc}` if necessary.
        // If it was appended then return as it will already have notified the listener that this
        // has changed.
        // TODO(b/454257440): Investigate whether adding `{@inheritDoc}` to the main description of
        //  a comment in this case is necessary.
        if (blockTagSections.size == 1 && appendInheritDocIfNeeded()) {
            return
        }

        // Notify any listener.
        context.mutationListener.docCommentMutated()
    }

    override fun pendingBlockTagSection(
        tagTypeName: String,
        description: JavadocContent?
    ): DocContentOwner {
        val tagType = BlockTagTypes.tagTypeOf(tagTypeName)
        return PendingBlockTagSection(this, context, tagType, description.toSupplier())
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

    override fun check(predicate: JavadocContentPredicate) =
        description?.check(predicate) == true ||
            blockTagSections.any { it.description?.check(predicate) == true }

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

/**
 * A pending [BlockTagSection].
 *
 * Implements mutators in [DocContentOwner] to create and add a [blockTagSection] to [docComment]
 * and then delegates those mutators to [blockTagSection].
 */
internal class PendingBlockTagSection(
    private val docComment: DefaultDocComment,
    private val context: DocCommentContext,
    private val tagType: TagType<*>,
    private val description: ContentSupplier,
) : DocContentOwner {
    /**
     * Backing field for [blockTagSection].
     *
     * Lazily initialized by [blockTagSection] getter.
     */
    private var _blockTagSection: BlockTagSection? = null

    /**
     * The [BlockTagSection] that was added to [docComment].
     *
     * On first access this will create a [BlockTagSection] and add it to [docComment].
     */
    private val blockTagSection
        get() =
            _blockTagSection
                ?: run {
                    val new = DefaultBlockTagSection(context, tagType, description)
                    _blockTagSection = new
                    docComment.addBlockTagSection(new)
                    new
                }

    /**
     * Delegate to [_blockTagSection].
     *
     * Accessing this does not create [blockTagSection].
     */
    override val docContent: DocContent?
        get() = _blockTagSection?.description

    override fun append(other: DocContent) {
        blockTagSection.append(other)
    }

    override fun append(text: String) {
        blockTagSection.append(text)
    }
}
