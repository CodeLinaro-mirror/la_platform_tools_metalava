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

package com.android.tools.metalava.model

import java.io.File

/**
 * Represents a complete unit of code -- typically in the form of a set of source trees, but also
 * potentially backed by .jar files or even signature files
 */
interface Codebase {
    /** Description of what this codebase is (useful during debugging) */
    val description: String

    /**
     * The location of the API. Could point to a signature file, or a directory root for source
     * files, or a jar file, etc.
     */
    val location: File

    /** The manager of annotations within this codebase. */
    val annotationManager: AnnotationManager

    /** The packages in the codebase (may include packages that are not included in the API) */
    fun getPackages(): PackageList

    /** The rough size of the codebase (package count) */
    fun size(): Int

    /** Returns a class identified by fully qualified name, if in the codebase */
    fun findClass(className: String): ClassItem?

    /**
     * Resolve a class identified by fully qualified name.
     *
     * This does everything it can to retrieve a suitable class, e.g. searching classpath (if
     * available). That may include fabricating the [ClassItem] from nothing in the case of models
     * that work with a partial set of classes (like text model).
     */
    fun resolveClass(className: String): ClassItem?

    /** Returns a package identified by fully qualified name, if in the codebase */
    fun findPackage(pkgName: String): PackageItem?

    /** Returns true if this codebase supports documentation. */
    fun supportsDocumentation(): Boolean

    /**
     * Returns true if this codebase corresponds to an already trusted API (e.g. is read in from
     * something like an existing signature file); in that case, signature checks etc will not be
     * performed.
     */
    fun trustedApi(): Boolean

    fun accept(visitor: ItemVisitor) {
        getPackages().accept(visitor)
    }

    /** Creates an annotation item for the given (fully qualified) Java source */
    fun createAnnotation(
        source: String,
        context: Item? = null,
    ): AnnotationItem

    /** Reports that the given operation is unsupported for this codebase type */
    fun unsupported(desc: String? = null): Nothing

    /** Discards this model */
    fun dispose()

    /** If true, this codebase has already been filtered */
    val preFiltered: Boolean

    fun isEmpty(): Boolean {
        return getPackages().packages.isEmpty()
    }
}

sealed class MinSdkVersion

data class SetMinSdkVersion(val value: Int) : MinSdkVersion()

object UnsetMinSdkVersion : MinSdkVersion()

const val CLASS_ESTIMATE = 15000

abstract class DefaultCodebase(
    final override var location: File,
    final override var description: String,
    final override val preFiltered: Boolean,
    final override val annotationManager: AnnotationManager,
) : Codebase {

    override fun unsupported(desc: String?): Nothing {
        error(
            desc
                ?: "This operation is not available on this type of codebase (${this.javaClass.simpleName})"
        )
    }

    override fun dispose() {
        description += " [disposed]"
    }

    /** A list of all the classes. Primarily used by [iterateAllClasses]. */
    private val allClasses: MutableList<ClassItem> = ArrayList(CLASS_ESTIMATE)

    /**
     * Add a [ClassItem].
     *
     * It is the responsibility of the caller to ensure that each [classItem] is not added more than
     * once.
     */
    protected fun addClass(classItem: ClassItem) {
        allClasses.add(classItem)
    }

    /**
     * Iterate over all the [ClassItem]s in the [Codebase].
     *
     * If additional classes are added to the [Codebase] by [body], e.g. by resolving a
     * `ClassTypeItem` to a class on the classpath that was not previously loaded, then they will be
     * included in the iteration.
     */
    fun iterateAllClasses(body: (ClassItem) -> Unit) {
        // Iterate by index not using an iterator to avoid `ConcurrentModificationException`s.
        // Limit the first round of iteration to just the classes that were present at the start.
        var start = 0
        var end = allClasses.size
        do {
            // Iterate over the classes in the selected range, invoking [body] pn each.
            for (i in start until end) {
                val classItem = allClasses[i]
                body(classItem)
            }

            // Move the range to include all the classes, if any, added during the previous round.
            start = end
            end = allClasses.size

            // Repeat until no new classes were added.
        } while (start < end)
    }

    /** Iterate over all the classes resolving their super class and interface types. */
    fun resolveSuperTypes() {
        iterateAllClasses { classItem ->
            classItem.superClass()
            for (interfaceType in classItem.interfaceTypes()) {
                interfaceType.asClass()
            }
        }
    }
}
