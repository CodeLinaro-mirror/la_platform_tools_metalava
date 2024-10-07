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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.reporter.FileLocation
import java.io.File

/** Supports building a [DefaultCodebase] that is a subset of another [DefaultCodebase]. */
class TextCodebaseBuilder private constructor(private val assembler: TextCodebaseAssembler) {

    companion object {
        fun build(
            location: File,
            description: String,
            annotationManager: AnnotationManager,
            block: TextCodebaseBuilder.() -> Unit
        ): Codebase {
            val assembler =
                TextCodebaseAssembler.createAssembler(
                    location = location,
                    description = description,
                    annotationManager = annotationManager,
                    classResolver = null,
                )
            val builder = TextCodebaseBuilder(assembler)
            builder.block()

            return assembler.codebase
        }
    }

    val codebase = assembler.codebase

    val description by codebase::description

    private val itemFactory = assembler.itemFactory

    private fun getOrAddPackage(pkgName: String) = codebase.findOrCreatePackage(pkgName)

    fun addPackage(pkg: PackageItem) {
        codebase.addPackage(pkg as DefaultPackageItem)
    }

    fun addClass(cls: ClassItem) {
        // Replicate some of the registration code from DefaultClassItem initialization block. This
        // does not register classes correctly. e.g. It adds nested classes as top level classes in
        // the package. While that is strictly speaking invalid it works for this which is only used
        // to create a very short-lived Codebase that is written out to a JDiff file.
        // TODO(b/369078254): Clean this up.
        codebase.registerClass(cls as DefaultClassItem)
        val containingPackage = getOrAddPackage(cls.containingPackage().qualifiedName())
        containingPackage.addTopClass(cls)

        // If the class is emittable then make sure its package is too.
        if (cls.emit) {
            containingPackage.emit = true
        }
    }

    fun addConstructor(ctor: ConstructorItem) {
        val cls = getOrAddClass(ctor.containingClass())
        cls.addConstructor(ctor)
    }

    fun addMethod(method: MethodItem) {
        val cls = getOrAddClass(method.containingClass())
        cls.addMethod(method)
    }

    fun addField(field: FieldItem) {
        val cls = getOrAddClass(field.containingClass())
        cls.addField(field)
    }

    fun addProperty(property: PropertyItem) {
        val cls = getOrAddClass(property.containingClass())
        cls.addProperty(property)
    }

    private fun getOrAddClass(fullClass: ClassItem): DefaultClassItem {
        val cls = codebase.findClassInCodebase(fullClass.qualifiedName())
        if (cls != null) {
            return cls
        }
        val pkg = getOrAddPackage(fullClass.containingPackage().qualifiedName())

        return itemFactory.createClassItem(
            fileLocation = FileLocation.UNKNOWN,
            modifiers = fullClass.modifiers,
            classKind = fullClass.classKind,
            containingClass = null,
            containingPackage = pkg,
            qualifiedName = fullClass.qualifiedName(),
            typeParameterList = fullClass.typeParameterList,
            origin = fullClass.origin,
            superClassType = fullClass.superClassType(),
            interfaceTypes = fullClass.interfaceTypes(),
        )
    }
}
