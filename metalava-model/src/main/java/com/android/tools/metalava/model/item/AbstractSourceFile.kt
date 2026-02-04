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
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.imports.ImportResolver
import com.android.tools.metalava.model.scope.NameClassification
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

    /** Resolve [simpleName] to a [ReferencableItem] using [importResolver]. */
    private fun importedItem(
        simpleName: String,
        onDemand: Boolean,
        nameClassification: NameClassification
    ): ReferencableItem? {
        // Resolve the import, if possible.
        val resolvedImport = importResolver.resolveImport(simpleName, onDemand) ?: return null

        // Resolve the class.
        val qualifiedClassName = resolvedImport.qualifiedClassName
        val resolvedClass = codebase.resolveClass(qualifiedClassName) ?: return null

        // Check if a member name was provided and if not just return the class, if allowed.
        val memberName =
            resolvedImport.memberName
                ?: return nameClassification.findClass { resolvedClass }
                    ?: InvalidReferencableItem(
                        "Expected ${nameClassification.describeName(simpleName)} but found '$resolvedClass'"
                    )

        // Return the result of trying to resolve a nested class.
        return resolvedClass.resolveClassMember(memberName, nameClassification)
    }

    /** Resolve class member [memberName] classified as [nameClassification]. */
    private fun ClassItem.resolveClassMember(
        memberName: String,
        nameClassification: NameClassification
    ): ReferencableItem? =
        // Determine in memberName is a member of a nested class by constructing its fully qualified
        // name and resolving it. That is necessary because the nested classes may not be created
        // properly during snapshotting.
        // TODO(b/474319264): Check nested classes instead.
        nameClassification.findClass { codebase.resolveClass("${qualifiedName()}.$memberName") }
            ?: nameClassification.findField { findField(memberName) }
            ?: nameClassification.findMethodSet { findMethodSet(memberName) }

    override val containingScope: ReferencableNameScope?
        get() =
            // Fall straight back to the root package as the containing package has been checked in
            // [resolveReferencableItemBySimpleName].
            codebase.rootPackage

    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        nameClassification: NameClassification,
        isFirstSimpleName: Boolean
    ) =
        // First, check for other top level classes in the same file.
        nameClassification.findClass { classes().find { it.simpleName() == simpleName } }
            // Then check for named imports first.
            ?: importedItem(simpleName, onDemand = false, nameClassification)
            // Then check the containing package.
            ?: containingPackage.resolveReferencableItemBySimpleName(
                simpleName,
                nameClassification,
                isFirstSimpleName
            )
            // Then check for on demand imports.
            ?: importedItem(simpleName, onDemand = true, nameClassification)
}
