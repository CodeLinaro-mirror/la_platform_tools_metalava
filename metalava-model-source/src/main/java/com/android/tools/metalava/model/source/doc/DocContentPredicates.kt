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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.model.source.javadoc.ContainsInlineTagVisitor
import com.android.tools.metalava.model.source.javadoc.FindPossiblyImportedTypeReferencesVisitor
import com.android.tools.metalava.model.source.javadoc.TextContainsAnyVisitor

/** Marker interface that represents a predicate that can be applied to [DocContent]. */
object DocContentPredicates {
    /** Check if the textual parts of [DocContent] match [predicate]. */
    fun textContainsAny(predicate: (String) -> Boolean): DocContentPredicate =
        TextContainsAnyVisitor(predicate)

    /** Check if the [DocContent] contains an inline tag of type [tagTypeName]. */
    fun containsInlineTag(tagTypeName: String): DocContentPredicate =
        ContainsInlineTagVisitor(tagTypeName)

    /**
     * Return a [DocContentPredicate] that checks if a [DocContent] contains a possible reference to
     * an imported type.
     */
    fun containsPossiblyImportedTypeReference(importedTypeName: String): DocContentPredicate =
        FindPossiblyImportedTypeReferencesVisitor(importedTypeName)
}
