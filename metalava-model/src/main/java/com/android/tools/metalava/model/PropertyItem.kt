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

interface PropertyItem : MemberItem, TypeParameterListOwner, InheritableItem {
    /** The getter for this property, if it exists; inverse of [MethodItem.property] */
    val getter: MethodItem?

    /** The setter for this property, if it exists; inverse of [MethodItem.property] */
    val setter: MethodItem?

    /** The backing field for this property, if it exists; inverse of [FieldItem.property] */
    val backingField: FieldItem?

    /**
     * The constructor parameter for this property, if declared in a primary constructor; inverse of
     * [ParameterItem.property]
     */
    val constructorParameter: ParameterItem?

    /** The type of this property */
    override fun type(): TypeItem

    /** The receiver type of this property, if one exists. */
    val receiver: TypeItem?

    /** The type parameters of this property. */
    override val typeParameterList: TypeParameterList

    /**
     * The [context parameters](https://kotlinlang.org/docs/context-parameters.html) of this
     * property.
     */
    val contextParameters: List<ParameterItem>

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
            it.name() == name() &&
                equalReceivers(receiver, it.receiver) &&
                equalContextParameters(contextParameters, it.contextParameters)
        }

    private fun receiverString(): String =
        receiver?.let { it.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) + "." } ?: ""

    private fun contextString(): String {
        return if (contextParameters.isNotEmpty()) {
            "(${contextParameters.joinToString(", ") {
                "context " + it.type().toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS)
            }})"
        } else {
            ""
        }
    }

    override fun baselineElementId() = buildString {
        if (containingClass().simpleName() != ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME) {
            append(containingClass().qualifiedName())
        } else {
            append(containingPackage().qualifiedName())
        }
        append("#")
        append(receiverString())
        append(name())
        append(contextString())
    }

    /**
     * Duplicates this property item.
     *
     * Override to specialize the return type.
     */
    override fun duplicate(targetContainingClass: ClassItem): PropertyItem

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyItem) return false

        return name() == other.name() &&
            containingClass() == other.containingClass() &&
            equalReceivers(receiver, other.receiver) &&
            equalContextParameters(contextParameters, other.contextParameters)
    }

    override fun hashCodeForItem(): Int {
        var result = name().hashCode()
        result = 31 * result + TypeComparator.NULLABILITY_AWARE.hash(receiver)
        result =
            31 * result +
                contextParameters.fold(1) { acc, param ->
                    31 * acc + TypeComparator.NULLABILITY_AWARE.hash(param.type())
                }
        return result
    }

    override fun describe(capitalize: Boolean) = buildString {
        append(if (capitalize) "Property" else "property")
        append(" ")
        append(containingClass().qualifiedName())
        append("#")
        append(receiverString())
        append(name())
        append(contextString())
    }

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
        /** Orders [PropertyItem]s by their [PropertyItem.name]. */
        val comparator: Comparator<PropertyItem> = Comparator.comparing { it.name() }

        /** Returns whether the two types should be considered equal property receivers. */
        fun equalReceivers(receiver1: TypeItem?, receiver2: TypeItem?): Boolean {
            // Nullability is important for property receivers because kotlin allows defining
            // properties which differ only in receiver nullability.
            return TypeComparator.NULLABILITY_AWARE.compare(receiver1, receiver2)
        }

        /** Returns whether the two lists should be considered equal context parameters. */
        fun equalContextParameters(
            contextParameters1: List<ParameterItem>,
            contextParameters2: List<ParameterItem>
        ): Boolean {
            return equalContextParameterTypes(
                contextParameters1.map { it.type() },
                contextParameters2.map { it.type() }
            )
        }

        /** Returns whether the two lists should be considered equal context parameter types. */
        fun equalContextParameterTypes(
            contextParameters1: List<TypeItem>,
            contextParameters2: List<TypeItem>
        ): Boolean {
            // Nullability is important for property context parameters because kotlin allows
            // defining properties which differ only in context parameter nullability.
            return contextParameters1.size == contextParameters2.size &&
                contextParameters1.zip(contextParameters2).all { (thisParam, otherParam) ->
                    TypeComparator.NULLABILITY_AWARE.compare(thisParam, otherParam)
                }
        }
    }
}
