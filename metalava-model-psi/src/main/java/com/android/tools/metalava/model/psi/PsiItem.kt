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

import com.android.tools.metalava.model.AbstractItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UElement

abstract class PsiItem
internal constructor(
    final override val codebase: PsiBasedCodebase,
    element: PsiElement,
    fileLocation: FileLocation = PsiFileLocation(element),
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
) :
    AbstractItem(
        fileLocation = fileLocation,
        itemLanguage = element.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ) {

    /** The source PSI provided by UAST */
    internal val sourcePsi: PsiElement? = (element as? UElement)?.sourcePsi

    /** Returns the PSI element for this item */
    abstract fun psi(): PsiElement

    override fun isFromClassPath(): Boolean {
        return codebase.fromClasspath || containingClass()?.isFromClassPath() ?: false
    }

    companion object {

        internal fun modifiers(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
        ): DefaultModifierList {
            return PsiModifierItem.create(codebase, element)
        }
    }
}

/** Get the [ItemLanguage] for this [PsiElement]. */
val PsiElement.itemLanguage
    get() = if (isKotlin()) ItemLanguage.KOTLIN else ItemLanguage.JAVA

/** Check whether this [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}
