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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.item.ResourceFile

interface PackageItem : SelectableItem {
    /**
     * The overview documentation associated with the package; retrieved from an `overview.html`
     * file listed in the source files.
     *
     * If present this is copied to an `overview.html` in the stubs package directory when
     * generating documentation stubs.
     */
    val overviewDocumentation: ResourceFile?
        get() = null

    /** The qualified name of this package */
    fun qualifiedName(): String

    /**
     * All top level classes in this package.
     *
     * This is a snapshot of the classes in this package and will not be affected by any additional
     * classes added to the package after the list is returned.
     */
    fun topLevelClasses(): List<ClassItem>

    /**
     * All top level classes **and nested classes** in this package flattened into a single
     * [Sequence].
     */
    fun allClasses(): Sequence<ClassItem> {
        return topLevelClasses().asSequence().flatMap { it.allClasses() }
    }

    override fun type(): TypeItem? = null

    override fun setType(type: TypeItem) =
        error("Cannot call setType(TypeItem) on PackageItem: $this")

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) = codebase.findPackage(qualifiedName())

    override fun parent(): PackageItem? =
        if (qualifiedName().isEmpty()) null else containingPackage()

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated

    override fun baselineElementId() = qualifiedName()

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackageItem) return false

        return qualifiedName() == other.qualifiedName()
    }

    override fun hashCodeForItem(): Int {
        return qualifiedName().hashCode()
    }

    override fun toStringForItem() =
        "package ${qualifiedName().let { if (it == "") "<root>" else it}}"

    companion object {
        val comparator: Comparator<PackageItem> = Comparator { a, b ->
            a.qualifiedName().compareTo(b.qualifiedName())
        }
    }
}
