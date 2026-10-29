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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeParameterListOwner
import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.DocComment
import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.source.doc.DocCommentPredicate
import com.android.tools.metalava.model.source.doc.DocTypeParser
import com.android.tools.metalava.model.source.doc.DocumentationIssueReporter
import com.android.tools.metalava.model.source.doc.JavaSummaryTruncationWorkaround
import com.android.tools.metalava.model.source.doc.TagTypes
import com.android.tools.metalava.model.source.javadoc.ExprContext
import com.android.tools.metalava.model.source.javadoc.InvalidBlockUseVisitor
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.toOptionalJavadocContent
import com.android.tools.metalava.reporter.Issues
import java.io.PrintWriter

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
internal abstract class AbstractItemDocumentation(
    protected val item: SelectableItem,
) : ItemDocumentation, DocumentationIssueReporter, DocCommentContext {

    /** The [DocComment] that contains the documentation content. */
    protected abstract val docComment: DocComment

    override fun resolveItemReference(
        sourceReference: String,
        nameClassification: NameClassification
    ): ReferencableItem {
        return item.resolveReferencableItem(sourceReference, nameClassification)
    }

    /** Implements [ExprContext.isFlagEnabled]. */
    override fun isFlagEnabled(flagName: String): Boolean {
        val apiFlags = item.codebase.config.apiFlags ?: return true
        return !apiFlags[flagName].action.revert
    }

    override val isHidden: Boolean
        get() {
            // When API surfaces are configured in a configuration file, the Javadoc `@hide` block
            // tag is ignored as a mechanism for hiding elements. Instead, explicit annotations
            // (e.g. `@android.annotation.Hide`) must be used.
            if (item.codebase.config.apiSurfacesConfigured) {
                return false
            }
            return hasBlockTagOfType("hide")
        }

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

    override val containingClassItem: ClassItem?
        get() =
            when (item) {
                is ClassItem -> item
                is MemberItem -> item.containingClass()
                else -> null
            }

    /** Implements [DocCommentContext.docTypeParser]. */
    override val docTypeParser: DocTypeParser
        get() = DocTypeParser.create(reporter = this, item)

    override val isRemoved: Boolean
        get() {
            // When API surfaces are configured in a configuration file, the Javadoc `@removed`
            // block tag is ignored as a mechanism for removing elements. Instead, explicit
            // annotations (e.g. `@android.annotation.RemovedFromApi`) must be used.
            if (item.codebase.config.apiSurfacesConfigured) {
                return false
            }
            return hasBlockTagOfType("removed")
        }

    override fun hasBlockTagOfType(blockTagType: String) =
        docComment.hasBlockTagOfType(blockTagType)

    override fun print(writer: PrintWriter) {
        // Remove all `@hide`, and `@doconly` tags before printing to prevent them from being
        // visible to the documentation generation tool that consumes the stubs. That is because the
        // tool may act upon them, e.g. hiding any APIs that are tagged with `@hide`.
        docComment.removeBlockTagSections {
            val type = it.tagType.name
            type == "hide" || type == "doconly"
        }

        checkDocumentationBeforePrinting()

        // Print the docComment as Javadoc.
        docComment.printAsJavadocComment(
            writer,
            // Apply the [JavaSummaryTruncationWorkaround] to the main description.
            mainDescriptionRewriter = JavaSummaryTruncationWorkaround.INSTANCE
        )
    }

    /**
     * Check the documentation content [docComment] before printing it.
     *
     * Verifies that it does not contain anything which could cause problems downstream, e.g. in
     * `doclava`.
     */
    private fun checkDocumentationBeforePrinting() {
        if (docComment.check(InvalidBlockUseVisitor.INSTANCE)) {
            item.codebase.reporter.report(
                Issues.INVALID_BLOCK_TAG_USE,
                item,
                "Documentation contains '@hide', `@removed` or '@doconly' that is not used as a block tag; that could cause unexpected behavior downstream.",
                fileLocation,
            )
        }
    }

    override val mainDescription: DocContent?
        get() = docComment.description

    override val mainDescriptionOwner: DocContentOwner
        get() = docComment

    override fun blockTagDescription(tagTypeName: String): DocContent? =
        findBlockTagSection(tagTypeName)?.docContent

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
        docComment.blockTagSections.find { it.typeSafeTagData(TagTypes.PARAM)?.name == name }

    override fun check(predicate: DocContentPredicate) =
        docComment.check(predicate as DocCommentPredicate)

    /** Check to see if this requires a source comment. */
    override fun requiresSourceComment() = docComment.requiresSourceComment()

    override fun removeDeprecatedSection() {
        // Try and remove all the `@deprecated` sections.
        docComment.removeBlockTagSections { it.tagType == TagTypes.DEPRECATED }
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

    override fun duplicate(item: SelectableItem): ItemDocumentation =
        DefaultItemDocumentation(item, docComment, fileLocation)

    final override fun snapshot(item: SelectableItem): ItemDocumentation =
        // Return this to avoid parsing the text again and duplicating errors. This is not strictly
        // speaking safe as the underlying DocComment is mutable, and this will end up sharing it
        // across multiple snapshots, but it does not seem to cause any problems at the moment.
        this
}
