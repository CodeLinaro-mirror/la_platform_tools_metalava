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

package com.android.tools.metalava

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.source.doc.containsWord

/** Returns `true` if [DocContent] contains the word `null`. */
val NULL_WORD_PREDICATE = DocContentPredicates.textContainsAny { it.containsWord("null") }

/** Returns `true` if this contains the word `null`. */
fun DocContent?.containsNullWord() = this?.check(NULL_WORD_PREDICATE) == true

/** Returns true if the textual parts of the documentation contain [word]. */
fun ItemDocumentation.containsWord(word: String): Boolean {
    val predicate = DocContentPredicates.textContainsAny { it.containsWord(word) }
    return check(predicate)
}
