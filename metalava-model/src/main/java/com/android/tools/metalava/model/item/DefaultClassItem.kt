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
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.reporter.FileLocation

open class DefaultClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val source: SourceFile?,
    final override val classKind: ClassKind,
    private val containingClass: ClassItem?,
    private val containingPackage: PackageItem,
    private val qualifiedName: String,
    final override val typeParameterList: TypeParameterList,
    final override val origin: ClassOrigin,
    private var superClassType: ClassTypeItem?,
    private var interfaceTypes: List<ClassTypeItem>,
) :
    DefaultSelectableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    ClassItem {

    private val simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)

    private val fullName: String

    init {
        // Register the class first. Leaking `this` is ok as it only uses its qualified name and
        // fileLocation, both of which have been initialized. If registration succeeded then wire
        // the class into the containing package/containing class. If it failed, because it is a
        // duplicate, then do nothing.
        if (codebase.registerClass(@Suppress("LeakingThis") this)) {
            // Only emit classes that were specified on the command line.
            emit = emit && origin == ClassOrigin.COMMAND_LINE

            // If this class is emittable then make sure its package is too.
            if (emit) {
                containingPackage.emit = true
            }

            if (containingClass == null) {
                (containingPackage as DefaultPackageItem).addTopClass(this)
                fullName = simpleName
            } else {
                (containingClass as DefaultClassItem).addNestedClass(this)
                fullName = "${containingClass.fullName()}.$simpleName"
            }
        } else {
            // The fullName needs to be initialized to something so initializing it to something
            // invalid will ensure it is not accidentally used.
            fullName = "duplicate class"
        }
    }

    /** If [source] is not set and this is a nested class then try the containing class. */
    override fun sourceFile() = source ?: containingClass?.sourceFile()

    final override fun containingPackage(): PackageItem = containingPackage

    final override fun containingClass() = containingClass

    final override fun qualifiedName() = qualifiedName

    final override fun simpleName() = simpleName

    final override fun fullName() = fullName

    final override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    final override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = createClassTypeItemForThis()
        }
        return cachedType
    }

    protected open fun createClassTypeItemForThis() =
        DefaultResolvedClassTypeItem.createForClass(this)

    final override var frozen = false
        private set

    override fun freeze() {
        if (frozen) return
        frozen = true
        superClass()?.freeze()
        for (interfaceType in interfaceTypes) {
            interfaceType.asClass()?.freeze()
        }
    }

    private fun ensureNotFrozen() {
        if (frozen) error("Cannot modify frozen $this")
    }

    final override fun mutateModifiers(mutator: MutableModifierList.() -> Unit) {
        ensureNotFrozen()
        super.mutateModifiers(mutator)
    }

    final override fun superClassType(): ClassTypeItem? = superClassType

    /** Set the super class [ClassTypeItem]. */
    fun setSuperClassType(superClassType: ClassTypeItem?) {
        ensureNotFrozen()
        this.superClassType = superClassType
    }

    final override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    final override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        ensureNotFrozen()
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

    /** Compute the value for [ClassItem.allInterfaces]. */
    private fun computeAllInterfaces() = buildList {
        // Add self as interface if applicable
        if (isInterface()) {
            add(this@DefaultClassItem)
        }

        // Add all the interfaces of super class
        superClass()?.let { superClass -> superClass.allInterfaces().forEach { add(it) } }

        // Add all the interfaces of direct interfaces
        interfaceTypes().forEach { interfaceType ->
            val itf = interfaceType.asClass()
            itf?.allInterfaces()?.forEach { add(it) }
        }
    }

    /** The mutable list of [ConstructorItem] that backs [constructors]. */
    private val mutableConstructors = mutableListOf<ConstructorItem>()

    final override fun constructors(): List<ConstructorItem> = mutableConstructors

    /** Add a constructor to this class. */
    fun addConstructor(constructor: ConstructorItem) {
        ensureNotFrozen()
        mutableConstructors += constructor

        // Keep track of whether any implicit constructors were added.
        if (constructor.isImplicitConstructor()) {
            hasImplicitDefaultConstructor = true
        }
    }

    /** Tracks whether the class has an implicit default constructor. */
    private var hasImplicitDefaultConstructor = false

    final override fun hasImplicitDefaultConstructor(): Boolean = hasImplicitDefaultConstructor

    override fun createDefaultConstructor(visibility: VisibilityLevel): ConstructorItem {
        return DefaultConstructorItem.createDefaultConstructor(
            codebase = codebase,
            itemLanguage = itemLanguage,
            variantSelectorsFactory = variantSelectors::duplicate,
            containingClass = this,
            visibility = visibility,
        )
    }

    /** The mutable list of [MethodItem] that backs [methods]. */
    private val mutableMethods = mutableListOf<MethodItem>()

    final override fun methods(): List<MethodItem> = mutableMethods

    /** Add a method to this class. */
    final override fun addMethod(method: MethodItem) {
        ensureNotFrozen()
        mutableMethods += method
    }

    /**
     * Replace an existing method with [method], if no such method exists then just add [method] to
     * the list of methods.
     */
    fun replaceOrAddMethod(method: MethodItem) {
        ensureNotFrozen()
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
        ensureNotFrozen()
        mutableFields += field
    }

    final override fun fields(): List<FieldItem> = mutableFields

    /** The mutable list of [PropertyItem] that backs [properties]. */
    private val mutableProperties = mutableListOf<PropertyItem>()

    final override fun properties(): List<PropertyItem> = mutableProperties

    /** Add a property to this class. */
    fun addProperty(property: PropertyItem) {
        ensureNotFrozen()
        mutableProperties += property
    }

    /** The mutable list of nested [ClassItem] that backs [nestedClasses]. */
    private val mutableNestedClasses = mutableListOf<ClassItem>()

    final override fun nestedClasses(): List<ClassItem> = mutableNestedClasses

    /** Add a nested class to this class. */
    private fun addNestedClass(classItem: ClassItem) {
        ensureNotFrozen()
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
