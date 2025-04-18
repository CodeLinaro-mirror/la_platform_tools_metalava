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

import com.android.tools.metalava.model.ClassTypeItem

/** Base class for [FieldReferenceValue] implementations. */
internal abstract class DefaultFieldReferenceValue(
    final override val classTypeItem: ClassTypeItem,
    final override val fieldName: String,
) : DefaultValue(), FieldReferenceValue {
    init {
        require(classTypeItem.arguments.isEmpty()) {
            "Class type item ($classTypeItem) should not have any arguments"
        }
    }
}

internal class DefaultConstantFieldValue(
    classTypeItem: ClassTypeItem,
    fieldName: String,
    override val constantValue: ConstantValue?,
) : DefaultFieldReferenceValue(classTypeItem, fieldName), ConstantFieldValue

internal class DefaultEnumConstantValue(
    classTypeItem: ClassTypeItem,
    fieldName: String,
) : DefaultFieldReferenceValue(classTypeItem, fieldName), EnumConstantValue
