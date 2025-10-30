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

package com.android.tools.metalava.model.doc

import com.android.tools.metalava.model.ItemDocumentation

/**
 * An immutable block of content extracted from a documentation comment.
 *
 * @see DocContentOwner.docContent
 */
interface DocContent

/**
 * The owner of a block of content extracted from a documentation comment.
 *
 * @see ItemDocumentation.mainDescriptionOwner
 * @see ItemDocumentation.blockTagDescriptionOwner
 * @see ItemDocumentation.paramTagDescriptionOwner
 */
interface DocContentOwner {
    /**
     * The optional [DocContent], returns `null` if the underlying content is empty, i.e. has no
     * significant non-whitespace content .
     *
     * // TODO(b/450228132): Make sure this returns fully qualified content.
     */
    val docContent: DocContent?

    /**
     * Append [DocContent] to [docContent].
     *
     * If [docContent] is `null` then this will just set [docContent] to [other]. Otherwise, it will
     * append a line break separator, i.e. `<br>` between [docContent] and [other] and store the
     * result in [docContent].
     */
    fun append(other: DocContent)

    /**
     * Append [text] to [docContent].
     *
     * The [text] can contain text as well as inline tags. It will behave as if it was added to the
     * original source. It must be well-formed, if it is not then this will throw an exception.
     *
     * // TODO(b/450228132): Make sure a fully qualified version of [text] is appended.
     */
    fun append(text: String)
}
