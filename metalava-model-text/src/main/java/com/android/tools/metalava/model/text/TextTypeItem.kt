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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem

sealed class TextTypeItem(open val codebase: TextCodebase) : DefaultTypeItem(codebase) {

    override fun asClass(): ClassItem? {
        if (this is PrimitiveTypeItem) {
            return null
        }
        val cls = run {
            val erased = toErasedTypeString()
            // Also chop off array dimensions
            val index = erased.indexOf('[')
            if (index != -1) {
                erased.substring(0, index)
            } else {
                erased
            }
        }
        return codebase.getOrCreateClass(cls)
    }

    internal abstract fun duplicate(withNullability: TypeNullability): TextTypeItem

    companion object {

        fun eraseTypeArguments(s: String): String {
            val index = s.indexOf('<')
            if (index != -1) {
                var balance = 0
                for (i in index..s.length) {
                    val c = s[i]
                    if (c == '<') {
                        balance++
                    } else if (c == '>') {
                        balance--
                        if (balance == 0) {
                            return if (i == s.length - 1) {
                                s.substring(0, index)
                            } else {
                                s.substring(0, index) + s.substring(i + 1)
                            }
                        }
                    }
                }

                return s.substring(0, index)
            }
            return s
        }
    }
}

/** A [PrimitiveTypeItem] parsed from a signature file. */
internal class TextPrimitiveTypeItem(
    override val codebase: TextCodebase,
    override val kind: PrimitiveTypeItem.Primitive,
    override val modifiers: TextTypeModifiers
) : PrimitiveTypeItem, TextTypeItem(codebase) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextPrimitiveTypeItem(codebase, kind, modifiers.duplicate(withNullability))
    }

    // Text types are immutable, so the modifiers don't actually need to be duplicated.
    override fun duplicate(): TypeItem = this
}

/** An [ArrayTypeItem] parsed from a signature file. */
internal class TextArrayTypeItem(
    override val codebase: TextCodebase,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
    override val modifiers: TextTypeModifiers
) : ArrayTypeItem, TextTypeItem(codebase) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextArrayTypeItem(
            codebase,
            componentType,
            isVarargs,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(componentType: TypeItem): ArrayTypeItem {
        return TextArrayTypeItem(codebase, componentType, isVarargs, modifiers)
    }
}

/** A [ClassTypeItem] parsed from a signature file. */
internal class TextClassTypeItem(
    override val codebase: TextCodebase,
    override val qualifiedName: String,
    override val parameters: List<TypeItem>,
    override val outerClassType: ClassTypeItem?,
    override val modifiers: TextTypeModifiers
) : ClassTypeItem, TextTypeItem(codebase) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextClassTypeItem(
            codebase,
            qualifiedName,
            parameters,
            outerClassType,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(outerClass: ClassTypeItem?, parameters: List<TypeItem>): ClassTypeItem {
        return TextClassTypeItem(codebase, qualifiedName, parameters, outerClass, modifiers)
    }
}

/** A [VariableTypeItem] parsed from a signature file. */
internal class TextVariableTypeItem(
    override val codebase: TextCodebase,
    override val name: String,
    override val asTypeParameter: TypeParameterItem,
    override val modifiers: TextTypeModifiers
) : VariableTypeItem, TextTypeItem(codebase) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextVariableTypeItem(
            codebase,
            name,
            asTypeParameter,
            modifiers.duplicate(withNullability)
        )
    }

    // Text types are immutable, so the modifiers don't actually need to be duplicated.
    override fun duplicate(): TypeItem = this
}

/** A [WildcardTypeItem] parsed from a signature file. */
internal class TextWildcardTypeItem(
    override val codebase: TextCodebase,
    override val extendsBound: TypeItem?,
    override val superBound: TypeItem?,
    override val modifiers: TextTypeModifiers
) : WildcardTypeItem, TextTypeItem(codebase) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextWildcardTypeItem(
            codebase,
            extendsBound,
            superBound,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(extendsBound: TypeItem?, superBound: TypeItem?): WildcardTypeItem {
        return TextWildcardTypeItem(codebase, extendsBound, superBound, modifiers)
    }
}
