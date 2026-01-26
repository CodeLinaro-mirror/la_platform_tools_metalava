/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind

/** Creates [TypeItem]s from [Type]s. */
internal class TurbineTypeItemFactory(
    private val initializer: TurbineCodebaseInitialiser,
    private val annotationFactory: TurbineAnnotationFactory,
    typeParameterScope: TypeParameterScope,
) : DefaultTypeItemFactory<Type, TurbineTypeItemFactory>(typeParameterScope) {

    private val codebase: DefaultCodebase = initializer.codebase

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        TurbineTypeItemFactory(initializer, annotationFactory, scope)

    override fun getType(
        underlyingType: Type,
        contextNullability: ContextNullability,
        isVarArg: Boolean,
    ) = createType(underlyingType, isVarArg, contextNullability)

    private fun createModifiers(
        annos: List<AnnoInfo>,
        contextNullability: ContextNullability,
    ): TypeModifiers {
        val typeAnnotations = annotationFactory.createAnnotations(annos)
        // Compute the nullability, factoring in any context nullability and type annotations.
        // Turbine does not support kotlin so the kotlin nullability is always null.
        val nullability = contextNullability.compute(null, typeAnnotations)
        return DefaultTypeModifiers.create(typeAnnotations, nullability)
    }

    internal fun createType(
        type: Type,
        isVarArg: Boolean,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        return when (val kind = type.tyKind()) {
            Type.TyKind.PRIM_TY -> {
                type as Type.PrimTy
                // Primitives are always non-null.
                val modifiers = createModifiers(type.annos(), ContextNullability.forceNonNull)
                when (type.primkind()) {
                    TurbineConstantTypeKind.BOOLEAN ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.BOOLEAN)
                    TurbineConstantTypeKind.BYTE ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.BYTE)
                    TurbineConstantTypeKind.CHAR ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.CHAR)
                    TurbineConstantTypeKind.DOUBLE ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.DOUBLE)
                    TurbineConstantTypeKind.FLOAT ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.FLOAT)
                    TurbineConstantTypeKind.INT ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.INT)
                    TurbineConstantTypeKind.LONG ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.LONG)
                    TurbineConstantTypeKind.SHORT ->
                        TypeItem.createPrimitiveType(modifiers, PrimitiveTypeItem.Primitive.SHORT)
                    else ->
                        throw IllegalStateException("Invalid primitive type in API surface: $type")
                }
            }
            Type.TyKind.ARRAY_TY -> {
                createArrayType(type as Type.ArrayTy, isVarArg, contextNullability)
            }
            Type.TyKind.CLASS_TY -> {
                type as Type.ClassTy
                var outerClass: ClassTypeItem? = null
                // A ClassTy is represented by list of SimpleClassTY each representing a nested
                // class. e.g. , Outer.Inner.Inner1 will be represented by three simple classes
                // Outer, Outer.Inner and Outer.Inner.Inner1
                val iterator = type.classes().iterator()
                while (iterator.hasNext()) {
                    val simpleClass = iterator.next()

                    // Select the ContextNullability. If there is another SimpleClassTy after this
                    // then this is an outer class which can never be null, so force it to be
                    // non-null. Otherwise, this is the nested class so use the supplied
                    // ContextNullability.
                    val actualContextNullability =
                        if (iterator.hasNext()) {
                            // For all outer class types, set the nullability to non-null.
                            ContextNullability.forceNonNull
                        } else {
                            // Use the supplied ContextNullability.
                            contextNullability
                        }

                    outerClass =
                        createNestedClassType(simpleClass, outerClass, actualContextNullability)
                }
                outerClass!!
            }
            Type.TyKind.TY_VAR -> {
                type as Type.TyVar
                val modifiers = createModifiers(type.annos(), contextNullability)
                val typeParameter = typeParameterScope.getTypeParameter(type.sym().name())
                TypeItem.createVariableType(modifiers, typeParameter)
            }
            Type.TyKind.WILD_TY -> {
                type as Type.WildTy
                // Wildcards themselves don't have a defined nullability.
                val modifiers =
                    createModifiers(type.annotations(), ContextNullability.forceUndefined)
                when (type.boundKind()) {
                    Type.WildTy.BoundKind.UPPER -> {
                        val upperBound = createWildcardBound(type.bound())
                        TypeItem.createWildcardType(modifiers, upperBound, null)
                    }
                    Type.WildTy.BoundKind.LOWER -> {
                        // LowerBounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        val lowerBound = createWildcardBound(type.bound())
                        TypeItem.createWildcardType(modifiers, upperBound, lowerBound)
                    }
                    Type.WildTy.BoundKind.NONE -> {
                        // Unbounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        TypeItem.createWildcardType(modifiers, upperBound, null)
                    }
                    else ->
                        throw IllegalStateException("Invalid wildcard type in API surface: $type")
                }
            }
            Type.TyKind.VOID_TY ->
                TypeItem.createPrimitiveType(
                    // Primitives are always non-null.
                    createModifiers(emptyList(), ContextNullability.forceNonNull),
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.NONE_TY ->
                TypeItem.createPrimitiveType(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.emptyNonNullModifiers,
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.ERROR_TY -> {
                // This is case of unresolved superclass or implemented interface
                type as Type.ErrorTy
                TypeItem.createClassType(
                    codebase,
                    DefaultTypeModifiers.emptyUndefinedModifiers,
                    type.name(),
                    emptyList(),
                    null,
                )
            }
            else -> throw IllegalStateException("Invalid type in API surface: $kind")
        }
    }

    private fun createWildcardBound(type: Type) = getGeneralType(type) as ReferenceTypeItem

    private fun createArrayType(
        type: Type.ArrayTy,
        isVarArg: Boolean,
        contextNullability: ContextNullability,
    ): TypeItem {
        // Create a component type item from the Turbine element type, using a special context
        // nullability for it.
        val componentTypeItem = getType(type.elementType(), contextNullability.forComponentType())

        // Create an array type item from the Turbine array type using the supplied isVarArg and
        // contextual nullability.
        val modifiers = createModifiers(type.annos(), contextNullability)
        return TypeItem.createArrayType(modifiers, componentTypeItem, isVarArg)
    }

    /**
     * Retrieves the `ClassTypeItem` representation of the outer class associated with a given
     * nested class type. Intended for types that are not explicitly mentioned within the source
     * code.
     *
     * @param type The `Type.ClassTy.SimpleClassTy` object representing the nested class.
     * @return The `ClassTypeItem` representing the outer class.
     */
    private fun getOuterClassType(type: Type.ClassTy.SimpleClassTy): ClassTypeItem {
        val className = type.sym().qualifiedName
        val classTypeElement = initializer.getTypeElement(className)!!
        return createOuterClassType(classTypeElement.enclosingElement!!)!!
    }

    /**
     * Constructs a `ClassTypeItem` representation from a type element. Intended for types that are
     * not explicitly mentioned within the source code.
     *
     * @param element The `Element` object representing the type.
     * @return The corresponding `ClassTypeItem`, or null if the `element` does not represent a
     *   declared type.
     */
    private fun createOuterClassType(element: Element): ClassTypeItem? {
        if (element.asType().kind != TypeKind.DECLARED) return null

        val outerClassElement = element.enclosingElement!!
        val outerClassTypeItem = createOuterClassType(outerClassElement)

        element as TypeElement

        // Since this type was never part of source , it won't have any annotation or arguments
        val modifiers = DefaultTypeModifiers.emptyNonNullModifiers
        val classTypeItem =
            TypeItem.createClassType(
                codebase,
                modifiers,
                element.qualifiedName.toString(), // Assuming qualifiedName is available on element
                emptyList(),
                outerClassTypeItem
            )
        return classTypeItem
    }

    private fun createNestedClassType(
        type: Type.ClassTy.SimpleClassTy,
        outerClass: ClassTypeItem?,
        contextNullability: ContextNullability,
    ): ClassTypeItem {
        val sym = type.sym()
        val outerClassItem =
            if (sym.binaryName().contains("$") && outerClass == null) {
                getOuterClassType(type)
            } else {
                outerClass
            }

        val modifiers = createModifiers(type.annos(), contextNullability)
        val qualifiedName = sym.qualifiedName
        val parameters = type.targs().map { getGeneralType(it) as TypeArgumentTypeItem }
        return TypeItem.createClassType(
            codebase,
            modifiers,
            qualifiedName,
            parameters,
            outerClassItem
        )
    }
}
