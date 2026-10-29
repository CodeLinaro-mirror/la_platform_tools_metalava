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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.JavaImport
import com.android.tools.metalava.model.item.AbstractSourceFile
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.source.doc.characterOffsetFor
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.diag.LineMap
import com.google.turbine.tree.Tree.CompUnit

internal class TurbineSourceFile(
    override val codebase: DefaultCodebase,
    val compUnit: CompUnit,
) : AbstractSourceFile() {

    override val fileLocation: FileLocation = TurbineFileLocation.forTree(this)

    override fun computeContainingPackageName() = getPackageName(compUnit)

    override fun getHeaderComments() = compUnit.getHeaderComments()

    override fun classes(): Sequence<ClassItem> {
        val pkgName = getPackageName(compUnit)
        val classDecls = compUnit.decls() // Top level class declarations
        val classNames = classDecls.map { pkgName + "." + it.name().value() }
        return classNames.asSequence().mapNotNull { codebase.findClass(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TurbineSourceFile && compUnit == other.compUnit
    }

    override fun hashCode(): Int {
        return compUnit.hashCode()
    }

    override fun allJavaImports() =
        compUnit.imports().map { import ->
            JavaImport(
                qualifiedName = import.type().dotSeparatedName,
                onDemand = import.wild(),
                static = import.stat(),
            )
        }

    /**
     * The [LineMap] used to map positions in the source file into line numbers.
     *
     * Created lazily as it can be expensive to create.
     */
    private val lineMap by
        lazy(LazyThreadSafetyMode.NONE) { LineMap.create(compUnit.source().source()) }

    /**
     * Get the line number for [position] which was retrieved from
     * [com.google.turbine.tree.Tree.position].
     */
    fun lineForPosition(position: Int) = lineMap.lineNumber(position)

    /**
     * Get the character position for [position] which was retrieved from
     * [com.google.turbine.tree.Tree.position].
     */
    fun characterPositionForPosition(position: Int) =
        compUnit.source().source().characterOffsetFor(position) + 1
}
