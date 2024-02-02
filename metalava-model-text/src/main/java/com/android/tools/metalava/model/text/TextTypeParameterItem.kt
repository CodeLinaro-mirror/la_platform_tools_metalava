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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem

internal class TextTypeParameterItem(
    codebase: TextCodebase,
    private val typeParameterString: String,
    private val name: String,
    private val isReified: Boolean,
) :
    TextItem(
        codebase = codebase,
        position = SourcePositionInfo.UNKNOWN,
        modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
    ),
    TypeParameterItem {

    private var owner: TypeParameterListOwner? = null

    private var bounds: List<TypeItem>? = null

    override fun name(): String {
        return name
    }

    override fun toString() = typeParameterString

    override fun type(): TextVariableTypeItem {
        return TextVariableTypeItem(
            codebase,
            name,
            this,
            TextTypeModifiers.create(codebase, emptyList(), null)
        )
    }

    override fun typeBounds(): List<TypeItem> {
        if (bounds == null) {
            val boundsStringList = bounds(typeParameterString, owner)
            bounds =
                if (boundsStringList.isEmpty()) {
                    emptyList()
                } else {
                    boundsStringList.map {
                        codebase.typeResolver.obtainTypeFromString(it, gatherTypeParams(owner))
                    }
                }
        }
        return bounds!!
    }

    override fun isReified(): Boolean = isReified

    internal fun setOwner(newOwner: TypeParameterListOwner) {
        owner = newOwner
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.name()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {
        fun create(
            codebase: TextCodebase,
            typeParameterString: String,
        ): TextTypeParameterItem {
            val length = typeParameterString.length
            var nameEnd = length

            val isReified = typeParameterString.startsWith("reified ")
            val nameStart =
                if (isReified) {
                    8 // "reified ".length
                } else {
                    0
                }

            for (i in nameStart until length) {
                val c = typeParameterString[i]
                if (!Character.isJavaIdentifierPart(c)) {
                    nameEnd = i
                    break
                }
            }
            val name = typeParameterString.substring(nameStart, nameEnd)
            return TextTypeParameterItem(
                codebase = codebase,
                typeParameterString = typeParameterString,
                name = name,
                isReified = isReified,
            )
        }

        fun bounds(typeString: String?, owner: TypeParameterListOwner? = null): List<String> {
            val s = typeString ?: return emptyList()
            val index = s.indexOf("extends ")
            if (index == -1) {
                // See if this is a type variable that has bounds in the parent
                val parameters =
                    (owner as? TextMemberItem)
                        ?.containingClass()
                        ?.typeParameterList()
                        ?.typeParameters()
                        ?: return emptyList()
                for (p in parameters) {
                    if (p.name() == s) {
                        return p.typeBounds().map { it.toTypeString() }
                    }
                }

                return emptyList()
            }
            val list = mutableListOf<String>()
            var angleBracketBalance = 0
            var start = index + "extends ".length
            val length = s.length
            for (i in start until length) {
                val c = s[i]
                if (c == '&' && angleBracketBalance == 0) {
                    add(list, typeString, start, i)
                    start = i + 1
                } else if (c == '<') {
                    angleBracketBalance++
                } else if (c == '>') {
                    angleBracketBalance--
                    if (angleBracketBalance == 0) {
                        add(list, typeString, start, i + 1)
                        start = i + 1
                    }
                }
            }
            if (start < length) {
                add(list, typeString, start, length)
            }
            return list
        }

        private fun add(list: MutableList<String>, s: String, from: Int, to: Int) {
            for (i in from until to) {
                if (!Character.isWhitespace(s[i])) {
                    var end = to
                    while (end > i && s[end - 1].isWhitespace()) {
                        end--
                    }
                    var begin = i
                    while (begin < end && s[begin].isWhitespace()) {
                        begin++
                    }
                    if (begin == end) {
                        return
                    }
                    val element = s.substring(begin, end)
                    list.add(element)
                    return
                }
            }
        }

        /** Collect all the type parameters in scope for the given [owner]. */
        internal fun gatherTypeParams(owner: TypeParameterListOwner?): List<TypeParameterItem> {
            return owner?.let {
                it.typeParameterList().typeParameters() +
                    gatherTypeParams(owner.typeParameterListOwnerParent())
            }
                ?: emptyList()
        }
    }
}
