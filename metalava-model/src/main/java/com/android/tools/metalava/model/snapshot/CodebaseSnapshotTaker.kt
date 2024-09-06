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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultConstructorItem
import com.android.tools.metalava.model.item.DefaultFieldItem
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultMethodItem
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultParameterItem
import com.android.tools.metalava.model.item.DefaultPropertyItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs

/** Stack of [SnapshotTypeItemFactory] */
internal typealias TypeItemFactoryStack = ArrayList<SnapshotTypeItemFactory>

/** Push new [SnapshotTypeItemFactory] onto the top of the stack. */
internal fun TypeItemFactoryStack.push(factory: SnapshotTypeItemFactory) {
    add(factory)
}

/** Pop [SnapshotTypeItemFactory] from the top of the stack. */
internal fun TypeItemFactoryStack.pop() {
    removeLast()
}

/** Constructs a [Codebase] by taking a snapshot of another [Codebase] that is being visited. */
class CodebaseSnapshotTaker private constructor() : DelegatedVisitor, CodebaseAssembler {

    /**
     * The [Codebase] that is under construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var codebase: DefaultCodebase

    override val itemFactory: DefaultItemFactory by
        lazy(LazyThreadSafetyMode.NONE) {
            DefaultItemFactory(
                codebase,
                // Snapshots currently only support java.
                defaultItemLanguage = ItemLanguage.JAVA,
                // Snapshots have already been separated by API surface variants, so they can use
                // the same immutable ApiVariantSelectors.
                ApiVariantSelectors.IMMUTABLE_FACTORY,
            )
        }

    /**
     * The original [Codebase] that is being snapshotted construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var originalCodebase: Codebase

    private val globalTypeItemFactory by
        lazy(LazyThreadSafetyMode.NONE) { SnapshotTypeItemFactory(codebase) }

    /**
     * Stack of [SnapshotTypeItemFactory] that contain information about the [TypeParameterItem]s
     * that are in scope and can resolve a type variable reference to the parameter.
     */
    private val typeItemFactoryStack = TypeItemFactoryStack()

    /** Get the current [SnapshotTypeItemFactory], i.e. the closest enclosing one. */
    private val typeItemFactory
        get() = typeItemFactoryStack.last()

    /**
     * The current [PackageItem], set in [visitPackage], cleared in [afterVisitPackage], relies on
     * the [PackageItem]s being visited as a flat list, not a package hierarchy.
     */
    private var currentPackage: DefaultPackageItem? = null

    /**
     * The current [ClassItem], that forms a stack through the [ClassItem.containingClass].
     *
     * Set (pushed on the stack) in [visitClass]. Reset (popped off the stack) in [afterVisitClass].
     */
    private var currentClass: DefaultClassItem? = null

    /** Take a snapshot of this [ModifierList] for [codebase]. */
    private fun ModifierList.snapshot() = (this as DefaultModifierList).snapshot(codebase)

    /** General [TypeItem] specific snapshot. */
    private fun TypeItem.snapshot() = typeItemFactory.getGeneralType(this)

    /** [ClassTypeItem] specific snapshot. */
    private fun ClassTypeItem.snapshot() = typeItemFactory.getGeneralType(this) as ClassTypeItem

    override fun visitCodebase(codebase: Codebase) {
        this.originalCodebase = codebase
        val newCodebase =
            DefaultCodebase(
                location = codebase.location,
                description = "snapshot of ${codebase.description}",
                preFiltered = true,
                annotationManager = codebase.annotationManager,
                trustedApi = true,
                // Supports documentation if the copied codebase does.
                supportsDocumentation = codebase.supportsDocumentation(),
                assemblerFactory = { this },
            )

        this.codebase = newCodebase
        typeItemFactoryStack.push(globalTypeItemFactory)
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        typeItemFactoryStack.pop()
    }

    /**
     * Construct a [PackageDocs] that contains a [PackageDoc] that in turn contains information
     * extracted from [packageItem] that can be used to create a new [PackageItem] that is a
     * snapshot of [packageItem].
     */
    private fun packageDocsForPackageItem(packageItem: PackageItem) =
        MutablePackageDoc(
                qualifiedName = packageItem.qualifiedName(),
                fileLocation = packageItem.fileLocation,
                modifiers = packageItem.modifiers.snapshot(),
                commentFactory = packageItem.documentation::snapshot,
                overview = packageItem.overviewDocumentation,
            )
            .let { PackageDocs(mapOf(it.qualifiedName to it)) }

