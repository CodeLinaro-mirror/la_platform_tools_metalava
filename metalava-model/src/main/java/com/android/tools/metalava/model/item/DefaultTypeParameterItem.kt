/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem

/** A [TypeParameterItem] implementation suitable for use by multiple models. */
open class DefaultTypeParameterItem(
    override val codebase: Codebase,
    modifiers: BaseModifierList,
    private val name: String,
    private val isReified: Boolean,
) : TypeParameterItem {

    final override val modifiers: ModifierList = modifiers.toImmutable()

    final override fun name() = name

    /** Must only be used by [type] to cache its result. */
    private lateinit var variableTypeItem: VariableTypeItem

    override fun type(): VariableTypeItem {
        if (!::variableTypeItem.isInitialized) {
            variableTypeItem = createVariableTypeItem()
        }
        return variableTypeItem
    }

    /** Create a [VariableTypeItem] for this [TypeParameterItem]. */
    protected open fun createVariableTypeItem(): VariableTypeItem =
        DefaultVariableTypeItem(DefaultTypeModifiers.emptyUndefinedModifiers, this)

    lateinit var bounds: List<BoundsTypeItem>

    final override fun typeBounds(): List<BoundsTypeItem> = bounds

    final override fun isReified(): Boolean = isReified

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name() == other.name()
    }

    override fun hashCode(): Int {
        return name().hashCode()
    }

    override fun toString(): String =
        if (typeBounds().isEmpty() && !isReified()) name()
        else
            buildString {
                if (isReified()) append("reified ")
                append(name())
                if (typeBounds().isNotEmpty()) {
                    append(" extends ")
                    typeBounds().joinTo(this, " & ")
                }
            }
}
