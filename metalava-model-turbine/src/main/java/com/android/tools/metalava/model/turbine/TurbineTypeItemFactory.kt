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
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.google.common.collect.ImmutableList
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type

/** Creates [TypeItem]s from [Type]s. */
internal class TurbineTypeItemFactory(
    private val initializer: TurbineCodebaseInitialiser,
    private val annotationFactory: TurbineAnnotationFactory,
    typeParameterScope: TypeParameterScope,
) : DefaultTypeItemFactory<Type, TurbineTypeItemFactory>(typeParameterScope) {

    // TODO(b/479907812): Provide a non-null FieldResolver.
    val fieldResolver: FieldResolver?
        get() = null

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
        val typeAnnotations = annotationFactory.createAnnotations(annos, fieldResolver)
        // Compute the nullability, factoring in any context nullability and type annotations.
        // Turbine does not support kotlin so the kotlin nullability is always null.
        val nullability = contextNullability.compute(null, typeAnnotations)
        return TypeModifiers.create(typeAnnotations, nullability)
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
                createClassTypeItemFromSimpleTys(type.classes(), contextNullability)!!
            }
            Type.TyKind.TY_VAR -> {
                type as Type.TyVar
                val modifiers = createModifiers(type.annos(), contextNullability.forTypeVariable())
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
                    TypeModifiers.emptyNonNullModifiers,
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.ERROR_TY -> {
                // This is case of unresolved superclass or implemented interface
                type as Type.ErrorTy
                TypeItem.createClassType(
                    TypeModifiers.emptyUndefinedModifiers,
                    type.name(),
                    createTypeArguments(type.targs()),
                    null,
                )
            }
            else -> throw IllegalStateException("Invalid type in API surface: $kind")
        }
    }

    /**
     * Create a [ClassTypeItem] from a list of [Type.ClassTy.SimpleClassTy] using
     * [contextNullability].
     */
    private fun createClassTypeItemFromSimpleTys(
        simpleTys: List<Type.ClassTy.SimpleClassTy>,
        contextNullability: ContextNullability
    ): ClassTypeItem? {
        var outerClass: ClassTypeItem? = null
        // A ClassTy is represented by list of SimpleClassTy each representing a nested
        // class. e.g. , Outer.Inner.Inner1 will be represented by three simple classes
        // Outer, Outer.Inner and Outer.Inner.Inner1
        val iterator = simpleTys.iterator()
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

            outerClass = createNestedClassType(simpleClass, outerClass, actualContextNullability)
        }
        return outerClass
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
     * Create the [ClassTypeItem] representation of the outer class associated with [classSymbol].
     *
     * If [classSymbol] is not for a nested class then this returns null.
     */
    private fun optionalOuterClassType(classSymbol: ClassSymbol): ClassTypeItem? {
        val binaryName = classSymbol.binaryName()

        // If it does not contain a $ then it is not a nested class.
        val firstDollarIndex = binaryName.indexOf('$')
        if (firstDollarIndex == -1) {
            return null
        }

        // Construct a list pf SimpleClassTys from the symbol.
        val simpleClassTys = buildList {
            val length = binaryName.length

            // Iterate over each $ in the binary name of the symbol creating a SimpleClassTy for
            // each containing class. e.g. when given pkg/A$B$C it will construct SimpleClassTys for
            // * pkg/A
            // * pkg/A$B
            var startIndex = firstDollarIndex
            while (startIndex < length) {
                // Find the next $, if none exists then drop out. That ignores the innermost class
                // which is created by the caller.
                val nextDollarIndex = binaryName.indexOf('$', startIndex)
                if (nextDollarIndex == -1) {
                    break
                }

                // Create a symbol for the outer class.
                val outerClassSymbol = ClassSymbol(binaryName.substring(0, nextDollarIndex))

                // Wrap that in a SimpleClassTy
                val simpleClassTy =
                    Type.ClassTy.SimpleClassTy.create(
                        outerClassSymbol,
                        ImmutableList.of(),
                        ImmutableList.of(),
                    )

                // Add it to the list.
                add(simpleClassTy)

                // Skip past the $.
                startIndex = nextDollarIndex + 1
            }
        }

        // Create a ClassTypeItem from the list of SimpleClassTys.
        return createClassTypeItemFromSimpleTys(simpleClassTys, ContextNullability.forceNonNull)
    }

    private fun createNestedClassType(
        type: Type.ClassTy.SimpleClassTy,
        outerClass: ClassTypeItem?,
        contextNullability: ContextNullability,
    ): ClassTypeItem {
        val sym = type.sym()

        // If no outer class was provided then try and get an outer class type item from the type
        // symbol.
        val outerClassItem = outerClass ?: optionalOuterClassType(type.sym())

        val modifiers = createModifiers(type.annos(), contextNullability)
        val qualifiedName = sym.qualifiedName
        val parameters = createTypeArguments(type.targs())
        return TypeItem.createClassType(
            modifiers,
            qualifiedName,
            parameters,
            outerClassItem,
        )
    }

    /** Create a list of [TypeArgumentTypeItem]s from [types]. */
    private fun createTypeArguments(types: ImmutableList<Type>) =
        types.map { getGeneralType(it) as TypeArgumentTypeItem }
}