    override fun visitPackage(pkg: PackageItem) {
        // Get a PackageDocs that contains a PackageDoc that contains information extracted from the
        // PackageItem being visited. This is needed to ensure that the findOrCreatePackage(...)
        // call below will use the correct information when creating the package. As only a single
        // PackageDoc is provided for this package it means that if findOrCreatePackage(...) had to
        // created a containing package that package would not have a PackageDocs and might be
        // incorrect. However, that should not be a problem as the packages are visited in order
        // such that a containing package is visited before any contained packages.
        val packageDocs = packageDocsForPackageItem(pkg)
        val packageName = pkg.qualifiedName()
        val newPackage = codebase.findOrCreatePackage(packageName, packageDocs)
        currentPackage = newPackage
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        currentPackage = null
    }

    /**
     * Create a snapshot of this [TypeParameterList] and an associated [SnapshotTypeItemFactory].
     *
     * @param description the description to use when failing to resolve a type parameter by name.
     */
    private fun TypeParameterList.snapshot(description: String) =
        if (this == TypeParameterList.NONE) TypeParameterListAndFactory(this, typeItemFactory)
        else
            DefaultTypeParameterList.createTypeParameterItemsAndFactory(
                typeItemFactory,
                description,
                this,
                { typeParameterItem ->
                    DefaultTypeParameterItem(
                        codebase = codebase,
                        itemLanguage = typeParameterItem.itemLanguage,
                        modifiers = typeParameterItem.modifiers.snapshot(),
                        name = typeParameterItem.name(),
                        isReified = typeParameterItem.isReified()
                    )
                },
                // Create, set and return the [BoundsTypeItem] list.
                { typeItemFactory, typeParameterItem ->
                    typeParameterItem.typeBounds().map { typeItemFactory.getBoundsType(it) }
                },
            )

    override fun visitClass(cls: ClassItem) {
        // Create a TypeParameterList and SnapshotTypeItemFactory for the class.
        val (typeParameterList, classTypeItemFactory) =
            cls.typeParameterList.snapshot("class ${cls.qualifiedName()}")

        // Push on the stack before resolving any types just in case they refer to a type parameter.
        typeItemFactoryStack.push(classTypeItemFactory)

        // Snapshot the super class type, if any.
        val snapshotSuperClassType =
            cls.superClassType()?.let { superClassType ->
                typeItemFactory.getSuperClassType(superClassType)
            }
        val snapshotInterfaceTypes =
            cls.interfaceTypes().map { typeItemFactory.getInterfaceType(it) }

        val containingClass = currentClass
        val containingPackage = currentPackage!!
        val newClass =
            DefaultClassItem(
                codebase = codebase,
                fileLocation = cls.fileLocation,
                itemLanguage = cls.itemLanguage,
                modifiers = cls.modifiers.snapshot(),
                documentationFactory = cls.documentation::snapshot,
                variantSelectorsFactory = cls.variantSelectors::duplicate,
                source = null,
                classKind = cls.classKind,
                containingClass = containingClass,
                containingPackage = containingPackage,
                qualifiedName = cls.qualifiedName(),
                simpleName = cls.simpleName(),
                fullName = cls.fullName(),
                typeParameterList = typeParameterList,
                isFromClassPath = cls.isFromClassPath(),
                superClassType = snapshotSuperClassType,
                interfaceTypes = snapshotInterfaceTypes,
            )

        currentClass = newClass
    }

    override fun afterVisitClass(cls: ClassItem) {
        currentClass = currentClass?.containingClass() as? DefaultClassItem
        typeItemFactoryStack.pop()
    }

    /** Push this [SnapshotTypeItemFactory] in scope before executing [body] and pop afterwards. */
    private inline fun SnapshotTypeItemFactory.inScope(body: () -> Unit) {
        typeItemFactoryStack.push(this)
        body()
        typeItemFactoryStack.pop()
    }

    /** Return a factory that will create a snapshot of this list of [ParameterItem]s. */
    private fun List<ParameterItem>.snapshot(containingCallable: CallableItem) =
        map { parameterItem ->
            // Retrieve the public name immediately to remove any dependencies on this in the
            // lambda passed to publicNameProvider.
            val publicName = parameterItem.publicName()
            DefaultParameterItem(
                codebase = codebase,
                fileLocation = parameterItem.fileLocation,
                itemLanguage = parameterItem.itemLanguage,
                modifiers = parameterItem.modifiers.snapshot(),
                name = parameterItem.name(),
                publicNameProvider = { publicName },
                containingCallable = containingCallable,
                parameterIndex = parameterItem.parameterIndex,
                type = parameterItem.type().snapshot(),
                defaultValueFactory = parameterItem.defaultValue::snapshot,
            )
        }

