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
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.item.AbstractSourceFile
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.value.OptionalValueProvider
import com.android.tools.metalava.model.value.Value
import java.util.IdentityHashMap

/** Constructs a [Codebase] by taking a snapshot of another [Codebase] that is being visited. */
class CodebaseSnapshotTaker
private constructor(referenceVisitorFactory: (DelegatedVisitor) -> ItemVisitor) :
    DefaultCodebaseAssembler(), DelegatedVisitor {

    /**
     * The [Codebase] that is under construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var snapshotCodebase: DefaultCodebase

    /**
     * The [ItemVisitor] to use in [createClassFromUnderlyingModel] to create a [ClassItem] that is
     * not emitted as part of the snapshot but is included because it is referenced from a
     * [ClassItem] that is emitted from the snapshot.
     */
    private val referenceVisitor = referenceVisitorFactory(this)

    override val itemFactory: DefaultItemFactory by
        lazy(LazyThreadSafetyMode.NONE) {
            DefaultItemFactory(
                snapshotCodebase,
                // Snapshots currently only support java.
                defaultSourceLanguage = SourceLanguage.JAVA,
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
        lazy(LazyThreadSafetyMode.NONE) { SnapshotTypeItemFactory(snapshotCodebase) }

    /** Take a snapshot of this [ModifierList] for [snapshotCodebase]. */
    private fun ModifierList.snapshot() = snapshot(snapshotCodebase)

    /**
     * Snapshots need to preserve class nesting when visiting otherwise [ClassItem.containingClass]
     * will not be initialized correctly.
     */
    override val requiresClassNesting: Boolean
        get() = false

    private lateinit var sourceFileCache: SnapshotSourceFileCache

    override fun visitCodebase(codebase: Codebase) {
        this.originalCodebase = codebase
        val newCodebase =
            DefaultCodebase(
                location = codebase.location,
                description = "snapshot of ${codebase.description}",
                preFiltered = true,
                config = codebase.config,
                trustedApi = true,
                // Supports documentation if the copied codebase does.
                supportsDocumentation = codebase.supportsDocumentation(),
                assembler = this,
                isMultiplatform = codebase.isMultiplatform,
            )

        this.snapshotCodebase = newCodebase
        this.sourceFileCache = SnapshotSourceFileCache(newCodebase)
    }

    /**
     * Construct a [PackageDocs] that contains a [PackageDoc] for [packageItem] and each of its
     * ancestor [PackageItem]s from the original [Codebase] that have not yet been included in the
     * snapshot.
     *
     * Each [PackageDoc] contains information necessary for create [PackageItem]s that are snapshots
     * of the original [PackageItem].
     *
     * It is necessary to include a [PackageDoc] for ancestor [PackageItem]s as while the
     * [PackageItem]s are visited in order from the top-most down, they are ignored by the visitor
     * if they do not contain any classes.
     */
    private fun packageDocsForPackageItem(packageItem: PackageItem): PackageDocs {
        return PackageDocs(
            buildMap {
                var pkgItem: PackageItem? = packageItem
                while (pkgItem != null) {
                    val qualifiedName = pkgItem.qualifiedName()
                    if (snapshotCodebase.findPackage(qualifiedName) != null) {
                        break
                    }

                    val packageDoc =
                        MutablePackageDoc(
                            qualifiedName = qualifiedName,
                            fileLocation = pkgItem.fileLocation,
                            modifiers = pkgItem.modifiers.snapshot(),
                            commentFactory = pkgItem.documentation::snapshot,
                            overview = pkgItem.overviewDocumentation,
                        )
                    put(qualifiedName, packageDoc)

                    pkgItem = pkgItem.containingPackage()
                }
            }
        )
    }

    /**
     * Override to throw an error.
     *
     * This should never be called when taking a snapshot as:
     * 1. This will only be called for package items that have a null [PackageDoc.commentFactory].
     * 2. Every [PackageItem] that is created in the snapshot [Codebase] must have a matching
     *    [PackageItem] in the original [Codebase].
     * 3. [packageDocsForPackageItem] will ensure that the [PackageDoc.commentFactory] for every
     *    [PackageItem] being snapshot is set to a non-null value.
     */
    override fun emptyPackageDocumentationFactory(): ItemDocumentationFactory {
        error(
            "Internal Error: PackageItems in the snapshot must always be created from PackageItems in the original codebase"
        )
    }

    /** Get the [PackageItem] corresponding to this [PackageItem] in the snapshot codebase. */
    private fun PackageItem.getSnapshotPackage(): PackageItem {
        // Check to see if the package already exists to avoid unnecessarily creating PackageDocs.
        val packageName = qualifiedName()
        snapshotCodebase.findPackage(packageName)?.let {
            return it
        }

        // Get a PackageDocs that contains a PackageDoc for the PackageItem being visited and each
        // of its ancestor PackageItems that are not already been included in this snapshot. They
        // are needed to ensure that the findOrCreatePackage(...) call below will use the correct
        // information when creating the snapshots of the PackageItem.
        val packageDocs = packageDocsForPackageItem(this)
        val newPackageItem = snapshotCodebase.findOrCreatePackage(packageName, packageDocs)
        newPackageItem.copySelectedApiVariants(this)
        return newPackageItem
    }

    /**
     * Take a snapshot of the documentation.
     *
     * If necessary revert the documentation change that accompanied a deprecation change.
     *
     * Deprecating an API requires adding an `@Deprecated` annotation and an `@deprecated` Javadoc
     * tag with text that explains why it is being deprecated and what will replace it. When the
     * deprecation change is being reverted then this will remove the `@deprecated` tag and its
     * associated text to avoid warnings when compiling and misleading information being written
     * into the Javadoc.
     */
    private fun snapshotDocumentation(
        itemToSnapshot: SelectableItem,
        documentedItem: SelectableItem,
    ): ItemDocumentationFactory {
        // The documentation does not need to be reverted if...
        if (
            // the item is not being reverted
            itemToSnapshot === documentedItem
            // or if the deprecation status has not changed
            ||
                itemToSnapshot.effectivelyDeprecated == documentedItem.effectivelyDeprecated
                // or if the item was previously deprecated
                ||
                itemToSnapshot.effectivelyDeprecated
        )
            return documentedItem.documentation::snapshot

        val documentation = documentedItem.documentation
        return { item -> documentation.snapshot(item).apply { removeDeprecatedSection() } }
    }

    /** Get the [ClassItem] corresponding to this [ClassItem] in the [snapshotCodebase]. */
    private fun ClassItem.getSnapshotClass(): DefaultClassItem =
        snapshotCodebase.resolveClass(qualifiedName()) as DefaultClassItem

    /** Copy [SelectableItem.selectedApiVariants] from [original] to this. */
    private fun <T : SelectableItem> T.copySelectedApiVariants(original: T) {
        selectedApiVariants = original.selectedApiVariants
    }

    override fun visitClass(cls: ClassItem) {
        val classToSnapshot = cls.actualItemToSnapshot

        // Get the snapshot of the containing package.
        val containingPackage = cls.containingPackage().getSnapshotPackage()

        // Get the snapshot of the containing class, if any.
        val containingClass = cls.containingClass()?.getSnapshotClass()

        // Create a TypeParameterList and SnapshotTypeItemFactory for the class.
        val (typeParameterList, classTypeItemFactory) =
            globalTypeItemFactory.from(containingClass).inScope {
                classToSnapshot.typeParameterList.snapshot(
                    "class ${classToSnapshot.qualifiedName()}"
                )
            }

        // Snapshot the super class type, if any.
        val snapshotSuperClassType =
            classToSnapshot.superClassType()?.let { superClassType ->
                classTypeItemFactory.getSuperClassType(superClassType)
            }
        val snapshotInterfaceTypes =
            classToSnapshot.interfaceTypes().map { classTypeItemFactory.getInterfaceType(it) }

        val optionalAliasedType =
            if (classToSnapshot.classKind == ClassKind.TYPEALIAS) {
                classTypeItemFactory.getGeneralType(classToSnapshot.aliasedType)
            } else {
                null
            }

        // Create the class and register it in the codebase.
        val newClass =
            itemFactory.createClassItem(
                fileLocation = classToSnapshot.fileLocation,
                sourceLanguage = classToSnapshot.sourceLanguage,
                targetLanguages = classToSnapshot.targetLanguages,
                modifiers = classToSnapshot.modifiers.snapshot(),
                documentationFactory = snapshotDocumentation(classToSnapshot, cls),
                source = sourceFileCache.snapshotSourceFile(cls.sourceFile()),
                classKind = classToSnapshot.classKind,
                containingClass = containingClass,
                containingPackage = containingPackage,
                qualifiedName = classToSnapshot.qualifiedName(),
                typeParameterList = typeParameterList,
                origin = classToSnapshot.origin,
                superClassType = snapshotSuperClassType,
                interfaceTypes = snapshotInterfaceTypes,
                optionalAliasedType = optionalAliasedType
            )
        newClass.copySelectedApiVariants(classToSnapshot)
    }

    /** Execute [body] within [SnapshotTypeItemFactoryContext]. */
    private inline fun <T> SnapshotTypeItemFactory.inScope(
        body: SnapshotTypeItemFactoryContext.() -> T
    ) = SnapshotTypeItemFactoryContext(this).body()

    override fun visitConstructor(constructor: ConstructorItem) {
        val constructorToSnapshot = constructor.actualItemToSnapshot

        val containingClass = constructor.containingClass().getSnapshotClass()

        // Create a TypeParameterList and SnapshotTypeItemFactory for the constructor.
        val (typeParameterList, constructorTypeItemFactory) =
            globalTypeItemFactory.from(containingClass).inScope {
                constructorToSnapshot.typeParameterList.snapshot(constructorToSnapshot.describe())
            }

        val newConstructor =
            // Resolve any type parameters used in the constructor's return type and parameter items
            // within the scope of the constructor's SnapshotTypeItemFactory.
            constructorTypeItemFactory.inScope {
                itemFactory.createConstructorItem(
                    fileLocation = constructorToSnapshot.fileLocation,
                    sourceLanguage = constructorToSnapshot.sourceLanguage,
                    targetLanguages = constructorToSnapshot.targetLanguages,
                    modifiers = constructorToSnapshot.modifiers.snapshot(),
                    documentationFactory =
                        snapshotDocumentation(constructorToSnapshot, constructor),
                    name = constructorToSnapshot.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = constructorToSnapshot.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        constructorToSnapshot.parameters().snapshot(containingCallable, constructor)
                    },
                    throwsTypes =
                        constructorToSnapshot.throwsTypes().map {
                            typeItemFactory.getExceptionType(it)
                        },
                    callableBodyFactory = constructorToSnapshot.body::snapshot,
                    implicitConstructor = constructorToSnapshot.isImplicitConstructor(),
                    isPrimary = constructorToSnapshot.isPrimary,
                )
            }
        newConstructor.copySelectedApiVariants(constructorToSnapshot)

        containingClass.addConstructor(newConstructor)
    }

    override fun visitMethod(method: MethodItem) {
        val methodToSnapshot = method.actualItemToSnapshot

        val containingClass = method.containingClass().getSnapshotClass()

        // Create a TypeParameterList and SnapshotTypeItemFactory for the method.
        val (typeParameterList, methodTypeItemFactory) =
            globalTypeItemFactory.from(containingClass).inScope {
                methodToSnapshot.typeParameterList.snapshot(methodToSnapshot.describe())
            }

        // Defer retrieval of the defaultValue until it is needed as it could throw an exception.
        // This makes it easier to incrementally expand the Value model without breaking existing
        // snapshot tests.
        // TODO(b/354633349): Stop deferring retrieval.
        val defaultValueProvider =
            object : OptionalValueProvider {
                override val optionalValue: Value?
                    get() = methodToSnapshot.defaultValue?.snapshot(snapshotCodebase)
            }

        val newMethod =
            // Resolve any type parameters used in the method's return type and parameter items
            // within the scope of the method's SnapshotTypeItemFactory.
            methodTypeItemFactory.inScope {
                itemFactory.createMethodItem(
                    fileLocation = methodToSnapshot.fileLocation,
                    sourceLanguage = methodToSnapshot.sourceLanguage,
                    targetLanguages = methodToSnapshot.targetLanguages,
                    modifiers = methodToSnapshot.modifiers.snapshot(),
                    documentationFactory = snapshotDocumentation(methodToSnapshot, method),
                    name = methodToSnapshot.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = methodToSnapshot.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        methodToSnapshot.parameters().snapshot(containingCallable, method)
                    },
                    throwsTypes =
                        methodToSnapshot.throwsTypes().map { typeItemFactory.getExceptionType(it) },
                    callableBodyFactory = methodToSnapshot.body::snapshot,
                    defaultValueProvider = defaultValueProvider,
                    isExtensionMethod = methodToSnapshot.isExtensionMethod(),
                )
            }
        newMethod.copySelectedApiVariants(methodToSnapshot)

        containingClass.addMethod(newMethod)
    }

    override fun visitField(field: FieldItem) {
        val fieldToSnapshot = field.actualItemToSnapshot

        // Defer retrieval of the initialValue until it is needed as it could throw an exception.
        // This makes it easier to incrementally expand the Value model without breaking existing
        // snapshot tests.
        // TODO(b/354633349): Stop deferring retrieval.
        val constantValueProvider =
            object : OptionalValueProvider {
                override val optionalValue: Value?
                    get() = fieldToSnapshot.constantValue?.snapshot(snapshotCodebase)
            }

        val containingClass = field.containingClass().getSnapshotClass()
        val newField =
            // Resolve any type parameters used in the field's type within the scope of the
            // containing class's SnapshotTypeItemFactory.
            globalTypeItemFactory.from(containingClass).inScope {
                itemFactory.createFieldItem(
                    fileLocation = fieldToSnapshot.fileLocation,
                    sourceLanguage = fieldToSnapshot.sourceLanguage,
                    targetLanguages = fieldToSnapshot.targetLanguages,
                    modifiers = fieldToSnapshot.modifiers.snapshot(),
                    documentationFactory = snapshotDocumentation(fieldToSnapshot, field),
                    name = fieldToSnapshot.name(),
                    containingClass = containingClass,
                    type = fieldToSnapshot.type().snapshot(),
                    isEnumConstant = fieldToSnapshot.isEnumConstant(),
                    constantValueProvider = constantValueProvider,
                )
            }
        newField.copySelectedApiVariants(fieldToSnapshot)

        containingClass.addField(newField)
    }

    override fun visitProperty(property: PropertyItem) {
        val propertyToSnapshot = property.actualItemToSnapshot
        val containingClass = property.containingClass().getSnapshotClass()

        // Create a TypeParameterList and SnapshotTypeItemFactory for the property.
        val (typeParameterList, propertyTypeItemFactory) =
            globalTypeItemFactory.from(containingClass).inScope {
                propertyToSnapshot.typeParameterList.snapshot(propertyToSnapshot.describe())
            }

        val newProperty =
            // Resolve any type parameters used in the property's type within the scope of the
            // containing class's SnapshotTypeItemFactory.
            propertyTypeItemFactory.inScope {
                itemFactory.createPropertyItem(
                    fileLocation = propertyToSnapshot.fileLocation,
                    sourceLanguage = propertyToSnapshot.sourceLanguage,
                    modifiers = propertyToSnapshot.modifiers.snapshot(),
                    documentationFactory = snapshotDocumentation(propertyToSnapshot, property),
                    name = propertyToSnapshot.name(),
                    containingClass = containingClass,
                    type = propertyToSnapshot.type().snapshot(),
                    getter = property.getter,
                    setter = property.setter,
                    constructorParameter = property.constructorParameter,
                    backingField = property.backingField,
                    receiver = property.receiver?.snapshot(),
                    typeParameterList = typeParameterList
                )
            }
        newProperty.copySelectedApiVariants(propertyToSnapshot)

        containingClass.addProperty(newProperty)
    }

    override fun createPackageFromUnderlyingModel(qualifiedName: String): PackageItem? {
        val originalPackage = originalCodebase.resolvePackage(qualifiedName) ?: return null
        return originalPackage.getSnapshotPackage()
    }

    /** Take a snapshot of [qualifiedName]. */
    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        // Resolve the class in the original codebase, if possible.
        val originalClass = originalCodebase.resolveClass(qualifiedName) ?: return null

        // Take a snapshot of a class that is referenced from, but not defined within, the snapshot.
        originalClass.accept(referenceVisitor)

        // Find the newly added class, if no class was added then it is not part of this snapshot
        // so return `null`.
        val classItem = snapshotCodebase.findClass(originalClass.qualifiedName()) ?: return null

        // Any class that is created only when resolving references is by definition not part of the
        // codebase and so will not be emitted.
        classItem.emit = false

        return classItem
    }

    companion object {
        /**
         * Take a snapshot of [codebase].
         *
         * @param definitionVisitorFactory a factory for creating an [ItemVisitor] that delegates to
         *   a [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase]
         *   will be defined within and emitted from the snapshot.
         * @param referenceVisitorFactory a factory for creating an [ItemVisitor] that delegates to
         *   a [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase]
         *   will be referenced from within but not emitted from the snapshot.
         */
        fun takeSnapshot(
            codebase: Codebase,
            definitionVisitorFactory: (DelegatedVisitor) -> ItemVisitor,
            referenceVisitorFactory: (DelegatedVisitor) -> ItemVisitor,
        ): Codebase {
            // Create a snapshot taker that will construct the snapshot. Pass in the
            // referenceVisitorFactory so it can create the reference visitor for use in creating
            // Items that are referenced from the snapshot.
            val taker = CodebaseSnapshotTaker(referenceVisitorFactory)

            // Wrap it in a visitor that will determine which Items are defined in the snapshot and
            // then apply that visitor to the input codebase.
            val definitionVisitor = definitionVisitorFactory(taker)
            codebase.accept(definitionVisitor)

            // Return the constructed snapshot.
            return taker.snapshotCodebase
        }
    }

    /** Encapsulates state and methods needed to take a snapshot of [TypeItem]s. */
    internal inner class SnapshotTypeItemFactoryContext(
        val typeItemFactory: SnapshotTypeItemFactory
    ) {
        /**
         * Create a snapshot of this [TypeParameterList] and an associated
         * [SnapshotTypeItemFactory].
         *
         * @param description the description to use when failing to resolve a type parameter by
         *   name.
         */
        internal fun TypeParameterList.snapshot(description: String) =
            if (this == TypeParameterList.NONE) TypeParameterListAndFactory(this, typeItemFactory)
            else
                DefaultTypeParameterList.createTypeParameterItemsAndFactory(
                    typeItemFactory,
                    description,
                    this,
                    { typeParameterItem ->
                        DefaultTypeParameterItem(
                            classResolver = snapshotCodebase,
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

        /** General [TypeItem] specific snapshot. */
        internal fun TypeItem.snapshot() = typeItemFactory.getGeneralType(this)

        /** [ClassTypeItem] specific snapshot. */
        internal fun ClassTypeItem.snapshot() =
            typeItemFactory.getGeneralType(this) as ClassTypeItem

        /** Create a snapshot of this list of [ParameterItem]s. */
        internal fun List<ParameterItem>.snapshot(
            containingCallable: CallableItem,
            currentCallable: CallableItem
        ): List<ParameterItem> {
            return map { parameterItem ->
                // Retrieve the public name immediately to remove any dependencies on this in the
                // lambda passed to publicNameProvider.
                val publicName = parameterItem.publicName()

                // The parameter being snapshot may be from a previously released API, which may not
                // track parameter names and so may have to auto-generate them. This code tries to
                // avoid using the auto-generated names if possible. If the `publicName()` of the
                // parameter being snapshot is not `null` then get its `name()` as that will either
                // be set to the public name or another developer supplied name. Either way it will
                // not be auto-generated. However, if its `publicName()` is `null` then its `name()`
                // will be auto-generated so try and avoid that is possible. Instead, use the name
                // of the corresponding parameter from `currentCallable` as that is more likely to
                // have a developer supplied name, although it will be the same as `parameterItem`
                // if `currentCallable` is not being reverted.
                val name =
                    if (publicName != null) parameterItem.name()
                    else {
                        val namedParameter =
                            currentCallable.parameters()[parameterItem.parameterIndex]
                        namedParameter.name()
                    }

                itemFactory.createParameterItem(
                    fileLocation = parameterItem.fileLocation,
                    sourceLanguage = parameterItem.sourceLanguage,
                    modifiers = parameterItem.modifiers.snapshot(),
                    name = name,
                    publicName = publicName,
                    containingCallable = containingCallable,
                    parameterIndex = parameterItem.parameterIndex,
                    type = parameterItem.type().snapshot(),
                    hasDefaultValue = parameterItem.hasDefaultValue(),
                )
            }
        }
    }
}

/**
 * Get the actual item to snapshot, this takes into account whether the item has been reverted.
 *
 * The [Showability.revertItem] is only set to a non-null value if changes to this [SelectableItem]
 * have been reverted AND this [SelectableItem] existed in the previously released API.
 *
 * This casts the [Showability.revertItem] to the same type as this is called upon. That is safe as,
 * if set to a non-null value the [Showability.revertItem] will always point to a [SelectableItem]
 * of the same type.
 */
private val <reified T : SelectableItem> T.actualItemToSnapshot: T
    inline get() = (showability.revertItem ?: this) as T

/**
 * Creates [SourceFile] snapshots on demand for a [SourceFile] and caches the result for reuse.
 *
 * @param targetCodebase the [DefaultCodebase] of which any created [SourceFile]s are part.
 */
internal class SnapshotSourceFileCache(
    private val targetCodebase: DefaultCodebase,
) {
    /** Map from original [SourceFile] to the snapshot [SourceFile]. */
    private val snapshotSourceFiles = IdentityHashMap<SourceFile, SourceFile>()

    /** Get the snapshot [SourceFile] for a [SourceFile]. */
    internal fun snapshotSourceFile(sourceFile: SourceFile?): SourceFile? {
        sourceFile ?: return null
        return snapshotSourceFiles.computeIfAbsent(sourceFile) { originalSourceFile ->
            var pkgName = originalSourceFile.containingPackage.qualifiedName()
            SourceFileSnapshot(
                targetCodebase,
                targetCodebase.resolvePackage(pkgName)!!,
                originalSourceFile,
            )
        }
    }
}

/**
 * A snapshot of a [SourceFile].
 *
 * This delegates a number of methods to the [originalSourceFile].
 */
internal class SourceFileSnapshot(
    override val codebase: Codebase,
    override val containingPackage: PackageItem,
    private val originalSourceFile: SourceFile
) : AbstractSourceFile() {

    override fun classes() =
        originalSourceFile.classes().mapNotNull { codebase.resolveClass(it.qualifiedName()) }

    /** Delegate to [originalSourceFile] as they are not changed by snapshotting. */
    override fun getHeaderComments(): String? = originalSourceFile.getHeaderComments()

    /**
     * Delegate to [originalSourceFile] as while they could contain references to classes which are
     * not part of the snapshot they will be filtered when this is called.
     */
    override fun getImports() = originalSourceFile.getImports()

    /**
     * Delegate to [originalSourceFile] as while they could contain references to classes which are
     * not part of the snapshot they will be filtered by [predicate] when this is called.
     */
    override fun getImports(predicate: FilterPredicate) = originalSourceFile.getImports(predicate)

    /**
     * Delegate to [originalSourceFile] as while they could contain references to classes which are
     * not part of the snapshot they will be ignored as they will not appear in [codebase].
     */
    override fun allJavaImports() = originalSourceFile.allJavaImports()
}
