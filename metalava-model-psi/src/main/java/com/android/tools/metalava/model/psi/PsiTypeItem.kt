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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.findAnnotation
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
import java.util.function.Predicate

/** Represents a type backed by PSI */
sealed class PsiTypeItem(open val codebase: PsiBasedCodebase, open val psiType: PsiType) :
    DefaultTypeItem(codebase) {
    private var toString: String? = null
    private var toAnnotatedString: String? = null
    private var asClass: PsiClassItem? = null

    fun toTypeStringWithOldKotlinNulls(
        annotations: Boolean,
        kotlinStyleNulls: Boolean,
        context: Item?,
        filter: Predicate<Item>?
    ): String {
        if (filter != null) {
            // No caching when specifying filter.
            // TODO: When we support type use annotations, here we need to deal with markRecent
            //  and clearAnnotations not really having done their job.
            return toTypeString(
                codebase = codebase,
                type = psiType,
                annotations = annotations,
                kotlinStyleNulls = kotlinStyleNulls,
                context = context,
                filter = filter
            )
        }

        return when {
            kotlinStyleNulls && annotations -> {
                if (toAnnotatedString == null) {
                    toAnnotatedString =
                        toTypeString(
                            codebase = codebase,
                            type = psiType,
                            annotations = annotations,
                            kotlinStyleNulls = kotlinStyleNulls,
                            context = context,
                            filter = filter
                        )
                }
                toAnnotatedString!!
            }
            kotlinStyleNulls || annotations ->
                toTypeString(
                    codebase = codebase,
                    type = psiType,
                    annotations = annotations,
                    kotlinStyleNulls = kotlinStyleNulls,
                    context = context,
                    filter = filter
                )
            else -> {
                if (toString == null) {
                    toString =
                        TypeItem.formatType(
                            getCanonicalText(
                                codebase = codebase,
                                owner = context,
                                type = psiType,
                                annotated = false,
                                mapAnnotations = false,
                                kotlinStyleNulls = kotlinStyleNulls,
                                filter = filter
                            )
                        )
                }
                toString!!
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return when (other) {
            is TypeItem -> TypeItem.equalsWithoutSpace(toTypeString(), other.toTypeString())
            else -> false
        }
    }

    override fun asClass(): PsiClassItem? {
        if (this is PrimitiveTypeItem) {
            return null
        }
        if (asClass == null) {
            asClass = codebase.findClass(psiType)
        }
        return asClass
    }

    override fun hashCode(): Int {
        return psiType.hashCode()
    }

    override fun convertType(replacementMap: Map<String, String>?, owner: Item?): TypeItem {
        val s = convertTypeString(replacementMap)
        // This is a string based type conversion, so there is no Kotlin type information.
        return create(codebase, codebase.createPsiType(s, (owner as? PsiItem)?.psi()), null)
    }

    override fun hasTypeArguments(): Boolean {
        val type = psiType
        return type is PsiClassType && type.hasParameters()
    }

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

    internal abstract fun duplicate(): PsiTypeItem

    companion object {
        private fun toTypeString(
            codebase: PsiBasedCodebase,
            type: PsiType,
            annotations: Boolean,
            kotlinStyleNulls: Boolean,
            context: Item?,
            filter: Predicate<Item>?
        ): String {
            val typeString =
                if (kotlinStyleNulls || annotations) {
                    try {
                        getCanonicalText(
                            codebase = codebase,
                            owner = context,
                            type = type,
                            annotated = annotations,
                            mapAnnotations = true,
                            kotlinStyleNulls = kotlinStyleNulls,
                            filter = filter
                        )
                    } catch (ignore: Throwable) {
                        type.canonicalText
                    }
                } else {
                    type.canonicalText
                }

            return TypeItem.formatType(typeString)
        }

        private fun getCanonicalText(
            codebase: PsiBasedCodebase,
            owner: Item?,
            type: PsiType,
            annotated: Boolean,
            mapAnnotations: Boolean,
            kotlinStyleNulls: Boolean,
            filter: Predicate<Item>?
        ): String {
            return try {
                if (kotlinStyleNulls && owner?.hasInheritedGenericType() != true) {
                    // Any nullness annotations on the element to merge in? When we have something
                    // like
                    //  @Nullable String foo
                    // the Nullable annotation can be on the element itself rather than the type,
                    // so if we print the type without knowing the nullness annotation on the
                    // element, we'll think it's unannotated and we'll display it as "String!".
                    val nullness =
                        owner?.modifiers?.findAnnotation(AnnotationItem::isNullnessAnnotation)
                    var elementAnnotations =
                        if (nullness != null) {
                            listOf(nullness)
                        } else null

                    val implicitNullness = if (owner != null) owner.implicitNullness() else null
                    val annotatedType =
                        if (implicitNullness != null) {
                            val provider =
                                if (implicitNullness == true) {
                                    codebase.getNullableAnnotationProvider()
                                } else {
                                    codebase.getNonNullAnnotationProvider()
                                }

                            // Special handling for implicitly non-null arrays that also have an
                            // implicitly non-null component type
                            if (
                                implicitNullness == false &&
                                    type is PsiArrayType &&
                                    owner != null &&
                                    owner.impliesNonNullArrayComponents()
                            ) {
                                type.componentType
                                    .annotate(provider)
                                    .createArrayType()
                                    .annotate(provider)
                            } else if (
                                implicitNullness == false &&
                                    owner is ParameterItem &&
                                    owner.containingMethod().isEnumSyntheticMethod()
                            ) {
                                // Workaround the fact that the Kotlin synthetic enum methods
                                // do not have nullness information; this must be the parameter
                                // to the valueOf(String) method.
                                // See https://youtrack.jetbrains.com/issue/KT-39667.
                                return JAVA_LANG_STRING
                            } else {
                                type.annotate(provider)
                            }
                        } else if (
                            nullness != null &&
                                owner.modifiers.isVarArg() &&
                                owner.isKotlin() &&
                                type is PsiEllipsisType
                        ) {
                            // Varargs the annotation applies to the component type instead
                            val nonNullProvider = codebase.getNonNullAnnotationProvider()
                            val provider =
                                if (nullness.isNonNull()) {
                                    nonNullProvider
                                } else codebase.getNullableAnnotationProvider()
                            val componentType = type.componentType.annotate(provider)
                            elementAnnotations = null
                            PsiEllipsisType(componentType, nonNullProvider)
                        } else {
                            type
                        }
                    val printer =
                        PsiTypePrinter(
                            codebase,
                            filter,
                            mapAnnotations,
                            kotlinStyleNulls,
                            annotated
                        )

                    printer.getAnnotatedCanonicalText(annotatedType, elementAnnotations)
                } else if (annotated) {
                    type.getCanonicalText(true)
                } else {
                    type.getCanonicalText(false)
                }
            } catch (e: Throwable) {
                return type.getCanonicalText(false)
            }
        }

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
            kotlinType: KotlinTypeInfo?
        ): PsiTypeItem {
            return when (psiType) {
                is PsiPrimitiveType -> PsiPrimitiveTypeItem(codebase, psiType, kotlinType)
                is PsiArrayType -> PsiArrayTypeItem(codebase, psiType, kotlinType)
                is PsiClassType -> {
                    if (psiType.resolve() is PsiTypeParameter) {
                        PsiVariableTypeItem(codebase, psiType, kotlinType)
                    } else {
                        PsiClassTypeItem(codebase, psiType, kotlinType)
                    }
                }
                is PsiWildcardType -> PsiWildcardTypeItem(codebase, psiType, kotlinType)
                // There are other [PsiType]s, but none can appear in API surfaces.
                else -> throw IllegalStateException("Invalid type in API surface: $psiType")
            }
        }
    }
}

/** A [PsiTypeItem] backed by a [PsiPrimitiveType]. */
internal class PsiPrimitiveTypeItem(
    override val codebase: PsiBasedCodebase,
    override val psiType: PsiPrimitiveType,
    kotlinType: KotlinTypeInfo? = null,
    override val kind: PrimitiveTypeItem.Primitive = getKind(psiType),
    override val modifiers: PsiTypeModifiers =
        PsiTypeModifiers.create(codebase, psiType, kotlinType)
) : PrimitiveTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(): PsiPrimitiveTypeItem =
        PsiPrimitiveTypeItem(
            codebase = codebase,
            psiType = psiType,
            kind = kind,
            modifiers = modifiers.duplicate()
        )

    companion object {
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
    override val codebase: PsiBasedCodebase,
    override val psiType: PsiArrayType,
    kotlinType: KotlinTypeInfo? = null,
    override val componentType: PsiTypeItem =
        create(codebase, psiType.componentType, kotlinType?.forArrayComponentType()),
    override val isVarargs: Boolean = psiType is PsiEllipsisType,
    override val modifiers: PsiTypeModifiers =
        PsiTypeModifiers.create(codebase, psiType, kotlinType)
) : ArrayTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(): PsiArrayTypeItem =
        PsiArrayTypeItem(
            codebase = codebase,
            psiType = psiType,
            componentType = componentType.duplicate(),
            isVarargs = isVarargs,
            modifiers = modifiers.duplicate()
        )
}

