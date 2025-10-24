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

/**
 * A block tag section of [DocComment.blockTagSections].
 *
 * Looks like:
 * ```
 *     * @<tag-type> <description>
 * ```
 */
internal interface BlockTagSection {
    /** The type of the block tag. */
    val tagType: String

    /** The description of the block tag. */
    val description: DocDescription
}

internal class DefaultBlockTagSection(
    override val tagType: String,
    override val description: DocDescription,
) : BlockTagSection {
    override fun toString() = buildString {
        append("tag-type: ")
        append(tagType)
        append("\ndescription: ")
        append(description)
    }
}
