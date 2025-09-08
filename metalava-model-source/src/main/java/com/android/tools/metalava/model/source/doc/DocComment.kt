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

package com.android.tools.metalava.model.source.doc

/** A Javadoc or KDoc comment associated with an API element. */
interface DocComment {
    /** The main description, i.e. the part before any block tags. */
    val description: DocDescription

    /**
     * The block tag sections, i.e. the parts that start `@<block-tag-type> ...`.
     *
     * There can be more than one block tag section of some types, e.g. `@param`, `@see`.
     */
    val blockTagSections: List<BlockTagSection>

    /** Check to see whether there are any block tags of type [blockTagType]. */
    fun hasBlockTagOfType(blockTagType: String): Boolean

    companion object {
        /** Create a [DocComment] from [text], reporting any issues to [reporter]. */
        fun createDocComment(text: String): DocComment {
            return DocCommentParser.parseText(text)
        }
    }
}

internal class DefaultDocComment(
    override val description: DocDescription,
    override val blockTagSections: List<BlockTagSection>
) : DocComment {
    override fun hasBlockTagOfType(blockTagType: String) =
        blockTagSections.any { it.tagType == blockTagType }

    override fun toString() = buildString {
        append("description: ")
        append(description)
        for (section in blockTagSections) {
            append("\n@")
            append(section.tagType)
            append(" ")
            append(section.description)
        }
    }
}
