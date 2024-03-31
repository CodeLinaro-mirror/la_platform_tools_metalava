/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.reporter.IssueLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class PsiLocationProvider {

    companion object {
        /**
         * Compute a [IssueLocation] (including [BaselineKey]) from a [PsiElement]
         *
         * @param element the optional element from which the path, line and [BaselineKey] will be
         *   computed.
         * @param overridingBaselineKey the optional [BaselineKey] to use instead of the
         *   [BaselineKey] computed from the element.
         */
        fun elementToIssueLocation(
            element: PsiElement?,
            overridingBaselineKey: BaselineKey? = null
        ): IssueLocation {
            val fileLocation = element?.let { PsiFileLocation(it) } ?: FileLocation.UNKNOWN
            val actualBaselineKey = overridingBaselineKey ?: getBaselineKey(element)
            return IssueLocation(fileLocation, actualBaselineKey)
        }

        private fun getBaselineKey(element: PsiElement?): BaselineKey {
            element ?: return BaselineKey.UNKNOWN
            return when (element) {
                is PsiFile -> {
                    val virtualFile = element.virtualFile
                    val file = VfsUtilCore.virtualToIoFile(virtualFile)
                    BaselineKey.forFile(file)
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
    val location = PsiLocationProvider.elementToIssueLocation(element)
    return report(id, null, message, location)
}
