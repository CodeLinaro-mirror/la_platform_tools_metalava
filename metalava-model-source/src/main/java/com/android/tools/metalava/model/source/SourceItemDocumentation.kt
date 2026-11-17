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
import com.android.tools.metalava.model.SourcePosition
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
) : AbstractItemDocumentation(item), DocCommentSupplier {
    override val docCommentSupplier: DocCommentSupplier
        get() = this

    override val fileLocation: FileLocation
        get() = sourceComment.fileLocation

    override fun fileLocation(charOffset: Int): FileLocation {
        val startOffset = sourceComment.startOffset
        if (startOffset < 0) return fileLocation
        val sourceFile = item.sourceFile() ?: return fileLocation
        return sourceFile.fileLocation(
            SourcePosition(startOffset + charOffset),
            includeCharacterPosition = true,
        )
    }

    /**
     * Lazily initialized initial [DocComment] parsed from [sourceComment].
     *
     * Cached separately from [docComment] so that if this or a duplicate/snapshot is mutated, other
     * duplicates/snapshots created before the mutation can still obtain the unmutated initial
     * [DocComment] without re-parsing [sourceComment].
     */
    private lateinit var initialDocComment: DocComment

    override fun obtainInitialDocComment(): DocComment {
        if (!::initialDocComment.isInitialized) {
            initialDocComment =
                DocComment.createDocComment(
                    context = this,
                    sourceComment.text,
                    reporter = this,
                )
        }
        return initialDocComment
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

    /**
     * The 0-based character offset of the start of the comment from the start of the source file,
     * or `-1` if there is no comment.
     */
    val startOffset: Int

    /** The text contents of the source comment, including javadoc start and end tokens */
    val text: String
}

/**
 * An abstract [SourceComment] that initializes [fileLocation], [startOffset], and [text] lazily
 * through subclass provided methods [obtainFileLocation], [obtainStartOffset], and [obtainText]
 * respectively.
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

    /**
     * Obtain the 0-based character offset of the start of the comment from the start of the source
     * file, or `-1` if there is no comment.
     */
    protected abstract fun obtainStartOffset(): Int

    /** Lazily initialized backing property for [startOffset]. */
    private var _startOffset: Int = Int.MIN_VALUE

    override val startOffset: Int
        get() {
            if (_startOffset == Int.MIN_VALUE) {
                _startOffset = obtainStartOffset()
            }
            return _startOffset
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

    override val startOffset: Int
        get() = -1
}

/** Wrap a [String] in an [ItemDocumentationFactory]. */
fun String.toItemDocumentationFactory(): ItemDocumentationFactory =
    ItemDocumentationFactory { item ->
        SourceItemDocumentation(item, SourceCommentFromString(this))
    }
