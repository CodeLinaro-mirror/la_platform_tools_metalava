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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterListOwner
import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.BlockTagTypes
import com.android.tools.metalava.model.source.doc.DocComment
import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.source.doc.DocCommentMutationListener
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.model.source.javadoc.JavadocContentPredicate
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.toOptionalJavadocContent
import com.android.tools.metalava.reporter.Issues
import java.io.PrintWriter

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
abstract class AbstractItemDocumentation(
    protected val item: SelectableItem,
) :
    ItemDocumentation,
    DocumentationIssueReporter,
    DocCommentContext,
    // Implement this as a temporary measure while this needs to keep [text] and [docComment] in
    // sync.
    DocCommentMutationListener {

    /**
     * Lazily initialized backing property for [text].
     *
     * If this is `null` then it requires setting. If [_docComment] is `null` then it will be set by
     * calling [initializeTextBackingField]. Otherwise, it will be set by printing [_docComment].
     */
    protected var _text: String? = null

    /**
     * Called when [_text] requires initializing. Implementations must set [_text] to a non-null
     * value.
     */
    protected abstract fun initializeTextBackingField()

    /**
     * Ensures that [text]'s backing field has been initialized even if it is currently `null`.
     *
     * The `text` field has been initialized if [_text] is non-null or if [_docComment] is non-null.
     * As the [_docComment] is only created from [_text] if it is non-null then [_text] must have
     * been non-null in the past.
     */
    protected fun ensureTextBackingFieldHasBeenInitialized() {
        if (_text == null && _docComment == null) {
            initializeTextBackingField()
        }
    }

    /**
     * The mutable text contents of the documentation.
     *
     * It uses [_text] as its backing field and setting this will invoke [textChanged].
     *
     * If [_docComment] is null then this needs initializing from the model, otherwise this is set
     * from [_docComment].
     */
    override var text: String
        get() {
            if (_text == null) {
                val docComment = _docComment
                if (docComment == null) {
                    // Initialize from the underlying model.
                    initializeTextBackingField()
                } else {
                    // Initialize from the _docComment so that it reflects any changes in it.
                    _text = docComment.asJavadocCommentString()
                }
            }
            return _text!!
        }
        set(value) {
            _text = value
            textChanged()
        }

    /**
     * Called when [text] changes to discard [_docComment] so it will be regenerated the next time
     * [docComment] is accessed.
     *
     * This ensures that [text] and [_docComment] do not get out of sync. It is needed because
     * currently both [text] and [docComment] are modified directly. Longer term, changes will be
     * applied directly to [_docComment] and [text] will be dropped.
     */
    private fun textChanged() {
        _docComment = null
    }

    /** Lazily initialized from [text]. Is cleared by [textChanged] if [text] is modified. */
    private var _docComment: DocComment? = null

    private val docComment: DocComment
        get() {
            val docComment = _docComment
            return if (docComment == null) {
                val new =
                    DocComment.createDocComment(
                        context = this,
                        text,
                        reporter = this,
                    )
                _docComment = new
                new
            } else {
                docComment
            }
        }

    override val mutationListener: DocCommentMutationListener
        get() = this

    /**
     * Called when [docComment] is mutated to discard [_text] so it will be regenerated from
     * [_docComment] the next time [text] is accessed.
     *
     * This ensures that [text] and [_docComment] do not get out of sync. It is needed because
     * currently both [text] and [docComment] are modified directly. Longer term, changes will be
     * applied directly to [_docComment] and [text] will be dropped.
     */
    override fun docCommentMutated() {
        _text = null
    }

    override val isHidden
        get() = hasBlockTagOfType("hide")

    /**
     * Return the ordinal for the first item that matches [predicate].
     *
     * If no item matches then return the length of the list, as if the unknown item was at the end.
     */
    inline fun <T> List<T>.ordinalInListUnknownAtEnd(predicate: (T) -> Boolean): Int {
        val index = indexOfFirst(predicate)
        return if (index == -1) size else index
    }

    /** Implements [DocCommentContext.ordinalInParamsList]. */
    override fun ordinalInParamsList(name: String): Int {
        return if (item is TypeParameterListOwner) {
            val typeParameterList = item.typeParameterList
            val typeParameterCount = typeParameterList.size

            if (name.startsWith("<") && name.endsWith(">")) {
                val typeParameterName = name.substring(1, name.length - 1)
                // Type parameters are always at the start of the `@param` list so just return the
                // ordinal in the type parameter list with unknown at the end.
                typeParameterList.ordinalInListUnknownAtEnd { it.name() == typeParameterName }
            } else {
                // Get the callable parameters list, if any.
                val parametersList = (item as? CallableItem)?.parameters() ?: emptyList()

                // Get the ordinal of the parameter in the callable parameters list.
                val ordinalInParametersList =
                    parametersList.ordinalInListUnknownAtEnd { it.name() == name }

                // Callable parameters always start after type parameters, both known and unknown
                // so offset their ordinal so they come after the
                val parameterListStart = typeParameterCount + 1
                parameterListStart + ordinalInParametersList
            }
        } else {
            // Only TypeParameterListOwners have parameters or either type.
            0
        }
    }

    /** Implements [DocCommentContext.isOverridingMethod]. */
    override fun isOverridingMethod() =
        // Purposely does not cache this as superMethods() is already cached.
        item is MethodItem && item.superMethods().isNotEmpty()

    /** Implements [DocCommentContext.fullyQualifyComment]. */
    override fun fullyQualifyComment(comment: String) = fullyQualifiedDocumentation(comment)

    override val isDocOnly
        get() = hasBlockTagOfType("doconly")

    override val isRemoved
        get() = hasBlockTagOfType("removed")

    override fun hasBlockTagOfType(blockTagType: String) =
        docComment.hasBlockTagOfType(blockTagType)

    override fun print(writer: PrintWriter) {
        val originalText = text

        // Before printing fully qualify the comment. This expects a whole comment and will fix up
        // @link and @see tags.
        val fullyQualifiedText = fullyQualifiedDocumentation(text)

        // Only print the comment if it is not blank.
        if (fullyQualifiedText.isNotBlank()) {
            // If fully qualifying did not change the text then used the docComment, otherwise
            // create a new one from the fully qualified text.
            val fullyQualifiedComment =
                if (fullyQualifiedText == originalText) docComment
                else
                    DocComment.createDocComment(
                        context = this,
                        fullyQualifiedText,
                        reporter = this,
                    )

            // Print the docComment as Javadoc.
            fullyQualifiedComment.printAsJavadocComment(writer)
        }
    }

    override val mainDescription: DocContent?
        get() = docComment.description

    override val mainDescriptionOwner: DocContentOwner
        get() = docComment

    override fun blockTagDescription(tagTypeName: String, forAppending: Boolean): DocContent? =
        findBlockTagSection(tagTypeName)?.let { blockTagSection ->
            if (forAppending) blockTagSection.docContentForAppending else blockTagSection.docContent
        }

    override fun blockTagDescriptionOwner(tagTypeName: String): DocContentOwner {
        return findBlockTagSection(tagTypeName)
            ?: docComment.pendingBlockTagSection(
                tagTypeName,
            )
    }

    /** Find the block tag section for [tagTypeName]. */
    private fun findBlockTagSection(tagTypeName: String): BlockTagSection? =
        docComment.blockTagSections.find { it.tagType.name == tagTypeName }

    override fun paramTagDescription(name: String): DocContent? =
        findParamTagSection(name)?.docContent

    override fun paramTagDescriptionOwner(name: String): DocContentOwner {
        return findParamTagSection(name)
            ?: docComment.pendingBlockTagSection(
                "param",
                // Pass the parameter name through the description.
                description = JavadocText(name),
            )
    }

    /** Find the block tag section for `@param` of [name]. */
    private fun findParamTagSection(name: String): BlockTagSection? =
        docComment.blockTagSections.find { it.typeSafeTagData(BlockTagTypes.PARAM)?.name == name }

    override fun check(predicate: DocContentPredicate) =
        docComment.check(predicate as JavadocContentPredicate)

    /** Check to see if this requires a source comment. */
    override fun requiresSourceComment() = docComment.requiresSourceComment()

    override fun workAroundJavaDocSummaryTruncationIssue() {
        // Work around javadoc cutting off the summary line after the first ". ".
        val firstDot = text.indexOf(".")
        if (firstDot > 0 && text.regionMatches(firstDot - 1, "e.g. ", 0, 5, false)) {
            text = text.substring(0, firstDot) + ".g.&nbsp;" + text.substring(firstDot + 4)
        }
    }

    override fun removeDeprecatedSection() {
        // Try and remove all the `@deprecated` sections.
        docComment.removeBlockTagSections { it.tagType == BlockTagTypes.DEPRECATED }
    }

    override fun addUniqueBlockTagSectionWithSimpleText(tagTypeName: String, text: String) {
        // Remove any existing sections of the specified type.
        docComment.removeBlockTagSections { it.tagType.name == tagTypeName }

        // Add a block tag section to the end.
        docComment.addBlockTagSection(tagTypeName, text.toOptionalJavadocContent())
    }

    override fun report(issue: Issues.Issue, message: String, lineOffset: Int, charOffset: Int) {
        val location = fileLocation.adjustForLineAndCharOffset(lineOffset, charOffset)
        item.codebase.reporter.report(issue, null, message, location)
    }
}
