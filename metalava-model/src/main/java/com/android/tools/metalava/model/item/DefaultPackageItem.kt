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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.reporter.FileLocation

open class DefaultPackageItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    targetLanguages: Set<TargetLanguage>,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val qualifiedName: String,
    val containingPackage: PackageItem?,
    override val overviewDocumentation: ResourceFile?,
) :
    DefaultSelectableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        sourceLanguage = sourceLanguage,
        targetLanguages = targetLanguages,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    PackageItem {

    init {
        // Newly created package's always have `emit = false` as they should only be emitted if they
        // have at least one class that has `emit = true`. That will be updated, if necessary, when
        // adding a class or type alias to the package.
        emit = false

        containingPackage?.addChildPackage(this)
    }

    private val topClasses = mutableListOf<ClassItem>()

    private val childPackages = mutableListOf<PackageItem>()

    final override fun qualifiedName(): String = qualifiedName

    final override fun topLevelClasses(): List<ClassItem> =
        // Return a copy to avoid a ConcurrentModificationException.
        topClasses.toList()

    /** Get the name of [simpleName] relative to this package. */
    private fun packageRelativeName(simpleName: String) =
        if (qualifiedName == "") simpleName else "$qualifiedName.$simpleName"

    override val containingScope: ReferencableNameScope?
        get() =
            // If this is the root package then there is no containing scope. Otherwise, the
            // containing scope is the root package (for resolving the package part of a qualified
            // name). Nested packages do not inherit the scope of their containing package.
            if (containingPackage == null) null else codebase.rootPackage

    /**
     * Resolves [simpleName] relative to this [PackageItem].
     *
     * Implements https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2
     *
     * First, this will check to see if [simpleName] refers to a [ClassItem] contained within this
     * [PackageItem], returning the [ClassItem] if it does.
     *
     * Secondly, if this is the [Codebase.rootPackage] or [isFirstSimpleName] is `false` then this
     * will then check to see if the [simpleName] refers to a child [PackageItem] within this
     * [PackageItem], returning the child [PackageItem] if it does.
     *
     * Otherwise, this will return `null`.
     */
    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        isFirstSimpleName: Boolean
    ): ReferencableItem? {
        // First, check to see if it [simpleName] is a class in this package, returning it if it is.
        val inPackageName = packageRelativeName(simpleName)
        return codebase.resolveClass(inPackageName)
            // Then, if allowed, check to see if it is a sub-package of this one.
            ?: if (!isFirstSimpleName || containingPackage == null)
                codebase.resolvePackage(inPackageName)
            else null
    }

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    final override fun containingPackage(): PackageItem? {
        return containingPackage
    }

    fun addTopClass(classItem: ClassItem) {
        topClasses.add(classItem)
    }

    override fun addChildPackage(pkg: PackageItem) {
        childPackages.add(pkg)
    }

    override fun childPackages(): List<PackageItem> {
        return childPackages.toList()
    }
}
