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

package com.android.tools.metalava.model.source

import androidx.tracing.Tracer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/**
 * Encapsulates the prepared, surface-independent state (such as extracted source roots, indexed
 * source path, and bound classpath) for a set of sources and classpath jars.
 *
 * An [Environment] is created by [EnvironmentManager.createEnvironment] and can be used to create
 * one or more [SourceParser] instances (e.g. for multiple API surfaces with different
 * [Codebase.Config]s) that share the underlying parsed ASTs and bound classpath state.
 */
interface Environment {
    /** The [SourceSet] with extracted source roots. */
    val sourceSet: SourceSet

    /** The absolute classpath jar files. */
    val classPath: List<File>

    /**
     * Optional lint project model file that can describe project structures in detail.
     *
     * Only supported by the PSI model.
     */
    val projectDescription: File?

    /** The Java language level as a string, e.g. 1.8, 17, etc. */
    val javaLanguageLevel: String

    /** The Kotlin language level as a string, e.g. 1.8, etc. */
    val kotlinLanguageLevel: String

    /** The optional path to the JDK home directory. */
    val jdkHome: File?

    /**
     * Create a [SourceParser] bound to this [Environment] that can be used to create [Codebase]
     * related objects using [codebaseConfig].
     */
    fun createSourceParser(codebaseConfig: Codebase.Config): SourceParser
}

/**
 * Base [Environment] implementation that extracts source roots from [rawSourceSet] and normalizes
 * [rawClassPath] to absolute files.
 */
abstract class AbstractEnvironment<M : EnvironmentManager>(
    val environmentManager: M,
    rawSourceSet: SourceSet,
    rawClassPath: List<File>,
    override val projectDescription: File?,
    override val javaLanguageLevel: String,
    override val kotlinLanguageLevel: String,
    override val jdkHome: File?,
    protected val reporter: Reporter,
    protected val tracer: Tracer,
) : Environment {
    final override val sourceSet: SourceSet =
        tracer.trace("extractRoots") { rawSourceSet.extractRoots(reporter) }

    final override val classPath: List<File> = rawClassPath.map { it.absoluteFile }
}
