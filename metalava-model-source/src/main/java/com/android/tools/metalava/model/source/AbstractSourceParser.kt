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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.reporter.Reporter
import java.io.File

abstract class AbstractSourceParser(
    protected val codebaseConfig: Codebase.Config,
    protected val tracer: Tracer,
) : SourceParser {
    protected val reporter: Reporter
        get() = codebaseConfig.reporter

    final override fun parseSources(
        description: String,
        apiPackages: PackageFilter?,
        compiledSourceJar: File?,
    ): Codebase? =
        processSources(
                description = description,
                apiPackages = apiPackages,
                compiledSourceJar = compiledSourceJar,
            )
            ?.let { postProcessCodebase(it) }

    /** Process the sources in [Environment] to produce a [Codebase], if possible. */
    protected abstract fun processSources(
        description: String,
        apiPackages: PackageFilter?,
        compiledSourceJar: File?,
    ): Codebase?

    final override fun parseJavaStubs(
        javaStubFiles: List<File>,
        apiPackages: PackageFilter?,
    ): Codebase? =
        processJavaStubs(
                javaStubFiles = javaStubFiles.map { it.absoluteFile }.distinct(),
                apiPackages = apiPackages,
            )
            ?.let { postProcessCodebase(it) }

    /** Process the [javaStubFiles] against [Environment] to produce a [Codebase], if possible. */
    protected abstract fun processJavaStubs(
        javaStubFiles: List<File>,
        apiPackages: PackageFilter?,
    ): Codebase?

    /** Perform common post-processing on a newly parsed source [codebase]. */
    private fun postProcessCodebase(codebase: Codebase): Codebase {
        // Determine sealed class exhaustivity.
        tracer.trace("determineIfInaccessibleClassesMakeSuperClassesNonExhaustive") {
            codebase.determineIfInaccessibleClassesMakeSuperClassesNonExhaustive()
        }
        return codebase
    }

    /**
     * Determine if sealed classes make super classes non-exhaustive.
     *
     * Instances of sealed classes can be matched using `when` statements. If all the subclasses of
     * a sealed class are available to API consumers, then new subclasses can't be added to the
     * sealed class because doing so would be a breaking change (clients' `when` statements would no
     * longer be exhaustive). In this case, we label the sealed class as exhaustive. If there is an
     * inaccessible class that extends a sealed class, however, then the sealed class is not
     * exhaustive. For more details, see b/447143803
     */
    private fun Codebase.determineIfInaccessibleClassesMakeSuperClassesNonExhaustive() {
        // Iterate over all sealed classes.
        for (classItem in getPackages().allClasses()) {
            val modifiers = classItem.modifiers
            if (!modifiers.isSealed()) continue

            // At the point the sealed class must have exhaustive = true, set by the source model
            // when creating the modifiers for a `sealed` class.
            require(modifiers.isExhaustive())

            // If any of its permitted subclasses are inaccessible then mark it as non-exhaustive.
            val permitTypes = classItem.permitTypes
            for (type in permitTypes) {
                // Resolve the permitted type to a subclass.
                val subclassTypeItem = type.resolveClass(this)

                // If the subclass is inaccessible then mark the sealed class as `non-exhaustive`
                // and exit. If the subclass could not be resolved then treat it as inaccessible.
                if (subclassTypeItem?.isInaccessible() != false) {
                    classItem.mutateModifiers { setExhaustive(false) }
                    break
                }
            }
        }
    }

    /**
     * Determine whether this [ClassItem] is inaccessible.
     *
     * If this is a nested class then it is inaccessible if any of its enclosing [ClassItem]s are
     * also inaccessible.
     */
    private fun ClassItem.isInaccessible(): Boolean {
        var classItem: ClassItem? = this
        while (classItem != null) {
            val modifiers = classItem.modifiers
            val inaccessible =
                modifiers.annotations().any { it.isHideAnnotation() } ||
                    !modifiers.hasApiVisibility()
            if (inaccessible) return true

            classItem = classItem.containingClass()
        }

        return false
    }
}
