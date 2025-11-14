/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.imports.ImportResolver
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.snapshot.SourceFileSnapshot

/** Base class for model implementations of [SourceFile]. */
abstract class AbstractSourceFile() : SourceFile {
    override fun snapshot(targetCodebase: Codebase): SourceFile {
        return SourceFileSnapshot(
            targetCodebase,
            targetCodebase.resolvePackage(containingPackage.qualifiedName())!!,
            originalSourceFile = this,
        )
    }

    /** Backing field of [importResolver]. */
    private lateinit var _importResolver: ImportResolver

    /**
     * [ImportResolver] that can be used to resolve simple names to fully qualified names using the
     * imports.
     */
    private val importResolver: ImportResolver
        get() {
            if (!::_importResolver.isInitialized) {
                _importResolver = ImportResolver(codebase, allJavaImports())
            }
            return _importResolver
        }

    /** Resolve [simpleName] to a [ClassItem] using [importResolver]. */
    private fun importedClassItem(simpleName: String, onDemand: Boolean): ClassItem? {
        // Resolve the import, if possible.
        val resolvedImport = importResolver.resolveImport(simpleName, onDemand) ?: return null

        // Assume that the resolved import was for a qualified class name.
        val qualifiedClassName = resolvedImport.treatAsQualifiedClassName()

        // Resolve the class.
        return codebase.resolveClass(qualifiedClassName)
    }

    override val containingScope: ReferencableNameScope?
        get() =
            // Fall straight back to the root package as the containing package has been checked in
            // [resolveReferencableItemBySimpleName].
            codebase.rootPackage

    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        isFirstSimpleName: Boolean
    ) =
        // First, check for other top level classes in the same file.
        classes().find { it.simpleName() == simpleName }
            // Then check for named imports first.
            ?: importedClassItem(simpleName, onDemand = false)
            // Then check the containing package.
            ?: containingPackage.resolveReferencableItemBySimpleName(simpleName, isFirstSimpleName)
            // Then check for on demand imports.
            ?: importedClassItem(simpleName, onDemand = true)
}
