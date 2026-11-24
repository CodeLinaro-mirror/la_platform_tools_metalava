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
     * [Environment.classPath].
     *
     * If an implementation supports this it must provide [Capability.CLASS_PATH_RESOLVER].
     */
    fun getClassPathResolver(): ClassPathResolver

    /**
     * Parse the sources in the [Environment] into a [Codebase].
     *
     * @param description the description to use for [Codebase.description].
     * @param apiPackages an optional [PackageFilter] that if specified will result in only
     *   including the source classes that match the filter in the
     *   [Codebase.getTopLevelClassesFromSource] list.
     * @param compiledSourceJar a jar file containing the compiled version of the sources. Used to
     *   add the compiled JVM forms of Kotlin source APIs. If the implementation supports this then
     *   it must provide [Capability.JAR_WITH_SOURCES]. Only supported by the PSI model.
     */
    fun parseSources(
        description: String,
        apiPackages: PackageFilter? = null,
        compiledSourceJar: File? = null,
    ): Codebase?

    /**
     * Parse the [javaStubFiles] against the [Environment] into a [Codebase].
     *
     * @param javaStubFiles the list of Java stub files to parse.
     * @param apiPackages an optional [PackageFilter] that if specified will result in only
     *   including the source classes that match the filter in the
     *   [Codebase.getTopLevelClassesFromSource] list.
     */
    fun parseJavaStubs(
        javaStubFiles: List<File>,
        apiPackages: PackageFilter? = null,
    ): Codebase?

    /**
     * Creates a multiplatform codebase based on the [Environment.projectDescription] file, which is
     * a lint project model that can describe project structures in detail.
     *
     * Only supported by the PSI model.
     */
    fun createMultiplatformCodebase(): MultiplatformCodebase
}
