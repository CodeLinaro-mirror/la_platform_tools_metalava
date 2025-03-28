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
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.TypeAliasItem
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.util.HashMap

private const val CLASS_ESTIMATE = 15000

/** Base class of [Codebase]s. */
open class DefaultCodebase(
    final override var location: File,
    description: String,
    override val preFiltered: Boolean,
    final override val config: Codebase.Config,
    private val trustedApi: Boolean,
    private val supportsDocumentation: Boolean,
    val assembler: CodebaseAssembler,
) : Codebase {

    final override val annotationManager: AnnotationManager = config.annotationManager

    final override val apiSurfaces: ApiSurfaces = config.apiSurfaces

    final override var description: String = description
        private set

    final override fun trustedApi() = trustedApi

    final override fun supportsDocumentation() = supportsDocumentation

    final override fun toString() = description

    override fun dispose() {
        description += " [disposed]"
    }

    final override var containsRevertedItem: Boolean = false
        private set

    override fun markContainsRevertedItem() {
        containsRevertedItem = true
    }

    override val reporter: Reporter = config.reporter

    /** Tracks [DefaultPackageItem] use in this [Codebase]. */
    val packageTracker = PackageTracker(assembler::createPackageItem)

    final override fun getPackages() = packageTracker.getPackages()

    final override fun size() = packageTracker.size

    final override fun findPackage(pkgName: String) = packageTracker.findPackage(pkgName)

    fun findOrCreatePackage(
        packageName: String,
        packageDocs: PackageDocs = PackageDocs.EMPTY,
    ) = packageTracker.findOrCreatePackage(packageName, packageDocs)

    /**
     * Map from fully qualified name to [DefaultClassItem] for every class created by this.
     *
     * Classes are added via [registerClass] while initialising the codebase.
     */
    private val allClassesByName = HashMap<String, DefaultClassItem>(CLASS_ESTIMATE)

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

    override fun freezeClasses() {
        for (classItem in topLevelClassesFromSource) {
            classItem.freeze()
        }
    }

    /** Tracks all known type aliases in the codebase by qualified name. */
    private val allTypeAliasesByName = HashMap<String, DefaultTypeAliasItem>()

    override fun findTypeAlias(typeAliasName: String): TypeAliasItem? {
        return allTypeAliasesByName[typeAliasName]
    }

    /**
     * Adds the [typeAlias] to the [Codebase], throwing an error if there is already a type alias
     * with the same qualified name.
     */
    internal fun addTypeAlias(typeAlias: DefaultTypeAliasItem) {
        if (typeAlias.qualifiedName in allTypeAliasesByName) {
            error("Duplicate typealias ${typeAlias.qualifiedName}")
        }
        allTypeAliasesByName[typeAlias.qualifiedName] = typeAlias
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

    /**
     * Register the class by name, return `true` if the class was registered and `false` if it was
     * not, i.e. because it is a duplicate.
     */
    fun registerClass(classItem: DefaultClassItem): Boolean {
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

    override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        return DefaultAnnotationItem.create(this, source)
    }
}
