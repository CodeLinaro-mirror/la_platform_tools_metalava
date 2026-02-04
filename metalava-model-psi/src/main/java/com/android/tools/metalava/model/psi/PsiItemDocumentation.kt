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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.AbstractItemDocumentation
import com.android.tools.metalava.model.source.toItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement

/** A Psi specialization of [ItemDocumentation]. */
internal class PsiItemDocumentation(
    item: SelectableItem,
    private val codebase: PsiBasedCodebase,
    private val psi: PsiElement,
) : AbstractItemDocumentation(item) {

    override fun initializeTextBackingField() {
        initializeFromPsiElement(psi)
    }

    private var psiComment: PsiComment? = null

    override val fileLocation: FileLocation
        get() {
            // Make sure that the psiComment is initialized by making sure that the text backing
            // field has been initialized as they are initialized together in
            // [initializeTextBackingField].
            ensureTextBackingFieldHasBeenInitialized()
            return PsiFileLocation.fromPsiElement(psiComment)
        }

    /**
     * Lazy initializer for [_text] and [psiComment].
     *
     * Updates the [_text] field with the text of the document comment associated with [element] and
     * [psiComment] with the [PsiComment] for the document comment.
     */
    private fun initializeFromPsiElement(element: PsiElement) {
        when (element) {
            is PsiCompiledElement -> {
                // Drop through.
            }
            is KtDeclaration -> {
                element.docComment?.let { comment ->
                    _text = comment.text
                    psiComment = comment
                    return
                }
            }
            is UElement -> {
                val comments = element.comments
                if (comments.isNotEmpty()) {
                    for (comment in comments) {
                        val text = comment.text
                        if (text.startsWith("/**")) {
                            psiComment = comment.sourcePsi
                            _text = text
                            return
                        }
                    }
                }
            }
            is PsiDocCommentOwner -> {
                val docComment = element.docComment
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        _text = text
                        psiComment = docComment
                        return
                    }
                }
            }
            is PsiPackageStatement -> {
                val docComment =
                    PsiTreeUtil.getPrevSiblingOfType(element, PsiDocComment::class.java)
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        _text = text
                        psiComment = docComment
                        return
                    }
                }
            }
        }

        _text = ""
        psiComment = null
    }

    override fun duplicate(item: SelectableItem) =
        if (item is PsiItem) PsiItemDocumentation(item, codebase, psi)
        else text.toItemDocumentationFactory().create(item)!!

    override fun snapshot(item: SelectableItem) = this

    companion object {
        /**
         * Get an [ItemDocumentationFactory] for the [psi].
         *
         * If [Codebase.Config.allowReadingComments] is `true` then this will return a factory that
         * creates a [PsiItemDocumentation] instance, otherwise it will return
         * [ItemDocumentation.NONE_FACTORY].
         *
         * @param psi the underlying element from which the documentation will be retrieved.
         *   Although this is usually accessible through the [PsiItem.psi] property, that is not
         *   true within the [ItemDocumentationFactory] as that is called during initialization of
         *   the [PsiItem] before [PsiItem.psi] has been initialized.
         */
        internal fun factory(
            psi: PsiElement,
            codebase: PsiBasedCodebase,
        ) =
            if (codebase.config.allowReadingComments) {
                // When reading comments provide full access to them.
                ItemDocumentationFactory { item -> PsiItemDocumentation(item, codebase, psi) }
            } else {
                // Otherwise, there is no documentation to use.
                ItemDocumentation.NONE_FACTORY
            }
    }
}
