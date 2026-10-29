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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultLambdaTypeItem(
    modifiers: TypeModifiers,
    qualifiedName: String,
    arguments: List<TypeArgumentTypeItem>,
    outerClassType: ClassTypeItem?,
    override val isSuspend: Boolean,
    override val receiverType: TypeItem?,
    override val parameterTypes: List<TypeItem>,
    override val returnType: TypeItem,
    isValueClassType: Boolean = false,
) :
    DefaultClassTypeItem(
        modifiers = modifiers,
        qualifiedName = qualifiedName,
        arguments = arguments,
        outerClassType = outerClassType,
        isValueClassType = isValueClassType,
    ),
    LambdaTypeItem {

    /** Cached result of [asJvmClassType]. */
    private lateinit var jvmClassType: ClassTypeItem

    override fun asJvmClassType(): ClassTypeItem {
        if (!::jvmClassType.isInitialized) {
            jvmClassType = createJvmClassType()
        }
        return jvmClassType
    }

    /**
     * Create the [ClassTypeItem] representing the Kotlin JVM `Function<N>` type for this lambda.
     */
    private fun createJvmClassType(): ClassTypeItem {
        // Combine the optional receiver type, parameter types, and return type into a single
        // list of type arguments for the Kotlin Function<N> class.
        val arguments =
            buildList((if (receiverType == null) 0 else 1) + parameterTypes.size + 1) {
                receiverType?.let { add(it.asTypeArgument()) }
                parameterTypes.mapTo(this) { it.asTypeArgument() }
                add(returnType.asTypeArgument())
            }

        // The function arity doesn't include the return type.
        val qualifiedName = "kotlin.jvm.functions.Function${arguments.size - 1}"
        return TypeItem.createClassType(
            modifiers = modifiers,
            qualifiedName = qualifiedName,
            arguments = arguments,
            outerClassType = null,
            isValueClassType = isValueClassType,
        )
    }

    /**
     * Convert this [TypeItem] into a [TypeArgumentTypeItem] suitable for use as a type argument of
     * a Kotlin `Function<N>` class.
     */
    private fun TypeItem.asTypeArgument(): TypeArgumentTypeItem =
        when (this) {
            is PrimitiveTypeItem -> {
                // Primitive types cannot be used as type arguments in JVM generics so map them to
                // their boxed equivalents; void (from Kotlin Unit) maps to kotlin.Unit.
                val qualifiedName =
                    if (kind == PrimitiveTypeItem.Primitive.VOID) {
                        "kotlin.Unit"
                    } else {
                        kind.wrapperClass.canonicalName
                    }
                TypeItem.createClassType(
                    modifiers = modifiers,
                    qualifiedName = qualifiedName,
                    arguments = emptyList(),
                    outerClassType = null,
                    isValueClassType = isValueClassType,
                )
            }
            // Nested lambda types must also be converted to their Kotlin Function<N> class type.
            // This must come before ReferenceTypeItem while LambdaTypeItem extends
            // ClassTypeItem.
            is LambdaTypeItem -> asJvmClassType()
            is ReferenceTypeItem -> this
            else -> error("Unexpected type $this ($javaClass)")
        }

    override fun substitute(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>,
    ): LambdaTypeItem =
        if (requiresNewInstance(modifiers, outerClassType, arguments))
            DefaultLambdaTypeItem(
                modifiers,
                qualifiedName,
                arguments,
                outerClassType,
                isSuspend,
                receiverType,
                parameterTypes,
                returnType,
            )
        else this
}
