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

import com.google.common.collect.Iterables
import com.google.turbine.binder.ConstEvaluator
import com.google.turbine.binder.Resolve
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.Env
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.MemberImportIndex
import com.google.turbine.binder.lookup.Scope
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.model.TurbineFlag
import com.google.turbine.tree.Tree

/**
 * This copies functionality used within [ConstEvaluator].
 *
 * Ideally, the Turbine team will be able to refactor [ConstEvaluator] to pull that functionality
 * out for use by us.
 */
internal class TurbineFieldResolver(
    private val origin: ClassSymbol,
    private val owner: ClassSymbol,
    private val memberImports: MemberImportIndex,
    private val scope: Scope,
    private val env: CompoundEnv<ClassSymbol, TypeBoundClass>
) {
    override fun toString() = "TurbineFieldResolver($origin)"

    fun resolveField(t: Tree.ConstVarName): TypeBoundClass.FieldInfo? {
        val simpleName = t.name()[0]
        var field = lexicalField(env, owner, simpleName)
        if (field != null) {
            return field
        }
        field = resolveQualifiedField(t)
        if (field != null) {
            return field
        }
        val classSymbol = memberImports.singleMemberImport(simpleName.value())
        if (classSymbol != null) {
            field = Resolve.resolveField(env, origin, classSymbol, simpleName)
            if (field != null) {
                return field
            }
        }
        val it = memberImports.onDemandImports()
        while (it.hasNext()) {
            field = Resolve.resolveField(env, origin, it.next(), simpleName)
            if (field == null) {
                continue
            }
            if ((field.access() and TurbineFlag.ACC_PRIVATE) == TurbineFlag.ACC_PRIVATE) {
                continue
            }
            return field
        }

        return null
    }

    private fun resolveQualifiedField(t: Tree.ConstVarName): TypeBoundClass.FieldInfo? {
        if (t.name().size <= 1) {
            return null
        }
        val result = scope.lookup(LookupKey(t.name())) ?: return null
        if (result.remaining().isEmpty()) {
            return null
        }
        var sym = result.sym() as ClassSymbol
        for (i in 0 until result.remaining().size - 1) {
            sym = Resolve.resolve(env, sym, sym, result.remaining()[i]) ?: return null
        }
        return Resolve.resolveField(env, origin, sym, Iterables.getLast(result.remaining()))
    }

    private fun lexicalField(
        env: Env<ClassSymbol, TypeBoundClass>,
        initialSym: ClassSymbol?,
        name: Tree.Ident
    ): TypeBoundClass.FieldInfo? {
        var sym = initialSym
        while (sym != null) {
            val info = env.getNonNull(sym)
            val field = Resolve.resolveField(env, origin, sym, name)
            if (field != null) {
                return field
            }
            sym = info.owner()
        }
        return null
    }
}
