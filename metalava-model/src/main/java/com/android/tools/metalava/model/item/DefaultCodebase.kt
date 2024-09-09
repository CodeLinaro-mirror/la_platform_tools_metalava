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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MutableCodebase
import com.android.tools.metalava.reporter.Issues
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
    reporter: Reporter? = null,
    val assembler: CodebaseAssembler,
) :
    AbstractCodebase(
        location,
        description,
        preFiltered,
        annotationManager,
        trustedApi,
        supportsDocumentation,
    ),
    MutableCodebase {

    private val optionalReporter = reporter

    override val reporter: Reporter
        get() = optionalReporter ?: unsupported("reporter is not available")

    /** Tracks [DefaultPackageItem] use in this [Codebase]. */
    val packageTracker = PackageTracker(assembler::createPackageItem)

    final override fun getPackages() = packageTracker.getPackages()

    final override fun size() = packageTracker.size

    final override fun findPackage(pkgName: String) = packageTracker.findPackage(pkgName)

    fun findOrCreatePackage(
        packageName: String,
        packageDocs: PackageDocs = PackageDocs.EMPTY,
    ) = packageTracker.findOrCreatePackage(packageName, packageDocs)

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

    final override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    fun addTopLevelClassFromSource(classItem: ClassItem) {
        topLevelClassesFromSource.add(classItem)
    }

    /**
     * Look for classes in this [Codebase].
     *
     * A class can be added to this [Codebase] in two ways:
     * * Created specifically for this [Codebase], i.e. its [ClassItem.codebase] is this. That can
     *   happen during initialization or because [CodebaseAssembler.createClassFromUnderlyingModel]
     *   creates a [ClassItem] in this [Codebase].
     * * Created by another [Codebase] and returned by
     *   [CodebaseAssembler.createClassFromUnderlyingModel], i.e. its [ClassItem.codebase] is NOT
     *   this.
     */
    final override fun findClass(className: String): ClassItem? =
        findClassInCodebase(className) ?: externalClassesByName[className]

    /** Register [DefaultClassItem] with this [Codebase]. */
    final override fun registerClass(classItem: DefaultClassItem): Boolean {
        // Check for duplicates, ignore the class if it is a duplicate.
        val qualifiedName = classItem.qualifiedName()
        val existing = allClassesByName[qualifiedName]
        if (existing != null) {
            reporter.report(
                Issues.DUPLICATE_SOURCE_CLASS,
                classItem,
                "Attempted to register $qualifiedName twice; once from ${existing.fileLocation.path} and this one from ${classItem.fileLocation.path}"
            )
            // The class was not registered.
            return false
        }

        // Register it by name.
        allClassesByName[qualifiedName] = classItem

        // Perform any subclass specific processing on the newly registered class.
        assembler.newClassRegistered(classItem)

        // The class was registered.
        return true
    }

    /** Map from name to an external class that was registered using [] */
    private val externalClassesByName = mutableMapOf<String, ClassItem>()

    /**
     * Looks for an existing class in this [Codebase] and if that cannot be found then delegate to
     * the [assembler] to see if it can create a class from the underlying model.
     */
    final override fun resolveClass(className: String): ClassItem? {
        findClass(className)?.let {
            return it
        }
        val created = assembler.createClassFromUnderlyingModel(className) ?: return null
        // If the returned class was not created as part of this Codebase then register it as an
        // external class so that findClass(...) will find it next time.
        if (created.codebase !== this) {
            // Register as an external class.
            externalClassesByName[className] = created
        }
        return created
    }

    open override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        return DefaultAnnotationItem.create(this, source)
    }
}
