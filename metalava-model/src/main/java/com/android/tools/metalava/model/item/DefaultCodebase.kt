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
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.util.HashMap

private const val PACKAGE_ESTIMATE = 500

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
    open val assembler = assemblerFactory(@Suppress("LeakingThis") this)

    override val reporter: Reporter
        get() = unsupported("reporter is not available")

    /** Map from package name to [DefaultPackageItem] of all packages in this. */
    private val packagesByName = HashMap<String, DefaultPackageItem>(PACKAGE_ESTIMATE)

    final override fun getPackages(): PackageList {
        val list = packagesByName.values.toMutableList()
        list.sortWith(PackageItem.comparator)
        return PackageList(this, list)
    }

    final override fun size(): Int {
        return packagesByName.size
    }

    final override fun findPackage(pkgName: String): DefaultPackageItem? {
        return packagesByName[pkgName]
    }

    /** Add the package to this. */
    fun addPackage(packageItem: DefaultPackageItem) {
        packagesByName[packageItem.qualifiedName()] = packageItem
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
