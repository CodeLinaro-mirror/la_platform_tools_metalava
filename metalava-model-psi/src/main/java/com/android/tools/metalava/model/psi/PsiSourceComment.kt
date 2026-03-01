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

import com.android.tools.metalava.model.source.LazySourceComment
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement

/**
 * A Psi implementation of [LazySourceComment] that retrieves the comment details from [psiElement].
 */
internal class PsiSourceComment(private val psiElement: PsiElement) : LazySourceComment() {
    /**
     * Lazily initialized reference to the [PsiComment], if any, from which the [text] was
     * retrieved.
     *
     * This is initialized in [obtainText].
     */
    private var psiComment: PsiComment? = null

    override fun obtainFileLocation(): FileLocation {
        // Make sure that the psiComment is initialized by making sure that the text backing field
        // has been initialized as they are initialized together in [obtainText].
        text
        return PsiFileLocation.fromPsiElement(psiComment)
    }

    override fun obtainText(): String {
        when (psiElement) {
            is PsiCompiledElement -> {
                // Drop through.
            }
            is KtDeclaration -> {
                psiElement.docComment?.let { comment ->
                    psiComment = comment
                    return comment.text
                }
            }
            is UElement -> {
                val comments = psiElement.comments
                if (comments.isNotEmpty()) {
                    for (comment in comments) {
                        val text = comment.text
                        if (text.startsWith("/**")) {
                            psiComment = comment.sourcePsi
                            return text
                        }
                    }
                }
            }
            is PsiDocCommentOwner -> {
                val docComment = psiElement.docComment
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        psiComment = docComment
                        return text
                    }
                } else {
                    // A doc comment could not be found so look a little deeper.
                    if (psiElement.annotations.size > 0) {
                        // If the element has annotations then if the annotations come before the
                        // doc comment then the doc comment will be a child of the PsiModifierList.
                        psiElement.children
                            // Get the first PsiModifierList.
                            .filterIsInstance<PsiModifierList>()
                            .firstOrNull()
                            // Get its first PsiDocComment.
                            ?.children
                            ?.filterIsInstance<PsiDocComment>()
                            ?.firstOrNull()
                            ?.let { docComment ->
                                val text = docComment.text

                                // Make sure that the text is a doc comment, i.e. starts with /**.
                                if (text.startsWith("/**")) {
                                    psiComment = docComment
                                    return text
                                }
                            }
                    }
                }
            }
            is PsiPackageStatement -> {
                val docComment =
                    PsiTreeUtil.getPrevSiblingOfType(psiElement, PsiDocComment::class.java)
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        psiComment = docComment
                        return text
                    }
                }
            }
        }

        psiComment = null
        return ""
    }
}
