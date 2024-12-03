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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem

/**
 * Visits the API codebase and inserts into the [Api] the classes, methods and fields.
 *
 * The [Item]s to be visited is determined by the [codebaseFragment].
 */
fun addApisFromCodebase(
    api: Api,
    sdkVersion: SdkVersion,
    codebaseFragment: CodebaseFragment,
    useInternalNames: Boolean,
) {
    val delegatedVisitor =
        object : DelegatedVisitor {
            val updater = ApiElement.Updater.forSdkVersion(sdkVersion)

            var currentClass: ApiClass? = null

            override fun afterVisitClass(cls: ClassItem) {
                currentClass = null
            }

            override fun visitClass(cls: ClassItem) {
                val newClass = api.updateClass(cls.nameInApi(), updater, cls.effectivelyDeprecated)
                currentClass = newClass

                if (cls.isClass()) {
                    val superClass = cls.superClass()
                    if (superClass != null) {
                        newClass.updateSuperClass(superClass.nameInApi(), updater)
                    }
                } else if (cls.isInterface()) {
                    val superClass = cls.superClass()
                    if (superClass != null && !superClass.isJavaLangObject()) {
                        newClass.updateInterface(superClass.nameInApi(), updater)
                    }
                } else if (cls.isEnum()) {
                    // Implicit super class; match convention from bytecode
                    if (newClass.name != enumClass) {
                        newClass.updateSuperClass(enumClass, updater)
                    }

                    // Mimic doclava enum methods
                    enumMethodNames(newClass.name).forEach { name ->
                        newClass.updateMethod(name, updater, false)
                    }
                } else if (cls.isAnnotationType()) {
                    // Implicit super class; match convention from bytecode
                    if (newClass.name != annotationClass) {
                        newClass.updateSuperClass(objectClass, updater)
                        newClass.updateInterface(annotationClass, updater)
                    }
                }

                // Ensure we don't end up with
                //    -  <extends name="java/lang/Object"/>
                //    +  <extends name="java/lang/Object" removed="29"/>
                // which can happen because the bytecode always explicitly contains extends
                // java.lang.Object
                // but in the source code we don't see it, and the lack of presence of this
                // shouldn't be
                // taken as a sign that we no longer extend object. But only do this if the class
                // didn't
                // previously extend object and now extends something else.
                if (
                    (cls.isClass() || cls.isInterface()) &&
                        newClass.superClasses.singleOrNull()?.name == objectClass
                ) {
                    newClass.updateSuperClass(objectClass, updater)
                }

                for (interfaceType in cls.interfaceTypes()) {
                    val interfaceClass = interfaceType.asClass() ?: return
                    newClass.updateInterface(interfaceClass.nameInApi(), updater)
                }
            }

            private fun visitCallable(callable: CallableItem) {
                if (callable.isPrivate || callable.isPackagePrivate) {
                    return
                }
                currentClass?.updateMethod(
                    callable.nameInApi(),
                    updater,
                    callable.effectivelyDeprecated
                )
            }

            override fun visitConstructor(constructor: ConstructorItem) {
                visitCallable(constructor)
            }

            override fun visitMethod(method: MethodItem) {
                visitCallable(method)
            }

            override fun visitField(field: FieldItem) {
                if (field.isPrivate || field.isPackagePrivate) {
                    return
                }
                currentClass?.updateField(field.nameInApi(), updater, field.effectivelyDeprecated)
            }

            /** The name of the field in this [Api], based on [useInternalNames] */
            fun FieldItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    name()
                }
            }

            /** The name of the method in this [Api], based on [useInternalNames] */
            fun CallableItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName() +
                        // Use "V" instead of the type of the constructor for backwards
                        // compatibility
                        // with the older bytecode
                        internalDesc(voidConstructorTypes = true)
                } else {
                    val paramString = parameters().joinToString(",") { it.type().toTypeString() }
                    name() + typeParameterList + "(" + paramString + ")"
                }
            }

            /** The name of the class in this [Api], based on [useInternalNames] */
            fun ClassItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    qualifiedName()
                }
            }

            // The names of some common classes, based on [useInternalNames]
            val objectClass = nameForClass("java", "lang", "Object")
            val annotationClass = nameForClass("java", "lang", "annotation", "Annotation")
            val enumClass = nameForClass("java", "lang", "Enum")

            /** Generates a class name from the package and class names in [nameParts] */
            fun nameForClass(vararg nameParts: String): String {
                val separator = if (useInternalNames) "/" else "."
                return nameParts.joinToString(separator)
            }

            /** The names of the doclava enum methods, based on [useInternalNames] */
            fun enumMethodNames(className: String): List<String> {
                return if (useInternalNames) {
                    listOf("valueOf(Ljava/lang/String;)L$className;", "values()[L$className;")
                } else {
                    listOf("valueOf(java.lang.String)", "values()")
                }
            }
        }

    codebaseFragment.accept(delegatedVisitor)
}

/**
 * Like [CallableItem.internalName] but is the desc-portion of the internal signature, e.g. for the
 * method "void create(int x, int y)" the internal name of the constructor is "create" and the desc
 * is "(II)V"
 */
fun CallableItem.internalDesc(voidConstructorTypes: Boolean = false): String {
    val sb = StringBuilder()
    sb.append("(")

    // Inner, i.e. non-static nested, classes get an implicit constructor parameter for the
    // outer type
    if (
        isConstructor() &&
            containingClass().containingClass() != null &&
            !containingClass().modifiers.isStatic()
    ) {
        sb.append(containingClass().containingClass()?.type()?.internalName() ?: "")
    }

    for (parameter in parameters()) {
        sb.append(parameter.type().internalName())
    }

    sb.append(")")
    sb.append(if (voidConstructorTypes && isConstructor()) "V" else returnType().internalName())
    return sb.toString()
}
