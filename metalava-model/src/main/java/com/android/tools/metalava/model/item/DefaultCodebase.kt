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

import com.android.tools.metalava.model.AbstractCodebase
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.CLASS_ESTIMATE
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.util.HashMap

/**
 * Base class of [Codebase]s for the models that do not incorporate their underlying model, if any,
 * into their [Item] implementations.
 */
open class DefaultCodebase(
    location: File,
    description: String,
    preFiltered: Boolean,
    annotationManager: AnnotationManager,
    trustedApi: Boolean,
    supportsDocumentation: Boolean,
    assemblerFactory: CodebaseAssemblerFactory,
) :
    AbstractCodebase(
        location,
        description,
        preFiltered,
        annotationManager,
        trustedApi,
        supportsDocumentation,
    ) {

    /**
     * Create a [CodebaseAssembler] appropriate for this [Codebase].
     *
     * The leaking of `this` is safe as the implementations do not access anything that has not been
     * initialized.
     */
    val assembler = assemblerFactory(@Suppress("LeakingThis") this)

    override val reporter: Reporter
        get() = unsupported("reporter is not available")

    /** Tracks [DefaultPackageItem] use in this [Codebase]. */
    val packageTracker = PackageTracker { packageName, packageDoc ->
        val documentationFactory = packageDoc.commentFactory ?: "".toItemDocumentationFactory()
        assembler.itemFactory.createPackageItem(
            packageDoc.fileLocation,
            packageDoc.modifiers ?: DefaultModifierList.createPublic(),
            documentationFactory,
            packageName,
            packageDoc.overview,
        )
    }

    final override fun getPackages() = packageTracker.getPackages()

    final override fun size() = packageTracker.size

    final override fun findPackage(pkgName: String) = packageTracker.findPackage(pkgName)

    /** Add the package to this. */
    fun addPackage(packageItem: DefaultPackageItem) {
        packageTracker.addPackage(packageItem)
    }

    /**
     * Map from fully qualified name to [DefaultClassItem] for every class created by this.
     *
     * Classes are added via [registerClass] while initialising the codebase.
     */
    protected val allClassesByName = HashMap<String, DefaultClassItem>(CLASS_ESTIMATE)

    /** Find a class created by this [Codebase]. */
    fun findClassInCodebase(className: String) = allClassesByName[className]

    /**
     * A list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    private val topLevelClassesFromSource: MutableList<ClassItem> = ArrayList(CLASS_ESTIMATE)

    override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    fun addTopLevelClassFromSource(classItem: ClassItem) {
        topLevelClassesFromSource.add(classItem)
    }

    /**
     * Look for classes in this [Codebase].
     *
     * This is left open so that subclasses can extend this to look for classes from elsewhere, e.g.
     * classes provided by a [ClassResolver] which would come from a separate [Codebase].
     */
    override fun findClass(className: String): ClassItem? = findClassInCodebase(className)

    /** Register [DefaultClassItem] with this [Codebase]. */
    internal fun registerClass(classItem: DefaultClassItem) {
        val qualifiedName = classItem.qualifiedName()
        val existing = allClassesByName.put(qualifiedName, classItem)
        if (existing != null) {
            error(
                "Attempted to register $qualifiedName twice; once from ${existing.fileLocation.path} and this one from ${classItem.fileLocation.path}"
            )
        }

        addClass(classItem)

        // Perform any subclass specific processing on the newly registered class.
        newClassRegistered(classItem)
    }

    /** Overrideable hook, called from [registerClass] for each new [DefaultClassItem]. */
    open fun newClassRegistered(classItem: DefaultClassItem) {}

    /**
     * Looks for an existing class in this [Codebase] and if that cannot be found then delegate to
     * the [assembler] to see if it can create a class from the underlying model.
     */
    override fun resolveClass(className: String) =
        findClass(className) ?: assembler.createClassFromUnderlyingModel(className)

    final override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        return DefaultAnnotationItem.create(this, source)
    }
}
