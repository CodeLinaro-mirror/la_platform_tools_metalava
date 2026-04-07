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
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
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
    updater: ApiHistoryUpdater,
    codebaseFragment: CodebaseFragment,
) {
    val useInternalNames = api.useInternalNames

    // Keep track of the versions added to this api, if necessary.
    updater.update(api)

    val delegatedVisitor =
        object : DelegatedVisitor {

            var currentClass: ApiClass? = null

            override fun afterVisitClass(cls: ClassItem) {
                currentClass = null
            }

            override fun visitClass(cls: ClassItem) {
                val newClass =
                    api.updateClass(
                        cls.nameInApi(),
                        updater,
                        cls.effectivelyDeprecated,
                        cls.isEnum(),
                    )
                currentClass = newClass

                // Add the super class, if available.
                val superClass = cls.superClass()
                if (superClass == null) {
                    // If no explicit super class has been provided then see if the ClassKind can
                    // provide one.
                    cls.classKind.binarySuperClassType?.let { superClassType ->
                        newClass.updateSuperClass(superClassType.nameInApi(), updater)
                    }
                } else {
                    newClass.updateSuperClass(superClass.nameInApi(), updater)
                }

                // Add the interfaces, if any.
                for (interfaceType in cls.interfaceTypes()) {
                    val interfaceClass = interfaceType.resolveClass(cls.codebase) ?: return
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

            /** The name of the field in this [Api], based on [Api.useInternalNames] */
            fun FieldItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    name()
                }
            }

            /** The name of the method in this [Api], based on [Api.useInternalNames] */
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

            /** The name of the class in this [Api], based on [Api.useInternalNames] */
            fun ClassItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    qualifiedName()
                }
            }

            /**
             * The name of this class type in this [Api], based on [Api.useInternalNames].
             *
             * This does not work on nested classes, but it should only ever be called on the
             * [ClassKind.binarySuperClassType].
             */
            fun ClassTypeItem.nameInApi() =
                if (useInternalNames) {
                    qualifiedName.replace('.', '/')
                } else {
                    qualifiedName
                }
        }

    codebaseFragment.accept(delegatedVisitor)
}
