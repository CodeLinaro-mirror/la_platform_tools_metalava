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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ApiVariantSelectorsFactory
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
import com.android.tools.metalava.model.computeAllInterfaces
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.reporter.FileLocation

abstract class DefaultClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val source: SourceFile?,
    final override val classKind: ClassKind,
    private val containingClass: ClassItem?,
    private val qualifiedName: String,
    private val simpleName: String,
    private val fullName: String,
    final override val typeParameterList: TypeParameterList,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    ClassItem {

    final override fun getSourceFile() = source

    private lateinit var containingPackage: PackageItem

    fun setContainingPackage(containingPackage: PackageItem) {
        this.containingPackage = containingPackage
    }

    final override fun containingPackage(): PackageItem =
        containingClass()?.containingPackage() ?: containingPackage

    final override fun containingClass() = containingClass

    final override fun qualifiedName() = qualifiedName

    final override fun simpleName() = simpleName

    final override fun fullName() = fullName

    final override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    final override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = DefaultResolvedClassTypeItem.createForClass(this)
        }
        return cachedType
    }

    /**
     * The optional, mutable super class [ClassTypeItem].
     *
     * This could be a constructor val apart from the fact that Text needs to mutate the super class
     * type of an existing class when merging classes that are defined in multiple API surfaces.
     */
    private var superClassType: ClassTypeItem? = null

    final override fun superClassType(): ClassTypeItem? = superClassType

    /** Set the super class [ClassTypeItem]. */
    fun setSuperClassType(superClassType: ClassTypeItem?) {
        this.superClassType = superClassType
    }

    private var interfaceTypes = emptyList<ClassTypeItem>()

    final override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    final override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        this.interfaceTypes = interfaceTypes
    }

    /** Cache of the results of calling [cacheAllInterfaces]. */
    private var cacheAllInterfaces: List<ClassItem>? = null

    final override fun allInterfaces(): Sequence<ClassItem> {
        if (cacheAllInterfaces == null) {
            cacheAllInterfaces = computeAllInterfaces()
        }

        return cacheAllInterfaces!!.asSequence()
    }

    /** The mutable list of [ConstructorItem] that backs [constructors]. */
    private val mutableConstructors = mutableListOf<ConstructorItem>()

    final override fun constructors(): List<ConstructorItem> = mutableConstructors

    /** Add a constructor to this class. */
    fun addConstructor(constructor: ConstructorItem) {
        mutableConstructors += constructor
    }

    final override var stubConstructor: ConstructorItem? = null

    /**
     * Tracks whether the class has an implicit default constructor.
     *
     * TODO(b/345775012): Stop it from being public.
     */
    var hasImplicitDefaultConstructor = false

    final override fun hasImplicitDefaultConstructor(): Boolean = hasImplicitDefaultConstructor

    /** The mutable list of [MethodItem] that backs [methods]. */
    private val mutableMethods = mutableListOf<MethodItem>()

    final override fun methods(): List<MethodItem> = mutableMethods

    /** Add a method to this class. */
    override fun addMethod(method: MethodItem) {
        mutableMethods += method
    }

    /**
     * Replace an existing method with [method], if no such method exists then just add [method] to
     * the list of methods.
     */
    fun replaceOrAddMethod(method: MethodItem) {
        val iterator = mutableMethods.listIterator()
        while (iterator.hasNext()) {
            val existing = iterator.next()
            if (existing == method) {
                iterator.set(method)
                return
            }
        }
        mutableMethods += method
    }

    /** The mutable list of [FieldItem] that backs [fields]. */
    private val mutableFields = mutableListOf<FieldItem>()

    /** Add a field to this class. */
    fun addField(field: FieldItem) {
        mutableFields += field
    }

    final override fun fields(): List<FieldItem> = mutableFields

    /** The mutable list of [PropertyItem] that backs [properties]. */
    private val mutableProperties = mutableListOf<PropertyItem>()

    final override fun properties(): List<PropertyItem> = mutableProperties

    /** Add a property to this class. */
    fun addProperty(property: PropertyItem) {
        mutableProperties += property
    }

    /** The mutable list of nested [ClassItem] that backs [nestedClasses]. */
    private val mutableNestedClasses = mutableListOf<ClassItem>()

    final override fun nestedClasses(): List<ClassItem> = mutableNestedClasses

    /** Add a nested class to this class. */
    fun addNestedClass(classItem: ClassItem) {
        mutableNestedClasses.add(classItem)
    }

    /** Cache result of [getRetention]. */
    private var cacheRetention: AnnotationRetention? = null

    final override fun getRetention(): AnnotationRetention {
        cacheRetention?.let {
            return it
        }

        if (!isAnnotationType()) {
            error("getRetention() should only be called on annotation classes")
        }

        cacheRetention = ClassItem.findRetention(this)
        return cacheRetention!!
    }
}
