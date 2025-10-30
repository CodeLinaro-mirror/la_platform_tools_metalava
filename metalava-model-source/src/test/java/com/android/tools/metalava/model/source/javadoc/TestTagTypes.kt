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

import com.android.tools.metalava.model.source.doc.BlockTagTypes
import com.android.tools.metalava.model.source.doc.DocCommentContext
import com.android.tools.metalava.model.source.doc.ExtractDataResult
import com.android.tools.metalava.model.source.doc.InlineTagTypes
import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType
import java.io.PrintWriter

internal object TestTagTypes {
    val BAR_TAG_TYPE =
        BarTagType().also {
            // Register for use as block and inline tags.
            BlockTagTypes.register(it)
            InlineTagTypes.register(it)
        }
}

internal class BarTagType : TagType<BarTagData>("bar") {
    override fun extractData(
        context: DocCommentContext,
        text: CharSequence
    ): ExtractDataResult<BarTagData>? {
        val identifier = text.findLeadingIdentifier() ?: return null
        return ExtractDataResult(
            tagData = BarTagData(identifier),
            consumedContent = identifier.length + 1,
        )
    }
}

internal data class BarTagData(val identifier: String) : TagData {
    override fun printAfterTagType(writer: PrintWriter) {
        writer.print(" ")
        writer.print(identifier)
    }
}
