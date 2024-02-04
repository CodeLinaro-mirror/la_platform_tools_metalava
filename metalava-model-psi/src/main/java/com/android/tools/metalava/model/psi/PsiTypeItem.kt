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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.impl.source.PsiImmediateClassType
import com.intellij.psi.util.TypeConversionUtil
import java.lang.IllegalStateException

/** Represents a type backed by PSI */
sealed class PsiTypeItem(val codebase: PsiBasedCodebase, val psiType: PsiType) :
    DefaultTypeItem(codebase) {

    /** Returns `true` if `this` type can be assigned from `other` without unboxing the other. */
    fun isAssignableFromWithoutUnboxing(other: PsiTypeItem): Boolean {
        if (this is PrimitiveTypeItem && other !is PrimitiveTypeItem) {
            return false
        }
        return TypeConversionUtil.isAssignable(psiType, other.psiType)
    }

    /**
     * Finishes initialization of a type by correcting its nullability based on the owning item,
     * which was not constructed yet when the type was created.
     */
    internal fun finishInitialization(owner: PsiItem) {
        val implicitNullness = owner.implicitNullness()
        // Kotlin varargs can't be null, but the annotation for the component type ends up on the
        // context item, so avoid setting Kotlin varargs to nullable.
        if (
            (implicitNullness == true || owner.modifiers.isNullable()) &&
                !(owner.isKotlin() && this is ArrayTypeItem && isVarargs)
        ) {
            modifiers.setNullability(TypeNullability.NULLABLE)
        } else if (implicitNullness == false || owner.modifiers.isNonNull()) {
            modifiers.setNullability(TypeNullability.NONNULL)
        }

        // Also set component array types that should be non-null.
        if (this is PsiArrayTypeItem && owner.impliesNonNullArrayComponents()) {
            componentType.modifiers.setNullability(TypeNullability.NONNULL)
        }
    }

    companion object {
        /**
         * Determine if this item implies that its associated type is a non-null array with non-null
         * components. This is true for the synthetic `Enum.values()` method and any annotation
         * properties or accessors.
         */
        private fun Item.impliesNonNullArrayComponents(): Boolean {
            fun MemberItem.isAnnotationPropertiesOrAccessors(): Boolean =
                containingClass().isAnnotationType() && !modifiers.isStatic()

            // TODO: K2 UAST regression, KTIJ-24754
            fun MethodItem.isEnumValues(): Boolean =
                containingClass().isEnum() &&
                    modifiers.isStatic() &&
                    name() == "values" &&
                    parameters().isEmpty()

            return when (this) {
                is MemberItem -> {
                    isAnnotationPropertiesOrAccessors() || (this is MethodItem && isEnumValues())
                }
                else -> false
            }
        }

        internal fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiType,
            kotlinType: KotlinTypeInfo?,
            typeUse: TypeUse = TypeUse.GENERAL,
        ): PsiTypeItem {
            return when (psiType) {
                is PsiPrimitiveType ->
                    PsiPrimitiveTypeItem.create(
                        codebase = codebase,
                        psiType = psiType,
                        kotlinType = kotlinType,
                    )
                is PsiArrayType ->
                    PsiArrayTypeItem.create(
                        codebase = codebase,
                        psiType = psiType,
                        kotlinType = kotlinType,
                    )
                is PsiClassType -> {
                    if (psiType.resolve() is PsiTypeParameter) {
                        PsiVariableTypeItem.create(
                            codebase = codebase,
                            psiType = psiType,
                            kotlinType = kotlinType,
                        )
                    } else {
                        PsiClassTypeItem.create(
                            codebase = codebase,
                            psiType = psiType,
                            kotlinType = kotlinType,
                            typeUse = typeUse,
                        )
                    }
                }
                is PsiWildcardType ->
                    PsiWildcardTypeItem.create(
                        codebase = codebase,
                        psiType = psiType,
                        kotlinType = kotlinType,
                    )
                // There are other [PsiType]s, but none can appear in API surfaces.
                else -> throw IllegalStateException("Invalid type in API surface: $psiType")
            }
        }
    }
}

