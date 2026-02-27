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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiType

/** Global context that is used during construction and initialization of a [PsiBasedCodebase]. */
internal interface PsiGlobalContext {
    /** The [psiCodebase] returned as a [DefaultCodebase]. */
    val codebase: DefaultCodebase
        get() = psiCodebase

    /** The [PsiBasedCodebase] being constructed. */
    val psiCodebase: PsiBasedCodebase

    /** Factory for creating [Item] implementations. */
    val itemFactory: DefaultItemFactory

    /** The global, i.e. no class specific, [PsiTypeItemFactory]. */
    val globalTypeItemFactory: PsiTypeItemFactory

    /**
     * Create a [PsiType] from the source representation [sourceType].
     *
     * Names are resolved relative to [context].
     */
    fun createPsiType(sourceType: String, context: PsiElement? = null): PsiType

    /** Find a [PsiPackage] called [packageName], if possible. */
    fun findPsiPackage(packageName: String): PsiPackage?
}
