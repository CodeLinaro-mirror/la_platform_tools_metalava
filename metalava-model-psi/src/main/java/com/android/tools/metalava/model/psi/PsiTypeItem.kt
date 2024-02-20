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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
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
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.TypeConversionUtil
import java.lang.IllegalStateException
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeMappingMode
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

/** Represents a type backed by PSI */
sealed class PsiTypeItem(
    val psiType: PsiType,
    modifiers: TypeModifiers,
) : DefaultTypeItem(modifiers) {

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
            typeItemFactory: PsiTypeItemFactory,
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
                        typeItemFactory = typeItemFactory,
                    )
                is PsiClassType -> {
                    val psiClass = psiType.resolve()
                    val typeParameterScope = typeItemFactory.typeParameterScope
                    val typeParameterItem =
                        when (psiClass) {
                            // If the type resolves to a PsiTypeParameter then the TypeParameterItem
                            // must exist.
                            is PsiTypeParameter -> {
                                val name = psiClass.qualifiedName ?: psiType.name
                                typeParameterScope.getTypeParameter(name)
                            }
                            // If the type could not be resolved then the TypeParameterItem might
                            // exist.
                            null ->
                                psiType.className?.let { name ->
                                    typeParameterScope.findTypeParameter(name)
                                }
                            // Else it is not a TypeParameterItem.
                            else -> null
                        }

                    if (typeParameterItem != null) {
                        PsiVariableTypeItem.create(
                            codebase = codebase,
                            psiType = psiType,
                            kotlinType = kotlinType,
                            typeParameterItem = typeParameterItem,
                        )
                    } else {
                        PsiClassTypeItem.create(
                            codebase = codebase,
                            psiType = psiType,
                            kotlinType = kotlinType,
                            typeUse = typeUse,
                            typeItemFactory = typeItemFactory,
                        )
                    }
                }
                is PsiWildcardType ->
                    PsiWildcardTypeItem.create(
                        codebase = codebase,
                        psiType = psiType,
                        kotlinType = kotlinType,
                        typeItemFactory = typeItemFactory,
                    )
                // There are other [PsiType]s, but none can appear in API surfaces.
                else -> throw IllegalStateException("Invalid type in API surface: $psiType")
            }
        }
    }
}

/** A [PsiTypeItem] backed by a [PsiPrimitiveType]. */
internal class PsiPrimitiveTypeItem(
    psiType: PsiType,
    override val kind: PrimitiveTypeItem.Primitive,
    modifiers: TypeModifiers,
) : PrimitiveTypeItem, PsiTypeItem(psiType, modifiers) {
    override fun duplicate(): PsiPrimitiveTypeItem =
        PsiPrimitiveTypeItem(psiType = psiType, kind = kind, modifiers = modifiers.duplicate())

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiPrimitiveType,
            kotlinType: KotlinTypeInfo?,
        ) =
            PsiPrimitiveTypeItem(
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
    psiType: PsiType,
    override val componentType: PsiTypeItem,
    override val isVarargs: Boolean,
    modifiers: TypeModifiers,
) : ArrayTypeItem, PsiTypeItem(psiType, modifiers) {
    override fun duplicate(componentType: TypeItem): ArrayTypeItem =
        PsiArrayTypeItem(
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
            typeItemFactory: PsiTypeItemFactory,
        ) =
            PsiArrayTypeItem(
                psiType = psiType,
                componentType =
                    create(
                        codebase,
                        psiType.componentType,
                        kotlinType?.forArrayComponentType(),
                        typeItemFactory,
                    ),
                isVarargs = psiType is PsiEllipsisType,
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )
    }
}

