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

import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocContentPredicate
import com.android.tools.metalava.model.source.javadoc.JavadocContentVisitor

/** Extends [JavadocContentVisitor] to visit [BlockTagSection]s as well as [JavadocContent]. */
internal interface DocCommentVisitor<R> : JavadocContentVisitor<R> {
    fun visit(blockTagSection: BlockTagSection): R
}

/** A specialised [DocCommentVisitor] that can be treated as a predicate on [DocComment]. */
internal interface DocCommentPredicate : DocCommentVisitor<Boolean>, JavadocContentPredicate
