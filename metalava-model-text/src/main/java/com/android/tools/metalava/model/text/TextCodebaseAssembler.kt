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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.bestGuessAtFullName
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebaseFactory
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.PackageDocs
import java.io.File

internal class TextCodebaseAssembler(
    codebaseFactory: DefaultCodebaseFactory,
    private val classResolver: ClassResolver?,
) : DefaultCodebaseAssembler() {

    internal val codebase = codebaseFactory(this)

    /** Creates [Item] instances for this. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Signature files do not contain information about whether an item was originally
            // created from Java or Kotlin.
            defaultItemLanguage = ItemLanguage.UNKNOWN,
            // Signature files have already been separated by API surface variants, so they can use
            // the same immutable ApiVariantSelectors.
            defaultVariantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        )

    fun initialize() {
        // Make sure that it has a root package.
        codebase.packageTracker.createInitialPackages(PackageDocs.EMPTY)
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String) =
        getOrCreateClass(qualifiedName)

    /**
     * The [StubKind] required for each class which could not be found, defaults to [StubKind.CLASS]
     * if not specified.
     *
     * Specific types, require a specific type of class, e.g. a type used in an `extends` clause of
     * a concrete class requires a concrete class, whereas a type used in an `implements` clause of
     * a concrete class, or an `extends` list of an interface requires an interface.
     *
     * Similarly, an annotation must be an annotation type and extends
     * `java.lang.annotation.Annotation` and a `throws` type that is not a type parameter must be a
     * concrete class that extends `java.lang.Throwable.`
     *
     * This contains information about the type use so that if a stub class is needed a class of the
     * appropriate structure can be fabricated to avoid spurious issues being reported.
     */
    private val requiredStubKindForClass = mutableMapOf<String, StubKind>()

    override fun newClassRegistered(classItem: DefaultClassItem) {
        // A real class exists so a stub will not be created so the hint as to the kind of class
        // that the stubs should be is no longer needed.
        requiredStubKindForClass.remove(classItem.qualifiedName())
    }

    /**
     * Register that the class type requires a specific stub kind.
     *
     * If a concrete class already exists then this does nothing. Otherwise, this registers the
     * [StubKind] for the [ClassTypeItem.qualifiedName], making sure that it does not conflict with
     * any previous requirements.
     */
    fun requireStubKindFor(classTypeItem: ClassTypeItem, stubKind: StubKind) {
        val qualifiedName = classTypeItem.qualifiedName

        // If a real class already exists then a stub will not need to be created.
        if (codebase.findClass(qualifiedName) != null) return

        val existing = requiredStubKindForClass.put(qualifiedName, stubKind)
        if (existing != null && existing != stubKind) {
            error(
                "Mismatching required stub kinds for $qualifiedName, found $existing and $stubKind"
            )
        }
    }

    /**
     * Gets an existing, or creates a new [ClassItem].
     *
     * Tries to find [qualifiedName] in [codebase]. If not found, then if a [classResolver] is
     * provided it will invoke that and return the [ClassItem] it returns if any. Otherwise, it will
     * create an empty stub class of the [StubKind] specified in [requiredStubKindForClass] or
     * [StubKind.CLASS] if no specific [StubKind] was required.
     *
     * Initializes outer classes and packages for the created class as needed.
     *
     * @param qualifiedName the fully qualified name of the class.
     * @param isOuterClassOfClassInThisCodebase if `true` then this is searching for an outer class
     *   of a class in this codebase, in which case this must only search classes in this codebase,
     *   otherwise it can search for external classes too.
     */
    internal fun getOrCreateClass(
        qualifiedName: String,
        isOuterClassOfClassInThisCodebase: Boolean = false,
    ): ClassItem {
        // Check this codebase first, if found then return it.
        codebase.findClass(qualifiedName)?.let {
            return it
        }

        // Only check for external classes if this is not searching for an outer class of a class in
        // this codebase and there is a class resolver that will populate the external classes.
        if (!isOuterClassOfClassInThisCodebase && classResolver != null) {
            // Try and resolve the class, returning it if it was found.
            classResolver.resolveClass(qualifiedName)?.let {
                return it
            }
        }

        val fullName = bestGuessAtFullName(qualifiedName)

        val outerClass =
            if (fullName.contains('.')) {
                // We created a new nested class stub. We need to fully initialize it with outer
                // classes, themselves possibly stubs
                val outerName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
                val outerClass =
                    getOrCreateClass(outerName, isOuterClassOfClassInThisCodebase = true)

                // As outerClass and stubClass are from the same codebase the outerClass must be a
                // DefaultClassItem so cast it to one so that the code below can use
                // DefaultClassItem methods.
                outerClass as DefaultClassItem
            } else {
                null
            }

        // Find/create package
        val pkg =
            if (outerClass == null) {
                val endIndex = qualifiedName.lastIndexOf('.')
                val pkgPath = if (endIndex != -1) qualifiedName.substring(0, endIndex) else ""
                codebase.findOrCreatePackage(pkgPath, emit = false)
            } else {
                outerClass.containingPackage() as DefaultPackageItem
            }

        // Build a stub class of the required kind.
        val requiredStubKind = requiredStubKindForClass.remove(qualifiedName) ?: StubKind.CLASS
        val stubClass =
            StubClassBuilder.build(
                assembler = this,
                qualifiedName = qualifiedName,
                fullName = fullName,
                containingClass = outerClass,
                containingPackage = pkg,
            ) {
                // Apply stub kind specific mutations to the stub class being built.
                requiredStubKind.mutator(this)
            }

        stubClass.emit = false

        return stubClass
    }

    companion object {
        /** Create a [TextCodebaseAssembler]. */
        fun createAssembler(
            location: File,
            description: String,
            annotationManager: AnnotationManager,
            classResolver: ClassResolver?,
        ): TextCodebaseAssembler {
            val assembler =
                TextCodebaseAssembler(
                    codebaseFactory = { assembler ->
                        DefaultCodebase(
                            location = location,
                            description = description,
                            preFiltered = true,
                            annotationManager = annotationManager,
                            trustedApi = true,
                            supportsDocumentation = false,
                            assembler = assembler,
                        )
                    },
                    classResolver = classResolver,
                )
            assembler.initialize()

            return assembler
        }
    }
}
