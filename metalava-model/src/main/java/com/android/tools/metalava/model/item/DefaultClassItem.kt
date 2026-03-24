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
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.utils.extractSimpleName
import com.android.tools.metalava.reporter.FileLocation

internal class DefaultClassItem(
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
    override val typeParameterList: TypeParameterList,
    origin: ClassOrigin,
    private var superClassType: ClassTypeItem?,
    private var interfaceTypes: List<ClassTypeItem>,
    override val isFileFacade: Boolean,
    /**
     * If [classKind] is [ClassKind.TYPEALIAS], the [optionalAliasedType] must be specified.
     * Otherwise, it should be null.
     */
    optionalAliasedType: TypeItem?,
    override val isMultiFileClass: Boolean = false,
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
    ClassItem,
    SkeletonClassItem {

    private val simpleName = qualifiedName.extractSimpleName()

    private val fullName: String

    override var origin: ClassOrigin = origin
        set(value) {
            ensureNotFrozen()
            field = value
        }

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

    override fun containingPackage(): PackageItem = containingPackage

    override fun containingClass() = containingClass

    private lateinit var sealedClassSubclasses: List<ClassItem>

    override fun sealedClassDirectSubclasses(): List<ClassItem> {
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

    override fun qualifiedName() = qualifiedName

    override fun simpleName() = simpleName

    override fun fullName() = fullName

    override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = TypeItem.createClassTypeForClassItem(this)
        }
        return cachedType
    }

    override var frozen = false
        private set

    override fun freeze() {
        if (frozen) return
        frozen = true
        superClass()?.freeze()
        for (interfaceType in interfaceTypes) {
            interfaceType.resolveClass(codebase)?.freeze()
        }
    }

    private fun ensureNotFrozen() {
        if (frozen) error("Cannot modify frozen $this")
    }

    override var classKind: ClassKind = classKind
        set(value) {
            ensureNotFrozen()
            field = value
        }

    override var optionalAliasedType: TypeItem? = optionalAliasedType
        set(value) {
            ensureNotFrozen()
            field = value
        }

    override fun mutateModifiers(mutator: MutableModifierList.() -> Unit) {
        ensureNotFrozen()
        super.mutateModifiers(mutator)
    }

    override fun superClassType(): ClassTypeItem? = superClassType

    override fun setSuperClassType(superClassType: ClassTypeItem?) {
        ensureNotFrozen()
        this.superClassType = superClassType
    }

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        ensureNotFrozen()
        this.interfaceTypes = interfaceTypes
    }

    /** Cache of the results of calling [cacheAllInterfaces]. */
    private var cacheAllInterfaces: List<ClassItem>? = null

    override fun allInterfaces(): Sequence<ClassItem> {
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
            val itf = interfaceType.resolveClass(codebase)
            itf?.allInterfaces()?.forEach { add(it) }
        }
    }

    /** The mutable list of [ConstructorItem] that backs [constructors]. */
    private val mutableConstructors = mutableListOf<ConstructorItem>()

    override fun constructors(): List<ConstructorItem> = mutableConstructors

    override fun addConstructor(constructor: ConstructorItem) {
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

    override fun replaceOrAddConstructor(constructor: ConstructorItem) {
        replaceOrAddItem(constructor, mutableConstructors)
    }

    override fun createImplicitDefaultConstructor(visibility: VisibilityLevel): ConstructorItem {
        return DefaultConstructorItem.createImplicitDefaultConstructor(
            codebase = codebase,
            sourceLanguage = sourceLanguage,
            variantSelectorsFactory = variantSelectors::duplicate,
            containingClass = this,
            visibility = visibility,
        )
    }

    /** The mutable list of [MethodItem] that backs [methods]. */
    private val mutableMethods = mutableListOf<MethodItem>()

    override fun methods(): List<MethodItem> = mutableMethods

    override fun addMethod(method: MethodItem) {
        ensureNotFrozen()
        mutableMethods += method
    }

    override fun replaceOrAddMethod(method: MethodItem) {
        replaceOrAddItem(method, mutableMethods)
    }

    /** The mutable list of [FieldItem] that backs [fields]. */
    private val mutableFields = mutableListOf<FieldItem>()

    override fun addField(field: FieldItem) {
        ensureNotFrozen()
        mutableFields += field
    }

    override fun fields(): List<FieldItem> = mutableFields

    /** The mutable list of [PropertyItem] that backs [properties]. */
    private val mutableProperties = mutableListOf<PropertyItem>()

    override fun properties(): List<PropertyItem> = mutableProperties

    override fun addProperty(property: PropertyItem) {
        ensureNotFrozen()
        mutableProperties += property
    }

    override fun replaceOrAddProperty(property: PropertyItem) {
        replaceOrAddItem(property, mutableProperties)
    }

    /** The mutable list of nested [ClassItem] that backs [nestedClasses]. */
    private val mutableNestedClasses = mutableListOf<ClassItem>()

    override fun nestedClasses(): List<ClassItem> = mutableNestedClasses

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
        // First, check to see if it matches this class and if it does then return it. Only do that
        // for the first simple name in a qualified name, otherwise it would treat something like
        // java.util.Map.Map.Map.Map as if it was `java.util.Map`.
        nameClassification.findClass {
            if (isFirstSimpleName && simpleName == simpleName()) this else null
        }
            // Then check to see type parameters.
            ?: nameClassification.findTypeParameter {
                typeParameterList.find { it.name() == simpleName }
            }
            // Then, check to see if it is a field of this class.
            ?: nameClassification.findField { findField(simpleName) }
            // Then, check to see if this contains any method with the same name, if it does
            // then return a ReferencableMethodSet.
            ?: nameClassification.findCallableSet { findCallableSet(simpleName) }
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
                it.resolveClass(codebase)
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
                error(
                    "annotationClass can only be accessed on annotation classes but $qualifiedName is $classKind"
                )
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
}

/**
 * Check to see if [name] refers to a method or constructor in this [ClassItem].
 *
 * If [name] matches [ClassItem.simpleName] then it is assumed to be a constructor. In that case if
 * this [ClassItem] has any constructors then this [ClassItem] is returned to represent the set of
 * constructors, otherwise `null` is returned. [ClassItem] is used to represent the constructors
 * because that matches the specification. Constructors cannot usually be referenced by name and
 * instead the class is referenced which gives access to its constructor.
 *
 * Else, [ClassItem.methods] is searched for a [MethodItem] that matches [name]. If at least one
 * could be found then returns a [ReferencableMethodSet] to represent the set of all [MethodItem]s
 * called [name].
 *
 * Otherwise, `null` is returned.
 */
internal fun ClassItem.findCallableSet(name: String) =
    if (name == simpleName()) {
        if (constructors().isEmpty()) {
            null
        } else {
            this
        }
    } else if (methods().any { it.name() == name }) {
        ReferencableMethodSet(this, name)
    } else {
        null
    }
