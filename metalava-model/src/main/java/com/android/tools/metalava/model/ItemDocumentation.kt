/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.reporter.FileLocation
import java.io.PrintWriter

/** A factory that will create an [ItemDocumentation] for a specific [SelectableItem]. */
fun interface ItemDocumentationFactory {
    fun create(item: SelectableItem): ItemDocumentation?
}

/**
 * The documentation associated with an [Item].
 *
 * This implements [CharSequence] to simplify migration.
 */
interface ItemDocumentation {
    val text: String

    /** The location of the start of the document comment. */
    val fileLocation: FileLocation
        get() = FileLocation.UNKNOWN

    /**
     * True if the documentation contains one of the following tags that indicates that it should
     * not be part of an API, unless overridden by a show annotation:
     * * `@hide`
     * * `@pending`
     * * `@suppress`
     */
    val isHidden: Boolean

    /**
     * True if the documentation contains `@doconly` which indicates that it should only be included
     * in stubs that are generated for documentation purposes.
     */
    val isDocOnly: Boolean

    /**
     * True if the documentation contains `@removed` which indicates that the [Item] must not be
     * included in stubs or the main signature file but will be included in the `removed` signature
     * file as it is still considered part of the API available at runtime and so cannot be removed
     * altogether.
     */
    val isRemoved: Boolean

    /**
     * Return a duplicate of this instance.
     *
     * [ItemDocumentation] instances can be mutable, and if they are then they must not be shared.
     */
    fun duplicate(item: SelectableItem): ItemDocumentation

    /**
     * Like [duplicate] except that it returns an instance of [ItemDocumentation] suitable for use
     * in the snapshot.
     */
    fun snapshot(item: SelectableItem): ItemDocumentation

    /**
     * Check to see whether this has a tag section of [blockTagType].
     *
     * @param blockTagType the type of the tag, e.g. `param` for `@param p ...`.
     */
    fun hasBlockTagOfType(blockTagType: String): Boolean

    /**
     * Print the documentation to [writer].
     *
     * The printed documentation will be suitable for use in a stub source file, i.e. references
     * will, where possible, be fully qualified.
     */
    fun print(writer: PrintWriter)

    /** Get the main description of the comment. */
    val mainDescription: DocContent?

    /** Get the owner of the main description of the comment. */
    val mainDescriptionOwner: DocContentOwner

    /**
     * Get the description for the first block tag of [tagTypeName].
     *
     * Returns `null` if this has no underlying Javadoc comment, or no such block tag.
     */
    fun blockTagDescription(tagTypeName: String): DocContent?

    /**
     * Get the owner of the description for the first block tag of [tagTypeName].
     *
     * If no such block tag exists then this will create a pending block tag that will be added if
     * any content is appended to it through the returned [DocContentOwner]. In that case no
     * reference is held internally to the returned [DocContentOwner] so there is no risk of a
     * memory leak.
     */
    fun blockTagDescriptionOwner(tagTypeName: String): DocContentOwner

    /**
     * Get the description for the @param tag for [name]
     *
     * Returns `null` if this has no Javadoc comment, or no such parameter, or the description is
     * empty, i.e. has no significant non-whitespace content, or is invalid, e.g. no parameter name.
     */
    fun paramTagDescription(name: String): DocContent?

    /**
     * Get owner of the description for the `@param` tag for [name]
     *
     * If no such `@param` tag exists then this will create a pending `@param` tag that will be
     * added if any content is appended to it through the returned [DocContentOwner]. In that case
     * no reference is held internally to the returned [DocContentOwner] so there is no risk of a
     * memory leak.
     */
    fun paramTagDescriptionOwner(name: String): DocContentOwner

    /**
     * Check if [predicate] matches this documentation, checks [mainDescription] and all the block
     * tag descriptions, including the `@param` tags.
     */
    fun check(predicate: DocContentPredicate): Boolean

    /**
     * Check to see if this requires a source comment.
     *
     * This returns `true` if it would need to be written as a comment if this was generated in the
     * sources. That can either be because it was created from a comment in the original sources, or
     * it has been mutated since creation.
     */
    fun requiresSourceComment(): Boolean

    /** Remove the `@deprecated` section, if any. */
    fun removeDeprecatedSection()

    /**
     * Adds a unique block tag section of [tagTypeName] with some simple [text], i.e. no inline
     * tags.
     *
     * @param tagTypeName the type of the tag, e.g. `apiSince` for `@apiSince 27`.
     * @param text the text description.
     */
    fun addUniqueBlockTagSectionWithSimpleText(tagTypeName: String, text: String)

    companion object {
        /**
         * A special [ItemDocumentationFactory] that returns `null`.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE_FACTORY: ItemDocumentationFactory = ItemDocumentationFactory { null }
    }
}

/** Return an [ItemDocumentationFactory] that will create a duplicate of this. */
fun ItemDocumentation?.duplicatingFactory(): ItemDocumentationFactory =
    this?.let { ItemDocumentationFactory { item -> it.duplicate(item) } }
        ?: ItemDocumentation.NONE_FACTORY

/** Return an [ItemDocumentationFactory] that will take a snapshot of this. */
fun ItemDocumentation?.snapshottingFactory(): ItemDocumentationFactory =
    this?.let { ItemDocumentationFactory { item: SelectableItem -> it.snapshot(item) } }
        ?: ItemDocumentation.NONE_FACTORY
