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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.SUPPRESS_ANNOTATIONS
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.value.StringValue
import com.android.tools.metalava.reporter.FileLocation
import java.util.concurrent.atomic.AtomicInteger

/** Base [com.android.tools.metalava.model.Item] implementation that is common to all models. */
internal sealed class DefaultItem(
    final override val codebase: Codebase,
    final override val fileLocation: FileLocation,
    final override val sourceLanguage: SourceLanguage,
    modifiers: BaseModifierList,
) : Item {
    /**
     * The immutable [modifiers].
     *
     * The supplied `modifiers` parameter could be either
     * [com.android.tools.metalava.model.MutableModifierList] or
     * [com.android.tools.metalava.model.ModifierList] but this requires a
     * [com.android.tools.metalava.model.ModifierList] so get one using
     * [BaseModifierList.toImmutable].
     *
     * The [com.android.tools.metalava.model.ModifierList] that this references is immutable but the
     * [mutateModifiers] method can be used to change the
     * [com.android.tools.metalava.model.ModifierList] to which this refers.
     */
    final override var modifiers: ModifierList = modifiers.toImmutable()
        private set

    final override val sortingRank: Int = nextRank.getAndIncrement()

    final override val originallyDeprecated
        // Delegate to the [ModifierList.isDeprecated] method so that changes to that will affect
        // the value of this and [Item.effectivelyDeprecated] which delegates to this.
        get() = modifiers.isDeprecated()

    override fun mutateModifiers(mutator: MutableModifierList.() -> Unit) {
        val mutable = modifiers.toMutable()
        mutable.mutator()
        modifiers = mutable.toImmutable()
    }

    final override val isPublic: Boolean
        get() = modifiers.isPublic()

    final override val isProtected: Boolean
        get() = modifiers.isProtected()

    final override val isInternal: Boolean
        get() = modifiers.isInternal()

    final override val isPackagePrivate: Boolean
        get() = modifiers.isPackagePrivate()

    final override val isPrivate: Boolean
        get() = modifiers.isPrivate()

    companion object {
        private var nextRank = AtomicInteger()
    }

    final override fun suppressedIssues(): Set<String> {
        return buildSet {
            for (annotation in modifiers.annotations()) {
                val annotationName = annotation.qualifiedName
                if (annotationName in SUPPRESS_ANNOTATIONS) {
                    for (attribute in annotation.attributes) {
                        // Assumption that all annotations in SUPPRESS_ANNOTATIONS only have
                        // one attribute such as value/names that is an array of String, e.g.
                        // Example: @SuppressLint({"RequiresFeature", "AllUpper"})
                        // Example: @SuppressLint("RequiresFeature")
                        for (value in attribute.value.asFlatList()) {
                            if (value is StringValue) add(value.underlyingValue)
                        }
                    }
                }
            }
        }
    }

    final override fun equals(other: Any?) = equalsToItem(other)

    final override fun hashCode() = hashCodeForItem()

    final override fun toString() = toStringForItem()
}
