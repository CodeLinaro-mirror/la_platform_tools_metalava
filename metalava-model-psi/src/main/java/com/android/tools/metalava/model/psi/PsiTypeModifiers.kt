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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.getContainingUMethod

/** Modifiers for a [PsiTypeItem]. */
internal class PsiTypeModifiers(
    private val annotations: MutableList<PsiAnnotationItem>,
    private var nullability: TypeNullability
) : TypeModifiers {
    override fun annotations(): List<AnnotationItem> = annotations

    override fun addAnnotation(annotation: AnnotationItem) {
        annotations.add(annotation as PsiAnnotationItem)
    }

    override fun removeAnnotation(annotation: AnnotationItem) {
        annotations.remove(annotation)
    }

    override fun nullability(): TypeNullability = nullability

    override fun setNullability(newNullability: TypeNullability) {
        nullability = newNullability
    }

    fun duplicate() = PsiTypeModifiers(annotations.toMutableList(), nullability)

    companion object {
        /** Creates modifiers in the given [codebase] based on the annotations of the [type]. */
        fun create(
            codebase: PsiBasedCodebase,
            type: PsiType,
            kotlinType: KotlinTypeInfo?
        ): PsiTypeModifiers {
            val annotations = type.annotations.map { PsiAnnotationItem.create(codebase, it) }
            // Some types have defined nullness, and kotlin types have nullness information.
            // Otherwise, look at the annotations and default to platform nullness.
            val nullability =
                when (type) {
                    is PsiPrimitiveType -> TypeNullability.NONNULL
                    is PsiWildcardType -> TypeNullability.UNDEFINED
                    else -> kotlinType?.nullability()
                            ?: annotations
                                .firstOrNull { it.isNullnessAnnotation() }
                                ?.let { TypeNullability.ofAnnotation(it) }
                                ?: TypeNullability.PLATFORM
                }
            return PsiTypeModifiers(annotations.toMutableList(), nullability)
        }
    }
}

/** A wrapper for a [KtType] and the [KtAnalysisSession] needed to analyze it. */
internal data class KotlinTypeInfo(val analysisSession: KtAnalysisSession?, val ktType: KtType?) {
    /**
     * Finds the nullability of the [ktType]. If there is no [analysisSession] or [ktType], defaults
     * to [TypeNullability.NONNULL] since the type is from Kotlin source.
     */
    fun nullability(): TypeNullability? {
        return if (analysisSession != null && ktType != null) {
            analysisSession.run {
                if (analysisSession.isInheritedGenericType(ktType)) {
                    TypeNullability.UNDEFINED
                } else if (ktType.nullability == KtTypeNullability.NULLABLE) {
                    TypeNullability.NULLABLE
                } else if (ktType.nullability == KtTypeNullability.NON_NULLABLE) {
                    TypeNullability.NONNULL
                } else {
                    // No nullability information, possibly a propagated platform type.
                    null
                }
            }
        } else {
            TypeNullability.NONNULL
        }
    }

    /**
     * Creates [KotlinTypeInfo] for the component type of this [ktType], assuming it is an array.
     */
    fun forArrayComponentType(): KotlinTypeInfo {
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run { ktType?.getArrayElementType() }
        )
    }

    /**
     * Creates [KotlinTypeInfo] for the parameter number [index] of this [ktType], assuming it is a
     * class type.
     */
    fun forParameter(index: Int): KotlinTypeInfo {
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run {
                (ktType as? KtFunctionalType)?.let {
                    if (it.hasReceiver && index == 0) {
                        it.receiverType
                    } else {
                        // If there's a receiver, the index into the parameter list is one less.
                        val effectiveIndex =
                            if (it.hasReceiver) {
                                index - 1
                            } else {
                                index
                            }
                        if (effectiveIndex >= it.parameterTypes.size) {
                            it.returnType
                        } else {
                            it.parameterTypes[effectiveIndex]
                        }
                    }
                }
                    ?: (ktType as? KtNonErrorClassType)?.ownTypeArguments?.getOrNull(index)?.type
            }
        )
    }

    /**
     * Creates [KotlinTypeInfo] for the outer class type of this [ktType], assuming it is a class.
     */
    fun forOuterClass(): KotlinTypeInfo {
        return KotlinTypeInfo(
            analysisSession,
            analysisSession?.run {
                (ktType as? KtNonErrorClassType)?.classId?.outerClassId?.let { outerClassId ->
                    buildClassType(outerClassId) {
                        // Add the parameters of the class type with nullability information.
                        ktType.qualifiers
                            .firstOrNull { it.name == outerClassId.shortClassName }
                            ?.typeArguments
                            ?.forEach { argument(it) }
                    }
                }
            }
        )
    }

    companion object {
        /**
         * Creates a [KotlinTypeInfo] instance from the given [context], with null values if the
         * [KtType] for the [context] can't be resolved.
         */
        fun fromContext(context: PsiElement): KotlinTypeInfo {
            return when (val sourcePsi = (context as? UElement)?.sourcePsi) {
                is KtCallableDeclaration -> {
                    analyze(sourcePsi) { KotlinTypeInfo(this, sourcePsi.getReturnKtType()) }
                }
                is KtTypeReference ->
                    analyze(sourcePsi) { KotlinTypeInfo(this, sourcePsi.getKtType()) }
                is KtPropertyAccessor ->
                    analyze(sourcePsi) { KotlinTypeInfo(this, sourcePsi.getKtType()) }
                else -> {
                    // Check if this is the parameter of a synthetic setter
                    val containingMethod = (context as? UParameter)?.getContainingUMethod()
                    return if (containingMethod?.sourcePsi is KtProperty) {
                        fromContext(containingMethod)
                    } else {
                        KotlinTypeInfo(null, null)
                    }
                }
            }
        }

        // Mimic `hasInheritedGenericType` in `...uast.kotlin.FirKotlinUastResolveProviderService`
        fun KtAnalysisSession.isInheritedGenericType(ktType: KtType): Boolean {
            return ktType is KtTypeParameterType &&
                // explicitly nullable, e.g., T?
                !ktType.isMarkedNullable &&
                // non-null upper bound, e.g., T : Any
                ktType.canBeNull
        }
    }
}
