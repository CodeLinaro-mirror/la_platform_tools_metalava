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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.type.TypeString

/**
 * Raw representation of a `record_component` declaration parsed from a signature file before
 * creating a [RecordComponentItem] or parsing its [TypeItem].
 */
class ParsedRecordComponent(
    /** The [SourcePosition] of the `record_component` keyword in the signature file. */
    val sourcePosition: SourcePosition,

    /** The modifiers (including annotations) of the record component. */
    val modifiers: MutableModifierList,

    /** The name of the record component. */
    val name: String,

    /** The raw [TypeString] of the record component's type. */
    val typeString: TypeString,

    /** The 0-based `#<index>` of the record component in the record header. */
    val recordComponentIndex: Int,
)
