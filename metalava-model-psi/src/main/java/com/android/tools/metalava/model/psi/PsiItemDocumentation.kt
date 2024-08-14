/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AbstractItemDocumentation
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.sourcePsiElement

/** A Psi specialization of [ItemDocumentation]. */
internal class PsiItemDocumentation(
    private val item: PsiItem,
    private val psi: PsiElement,
    private val extraDocs: String?,
) : AbstractItemDocumentation() {

    /** Lazily initialized backing property for [text]. */
    private lateinit var _text: String

    override var text: String
        get() = if (::_text.isInitialized) _text else initializeText()
        set(value) {
            _text = value
        }

    /** Lazy initializer for [_text]. */
    private fun initializeText(): String {
        _text = javadoc(psi).let { if (extraDocs != null) it + "\n$extraDocs" else it }
        return _text
    }

    override fun duplicate(item: Item) = PsiItemDocumentation(item as PsiItem, psi, extraDocs)

    override fun findTagDocumentation(tag: String, value: String?): String? {
        if (psi is PsiCompiledElement) {
            return null
        }
        if (text.isBlank()) {
            return null
        }

        // We can't just use element.docComment here because we may have modified
        // the comment and then the comment snapshot in PSI isn't up to date with our
        // latest changes
        val docComment = item.codebase.getComment(text)
        val tagComment =
            if (value == null) {
                docComment.findTagByName(tag)
            } else {
                docComment.findTagsByName(tag).firstOrNull { it.valueElement?.text == value }
            }

        if (tagComment == null) {
            return null
        }

        val text = tagComment.text
        // Trim trailing next line (javadoc *)
        var index = text.length - 1
        while (index > 0) {
            val c = text[index]
            if (!(c == '*' || c.isWhitespace())) {
                break
            }
            index--
        }
        index++
        if (index < text.length) {
            return text.substring(0, index)
        } else {
            return text
        }
    }

    override fun mergeDocumentation(comment: String, tagSection: String?) {
        text = mergeDocumentation(text, psi, comment, tagSection, append = true)
    }

    override fun findMainDocumentation(): String {
        if (text == "") return text
        val comment = item.codebase.getComment(text)
        val end = findFirstTag(comment)?.textRange?.startOffset ?: text.length
        return comment.text.substring(0, end)
    }

    override fun fullyQualifiedDocumentation(documentation: String) =
        item.codebase.docQualifier.toFullyQualifiedDocumentation(item, documentation)

    companion object {
        /**
         * Get an [ItemDocumentationFactory] for the [psi].
         *
         * If [PsiBasedCodebase.allowReadingComments] is `true` then this will return a factory that
         * creates a [PsiItemDocumentation] instance. If [extraDocs] is not-null then this will
         * return a factory that will create an [ItemDocumentation] wrapper around [extraDocs],
         * otherwise it will return [ItemDocumentation.NONE_FACTORY].
         *
         * @param psi the underlying element from which the documentation will be retrieved.
         *   Although this is usually accessible through the [PsiItem.psi] property, that is not
         *   true within the [ItemDocumentationFactory] as that is called during initialization of
         *   the [PsiItem] before [PsiItem.psi] has been initialized.
         */
        internal fun factory(
            psi: PsiElement,
            codebase: PsiBasedCodebase,
            extraDocs: String? = null,
        ) =
            if (codebase.allowReadingComments) {
                // When reading comments provide full access to them.
                { item ->
                    val psiItem = item as PsiItem
                    PsiItemDocumentation(psiItem, psi, extraDocs)
                }
            } else {
                // If extraDocs are provided then they most likely contain documentation for the
                // package from a `package-info.java` or `package.html` file. Make sure that they
                // are included in the `ItemDocumentation`, otherwise package hiding will not work.
                extraDocs?.toItemDocumentationFactory()
                // Otherwise, there is no documentation to use.
                ?: ItemDocumentation.NONE_FACTORY
            }

        // Gets the javadoc of the current element
        private fun javadoc(element: PsiElement): String {
            if (element is PsiCompiledElement) {
                return ""
            }

            if (element is KtDeclaration) {
                return element.docComment?.text.orEmpty()
            }

            if (element is UElement) {
                val comments = element.comments
                if (comments.isNotEmpty()) {
                    val sb = StringBuilder()
                    comments.asSequence().joinTo(buffer = sb, separator = "\n") { it.text }
                    return sb.toString()
                } else {
                    // Temporary workaround: UAST seems to not return document nodes
                    // https://youtrack.jetbrains.com/issue/KT-22135
                    val first = element.sourcePsiElement?.firstChild
                    if (first is KDoc) {
                        return first.text
                    }
                }
            }

            if (element is PsiDocCommentOwner && element.docComment !is PsiCompiledElement) {
                return element.docComment?.text ?: ""
            }

            return ""
        }
    }
}
