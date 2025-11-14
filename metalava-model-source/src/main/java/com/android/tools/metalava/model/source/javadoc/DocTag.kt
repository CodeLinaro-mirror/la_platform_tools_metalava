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

import com.android.tools.metalava.model.source.doc.BlockTagSection
import com.android.tools.metalava.model.source.doc.JavadocContentPrinter
import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType

/** Common interface for [JavadocInlineTag] and [BlockTagSection]. */
internal interface DocTag {
    /** The [TagType] of this tag. */
    val tagType: TagType<*>

    /** The optional [TagData] extracted from the tag's content by [TagType.extractData]. */
    val tagData: TagData?

    /** The tag content, excludes any that was extracted into [tagData]. */
    val content: JavadocContent?

    /**
     * Print the contents of this tag, if any.
     *
     * The contents come from [tagData] and/or [content].
     */
    fun printTagContents(contentPrinter: JavadocContentPrinter) {
        // If there is tag data then get it to print the tag contents, else if there is content then
        // print that, else there is nothing to do.
        tagData?.printTagContents(contentPrinter, content)
            ?: contentPrinter.print(content, addLeadingSpaceIfNeeded = true)
    }
}
