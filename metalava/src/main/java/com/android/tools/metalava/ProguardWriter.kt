/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.VisibilityLevel
import java.io.PrintWriter

class ProguardWriter(
    private val writer: PrintWriter,
) : DelegatedVisitor {

    override fun visitClass(cls: ClassItem) {
        writer.print("-keep class ")
        writer.print(getCleanTypeName(cls.type()))
        writer.print(" {\n")
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.print("}\n")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        writer.print("    ")
        writer.print("<init>")

        writeParametersKeepList(constructor.parameters())
        writer.print(";\n")
    }

    override fun visitMethod(method: MethodItem) {
        writer.print("    ")
        val modifiers = method.modifiers
        val visibilityLevel = modifiers.getVisibilityLevel()
        if (visibilityLevel != VisibilityLevel.PACKAGE_PRIVATE) {
            writer.write(visibilityLevel.javaSourceCodeModifier + " ")
        }

        if (modifiers.isStatic()) {
            writer.print("static ")
        }
        if (modifiers.isAbstract()) {
            writer.print("abstract ")
        }
        if (modifiers.isSynchronized()) {
            writer.print("synchronized ")
        }

        writer.print(getCleanTypeName(method.returnType()))
        writer.print(" ")
        writer.print(method.name())

        writeParametersKeepList(method.parameters())

        writer.print(";\n")
    }

    private fun writeParametersKeepList(params: List<ParameterItem>) {
        writer.print("(")

        for (pi in params) {
            if (pi !== params[0]) {
                writer.print(", ")
            }
            writer.print(getCleanTypeName(pi.type()))
        }

        writer.print(")")
    }

    override fun visitField(field: FieldItem) {
        writer.print("    ")

        val modifiers = field.modifiers
        val visibilityLevel = modifiers.getVisibilityLevel()
        if (visibilityLevel != VisibilityLevel.PACKAGE_PRIVATE) {
            writer.write(visibilityLevel.javaSourceCodeModifier + " ")
        }

        if (modifiers.isStatic()) {
            writer.print("static ")
        }
        if (modifiers.isTransient()) {
            writer.print("transient ")
        }
        if (modifiers.isVolatile()) {
            writer.print("volatile ")
        }

        writer.print(getCleanTypeName(field.type()))

        writer.print(" ")
        writer.print(field.name())

        writer.print(";\n")
    }

    private fun getCleanTypeName(t: TypeItem?): String {
        return t?.toTypeString(PROGUARD_TYPE_STRING_CONFIGURATION) ?: ""
    }

    companion object {
        private val PROGUARD_TYPE_STRING_CONFIGURATION =
            TypeStringConfiguration(
                eraseGenerics = true,
                nestedClassSeparator = '$',
                treatVarargsAsArray = true,
            )
    }
}
