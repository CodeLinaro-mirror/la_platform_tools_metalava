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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.doc.DocComment
import com.android.tools.metalava.reporter.FileLocation

/**
 * An [ItemDocumentation] implementation intended for use by source models.
 *
 * Initializes the documentation from a model specific [SourceComment].
 */
internal class SourceItemDocumentation(
    item: SelectableItem,
    private val sourceComment: SourceComment,
) : AbstractItemDocumentation(item) {
    override val fileLocation: FileLocation
        get() = sourceComment.fileLocation

    /** Lazily initialized backing property for [docComment]. */
    private lateinit var _docComment: DocComment

    override val docComment: DocComment
        get() {
            if (!::_docComment.isInitialized) {
                _docComment =
                    DocComment.createDocComment(
                        context = this,
                        sourceComment.text,
                        reporter = this,
                    )
            }
            return _docComment
        }
}

/** Create an [ItemDocumentation] instance for [item] from [sourceComment]. */
fun createSourceItemDocumentation(
    item: SelectableItem,
    sourceComment: SourceComment
): ItemDocumentation = SourceItemDocumentation(item, sourceComment)

/** Represents a comment in the source. */
interface SourceComment {
    /** The location of the beginning of the comment. */
    val fileLocation: FileLocation

    /** The text contents of the source comment, including javadoc start and end tokens */
    val text: String
}

/**
 * An abstract [SourceComment] that initializes [fileLocation] and [text] lazily through subclass
 * provided methods [obtainFileLocation] and [obtainText] respectively.
 */
abstract class LazySourceComment : SourceComment {
    /** Lazily initialized backing property for [fileLocation]. */
    private lateinit var _fileLocation: FileLocation

    /** Obtain the [FileLocation] of the comment, called when [fileLocation] is first accessed. */
    protected abstract fun obtainFileLocation(): FileLocation

    override val fileLocation: FileLocation
        get() {
            if (!::_fileLocation.isInitialized) {
                _fileLocation = obtainFileLocation()
            }
            return _fileLocation
        }

    /** Lazily initialized backing property for [text]. */
    private lateinit var _text: String

    /** Obtain the text content of the comment, called when [text] is first accessed. */
    protected abstract fun obtainText(): String

    override val text: String
        get() {
            if (!::_text.isInitialized) {
                _text = obtainText()
            }
            return _text
        }
}

/** A [SourceComment] that provides the content from a [String]. */
private data class SourceCommentFromString(override val text: String) : SourceComment {
    override val fileLocation: FileLocation
        get() = FileLocation.UNKNOWN
}

/** Wrap a [String] in an [ItemDocumentationFactory]. */
fun String.toItemDocumentationFactory(): ItemDocumentationFactory =
    ItemDocumentationFactory { item ->
        SourceItemDocumentation(item, SourceCommentFromString(this))
    }
