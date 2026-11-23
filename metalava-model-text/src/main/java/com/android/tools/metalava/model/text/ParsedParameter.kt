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
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeString

/**
 * Raw representation of a callable or property context parameter parsed from a signature file
 * before creating a [ParameterItem] or parsing its [TypeItem].
 *
 * This intermediate representation is also needed when constructing a [ParameterItem] because
 * [TextTypeItemFactory.getMethodParameterType] requires a [MethodFingerprint] with the total number
 * of method parameters.
 */
class ParsedParameter(
    /**
     * The internal name of the parameter (`publicName` if present, or `"arg<index+1>"` / `"_"`
     * otherwise).
     */
    val name: String,

    /** The public parameter name if declared in the signature file, or `null` if omitted. */
    val publicName: String?,

    /** `true` if the `optional` keyword was present, indicating a default value. */
    val hasDefaultValue: Boolean,

    /** The raw [TypeString] of the parameter's type. */
    val typeString: TypeString,

    /** The modifiers (including annotations and `vararg`) of the parameter. */
    val modifiers: MutableModifierList,

    /** The [SourcePosition] of the parameter in the signature file. */
    val sourcePosition: SourcePosition,

    /** The 0-based index of the parameter in the parameter list. */
    val index: Int,

    /**
     * The explicit [ParameterKind] (`context` or `receiver`) if specified in the signature file, or
     * `null` if none was specified.
     */
    val optionalKind: ParameterKind?,
)
