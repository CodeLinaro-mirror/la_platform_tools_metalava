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

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.model.utils.extractSimpleName
import com.android.tools.metalava.reporter.FileLocation

open class DefaultClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    sourceLanguage: SourceLanguage,
    targetLanguages: Set<TargetLanguage>,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    private val source: SourceFile?,
    classKind: ClassKind,
    private val containingClass: ClassItem?,
    private val containingPackage: PackageItem,
    private val qualifiedName: String,
    final override val typeParameterList: TypeParameterList,
    final override val origin: ClassOrigin,
    private var superClassType: ClassTypeItem?,
    private var interfaceTypes: List<ClassTypeItem>,
    override val isFileFacade: Boolean,
    /**
     * If [classKind] is [ClassKind.TYPEALIAS], the [optionalAliasedType] must be specified.
     * Otherwise, it should be null.
     */
    optionalAliasedType: TypeItem?,
) :
    DefaultSelectableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        sourceLanguage = sourceLanguage,
        targetLanguages = targetLanguages,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ),
    ClassItem {

    private val simpleName = qualifiedName.extractSimpleName()

    private val fullName: String

    init {
        // Register the class first. Leaking `this` is ok as it only uses its qualified name and
        // fileLocation, both of which have been initialized. If registration succeeded then wire
        // the class into the containing package/containing class. If it failed, because it is a
        // duplicate, then do nothing.
        @Suppress("LeakingThis") val classItem = this
        if (codebase.registerClass(classItem)) {
            // Only emit classes that were specified on the command line.
            emit = emit && origin == ClassOrigin.COMMAND_LINE

            // If this class is emittable then make sure its package is too.
            if (emit) {
                containingPackage.emit = true
            }

            if (containingClass == null) {
                (containingPackage as DefaultPackageItem).addTopClass(classItem)
                fullName = simpleName
            } else {
                (containingClass as DefaultClassItem).addNestedClass(classItem)
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

    private lateinit var sealedClassSubclasses: List<ClassItem>

    final override fun sealedClassDirectSubclasses(): List<ClassItem> {
        if (!isEffectivelySealed()) {
            error(
                "Computing subclasses is only available for effectively sealed classes and interfaces"
            )
        }
        if (!::sealedClassSubclasses.isInitialized) {
            sealedClassSubclasses =
                containingPackage
                    .allClasses()
                    .filter { cls ->
                        cls.superClassType()?.qualifiedName == qualifiedName ||
                            cls.interfaceTypes().any { it.qualifiedName == qualifiedName }
                    }
                    .toList()
        }
        return sealedClassSubclasses
    }

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

    final override var classKind: ClassKind = classKind
        set(value) {
            ensureNotFrozen()
            field = value
        }

    final override var optionalAliasedType: TypeItem? = optionalAliasedType
        set(value) {
            ensureNotFrozen()
            field = value
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
    }

    /**
     * If there is a version of [item] already in [mutableItems], replaces the existing version with
     * [item]. Otherwise, adds [item] to the end of [mutableItems].
     */
    private fun <I : Item> replaceOrAddItem(item: I, mutableItems: MutableList<I>) {
        ensureNotFrozen()
        val iterator = mutableItems.listIterator()
        while (iterator.hasNext()) {
            val existing = iterator.next()
            if (existing == item) {
                iterator.set(item)
                return
            }
        }
        mutableItems += item
    }

    /**
     * If there is already a constructor with the same signature as [constructor], replaces the
     * existing version with the new one. If there is not a matching constructor, just adds
     * [constructor] to the list of constructors.
     */
    fun replaceOrAddConstructor(constructor: ConstructorItem) {
        replaceOrAddItem(constructor, mutableConstructors)
    }

    override fun createDefaultConstructor(visibility: VisibilityLevel): ConstructorItem {
        return DefaultConstructorItem.createDefaultConstructor(
            codebase = codebase,
            sourceLanguage = sourceLanguage,
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
        replaceOrAddItem(method, mutableMethods)
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

    /**
     * If there is already a property with the same signature as [property], replaces the existing
     * version with the new one. If there is not a matching property, just adds [property] to the
     * list of properties.
     */
    fun replaceOrAddProperty(property: PropertyItem) {
        replaceOrAddItem(property, mutableProperties)
    }

    /** The mutable list of nested [ClassItem] that backs [nestedClasses]. */
    private val mutableNestedClasses = mutableListOf<ClassItem>()

    final override fun nestedClasses(): List<ClassItem> = mutableNestedClasses

    /** Add a nested class to this class. */
    private fun addNestedClass(classItem: ClassItem) {
        ensureNotFrozen()
        mutableNestedClasses.add(classItem)
    }

    override val containingScope: ReferencableNameScope?
        get() = containingClass() ?: sourceFile()

    override fun resolveReferencableItemBySimpleName(
        simpleName: String,
        nameClassification: NameClassification,
        isFirstSimpleName: Boolean
    ) =
        // Implements https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2
        // First, check to see if it matches this class and if it does then return it.
        if (simpleName == simpleName()) this
        else
        // Then check to see type parameters.
        nameClassification.findTypeParameter { typeParameterList.find { it.name() == simpleName } }
                // Then, check to see if it is a field of this class.
                ?: nameClassification.findField { mutableFields.find { it.name() == simpleName } }
                // Then, check to see if it matches a nested class and if it does then return that.
                ?: nameClassification.findClass {
                    mutableNestedClasses.find { it.simpleName() == simpleName }
                }
                // Then, check to see if it matches a class defined in a super class.
                ?: superClass()
                    ?.resolveReferencableItemBySimpleName(
                        simpleName,
                        nameClassification,
                        isFirstSimpleName
                    )
                // Then, check to see if it matches a class defined in a super interface.
                ?: interfaceTypes().firstNotNullOfOrNull {
                    it.asClass()
                        ?.resolveReferencableItemBySimpleName(
                            simpleName,
                            nameClassification,
                            isFirstSimpleName
                        )
                }

    /** Cache value of [annotationClass]. */
    private lateinit var cachedAnnotationClass: AnnotationClass

    override val annotationClass: AnnotationClass
        get() {
            if (classKind != ClassKind.ANNOTATION_TYPE) {
                error("annotationClass can only be accessed on annotation classes")
            }

            if (!::cachedAnnotationClass.isInitialized) {
                cachedAnnotationClass = DefaultAnnotationClass(this)
            }

            return cachedAnnotationClass
        }

    override val aliasedType: TypeItem
        get() {
            if (classKind != ClassKind.TYPEALIAS) {
                error("aliasedType can only be accessed on typealiases")
            }
            return optionalAliasedType!!
        }

    companion object {
        /** Creates a [DefaultClassItem] which has [ClassKind.TYPEALIAS]. */
        fun createTypeAlias(
            codebase: DefaultCodebase,
            fileLocation: FileLocation,
            modifiers: BaseModifierList,
            documentationFactory: ItemDocumentationFactory,
            variantSelectorsFactory: ApiVariantSelectorsFactory,
            aliasedType: TypeItem,
            qualifiedName: String,
            typeParameterList: TypeParameterList,
            containingPackage: PackageItem,
            origin: ClassOrigin,
        ): DefaultClassItem {
            return DefaultClassItem(
                codebase = codebase,
                fileLocation = fileLocation,
                // Typealiases can only be defined in Kotlin.
                sourceLanguage = SourceLanguage.KOTLIN,
                // Typealiases can only be referenced from Kotlin source.
                targetLanguages = TargetLanguageSet.KOTLIN_ONLY,
                modifiers = modifiers,
                documentationFactory = documentationFactory,
                variantSelectorsFactory = variantSelectorsFactory,
                source = null,
                classKind = ClassKind.TYPEALIAS,
                // Typealiases can only be defined at the top leve.
                containingClass = null,
                containingPackage = containingPackage,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameterList,
                origin = origin,
                // Typealiases don't have a superclass or interface types, since they are not
                // normal classes.
                superClassType = null,
                interfaceTypes = emptyList(),
                isFileFacade = false,
                optionalAliasedType = aliasedType,
            )
        }
    }
}
