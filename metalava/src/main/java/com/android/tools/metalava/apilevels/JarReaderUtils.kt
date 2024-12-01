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
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun Api.readAndroidJar(sdkVersion: SdkVersion, jar: File) {
    update(sdkVersion)
    readJar(sdkVersion, jar)
}

fun Api.readExtensionJar(
    extVersion: ExtVersion,
    module: String,
    jar: File,
    nextSdkVersion: SdkVersion
) {
    readJar(nextSdkVersion, jar, extVersion, module)
}

fun Api.readJar(
    sdkVersion: SdkVersion,
    jar: File,
    extVersion: ExtVersion? = null,
    module: String? = null
) {
    val fis = FileInputStream(jar)
    ZipInputStream(fis).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.name.endsWith(SdkConstants.DOT_CLASS)) {
                entry = zis.nextEntry
                continue
            }
            val bytes = zis.readBytes()
            val reader = ClassReader(bytes)
            val classNode = ClassNode(Opcodes.ASM5)
            reader.accept(classNode, 0)

            val classDeprecated = isDeprecated(classNode.access)
            val theClass =
                addClass(
                    classNode.name,
                    sdkVersion,
                    classDeprecated,
                )
            extVersion?.let { theClass.updateExtension(extVersion) }
            module?.let { theClass.updateMainlineModule(module) }

            theClass.updateHidden((classNode.access and Opcodes.ACC_PUBLIC) == 0)

            // super class
            if (classNode.superName != null) {
                theClass.addSuperClass(classNode.superName, sdkVersion)
            }

            // interfaces
            for (interfaceName in classNode.interfaces) {
                theClass.addInterface(interfaceName, sdkVersion)
            }

            // fields
            for (field in classNode.fields) {
                val fieldNode = field as FieldNode
                if ((fieldNode.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
                    continue
                }
                if (!fieldNode.name.startsWith("this\$") && fieldNode.name != "\$VALUES") {
                    val apiField =
                        theClass.addField(
                            fieldNode.name,
                            sdkVersion,
                            classDeprecated || isDeprecated(fieldNode.access),
                        )
                    extVersion?.let { apiField.updateExtension(extVersion) }
                }
            }

            // methods
            for (method in classNode.methods) {
                val methodNode = method as MethodNode
                if ((methodNode.access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) == 0) {
                    continue
                }
                if (methodNode.name != "<clinit>") {
                    val apiMethod =
                        theClass.addMethod(
                            methodNode.name + methodNode.desc,
                            sdkVersion,
                            classDeprecated || isDeprecated(methodNode.access),
                        )
                    extVersion?.let { apiMethod.updateExtension(extVersion) }
                }
            }

            entry = zis.nextEntry
        }
    }
}

private fun isDeprecated(access: Int) = (access and Opcodes.ACC_DEPRECATED) != 0
