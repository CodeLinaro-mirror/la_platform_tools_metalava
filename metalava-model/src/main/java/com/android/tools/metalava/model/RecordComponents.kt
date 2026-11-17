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

/**
 * The collection of [RecordComponentItem]s for a [ClassKind.RECORD] [ClassItem].
 *
 * Order is important so this iterates over them in their declaration order and provides access to
 * them by their index.
 *
 * [RecordComponentItem.name]s are unique within this collection so it also provides access by name.
 */
class RecordComponents
private constructor(private val recordComponentItems: List<RecordComponentItem>) :
    // Allow the [RecordComponentItem]s to be iterated over.
    Iterable<RecordComponentItem> {
    /** Map from [RecordComponentItem.name] to [RecordComponentItem]. */
    private val byName = recordComponentItems.associateBy { it.name }

    /** The number of [RecordComponentItem]s in this collection. */
    val size = recordComponentItems.size

    /** Get the [RecordComponentItem] by its [index] in the declaration list. */
    operator fun get(index: Int) = recordComponentItems[index]

    /** Get the [RecordComponentItem] by its [name]. */
    operator fun get(name: String) = byName[name]

    /** Check whether there is a [RecordComponentItem] called [name]. */
    operator fun contains(name: String) = name in byName

    /** Iterate over in declaration order. */
    override fun iterator() = recordComponentItems.iterator()

    companion object {
        internal val EMPTY = RecordComponents(emptyList())

        /** Create a [RecordComponents] from [components]. */
        fun create(components: List<RecordComponentItem>) =
            if (components.isEmpty()) EMPTY else RecordComponents(components)
    }
}
