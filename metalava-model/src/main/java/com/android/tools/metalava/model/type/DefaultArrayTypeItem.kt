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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.DefaultStandaloneTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultArrayTypeItem(
    modifiers: TypeModifiers,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
    isValueClassType: Boolean = false,
) : ArrayTypeItem, DefaultStandaloneTypeItem(modifiers, isValueClassType) {

    override fun substitute(
        modifiers: TypeModifiers,
        componentType: TypeItem,
        isVarargs: Boolean,
    ) =
        if (
            modifiers !== this.modifiers ||
                componentType !== this.componentType ||
                isVarargs != this.isVarargs
        )
            TypeItem.createArrayType(
                modifiers,
                componentType,
                isVarargs,
                isValueClassType,
            )
        else this

    override fun equalsImpl(other: DefaultStandaloneTypeItem): Boolean {
        if (other !is ArrayTypeItem) return false
        return isVarargs == other.isVarargs && componentType == other.componentType
    }

    override fun hashCodeImpl(): Int {
        var result = isVarargs.hashCode()
        result = 31 * result + componentType.hashCode()
        return result
    }
}
