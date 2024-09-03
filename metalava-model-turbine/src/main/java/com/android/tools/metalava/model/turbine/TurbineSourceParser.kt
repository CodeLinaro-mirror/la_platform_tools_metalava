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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.Reporter
import java.io.File

internal class TurbineSourceParser(
    private val reporter: Reporter,
    private val annotationManager: AnnotationManager,
    private val allowReadingComments: Boolean
) : SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        TODO("implement it")
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classPath: List<File>,
    ): Codebase {
        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile

        // Create the Codebase. The initialization of the codebase has to done after the creation of
        // the codebase and not during, i.e. in the lambda, because the codebase will not be fully
        // initialized when it is called.
        val codebase =
            TurbineBasedCodebase(rootDir, description, annotationManager, reporter) { codebase ->
                TurbineCodebaseInitialiser(
                    codebase as TurbineBasedCodebase,
                    classPath,
                    allowReadingComments,
                )
            }

        // Initialize the codebase.
        (codebase.assembler as TurbineCodebaseInitialiser).initialize(sourceSet)

        // Return the newly created and initialized codebase.
        return codebase
    }

    override fun loadFromJar(apiJar: File): Codebase {
        TODO("b/299044569 handle this")
    }
}
