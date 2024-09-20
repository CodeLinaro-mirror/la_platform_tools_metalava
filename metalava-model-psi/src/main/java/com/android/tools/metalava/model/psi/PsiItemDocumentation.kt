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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.reporter.Issues
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.javadoc.PsiDocToken
import com.intellij.psi.javadoc.PsiInlineDocTag
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

        // We can't just use element.docComment here because we may have modified the comment and
        // then the comment snapshot in PSI isn't up-to-date with our latest changes
        val docComment = item.codebase.psiAssembler.getComment(text)
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
        return if (index < text.length) {
            text.substring(0, index)
        } else {
            text
        }
    }

    override fun mergeDocumentation(comment: String, tagSection: String?) {
        text = mergeDocumentation(text, psi, comment, tagSection, append = true)
    }

    override fun findMainDocumentation(): String {
        if (text == "") return text
        val comment = item.codebase.psiAssembler.getComment(text)
        val end = findFirstTag(comment)?.textRange?.startOffset ?: text.length
        return comment.text.substring(0, end)
    }

    override fun fullyQualifiedDocumentation(documentation: String): String {
        if (documentation.isBlank() || !containsLinkTags(documentation)) {
            return documentation
        }

        val assembler = item.codebase.psiAssembler
        val comment =
            try {
                assembler.getComment(documentation, psi)
            } catch (throwable: Throwable) {
                // TODO: Get rid of line comments as documentation
                // Invalid comment
                if (documentation.startsWith("//") && documentation.contains("/**")) {
                    return fullyQualifiedDocumentation(
                        documentation.substring(documentation.indexOf("/**"))
                    )
                }
                assembler.getComment(documentation, psi)
            }
        return buildString(documentation.length) { expand(comment, this) }
    }

    private fun reportUnresolvedDocReference(unresolved: String) {
        if (!REPORT_UNRESOLVED_SYMBOLS) {
            return
        }

        if (unresolved.startsWith("{@") && !unresolved.startsWith("{@link")) {
            return
        }

        // References are sometimes split across lines and therefore have newlines, leading
        // asterisks etc. in the middle: clean this up before emitting reference into error message
        val cleaned = unresolved.replace("\n", "").replace("*", "").replace("  ", " ")

        item.codebase.reporter.report(
            Issues.UNRESOLVED_LINK,
            item,
            "Unresolved documentation reference: $cleaned"
        )
    }

    private fun expand(element: PsiElement, sb: StringBuilder) {
        when {
            element is PsiWhiteSpace -> {
                sb.append(element.text)
            }
            element is PsiDocToken -> {
                assert(element.firstChild == null)
                val text = element.text
                // Auto-fix some docs in the framework which starts with R.styleable in @attr
                if (text.startsWith("R.styleable#") && item.documentation.contains("@attr")) {
                    sb.append("android.")
                }

                sb.append(text)
            }
            element is PsiDocMethodOrFieldRef -> {
                val text = element.text
                var resolved = element.reference?.resolve()

                // Workaround: relative references doesn't work from a class item to its members
                if (resolved == null && item is ClassItem) {
                    // For some reason, resolving relative methods and field references at the root
                    // level isn't working right.
                    if (PREPEND_LOCAL_CLASS && text.startsWith("#")) {
                        var end = text.indexOf('(')
                        if (end == -1) {
                            // definitely a field
                            end = text.length
                            val fieldName = text.substring(1, end)
                            val field = item.findField(fieldName)
                            if (field != null) {
                                resolved = (field as? PsiFieldItem)?.psi()
                            }
                        }
                        if (resolved == null) {
                            val methodName = text.substring(1, end)
                            resolved =
                                (item as PsiClassItem)
                                    .psi()
                                    .findMethodsByName(methodName, true)
                                    .firstOrNull()
                        }
                    }
                }

                if (resolved is PsiMember) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null && !samePackage(containingClass)) {
                        val referenceText = element.reference?.element?.text ?: text
                        if (!PREPEND_LOCAL_CLASS && referenceText.startsWith("#")) {
                            sb.append(text)
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
                        sb.append('#')
                        sb.append(resolved.name)
                        val index = text.indexOf('(')
                        if (index != -1) {
                            sb.append(text.substring(index))
                        }
                    } else {
                        sb.append(text)
                    }
                } else {
                    if (resolved == null) {
                        val referenceText = element.reference?.element?.text ?: text
                        if (text.startsWith("#") && item is ClassItem) {
                            // Unfortunately resolving references is broken from class javadocs
                            // to members using just a relative reference, #.
                        } else {
                            reportUnresolvedDocReference(referenceText)
                        }
                    }
                    sb.append(text)
                }
            }
            element is PsiJavaCodeReferenceElement -> {
                val resolved = element.resolve()
                if (resolved is PsiClass) {
                    if (samePackage(resolved) || resolved is PsiTypeParameter) {
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
                    if (resolved == null) {
                        reportUnresolvedDocReference(text)
                    }
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
        val name = element.name
        if (name == "code" || name == "literal") {
            // @code: don't attempt to rewrite this
            sb.append(element.text)
            return true
        }

        val reference = extractReference(element)
        val referenceText = reference?.element?.text ?: element.text
        val customLinkText = extractCustomLinkText(element)
        val displayText = customLinkText?.text ?: referenceText
        if (!PREPEND_LOCAL_CLASS && referenceText.startsWith("#")) {
            val suffix = element.text
            if (suffix.contains("(") && suffix.contains(")")) {
                expandArgumentList(element, suffix, sb)
            } else {
                sb.append(suffix)
            }
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
                        var className = computeFullClassName(referencedElement)
                        if (className.indexOf('.') != -1 && !referenceText.startsWith(className)) {
                            val simpleName = referencedElement.name
                            if (simpleName != null && referenceText.startsWith(simpleName)) {
                                className = simpleName
                            }
                        }
                        if (referenceText.startsWith(className)) {
                            sb.append("{@")
                            sb.append(element.name)
                            sb.append(' ')
                            sb.append(referencedElement.classQualifiedName)
                            val suffix = referenceText.substring(className.length)
                            if (suffix.contains("(") && suffix.contains(")")) {
                                expandArgumentList(element, suffix, sb)
                            } else {
                                sb.append(suffix)
                            }
                            sb.append(' ')
                            sb.append(displayText)
                            sb.append("}")
                            return true
                        }
                    }
                }
            }
        }

        var resolved = reference?.resolve()
        if (resolved == null && item is ClassItem) {
            // For some reason, resolving relative methods and field references at the root
            // level isn't working right.
            if (PREPEND_LOCAL_CLASS && referenceText.startsWith("#")) {
                var end = referenceText.indexOf('(')
                if (end == -1) {
                    // definitely a field
                    end = referenceText.length
                    val fieldName = referenceText.substring(1, end)
                    val field = item.findField(fieldName)
                    if (field != null) {
                        resolved = (field as? PsiFieldItem)?.psi()
                    }
                }
                if (resolved == null) {
                    val methodName = referenceText.substring(1, end)
                    resolved =
                        (item as PsiClassItem)
                            .psi()
                            .findMethodsByName(methodName, true)
                            .firstOrNull()
                }
            }
        }

        if (resolved != null) {
            when (resolved) {
                is PsiClass -> {
                    val text = element.text
                    if (samePackage(resolved)) {
                        sb.append(text)
                        return true
                    }
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
                            name == "see" -> {
                                val suffix =
                                    text.substring(
                                        text.indexOf(referenceText) + referenceText.length
                                    )
                                "@see $qualifiedName$suffix"
                            }
                            text.startsWith("{") -> "{@$name $qualifiedName $displayText}"
                            else -> "@$name $qualifiedName $displayText"
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
                    if (samePackage(containing)) {
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
        } else {
            reportUnresolvedDocReference(referenceText)
        }

        return false
    }

    private fun expandArgumentList(element: PsiInlineDocTag, suffix: String, sb: StringBuilder) {
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

    private fun samePackage(cls: PsiClass): Boolean {
        if (INCLUDE_SAME_PACKAGE) {
            // doclava seems to have REAL problems with this
            return false
        }
        val pkg = packageName() ?: return false
        return cls.qualifiedName == "$pkg.${cls.name}"
    }

    private fun packageName(): String? {
        var curr: Item? = item
        while (curr != null) {
            if (curr is PackageItem) {
                return curr.qualifiedName()
            }
            curr = curr.parent()
        }

        return null
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
        val salientElement: PsiElement =
            dataElements.firstOrNull { it !is PsiWhiteSpace && it !is PsiDocToken } ?: return null
        val child = salientElement.firstChild
        return if (child !is PsiReference) null else child
    }

    private fun extractCustomLinkText(tag: PsiDocTag): PsiDocToken? {
        val dataElements = tag.dataElements
        if (dataElements.isEmpty()) {
            return null
        }
        val salientElement: PsiElement =
            dataElements.lastOrNull { it !is PsiWhiteSpace && it !is PsiDocMethodOrFieldRef }
                ?: return null
        return if (salientElement !is PsiDocToken) null else salientElement
    }

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
                    comments.joinTo(buffer = sb, separator = "\n") { it.text }
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
