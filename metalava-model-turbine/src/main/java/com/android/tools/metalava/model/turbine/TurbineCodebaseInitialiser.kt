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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FixedFieldValue
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.ModifierFlags.Companion.ABSTRACT
import com.android.tools.metalava.model.ModifierFlags.Companion.DEFAULT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.PRIVATE
import com.android.tools.metalava.model.ModifierFlags.Companion.PROTECTED
import com.android.tools.metalava.model.ModifierFlags.Companion.PUBLIC
import com.android.tools.metalava.model.ModifierFlags.Companion.SEALED
import com.android.tools.metalava.model.ModifierFlags.Companion.STATIC
import com.android.tools.metalava.model.ModifierFlags.Companion.STRICT_FP
import com.android.tools.metalava.model.ModifierFlags.Companion.SYNCHRONIZED
import com.android.tools.metalava.model.ModifierFlags.Companion.TRANSIENT
import com.android.tools.metalava.model.ModifierFlags.Companion.VARARG
import com.android.tools.metalava.model.ModifierFlags.Companion.VOLATILE
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebaseFactory
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.DefaultValue
import com.android.tools.metalava.model.item.FieldValue
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.gatherPackageJavadoc
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass.FieldInfo
import com.google.turbine.binder.bound.TypeBoundClass.MethodInfo
import com.google.turbine.binder.bound.TypeBoundClass.ParamInfo
import com.google.turbine.binder.bound.TypeBoundClass.TyVarInfo
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.SimpleEnv
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.TopLevelIndex
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.binder.sym.TyVarSymbol
import com.google.turbine.diag.SourceFile
import com.google.turbine.diag.TurbineLog
import com.google.turbine.model.TurbineFlag
import com.google.turbine.model.TurbineTyKind
import com.google.turbine.parse.Parser
import com.google.turbine.processing.ModelFactory
import com.google.turbine.processing.TurbineElements
import com.google.turbine.processing.TurbineTypes
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.Anno
import com.google.turbine.tree.Tree.AnnoExpr
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.tree.Tree.Literal
import com.google.turbine.tree.Tree.MethDecl
import com.google.turbine.tree.Tree.TyDecl
import com.google.turbine.tree.Tree.VarDecl
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type
import java.io.File
import java.util.Optional
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * This initializer acts as an adapter between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
internal class TurbineCodebaseInitialiser(
    codebaseFactory: DefaultCodebaseFactory,
    private val classpath: List<File>,
    private val allowReadingComments: Boolean,
) : DefaultCodebaseAssembler() {

    internal val codebase = codebaseFactory(this)

    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /**
     * Map between ClassSymbols and TurbineClass for classes present on the source path or the class
     * path
     */
    private lateinit var envClassMap: CompoundEnv<ClassSymbol, TypeBoundClass>

    private lateinit var index: TopLevelIndex

    /** Caches [TurbineSourceFile] instances. */
    private lateinit var sourceFileCache: TurbineSourceFileCache

    /** Factory for creating [AnnotationItem]s from [AnnoInfo]s. */
    private lateinit var annotationFactory: TurbineAnnotationFactory

    /** Global [TurbineTypeItemFactory] from which all other instances are created. */
    private lateinit var globalTypeItemFactory: TurbineTypeItemFactory

    /** Creates [Item] instances for [codebase]. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Turbine can only process java files.
            defaultItemLanguage = ItemLanguage.JAVA,
            // Source files need to track which parts belong to which API surface variants, so they
            // need to create an ApiVariantSelectors instance that can be used to track that.
            defaultVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        )

    /**
     * Data Type: TurbineElements (An implementation of javax.lang.model.util.Elements)
     *
     * Usage: Enables lookup of TypeElement objects by name.
     */
    private lateinit var turbineElements: TurbineElements

    /**
     * Populates [codebase] from the [sourceSet].
     *
     * Then creates the packages, classes and their members, as well as sets up various class
     * hierarchies using the binder's output
     */
    fun initialize(
        sourceSet: SourceSet,
        apiPackages: PackageFilter?,
    ) {
        // Get the units from the source files provided on the command line.
        val commandLineSources = sourceSet.sources
        val sourceFiles = getSourceFiles(commandLineSources.asSequence())
        val units = sourceFiles.map { Parser.parse(it) }

        // Get the sequence of all files that can be found on the source path which are not
        // explicitly listed on the command line.
        val scannedFiles = scanSourcePath(sourceSet.sourcePath, commandLineSources.toSet())
        val sourcePathFiles = getSourceFiles(scannedFiles)

        // Get the set of qualified class names provided on the command line. If a `.java` file
        // contains multiple java classes then it just used the main class name.
        val commandLineClasses = units.mapNotNull { unit -> unit.mainClassQualifiedName }.toSet()

        // Get the units for the extra source files found on the source path.
        val extraUnits =
            sourcePathFiles
                .map { Parser.parse(it) }
                // Ignore any files that contain duplicates of a class that was specified on the
                // command line. This is needed when merging annotations from other java files as
                // there may be duplicate definitions of the class on the source path.
                .filter { unit -> unit.mainClassQualifiedName !in commandLineClasses }

        // Combine all the units together.
        val allUnits = ImmutableList.builder<CompUnit>().addAll(units).addAll(extraUnits).build()

        // Bind the units
        try {
            val procInfo =
                ProcessorInfo.create(
                    ImmutableList.of(),
                    null,
                    ImmutableMap.of(),
                    SourceVersion.latest()
                )

            // Any non-fatal error (like unresolved symbols) will be captured in this log and will
            // be ignored.
            val log = TurbineLog()

            bindingResult =
                Binder.bind(
                    log,
                    allUnits,
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    procInfo,
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
            index = bindingResult.tli()
        } catch (e: Throwable) {
            throw e
        }
        // Get the SourceTypeBoundClass for all units that have been bound together.
        val allSourceClassMap = bindingResult.units()

        // Maps class symbols to their source-based definitions
        val sourceEnv = SimpleEnv(allSourceClassMap)

        // Maps class symbols to their classpath-based definitions
        val classPathEnv = bindingResult.classPathEnv()

        // Provides a unified view of both source and classpath classes. Although, the `sourceEnv`
        // is appended to the `CompoundEnv` that contains the `classPathEnv`, it is actually
        // queried first. So, this will search for a class on the source path first and then on the
        // class path.
        envClassMap = CompoundEnv.of<ClassSymbol, TypeBoundClass>(classPathEnv).append(sourceEnv)

        // used to create language model elements for code analysis
        val factory = ModelFactory(envClassMap, ClassLoader.getSystemClassLoader(), index)
        // provides type-related operations within the Turbine compiler context
        val turbineTypes = TurbineTypes(factory)
        // provides access to code elements (packages, types, members) for analysis.
        turbineElements = TurbineElements(factory, turbineTypes)

        // Create a cache from SourceFile to the TurbineSourceFile wrapper. The latter needs the
        // CompUnit associated with the SourceFile so pass in all the CompUnits so it can find it.
        sourceFileCache = TurbineSourceFileCache(codebase, allUnits)

        // Create a factory for creating annotations from AnnoInfo.
        annotationFactory = TurbineAnnotationFactory(codebase, sourceFileCache)

        // Create the global TurbineTypeItemFactory.
        globalTypeItemFactory =
            TurbineTypeItemFactory(this, annotationFactory, TypeParameterScope.empty)

        // Find the package-info.java units.
        val packageInfoUnits = allUnits.filter { it.isPackageInfo() }

        // Split the map from ClassSymbol to SourceTypeBoundClass into separate package-info and
        // normal classes.
        val (packageInfoClasses, allSourceClasses) =
            separatePackageInfoClassesFromRealClasses(allSourceClassMap)

        val packageInfoList =
            combinePackageInfoClassesAndUnits(packageInfoClasses, packageInfoUnits)

        // Scan the files looking for package.html and overview.html files and combine that with
        // information from package-info.java units to create a comprehensive set of package
        // documentation just in case they are needed during package creation.
        val packageDocs =
            gatherPackageJavadoc(
                codebase.reporter,
                sourceSet,
                packageNameFilter = { true },
                packageInfoList
            ) { (unit, packageName, sourceTypeBoundClass) ->
                val source = unit.source().source()
                val file = File(unit.source().path())
                val fileLocation = FileLocation.forFile(file)
                val comment = getHeaderComments(source).toItemDocumentationFactory()

                val annotations =
                    annotationFactory.createAnnotations(sourceTypeBoundClass.annotations())

                val modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC, annotations)
                MutablePackageDoc(packageName, fileLocation, modifiers, comment)
            }

        // Get the map from ClassSymbol to SourceTypeBoundClass for only those classes provided on
        // the command line as only those classes can contribute directly to the API.
        val commandLineSourceClasses =
            topLevelAccessibleCommandLineClasses(allSourceClasses, commandLineSources)

        createAllPackages(packageDocs)
        createAllCommandLineClasses(commandLineSourceClasses, apiPackages)
    }

    /**
     * Compute the set of accessible, top level classes that were specified on the command line.
     *
     * @param allSourceClasses all the [SourceTypeBoundClass]s found during binding, includes those
     *   from the source path as well as those whose containing file was provided on the command
     *   line.
     * @param commandLineSources the list of source [File]s provided on the command line.
     */
    private fun topLevelAccessibleCommandLineClasses(
        allSourceClasses: Map<ClassSymbol, SourceTypeBoundClass>,
        commandLineSources: List<File>
    ): Map<ClassSymbol, SourceTypeBoundClass> {
        // The set of paths supplied on the command line.
        val commandLinePaths = commandLineSources.map { it.path }.toSet()

        // Get the map from ClassSymbol to SourceTypeBoundClass for only the accessible, top level
        // classes provided on the command line as only those classes (and their nested classes) can
        // contribute directly to the API.
        return allSourceClasses.filter { (_, sourceTypeBoundClass) ->
            // Ignore nested classes, they will be created as part of the construction of their
            // containing class.
            if (sourceTypeBoundClass.owner() != null) return@filter false

            // Ignore inaccessible classes.
            if (!sourceTypeBoundClass.isAccessible) return@filter false

            // Ignore classes whose paths were not specified on the command line.
            val path = sourceTypeBoundClass.source().path()
            path in commandLinePaths
        }
    }

    /**
     * Get the qualified class name of the main class in a unit.
     *
     * If a `.java` file contains multiple java classes then the main class is the first one which
     * is assumed to be the public class.
     */
    private val CompUnit.mainClassQualifiedName: String?
        get() {
            val pkgName = getPackageName(this)
            return decls().firstOrNull()?.let { decl -> "$pkgName.${decl.name()}" }
        }

    private fun scanSourcePath(sourcePath: List<File>, existingSources: Set<File>): Sequence<File> {
        val visited = mutableSetOf<String>()
        return sourcePath
            .asSequence()
            .flatMap { sourceRoot ->
                sourceRoot
                    .walkTopDown()
                    // The following prevents repeatedly re-entering the same directory if there is
                    // a cycle in the files, e.g. a symlink from a subdirectory back up to an
                    // ancestor directory.
                    .onEnter { dir ->
                        // Use the canonical path as each file in a cycle can be represented by an
                        // infinite number of paths and using them would make the visited check
                        // useless.
                        val canonical = dir.canonicalPath
                        return@onEnter if (canonical in visited) false
                        else {
                            visited += canonical
                            true
                        }
                    }
            }
            .filter { it !in existingSources }
    }

    /**
     * Find the TypeBoundClass for the `ClassSymbol` in the source path and if it could not find it
     * then look in the class path. It is guaranteed to be found in one of those places as otherwise
     * there would be no `ClassSymbol`.
     */
    private fun typeBoundClassForSymbol(classSymbol: ClassSymbol) = envClassMap.get(classSymbol)!!

    /**
     * Separate `package-info.java` synthetic classes from real classes.
     *
     * Turbine treats a `package-info.java` file as if it created a class called `package-info`.
     * This method separates the [sourceClassMap] into two, one for the synthetic `package-info`
     * classes and one for real classes.
     *
     * @param sourceClassMap the map from [ClassSymbol] to [SourceTypeBoundClass] for all classes,
     *   real or synthetic.
     */
    private fun separatePackageInfoClassesFromRealClasses(
        sourceClassMap: Map<ClassSymbol, SourceTypeBoundClass>,
    ): Pair<Map<ClassSymbol, SourceTypeBoundClass>, Map<ClassSymbol, SourceTypeBoundClass>> {
        val packageInfoClasses = mutableMapOf<ClassSymbol, SourceTypeBoundClass>()
        val sourceClasses = mutableMapOf<ClassSymbol, SourceTypeBoundClass>()
        for ((symbol, typeBoundClass) in sourceClassMap) {
            if (symbol.simpleName() == "package-info") {
                packageInfoClasses[symbol] = typeBoundClass
            } else {
                sourceClasses[symbol] = typeBoundClass
            }
        }
        return Pair(packageInfoClasses, sourceClasses)
    }

    /**
     * Encapsulates information needed to create a [DefaultPackageItem] in [gatherPackageJavadoc].
     */
    data class PackageInfoClass(
        val unit: CompUnit,
        val packageName: String,
        val sourceTypeBoundClass: SourceTypeBoundClass,
    )

    /** Combine `package-info.java` synthetic classes and units */
    private fun combinePackageInfoClassesAndUnits(
        sourceClassMap: Map<ClassSymbol, SourceTypeBoundClass>,
        packageInfoUnits: List<CompUnit>
    ): List<PackageInfoClass> {
        // Create a mapping between the package name and the unit.
        val packageInfoMap = packageInfoUnits.associateBy { getPackageName(it) }

        return sourceClassMap.entries.map { (symbol, typeBoundClass) ->
            val packageName = symbol.packageName().replace('/', '.')
            PackageInfoClass(
                unit = packageInfoMap[packageName]!!,
                packageName = packageName,
                sourceTypeBoundClass = typeBoundClass,
            )
        }
    }

    /** Check if this is for a `package-info.java` file or not. */
    private fun CompUnit.isPackageInfo() =
        source().path().let { it == JAVA_PACKAGE_INFO || it.endsWith("/" + JAVA_PACKAGE_INFO) }

    private fun createAllPackages(packageDocs: PackageDocs) {
        // Create packages for all the documentation packages and make sure there is a root package.
        codebase.packageTracker.createInitialPackages(packageDocs)
    }

    private fun createAllCommandLineClasses(
        sourceClassMap: Map<ClassSymbol, SourceTypeBoundClass>,
        apiPackages: PackageFilter?,
    ) {
        // Iterate over all the classes in the sources.
        for ((classSymbol, sourceBoundClass) in sourceClassMap) {
            // If a package filter is supplied then ignore any classes that do not match it.
            if (apiPackages != null) {
                val packageName = classSymbol.dotSeparatedPackageName
                if (!apiPackages.matches(packageName)) continue
            }

            val classItem =
                createTopLevelClassAndContents(
                    classSymbol = classSymbol,
                    sourceBoundClass,
                    origin = ClassOrigin.COMMAND_LINE,
                )
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    val ClassSymbol.isTopClass
        get() = !binaryName().contains('$')

    /**
     * Create top level classes, their nested classes and all the other members.
     *
     * All the classes are registered by name and so can be found by
     * [createClassFromUnderlyingModel].
     */
    private fun createTopLevelClassAndContents(
        classSymbol: ClassSymbol,
        typeBoundClass: TypeBoundClass = typeBoundClassForSymbol(classSymbol),
        origin: ClassOrigin,
    ): ClassItem {
        if (!classSymbol.isTopClass) error("$classSymbol is not a top level class")
        return createClass(
            classSymbol = classSymbol,
            typeBoundClass = typeBoundClass,
            containingClassItem = null,
            enclosingClassTypeItemFactory = globalTypeItemFactory,
            origin = origin,
        )
    }

    /** Tries to create a class from a Turbine class with [qualifiedName]. */
    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        // This will get the symbol for the top class even if the class name is for a nested
        // class.
        val topClassSym = getClassSymbol(qualifiedName)

        // Create the top level class, if needed, along with any nested classes and register
        // them all by name.
        topClassSym?.let {
            // It is possible that the top level class has already been created but just did not
            // contain the requested nested class so check to make sure it exists before
            // creating it.
            val topClassName = topClassSym.qualifiedName
            codebase.findClass(topClassName)
                ?: let {
                    // Get the origin of the class.
                    val typeBoundClass = typeBoundClassForSymbol(topClassSym)
                    val origin =
                        when (typeBoundClass) {
                            is SourceTypeBoundClass -> ClassOrigin.SOURCE_PATH
                            else -> ClassOrigin.CLASS_PATH
                        }

                    // Create and register the top level class and its nested classes.
                    createTopLevelClassAndContents(
                        classSymbol = topClassSym,
                        typeBoundClass = typeBoundClass,
                        origin = origin,
                    )

                    // Now try and find the actual class that was requested by name. If it exists it
                    // should have been created in the previous call.
                    return codebase.findClass(qualifiedName)
                }
        }

        // Could not be found.
        return null
    }

    private fun createModifiers(flag: Int, annoInfos: List<AnnoInfo>): MutableModifierList {
        val annotations = annotationFactory.createAnnotations(annoInfos)
        val modifierItem =
            when (flag) {
                0 -> { // No Modifier. Default modifier is PACKAGE_PRIVATE in such case
                    createMutableModifiers(
                        visibility = VisibilityLevel.PACKAGE_PRIVATE,
                        annotations = annotations,
                    )
                }
                else -> {
                    createMutableModifiers(computeFlag(flag), annotations)
                }
            }
        modifierItem.setDeprecated(isDeprecated(annotations))
        return modifierItem
    }

    /**
     * Given flag value corresponding to Turbine modifiers compute the equivalent flag in Metalava.
     */
    private fun computeFlag(flag: Int): Int {
        // If no visibility flag is provided, result remains 0, implying a 'package-private' default
        // state.
        var result = 0

        if (flag and TurbineFlag.ACC_STATIC != 0) {
            result = result or STATIC
        }
        if (flag and TurbineFlag.ACC_ABSTRACT != 0) {
            result = result or ABSTRACT
        }
        if (flag and TurbineFlag.ACC_FINAL != 0) {
            result = result or FINAL
        }
        if (flag and TurbineFlag.ACC_NATIVE != 0) {
            result = result or NATIVE
        }
        if (flag and TurbineFlag.ACC_SYNCHRONIZED != 0) {
            result = result or SYNCHRONIZED
        }
        if (flag and TurbineFlag.ACC_STRICT != 0) {
            result = result or STRICT_FP
        }
        if (flag and TurbineFlag.ACC_TRANSIENT != 0) {
            result = result or TRANSIENT
        }
        if (flag and TurbineFlag.ACC_VOLATILE != 0) {
            result = result or VOLATILE
        }
        if (flag and TurbineFlag.ACC_DEFAULT != 0) {
            result = result or DEFAULT
        }
        if (flag and TurbineFlag.ACC_SEALED != 0) {
            result = result or SEALED
        }
        if (flag and TurbineFlag.ACC_VARARGS != 0) {
            result = result or VARARG
        }

        // Visibility Modifiers
        if (flag and TurbineFlag.ACC_PUBLIC != 0) {
            result = result or PUBLIC
        }
        if (flag and TurbineFlag.ACC_PRIVATE != 0) {
            result = result or PRIVATE
        }
        if (flag and TurbineFlag.ACC_PROTECTED != 0) {
            result = result or PROTECTED
        }

        return result
    }

    private fun isDeprecated(annotations: List<AnnotationItem>?): Boolean {
        return annotations?.any { it.qualifiedName == "java.lang.Deprecated" } ?: false
    }

    private val ClassSymbol.dotSeparatedPackageName
        get() = packageName().replace('/', '.')

    private fun createClass(
        classSymbol: ClassSymbol,
        typeBoundClass: TypeBoundClass = typeBoundClassForSymbol(classSymbol),
        containingClassItem: DefaultClassItem?,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
        origin: ClassOrigin,
    ): ClassItem {
        val sourceTypeBoundClass = typeBoundClass as? SourceTypeBoundClass
        val decl = sourceTypeBoundClass?.decl()

        // Get the package item
        val pkgName = classSymbol.dotSeparatedPackageName
        val pkgItem = codebase.findOrCreatePackage(pkgName)

        // Create the sourcefile
        val sourceFile =
            if (sourceTypeBoundClass != null) {
                sourceFileCache.turbineSourceFile(sourceTypeBoundClass.source())
            } else null
        val fileLocation =
            when {
                sourceFile != null -> TurbineFileLocation.forTree(sourceFile, decl)
                containingClassItem != null ->
                    TurbineFileLocation.forTree(containingClassItem, decl)
                else -> FileLocation.UNKNOWN
            }

        // Create class
        val qualifiedName = classSymbol.qualifiedName
        val documentation = javadoc(decl)
        val modifierItem =
            createModifiers(
                typeBoundClass.access(),
                typeBoundClass.annotations(),
            )
        val (typeParameters, classTypeItemFactory) =
            createTypeParameters(
                typeBoundClass.typeParameterTypes(),
                enclosingClassTypeItemFactory,
                "class $qualifiedName",
            )
        val classKind = getClassKind(typeBoundClass.kind())

        modifierItem.setSynchronized(false) // A class can not be synchronized in java

        if (classKind == ClassKind.ANNOTATION_TYPE) {
            if (!modifierItem.hasAnnotation(AnnotationItem::isRetention)) {
                modifierItem.addDefaultRetentionPolicyAnnotation(codebase, isKotlin = false)
            }
        }

        // Set up the SuperClass
        val superClassType =
            when (classKind) {
                // Normal classes and enums have a non-null super class type.
                ClassKind.CLASS,
                ClassKind.ENUM ->
                    typeBoundClass.superClassType()?.let {
                        classTypeItemFactory.getSuperClassType(it)
                    }
                // Interfaces and annotations (which are a form of interface) do not.
                ClassKind.INTERFACE,
                ClassKind.ANNOTATION_TYPE -> null
            }

        // Set interface types
        val interfaceTypes =
            typeBoundClass.interfaceTypes().map { classTypeItemFactory.getInterfaceType(it) }

        val classItem =
            itemFactory.createClassItem(
                fileLocation = fileLocation,
                modifiers = modifierItem,
                documentationFactory = getCommentedDoc(documentation),
                source = sourceFile,
                classKind = classKind,
                containingClass = containingClassItem,
                containingPackage = pkgItem,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameters,
                origin = origin,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
            )

        // Create fields
        createFields(classItem, typeBoundClass.fields(), classTypeItemFactory)

        // Create methods
        createMethods(classItem, typeBoundClass.methods(), classTypeItemFactory)

        // Create constructors
        createConstructors(classItem, typeBoundClass.methods(), classTypeItemFactory)

        // Create InnerClasses.
        val children = typeBoundClass.children()
        createNestedClasses(classItem, children.values.asList(), classTypeItemFactory)

        return classItem
    }

    fun getClassKind(type: TurbineTyKind): ClassKind {
        return when (type) {
            TurbineTyKind.INTERFACE -> ClassKind.INTERFACE
            TurbineTyKind.ENUM -> ClassKind.ENUM
            TurbineTyKind.ANNOTATION -> ClassKind.ANNOTATION_TYPE
            else -> ClassKind.CLASS
        }
    }

    private fun createTypeParameters(
        tyParams: ImmutableMap<TyVarSymbol, TyVarInfo>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
        description: String,
    ): TypeParameterListAndFactory<TurbineTypeItemFactory> {

        if (tyParams.isEmpty())
            return TypeParameterListAndFactory(
                TypeParameterList.NONE,
                enclosingClassTypeItemFactory
            )

        // Create a list of [TypeParameterItem]s from turbine specific classes.
        return DefaultTypeParameterList.createTypeParameterItemsAndFactory(
            enclosingClassTypeItemFactory,
            description,
            tyParams.toList(),
            { (sym, tyParam) -> createTypeParameter(sym, tyParam) },
            { typeItemFactory, (_, tParam) -> createTypeParameterBounds(tParam, typeItemFactory) },
        )
    }

    /**
     * Create the [DefaultTypeParameterItem] without any bounds and register it so that any uses of
     * it within the type bounds, e.g. `<E extends Enum<E>>`, or from other type parameters within
     * the same [TypeParameterList] can be resolved.
     */
    private fun createTypeParameter(sym: TyVarSymbol, param: TyVarInfo): DefaultTypeParameterItem {
        val modifiers = createModifiers(0, param.annotations())
        val typeParamItem =
            itemFactory.createTypeParameterItem(
                modifiers,
                name = sym.name(),
                // Java does not supports reified generics
                isReified = false,
            )
        return typeParamItem
    }

    /** Create the bounds of a [DefaultTypeParameterItem]. */
    private fun createTypeParameterBounds(
        param: TyVarInfo,
        typeItemFactory: TurbineTypeItemFactory,
    ): List<BoundsTypeItem> {
        val typeBounds = mutableListOf<BoundsTypeItem>()
        val upperBounds = param.upperBound()

        upperBounds.bounds().mapTo(typeBounds) { typeItemFactory.getBoundsType(it) }
        param.lowerBound()?.let { typeBounds.add(typeItemFactory.getBoundsType(it)) }

        return typeBounds.toList()
    }

    /** This method sets up the nested class hierarchy. */
    private fun createNestedClasses(
        classItem: DefaultClassItem,
        nestedClasses: ImmutableList<ClassSymbol>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        for (nestedClassSymbol in nestedClasses) {
            createClass(
                classSymbol = nestedClassSymbol,
                containingClassItem = classItem,
                enclosingClassTypeItemFactory = enclosingClassTypeItemFactory,
                origin = classItem.origin,
            )
        }
    }

    /** This methods creates and sets the fields of a class */
    private fun createFields(
        classItem: DefaultClassItem,
        fields: ImmutableList<FieldInfo>,
        typeItemFactory: TurbineTypeItemFactory,
    ) {
        for (field in fields) {
            val flags = field.access()
            val decl = field.decl()
            val fieldModifierItem =
                createModifiers(
                    flags,
                    field.annotations(),
                )
            val isEnumConstant = (flags and TurbineFlag.ACC_ENUM) != 0
            val fieldValue = createInitialValue(field)
            val type =
                typeItemFactory.getFieldType(
                    underlyingType = field.type(),
                    itemAnnotations = fieldModifierItem.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = fieldModifierItem.isFinal(),
                    isInitialValueNonNull = {
                        // The initial value is non-null if the value is a literal which is not
                        // null.
                        fieldValue.initialValue(false) != null
                    }
                )

            val documentation = javadoc(decl)
            val fieldItem =
                itemFactory.createFieldItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = fieldModifierItem,
                    documentationFactory = getCommentedDoc(documentation),
                    name = field.name(),
                    containingClass = classItem,
                    type = type,
                    isEnumConstant = isEnumConstant,
                    fieldValue = fieldValue,
                )

            classItem.addField(fieldItem)
        }
    }

    private fun createMethods(
        classItem: DefaultClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        for (method in methods) {
            // Ignore constructors.
            if (method.sym().name() == "<init>") continue

            val decl: MethDecl? = method.decl()
            val methodModifierItem =
                createModifiers(
                    method.access(),
                    method.annotations(),
                )
            val name = method.name()
            val (typeParams, methodTypeItemFactory) =
                createTypeParameters(
                    method.tyParams(),
                    enclosingClassTypeItemFactory,
                    name,
                )
            val documentation = javadoc(decl)
            val defaultValueExpr = getAnnotationDefaultExpression(method)
            val defaultValue =
                if (method.defaultValue() != null)
                    TurbineValue(method.defaultValue()!!, defaultValueExpr)
                        .getSourceForMethodDefault()
                else ""

            val parameters = method.parameters()
            val fingerprint = MethodFingerprint(name, parameters.size)
            val isAnnotationElement = classItem.isAnnotationType() && !methodModifierItem.isStatic()
            val returnType =
                methodTypeItemFactory.getMethodReturnType(
                    underlyingReturnType = method.returnType(),
                    itemAnnotations = methodModifierItem.annotations(),
                    fingerprint = fingerprint,
                    isAnnotationElement = isAnnotationElement,
                )

            val methodItem =
                itemFactory.createMethodItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = methodModifierItem,
                    documentationFactory = getCommentedDoc(documentation),
                    name = name,
                    containingClass = classItem,
                    typeParameterList = typeParams,
                    returnType = returnType,
                    parameterItemsFactory = { containingCallable ->
                        createParameters(
                            containingCallable,
                            decl?.params(),
                            parameters,
                            methodTypeItemFactory,
                        )
                    },
                    throwsTypes = getThrowsList(method.exceptions(), methodTypeItemFactory),
                    annotationDefault = defaultValue,
                )

            // Ignore enum synthetic methods.
            if (methodItem.isEnumSyntheticMethod()) continue

            classItem.addMethod(methodItem)
        }
    }

    private fun createParameters(
        containingCallable: CallableItem,
        parameterDecls: List<VarDecl>?,
        parameters: List<ParamInfo>,
        typeItemFactory: TurbineTypeItemFactory,
    ): List<ParameterItem> {
        val fingerprint = MethodFingerprint(containingCallable.name(), parameters.size)
        // Some parameters in [parameters] are implicit parameters that do not have a corresponding
        // entry in the [parameterDecls] list. The number of implicit parameters is the total
        // number of [parameters] minus the number of declared parameters [parameterDecls]. The
        // implicit parameters are always at the beginning so the offset from the declared parameter
        // in [parameterDecls] to the corresponding parameter in [parameters] is simply the number
        // of the implicit parameters.
        val declaredParameterOffset = parameters.size - (parameterDecls?.size ?: 0)
        return parameters.mapIndexed { idx, parameter ->
            val parameterModifierItem =
                createModifiers(parameter.access(), parameter.annotations()).toImmutable()
            val type =
                typeItemFactory.getMethodParameterType(
                    underlyingParameterType = parameter.type(),
                    itemAnnotations = parameterModifierItem.annotations(),
                    fingerprint = fingerprint,
                    parameterIndex = idx,
                    isVarArg = parameterModifierItem.isVarArg(),
                )
            // Get the [Tree.VarDecl] corresponding to the [ParamInfo], if available.
            val decl =
                if (parameterDecls != null && idx >= declaredParameterOffset)
                    parameterDecls.get(idx - declaredParameterOffset)
                else null

            val fileLocation =
                TurbineFileLocation.forTree(containingCallable.containingClass(), decl)
            val parameterItem =
                itemFactory.createParameterItem(
                    fileLocation = fileLocation,
                    modifiers = parameterModifierItem,
                    name = parameter.name(),
                    publicNameProvider = { null },
                    containingCallable = containingCallable,
                    parameterIndex = idx,
                    type = type,
                    defaultValueFactory = { DefaultValue.NONE },
                )
            parameterItem
        }
    }

    private fun createConstructors(
        classItem: DefaultClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        for (constructor in methods) {
            // Skip real methods.
            if (constructor.sym().name() != "<init>") continue

            val decl: MethDecl? = constructor.decl()
            val constructorModifierItem =
                createModifiers(
                    constructor.access(),
                    constructor.annotations(),
                )
            val (typeParams, constructorTypeItemFactory) =
                createTypeParameters(
                    constructor.tyParams(),
                    enclosingClassTypeItemFactory,
                    constructor.name(),
                )
            val isImplicitDefaultConstructor =
                (constructor.access() and TurbineFlag.ACC_SYNTH_CTOR) != 0
            val name = classItem.simpleName()
            val documentation = javadoc(decl)
            val constructorItem =
                itemFactory.createConstructorItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = constructorModifierItem,
                    documentationFactory = getCommentedDoc(documentation),
                    // Turbine's Binder gives return type of constructors as void but the
                    // model expects it to the type of object being created. So, use the
                    // containing [ClassItem]'s type as the constructor return type.
                    name = name,
                    containingClass = classItem,
                    typeParameterList = typeParams,
                    returnType = classItem.type(),
                    parameterItemsFactory = { constructorItem ->
                        createParameters(
                            constructorItem,
                            decl?.params(),
                            constructor.parameters(),
                            constructorTypeItemFactory,
                        )
                    },
                    throwsTypes =
                        getThrowsList(constructor.exceptions(), constructorTypeItemFactory),
                    implicitConstructor = isImplicitDefaultConstructor,
                )

            classItem.addConstructor(constructorItem)
        }
    }

    /**
     * Get the ClassSymbol corresponding to a qualified name. Since the Turbine's lookup method
     * returns only top-level classes, this method will return the ClassSymbol of outermost class
     * for nested classes.
     */
    private fun getClassSymbol(name: String): ClassSymbol? {
        val result = index.scope().lookup(createLookupKey(name))
        return result?.let { it.sym() as ClassSymbol }
    }

    /** Creates a LookupKey from a given name */
    private fun createLookupKey(name: String): LookupKey {
        val idents = name.split(".").mapIndexed { idx, it -> Ident(idx, it) }
        return LookupKey(ImmutableList.copyOf(idents))
    }

    private fun javadoc(item: TyDecl?): String {
        if (!allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun javadoc(item: VarDecl?): String {
        if (!allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun javadoc(item: MethDecl?): String {
        if (!allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun getThrowsList(
        throwsTypes: List<Type>,
        enclosingTypeItemFactory: TurbineTypeItemFactory
    ): List<ExceptionTypeItem> {
        return throwsTypes.map { type -> enclosingTypeItemFactory.getExceptionType(type) }
    }

    private fun getCommentedDoc(doc: String): ItemDocumentationFactory {
        return buildString {
                if (doc != "") {
                    append("/**")
                    append(doc)
                    append("*/")
                }
            }
            .toItemDocumentationFactory()
    }

    private fun createInitialValue(field: FieldInfo): FieldValue {
        val optExpr = field.decl()?.init()
        val expr = if (optExpr != null && optExpr.isPresent()) optExpr.get() else null
        val constantValue = field.value()?.getValue()

        val initialValueWithoutRequiredConstant =
            when {
                constantValue != null -> constantValue
                expr == null -> null
                else ->
                    when (expr.kind()) {
                        Tree.Kind.LITERAL -> {
                            (expr as Literal).value().underlyingValue
                        }
                        // Class Type
                        Tree.Kind.CLASS_LITERAL -> {
                            expr
                        }
                        else -> {
                            null
                        }
                    }
            }

        return FixedFieldValue(constantValue, initialValueWithoutRequiredConstant)
    }

    /**
     * Extracts the expression corresponding to the default value of a given annotation method. If
     * the method does not have a default value, returns null.
     */
    private fun getAnnotationDefaultExpression(method: MethodInfo) =
        method.decl()?.defaultValue()?.orElse(null)?.let { defaultTree ->

            // Turbine stores the default value as a Tree not an Expression so that it can use an
            // Anno class (which is not an Expression). It could wrap the Anno in an AnnoExpr but
            // does not, presumably as an optimization. However, this does wrap it in an AnnoExpr
            // as it allows for more consistent handling.
            when (defaultTree) {
                is Expression -> defaultTree
                is Anno -> AnnoExpr(defaultTree.position(), defaultTree)
                else -> error("unknown default value type (${defaultTree.javaClass}: $defaultTree")
            }
        }

    internal fun getTypeElement(name: String): TypeElement? = turbineElements.getTypeElement(name)
}

/** Create a [SourceFile] for every `.java` file in [sources]. */
private fun getSourceFiles(sources: Sequence<File>): List<SourceFile> {
    return sources
        .filter { it.isFile && it.extension == "java" } // Ensure only Java files are included
        .map { SourceFile(it.path, it.readText()) }
        .toList()
}

private const val ACC_PUBLIC_OR_PROTECTED = TurbineFlag.ACC_PUBLIC or TurbineFlag.ACC_PROTECTED

/** Check whether the [TypeBoundClass] is accessible. */
private val TypeBoundClass.isAccessible: Boolean
    get() {
        val flags = access()
        return flags and ACC_PUBLIC_OR_PROTECTED != 0
    }
