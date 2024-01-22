/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation

class TurbineParameterItem(
    codebase: TurbineBasedCodebase,
    private val name: String,
    private val containingMethod: TurbineMethodItem,
    override val parameterIndex: Int,
    private val type: TurbineTypeItem,
    modifiers: TurbineModifierItem,
) : TurbineItem(codebase, modifiers), ParameterItem {

    override fun name(): String = name

    override fun publicName(): String? {
        // Java: Look for @ParameterName annotation
        val annotation = modifiers.findAnnotation(AnnotationItem::isParameterName)
        return annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
    }

    override fun containingMethod(): MethodItem = containingMethod

    override fun hasDefaultValue(): Boolean = isDefaultValueKnown()

    override fun isDefaultValueKnown(): Boolean {
        return modifiers.hasAnnotation(AnnotationItem::isDefaultValue)
    }

    override fun defaultValue(): String? {
        val annotation = modifiers.findAnnotation(AnnotationItem::isDefaultValue)
        return annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
    }

    override fun equals(other: Any?): Boolean = TODO("b/295800205")

    override fun hashCode(): Int = TODO("b/295800205")

    override fun type(): TypeItem = type

    override fun isVarArgs(): Boolean = modifiers.isVarArg()
}
