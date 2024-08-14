/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import java.io.PrintWriter

internal class DexApiWriter(
    private val writer: PrintWriter,
) : DelegatedVisitor {

    override fun visitClass(cls: ClassItem) {
        writer.print(cls.type().internalName())
        writer.print("\n")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        writeCallable(constructor)
    }

    override fun visitMethod(method: MethodItem) {
        if (method.inheritedFromAncestor) {
            return
        }

        writeCallable(method)
    }

    private fun writeCallable(callable: CallableItem) {
        writer.print(callable.containingClass().type().internalName())
        writer.print("->")
        writer.print(callable.internalName())
        writer.print("(")
        for (pi in callable.parameters()) {
            writer.print(pi.type().internalName())
        }
        writer.print(")")
        if (callable.isConstructor()) {
            writer.print("V")
        } else {
            val returnType = callable.returnType()
            writer.print(returnType.internalName())
        }
        writer.print("\n")
    }

    override fun visitField(field: FieldItem) {
        val cls = field.containingClass()

        writer.print(cls.type().internalName())
        writer.print("->")
        writer.print(field.name())
        writer.print(":")
        writer.print(field.type().internalName())
        writer.print("\n")
    }
}
