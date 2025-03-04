/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.item.DefaultValue

/** Encapsulates information about default values retrieved from the annotations. */
class TurbineDefaultValue(private val modifiers: ModifierList) : DefaultValue {

    override fun hasDefaultValue(): Boolean = isDefaultValueKnown()

    override fun isDefaultValueKnown(): Boolean {
        return modifiers.hasAnnotation(AnnotationItem::isDefaultValue)
    }

    override fun value(): String? {
        val annotation = modifiers.findAnnotation(AnnotationItem::isDefaultValue)
        return annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
    }

    override fun duplicate(parameter: ParameterItem) = TurbineDefaultValue(parameter.modifiers)
}
