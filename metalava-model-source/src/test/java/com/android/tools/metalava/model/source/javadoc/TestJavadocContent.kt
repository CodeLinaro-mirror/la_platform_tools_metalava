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

import kotlin.test.assertEquals

/** Dump the internal structure of this [JavadocContent]. */
internal fun JavadocContent?.dumpContentStructure(): String = buildString {
    this@dumpContentStructure?.accept(
        object : JavadocContentVisitor<Unit> {
            private var indent = ""

            private fun appendPrefix() {
                append(indent)
            }

            private inline fun indent(body: () -> Unit) {
                val oldIndent = indent
                indent += "  "
                body()
                indent = oldIndent
            }

            override fun visit(list: JavadocContentList) {
                list.visitContents(this)
            }

            override fun visit(inlineTag: JavadocInlineTag) {
                appendPrefix()
                append("inlineTag: ")
                append(inlineTag.tagType)
                inlineTag.tagData?.let { tagData ->
                    append(" ")
                    append(tagData)
                }
                append("\n")
                inlineTag.content?.let { nestedContent -> indent { nestedContent.accept(this) } }
            }

            override fun visit(text: JavadocText) {
                appendPrefix()
                append("text: '")
                append(text.contents.replace("\n", "\\n"))
                append("'\n")
            }
        }
    )
}

/** Assert that the structure of [this] matches [expected]. */
internal fun JavadocContent?.assertStructure(expected: String?, message: String? = null) {
    // Generate a string representation of the model structure.
    val actualStructure = this?.dumpContentStructure()
    assertEquals(expected?.trimIndent(), actualStructure?.trimEnd(), message)
}
