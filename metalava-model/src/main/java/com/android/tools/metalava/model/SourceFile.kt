/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.snapshot.SourceFileSnapshot
import com.android.tools.metalava.reporter.FileLocation

/** Represents a Kotlin/Java source file */
interface SourceFile : ReferencableNameScope {
    /**
     * The location of this [SourceFile]
     *
     * If this is not [FileLocation.UNKNOWN] then it will not have a line number.
     */
    val fileLocation: FileLocation

    /** The [Codebase] to which this [SourceFile] belongs. */
    val codebase: Codebase

    /** The [PackageItem] to which this [SourceFile] belongs. */
    val containingPackage: PackageItem

    /** Top level classes contained in this file */
    fun classes(): Sequence<ClassItem>

    fun getHeaderComments(): String? = null

    /**
     * Get all the Java imports, no filtering, no sorting, includes static and on demand.
     *
     * Returns an empty list for Kotlin as this will be used for resolving references in Javadoc
     * comments that are written to the stubs, which is only done for Java APIs.
     */
    fun allJavaImports(): List<JavaImport>

    fun snapshot(targetCodebase: Codebase): SourceFile =
        SourceFileSnapshot(
            targetCodebase,
            originalSourceFile = this,
        )
}

/** Encapsulates information about the imports used in a Java [SourceFile]. */
data class JavaImport(
    /**
     * The qualified name of the import.
     *
     * If [onDemand] is `true` then this is everything before the `.*`. Otherwise, this is the
     * qualified name of the imported item(s).
     */
    val qualifiedName: String,

    /** `true` if the import used a wildcard, i.e. ended with `.*`. */
    val onDemand: Boolean,

    /** `true` if the import used the `static` keyword. */
    val static: Boolean,
)
