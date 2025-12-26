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
package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType

/**
 * An inline tag within a block of Javadoc.
 *
 * @property tagType the type of the inline tag, e.g. `link`, `code`, etc.
 * @property content the optional content, e.g. for `{@link ref}` it would be the reference, for
 *   `{@inheritDoc}` it would be `null`.
 */
internal class JavadocInlineTag(
    override val tagType: TagType<*>,
    override val tagData: TagData?,
    override val content: JavadocContent?,
) : JavadocContent, DocTag {
    override fun <R> accept(visitor: JavadocContentVisitor<R>) = visitor.visit(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavadocInlineTag

        if (tagType != other.tagType) return false
        if (content != other.content) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tagType.hashCode()
        result = 31 * result + (content?.hashCode() ?: 0)
        return result
    }

    override fun toString() = "JavadocInlineTag(@$tagType, $content)"
}
