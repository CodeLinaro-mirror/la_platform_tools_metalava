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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.value.Value.Companion.toString

internal class DefaultFieldReferenceValue(
    final override val qualifiedClassName: String,
    final override val fieldName: String,

    /**
     * The optional constant value of this field.
     *
     * Is `null` if the field does not reference a constant value.
     *
     * Note: This is NOT used in [equals], [hashCode] or [toString]. That is because this may be
     * provided lazily and accessing it may have side effects but those methods are not expected to
     * have side effects.
     */
    private val constantValue: ConstantValue? = null,
) : DefaultValue(), FieldReferenceValue {
    /** The [constantValue], if present, may be a [LiteralValue]. */
    override fun asLiteralValue() = constantValue?.asLiteralValue()
}
