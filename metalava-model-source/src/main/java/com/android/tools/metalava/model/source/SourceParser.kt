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
     * Get a [com.android.tools.metalava.model.ClassPathResolver] instance that will resolve items
     * provided by jars on the [classPath].
     *
     * @param classPath a list of jar [File]s.
     */
    fun getClassPathResolver(classPath: List<File>): ClassPathResolver =
        parseSources(
            sourceSet = SourceSet.empty(),
            description = "Codebase from classpath",
            classPath = classPath,
        ) ?: error("Could not create resolver from $classPath")

    /**
     * Parse a set of sources into a [Codebase].
     *
     * @param sourceSet the list of source files and root directories.
     * @param description the description to use for [Codebase.description].
     * @param classPath the possibly empty list of jar files which may provide additional classes
     *   referenced by the sources.
     * @param apiPackages an optional [PackageFilter] that if specified will result in only
     *   including the source classes that match the filter in the
     *   [Codebase.getTopLevelClassesFromSource] list.
     * @param projectDescription Lint project model that can describe project structures in detail.
     *   Only supported by the PSI model.
     * @param compiledSourceJar A jar file containing the compiled version of [sourceSet]. Used to
     *   add the compiled JVM forms of Kotlin source APIs. Only supported by the PSI model. If the
     *   implementation supports this then it must provide [Capability.JAR_WITH_SOURCES].
     */
    fun parseSources(
        sourceSet: SourceSet,
        description: String,
        classPath: List<File>,
        apiPackages: PackageFilter? = null,
        projectDescription: File? = null,
        compiledSourceJar: File? = null,
    ): Codebase?

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
