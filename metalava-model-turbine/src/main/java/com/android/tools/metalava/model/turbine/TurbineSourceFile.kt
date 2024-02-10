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
import com.android.tools.metalava.model.SourceFile
import com.google.turbine.tree.Tree.ImportDecl

internal class TurbineSourceFile(
    val codebase: TurbineBasedCodebase,
    val source: String,
    val imports: List<ImportDecl>,
) : SourceFile {

    override fun getHeaderComments(): String? = codebase.getHeaderComments(source)

    override fun classes(): Sequence<ClassItem> = TODO("b/295800205")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TurbineSourceFile && source == other.source
    }
}
