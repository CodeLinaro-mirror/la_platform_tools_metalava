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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.LambdaTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeTransformer
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultLambdaTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem

/**
 * A [DefaultTypeItemFactory] whose underlying type is another model's [TypeItem] that this will
 * snapshot.
 *
 * TODO: Optimize by reusing them where possible as they are immutable.
 */
internal class SnapshotTypeItemFactory(
    private val codebase: Codebase,
    typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
) : DefaultTypeItemFactory<TypeItem, SnapshotTypeItemFactory>(typeParameterScope), TypeTransformer {

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        SnapshotTypeItemFactory(codebase, scope)

    override fun getType(
        underlyingType: TypeItem,
        contextNullability: ContextNullability,
        isVarArg: Boolean
    ) = underlyingType.transform(this)

    /**
     * Take a snapshot of the [TypeModifiers].
     *
     * Only the [TypeModifiers.annotations] is model and [Codebase] dependent. All the other parts
     * are model independent with no connection to a specific [Codebase]. So, this is reused as is
     * if there are no [TypeModifiers.annotations].
     */
    private fun TypeModifiers.snapshot() =
        if (annotations.isEmpty()) {
            this
        } else {
            DefaultTypeModifiers(
                annotations.map { it.snapshot(codebase) },
                nullability,
            )
        }

    override fun transform(typeItem: ArrayTypeItem) =
        DefaultArrayTypeItem(
            typeItem.modifiers.snapshot(),
            typeItem.componentType.transform(this),
            typeItem.isVarargs,
        )

    override fun transform(typeItem: ClassTypeItem) =
        DefaultClassTypeItem(
            codebase,
            typeItem.modifiers.snapshot(),
            typeItem.qualifiedName,
            typeItem.arguments.map { it.transform(this) },
            typeItem.outerClassType?.transform(this),
        )

    override fun transform(typeItem: LambdaTypeItem) =
        DefaultLambdaTypeItem(
            codebase,
            typeItem.modifiers.snapshot(),
            typeItem.qualifiedName,
            typeItem.arguments.map { it.transform(this) },
            typeItem.outerClassType?.transform(this),
            typeItem.isSuspend,
            typeItem.receiverType?.transform(this),
            typeItem.parameterTypes.map { it.transform(this) },
            typeItem.returnType.transform(this),
        )

    override fun transform(typeItem: PrimitiveTypeItem) =
        DefaultPrimitiveTypeItem(typeItem.modifiers.snapshot(), typeItem.kind)

    override fun transform(typeItem: VariableTypeItem) =
        DefaultVariableTypeItem(
            typeItem.modifiers.snapshot(),
            typeParameterScope.getTypeParameter(typeItem.name),
        )

    override fun transform(typeItem: WildcardTypeItem) =
        DefaultWildcardTypeItem(
            typeItem.modifiers.snapshot(),
            typeItem.extendsBound?.transform(this),
            typeItem.superBound?.transform(this),
        )
}
