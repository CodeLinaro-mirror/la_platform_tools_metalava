/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.findClosestEnclosingNonEmptyPackage
import com.intellij.psi.PsiPackage

class PsiPackageItem
internal constructor(
    codebase: PsiBasedCodebase,
    private val psiPackage: PsiPackage,
    private val qualifiedName: String,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    override val overviewDocumentation: String?,
    /** True if this package is from the classpath (dependencies). Exposed in [isFromClassPath]. */
    private val fromClassPath: Boolean
) :
    PsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiPackage
    ),
    PackageItem {

    // Note - top level classes only
    private val classes: MutableList<ClassItem> = mutableListOf()

    override fun topLevelClasses(): List<ClassItem> =
        // Return a copy to avoid a ConcurrentModificationException.
        classes.toList()

    override fun containingClass(): ClassItem? = null

    override fun psi() = psiPackage

    lateinit var containingPackageField: PackageItem

    override fun containingPackage(): PackageItem? {
        return if (qualifiedName.isEmpty()) null
        else {
            if (!::containingPackageField.isInitialized) {
                containingPackageField = codebase.findClosestEnclosingNonEmptyPackage(qualifiedName)
            }
            containingPackageField
        }
    }

    fun addClass(cls: PsiClassItem) {
        if (!cls.isTopLevelClass()) {
            // TODO: Stash in a list somewhere to make allClasses() faster?
            return
        }

        /*
        // Temp debugging:
        val q = cls.qualifiedName()
        for (c in classes) {
            if (q == c.qualifiedName()) {
                assert(false, { "Unexpectedly found class $q already listed in $this" })
                return
            }
        }
        */

        classes.add(cls)
        cls.containingPackage = this
    }

    fun addClasses(classList: List<PsiClassItem>) {
        for (cls in classList) {
            addClass(cls)
        }
    }

    override fun qualifiedName(): String = qualifiedName

    override fun isFromClassPath(): Boolean = fromClassPath

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiPackage: PsiPackage,
            extraDocs: String?,
            overviewHtml: String?,
            fromClassPath: Boolean,
        ): PsiPackageItem {
            val commentText = javadocAsItemDocumentation(psiPackage, codebase, extraDocs)
            val modifiers = modifiers(codebase, psiPackage, commentText)
            if (modifiers.isPackagePrivate()) {
                // packages are always public (if not hidden explicitly with private)
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            }
            val qualifiedName = psiPackage.qualifiedName

            val pkg =
                PsiPackageItem(
                    codebase = codebase,
                    psiPackage = psiPackage,
                    qualifiedName = qualifiedName,
                    documentation = commentText,
                    overviewDocumentation = overviewHtml,
                    modifiers = modifiers,
                    fromClassPath = fromClassPath
                )
            return pkg
        }
    }
}
