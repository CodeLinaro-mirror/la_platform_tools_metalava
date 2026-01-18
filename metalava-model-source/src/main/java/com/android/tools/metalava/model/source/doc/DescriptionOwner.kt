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
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
import com.android.tools.metalava.model.source.javadoc.JavadocText
import com.android.tools.metalava.model.source.javadoc.TextEndsWithVisitor
import com.android.tools.metalava.model.source.javadoc.concatJavadocContent
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * Base class for classes that own a [JavadocContent] description.
 *
 * @param context Contextual information that can affect the behavior of documentation.
 * @param descriptionSupplier Supplies a [JavadocContent] instance when requested. May produce it
 *   lazily.
 * @param noComment `true` if there was no comment in the sources.
 */
internal open class DescriptionOwner(
    val context: DocCommentContext,
    protected val descriptionSupplier: ContentSupplier,
    protected val noComment: Boolean,
) : DocContentOwner {
    /**
     * A mutable and optional [JavadocContent] that is initialized lazily from [descriptionSupplier]
     * in [initializeDescription].
     */
    private lateinit var _description: Optional<JavadocContent>

    /**
     * Provides access to the [JavadocContent] in [_description].
     *
     * If [_description] is not initialized then this will initialize it from the
     * [descriptionSupplier]. It may need to do a lot of work to produce the [JavadocContent] so
     * this must only be accessed when absolutely necessary and only when processing actual API
     * documentation.
     */
    val description: JavadocContent?
        get() {
            ensureDescriptionIsInitialized()
            return _description.getOrNull()
        }

    /**
     * Ensure that [_description] is initialized from [descriptionSupplier].
     *
     * This can be called by subclasses to ensure that [initializeDescription] has been called
     * without retrieving [description].
     */
    protected fun ensureDescriptionIsInitialized() {
        if (!::_description.isInitialized) {
            initializeDescription(descriptionSupplier.content)
        }
    }

    /**
     * Initialize [_description] from [suppliedDescription].
     *
     * This can be overridden by subclasses to customize the initialization.
     */
    protected open fun initializeDescription(suppliedDescription: JavadocContent?) {
        _description = Optional.ofNullable(suppliedDescription)
    }

    override val docContent: DocContent?
        get() = description

    /**
     * Backing field for [docContentForAppending].
     *
     * This is generated on demand from [docContent] so when [docContent] changes this must be
     * discarded so the next time it is requested it will be regenerated from the updated
     * [docContent].
     */
    private var _docContentForAppending: Optional<DocContent>? = null

    /**
     * Special form of [docContent] that is specially prepared for appending to documentation on
     * other classes.
     */
    val docContentForAppending: DocContent?
        get() {
            if (_docContentForAppending == null) {
                _docContentForAppending = Optional.ofNullable(prepareForAppending(description))
            }

            return _docContentForAppending!!.getOrNull()
        }

    private fun prepareForAppending(content: JavadocContent?): DocContent? {
        content ?: return null
        // Insert the content into a Javadoc comment.
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { writer ->
            writer.print("/**\n")
            writer.print(" * ")
            val printer = JavadocContentPrinter(writer, context)
            printer.print(content)
            writer.print("\n */")
        }
        val text = stringWriter.toString()

        // Remove any leading whitespace. This is not strictly safe as it could contain `<pre>` tags
        // where whitespace is important. However, it is what the preceding implementation did so it
        // clearly did not cause too many issues.
        val trimmed = text.replace(Regex("""^ \*  +""", RegexOption.MULTILINE), " * ")

        // Fully qualify the content. This does not fully qualify references that are in the same
        // class or package but again this is what the preceding implementation did.
        val qualified = context.fullyQualifyComment(trimmed)

        // Parse the comment, throwing an error if any errors were found.
        val docComment =
            DocComment.createDocComment(
                context,
                qualified,
                // Use the null reporter as any issues found in this block tag will have been
                // reported elsewhere.
                DocumentationIssueReporter.NULL,
            )

        // Return the main description as the comment.
        return docComment.description
    }

    /**
     * Update [description] to [new].
     *
     * If [new] is the same as [description] then does nothing. Otherwise, it sets [_description] to
     * [new] and notifies any listener that the containing [DocComment] has changed.
     */
    private fun updateDescription(new: JavadocContent?) {
        if (new !== description) {
            _description = Optional.ofNullable(new)

            // Discard any content prepared for appending.
            _docContentForAppending = null

            // Notify any listener.
            context.mutationListener.docCommentMutated()
        }
    }

    override fun append(other: DocContent) {
        append(other as JavadocContent)
    }

    override fun append(text: String) {
        val supplier = LazyContentSupplier(context, DocumentationIssueReporter.THROWING, text)
        val content = supplier.content ?: return
        append(content)
    }

    /** Check whether this needs to insert `{@inheritDoc}` tags when appending to [description]. */
    private fun requiresInheritDoc(): Boolean =
        noComment && description == null && context.isOverridingMethod()

    /**
     * Append `{@inheritDoc}` if needed.
     *
     * @return `true` if `{@inheritDoc}` was appended which would have also notified the
     *   [DocComment] owner that it changed.
     */
    protected fun appendInheritDocIfNeeded(): Boolean {
        if (!requiresInheritDoc()) return false
        updateDescription(INHERIT_DOC_CONTENT)
        return true
    }

    /** Append [other] to [description]. */
    private fun append(other: JavadocContent) {
        val result =
            if (requiresInheritDoc()) {
                // Prepend `{@inheritDoc}` before the content to add.
                concatJavadocContent {
                    add(INHERIT_DOC_CONTENT)
                    // TODO(b/454257440): This was only added to maintain consistency with the
                    //   appendDocumentation method. Investigate whether it can be removed without
                    //   affecting the HTML formatting.
                    add(BLANK_LINE)
                    add(other)
                }
            } else {
                description.append(other)
            }
        updateDescription(result)
    }

    /**
     * Append [other] to [this] optional [JavadocContent] and return the result.
     *
     * If [this] is null then just returns [other], else joins [this], a [BR_SEPARATOR] and [other]
     * into a single [JavadocContent].
     */
    fun JavadocContent?.append(other: JavadocContent) =
        this?.let {
            concatJavadocContent {
                add(it)
                // Add a period after existing content, if needed.
                if (it.matches(NEEDS_PUNCTUATION)) {
                    add(PERIOD)
                }
                add(BR_SEPARATOR)
                add(other)
            }
        } ?: other

    companion object {
        /**
         * `{@inheritDoc}` inline tag to insert when modifying a comment that does not exist in the
         * sources and is attached to an overriding method.
         */
        private val INHERIT_DOC_CONTENT = JavadocInlineTag(TagTypes.INHERIT_DOC, null, null)

        /**
         * Blank line to add between [INHERIT_DOC_CONTENT] and the content being added to maintain
         * compatibility with the `appendDocumentation(...)` method.
         */
        private val BLANK_LINE = JavadocText("\n\n ")

        /**
         * The `<br>` separator inserted between existing content and the appended content in
         * [append].
         *
         * It has this specific form to ensure that it appears on its own line with the correct
         * indentation and the appended text starts on the line after that also with the correct
         * indentation.
         */
        private val BR_SEPARATOR = JavadocText("\n <br>\n ")

        /**
         * Predicate to check whether the existing content needs punctuation.
         *
         * It checks for the absence of `.` and `{@inheritDoc}`. The latter is included as it will
         * be replaced by content from the super method, and it is not known if that ends with a `.`
         * or not.
         *
         * TODO(b/454257440): Check content of super method.
         */
        private val NEEDS_PUNCTUATION = TextEndsWithVisitor {
            !it.endsWith(".") && !it.endsWith("{@inheritDoc}")
        }

        /** Period to insert at the end of the preceding sentence if it was not present. */
        private val PERIOD = JavadocText(".")
    }
}
