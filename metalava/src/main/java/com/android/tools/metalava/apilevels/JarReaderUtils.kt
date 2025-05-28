/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.metalava.apilevels

import com.android.SdkConstants
import com.android.tools.metalava.model.JAVA_ENUM_VALUES
import com.android.tools.metalava.model.JAVA_ENUM_VALUE_OF
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun Api.readJar(
    jar: File,
    updater: ApiHistoryUpdater,
    filter: ((String) -> Boolean)? = null,
) {
    require(useInternalNames) { "Cannot add jars to Api that does not use internal names" }
    // Update the Api for this version of the jar.
    updater.update(this)
    val fis = FileInputStream(jar)
    ZipInputStream(fis).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            val entryName = entry.name
            if (!entryName.endsWith(SdkConstants.DOT_CLASS)) {
                continue
            }

            // If a filter is provided and returns false then ignore the entry.
            if (filter != null && !filter(entryName)) {
                continue
            }
            val bytes = zis.readBytes()
            val reader = ClassReader(bytes)
            val classNode = ClassNode(Opcodes.ASM5)
            reader.accept(classNode, 0)

            val classAccess = classNode.access
            val isEnum = (classAccess and Opcodes.ACC_ENUM) != 0

            val classDeprecated = isDeprecated(classAccess)
            val theClass =
                updateClass(
                    classNode.name,
                    updater,
                    classDeprecated,
                    isEnum,
                )

            theClass.updateHidden((classAccess and Opcodes.ACC_PUBLIC) == 0)

            // super class
            if (classNode.superName != null) {
                theClass.updateSuperClass(classNode.superName, updater)
            }

            // interfaces
            for (interfaceName in classNode.interfaces) {
                theClass.updateInterface(interfaceName, updater)
            }

            // fields
            for (field in classNode.fields) {
                val fieldNode = field as FieldNode
                if ((fieldNode.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
                    continue
                }
                if (!fieldNode.name.startsWith("this\$") && fieldNode.name != "\$VALUES") {
                    theClass.updateField(
                        fieldNode.name,
                        updater,
                        classDeprecated || isDeprecated(fieldNode.access),
                    )
                }
            }

            // If this is an enum class then it will contain two methods added by the compiler, i.e.
            //   public static E valueOf(String)
            //   public static E[] values()
            //
            // Those methods are not recorded in signature files as there is no point in tracking
            // their history separately from the class. So, they need to be ignored here.
            //
            // If needed, compute the description of the two enum methods to simplify comparison.
            val (valueOfDesc, valuesDesc) =
                if (isEnum) {
                    val enumType = Type.getObjectType(classNode.name)
                    "(Ljava/lang/String;)${enumType.descriptor}" to "()[$enumType"
                } else null to null

            // methods
            for (method in classNode.methods) {
                val methodNode = method as MethodNode
                val methodAccess = methodNode.access

                // The only methods of interest are public and protected methods.
                if ((methodAccess and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
                    continue
                }
                val methodName = method.name

                // The class initializer is an implementation detail.
                if (methodName == "<clinit>") {
                    continue
                }

                val methodDesc = methodNode.desc
                // Ignore synthetic enum methods, i.e. valueOf(String) and values().
                if (
                    isEnum &&
                        methodAccess and Opcodes.ACC_STATIC != 0 &&
                        (methodName == JAVA_ENUM_VALUE_OF && methodDesc == valueOfDesc) ||
                        (methodName == JAVA_ENUM_VALUES && methodDesc == valuesDesc)
                ) {
                    continue
                }

                // Add the method.
                theClass.updateMethod(
                    methodName + methodDesc,
                    updater,
                    classDeprecated || isDeprecated(methodAccess),
                )
            }
        }
    }
}

private fun isDeprecated(access: Int) = (access and Opcodes.ACC_DEPRECATED) != 0
