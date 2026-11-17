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

import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.type.TypeString

/**
 * Raw representation of a `<T extends Bound1 & Bound2>` type parameter declaration parsed from a
 * signature file before creating a [TypeParameterItem] or parsing bound [TypeItem]s.
 */
class ParsedTypeParameter(
    /** The name of the type parameter (e.g. `"T"`). */
    val name: String,

    /** `true` if the type parameter was declared with the `reified` keyword. */
    val isReified: Boolean,

    /** The raw [TypeString] bounds declared after `extends`, or empty if unbounded. */
    val bounds: List<TypeString>,
)
