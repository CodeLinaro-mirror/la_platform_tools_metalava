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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.reporter.FileLocation

/** A default [com.android.tools.metalava.model.ItemDocumentation] containing JavaDoc/KDoc. */
internal class DefaultItemDocumentation(
    item: SelectableItem,
    text: String,
    override val fileLocation: FileLocation = FileLocation.UNKNOWN,
) : AbstractItemDocumentation(item) {

    init {
        // Initialize _text from the text constructor parameter.
        _text = text
    }

    /** Should never be called as _text has been initialized above. */
    override fun initializeTextBackingField() {
        error("Internal Error: _text backing field is uninitialized")
    }
}

/** Wrap a [String] in an [ItemDocumentationFactory]. */
fun String.toItemDocumentationFactory(): ItemDocumentationFactory = { toItemDocumentation(it) }

/** Wrap a [String] in an [ItemDocumentation] instance. */
private fun String.toItemDocumentation(item: SelectableItem): ItemDocumentation =
    DefaultItemDocumentation(item, this)

/** Creates a [DefaultItemDocumentation] for an item without any source comment. */
val NO_SOURCE_COMMENT_FACTORY = "".toItemDocumentationFactory()
