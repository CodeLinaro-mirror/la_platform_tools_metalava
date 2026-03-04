/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.provider.Capability
import java.io.File

/**
 * Provides support for working with jar files.
 *
 * A test only interface that abstracts away capabilities provided by source parsers.
 */
interface JarSupport {
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
     * Load a [Codebase] from a single jar.
     *
     * If an implementation supports this it must provide [Capability.LOAD_JAR].
     *
     * @param apiJar the jar file from which the [Codebase] will be loaded.
     * @param classPath the possibly empty list of jar files which may provide additional classes
     *   referenced by [apiJar].
     */
    fun loadFromJar(apiJar: File, classPath: List<File>): Codebase
}
