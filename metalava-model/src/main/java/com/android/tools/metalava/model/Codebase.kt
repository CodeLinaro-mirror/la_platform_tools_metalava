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

import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.ThrowingReporter
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

    /** Configuration of this [Codebase], typically comes from the command line. */
    val config: Config

    /** [Reporter] to which any issues found within the [Codebase] can be reported. */
    val reporter: Reporter

    /** The manager of annotations within this codebase. */
    val annotationManager: AnnotationManager

    /** The [ApiSurfaces] that will be tracked in this [Codebase]. */
    val apiSurfaces: ApiSurfaces

    /** The packages in the codebase (may include packages that are not included in the API) */
    fun getPackages(): PackageList

    /** The rough size of the codebase (package count) */
    fun size(): Int

    /**
     * Returns a list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    fun getTopLevelClassesFromSource(): List<ClassItem>

    /**
     * Return `true` if this whole [Codebase] was created from the class path, i.e. not from
     * sources.
     */
    fun isFromClassPath(): Boolean = false

    /**
     * Freeze all the classes loaded from sources, along with their super classes.
     *
     * This does not prevent adding new classes and does automatically freeze classes added after
     * this is called.
     */
    fun freezeClasses()

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

    /** Returns a typealias identified by fully qualified name, if in the codebase */
    fun findTypeAlias(typeAliasName: String): TypeAliasItem?

    /** Returns true if this codebase supports documentation. */
    fun supportsDocumentation(): Boolean

    /**
     * Returns true if this codebase corresponds to an already trusted API (e.g. is read in from
     * something like an existing signature file); in that case, signature checks etc will not be
     * performed.
     */
    fun trustedApi(): Boolean

    fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    /**
     * Creates an annotation item for the given (fully qualified) Java source.
     *
     * Returns `null` if the source contains an annotation that is not recognized by Metalava.
     */
    fun createAnnotation(
        source: String,
        context: Item? = null,
    ): AnnotationItem?

    /** Reports that the given operation is unsupported for this codebase type */
    fun unsupported(desc: String? = null): Nothing {
        error(
            desc
                ?: "This operation is not available on this type of codebase (${javaClass.simpleName})"
        )
    }

    /** Discards this model */
    fun dispose()

    /** If true, this codebase has already been filtered */
    val preFiltered: Boolean

    fun isEmpty(): Boolean {
        return getPackages().packages.isEmpty()
    }

    /** Indicates whether this [Codebase] contains a reverted item, or not. */
    val containsRevertedItem: Boolean

    /** Record that this [Codebase] contains at least one reverted item. */
    fun markContainsRevertedItem()

    /**
     * Contains configuration for [Codebase] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        /** Determines how annotations will affect the [Codebase]. */
        val annotationManager: AnnotationManager,

        /** The [ApiSurfaces] that will be tracked in the [Codebase]. */
        val apiSurfaces: ApiSurfaces = ApiSurfaces.DEFAULT,

        /** The reporter to use for issues found during processing of the [Codebase]. */
        val reporter: Reporter = ThrowingReporter.INSTANCE,
    ) {
        companion object {
            /**
             * A [Config] containing a [noOpAnnotationManager], [ApiSurfaces.DEFAULT] and no
             * reporter.
             */
            val NOOP =
                Config(
                    annotationManager = noOpAnnotationManager,
                )
        }
    }
}
