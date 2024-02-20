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
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.binder.sym.MethodSymbol

internal open class TurbineClassItem(
    codebase: TurbineBasedCodebase,
    private val name: String,
    private val fullName: String,
    private val qualifiedName: String,
    private val classSymbol: ClassSymbol,
    modifiers: TurbineModifierItem,
    override val classKind: ClassKind,
    override val typeParameterList: TypeParameterList,
    documentation: String,
    private val source: SourceFile?
) : TurbineItem(codebase, modifiers, documentation), ClassItem {

    override var artifact: String? = null

    override var hasPrivateConstructor: Boolean = false

    override var stubConstructor: ConstructorItem? = null

    internal lateinit var innerClasses: List<TurbineClassItem>

    private var superClassType: ClassTypeItem? = null

    private var allInterfaces: List<ClassItem>? = null

    internal lateinit var containingPackage: TurbinePackageItem

    internal lateinit var fields: List<TurbineFieldItem>

    internal lateinit var methods: MutableList<TurbineMethodItem>

    internal lateinit var constructors: List<TurbineConstructorItem>

    internal var containingClass: TurbineClassItem? = null

    private lateinit var interfaceTypesList: List<ClassTypeItem>

    private var asType: ClassTypeItem? = null

    internal var hasImplicitDefaultConstructor = false

    private var retention: AnnotationRetention? = null

    override fun allInterfaces(): Sequence<ClassItem> {
        if (allInterfaces == null) {
            val interfaces = mutableSetOf<ClassItem>()

            // Add self as interface if applicable
            if (isInterface()) {
                interfaces.add(this)
            }

            // Add all the interfaces of super class
            superClass()?.let { supClass ->
                supClass.allInterfaces().forEach { interfaces.add(it) }
            }

            // Add all the interfaces of direct interfaces
            interfaceTypesList.forEach { interfaceType ->
                val itf = interfaceType.asClass()
                itf?.allInterfaces()?.forEach { interfaces.add(it) }
            }

            allInterfaces = interfaces.toList()
        }

        return allInterfaces!!.asSequence()
    }

    override fun constructors(): List<ConstructorItem> = constructors

    override fun containingClass(): TurbineClassItem? = containingClass

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage

    override fun fields(): List<FieldItem> = fields

    override fun getRetention(): AnnotationRetention {
        retention?.let {
            return it
        }

        if (!isAnnotationType()) {
            error("getRetention() should only be called on annotation classes")
        }

        retention = ClassItem.findRetention(this)
        return retention!!
    }

    override fun hasImplicitDefaultConstructor(): Boolean = hasImplicitDefaultConstructor

    override fun createDefaultConstructor(): ConstructorItem {
        val sym = MethodSymbol(0, classSymbol, name)
        return TurbineConstructorItem.createDefaultConstructor(codebase, this, sym)
    }

    override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    override fun innerClasses(): List<ClassItem> = innerClasses

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypesList

    override fun isDefined(): Boolean {
        TODO("b/295800205")
    }

    override fun methods(): List<MethodItem> = methods

    /**
     * [PropertyItem]s are kotlin specific and it is unlikely that Turbine will ever support Kotlin
     * so just return an empty list.
     */
    override fun properties(): List<PropertyItem> = emptyList()

    override fun simpleName(): String = name

    override fun qualifiedName(): String = qualifiedName

    override fun fullName(): String = fullName

    override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        interfaceTypesList = interfaceTypes
    }

    internal fun setSuperClassType(superClassType: ClassTypeItem?) {
        this.superClassType = superClassType
    }

    override fun superClass(): ClassItem? = superClassType?.asClass()

    override fun superClassType(): ClassTypeItem? = superClassType

    override fun type(): ClassTypeItem {
        if (asType == null) {
            val parameters =
                typeParameterList.map { createVariableType(it as TurbineTypeParameterItem) }
            val mods = DefaultTypeModifiers.create(modifiers.annotations())
            val outerClassType = containingClass?.type()
            asType = DefaultClassTypeItem(codebase, mods, qualifiedName, parameters, outerClassType)
        }
        return asType!!
    }

    private fun createVariableType(typeParam: TurbineTypeParameterItem): VariableTypeItem {
        val mods = DefaultTypeModifiers.create(typeParam.modifiers.annotations())
        return DefaultVariableTypeItem(mods, typeParam)
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is ClassItem && qualifiedName() == other.qualifiedName()
    }

    override fun getSourceFile(): SourceFile? = source

    override fun inheritMethodFromNonApiAncestor(template: MethodItem): MethodItem {
        val method = template as TurbineMethodItem
        val replacementMap = mapTypeVariables(method.containingClass())
        val retType = method.returnType().convertType(replacementMap)
        val mods = method.modifiers.duplicate()
        val params =
            method.parameters().map { TurbineParameterItem.duplicate(codebase, it, replacementMap) }

        val duplicateMethod =
            TurbineMethodItem(
                codebase,
                method.getSymbol(),
                this,
                retType,
                mods,
                method.typeParameterList,
                method.documentation,
                method.defaultValue(),
            )
        mods.setOwner(duplicateMethod)
        duplicateMethod.parameters = params
        duplicateMethod.inheritedFrom = method.containingClass()
        duplicateMethod.throwableTypes = method.throwableTypes

        return duplicateMethod
    }

    override fun addMethod(method: MethodItem) {
        methods.add(method as TurbineMethodItem)
    }
}
