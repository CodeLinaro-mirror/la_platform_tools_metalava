/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.JavaImport
import com.android.tools.metalava.model.item.AbstractSourceFile
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStaticStatement
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class PsiSourceFile(
    override val codebase: PsiBasedCodebase,
    val file: PsiFile,
) : AbstractSourceFile() {

    override val fileLocation: FileLocation = PsiFileLocation.fromPsiElement(file)

    override fun computeContainingPackageName() = (file as PsiClassOwner).packageName

    override fun getHeaderComments(): String? {
        // https://youtrack.jetbrains.com/issue/KT-22135
        if (file is PsiJavaFile) {
            val pkg = file.packageStatement ?: return null
            return file.text.substring(0, pkg.startOffset)
        } else if (file is KtFile) {
            val pkg = file.packageDirective ?: return null
            return file.text.substring(0, pkg.startOffset)
        }

        return super.getHeaderComments()
    }

    override fun allJavaImports(): List<JavaImport> {
        file as? PsiJavaFile ?: return emptyList()

        val importList = file.importList ?: return emptyList()
        return importList.allImportStatements.mapNotNull { importStatement ->
            importStatement.importReference?.qualifiedName?.let { qualifiedName ->
                JavaImport(
                    qualifiedName = qualifiedName,
                    onDemand = importStatement.isOnDemand,
                    static = importStatement is PsiImportStaticStatement,
                )
            }
        }
    }

    override fun classes(): Sequence<ClassItem> {
        return (file as? PsiClassOwner)
            ?.classes
            ?.asSequence()
            ?.mapNotNull { codebase.findClass(it) }
            .orEmpty()
    }

    override fun toString(): String = "file ${file.virtualFile?.path}"
}
