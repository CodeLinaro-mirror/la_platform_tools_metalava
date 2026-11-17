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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.source.createSourceItemDocumentation
import com.android.tools.metalava.model.value.CombinedValueProvider
import com.android.tools.metalava.model.value.ValueUseSite
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UAnnotationMethod
import org.jetbrains.uast.UMethod

// This file contains extension functions and properties on PsiElement and related classes that are
// needed across multiple classes.

/** Get the [SourceLanguage] for this [PsiElement]. */
val PsiElement.sourceLanguage
    get() = if (isKotlin()) SourceLanguage.KOTLIN else SourceLanguage.JAVA

/** Check whether this [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}

/**
 * Get the simple name of a named class or type parameter.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getName] can sometimes be `null`. However, Metalava only gets the name for
 * named classes and type parameters which never return `null`. So, this extension property forces
 * it to be non-null.
 */
internal val PsiClass.simpleName
    get() = name!!

/**
 * Get the qualified name of a name class.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getQualifiedName] can sometimes be `null`. However, Metalava only gets the
 * qualified name for name classes which never return `null`. So, this extension property forces it
 * to be non-null.
 */
internal val PsiClass.classQualifiedName
    get() = qualifiedName!!

/** Get the package name from [PsiClass], returning the empty string for an unqualified class. */
val PsiClass.packageName
    get(): String {
        var top: PsiClass? = this
        while (top?.containingClass != null) {
            top = top.containingClass
        }
        top ?: return ""

        val simpleName = top.simpleName
        val qualifiedName = top.classQualifiedName

        if (simpleName == qualifiedName) {
            return ""
        }

        return qualifiedName.substring(0, qualifiedName.length - 1 - simpleName.length)
    }

/**
 * Get a [CombinedValueProvider] for the default value of the annotation attribute represented by
 * this [PsiMethod], or `null` if this is not an annotation method or it has no value.
 */
internal fun PsiMethod.defaultValueProvider(codebase: PsiBasedCodebase, returnType: TypeItem) =
    when (this) {
        is UAnnotationMethod -> {
            uastDefaultValue?.let { uDefaultValue ->
                codebase.valueFactory.providerFor(
                    returnType,
                    uDefaultValue,
                    ValueUseSite.ANNOTATION,
                )
            }
        }
        is PsiAnnotationMethod -> {
            defaultValue?.let { psiDefaultValue ->
                codebase.valueFactory.providerFor(
                    returnType,
                    psiDefaultValue,
                    ValueUseSite.ANNOTATION,
                )
            }
        }
        else -> null
    }

/** Get the [PsiParameter]s for a [PsiMethod]. */
val PsiMethod.psiParameters: List<PsiParameter>
    get() = if (this is UMethod) uastParameters else parameterList.parameters.toList()

/**
 * Get an [ItemDocumentationFactory] for this [PsiElement].
 *
 * If [Codebase.Config.allowReadingComments] is `true` then this will return a factory that creates
 * an [ItemDocumentation] instance, otherwise it will return [ItemDocumentation.NONE_FACTORY].
 */
internal fun PsiElement.createItemDocumentation(
    codebase: Codebase,
) =
    if (codebase.config.allowReadingComments) {
        // When reading comments provide full access to them.
        ItemDocumentationFactory { item ->
            createSourceItemDocumentation(item, PsiSourceComment(this))
        }
    } else {
        // Otherwise, there is no documentation to use.
        ItemDocumentation.NONE_FACTORY
    }
