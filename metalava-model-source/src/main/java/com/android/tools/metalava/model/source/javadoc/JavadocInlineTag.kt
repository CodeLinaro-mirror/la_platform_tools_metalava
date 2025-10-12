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

/**
 * An inline tag within a block of Javadoc.
 *
 * @property tagType the type of the inline tag, e.g. `link`, `code`, etc.
 * @property content the optional content, e.g. for `{@link ref}` it would be the reference, for
 *   `{@inheritDoc}` it would be `null`.
 */
internal class JavadocInlineTag(val tagType: String, val content: JavadocContent?) :
    JavadocContent {
    /**
     * An inline tag occupies multiple lines if the nested content, if present, occupies multiple
     * lines.
     */
    override fun isMultiLine() = content?.isMultiLine() == true

    /** An inline tag does not start with a newline. */
    override fun startsWithNewline() = false

    override fun accept(visitor: JavadocContentVisitor) {
        visitor.visit(this)
    }
}
