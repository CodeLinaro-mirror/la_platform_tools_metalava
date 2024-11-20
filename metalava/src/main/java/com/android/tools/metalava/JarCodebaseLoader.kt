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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Reporter
import java.io.Closeable
import java.io.File

/** Provides support for loading [Codebase]s from jar files. */
sealed interface JarCodebaseLoader {

    /** Load a [Codebase] from a jar file. */
    fun loadFromJarFile(
        apiJar: File,
        apiAnalyzerConfig: ApiAnalyzer.Config = ApiAnalyzer.Config(),
    ): Codebase

    companion object {
        /** Create an instance fo [JarCodebaseLoader] from an existing [SourceParser]. */
        fun createForSourceParser(
            progressTracker: ProgressTracker,
            reporter: Reporter,
            sourceParser: SourceParser,
        ): JarCodebaseLoader {
            return FromSourceParser(progressTracker, reporter, sourceParser)
        }
    }

    /** A [JarCodebaseLoader] created from an existing [SourceParser]. */
    private class FromSourceParser(
        private val progressTracker: ProgressTracker,
        private val reporter: Reporter,
        private val sourceParser: SourceParser,
    ) : JarCodebaseLoader {
        override fun loadFromJarFile(
            apiJar: File,
            apiAnalyzerConfig: ApiAnalyzer.Config,
        ): Codebase {
            progressTracker.progress("Processing jar file: ")

            val apiPredicateConfig = apiAnalyzerConfig.apiPredicateConfig
            val apiEmit =
                ApiPredicate(
                    config = apiPredicateConfig.copy(ignoreShown = true),
                )
            val apiReference = apiEmit

            val codebase = sourceParser.loadFromJar(apiJar)
            val analyzer = ApiAnalyzer(sourceParser, codebase, reporter, apiAnalyzerConfig)
            analyzer.mergeExternalInclusionAnnotations()
            analyzer.computeApi()
            analyzer.mergeExternalQualifierAnnotations()
            analyzer.generateInheritedStubs(apiEmit, apiReference)

            // Prevent the codebase from being mutated.
            codebase.freezeClasses()

            return codebase
        }
    }
}

/**
 * A [JarCodebaseLoader] that owns its own [EnvironmentManager] and supports releasing its resources
 * through the [close] method.
 */
class StandaloneJarCodebaseLoader
private constructor(
    /**
     * The [EnvironmentManager] that created the [SourceParser] that is used to read the jar files.
     */
    private val environmentManager: EnvironmentManager,

    /** The underlying [JarCodebaseLoader] to which this will delegate the [loadFromJarFile]. */
    private val delegate: JarCodebaseLoader,
) : Closeable, JarCodebaseLoader by delegate {

    /** Free up any resources held by [environmentManager]. */
    override fun close() {
        environmentManager.close()
    }

    companion object {
        /**
         * Create a [StandaloneJarCodebaseLoader].
         *
         * The caller must ensure that the [close] method is called after this has been finished
         * with to ensure prompt release of resources, e.g. using `...use { jarCodebaseLoader -> }`.
         */
        fun create(
            executionEnvironment: ExecutionEnvironment,
            progressTracker: ProgressTracker,
            reporter: Reporter,
        ): StandaloneJarCodebaseLoader {
            val sourceModelProvider = SourceModelProvider.getImplementation("psi")

            val environmentManager =
                sourceModelProvider.createEnvironmentManager(
                    executionEnvironment.disableStderrDumping()
                )

            val annotationManager = DefaultAnnotationManager()
            val codebaseConfig =
                Codebase.Config(
                    annotationManager = annotationManager,
                    reporter = reporter,
                )

            val sourceParser =
                environmentManager.createSourceParser(
                    codebaseConfig,
                )

            val jarLoader =
                JarCodebaseLoader.createForSourceParser(
                    progressTracker,
                    reporter,
                    sourceParser,
                )

            return StandaloneJarCodebaseLoader(environmentManager, jarLoader)
        }
    }
}
