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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.google.turbine.binder.sym.MethodSymbol

class TurbineMethodItem(
    override val codebase: Codebase,
    private val methodSymbol: MethodSymbol,
    private val parameters: List<ParameterItem>,
    private val containingClass: TurbineClassItem,
    override val modifiers: TurbineModifierItem,
) : TurbineItem(codebase, modifiers), MethodItem {

    private lateinit var superMethods: List<MethodItem>
    private lateinit var throwsTypes: List<ClassItem>

    override var inheritedMethod: Boolean = false
    override var inheritedFrom: ClassItem? = null

    override fun name(): String = methodSymbol.name()

    override fun parameters(): List<ParameterItem> = parameters

    override fun returnType(): TypeItem = TODO("b/295800205")

    override fun throwsTypes(): List<ClassItem> = throwsTypes

    override fun isExtensionMethod(): Boolean {
        TODO("b/295800205")
    }

    override fun isConstructor(): Boolean = false

    override fun containingClass(): ClassItem = containingClass

    override fun superMethods(): List<MethodItem> = superMethods

    override fun equals(other: Any?): Boolean {
        TODO("b/295800205")
    }

    override fun hashCode(): Int {
        TODO("b/295800205")
    }

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    override fun duplicate(targetContainingClass: ClassItem): TurbineMethodItem =
        TODO("b/295800205")

    override fun findMainDocumentation(): String = TODO("b/295800205")

    override fun typeParameterList(): TypeParameterList {
        TODO("b/295800205")
    }
}
