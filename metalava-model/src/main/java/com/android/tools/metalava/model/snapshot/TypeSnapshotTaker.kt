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
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeTransformer
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem

/**
 * Take a snapshot of a [TypeItem].
 *
 * TODO: Optimize by reusing them where possible as they are immutable.
 */
class TypeSnapshotTaker(private val codebase: Codebase) : TypeTransformer {

    /**
     * Take a snapshot of the [TypeModifiers].
     *
     * Only the [TypeModifiers.annotations] is model and [Codebase] dependent. All the other parts
     * are model independent with no connection to a specific [Codebase]. So, this is reused as is
     * if there are no [TypeModifiers.annotations].
     */
    private fun TypeModifiers.snapshot(): TypeModifiers {
        return if (annotations.isEmpty()) {
            this
        } else {
            DefaultTypeModifiers(
                annotations.map { it.snapshot(codebase) },
                nullability,
            )
        }
    }

    override fun transform(typeItem: ArrayTypeItem): ArrayTypeItem {
        return DefaultArrayTypeItem(
            typeItem.modifiers.snapshot(),
            typeItem.componentType.transform(this),
            typeItem.isVarargs,
        )
    }

    override fun transform(typeItem: ClassTypeItem): ClassTypeItem {
        return DefaultClassTypeItem(
            codebase,
            typeItem.modifiers.snapshot(),
            typeItem.qualifiedName,
            typeItem.arguments.map { it.transform(this) },
            typeItem.outerClassType?.transform(this),
        )
    }

    override fun transform(typeItem: LambdaTypeItem): LambdaTypeItem {
        error("Snapshotting LambdaTypeItem not supported yet")
    }

    override fun transform(typeItem: PrimitiveTypeItem): PrimitiveTypeItem {
        return DefaultPrimitiveTypeItem(
            typeItem.modifiers.snapshot(),
            typeItem.kind,
        )
    }

    override fun transform(typeItem: VariableTypeItem): VariableTypeItem {
        error("Snapshotting VariableTypeItem not supported yet")
    }

    override fun transform(typeItem: WildcardTypeItem): WildcardTypeItem {
        return DefaultWildcardTypeItem(
            typeItem.modifiers.snapshot(),
            typeItem.extendsBound?.transform(this),
            typeItem.superBound?.transform(this),
        )
    }
}
