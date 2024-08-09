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
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.model.updateCopiedMethodState
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.binder.sym.ClassSymbol

internal open class TurbineClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    private val name: String,
    private val fullName: String,
    private val qualifiedName: String,
    private val classSymbol: ClassSymbol,
    modifiers: DefaultModifierList,
    override val classKind: ClassKind,
    override val typeParameterList: TypeParameterList,
    documentation: ItemDocumentation,
    private val source: SourceFile?
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.JAVA,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
    ),
    ClassItem {

    override var artifact: String? = null

    override var hasPrivateConstructor: Boolean = false

    override var stubConstructor: ConstructorItem? = null

    internal lateinit var nestedClasses: List<TurbineClassItem>

    private var superClassType: ClassTypeItem? = null

    private var allInterfaces: List<ClassItem>? = null

    internal lateinit var containingPackage: PackageItem

    internal lateinit var fields: List<FieldItem>

    internal lateinit var methods: MutableList<TurbineMethodItem>

    internal lateinit var constructors: List<TurbineConstructorItem>

    internal var containingClass: TurbineClassItem? = null

    private lateinit var interfaceTypesList: List<ClassTypeItem>

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
        return TurbineConstructorItem.createDefaultConstructor(codebase, this)
    }

    override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    override fun nestedClasses(): List<ClassItem> = nestedClasses

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypesList

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

    override fun superClassType(): ClassTypeItem? = superClassType

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = DefaultResolvedClassTypeItem.createForClass(this)
        }
        return cachedType
    }

    override fun equals(other: Any?) = equalsToItem(other)

    override fun hashCode() = hashCodeForItem()

    override fun getSourceFile(): SourceFile? = source

    override fun inheritMethodFromNonApiAncestor(template: MethodItem): MethodItem {
        val method = template as TurbineMethodItem
        val replacementMap = mapTypeVariables(method.containingClass())
        val retType = method.returnType().convertType(replacementMap)
        val mods = method.modifiers.duplicate()
        val parameters = method.parameters()

        val duplicateMethod =
            TurbineMethodItem(
                codebase = codebase,
                fileLocation = FileLocation.UNKNOWN,
                modifiers = mods,
                documentation = method.documentation.duplicate(),
                name = method.name(),
                containingClass = this,
                typeParameterList = method.typeParameterList,
                returnType = retType,
                parameterItemsFactory = { methodItem ->
                    parameters.map {
                        TurbineParameterItem.duplicate(codebase, methodItem, it, replacementMap)
                    }
                },
                annotationDefault = method.defaultValue(),
                throwsTypes = method.throwsTypes(),
            )

        duplicateMethod.inheritedFrom = method.containingClass()

        duplicateMethod.updateCopiedMethodState()

        return duplicateMethod
    }

    override fun addMethod(method: MethodItem) {
        methods.add(method as TurbineMethodItem)
    }
}
