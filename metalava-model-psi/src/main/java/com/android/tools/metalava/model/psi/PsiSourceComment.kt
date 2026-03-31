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
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.java.FieldElement
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
                // If this element is a field then it may be a field in a multi-field declaration in
                // which case the doc comment is only associated with the first field, so look to
                // see if that is the case. Do this before checking the owner so that it does not
                // accidentally take a doc comment from within the middle of the declaration.
                if (psiElement is PsiField) {
                    // Get the syntax tree for this field.
                    SourceTreeToPsiMap.psiElementToTree(psiElement)?.let { sourceTree ->
                        // Search backwards through the syntax tree to find the first field in a
                        // multiple field declaration. Start with the tree node before this field.
                        var tree = sourceTree.treePrev

                        // Track whether a comma has been seen as a preceding field is only part of
                        // a multi-field declaration if it is separated with a comma.
                        var precedingFieldIsPartOfMultiFieldDeclaration = false

                        // Record the first field in the multi-field declaration, if any.
                        var firstField: FieldElement? = null

                        // Iterate through the previous siblings of the field.
                        while (tree != null) {
                            when {
                                tree is PsiWhiteSpace -> {
                                    // Ignore white spaces.
                                }
                                tree is PsiJavaToken && tree.tokenType == JavaTokenType.COMMA -> {
                                    // A comma indicates that the preceding field is part of the
                                    // same multi-field declaration as this one.
                                    precedingFieldIsPartOfMultiFieldDeclaration = true
                                }
                                tree is FieldElement -> {
                                    // If this is part of the same multi-field declaration then
                                    // treat it as the first unless a preceding one can be found.
                                    if (precedingFieldIsPartOfMultiFieldDeclaration) {
                                        // This is the earliest field that is part of a multi-field
                                        // declaration found so far.
                                        firstField = tree

                                        // Make sure to require a comma in order to treat a
                                        // preceding field as part of the multi-field declaration.
                                        precedingFieldIsPartOfMultiFieldDeclaration = false
                                    } else {
                                        // Otherwise, this field is separate so stop.
                                        break
                                    }
                                }
                                else -> {
                                    // Stop at anything that is not whitespace, comma or a field as
                                    // it has reached the beginning of the field declaration,
                                    // whether for a multi-field or not.
                                    break
                                }
                            }

                            // Move to the previous tree node.
                            tree = tree.treePrev
                        }

                        // If a field is found then check to see if it has a doc comment. It will be
                        // the first child if it is present. If it is present then use it, otherwise
                        // drop out.
                        if (firstField != null) {
                            val docComment = firstField.firstChildNode as? PsiDocComment
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
                }

                val docComment = psiElement.docComment
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        psiComment = docComment
                        return text
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
