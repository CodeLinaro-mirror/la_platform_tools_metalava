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

package com.android.tools.metalava.model

import java.util.Objects

interface PropertyItem : MemberItem, TypeParameterListOwner, InheritableItem {
    /** The getter for this property, if it exists; inverse of [MethodItem.property] */
    val getter: MethodItem?
        get() = null

    /** The setter for this property, if it exists; inverse of [MethodItem.property] */
    val setter: MethodItem?
        get() = null

    /** The backing field for this property, if it exists; inverse of [FieldItem.property] */
    val backingField: FieldItem?
        get() = null

    /**
     * The constructor parameter for this property, if declared in a primary constructor; inverse of
     * [ParameterItem.property]
     */
    val constructorParameter: ParameterItem?
        get() = null

    override fun describe(capitalize: Boolean) = toString()

    /** The type of this property */
    override fun type(): TypeItem

    /** The receiver type of this property, if one exists. */
    val receiver: TypeItem?

    /** The type parameters of this property. */
    override val typeParameterList: TypeParameterList

    /**
     * The visibility of the property's setter, or null if the property has no setter (or the
     * visibility is unknown).
     */
    val setterVisibility: VisibilityLevel?

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) =
        containingClass().findCorrespondingItemIn(codebase)?.properties()?.find {
            it.name() == name()
        }

    private fun receiverString(): String =
        receiver?.let { it.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) + "." } ?: ""

    override fun baselineElementId() =
        containingClass().qualifiedName() + "#" + receiverString() + name()

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyItem) return false

        return name() == other.name() &&
            containingClass() == other.containingClass() &&
            equalReceivers(receiver, other.receiver)
    }

    override fun hashCodeForItem(): Int {
        return Objects.hash(name(), receiver)
    }

    override fun toStringForItem(): String =
        "property ${containingClass().qualifiedName()}#${receiverString()}${name()}"

    // Inherit deprecation from the getter
    override val effectivelyDeprecated: Boolean
        get() =
            originallyDeprecated ||
                if (getter == null) {
                    containingClass().effectivelyDeprecated
                } else {
                    getter!!.effectivelyDeprecated
                }

    companion object {
        val comparator: java.util.Comparator<PropertyItem> = Comparator { a, b ->
            a.name().compareTo(b.name())
        }

        /** Returns whether the two types should be considered equal property receivers. */
        fun equalReceivers(receiver1: TypeItem?, receiver2: TypeItem?): Boolean {
            // Nullability is important for property receivers because kotlin allows defining
            // properties which differ only in receiver nullability.
            return receiver1?.equalToType(receiver2, true) ?: (receiver2 == null)
        }
    }
}
