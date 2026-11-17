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
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.item.AbstractSourceFile
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.diag.LineMap as TurbineDiagLineMap
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.CompUnit
import java.nio.file.Path

internal class TurbineSourceFile(
    override val codebase: DefaultCodebase,
    val compUnit: CompUnit,
) : AbstractSourceFile() {

    override val fileLocation: FileLocation =
        FileLocation.createLocation(Path.of(compUnit.source().path()))

    override fun computeLineMap(): LineMap =
        TurbineLineMap(TurbineDiagLineMap.create(compUnit.source().source()))

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
}

/** Get the [SourcePosition] of this optional [Tree] node. */
internal val Tree?.sourcePosition: SourcePosition
    get() = if (this == null) SourcePosition.UNKNOWN else SourcePosition(position())

/** A [LineMap] that wraps a [TurbineDiagLineMap]. */
private class TurbineLineMap(private val delegate: TurbineDiagLineMap) : LineMap {
    override fun lineNumber(charIndex: Int): Int = delegate.lineNumber(charIndex)

    override fun characterPosition(charIndex: Int): Int = delegate.column(charIndex) + 1

    override fun characterOffset(charIndex: Int): Int = delegate.column(charIndex)
}