/** A [PsiTypeItem] backed by a [PsiPrimitiveType]. */
internal class PsiPrimitiveTypeItem(
    codebase: PsiBasedCodebase,
    psiType: PsiType,
    override val kind: PrimitiveTypeItem.Primitive,
    override val modifiers: PsiTypeModifiers,
) : PrimitiveTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(): PsiPrimitiveTypeItem =
        PsiPrimitiveTypeItem(
            codebase = codebase,
            psiType = psiType,
            kind = kind,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiPrimitiveType,
            kotlinType: KotlinTypeInfo?,
        ) =
            PsiPrimitiveTypeItem(
                codebase = codebase,
                psiType = psiType,
                kind = getKind(psiType),
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )

        private fun getKind(type: PsiPrimitiveType): PrimitiveTypeItem.Primitive {
            return when (type) {
                PsiTypes.booleanType() -> PrimitiveTypeItem.Primitive.BOOLEAN
                PsiTypes.byteType() -> PrimitiveTypeItem.Primitive.BYTE
                PsiTypes.charType() -> PrimitiveTypeItem.Primitive.CHAR
                PsiTypes.doubleType() -> PrimitiveTypeItem.Primitive.DOUBLE
                PsiTypes.floatType() -> PrimitiveTypeItem.Primitive.FLOAT
                PsiTypes.intType() -> PrimitiveTypeItem.Primitive.INT
                PsiTypes.longType() -> PrimitiveTypeItem.Primitive.LONG
                PsiTypes.shortType() -> PrimitiveTypeItem.Primitive.SHORT
                PsiTypes.voidType() -> PrimitiveTypeItem.Primitive.VOID
                else -> throw IllegalStateException("Invalid primitive type in API surface: $type")
            }
        }
    }
}

/** A [PsiTypeItem] backed by a [PsiArrayType]. */
internal class PsiArrayTypeItem(
    codebase: PsiBasedCodebase,
    psiType: PsiType,
    override val componentType: PsiTypeItem,
    override val isVarargs: Boolean,
    override val modifiers: PsiTypeModifiers,
) : ArrayTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(componentType: TypeItem): ArrayTypeItem =
        PsiArrayTypeItem(
            codebase = codebase,
            psiType = psiType,
            componentType = componentType as PsiTypeItem,
            isVarargs = isVarargs,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiArrayType,
            kotlinType: KotlinTypeInfo?,
        ) =
            PsiArrayTypeItem(
                codebase = codebase,
                psiType = psiType,
                componentType =
                    create(codebase, psiType.componentType, kotlinType?.forArrayComponentType()),
                isVarargs = psiType is PsiEllipsisType,
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )
    }
}

/** A [PsiTypeItem] backed by a [PsiClassType] that does not represent a type variable. */
internal class PsiClassTypeItem(
    codebase: PsiBasedCodebase,
    psiType: PsiType,
    override val qualifiedName: String,
    override val arguments: List<PsiTypeItem>,
    override val outerClassType: PsiClassTypeItem?,
    override val className: String,
    override val modifiers: PsiTypeModifiers,
) : ClassTypeItem, PsiTypeItem(codebase, psiType) {

    private var asClass: ClassItem? = null

    override fun asClass(): ClassItem? {
        if (asClass == null) {
            asClass = codebase.findClass(psiType)
        }
        return asClass
    }

    override fun duplicate(outerClass: ClassTypeItem?, arguments: List<TypeItem>): ClassTypeItem =
        PsiClassTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments = arguments.map { it as PsiTypeItem },
            outerClassType = outerClass as? PsiClassTypeItem,
            className = className,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiClassType,
            kotlinType: KotlinTypeInfo?,
            typeUse: TypeUse,
        ): PsiClassTypeItem {
            val qualifiedName = computeQualifiedName(psiType)
            return PsiClassTypeItem(
                codebase = codebase,
                psiType = psiType,
                qualifiedName = qualifiedName,
                arguments = computeTypeArguments(codebase, psiType, kotlinType),
                outerClassType = computeOuterClass(psiType, codebase, kotlinType),
                // This should be able to use `psiType.name`, but that sometimes returns null.
                className = ClassTypeItem.computeClassName(qualifiedName),
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType, typeUse),
            )
        }

        private fun computeTypeArguments(
            codebase: PsiBasedCodebase,
            psiType: PsiClassType,
            kotlinType: KotlinTypeInfo?
        ): List<PsiTypeItem> {
            val psiParameters =
                psiType.parameters.toList().ifEmpty {
                    // Sometimes an immediate class type has no parameters even though the class
                    // does have them -- find the class parameters and convert them to types.
                    (psiType as? PsiImmediateClassType)?.resolve()?.typeParameters?.mapNotNull {
                        PsiSubstitutor.EMPTY.substitute(it)
                    }
                        ?: emptyList()
                }

            return psiParameters.mapIndexed { i, param ->
                create(codebase, param, kotlinType?.forParameter(i))
            }
        }

        private fun computeQualifiedName(psiType: PsiClassType): String {
            // It should be possible to do `psiType.rawType().canonicalText` instead, but this
            // doesn't
            // always work if psi is unable to resolve the reference.
            // See https://youtrack.jetbrains.com/issue/KTIJ-27093 for more details.
            return PsiNameHelper.getQualifiedClassName(psiType.canonicalText, true)
        }

        private fun computeOuterClass(
            psiType: PsiClassType,
            codebase: PsiBasedCodebase,
            kotlinType: KotlinTypeInfo?
        ): PsiClassTypeItem? {
            // TODO(b/300081840): this drops annotations on the outer class
            return PsiNameHelper.getOuterClassReference(psiType.canonicalText).let { outerClassName
                ->
                // [PsiNameHelper.getOuterClassReference] returns an empty string if there is no
                // outer class reference. If the type is not an inner type, it returns the package
                // name (e.g. for "java.lang.String" it returns "java.lang").
                if (outerClassName == "" || codebase.findPsiPackage(outerClassName) != null) {
                    null
                } else {
                    val psiOuterClassType =
                        codebase.createPsiType(
                            outerClassName,
                            // The context psi element allows variable types to be resolved (with no
                            // context, they would be interpreted as class types). The [psiContext]
                            // works in most cases, but is null when creating a type directly from a
                            // class declaration, so the resolved [psiType] provides context then.
                            psiType.psiContext ?: psiType.resolve()
                        )
                    (create(codebase, psiOuterClassType, kotlinType?.forOuterClass())
                            as PsiClassTypeItem)
                        .apply {
                            // An outer class reference can't be null.
                            modifiers.setNullability(TypeNullability.NONNULL)
                        }
                }
            }
        }
    }
}