/** A [PsiTypeItem] backed by a [PsiClassType] that does not represent a type variable. */
internal class PsiClassTypeItem(
    override val codebase: PsiBasedCodebase,
    override val psiType: PsiClassType,
    kotlinType: KotlinTypeInfo? = null,
    override val qualifiedName: String = computeQualifiedName(psiType),
    override val parameters: List<PsiTypeItem> = computeParameters(codebase, psiType, kotlinType),
    override val outerClassType: PsiClassTypeItem? =
        computeOuterClass(psiType, codebase, kotlinType),
    // This should be able to use `psiType.name`, but that sometimes returns null.
    override val className: String = ClassTypeItem.computeClassName(qualifiedName),
    override val modifiers: PsiTypeModifiers =
        PsiTypeModifiers.create(codebase, psiType, kotlinType)
) : ClassTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(): PsiClassTypeItem =
        PsiClassTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            parameters = parameters.map { it.duplicate() },
            outerClassType = outerClassType?.duplicate(),
            className = className,
            modifiers = modifiers.duplicate()
        )

    companion object {
        private fun computeParameters(
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
                        codebase.createPsiType(outerClassName, psiType.psiContext)
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
    override val codebase: PsiBasedCodebase,
    override val psiType: PsiClassType,
    kotlinType: KotlinTypeInfo? = null,
    override val name: String = psiType.name,
    override val modifiers: PsiTypeModifiers =
        PsiTypeModifiers.create(codebase, psiType, kotlinType),
) : VariableTypeItem, PsiTypeItem(codebase, psiType) {
    override val asTypeParameter: TypeParameterItem by lazy {
        codebase.findClass(psiType) as TypeParameterItem
    }

    override fun duplicate(): PsiVariableTypeItem =
        PsiVariableTypeItem(
            codebase = codebase,
            psiType = psiType,
            name = name,
            modifiers = modifiers.duplicate()
        )
}

/** A [PsiTypeItem] backed by a [PsiWildcardType]. */
internal class PsiWildcardTypeItem(
    override val codebase: PsiBasedCodebase,
    override val psiType: PsiWildcardType,
    kotlinType: KotlinTypeInfo? = null,
    override val extendsBound: PsiTypeItem? =
        createBound(psiType.extendsBound, codebase, kotlinType),
    override val superBound: PsiTypeItem? = createBound(psiType.superBound, codebase, kotlinType),
    override val modifiers: PsiTypeModifiers =
        PsiTypeModifiers.create(codebase, psiType, kotlinType)
) : WildcardTypeItem, PsiTypeItem(codebase, psiType) {
    override fun duplicate(): PsiWildcardTypeItem =
        PsiWildcardTypeItem(
            codebase = codebase,
            psiType = psiType,
            extendsBound = extendsBound?.duplicate(),
            superBound = superBound?.duplicate(),
            modifiers = modifiers.duplicate()
        )

    companion object {
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
