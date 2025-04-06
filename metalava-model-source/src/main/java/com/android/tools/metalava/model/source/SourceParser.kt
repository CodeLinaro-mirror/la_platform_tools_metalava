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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import java.io.File

/** Provides support for creating [Codebase] related objects from source files (including jars). */
interface SourceParser {

    /**
     * Get a [ClassResolver] instance that will resolve classes provided by jars on the [classPath].
     *
     * @param classPath a list of jar [File]s.
     */
    fun getClassResolver(classPath: List<File>): ClassResolver

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
     */
    fun parseSources(
        sourceSet: SourceSet,
        description: String,
        classPath: List<File>,
        apiPackages: PackageFilter?,
        projectDescription: File?,
    ): Codebase

    /**
     * Load a [Codebase] from a single jar.
     *
     * @param apiJar the jar file from which the [Codebase] will be loaded.
     * @param classPath the possibly empty list of jar files which may provide additional classes
     *   referenced by [apiJar].
     */
    fun loadFromJar(apiJar: File, classPath: List<File>): Codebase
}
