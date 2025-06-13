/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import org.jetbrains.uast.UAnnotation

internal object UAnnotationItem {

    private fun getAnnotationAttributes(
        codebase: PsiBasedCodebase,
        uAnnotation: UAnnotation,
    ): List<AnnotationAttribute> {
        val annotationPsiClass = uAnnotation.resolve()
        return uAnnotation.attributeValues
            .map { attribute ->
                val name = attribute.name ?: ANNOTATION_ATTR_VALUE
                AnnotationAttribute.createLazyAttribute(
                    name,
                    codebase.valueFactory.providerForAnnotationValue(
                        annotationPsiClass,
                        name,
                        attribute.expression
                    ),
                )
            }
            .toList()
    }

    fun create(
        codebase: PsiBasedCodebase,
        uAnnotation: UAnnotation,
    ): AnnotationItem? {
        // If the qualified name is a typealias, convert it to the aliased type because that is
        // the version that will be present as a class in the codebase.
        val originalName =
            uAnnotation.qualifiedName?.let {
                (codebase.findTypeAlias(it)?.aliasedType as? PsiClassTypeItem)?.qualifiedName ?: it
            } ?: return null
        return AnnotationItem.createAttributesLazily(
            annotationContext = codebase,
            fileLocation = PsiFileLocation.fromPsiElement(uAnnotation.sourcePsi),
            originalName = originalName,
            attributesGetter = { getAnnotationAttributes(codebase, uAnnotation) },
        )
    }
}
