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
import com.intellij.lang.ASTNode
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.JavaDocElementType
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

                // Get the PsiDocComment closest to the declaration, if any. That could be a
                // traditional doc comment or a Markdown comment. If it is a Markdown comment then
                // it will be ignored as Metalava does not yet support parsing Markdown comments.
                val docComment = closestDocComment(psiElement)
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

    /**
     * Find the closest [PsiDocComment], if any, to a declaration [element].
     *
     * The [PsiDocComment] could be either a traditional doc comment, or a Markdown comment.
     */
    private fun closestDocComment(element: PsiElement): PsiDocComment? {
        // The PsiElement should be composed of multiple ASTNodes. That includes everything from
        // the first doc comment that precedes the declaration element to the end of the
        // declaration (e.g. closing `}` of a class declaration or concrete method, `;` of an
        // abstract method or field. That also includes whitespace and line or block comments.
        val parent = element.node as? CompositeElement ?: return null

        // The last PsiDocComment that has been found
        var docComment: PsiDocComment? = null

        // Iterate over the parent's ASTNodes from first to last. It will stop at the first node
        // that is not a comment or whitespace. That will limit the work done when the doc comment
        // does not exist.
        var child: ASTNode? = parent.firstChildNode
        while (child != null) {
            // Get the node type.
            val childType = child.elementType
            when (childType) {
                JavaDocElementType.DOC_COMMENT,
                JavaDocElementType.DOC_MARKDOWN_COMMENT -> {
                    // Remember the doc comment but continue just in case there is one closer.
                    docComment = child as? PsiDocComment
                }
                TokenType.WHITE_SPACE,
                JavaTokenType.END_OF_LINE_COMMENT,
                JavaTokenType.C_STYLE_COMMENT -> {
                    // Ignore white space or non-doc comments as they can appear between doc
                    // comments. Drop out to move onto the next child.
                }
                else -> {
                    // Otherwise, exit early as while syntactically documentation comments can
                    // appear anywhere, by convention doc comments have to appear before any part of
                    // the API declaration.
                    break
                }
            }
            child = child.treeNext
        }
        return docComment
    }
}
