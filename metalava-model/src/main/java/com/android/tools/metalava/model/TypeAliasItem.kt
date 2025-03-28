/*
 * Copyright (C) 2025 The Android Open Source Project
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

/**
 * Represents a Kotlin type alias (https://kotlinlang.org/docs/type-aliases.html), which provides an
 * alternative name for an existing type.
 */
interface TypeAliasItem : SelectableItem, TypeParameterListOwner {
    /** The underlying type for which this type alias is an alternative name. */
    val aliasedType: TypeItem

    /** The fully qualified name of this type alias (including the package name). */
    val qualifiedName: String

    /** The simple name of this type alias (not including the package name). */
    val simpleName: String

    /**
     * The parent [PackageItem] of this type alias (type aliases can only be defined at the package
     * level, not nested in other kinds of [Item]s).
     */
    override fun parent(): PackageItem = containingPackage()

    override fun containingPackage(): PackageItem

    override fun type(): TypeItem = aliasedType

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    /** Type aliases cannot be defined within classes, this is always null. */
    override fun containingClass(): ClassItem? = null

    override fun baselineElementId(): String = qualifiedName

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeAliasItem) return false

        return qualifiedName == other.qualifiedName
    }

    override fun hashCodeForItem(): Int = qualifiedName.hashCode()

    override fun toStringForItem(): String = "typealias $qualifiedName"

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean
    ): TypeAliasItem? {
        return codebase.findTypeAlias(qualifiedName)
    }

    /** A type alias's type cannot be reset, this will error. */
    override fun setType(type: TypeItem) =
        error("Cannot call setType(TypeItem) on TypeAliasItem: $this")
}
