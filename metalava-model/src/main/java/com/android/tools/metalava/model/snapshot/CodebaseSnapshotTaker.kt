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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultPackageItem

/** Constructs a [Codebase] by taking a snapshot of another [Codebase] that is being visited. */
class CodebaseSnapshotTaker : DelegatedVisitor {
    /**
     * The [Codebase] that is under construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var codebase: DefaultCodebase

    /** Takes a snapshot of [TypeItem]s. */
    private val typeSnapshotTaker by lazy(LazyThreadSafetyMode.NONE) { TypeSnapshotTaker(codebase) }

    /**
     * The current [PackageItem], set in [visitPackage], cleared in [afterVisitPackage], relies on
     * the [PackageItem]s being visited as a flat list, not a package hierarchy.
     */
    private var currentPackage: DefaultPackageItem? = null

    /**
     * The current [ClassItem], that forms a stack through the [ClassItem.containingClass].
     *
     * Set (pushed on the stack) in [visitClass]. Reset (popped off the stack) in [afterVisitClass].
     */
    private var currentClass: DefaultClassItem? = null

    /** Take a snapshot of this [ModifierList] for [codebase]. */
    private fun ModifierList.snapshot() = (this as DefaultModifierList).snapshot(codebase)

    override fun visitCodebase(codebase: Codebase) {
        this.codebase =
            DefaultCodebase(
                location = codebase.location,
                description = "snapshot of ${codebase.description}",
                preFiltered = true,
                annotationManager = codebase.annotationManager,
                trustedApi = true,
                // Supports documentation if the copied codebase does.
                supportsDocumentation = codebase.supportsDocumentation(),
            )
    }

    override fun visitPackage(pkg: PackageItem) {
        val newPackage =
            DefaultPackageItem(
                codebase = codebase,
                fileLocation = pkg.fileLocation,
                itemLanguage = pkg.itemLanguage,
                modifiers = pkg.modifiers.snapshot(),
                documentationFactory = pkg.documentation::snapshot,
                variantSelectorsFactory = pkg.variantSelectors::duplicate,
                qualifiedName = pkg.qualifiedName(),
            )
        codebase.addPackage(newPackage)
        currentPackage = newPackage
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        currentPackage = null
    }

    override fun visitClass(cls: ClassItem) {
        val containingClass = currentClass
        val containingPackage = currentPackage!!
        val newClass =
            DefaultClassItem(
                codebase = codebase,
                fileLocation = cls.fileLocation,
                itemLanguage = cls.itemLanguage,
                modifiers = cls.modifiers.snapshot(),
                documentationFactory = cls.documentation::snapshot,
                variantSelectorsFactory = cls.variantSelectors::duplicate,
                source = null,
                classKind = cls.classKind,
                containingClass = currentClass,
                containingPackage = containingPackage,
                qualifiedName = cls.qualifiedName(),
                simpleName = cls.simpleName(),
                fullName = cls.fullName(),
                typeParameterList = TypeParameterList.NONE,
            )
        if (containingClass == null) {
            currentPackage!!.addTopClass(newClass)
        } else {
            containingClass.addNestedClass(newClass)
        }

        // Snapshot the super class type, if any.
        cls.superClassType()?.let { superClassType ->
            newClass.setSuperClassType(superClassType.transform(typeSnapshotTaker))
        }

        // Snapshot the interface types, if any.
        newClass.setInterfaceTypes(cls.interfaceTypes().map { it.transform(typeSnapshotTaker) })

        codebase.registerClass(newClass)
        currentClass = newClass
    }

    override fun afterVisitClass(cls: ClassItem) {
        currentClass = currentClass?.containingClass() as? DefaultClassItem
    }

    companion object {
        /** Take a snapshot of [codebase]. */
        fun takeSnapshot(codebase: Codebase): Codebase {
            // Create a snapshot taker that will construct the snapshot.
            val taker = CodebaseSnapshotTaker()

            // Wrap it in a visitor and visit the codebase.
            val visitor = NonFilteringDelegatingVisitor(taker)
            codebase.accept(visitor)

            // Return the constructed snapshot.
            return taker.codebase
        }
    }
}
