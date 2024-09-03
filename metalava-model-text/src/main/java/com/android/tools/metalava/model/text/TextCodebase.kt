/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.bestGuessAtFullName
import com.android.tools.metalava.model.item.CodebaseAssemblerFactory
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultPackageItem
import java.io.File
import java.util.HashMap

// Copy of ApiInfo in doclava1 (converted to Kotlin + some cleanup to make it work with metalava's
// data structures.
// (Converted to Kotlin such that I can inherit behavior via interfaces, in particular Codebase.)
internal class TextCodebase(
    location: File,
    annotationManager: AnnotationManager,
    private val classResolver: ClassResolver?,
    assemblerFactory: CodebaseAssemblerFactory = { codebase ->
        TextCodebaseAssembler(codebase as TextCodebase)
    },
) :
    DefaultCodebase(
        location = location,
        description = "Codebase",
        preFiltered = true,
        annotationManager = annotationManager,
        trustedApi = true,
        supportsDocumentation = false,
        assemblerFactory = assemblerFactory
    ) {

    override val assembler
        get() = super.assembler as TextCodebaseAssembler

    init {
        assembler.initialize()
    }

    /**
     * Map from fully qualified class name to a [ClassItem] that has been retrieved from a
     * [ClassResolver], if any.
     */
    private val externalClassesByName = HashMap<String, ClassItem>()

    /**
     * Override to first search within this [Codebase] and then look for classes that have been
     * loaded by a [classResolver].
     */
    override fun findClass(className: String) =
        super.findClass(className) ?: externalClassesByName[className]

    override fun resolveClass(className: String) = getOrCreateClass(className)

    override fun newClassRegistered(classItem: DefaultClassItem) {
        // A real class exists so a stub will not be created so the hint as to the kind of class
        // that the stubs should be is no longer needed.
        requiredStubKindForClass.remove(classItem.qualifiedName())
    }

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
        if (allClassesByName[qualifiedName] != null) return

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
     * Tries to find [qualifiedName] in [allClassesByName]. If not found, then if a [classResolver]
     * is provided it will invoke that and return the [ClassItem] it returns if any. Otherwise, it
     * will create an empty stub class of the [StubKind] specified in [requiredStubKindForClass] or
     * [StubKind.CLASS] if no specific [StubKind] was required.
     *
     * Initializes outer classes and packages for the created class as needed.
     *
     * @param qualifiedName the fully qualified name of the class.
     * @param isOuterClass if `true` then this is searching for an outer class of a class in this
     *   codebase, in which case this must only search classes in this codebase, otherwise it can
     *   search for external classes too.
     */
    fun getOrCreateClass(
        qualifiedName: String,
        isOuterClass: Boolean = false,
    ): ClassItem {
        // Check this codebase first, if found then return it.
        allClassesByName[qualifiedName]?.let { found ->
            return found
        }

        // Only check for external classes if this is not searching for an outer class and there is
        // a class resolver that will populate the external classes.
        if (!isOuterClass && classResolver != null) {
            // Check to see whether the class has already been retrieved from the resolver. If it
            // has then return it.
            externalClassesByName[qualifiedName]?.let { found ->
                return found
            }

            // Else try and resolve the class.
            val classItem = classResolver.resolveClass(qualifiedName)
            if (classItem != null) {
                // Save the class item, so it can be retrieved the next time this is loaded. This is
                // needed because otherwise TextTypeItem.asClass would not work properly.
                externalClassesByName[qualifiedName] = classItem
                return classItem
            }
        }

        val fullName = bestGuessAtFullName(qualifiedName)

        val outerClass =
            if (fullName.contains('.')) {
                // We created a new nested class stub. We need to fully initialize it with outer
                // classes, themselves possibly stubs
                val outerName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
                // Pass classResolver = null, so it only looks in this codebase for the outer class.
                val outerClass = getOrCreateClass(outerName, isOuterClass = true)

                // It makes no sense for a Foo to come from one codebase and Foo.Bar to come from
                // another.
                if (outerClass.codebase != this) {
                    throw IllegalStateException(
                        "Outer class $outerClass is from ${outerClass.codebase} but" +
                            " inner class $qualifiedName is from ${this}"
                    )
                }

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
                findPackage(pkgPath)
                    ?: run {
                        val newPkg =
                            assembler.itemFactory.createPackageItem(qualifiedName = pkgPath)
                        addPackage(newPkg)
                        newPkg.emit = false
                        newPkg
                    }
            } else {
                outerClass.containingPackage() as DefaultPackageItem
            }

        // Build a stub class of the required kind.
        val requiredStubKind = requiredStubKindForClass.remove(qualifiedName) ?: StubKind.CLASS
        val stubClass =
            StubClassBuilder.build(
                codebase = this,
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

    override fun unsupported(desc: String?): Nothing {
        error(desc ?: "Not supported for a signature-file based codebase")
    }
}
