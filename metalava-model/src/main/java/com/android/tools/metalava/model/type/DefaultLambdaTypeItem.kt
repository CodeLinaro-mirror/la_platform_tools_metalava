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
import com.android.tools.metalava.model.DefaultStandaloneTypeItem
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultLambdaTypeItem(
    override val isSuspend: Boolean,
    override val receiverType: TypeItem?,
    override val parameterTypes: List<TypeItem>,
    override val returnType: TypeItem,
    private val jvmClassType: ClassTypeItem,
) :
    DefaultStandaloneTypeItem(
        modifiers = jvmClassType.modifiers,
        isValueClassType = jvmClassType.isValueClassType,
    ),
    LambdaTypeItem {

    override fun asJvmClassType(): ClassTypeItem = jvmClassType

    override fun substitute(
        modifiers: TypeModifiers,
        receiverType: TypeItem?,
        parameterTypes: List<TypeItem>,
        returnType: TypeItem,
        jvmClassType: ClassTypeItem,
    ): LambdaTypeItem {
        val newJvmClassType = jvmClassType.substitute(modifiers = modifiers)
        return if (
            receiverType !== this.receiverType ||
                parameterTypes !== this.parameterTypes ||
                returnType !== this.returnType ||
                newJvmClassType !== this.jvmClassType
        ) {
            TypeItem.createLambdaType(
                isSuspend = isSuspend,
                receiverType = receiverType,
                parameterTypes = parameterTypes,
                returnType = returnType,
                jvmClassType = newJvmClassType,
            )
        } else this
    }

    override fun equalsImpl(other: DefaultStandaloneTypeItem): Boolean {
        if (other !is LambdaTypeItem) return false
        return isSuspend == other.isSuspend &&
            receiverType == other.receiverType &&
            parameterTypes == other.parameterTypes &&
            returnType == other.returnType &&
            jvmClassType == other.asJvmClassType()
    }

    override fun hashCodeImpl(): Int {
        var result = isSuspend.hashCode()
        result = 31 * result + (receiverType?.hashCode() ?: 0)
        result = 31 * result + parameterTypes.hashCode()
        result = 31 * result + returnType.hashCode()
        result = 31 * result + jvmClassType.hashCode()
        return result
    }
}
