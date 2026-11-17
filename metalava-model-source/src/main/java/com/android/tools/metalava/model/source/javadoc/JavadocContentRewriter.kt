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
 * Supports rewriting [JavadocContent] into an optional [JavadocContent].
 *
 * One example use is supporting tag specific behavior such as for `@param`.
 * * When initially read from the sources the `@param p content` block tag behaves like every other
 *   block tag and stores all its contents, i.e. parameter name and description in a
 *   `JavadocContent` instance. However, various parts of Metalava need access to the parameter name
 *   which needs removing from the content. This can be used to remove the parameter name from the
 *   content.
 */
internal interface JavadocContentRewriter : JavadocContentVisitor<JavadocContent?> {
    /**
     * Entry point for applying this to [content].
     *
     * Using this allows implementations to hide implementation details of exactly how this is
     * applied to [content].
     */
    fun rewrite(content: JavadocContent?): JavadocContent? {
        return content?.accept(this)
    }

    /**
     * Rewrite a [JavadocContentList] into optional [JavadocContent].
     * * Return [list] if rewriting does not change it.
     * * Return `null` if rewriting removes all the items from [JavadocContentList.contents].
     * * Return a [JavadocContent] instance if rewriting removes all but one of the items in
     *   [JavadocContentList.contents].
     * * Otherwise, return a new [JavadocContent] that is the result of rewriting.
     */
    override fun visit(list: JavadocContentList): JavadocContent?

    /**
     * Rewrite a [JavadocContentList] into optional [JavadocContent].
     * * Return [text] if rewriting does not change it.
     * * Return `null` if the rewriting removes all the text from [JavadocText.contents].
     * * Return a new [JavadocText] if rewriting changes the contents of [JavadocText.contents].
     * * Otherwise, return a new [JavadocContent] that is the result of rewriting.
     */
    override fun visit(text: JavadocText): JavadocContent?

    /**
     * Rewrite a [JavadocContentList] into optional [JavadocContent].
     * * Return [inlineTag] if rewriting does not change it.
     * * Return `null` if the [JavadocInlineTag] needs to be removed completely.
     * * Return a new [JavadocInlineTag] if its contents were changed.
     * * Otherwise, return a new [JavadocContent] that is the result of rewriting.
     */
    override fun visit(inlineTag: JavadocInlineTag): JavadocContent?
}
