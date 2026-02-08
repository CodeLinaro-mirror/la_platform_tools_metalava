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

package com.android.tools.metalava.model.source.javadoc

/**
 * A [TextContainsAnyVisitor] subclass that will look for invalid block tags in the text and also
 * look for matching inline tag names.
 */
internal class InvalidBlockUseVisitor :
    TextContainsAnyVisitor(
        { text ->
            // Find any '@' character first as there is no point in searching for the more
            // specific strings if it does not contain '@'.
            val index = text.indexOf('@')
            index != -1 && invalidBlockTags.any { tag -> text.indexOf(tag, index) != -1 }
        },
    ) {
    /** Override to check tag name as well. */
    override fun visit(inlineTag: JavadocInlineTag) =
        inlineTag.tagType.name in invalidBlockTagNames || super.visit(inlineTag)

    companion object {
        /** The set of invalid block tag names (without leading '@'). */
        private val invalidBlockTagNames =
            setOf(
                "hide",
                "removed",
                "doconly",
            )

        /** The set of invalid block tags (with leading '@'). */
        private val invalidBlockTags = invalidBlockTagNames.map { "@$it" }

        /**
         * Checks for the presence of `@hide`, `@removed` or `@doconly` which could cause problems
         * downstream, e.g. in `doclava`.
         */
        val INSTANCE = InvalidBlockUseVisitor()
    }
}
