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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemLanguage
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UElement

internal interface PsiItem : Item {

    override val codebase: PsiBasedCodebase

    /** The source PSI provided by UAST */
    val sourcePsi
        get() = (psi() as? UElement)?.sourcePsi

    /** Returns the PSI element for this item */
    fun psi(): PsiElement

    override fun isFromClassPath(): Boolean {
        return codebase.fromClasspath || containingClass()?.isFromClassPath() ?: false
    }
}

/** Get the [ItemLanguage] for this [PsiElement]. */
val PsiElement.itemLanguage
    get() = if (isKotlin()) ItemLanguage.KOTLIN else ItemLanguage.JAVA

/** Check whether this [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}