/** A [PsiTypeItem] backed by a [PsiClassType] that represents a type variable.e */
internal class PsiVariableTypeItem(
    codebase: PsiBasedCodebase,
    psiType: PsiType,
    override val name: String,
    override val modifiers: PsiTypeModifiers,
) : VariableTypeItem, PsiTypeItem(codebase, psiType) {
    override val asTypeParameter: TypeParameterItem by lazy {
        val cls = (psiType as PsiClassType).resolve() ?: error("Could not resolve $psiType")
        codebase.findOrCreateTypeParameter(cls as PsiTypeParameter)
    }

    private var asClass: ClassItem? = null

    override fun asClass(): ClassItem? {
        if (asClass == null) {
            asClass =
                asTypeParameter.typeBounds().firstOrNull()?.asClass()
                    ?: codebase.findOrCreateClass(JAVA_LANG_OBJECT)
        }
        return asClass
    }

    override fun duplicate(): PsiVariableTypeItem =
        PsiVariableTypeItem(
            codebase = codebase,
            psiType = psiType,
            name = name,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(codebase: PsiBasedCodebase, psiType: PsiClassType, kotlinType: KotlinTypeInfo?) =
            PsiVariableTypeItem(
                codebase = codebase,
                psiType = psiType,
                name = psiType.name,
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )
    }
}

/** A [PsiTypeItem] backed by a [PsiWildcardType]. */
internal class PsiWildcardTypeItem(
    codebase: PsiBasedCodebase,
    psiType: PsiType,
    override val extendsBound: PsiTypeItem?,
    override val superBound: PsiTypeItem?,
    override val modifiers: PsiTypeModifiers,
) : WildcardTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(extendsBound: TypeItem?, superBound: TypeItem?): WildcardTypeItem =
        PsiWildcardTypeItem(
            codebase = codebase,
            psiType = psiType,
            extendsBound = extendsBound as? PsiTypeItem,
            superBound = superBound as? PsiTypeItem,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiWildcardType,
            kotlinType: KotlinTypeInfo?,
        ) =
            PsiWildcardTypeItem(
                codebase = codebase,
                psiType = psiType,
                extendsBound = createBound(psiType.extendsBound, codebase, kotlinType),
                superBound = createBound(psiType.superBound, codebase, kotlinType),
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )

        /**
         * If a [PsiWildcardType] doesn't have a bound, the bound is represented as the null
         * [PsiType] instead of just `null`.
         */
        private fun createBound(
            bound: PsiType,
            codebase: PsiBasedCodebase,
            kotlinType: KotlinTypeInfo?
        ): PsiTypeItem? {
            return if (bound == PsiTypes.nullType()) {
                null
            } else {
                // Use the same Kotlin type, because the wildcard isn't its own level in the KtType.
                create(codebase, bound, kotlinType)
            }
        }
    }
}
