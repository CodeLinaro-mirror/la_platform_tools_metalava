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

import com.android.tools.metalava.reporter.BaselineKey
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.impl.light.LightElement
import java.nio.file.Path
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

/** A [FileLocation] that wraps [psiElement] and computes the [path] and [line] number on demand. */
class PsiFileLocation(private val psiElement: PsiElement) : FileLocation() {
    /**
     * Backing property for the [path] getter.
     *
     * This must only be accessed after calling [ensureInitialized].
     */
    private var _path: Path? = null

    /**
     * Backing property for the [line] getter.
     *
     * If this is [Int.MAX_VALUE] then this has not been initialized.
     *
     * This must only be accessed after calling [ensureInitialized].
     */
    private var _line: Int = Int.MIN_VALUE

    override val path: Path?
        get() {
            ensureInitialized()
            return _path
        }

    override val line: Int
        get() {
            ensureInitialized()
            return _line
        }

    override val baselineKey: BaselineKey
        get() = getBaselineKey(psiElement)

    /**
     * Make sure that this is initialized, if it is not then compute the [path] and [line] from the
     * [psiElement].
     */
    private fun ensureInitialized() {
        if (_line != Int.MIN_VALUE) {
            return
        }

        // Record that this has been initialized. This will make sure that whatever happens this
        // method does not get run multiple times on a single instance.
        _line = 0

        val psiFile = psiElement.containingFile ?: return
        val virtualFile = psiFile.virtualFile ?: return

        // Record the path.
        _path =
            try {
                virtualFile.toNioPath().toAbsolutePath()
            } catch (e: UnsupportedOperationException) {
                return
            }

        // Unwrap UAST for accurate Kotlin line numbers (UAST synthesizes text offsets sometimes)
        val sourceElement = (psiElement as? UElement)?.sourcePsi ?: psiElement

        // Skip doc comments for classes, methods and fields by pointing at the line where the
        // element's name is or falling back to the first line of its modifier list (which may
        // include annotations) or lastly to the start of the element itself
        val rangeElement =
            (sourceElement as? PsiNameIdentifierOwner)?.nameIdentifier
                ?: (sourceElement as? KtModifierListOwner)?.modifierList
                    ?: (sourceElement as? PsiModifierListOwner)?.modifierList ?: sourceElement

        val range = getTextRange(rangeElement)

        // Update the line number.
        _line =
            if (range == null) {
                -1 // No source offsets, use invalid line number
            } else {
                getLineNumber(psiFile.text, range.startOffset) + 1
            }
    }

    companion object {
        /**
         * Compute a [FileLocation] from a [PsiElement]
         *
         * @param element the optional element from which the path, line and [BaselineKey] will be
         *   computed.
         */
        fun fromPsiElement(element: PsiElement?): FileLocation {
            return element?.let { PsiFileLocation(it) } ?: FileLocation.UNKNOWN
        }

        private fun getTextRange(element: PsiElement): TextRange? {
            var range: TextRange? = null

            if (element is UClass) {
                range = element.sourcePsi?.textRange
            } else if (element is PsiCompiledElement) {
                if (element is LightElement) {
                    range = (element as PsiElement).textRange
                }
                if (range == null || TextRange.EMPTY_RANGE == range) {
                    return null
                }
            } else {
                range = element.textRange
            }

            return range
        }

        /** Returns the 0-based line number of character position <offset> in <text> */
        private fun getLineNumber(text: String, offset: Int): Int {
            var line = 0
            var curr = 0
            val target = offset.coerceAtMost(text.length)
            while (curr < target) {
                if (text[curr++] == '\n') {
                    line++
                }
            }
            return line
        }

        internal fun getBaselineKey(element: PsiElement?): BaselineKey {
            element ?: return BaselineKey.UNKNOWN
            return when (element) {
                is PsiFile -> {
                    val virtualFile = element.virtualFile
                    val file = VfsUtilCore.virtualToIoFile(virtualFile)
                    BaselineKey.forPath(file.toPath())
                }
                else -> {
                    val elementId = getElementId(element)
                    BaselineKey.forElementId(elementId)
                }
            }
        }

        private fun getElementId(element: PsiElement): String {
            return when (element) {
                is PsiClass -> element.qualifiedName ?: element.name ?: "?"
                is KtClass -> element.fqName?.asString() ?: element.name ?: "?"
                is PsiMethod -> {
                    val containingClass = element.containingClass
                    val name = element.name
                    val parameterList =
                        "(" +
                            element.parameterList.parameters.joinToString {
                                it.type.canonicalText
                            } +
                            ")"
                    if (containingClass != null) {
                        getElementId(containingClass) + "#" + name + parameterList
                    } else {
                        name + parameterList
                    }
                }
                is PsiField -> {
                    val containingClass = element.containingClass
                    val name = element.name
                    if (containingClass != null) {
                        getElementId(containingClass) + "#" + name
                    } else {
                        name
                    }
                }
                is KtProperty -> {
                    val containingClass = element.containingClass()
                    val name = element.nameAsSafeName.asString()
                    if (containingClass != null) {
                        getElementId(containingClass) + "#" + name
                    } else {
                        name
                    }
                }
                is PsiPackage -> element.qualifiedName
                is PsiParameter -> {
                    val method = element.declarationScope.parent
                    if (method is PsiMethod) {
                        getElementId(method) + " parameter #" + element.parameterIndex()
                    } else {
                        "?"
                    }
                }
                else -> element.toString()
            }
        }
    }
}

fun Reporter.report(id: Issues.Issue, element: PsiElement?, message: String): Boolean {
    val location = PsiFileLocation.fromPsiElement(element)
    return report(id, null, message, location)
}
