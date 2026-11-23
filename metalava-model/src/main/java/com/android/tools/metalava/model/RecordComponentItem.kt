/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import java.util.Objects

typealias RecordComponentItemsFactory = (ClassItem) -> List<RecordComponentItem>

/** An [Item] that represents a component in a record class. */
interface RecordComponentItem : Item {
    /** The modifiers of this, only the annotations are useful. */
    override val modifiers: ModifierList

    /**
     * The 0-based index of this within the list of record components of a [ClassKind.RECORD] class.
     */
    val recordComponentIndex: Int

    /** The name of the component. */
    val name: String

    /** The type of the component. */
    val type: TypeItem

    override fun type(): TypeItem = type

    override fun containingClass(): ClassItem

    override fun parent(): ClassItem = containingClass()

    override fun containingPackage(): PackageItem = containingClass().containingPackage()

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override val description: DocContent?
        get() = containingClass().documentation?.paramTagDescription(name)

    override val descriptionOwner: DocContentOwner
        get() = containingClass().requiredDocumentation.paramTagDescriptionOwner(name)

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyItem) return false

        return name == other.name() && containingClass() == other.containingClass()
    }

    override fun hashCodeForItem() = Objects.hash(name)

    override fun describe(capitalize: Boolean) = buildString {
        append(if (capitalize) "Record component" else "record component")
        append(" ")
        append(containingClass().qualifiedName())
        append(".")
        append(name)
    }

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated || containingClass().effectivelyDeprecated

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) = containingClass().findCorrespondingItemIn(codebase)?.recordComponents?.get(name)

    override fun baselineElementId() = "${containingClass().qualifiedName()}#$name"

    override val targetLanguages: Set<TargetLanguage>
        get() = containingClass().targetLanguages
}
