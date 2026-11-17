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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import java.io.File
import kotlin.collections.iterator

/**
 * Writes signature files for a [MultiplatformCodebase]. Should be used through [Companion.write].
 */
class MultiplatformSignatureWriter
private constructor(
    private val codebase: MultiplatformCodebase,
    private val outputDirectory: File,
    private val fragmentCreator: (Codebase) -> CodebaseFragment,
    private val outputCreator: (CodebaseFragment, File, String) -> Unit
) {
    private fun write() {
        val commonSourceSet = codebase.sourceSetToCodebase[COMMON_SOURCE_SET_NAME]
        if (commonSourceSet != null) {
            // When there is a common source set, write a signature file for common, and then for
            // each other source set write a delta signature file.
            writeSourceSet(COMMON_SOURCE_SET_NAME, commonSourceSet)
            for ((sourceSetName, sourceSet) in codebase.sourceSetToCodebase) {
                if (sourceSetName == COMMON_SOURCE_SET_NAME) continue
                writeSourceSetAsDelta(sourceSetName, sourceSet, commonSourceSet)
            }
        } else {
            // If there is no common source set, write each source set as a signature file.
            for ((sourceSetName, sourceSet) in codebase.sourceSetToCodebase) {
                writeSourceSet(sourceSetName, sourceSet)
            }
        }
    }

    /** Writes the API for the [sourceSet] named [name]. */
    private fun writeSourceSet(name: String, sourceSet: Codebase) {
        val fragment = fragmentCreator(sourceSet)
        writeFile(name, fragment)
    }

    /** Writes the API file for the [sourceSet] named [name] as a delta on the [baseSourceSet]. */
    private fun writeSourceSetAsDelta(name: String, sourceSet: Codebase, baseSourceSet: Codebase) {
        val sourceSetFragment = fragmentCreator(sourceSet)
        val delta =
            SnapshotDeltaMaker.createDelta(
                baseSourceSet,
                sourceSetFragment,
                checkMemberItemEquivalence = true,
                allowClassModifierChanges = true,
            )
        writeFile(name, delta)
    }

    /** Uses [outputCreator] to write the signature file for a [fragment] named [name]. */
    private fun writeFile(name: String, fragment: CodebaseFragment) {
        outputCreator(
            fragment,
            File(outputDirectory, "$name.txt"),
            "$name API",
        )
    }

    companion object {
        /**
         * Writes signature files for the [MultiplatformCodebase] into the [outputDirectory].
         *
         * @param fragmentCreator Lambda to convert a source set [Codebase] into a
         *   [CodebaseFragment].
         * @param outputCreator Lambda to take a [CodebaseFragment] and write it to an output
         *   [File], with a [String] description of the file.
         */
        fun write(
            codebase: MultiplatformCodebase,
            outputDirectory: File,
            fragmentCreator: (Codebase) -> CodebaseFragment,
            outputCreator: (CodebaseFragment, File, String) -> Unit
        ) {
            MultiplatformSignatureWriter(codebase, outputDirectory, fragmentCreator, outputCreator)
                .write()
        }

        /** The expected name for a common source set, which all other source sets depend on. */
        const val COMMON_SOURCE_SET_NAME = "commonMain"
    }
}
