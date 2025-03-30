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

import com.google.turbine.binder.bound.EnumConstantValue
import com.google.turbine.binder.bound.TurbineClassValue
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.model.Const
import com.google.turbine.model.Const.Kind
import com.google.turbine.model.Const.Value
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Ident

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
 * Extracts header comments from a source file string. Header comments are defined as any content
 * appearing before the "package" keyword.
 *
 * @param source The source file string.
 * @return The extracted header comments, or an empty string if no "package" keyword or comments are
 *   found.
 */
internal fun getHeaderComments(source: String): String {
    val packageIndex = source.indexOf("package")
    // Return everything before "package" keyword
    return if (packageIndex == -1) "" else source.substring(0, packageIndex)
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
