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

package com.android.tools.metalava.model.turbine

import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.tree.Tree

/** Supports resolving field references in expressions to specific fields in classes. */
internal interface FieldResolver {
    /**
     * Resolves a [Tree.ConstVarName] to a [TypeBoundClass.FieldInfo] if possible, returns `null`
     * otherwise.
     */
    fun resolveField(t: Tree.ConstVarName): TypeBoundClass.FieldInfo?
}

/** Resolves field references within [origin] using [BindingResult.resolveField]. */
internal class TurbineFieldResolver(
    private val origin: ClassSymbol,
    private val bindingResult: BindingResult,
) : FieldResolver {
    override fun toString() = "TurbineFieldResolver($origin)"

    override fun resolveField(t: Tree.ConstVarName): TypeBoundClass.FieldInfo? =
        bindingResult.resolveField(origin, t)
}
