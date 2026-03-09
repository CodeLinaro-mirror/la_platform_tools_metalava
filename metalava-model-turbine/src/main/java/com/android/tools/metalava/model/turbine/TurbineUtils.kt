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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.source.NO_SOURCE_COMMENT_FACTORY
import com.android.tools.metalava.model.source.createSourceItemDocumentation
import com.google.turbine.binder.bound.EnumConstantValue
import com.google.turbine.binder.bound.TurbineClassValue
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.model.Const
import com.google.turbine.model.Const.Kind
import com.google.turbine.model.Const.Value
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.tree.Tree.MethDecl
import com.google.turbine.tree.Tree.PkgDecl
import com.google.turbine.tree.Tree.TyDecl
import com.google.turbine.tree.Tree.VarDecl
import kotlin.jvm.optionals.getOrNull

/**
 * Extracts the package name from a provided compilation unit.
 *
 * @param unit The compilation unit from which to extract the package.
 * @return The extracted package name (e.g., "com.example.project"), or an empty string if no
 *   package is present.
 */
internal fun getPackageName(unit: CompUnit): String {
    val optPkg = unit.pkg()
    val pkg = if (optPkg.isPresent()) optPkg.get() else null
    return pkg?.name()?.dotSeparatedName ?: ""
}

/**
 * Creates a dot-separated name from a list of [Ident] objects.
 *
 * This is often used for constructing fully qualified names or package structures.
 *
 * @param this@extractNameFromIdent The list of [Ident] objects representing name segments.
 * @return The combined name with segments joined by "." (e.g., "java.util.List")
 */
internal val List<Ident>.dotSeparatedName: String
    get() {
        val nameList = map { it.value() }
        return nameList.joinToString(separator = ".")
    }

/**
 * Extracts header comments from a [CompUnit].
 *
 * Header comments are defined as any content appearing before the "package" keyword.
 *
 * @return The extracted header comments, or an empty string if no "package" keyword or comments are
 *   found.
 */
internal fun CompUnit.getHeaderComments(): String {
    // Find the package statement.
    val pkgDecl = pkg().getOrNull() ?: return ""
    val source = source().source()
    // The PkgDecl.position() is the start of the package name not the `package` keyword.
    val packageNamePosition = pkgDecl.position()
    // Search backwards for the start of the `package` keyword.
    val packageKeywordStart = source.lastIndexOf("package", packageNamePosition)
    // Return the content before the `package` keyword to match Java.
    return source.substring(0, packageKeywordStart)
}

/** Get an [ItemDocumentationFactory] for [decl] in [sourceFile]. */
internal fun TurbineGlobalContext.itemDocumentationFactoryForDecl(
    sourceFile: TurbineSourceFile?,
    decl: Tree?
): ItemDocumentationFactory {
    // If comments are not read then ignore the javadoc.
    if (!allowReadingComments) return ItemDocumentation.NONE_FACTORY

    val turbineJavadoc =
        when (decl) {
            is TyDecl -> decl.javadoc()
            is MethDecl -> decl.javadoc()
            is VarDecl -> decl.javadoc()
            is PkgDecl -> decl.javadoc()
            null -> null
            else -> error("Should never be called")
        } ?: return NO_SOURCE_COMMENT_FACTORY

    return ItemDocumentationFactory { item ->
        createSourceItemDocumentation(item, TurbineSourceComment(sourceFile, turbineJavadoc))
    }
}

/**
 * Get the qualified name, i.e. what would be used in an `import` statement, for this [ClassSymbol].
 */
internal val ClassSymbol.qualifiedName: String
    get() = binaryName().replace('/', '.').replace('$', '.')

/**
 * The underlying value of this [Const].
 *
 * e.g. [Integer] for integers, [String]s for strings and any other values.
 */
internal val Const.underlyingValue: Any?
    get() {
        when (kind()) {
            Kind.PRIMITIVE -> {
                val value = this as Value
                return value.value
            }
            // For cases like AnyClass.class, return the qualified name of AnyClass
            Kind.CLASS_LITERAL -> {
                val value = this as TurbineClassValue
                return value.type().toString()
            }
            Kind.ENUM_CONSTANT -> {
                val value = this as EnumConstantValue
                val temp = "${value.sym().owner().qualifiedName}.$value"
                return temp
            }
            else -> {
                return toString()
            }
        }
    }

internal val ClassSymbol.dotSeparatedPackageName
    get() = packageName().replace('/', '.')
