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

/** [TagType] for `@throws` block tag. */
internal class ThrowsTagType() : TagType<ThrowsTagData>("throws") {
    override fun extractData(
        context: DocCommentContext,
        text: CharSequence
    ): ExtractDataResult<ThrowsTagData>? {
        val throwsName = text.findLeadingIdentifier() ?: return null
        return ExtractDataResult(
            tagData = ThrowsTagData(throwsName),
        )
    }
}

/** Tag specific data for the `@throws` block tag. */
internal data class ThrowsTagData(
    /** The reference to the throwable class. */
    val throwableClass: String,
) : TagData {
    override fun compareTo(other: TagData): Int {
        other as ThrowsTagData
        return throwableClass.compareTo(other.throwableClass)
    }
}
