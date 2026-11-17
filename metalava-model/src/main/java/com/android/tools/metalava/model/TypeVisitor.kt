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

package com.android.tools.metalava.model

interface TypeVisitor {
    fun visit(primitiveType: PrimitiveTypeItem) = Unit

    fun visit(arrayType: ArrayTypeItem) = Unit

    fun visit(classType: ClassTypeItem) = Unit

    fun visit(lambdaType: LambdaTypeItem) = Unit

    fun visit(variableType: VariableTypeItem) = Unit

    fun visit(wildcardType: WildcardTypeItem) = Unit
}

open class BaseTypeVisitor : TypeVisitor {
    override fun visit(primitiveType: PrimitiveTypeItem) {
        visitType(primitiveType)
        visitPrimitiveType(primitiveType)
    }

    override fun visit(arrayType: ArrayTypeItem) {
        visitType(arrayType)
        visitArrayType(arrayType)

        arrayType.componentType.accept(this)
    }

    override fun visit(classType: ClassTypeItem) {
        visitType(classType)
        visitClassType(classType)

        classType.outerClassType?.accept(this)
        classType.arguments.forEach { it.accept(this) }
    }

    override fun visit(lambdaType: LambdaTypeItem) {
        visitType(lambdaType)
        visitLambdaType(lambdaType)

        // Visit the component types of the lambda directly rather than converting to a
        // ClassTypeItem via asJvmClassType(). Subclasses of BaseTypeVisitor inspect referenced
        // ClassTypeItems and do not need to visit the synthetic kotlin.jvm.functions.Function*
        // wrapper class or its wildcard bounds.
        lambdaType.receiverType?.accept(this)
        lambdaType.parameterTypes.forEach { it.accept(this) }
        lambdaType.returnType.accept(this)
    }

    override fun visit(variableType: VariableTypeItem) {
        visitType(variableType)
        visitVariableType(variableType)
    }

    override fun visit(wildcardType: WildcardTypeItem) {
        visitType(wildcardType)
        visitWildcardType(wildcardType)

        wildcardType.extendsBound?.accept(this)
        wildcardType.superBound?.accept(this)
    }

    open fun visitType(type: TypeItem) = Unit

    open fun visitPrimitiveType(primitiveType: PrimitiveTypeItem) = Unit

    open fun visitArrayType(arrayType: ArrayTypeItem) = Unit

    open fun visitClassType(classType: ClassTypeItem) = Unit

    open fun visitLambdaType(lambdaType: LambdaTypeItem) = Unit

    open fun visitVariableType(variableType: VariableTypeItem) = Unit

    open fun visitWildcardType(wildcardType: WildcardTypeItem) = Unit
}

/**
 * Visitor that recurs through both a main type and a list of other types, assumed to have the same
 * structure. When visiting an inner type of the main type, it also takes the corresponding inner
 * type of each of the other types. For instance, when visiting an [ArrayTypeItem], the visitor will
 * recur to the main type component type along with a list of component types from the other types.
 * If the types do not have the same structure, the list of other types will shrink when there is no
 * corresponding inner type relative to the main type.
 */
open class MultipleTypeVisitor {
    fun visit(primitiveType: PrimitiveTypeItem, other: List<TypeItem>) {
        visitType(primitiveType, other)
        visitPrimitiveType(primitiveType, other)
    }

    fun visit(arrayType: ArrayTypeItem, other: List<TypeItem>) {
        visitType(arrayType, other)
        visitArrayType(arrayType, other)

        arrayType.componentType.accept(
            this,
            other.mapNotNull { (it as? ArrayTypeItem)?.componentType }
        )
    }

    fun visit(classType: ClassTypeItem, other: List<TypeItem>) {
        visitType(classType, other)
        visitClassType(classType, other)

        visitClassComponents(classType, other)
    }

    fun visit(lambdaType: LambdaTypeItem, other: List<TypeItem>) {
        // Call visitType and visitLambdaType with lambdaType directly (rather than delegating to
        // visit(lambdaType.asJvmClassType(), other)) so that callers such as
        // ApiLint.checkHasNullability that check `type === itemType` see the original
        // LambdaTypeItem instance.
        visitType(lambdaType, other)
        visitLambdaType(lambdaType, other)

        // Traverse the component types using asJvmClassType() so that a LambdaTypeItem (e.g.
        // from Kotlin source) and a ClassTypeItem for kotlin.jvm.functions.Function* (e.g. from a
        // signature file or Java/bytecode super method) have the same structure and are visited in
        // lockstep.
        visitClassComponents(lambdaType.asJvmClassType(), other)
    }

    private fun visitClassComponents(classType: ClassTypeItem, other: List<TypeItem>) {
        val otherClassTypes = other.mapNotNull { it.asClassType() }
        classType.outerClassType?.accept(this, otherClassTypes.mapNotNull { it.outerClassType })
        classType.arguments.forEachIndexed { index, arg ->
            arg.accept(this, otherClassTypes.mapNotNull { it.arguments.getOrNull(index) })
        }
    }

    /**
     * Converts a [ClassTypeItem] or [LambdaTypeItem] to a [ClassTypeItem] so that both can be
     * traversed in lockstep.
     */
    private fun TypeItem.asClassType(): ClassTypeItem? =
        when (this) {
            is ClassTypeItem -> this
            is LambdaTypeItem -> asJvmClassType()
            else -> null
        }

    fun visit(variableType: VariableTypeItem, other: List<TypeItem>) {
        visitType(variableType, other)
        visitVariableType(variableType, other)
    }

    fun visit(wildcardType: WildcardTypeItem, other: List<TypeItem>) {
        visitType(wildcardType, other)
        visitWildcardType(wildcardType, other)

        if (wildcardType.superBound != null) {
            wildcardType.superBound?.accept(
                this,
                other.mapNotNull { (it as? WildcardTypeItem)?.superBound }
            )
        } else {
            // Only visit the extends bound if the super bound doesn't exist (don't visit implicit
            // object bounds)
            wildcardType.extendsBound?.accept(
                this,
                other.mapNotNull { (it as? WildcardTypeItem)?.extendsBound }
            )
        }
    }

    open fun visitType(type: TypeItem, other: List<TypeItem>) = Unit

    open fun visitPrimitiveType(primitiveType: PrimitiveTypeItem, other: List<TypeItem>) = Unit

    open fun visitArrayType(arrayType: ArrayTypeItem, other: List<TypeItem>) = Unit

    open fun visitClassType(classType: ClassTypeItem, other: List<TypeItem>) = Unit

    open fun visitLambdaType(lambdaType: LambdaTypeItem, other: List<TypeItem>) = Unit

    open fun visitVariableType(variableType: VariableTypeItem, other: List<TypeItem>) = Unit

    open fun visitWildcardType(wildcardType: WildcardTypeItem, other: List<TypeItem>) = Unit
}
