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

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeVisitor

open class TurbineClassItem(
    override val codebase: Codebase,
    private val name: String,
    private val qualifiedName: String,
    override val modifiers: ModifierList
) : ClassItem, TurbineItem(codebase = codebase, modifiers = modifiers) {

    override var artifact: String? = null

    override var hasPrivateConstructor: Boolean = false

    override val isTypeParameter: Boolean = false

    override var stubConstructor: ConstructorItem? = null

    override fun allInterfaces(): Sequence<ClassItem> {
        TODO("b/295800205")
    }

    override fun constructors(): List<ConstructorItem> {
        TODO("b/295800205")
    }

    override fun containingClass(): ClassItem? {
        TODO("b/295800205")
    }

    override fun containingPackage(): PackageItem {
        TODO("b/295800205")
    }

    override fun fields(): List<FieldItem> {
        TODO("b/295800205")
    }

    override fun getRetention(): AnnotationRetention {
        TODO("b/295800205")
    }

    override fun hasImplicitDefaultConstructor(): Boolean {
        TODO("b/295800205")
    }

    override fun hasTypeVariables(): Boolean {
        TODO("b/295800205")
    }

    override fun innerClasses(): List<ClassItem> {
        TODO("b/295800205")
    }

    override fun interfaceTypes(): List<TypeItem> {
        TODO("b/295800205")
    }

    override fun isAnnotationType(): Boolean {
        TODO("b/295800205")
    }

    override fun isDefined(): Boolean {
        TODO("b/295800205")
    }

    override fun isEnum(): Boolean {
        TODO("b/295800205")
    }

    override fun isInterface(): Boolean {
        TODO("b/295800205")
    }

    override fun methods(): List<MethodItem> {
        TODO("b/295800205")
    }

    override fun properties(): List<PropertyItem> {
        TODO("b/295800205")
    }

    override fun simpleName(): String = name

    override fun qualifiedName(): String = qualifiedName

    override fun fullName(): String {
        TODO("b/295800205")
    }

    override fun setInterfaceTypes(interfaceTypes: List<TypeItem>) {
        TODO("b/295800205")
    }

    override fun setSuperClass(superClass: ClassItem?, superClassType: TypeItem?) {
        TODO("b/295800205")
    }

    override fun superClass(): ClassItem? {
        TODO("b/295800205")
    }

    override fun superClassType(): TypeItem? {
        TODO("b/295800205")
    }

    override fun toType(): TypeItem {
        TODO("b/295800205")
    }

    override fun typeParameterList(): TypeParameterList {
        TODO("b/295800205")
    }

    override fun accept(visitor: ItemVisitor) {
        TODO("b/295800205")
    }

    override fun acceptTypes(visitor: TypeVisitor) {
        TODO("b/295800205")
    }

    override fun hashCode(): Int {
        TODO("b/295800205")
    }

    override fun equals(other: Any?): Boolean {
        return other is ClassItem && qualifiedName() == other.qualifiedName()
    }
}
