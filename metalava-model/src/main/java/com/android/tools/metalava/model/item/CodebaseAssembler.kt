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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers

/**
 * A factory that will create a [DefaultCodebase] for a specific [CodebaseAssembler].
 *
 * An implementation of this must not try and access any [CodebaseAssembler] functions as it will
 * not be fully initialized at the time this is called.
 */
typealias DefaultCodebaseFactory = (CodebaseAssembler) -> DefaultCodebase

/**
 * A [CodebaseAssembler] is responsible for providing a [Codebase] with access to classes which are
 * present in the underlying model but not yet present in the [Codebase].
 *
 * Although, the interface is simple, the implementation will do a vast amount of the work of
 * mapping an underlying model's representation of the API to a [Codebase], if not all of it.
 */
interface CodebaseAssembler {
    /**
     * Create a [PackageItem] for package called [packageName], with additional information from
     * [packageDoc] whose containing package, if any, is [containingPackage].
     */
    fun createPackageItem(
        packageName: String,
        annotations: List<AnnotationItem>,
        packageDoc: PackageDoc,
        containingPackage: PackageItem?,
    ): PackageItem

    /**
     * Create package annotations for [packageName].
     *
     * This will be used to create annotations for packages whether they are created by
     * [createPackageItem] or [createPackageFromUnderlyingModel]. It ensures consistent behavior for
     * packages from source `package-info.java` files and binary `package-info.class` files.
     */
    fun createPackageAnnotations(packageName: String): List<AnnotationItem> = emptyList()

    /**
     * A [PackageItem] with [qualifiedName] could not be found in the associated [Codebase] so look
     * in the underlying model's set of packages to see if one could be found there. If it could
     * then create a [PackageItem] representation of it and return that, otherwise return null.
     */
    fun createPackageFromUnderlyingModel(qualifiedName: String): PackageItem?

    /**
     * A [ClassItem] with [qualifiedName] could not be found in the associated [Codebase] so look in
     * the underlying model's set of classes to see if one could be found there. If it could then
     * create a [ClassItem] representation of it and return that, otherwise return null.
     */
    fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem?

    /**
     * Overrideable hook, called from [DefaultCodebase.registerClass] for each new
     * [DefaultClassItem].
     */
    fun newClassRegistered(classItem: DefaultClassItem) {}
}

/**
 * Base [CodebaseAssembler] for use by models that do not use model specific implementations of the
 * [Item] classes.
 */
abstract class DefaultCodebaseAssembler : CodebaseAssembler {

    /** Factory for creating appropriate [Item] subclasses for the [Codebase] this is assembling. */
    abstract val itemFactory: DefaultItemFactory

    override fun createPackageItem(
        packageName: String,
        annotations: List<AnnotationItem>,
        packageDoc: PackageDoc,
        containingPackage: PackageItem?,
    ): PackageItem {
        val documentationFactory = packageDoc.commentFactory ?: emptyPackageDocumentationFactory()
        val modifiers =
            if (annotations.isEmpty()) DEFAULT_PACKAGE_MODIFIERS
            else createImmutableModifiers(VisibilityLevel.PUBLIC, annotations)
        return itemFactory.createPackageItem(
            packageDoc.fileLocation,
            modifiers,
            documentationFactory,
            packageName,
            containingPackage,
            packageDoc.overview,
        )
    }

    /**
     * Get an [ItemDocumentationFactory] for empty package documentation.
     *
     * This will be called for packages that have no `package.html` or `package-info.java`.
     */
    protected abstract fun emptyPackageDocumentationFactory(): ItemDocumentationFactory

    companion object {
        private val DEFAULT_PACKAGE_MODIFIERS = createImmutableModifiers(VisibilityLevel.PUBLIC)
    }
}
