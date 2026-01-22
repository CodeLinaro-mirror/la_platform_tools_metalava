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

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.AbstractItemDocumentation
import com.android.tools.metalava.model.source.toItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.JavaDocTokenType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiDocToken
import com.intellij.psi.javadoc.PsiInlineDocTag
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

    override fun fullyQualifiedDocumentation(documentation: String): String {
        if (documentation.isBlank() || !containsLinkTags(documentation)) {
            return documentation
        }

        val assembler = codebase.psiAssembler
        val comment = assembler.getComment(documentation, psi)
        return buildString(documentation.length) { expand(comment, this) }
    }

    private fun expand(element: PsiElement, sb: StringBuilder) {
        when {
            element is PsiWhiteSpace -> {
                sb.append(element.text)
            }
            element is PsiDocToken -> {
                assert(element.firstChild == null)
                val text = element.text
                sb.append(text)
            }
            element is PsiDocMethodOrFieldRef -> {
                val text = element.text
                val resolved = element.reference?.resolve()
                if (resolved is PsiMember) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val referenceText = element.reference?.element?.text ?: text
                        if (referenceText.startsWith("#")) {
                            appendFullyQualifiedMemberSuffix(element, sb, referenceText)
                            return
                        }

                        var className = containingClass.classQualifiedName

                        if (
                            element.firstChildNode.elementType ===
                                JavaDocElementType.DOC_REFERENCE_HOLDER
                        ) {
                            val firstChildPsi =
                                SourceTreeToPsiMap.treeElementToPsi(
                                    element.firstChildNode.firstChildNode
                                )
                            if (firstChildPsi is PsiJavaCodeReferenceElement) {
                                val referenceElement = firstChildPsi as PsiJavaCodeReferenceElement?
                                val referencedElement = referenceElement!!.resolve()
                                if (referencedElement is PsiClass) {
                                    className = referencedElement.classQualifiedName
                                }
                            }
                        }

                        sb.append(className)

                        val hashIndex = text.indexOf('#')
                        val memberSuffix = if (hashIndex == -1) "" else text.substring(hashIndex)
                        appendFullyQualifiedMemberSuffix(element, sb, memberSuffix)
                    } else {
                        sb.append(text)
                    }
                } else {
                    if (text.startsWith("#")) {
                        appendFullyQualifiedMemberSuffix(element, sb, text)
                    } else {
                        sb.append(text)
                    }
                }
            }
            element is PsiJavaCodeReferenceElement -> {
                val resolved = element.resolve()
                if (resolved is PsiClass) {
                    if (resolved is PsiTypeParameter) {
                        sb.append(element.text)
                    } else {
                        sb.append(resolved.classQualifiedName)
                    }
                } else if (resolved is PsiMember) {
                    val text = element.text
                    sb.append(resolved.containingClass?.classQualifiedName)
                    sb.append('#')
                    sb.append(resolved.name)
                    val index = text.indexOf('(')
                    if (index != -1) {
                        sb.append(text.substring(index))
                    }
                } else {
                    val text = element.text
                    sb.append(text)
                }
            }
            element is PsiInlineDocTag -> {
                val handled = handleTag(element, sb)
                if (!handled) {
                    sb.append(element.text)
                }
            }
            element.firstChild != null -> {
                var curr = element.firstChild
                while (curr != null) {
                    expand(curr, sb)
                    curr = curr.nextSibling
                }
            }
            else -> {
                val text = element.text
                sb.append(text)
            }
        }
    }

    private fun handleTag(element: PsiInlineDocTag, sb: StringBuilder): Boolean {
        val tagType = element.name
        if (tagType == "code" || tagType == "literal" || tagType == "throws") {
            // Don't attempt to rewrite this
            return false
        }

        val reference = extractReference(element)
        val referenceText = reference?.element?.text ?: element.text
        val customLinkText = extractCustomLinkText(element)
        val displayText = customLinkText ?: referenceText.replaceFirst('#', '.')
        if (referenceText.startsWith("#")) {
            val suffix = element.text
            appendFullyQualifiedMemberSuffix(element, sb, suffix)
            return true
        }

        // TODO: If referenceText is already absolute, e.g.
        // android.Manifest.permission#BIND_CARRIER_SERVICES,
        // try to short circuit this?

        val valueElement = element.valueElement
        if (valueElement is CompositePsiElement) {
            if (
                valueElement.firstChildNode.elementType === JavaDocElementType.DOC_REFERENCE_HOLDER
            ) {
                val firstChildPsi =
                    SourceTreeToPsiMap.treeElementToPsi(valueElement.firstChildNode.firstChildNode)
                if (firstChildPsi is PsiJavaCodeReferenceElement) {
                    val referenceElement = firstChildPsi as PsiJavaCodeReferenceElement?
                    val referencedElement = referenceElement!!.resolve()
                    if (referencedElement is PsiClass) {
                        val qualifiedName = referencedElement.classQualifiedName
                        val hashIndex = referenceText.indexOf('#')
                        val className =
                            if (hashIndex == -1) referenceText
                            else referenceText.substring(0, hashIndex)
                        if (qualifiedName == className || qualifiedName.endsWith(".$className")) {
                            val suffix =
                                if (hashIndex == -1) "" else referenceText.substring(hashIndex)
                            appendFullyQualifiedTag(element, sb, qualifiedName, suffix, displayText)
                            return true
                        }
                    }
                }
            }
        }

        val resolved = reference?.resolve()
        if (resolved != null) {
            when (resolved) {
                is PsiClass -> {
                    // No need to handle class references in {@link} and {@linkplain} tags as they
                    // have been resolved in LinkTagType.
                    if (tagType == "link" || tagType == "linkplain") {
                        return false
                    }

                    val text = element.text
                    val qualifiedName =
                        resolved.qualifiedName
                            ?: run {
                                sb.append(text)
                                return true
                            }
                    if (referenceText == qualifiedName) {
                        // Already absolute
                        sb.append(text)
                        return true
                    }
                    val append =
                        when {
                            valueElement != null -> {
                                val start = valueElement.startOffsetInParent
                                val end = start + valueElement.textLength
                                text.substring(0, start) + qualifiedName + text.substring(end)
                            }
                            tagType == "see" -> {
                                val suffix =
                                    text.substring(
                                        text.indexOf(referenceText) + referenceText.length
                                    )
                                "@see $qualifiedName$suffix"
                            }
                            text.startsWith("{") -> "{@$tagType $qualifiedName $displayText}"
                            else -> "@$tagType $qualifiedName $displayText"
                        }
                    sb.append(append)
                    return true
                }
                is PsiMember -> {
                    val text = element.text
                    val containing =
                        resolved.containingClass
                            ?: run {
                                sb.append(text)
                                return true
                            }
                    val qualifiedName =
                        containing.qualifiedName
                            ?: run {
                                sb.append(text)
                                return true
                            }
                    if (referenceText.startsWith(qualifiedName)) {
                        // Already absolute
                        sb.append(text)
                        return true
                    }

                    // It may also be the case that the reference is already fully qualified
                    // but to some different class. For example, the link may be to
                    // android.os.Bundle#getInt, but the resolved method actually points to
                    // an inherited method into android.os.Bundle from android.os.BaseBundle.
                    // In that case we don't want to rewrite the link.
                    for (c in referenceText) {
                        if (c == '.') {
                            // Already qualified
                            sb.append(text)
                            return true
                        } else if (!Character.isJavaIdentifierPart(c)) {
                            break
                        }
                    }

                    if (valueElement != null) {
                        val start = valueElement.startOffsetInParent

                        var nameEnd = -1
                        var close = start
                        var balance = 0
                        while (close < text.length) {
                            val c = text[close]
                            if (c == '(') {
                                if (nameEnd == -1) {
                                    nameEnd = close
                                }
                                balance++
                            } else if (c == ')') {
                                balance--
                                if (balance == 0) {
                                    close++
                                    break
                                }
                            } else if (c == '}') {
                                if (nameEnd == -1) {
                                    nameEnd = close
                                }
                                break
                            } else if (balance == 0 && c == '#') {
                                if (nameEnd == -1) {
                                    nameEnd = close
                                }
                            } else if (balance == 0 && !Character.isJavaIdentifierPart(c)) {
                                break
                            }
                            close++
                        }
                        val memberPart = text.substring(nameEnd, close)
                        val append =
                            "${text.substring(0, start)}$qualifiedName$memberPart $displayText}"
                        sb.append(append)
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Append a fully qualified version of [element] to [sb].
     *
     * @param element the [PsiInlineDocTag] to append.
     * @param sb the destination [StringBuilder].
     * @param qualifiedName the fully qualified name of the class.
     * @param memberSuffix the not yet fully qualified member reference suffix.
     * @param label the label to use for the tag.
     */
    private fun appendFullyQualifiedTag(
        element: PsiInlineDocTag,
        sb: StringBuilder,
        qualifiedName: String,
        memberSuffix: String,
        label: String
    ) {
        // Open the doc tag.
        sb.append("{@")
        sb.append(element.name)
        sb.append(' ')

        // Append the fully qualified reference to the buffer, remembering where it started so it
        // can be extracted later.
        val startReference = sb.length
        sb.append(qualifiedName)
        appendFullyQualifiedMemberSuffix(element, sb, memberSuffix)

        // Extract the qualified reference, convert it into display text by replacing '#' with '.'
        // and compare it to the display text. If it is different then append the display text,
        // otherwise do not.
        if (sb.substring(startReference).replace('#', '.') != label) {
            sb.append(' ')
            sb.append(label)
        }

        // Close the doc tag.
        sb.append("}")
    }

    /**
     * Append a fully qualified version of [memberSuffix] to [sb].
     *
     * @param element the [PsiElement] that will be used to resolve any type references.
     * @param sb the destination [StringBuilder].
     * @param memberSuffix the not yet fully qualified member reference suffix.
     */
    private fun appendFullyQualifiedMemberSuffix(
        element: PsiElement,
        sb: StringBuilder,
        memberSuffix: String
    ) {
        if (memberSuffix.contains("(") && memberSuffix.contains(")")) {
            expandArgumentList(element, memberSuffix, sb)
        } else {
            sb.append(memberSuffix)
        }
    }

    private fun expandArgumentList(element: PsiElement, suffix: String, sb: StringBuilder) {
        val elementFactory = JavaPsiFacade.getElementFactory(element.project)
        // Try to rewrite the types to fully qualified names as well
        val begin = suffix.indexOf('(')
        sb.append(suffix.substring(0, begin + 1))
        var index = begin + 1
        var balance = 0
        var argBegin = index
        while (index < suffix.length) {
            val c = suffix[index++]
            if (c == '<' || c == '(') {
                balance++
            } else if (c == '>') {
                balance--
            } else if (c == ')' && balance == 0 || c == ',') {
                // Strip off javadoc header
                while (argBegin < index) {
                    val p = suffix[argBegin]
                    if (p != '*' && !p.isWhitespace()) {
                        break
                    }
                    argBegin++
                }
                if (index > argBegin + 1) {
                    val arg = suffix.substring(argBegin, index - 1).trim()
                    val space = arg.indexOf(' ')
                    // Strip off parameter name (shouldn't be there but happens
                    // in some Android sources sine tools didn't use to complain
                    val typeString =
                        if (space == -1) {
                            arg
                        } else {
                            if (space < arg.length - 1 && !arg[space + 1].isJavaIdentifierStart()) {
                                // Example: "String []"
                                arg
                            } else {
                                // Example "String name"
                                arg.substring(0, space)
                            }
                        }
                    var insert = arg
                    if (typeString[0].isUpperCase()) {
                        try {
                            val type = elementFactory.createTypeFromText(typeString, element)
                            insert = type.canonicalText
                        } catch (ignore: com.intellij.util.IncorrectOperationException) {
                            // Not a valid type - just leave what was in the parameter text
                        }
                    }
                    sb.append(insert)
                    sb.append(c)
                    if (c == ')') {
                        break
                    }
                } else if (c == ')') {
                    sb.append(')')
                    break
                }
                argBegin = index
            } else if (c == ')') {
                balance--
            }
        }
        while (index < suffix.length) {
            sb.append(suffix[index++])
        }
    }

    // Copied from UnnecessaryJavaDocLinkInspection and tweaked a bit
    private fun extractReference(tag: PsiDocTag): PsiReference? {
        val valueElement = tag.valueElement
        if (valueElement != null) {
            return valueElement.reference
        }
        // hack around the fact that a reference to a class is apparently
        // not a PsiDocTagValue
        val dataElements = tag.dataElements
        if (dataElements.isEmpty()) {
            return null
        }
        val salientElement = dataElements.firstOrNull { it !is PsiWhiteSpace && it !is PsiDocToken }
        return salientElement?.firstChild as? PsiReference
    }

    private fun extractCustomLinkText(tag: PsiDocTag): String? {
        val children = tag.children
        // The child elements should have the following structure
        // 1. Inline tag start.
        // 2. Tag name.
        // 3. Some white space.
        // 4. Some non-white space which is the reference.
        // 5. Some more white space.
        // 6. The rest of the label.
        // 7. Inline tag end.

        // 7. Skip inline tag end.
        val end = children.size - 1

        // 1-3. Find the reference, start from after the tag name.
        var start = 2
        while (start < end) {
            val child = children[start]
            if (child is PsiDocMethodOrFieldRef || child.firstChild is PsiReference) break
            start += 1
        }

        // 4. Skip past the reference.
        start += 1

        // 5. Skip white space and leading asterisks.
        while (start < end) {
            val child = children[start]
            // Stop at the first non-whitespace, non-leading asterisk element.
            if (
                child !is PsiWhiteSpace &&
                    !(child is PsiDocToken &&
                        child.tokenType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS)
            ) {
                break
            }
            start += 1
        }

        // Check to see if there is any label.
        if (start >= end) return null

        // 6. Collect label.
        return buildString {
                for (i in start until end) {
                    val child = children[i]

                    // If the child is a leading asterisk then it must not be added and also any
                    // leading spaces that have already been appended to this StringBuilder need to
                    // be removed as per the Javadoc specification on handling leading asterisks.
                    if (
                        child is PsiDocToken &&
                            child.tokenType == JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS
                    ) {
                        var trimmedIndex = length
                        while (trimmedIndex > 0 && this[trimmedIndex - 1] != '\n') trimmedIndex -= 1
                        setLength(trimmedIndex)
                        continue
                    }

                    append(child.text)
                }
            }
            .trimStart()
    }

    companion object {
        /**
         * Get an [ItemDocumentationFactory] for the [psi].
         *
         * If [PsiBasedCodebase.allowReadingComments] is `true` then this will return a factory that
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

/**
 * Computes the "full" class name; this is not the qualified class name (e.g. with package) but for
 * a nested class it includes all the outer classes
 */
private fun computeFullClassName(cls: PsiClass): String {
    if (cls.containingClass == null) {
        val name = cls.name
        return name!!
    } else {
        val list = mutableListOf<String>()
        var curr: PsiClass? = cls
        while (curr != null) {
            val name = curr.name
            curr =
                if (name != null) {
                    list.add(name)
                    curr.containingClass
                } else {
                    break
                }
        }
        return list.asReversed().joinToString(separator = ".") { it }
    }
}