/** A [PsiTypeItem] backed by a [PsiClassType] that does not represent a type variable. */
internal class PsiClassTypeItem(
    private val codebase: Codebase,
    psiType: PsiType,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: PsiClassTypeItem?,
    override val className: String,
    modifiers: TypeModifiers,
) : ClassTypeItem, PsiTypeItem(psiType, modifiers) {

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { codebase.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    override fun duplicate(
        outerClass: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem =
        PsiClassTypeItem(
            codebase = codebase,
            psiType = psiType,
            qualifiedName = qualifiedName,
            arguments = arguments,
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
            typeItemFactory: PsiTypeItemFactory,
        ): PsiClassTypeItem {
            val qualifiedName = computeQualifiedName(psiType)
            return PsiClassTypeItem(
                codebase = codebase,
                psiType = psiType,
                qualifiedName = qualifiedName,
                arguments =
                    computeTypeArguments(
                        codebase,
                        psiType,
                        kotlinType,
                        typeItemFactory,
                    ),
                outerClassType =
                    computeOuterClass(
                        psiType,
                        codebase,
                        kotlinType,
                        typeItemFactory,
                    ),
                // This should be able to use `psiType.name`, but that sometimes returns null.
                className = ClassTypeItem.computeClassName(qualifiedName),
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType, typeUse),
            )
        }

        private fun computeTypeArguments(
            codebase: PsiBasedCodebase,
            psiType: PsiClassType,
            kotlinType: KotlinTypeInfo?,
            typeItemFactory: PsiTypeItemFactory
        ): List<TypeArgumentTypeItem> {
            val psiParameters =
                psiType.parameters.toList().ifEmpty {
                    // Sometimes, when a PsiClassType's arguments are empty it is not because there
                    // are no arguments but due to a bug in Psi somewhere. Check to see if the
                    // kotlin type info has a different set of type arguments and if it has then use
                    // that to fix the type, otherwise just assume it should be empty.
                    kotlinType?.ktType?.let { ktType ->
                        (ktType as? KtNonErrorClassType)?.ownTypeArguments?.ifNotEmpty {
                            fixUpPsiTypeMissingTypeArguments(psiType, kotlinType)
                        }
                    }
                        ?: emptyList()
                }

            return psiParameters.mapIndexed { i, param ->
                create(codebase, param, kotlinType?.forParameter(i), typeItemFactory)
                    as TypeArgumentTypeItem
            }
        }

        /**
         * Fix up a [PsiClassType] that is missing type arguments.
         *
         * This seems to happen in a very limited situation. The example that currently fails, but
         * there may be more, appears to be due to an impedance mismatch between Kotlin collections
         * and Java collections.
         *
         * Assume the following Kotlin and Java classes from the standard libraries:
         * ```
         * package kotlin.collections
         * public interface MutableCollection<E> : Collection<E>, MutableIterable<E> {
         *     ...
         *     public fun addAll(elements: Collection<E>): Boolean
         *     public fun containsAll(elements: Collection<E>): Boolean
         *     public fun removeAll(elements: Collection<E>): Boolean
         *     public fun retainAll(elements: Collection<E>): Boolean
         *     ...
         * }
         *
         * package java.util;
         * public interface Collection<E> extends Iterable<E> {
         *     boolean addAll(Collection<? extends E> c);
         *     boolean containsAll(Collection<?> c);
         *     boolean removeAll(Collection<?> c);
         *     boolean retainAll(Collection<?> c);
         * }
         * ```
         *
         * The given the following class this function is called for the types of the parameters of
         * the `removeAll`, `retainAll` and `containsAll` methods but not for the `addAll` method.
         *
         * ```
         * abstract class Foo<Z> : MutableCollection<Z> {
         *     override fun addAll(elements: Collection<Z>): Boolean = true
         *     override fun containsAll(elements: Collection<Z>): Boolean = true
         *     override fun removeAll(elements: Collection<Z>): Boolean = true
         *     override fun retainAll(elements: Collection<Z>): Boolean = true
         * }
         * ```
         *
         * Metalava and/or the underlying Psi model, appears to treat the `MutableCollection` in
         * `Foo` as if it was a `java.util.Collection`, even though it is referring to
         * `kotlin.collections.Collection`. So, both `Foo` and `MutableCollection` are reported as
         * extending `java.util.Collection`.
         *
         * So, you have the following two methods (mapped into Java classes):
         *
         * From `java.util.Collection` itself:
         * ```
         *      boolean containsAll(java.util.Collection<?> c);
         * ```
         *
         * And from `kotlin.collections.MutableCollection`:
         * ```
         *     public fun containsAll(elements: java.util.Collection<E>): Boolean
         * ```
         *
         * But, strictly speaking that is not allowed for a couple of reasons:
         * 1. `java.util.Collection` is not covariant because it is mutable. However,
         *    `kotlin.collections.Collection` is covariant because it immutable.
         * 2. `Collection<Z>` is more restrictive than `Collection<?>`. Java will let you try and
         *    remove a collection of `Number` from a collection of `String` even though it is
         *    meaningless. Kotlin's approach is more correct but only possible because its
         *    `Collection` is immutable.
         *
         * The [kotlinType] seems to have handled that issue reasonably well producing a type of
         * `java.util.Collection<? extends Z>`. Unfortunately, when that is converted to a `PsiType`
         * the `PsiType` for `Z` does not resolve to a `PsiTypeParameter`.
         *
         * The wildcard is correct.
         */
        private fun fixUpPsiTypeMissingTypeArguments(
            psiType: PsiClassType,
            kotlinType: KotlinTypeInfo
        ): List<PsiType> {
            if (kotlinType.analysisSession == null || kotlinType.ktType == null) return emptyList()

            val ktType = kotlinType.ktType as KtNonErrorClassType

            // Restrict this fix to the known issue.
            val className = psiType.className
            if (className != "Collection") {
                return emptyList()
            }

            // Convert the KtType to PsiType.
            //
            // Convert the whole type rather than extracting the type parameters and converting them
            // separately because the result depends on the parameterized class, i.e.
            // `java.util.Collection` in this case. Also, type arguments can be wildcards but
            // wildcards cannot exist on their own. It will probably be relying on undefined
            // behavior to try and convert a wildcard on their own.
            val psiTypeFromKotlin =
                kotlinType.analysisSession.run {
                    // Use the default mode so that the resulting psiType is
                    // `java.util.Collection<? extends Z>`.
                    val mode = KtTypeMappingMode.DEFAULT
                    ktType.asPsiType(kotlinType.context, false, mode = mode)
                } as? PsiClassType
            return psiTypeFromKotlin?.parameters?.toList() ?: emptyList()
        }

        internal fun computeQualifiedName(psiType: PsiClassType): String {
            // It should be possible to do `psiType.rawType().canonicalText` instead, but this
            // doesn't
            // always work if psi is unable to resolve the reference.
            // See https://youtrack.jetbrains.com/issue/KTIJ-27093 for more details.
            return PsiNameHelper.getQualifiedClassName(psiType.canonicalText, true)
        }

        private fun computeOuterClass(
            psiType: PsiClassType,
            codebase: PsiBasedCodebase,
            kotlinType: KotlinTypeInfo?,
            typeItemFactory: PsiTypeItemFactory
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
                    (create(
                            codebase,
                            psiOuterClassType,
                            kotlinType?.forOuterClass(),
                            typeItemFactory,
                        )
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
    psiType: PsiType,
    modifiers: TypeModifiers,
    override val asTypeParameter: TypeParameterItem,
) : VariableTypeItem, PsiTypeItem(psiType, modifiers) {

    override val name: String = asTypeParameter.name()

    override fun duplicate(): PsiVariableTypeItem =
        PsiVariableTypeItem(
            psiType = psiType,
            modifiers = modifiers.duplicate(),
            asTypeParameter = asTypeParameter,
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiClassType,
            kotlinType: KotlinTypeInfo?,
            typeParameterItem: TypeParameterItem,
        ) =
            PsiVariableTypeItem(
                psiType = psiType,
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
                asTypeParameter = typeParameterItem,
            )
    }
}

/** A [PsiTypeItem] backed by a [PsiWildcardType]. */
internal class PsiWildcardTypeItem(
    psiType: PsiType,
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
    modifiers: TypeModifiers,
) : WildcardTypeItem, PsiTypeItem(psiType, modifiers) {
    override fun duplicate(
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): WildcardTypeItem =
        PsiWildcardTypeItem(
            psiType = psiType,
            extendsBound = extendsBound,
            superBound = superBound,
            modifiers = modifiers.duplicate()
        )

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiType: PsiWildcardType,
            kotlinType: KotlinTypeInfo?,
            typeItemFactory: PsiTypeItemFactory,
        ) =
            PsiWildcardTypeItem(
                psiType = psiType,
                extendsBound =
                    createBound(
                        psiType.extendsBound,
                        codebase,
                        kotlinType,
                        typeItemFactory,
                    ),
                superBound =
                    createBound(
                        psiType.superBound,
                        codebase,
                        kotlinType,
                        typeItemFactory,
                    ),
                modifiers = PsiTypeModifiers.create(codebase, psiType, kotlinType),
            )

        /**
         * If a [PsiWildcardType] doesn't have a bound, the bound is represented as the null
         * [PsiType] instead of just `null`.
         */
        private fun createBound(
            bound: PsiType,
            codebase: PsiBasedCodebase,
            kotlinType: KotlinTypeInfo?,
            typeItemFactory: PsiTypeItemFactory,
        ): ReferenceTypeItem? {
            return if (bound == PsiTypes.nullType()) {
                null
            } else {
                // Use the same Kotlin type, because the wildcard isn't its own level in the KtType.
                create(codebase, bound, kotlinType, typeItemFactory) as ReferenceTypeItem
            }
        }
    }
}
