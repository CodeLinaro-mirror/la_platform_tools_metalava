/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.provider.Capability
import java.io.File

/** Provides support for creating [Codebase] related objects from source files (including jars). */
interface SourceParser {
    /**
     * Get a [ClassPathResolver] instance that will resolve items provided by jars on the
     * [classPath].
     *
     * If an implementation supports this it must provide [Capability.CLASS_PATH_RESOLVER].
     *
     * @param classPath a list of jar [File]s.
     */
    fun getClassPathResolver(classPath: List<File>): ClassPathResolver

    /**
     * Parse a set of sources into a [Codebase].
     *
     * @param inputs the [Inputs].
     */
    fun parseSources(inputs: Inputs): Codebase?

    /** Inputs for [parseSources]. */
    data class Inputs(
        /** The list of source files and root directories. */
        val sourceSet: SourceSet,

        /** The description to use for [Codebase.description]. */
        val description: String,

        /**
         * The possibly empty list of jar files which may provide additional classes referenced by
         * the sources.
         */
        val classPath: List<File>,

        /**
         * An optional [PackageFilter] that if specified will result in only including the source
         * classes that match the filter in the [Codebase.getTopLevelClassesFromSource] list.
         */
        val apiPackages: PackageFilter? = null,

        /**
         * Lint project model that can describe project structures in detail.
         *
         * Only supported by the PSI model.
         */
        val projectDescription: File? = null,

        /**
         * A jar file containing the compiled version of [sourceSet]. Used to add the compiled JVM
         * forms of Kotlin source APIs.
         *
         * If the implementation supports this then it must provide [Capability.JAR_WITH_SOURCES].
         *
         * Only supported by the PSI model.
         */
        val compiledSourceJar: File? = null,

        /** Indicates whether to include Kotlin derived information in the [Codebase]. */
        val includeKotlinInCodebase: Boolean = true,
    )

    /**
     * Load a [Codebase] from a single jar.
     *
     * If an implementation supports this it must provide [Capability.LOAD_JAR].
     *
     * @param apiJar the jar file from which the [Codebase] will be loaded.
     * @param classPath the possibly empty list of jar files which may provide additional classes
     *   referenced by [apiJar].
     */
    fun loadFromJar(apiJar: File, classPath: List<File>): Codebase

    /**
     * Creates a multiplatform codebase based on the [projectDescription] file, which is a lint
     * project model that can describe project structures in detail.
     *
     * Only supported by the PSI model.
     */
    fun createMultiplatformCodebase(projectDescription: File): MultiplatformCodebase
}
