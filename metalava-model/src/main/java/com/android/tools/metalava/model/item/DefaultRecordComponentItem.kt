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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.reporter.FileLocation

internal class DefaultRecordComponentItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    modifiers: BaseModifierList,
    override val name: String,
    private val containingClass: ClassItem,
    type: TypeItem,
    override val recordComponentIndex: Int,
) :
    DefaultItem(
        codebase,
        fileLocation,
        sourceLanguage,
        modifiers,
    ),
    RecordComponentItem {
    override fun containingClass() = containingClass

    private var _type = type

    override fun type(): TypeItem = _type

    override val type: TypeItem
        get() = _type

    override fun setType(type: TypeItem) {
        this._type = type
    }
}
