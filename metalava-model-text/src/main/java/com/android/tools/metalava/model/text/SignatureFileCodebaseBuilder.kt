/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.WellKnownTypes
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.FileLocationSource
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.item.SealedClassImplicitPermitTypesUpdater
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.type.TypeString
import com.android.tools.metalava.model.utils.extractOptionalQualifierName
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import java.nio.file.Path

/**
 * Synthetic annotation used in signature files alongside `@FlaggedApi` to mark an API item whose
 * removal is gated by a flag.
 *
 * Kept without any package to make it clear that it is a synthetic annotation.
 */
internal const val FOR_REMOVAL_ANNOTATION = "ForRemoval"

/**
 * [SignatureFileConsumer] implementation that populates a [DefaultCodebase] from one or more
 * signature files.
 */
internal class SignatureFileCodebaseBuilder(
    /** Populates the [Codebase] from the parsed signature file. */
    private val assembler: TextCodebaseAssembler,

    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    override val defaultTargetLanguageSet: Set<TargetLanguage>,

    /** Merges class re-definitions across signature files. */
    private val classMerger: ClassMerger,

    /** Consumer of [TextTypeParser.Stats] after all signature files have been parsed. */
    private val apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
) : SignatureFileConsumer {
    /** The [DefaultCodebase] being populated. */
    private val codebase: DefaultCodebase = assembler.codebase

    /** Creates [Item] instances for [codebase]. */
    private val itemFactory = assembler.itemFactory

    /** Report recoverable errors encountered while parsing types. */
    private val typeItemParserErrorReporter =
        object : TypeItemParserErrorReporter {
            override fun report(issue: Issues.Issue, message: String, charOffset: Int) {
                reportIssue(issue, message, fileLocation(charOffset))
            }
        }

    /** The [ValueParser] to use for creating [Value]s from a signature file. */
    private val valueParser: ValueParser =
        ValueParser(
            codebase,
            TypeItemParser.forValueParser(codebase, typeItemParserErrorReporter),
        )

    /** Provides support for parsing and caching [TypeItem]s. Initialized in [startFile]. */
    private lateinit var typeParser: TextTypeParser

    /** Provides support for creating [TypeItem]s for specific uses. Initialized in [startFile]. */
    private lateinit var globalTypeItemFactory: TextTypeItemFactory

    /** The [Path] of the signature file currently being parsed. */
    private lateinit var currentPath: Path

    /** The [LineMap] of the signature file currently being parsed. */
    private lateinit var currentLineMap: LineMap

    /**
     * The [FileLocationSource] shared by all [Item]s created while parsing the current signature
     * file.
     */
    private var currentFileLocationSource: FileLocationSource = FileLocationSource.NONE

    /** The [ApiVariant] of the signature file currently being parsed. */
    private lateinit var currentApiVariant: ApiVariant

    /**
     * The [ClassOrigin] to assign to classes and type aliases defined in the signature file
     * currently being parsed.
     */
    lateinit var currentClassOrigin: ClassOrigin
        private set

    /**
     * Whether types in the current file should be interpreted to be in Kotlin format (e.g. `?`
     * suffix means nullable, `!` suffix means unknown, and absence of a suffix means not nullable).
     */
    private var kotlinStyleNulls: Boolean = false

    /** The [PackageItem] of the `package` block currently being parsed. */
    private lateinit var currentPackage: PackageItem

    /** The [SkeletonClassItem] of the class currently being parsed. */
    private lateinit var currentClass: SkeletonClassItem

    /**
     * The [ApiVariant] of [currentClass] to use as the default for its members.
     *
     * Usually [currentApiVariant], except when [currentClass] was moved to the
     * [ApiVariantType.REMOVED] variant due to an unflagged or finalized `@ForRemoval` annotation,
     * in which case its members also default to [ApiVariantType.REMOVED].
     */
    private lateinit var currentClassApiVariant: ApiVariant

    /** The [TextTypeItemFactory] for [currentClass]. */
    private lateinit var currentClassTypeItemFactory: TextTypeItemFactory

    /**
     * Whether [currentClass] is an existing class from a previously parsed signature file that
     * already had constructors when [startClass] was called.
     */
    private var hasExistingConstructors: Boolean = false

    /**
     * Whether [currentClass] is an existing class from a previously parsed signature file that
     * already had fields when [startClass] was called.
     */
    private var hasExistingFields: Boolean = false

    /**
     * Whether [currentClass] is an existing class from a previously parsed signature file that
     * already had methods when [startClass] was called.
     */
    private var hasExistingMethods: Boolean = false

    /**
     * Whether [currentClass] is an existing class from a previously parsed signature file that
     * already had properties when [startClass] was called.
     */
    private var hasExistingProperties: Boolean = false

    /** The [Path] of the previously parsed signature file, if any. */
    private var previousPath: Path? = null

    /** The [kotlinStyleNulls] setting of the previously parsed signature file, if any. */
    private var previousKotlinStyleNulls: Boolean? = null

    /**
     * Whether the class, typealias, or member currently being parsed had a `@ForRemoval` annotation
     * in its modifiers, to be recorded on [SelectedApi] when the item is created.
     *
     * Stored separately from the item's modifiers so that the synthetic `@ForRemoval` annotation is
     * never added to the item's modifiers (avoiding having to remove it later or risk merging it
     * onto an existing class in [ClassMerger]). Reset in [beforeClass] and [beforeMember] before
     * parsing each declaration's modifiers.
     */
    private var currentItemHasRemovedAnnotation: Boolean = false

    /** Get the [FileLocation] at the 0-based [charOffset] in the current file. */
    private fun fileLocation(charOffset: Int): FileLocation =
        currentLineMap.fileLocation(currentPath, charOffset)

    /** Get the [FileLocation] at [sourcePosition] in the current file. */
    private fun fileLocation(sourcePosition: SourcePosition): FileLocation =
        fileLocation(sourcePosition.offset)

    /**
     * Report a recoverable issue encountered while parsing at [location].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    private fun reportIssue(issue: Issues.Issue, message: String, location: FileLocation) {
        codebase.reporter.report(issue, null, message, location)
    }

    /**
     * Update [SelectableItem.selectedApi] for [item], returning the [ApiVariant] that was added to
     * [item].
     *
     * If [item] was already defined in a different API surface, this will not add the new surface.
     */
    private fun updateSelectedApi(item: SelectableItem): ApiVariant {
        // If the item's modifiers included the @ForRemoval marker annotation, record that on its
        // SelectedApi.
        if (currentItemHasRemovedAnnotation) {
            item.selectedApi.markHasRemovedAnnotation()
        }

        // Members inherit the variant of their enclosing class (which may have been moved to
        // REMOVED by @ForRemoval), while top-level and nested classes/typealiases start from the
        // signature file's variant.
        val defaultVariant = if (item is MemberItem) currentClassApiVariant else currentApiVariant

        // Place the item in the REMOVED variant of the surface if its removal is unconditional or
        // finalized; otherwise keep it in defaultVariant (which may itself be REMOVED if its
        // enclosing class was moved to REMOVED).
        val variant =
            if (currentItemHasRemovedAnnotation && shouldMoveToRemovedVariant(item)) {
                defaultVariant.surface.variantFor(ApiVariantType.REMOVED)
            } else {
                defaultVariant
            }
        item.selectedApi.addItemApiVariant(variant)
        return variant
    }

    /**
     * Returns whether an [item] marked with `@ForRemoval` should be placed in the
     * [ApiVariantType.REMOVED] variant rather than its default variant.
     *
     * An item marked with `@ForRemoval` is moved to [ApiVariantType.REMOVED] if it has no
     * `@FlaggedApi` annotation or if its `@FlaggedApi` annotation has [ApiFlagAction.FINALIZE].
     */
    private fun shouldMoveToRemovedVariant(item: SelectableItem): Boolean {
        // Check if the item has a @FlaggedApi annotation (the only annotation with a non-null
        // apiFlag).
        val flaggedApiAnnotation = item.modifiers.annotations().firstOrNull { it.apiFlag != null }
        val action = flaggedApiAnnotation?.apiFlag?.action

        // Move the item to REMOVED if the removal is unflagged (action == null) or if the flag
        // gating the removal has been finalized. If the flag is kept or reverted, the item stays in
        // its default variant.
        return action == null || action == ApiFlagAction.FINALIZE
    }

    /**
     * Called before parsing each [signatureFile] at [path] with [lineMap] and [format].
     *
     * Validates that [KOTLIN_STYLE_NULLS] is consistent across all parsed files and initializes
     * [typeParser] and [globalTypeItemFactory] on the first file.
     */
    override fun startFile(
        signatureFile: SignatureFile,
        path: Path,
        lineMap: LineMap,
        format: FileFormat,
    ) {
        currentPath = path
        currentLineMap = lineMap
        currentFileLocationSource = FileLocationSource.fromLineMap(path, lineMap)
        currentApiVariant = signatureFile.apiVariantFor(codebase.config.apiSurfaces)
        currentClassOrigin = signatureFile.classOrigin

        // Disallow a mixture of kotlinStyleNulls settings.
        val kotlinStyleNullsForThisFile = format[KOTLIN_STYLE_NULLS]
        if (
            previousKotlinStyleNulls != null &&
                previousKotlinStyleNulls != kotlinStyleNullsForThisFile
        ) {
            codebase.reporter.report(
                Issues.SIGNATURE_FILE_ERROR,
                null,
                "Preceding file $previousPath has different setting of kotlin-style-nulls which may cause issues",
                FileLocation.createLocation(path, 1),
            )
        }
        previousPath = path
        previousKotlinStyleNulls = kotlinStyleNullsForThisFile
        kotlinStyleNulls = kotlinStyleNullsForThisFile

        if (!::typeParser.isInitialized) {
            typeParser =
                TextTypeParser(codebase, kotlinStyleNullsForThisFile, typeItemParserErrorReporter)
            globalTypeItemFactory = TextTypeItemFactory(assembler, typeParser)
        }
    }

    /**
     * Completes processing after all signature files have been parsed, performing any deferred
     * class merges, reporting type parser statistics, and updating implicit permit types on sealed
     * classes.
     */
    override fun finish() {
        classMerger.performAnyDeferredMerges()

        apiStatsConsumer(typeParser.stats())

        // Update implicit permit types in any sealed class that does not have one provided.
        SealedClassImplicitPermitTypesUpdater.updateImplicitPermitTypes(codebase)
    }

    override fun parseModifierAnnotations(
        tokenStream: TokenStream,
        apiText: String,
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList {
        val annotations = getAnnotations(tokenStream, apiText)
        val modifiers = createMutableModifiers(visibilityLevel, annotations)
        // @Deprecated is also treated as a "modifier"
        if (annotations.any { it.qualifiedName == JAVA_LANG_DEPRECATED }) {
            modifiers.setDeprecated(true)
        }
        return modifiers
    }

    /**
     * Collects all the sequential annotations from [tokenStream], returning them as a (possibly
     * empty) list.
     */
    private fun getAnnotations(
        tokenStream: TokenStream,
        apiText: String,
    ): List<AnnotationItem> = buildList {
        while (tokenStream.match(SharedTokenType.AT)) {
            // @ForRemoval is a synthetic marker annotation used to record removed-annotation state
            // on SelectedApi rather than adding an annotation to the item's modifiers.
            if (tokenStream.peek().text(apiText) == FOR_REMOVAL_ANNOTATION) {
                tokenStream.consume()
                currentItemHasRemovedAnnotation = true
                continue
            }

            // Parse the annotation from the tokenStream, unshortening the class name if necessary,
            // and add it to the list if it is recognized.
            valueParser.parseAnnotationItem(tokenStream, apiText, unshorten = true)?.let {
                annotationItem ->
                add(annotationItem)
            }
        }
    }

    override fun startPackage(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
    ) {
        // Note: currentPackage.updateSelectedApi() is not called here because packages do not
        // belong to an API surface in their own right; their API variants are populated via
        // propagation from their contained classes and members.
        currentPackage = findOrCreatePackage(sourcePosition, name, modifiers)
    }

    /**
     * Find an existing package called [name] or create a new one.
     *
     * If an existing package exists then this makes sure that its annotations match [modifiers].
     */
    private fun findOrCreatePackage(
        sourcePosition: SourcePosition,
        name: String,
        modifiers: MutableModifierList,
    ): PackageItem {
        val annotations = modifiers.annotations()
        // Check to see if the package already exists, if it does then return it.
        codebase.findPackage(name)?.let { existing ->
            // If the same package showed up multiple times, make sure they have the same modifiers.
            // (Packages can't have public/private/etc., but they can have annotations, which are
            // part of ModifierList.)
            val existingAnnotations = existing.modifiers.annotations()
            if (annotations != existingAnnotations) {
                throw ApiParseException(
                    String.format(
                        "Contradicting declaration of package %s." +
                            " Previously seen with annotations \"%s\", but now with \"%s\"",
                        name,
                        existingAnnotations,
                        annotations,
                    ),
                    fileLocation(sourcePosition),
                )
            }

            return existing
        }

        // Wrap the file location and annotations in a PackageInfo.
        val packageInfo =
            PackageInfo(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                annotations = annotations,
                // Packages loaded from signature files have [SelectableItem.documentation] set to
                // `null`. That is not a problem as it is only needed when creating stubs containing
                // enhanced documentation which cannot be created from signature files.
                commentFactory = ItemDocumentation.NONE_FACTORY,
            )

        // Create the package. This relies on containing packages always being processed before any
        // contained package which is guaranteed by the signature file order.
        return codebase.packageTracker.createPackage(name, packageInfo)
    }

    override fun beforeClass() {
        // Reset per-item @ForRemoval state before parsing the class or typealias modifiers.
        currentItemHasRemovedAnnotation = false
    }

    override fun consumeTypeAlias(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        aliasedTypeString: TypeString,
    ) {
        val (typeParameterList, typeItemFactory) =
            createTypeParameterListAndFactory(
                globalTypeItemFactory,
                "typealias",
                name,
                typeParameters,
            )
        val qualifiedClassName = qualifiedName(currentPackage.qualifiedName(), name)
        val type = typeItemFactory.getGeneralType(aliasedTypeString)

        // Check for the existing class from a previously parsed file. If it was found then use that
        // and return. If it could not be found then drop through to create it.
        val classCharacteristics =
            ClassCharacteristics(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                qualifiedName = qualifiedClassName,
                fullName = name,
                classKind = ClassKind.TYPEALIAS,
                modifiers = modifiers.toImmutable(),
                superClassType = null,
                interfaceTypes = emptySet(),
                optionalAliasedType = type,
                classOrigin = currentClassOrigin,
            )
        if (checkForExistingClass(classCharacteristics)) {
            return
        }

        val typeAlias =
            itemFactory.createTypeAliasItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                qualifiedName = qualifiedClassName,
                containingPackage = currentPackage,
                aliasedType = type,
                typeParameterList = typeParameterList,
                origin = currentClassOrigin,
            )
        // Mark type alias as belonging to the main API surface of this signature file.
        updateSelectedApi(typeAlias)
    }

    override fun startClass(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        classKind: ClassKind,
        fullName: String,
        typeParameters: List<ParsedTypeParameter>,
        superClassTypeString: TypeString?,
        interfaceTypeStrings: List<TypeString>,
        permitTypeStrings: List<TypeString>,
        recordComponents: List<ParsedRecordComponent>?,
    ) {
        // Resolve the declared class name, outer class, and type parameters in the current package.
        val (
            qualifiedClassName,
            outerClass,
            typeParameterList,
            typeItemFactory,
        ) = resolveDeclaredClassType(currentPackage, fullName, typeParameters, sourcePosition)

        var superClassType =
            createSuperClassType(
                classKind,
                qualifiedClassName,
                outerClass,
                superClassTypeString,
                typeItemFactory,
            )
        val interfaceTypes = createInterfaceTypes(classKind, interfaceTypeStrings, typeItemFactory)
        val permitTypes = createPermitTypes(permitTypeStrings, typeItemFactory)

        // Check for the existing class from a previously parsed file. If it was found then use that
        // and return. If it could not be found then drop through to create it.
        val classCharacteristics =
            ClassCharacteristics(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                qualifiedName = qualifiedClassName,
                fullName = fullName,
                classKind = classKind,
                modifiers = modifiers.toImmutable(),
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
                optionalAliasedType = null,
                classOrigin = currentClassOrigin,
            )
        if (checkForExistingClass(classCharacteristics)) {
            return
        }

        // Default the superClassType() to java.lang.Object for any class that is not an interface,
        // annotation, or enum and which is not itself java.lang.Object.
        if (
            classKind == ClassKind.CLASS &&
                superClassType == null &&
                qualifiedClassName != JAVA_LANG_OBJECT
        ) {
            superClassType = WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE
        }

        // Create the DefaultClassItem and set its package.
        val cl =
            itemFactory.createClassItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                classKind = classKind,
                containingClass = outerClass,
                containingPackage = currentPackage,
                qualifiedName = qualifiedClassName,
                typeParameterList = typeParameterList,
                origin = currentClassOrigin,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes.toList(),
                permitTypes = permitTypes,
                targetLanguages = targetLanguages,
                // Classes with the placeholder name for top level declarations in a
                // MultiplatformCodebase are definitely facade classes. There isn't enough
                // information to tell for other classes, so this defaults to false otherwise.
                isFileFacade = fullName == ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME,
                recordComponentItemsFactory =
                    if (recordComponents == null) null
                    else
                        { classItem ->
                            recordComponents.map {
                                it.createRecordComponent(classItem, typeItemFactory)
                            }
                        }
            )
        // Update the class's SelectedApi and record its resolved variant as the default variant for
        // any members parsed in the class body.
        currentClassApiVariant = updateSelectedApi(cl)

        currentClass = cl
        currentClassTypeItemFactory = typeItemFactory
        hasExistingConstructors = false
        hasExistingFields = false
        hasExistingMethods = false
        hasExistingProperties = false
    }

    /**
     * Checks to see if there is an existing class with the same qualified name as
     * [classCharacteristics] already existing in the codebase. If there is, sets [currentClass] and
     * marks that [classCharacteristics] should be merged into the existing class.
     *
     * Returns whether a matching class was found.
     */
    private fun checkForExistingClass(
        classCharacteristics: ClassCharacteristics,
    ): Boolean {
        val existingClass =
            codebase.findClassInCodebase(classCharacteristics.qualifiedName) ?: return false

        // Set up the current class state so that any members in the class body are added to the
        // existing class (typealiases do not have a class body).
        if (classCharacteristics.classKind != ClassKind.TYPEALIAS) {
            currentClass = existingClass

            // Use the current signature file's variant as the default for any members added to the
            // existing class in this file.
            currentClassApiVariant = currentApiVariant
            currentClassTypeItemFactory = typeItemFactoryForClass(existingClass)
            hasExistingConstructors = existingClass.constructors().isNotEmpty()
            hasExistingFields = existingClass.fields().isNotEmpty()
            hasExistingMethods = existingClass.methods().isNotEmpty()
            hasExistingProperties = existingClass.properties().isNotEmpty()
        }

        // Perform any merge checks after loading all the files. That is needed because merging
        // may resolve classes and doing that during parsing can lead to issues.
        classMerger.deferMergingIntoExistingClass(existingClass, classCharacteristics)

        return true
    }

    /** Get the [TextTypeItemFactory] for a previously created [ClassItem]. */
    private fun typeItemFactoryForClass(classItem: ClassItem?): TextTypeItemFactory =
        globalTypeItemFactory.from(classItem)

    /**
     * Resolves [superClassTypeString] using [typeItemFactory], falling back to
     * [ClassKind.implicitSuperClassType] if [superClassTypeString] is `null`.
     */
    private fun createSuperClassType(
        classKind: ClassKind,
        qualifiedName: String,
        outerClass: ClassItem?,
        superClassTypeString: TypeString?,
        typeItemFactory: TextTypeItemFactory,
    ): ClassTypeItem? {
        if (superClassTypeString != null) {
            return typeItemFactory.getSuperClassType(superClassTypeString)
        }
        return classKind.implicitSuperClassType(qualifiedName, outerClass)
    }

    /**
     * Resolves [interfaceTypeStrings] using [typeItemFactory] and combines them with any
     * [ClassKind.implicitInterfaceType].
     */
    private fun createInterfaceTypes(
        classKind: ClassKind,
        interfaceTypeStrings: List<TypeString>,
        typeItemFactory: TextTypeItemFactory,
    ): Set<ClassTypeItem> {
        val interfaceTypes = mutableSetOf<ClassTypeItem>()

        // Add any ClassKind specific implicit interface types.
        classKind.implicitInterfaceType?.let { interfaceType -> interfaceTypes.add(interfaceType) }

        for (interfaceTypeString in interfaceTypeStrings) {
            interfaceTypes.add(typeItemFactory.getInterfaceType(interfaceTypeString))
        }
        return interfaceTypes
    }

    /**
     * Resolves [permitTypeStrings] using [typeItemFactory], returning the permitted subclass types
     * sorted by qualified name.
     */
    private fun createPermitTypes(
        permitTypeStrings: List<TypeString>,
        typeItemFactory: TextTypeItemFactory,
    ): List<ClassTypeItem> {
        if (permitTypeStrings.isEmpty()) {
            return emptyList()
        }
        val permitTypes =
            permitTypeStrings.mapTo(mutableListOf()) {
                typeItemFactory.getHierarchicalClassType(it)
            }
        permitTypes.sortWith(TypeItem.qualifiedComparator)
        return permitTypes
    }

    /** Encapsulates multiple return values from [resolveDeclaredClassType]. */
    private data class DeclaredClassTypeComponents(
        /** The fully qualified name, including package and full name. */
        val qualifiedName: String,
        /** The optional, resolved outer [ClassItem]. */
        val outerClass: SkeletonClassItem?,
        /** The set of type parameters. */
        val typeParameterList: TypeParameterList,
        /**
         * The [TextTypeItemFactory] including any type parameters in the [typeParameterList] in its
         * [TextTypeItemFactory.typeParameterScope].
         */
        val typeItemFactory: TextTypeItemFactory,
    )

    /**
     * Resolves the declared class name [fullName] and [parsedTypeParameters] in [pkg] into
     * [DeclaredClassTypeComponents].
     *
     * If the qualified name matches an existing class then return its information.
     */
    private fun resolveDeclaredClassType(
        pkg: PackageItem,
        fullName: String,
        parsedTypeParameters: List<ParsedTypeParameter>,
        classSourcePosition: SourcePosition,
    ): DeclaredClassTypeComponents {
        val pkgName = pkg.qualifiedName()
        val qualifiedName = qualifiedName(pkgName, fullName)

        // Split the full name into an optional outer class and a simple name.
        val outerClassFullName = fullName.extractOptionalQualifierName()
        val outerClass =
            if (outerClassFullName == null) {
                null
            } else {
                val qualifiedOuterClassName = qualifiedName(pkgName, outerClassFullName)

                // Search for the outer class in the codebase. This is safe as the outer class
                // always precedes its nested classes.
                assembler.getOrCreateClass(
                    qualifiedOuterClassName,
                    isOuterClassOfClassInThisCodebase = true
                ) as SkeletonClassItem
            }

        // Get the [TextTypeItemFactory] for the outer class, if any.
        val outerClassTypeItemFactory = typeItemFactoryForClass(outerClass)

        val (typeParameterList, typeItemFactory) =
            createTypeParameterListAndFactory(
                outerClassTypeItemFactory,
                "class",
                qualifiedName,
                parsedTypeParameters,
            )

        // Decide which type parameter list and factory to actually use.
        //
        // If the class already exists then reuse its type parameter list and factory, otherwise use
        // the newly created one.
        //
        // The reason for this is that otherwise any types parsed with the newly created factory
        // would reference type parameters in the newly created list which are different to the ones
        // belonging to the existing class.
        val (actualTypeParameterList, actualTypeItemFactory) =
            codebase.findClassInCodebase(qualifiedName)?.let { existingClass ->
                // Check to make sure that the type parameter lists are the same.
                val existingTypeParameterList = existingClass.typeParameterList
                val existingTypeParameterListString = existingTypeParameterList.toString()
                val normalizedTypeParameterListString = typeParameterList.toString()
                if (normalizedTypeParameterListString != existingTypeParameterListString) {
                    val location = existingClass.fileLocation
                    throw ApiParseException(
                        "Inconsistent type parameter list for $qualifiedName, this has $normalizedTypeParameterListString but it was previously defined as $existingTypeParameterListString at $location",
                        fileLocation(classSourcePosition)
                    )
                }

                Pair(existingTypeParameterList, typeItemFactoryForClass(existingClass))
            } ?: Pair(typeParameterList, typeItemFactory)

        return DeclaredClassTypeComponents(
            qualifiedName = qualifiedName,
            outerClass = outerClass,
            typeParameterList = actualTypeParameterList,
            typeItemFactory = actualTypeItemFactory,
        )
    }

    /**
     * Creates a [TypeParameterListAndFactory] from [parsedTypeParameters] in the context of
     * [enclosingTypeItemFactory].
     *
     * If [parsedTypeParameters] is empty, returns [TypeParameterList.NONE] paired with
     * [enclosingTypeItemFactory] without allocating a nested factory.
     */
    private fun createTypeParameterListAndFactory(
        enclosingTypeItemFactory: TextTypeItemFactory,
        kind: String,
        name: String,
        parsedTypeParameters: List<ParsedTypeParameter>,
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        if (parsedTypeParameters.isEmpty()) {
            return TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        }

        // Create the List<TypeParameterItem> and the corresponding TypeItemFactory that can be
        // used to resolve TypeParameterItems from the list. This performs the construction in two
        // stages to handle cycles between the parameters.
        return enclosingTypeItemFactory.createTypeParameterItemsAndFactory(
            "$kind $name",
            parsedTypeParameters,
            { parsed ->
                // TODO: Type use annotations support will need to handle annotations on the
                //  parameter.
                val modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC)
                itemFactory.createTypeParameterItem(
                    modifiers = modifiers,
                    name = parsed.name,
                    isReified = parsed.isReified,
                )
            },
            { typeItemFactory, parsed ->
                if (parsed.bounds.isEmpty()) {
                    WellKnownTypes.defaultTypeParameterBounds(forKotlin = false)
                } else {
                    parsed.bounds.map { typeItemFactory.getBoundsType(it) }
                }
            },
        )
    }

    override fun beforeMember() {
        // Reset per-item @ForRemoval state before parsing the member modifiers.
        currentItemHasRemovedAnnotation = false
    }

    override fun consumeConstructor(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
    ) {
        val containingClass = currentClass
        // Get a TypeParameterList and accompanying TypeItemFactory
        val (typeParameterList, typeItemFactory) =
            createTypeParameterListAndFactory(
                currentClassTypeItemFactory,
                "constructor",
                name,
                typeParameters,
            )

        val throwsList = createThrowsTypes(throwsTypeStrings, typeItemFactory)
        val constructor =
            itemFactory.createConstructorItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = containingClass.type(),
                parameterItemsFactory = { methodItem ->
                    createParameterItems(methodItem, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                // Signature files do not track implicit constructors, all constructors are treated
                // the same as whether it was created by the compiler or in the source has no effect
                // on the API surface.
                implicitConstructor = false,
                targetLanguages = targetLanguages,
            )
        updateSelectedApi(constructor)
        if (hasExistingConstructors) {
            containingClass.replaceOrAddConstructor(constructor)
        } else {
            containingClass.addConstructor(constructor)
        }
    }

    override fun consumeMethod(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        returnTypeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
        defaultAnnotationMethodValue: String?,
    ) {
        val containingClass = currentClass
        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) =
            createTypeParameterListAndFactory(
                currentClassTypeItemFactory,
                "method",
                name,
                typeParameters,
            )

        val returnType =
            typeItemFactory.getMethodReturnType(
                returnTypeString,
                modifiers.annotations(),
                MethodFingerprint(name, parameters.size),
                containingClass.isAnnotationType(),
            )
        synchronizeNullability(returnType, modifiers)

        if (containingClass.isInterface() && !modifiers.isDefault() && !modifiers.isStatic()) {
            modifiers.setAbstract(true)
        }

        val throwsList = createThrowsTypes(throwsTypeStrings, typeItemFactory)
        val defaultValueProvider =
            defaultAnnotationMethodValue?.let { valueString ->
                valueParser.providerFor(returnType, valueString, ValueUseSite.ANNOTATION)
            }

        val method =
            itemFactory.createMethodItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = returnType,
                parameterItemsFactory = { containingCallable ->
                    createParameterItems(containingCallable, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                defaultValueProvider = defaultValueProvider,
                targetLanguages = targetLanguages,
                isExtensionMethod = false, // no way to tell if this is an extension method
            )

        // Ignore enum synthetic methods. They are no longer included in signature files as they add
        // no information. However, they did use to be included and so this filters them out to
        // ensure that the resulting Codebase is consistent with the original source Codebase.
        if (method.isEnumSyntheticMethod()) return

        updateSelectedApi(method)
        if (hasExistingMethods) {
            containingClass.replaceOrAddMethod(method)
        } else {
            containingClass.addMethod(method)
        }
    }

    override fun consumeField(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeString: TypeString,
        valueString: String?,
        isEnumConstant: Boolean,
    ) {
        val containingClass = currentClass
        // Parse the type string and then synchronize the field's nullability with the type.
        val type =
            currentClassTypeItemFactory.getFieldType(
                underlyingType = typeString,
                isEnumConstant = isEnumConstant,
                isFinal = modifiers.isFinal(),
                isInitialValueNonNull = { valueString != null && valueString != "null" },
                itemAnnotations = modifiers.annotations(),
            )
        synchronizeNullability(type, modifiers)

        // In signature files fields have to be static and final in order for them to have a
        // constant value in addition to a value.
        val constantValueProvider =
            if (valueString != null) {
                if (modifiers.isStatic() && modifiers.isFinal())
                    valueParser.providerFor(type, valueString, ValueUseSite.FIELD)
                else {
                    // Report that the value is being ignored.
                    reportIssue(
                        Issues.SIGNATURE_FILE_ERROR,
                        "Field $name in $containingClass has a value of `$valueString` but is not `static` and `final`; ignoring value",
                        fileLocation(sourcePosition),
                    )
                    null
                }
            } else null

        val field =
            itemFactory.createFieldItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                type = type,
                isEnumConstant = isEnumConstant,
                constantValueProvider = constantValueProvider,
                targetLanguages = targetLanguages,
            )
        updateSelectedApi(field)
        if (hasExistingFields) {
            containingClass.replaceOrAddField(field)
        } else {
            containingClass.addField(field)
        }
    }

    override fun consumeProperty(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
        receiverTypeString: TypeString?,
        typeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        contextParameters: List<ParsedParameter>,
    ) {
        val containingClass = currentClass
        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) =
            createTypeParameterListAndFactory(
                currentClassTypeItemFactory,
                "property",
                name,
                typeParameters,
            )

        val type = typeItemFactory.getGeneralType(typeString)
        synchronizeNullability(type, modifiers)
        val receiverType = receiverTypeString?.let { typeItemFactory.getGeneralType(it) }

        val property =
            itemFactory.createPropertyItem(
                fileLocationSource = currentFileLocationSource,
                sourcePosition = sourcePosition,
                modifiers = modifiers,
                name = name,
                containingClass = containingClass,
                type = type,
                receiver = receiverType,
                typeParameterList = typeParameterList,
                // There isn't any information about whether a setter exists or its visibility if it
                // does in API files currently.
                setterVisibility = null,
                contextParameterFactory = { propertyItem ->
                    contextParameters.map { it.create(propertyItem, typeItemFactory) }
                },
            )
        updateSelectedApi(property)
        if (hasExistingProperties) {
            containingClass.replaceOrAddProperty(property)
        } else {
            containingClass.addProperty(property)
        }
    }

    /**
     * Create [ParameterItem]s for the [containingCallable] from the [parameters] using the
     * [typeItemFactory] to create types.
     *
     * This is called from within the constructor of the [containingCallable] so must only access
     * its `name` and its reference. In particularly it must not access its
     * [CallableItem.parameters] property as this is called during its initialization.
     */
    private fun createParameterItems(
        containingCallable: CallableItem,
        parameters: List<ParsedParameter>,
        typeItemFactory: TextTypeItemFactory,
    ): List<ParameterItem> {
        val methodFingerprint = MethodFingerprint(containingCallable.name(), parameters.size)
        return parameters.map { it.create(containingCallable, typeItemFactory, methodFingerprint) }
    }

    /**
     * Turn this [ParsedParameter] into a [ParameterItem] of the [containingCallable] by parsing the
     * [ParsedParameter.typeString].
     */
    private fun ParsedParameter.create(
        containingCallable: CallableItem,
        typeItemFactory: TextTypeItemFactory,
        methodFingerprint: MethodFingerprint,
    ): ParameterItem {
        val type =
            typeItemFactory.getMethodParameterType(
                typeString,
                modifiers.annotations(),
                methodFingerprint,
                index,
                modifiers.isVarArg(),
            )
        synchronizeNullability(type, modifiers)

        // The last parameter of a suspend function is the continuation parameter. If there was
        // a parameter kind listed in the file, use that, otherwise this is a value parameter.
        val kind =
            if (
                containingCallable.modifiers.isSuspend() &&
                    index == methodFingerprint.parameterCount - 1
            ) {
                ParameterKind.CONTINUATION
            } else {
                optionalKind ?: ParameterKind.VALUE
            }

        return itemFactory.createParameterItem(
            fileLocationSource = currentFileLocationSource,
            sourcePosition = sourcePosition,
            modifiers = modifiers,
            name = name,
            publicName = publicName,
            containingItem = containingCallable,
            parameterIndex = index,
            type = type,
            hasDefaultValue = hasDefaultValue,
            kind = kind,
        )
    }

    /**
     * Turn this [ParsedParameter] into a context [ParameterItem] of the [containingProperty] by
     * parsing the [ParsedParameter.typeString].
     */
    private fun ParsedParameter.create(
        containingProperty: PropertyItem,
        typeItemFactory: TextTypeItemFactory,
    ): ParameterItem {
        val type = typeItemFactory.getGeneralType(typeString)
        synchronizeNullability(type, modifiers)
        return itemFactory.createParameterItem(
            fileLocationSource = currentFileLocationSource,
            sourcePosition = sourcePosition,
            modifiers = modifiers,
            name = name,
            publicName = publicName,
            containingItem = containingProperty,
            parameterIndex = index,
            type = type,
            hasDefaultValue = hasDefaultValue,
            kind = ParameterKind.CONTEXT, // All ParameterItems for a property are context
        )
    }

    /** Resolves a list of [throwsTypeStrings] into [ExceptionTypeItem]s using [typeItemFactory]. */
    private fun createThrowsTypes(
        throwsTypeStrings: List<TypeString>,
        typeItemFactory: TextTypeItemFactory,
    ): List<ExceptionTypeItem> {
        if (throwsTypeStrings.isEmpty()) return emptyList()
        return throwsTypeStrings.map { typeString -> typeItemFactory.getExceptionType(typeString) }
    }

    /**
     * Synchronize nullability annotations on the API item and [TypeNullability].
     *
     * If the type string uses a Kotlin nullability suffix, this adds an annotation representing
     * that nullability to [modifiers].
     *
     * @param typeItem the type of the API item.
     * @param modifiers the API item's modifiers.
     */
    private fun synchronizeNullability(typeItem: TypeItem, modifiers: MutableModifierList) {
        if (kotlinStyleNulls) {
            // Add an annotation to the context item for the type's nullability if applicable.
            val annotationClassNameToAdd =
                // Treat varargs as non-null for consistency with the psi model.
                if (typeItem is ArrayTypeItem && typeItem.isVarargs) {
                    ANDROIDX_NONNULL
                } else {
                    val nullability = typeItem.modifiers.nullability
                    if (typeItem !is PrimitiveTypeItem && nullability == TypeNullability.NONNULL) {
                        ANDROIDX_NONNULL
                    } else if (nullability == TypeNullability.NULLABLE) {
                        ANDROIDX_NULLABLE
                    } else {
                        // No annotation to add, return.
                        return
                    }
                }
            val annotation =
                AnnotationItem.createMarkerAnnotation(codebase, annotationClassNameToAdd)
            modifiers.addAnnotation(annotation)
        }
    }

    /**
     * Create a [RecordComponentItem] on [classItem] from this [ParsedRecordComponent] using
     * [typeItemFactory] to resolve its [ParsedRecordComponent.typeString].
     */
    private fun ParsedRecordComponent.createRecordComponent(
        classItem: ClassItem,
        typeItemFactory: TextTypeItemFactory,
    ): RecordComponentItem =
        itemFactory.createRecordComponentItem(
            fileLocationSource = currentFileLocationSource,
            sourcePosition = sourcePosition,
            modifiers = modifiers,
            name = name,
            containingClass = classItem,
            type = typeItemFactory.getGeneralType(typeString),
            recordComponentIndex = recordComponentIndex,
        )

    private fun qualifiedName(pkg: String, className: String): String = "$pkg.$className"
}
