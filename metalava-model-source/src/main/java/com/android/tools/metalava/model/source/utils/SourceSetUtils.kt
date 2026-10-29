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

package com.android.tools.metalava.model.source.utils

import com.android.tools.lint.checks.infrastructure.ClassName
import java.io.File

const val PACKAGE_HTML = "package.html"
const val OVERVIEW_HTML = "overview.html"
const val DOT_JAVA = ".java"
const val DOT_KT = ".kt"

/** Finds the package of the given Java/Kotlin source file, if possible */
fun findPackage(file: File): String? {
    val source = file.readText(Charsets.UTF_8)
    val path = file.path
    return findPackage(source)
        // If no package was found, and it was a .kt file then it only has package level
        // declarations but no classes and is in the root package.
        ?: if (path.endsWith(DOT_KT)) "" else null
}

/** Finds the package of the given Java/Kotlin source code, if possible */
private fun findPackage(source: String): String? {
    // Replace is there to handle kotlin packages that have `` in them like com.`receiver`.example
    val className = ClassName(source)
    val simpleClassName = className.className
    val packageName =
        className.packageName?.replace("`", "")
            // If no package could be found but a class was found then the class is from the root
            // package.
            ?: simpleClassName?.let { "" }
    return packageName
}