    override fun visitConstructor(constructor: ConstructorItem) {
        // Create a TypeParameterList and SnapshotTypeItemFactory for the constructor.
        val (typeParameterList, constructorTypeItemFactory) =
            constructor.typeParameterList.snapshot(constructor.describe())

        // Resolve any type parameters used in the constructor's parameter items within the scope of
        // the constructor's SnapshotTypeItemFactory.
        constructorTypeItemFactory.inScope {
            val containingClass = currentClass!!
            val newConstructor =
                DefaultConstructorItem(
                    codebase = codebase,
                    fileLocation = constructor.fileLocation,
                    itemLanguage = constructor.itemLanguage,
                    modifiers = constructor.modifiers.snapshot(),
                    documentationFactory = constructor.documentation::snapshot,
                    variantSelectorsFactory = constructor.variantSelectors::duplicate,
                    name = constructor.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = constructor.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        constructor.parameters().snapshot(containingCallable)
                    },
                    throwsTypes =
                        constructor.throwsTypes().map { typeItemFactory.getExceptionType(it) },
                    callableBodyFactory = constructor.body::snapshot,
                    implicitConstructor = constructor.isImplicitConstructor(),
                )

            containingClass.addConstructor(newConstructor)
        }
    }

    override fun visitMethod(method: MethodItem) {
        // Create a TypeParameterList and SnapshotTypeItemFactory for the method.
        val (typeParameterList, methodTypeItemFactory) =
            method.typeParameterList.snapshot(method.describe())

        // Resolve any type parameters used in the method's parameter items within the scope of
        // the method's SnapshotTypeItemFactory.
        methodTypeItemFactory.inScope {
            val containingClass = currentClass!!
            val newMethod =
                DefaultMethodItem(
                    codebase = codebase,
                    fileLocation = method.fileLocation,
                    itemLanguage = method.itemLanguage,
                    modifiers = method.modifiers.snapshot(),
                    documentationFactory = method.documentation::snapshot,
                    variantSelectorsFactory = method.variantSelectors::duplicate,
                    name = method.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = method.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        method.parameters().snapshot(containingCallable)
                    },
                    throwsTypes = method.throwsTypes().map { typeItemFactory.getExceptionType(it) },
                    callableBodyFactory = method.body::snapshot,
                    annotationDefault = method.defaultValue(),
                )

            containingClass.addMethod(newMethod)
        }
    }

    override fun visitField(field: FieldItem) {
        val containingClass = currentClass!!
        val newField =
            DefaultFieldItem(
                codebase = codebase,
                fileLocation = field.fileLocation,
                itemLanguage = field.itemLanguage,
                modifiers = field.modifiers.snapshot(),
                documentationFactory = field.documentation::snapshot,
                variantSelectorsFactory = field.variantSelectors::duplicate,
                name = field.name(),
                containingClass = containingClass,
                type = field.type().snapshot(),
                isEnumConstant = field.isEnumConstant(),
                fieldValue = field.fieldValue?.snapshot(),
            )

        containingClass.addField(newField)
    }

    override fun visitProperty(property: PropertyItem) {
        val containingClass = currentClass!!
        val newProperty =
            DefaultPropertyItem(
                codebase = codebase,
                fileLocation = property.fileLocation,
                itemLanguage = property.itemLanguage,
                modifiers = property.modifiers.snapshot(),
                documentationFactory = property.documentation::snapshot,
                variantSelectorsFactory = property.variantSelectors::duplicate,
                name = property.name(),
                containingClass = containingClass,
                type = property.type().snapshot(),
            )

        containingClass.addProperty(newProperty)
    }

    /**
     * Take a snapshot of [qualifiedName].
     *
     * TODO(b/353737744): Handle resolving nested classes.
     */
    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        // Resolve the class in the original codebase, if possible.
        val originalClass = originalCodebase.resolveClass(qualifiedName) ?: return null

        // Take a snapshot of the class, that should add a new class to the snapshot codebase.
        val visitor = NonFilteringDelegatingVisitor(this)
        val originalPackage = originalClass.containingPackage()

        // Set up the state for taking a snapshot of a class.
        typeItemFactoryStack.push(globalTypeItemFactory)
        visitPackage(originalPackage)
        originalClass.accept(visitor)
        afterVisitPackage(originalPackage)
        typeItemFactoryStack.pop()

        // Find the newly added class.
        return codebase.findClass(originalClass.qualifiedName())!!
    }

    companion object {
        /** Take a snapshot of [codebase]. */
        fun takeSnapshot(codebase: Codebase): Codebase {
            // Create a snapshot taker that will construct the snapshot.
            val taker = CodebaseSnapshotTaker()

            // Wrap it in a visitor and visit the codebase.
            val visitor = NonFilteringDelegatingVisitor(taker)
            codebase.accept(visitor)

            // Return the constructed snapshot.
            return taker.codebase
        }
    }
}
