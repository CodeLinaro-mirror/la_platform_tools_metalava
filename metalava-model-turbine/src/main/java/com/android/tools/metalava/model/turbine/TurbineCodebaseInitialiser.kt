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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.item.DefaultCodebaseFactory
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.source.SourceCodebaseAssembler
import com.android.tools.metalava.model.source.SourcePackageInfo
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bytecode.BytecodeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.SimpleEnv
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.TopLevelIndex
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.diag.SourceFile
import com.google.turbine.diag.TurbineDiagnostic
import com.google.turbine.diag.TurbineError
import com.google.turbine.diag.TurbineLog
import com.google.turbine.model.TurbineFlag
import com.google.turbine.parse.Parser
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.type.AnnoInfo
import java.io.File
import java.nio.file.Paths
import java.util.Optional
import javax.lang.model.SourceVersion

/**
 * This initializer acts as an adapter between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
internal class TurbineCodebaseInitialiser(
    codebaseFactory: DefaultCodebaseFactory,
    private val classpath: List<File>,
) : SourceCodebaseAssembler(), TurbineGlobalContext {

    override val codebase = codebaseFactory(this)

    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /**
     * Map between ClassSymbols and TurbineClass for classes present on the source path or the class
     * path
     */
    private lateinit var envClassMap: CompoundEnv<ClassSymbol, TypeBoundClass>

    private lateinit var index: TopLevelIndex

    /** Caches [TurbineSourceFile] instances. */
    override lateinit var sourceFileCache: TurbineSourceFileCache

    /** Factory for creating [AnnotationItem]s from [AnnoInfo]s. */
    override lateinit var annotationFactory: TurbineAnnotationFactory

    /** Global [TurbineTypeItemFactory] from which all other instances are created. */
    override lateinit var globalTypeItemFactory: TurbineTypeItemFactory

    /** Creates [Item] instances for [codebase]. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Turbine can only process java files.
            defaultSourceLanguage = SourceLanguage.JAVA,
            // Source files need to track which parts belong to which API surface variants, so they
            // need to create an ApiVariantSelectors instance that can be used to track that.
            defaultVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        )

    override lateinit var valueFactory: TurbineValueFactory

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
        // Any non-fatal error (like unresolved symbols) will be captured in this log and will
        // be handled below.
        val log = TurbineLog()

        // Get the units from the source files provided on the command line.
        val commandLineSources = sourceSet.sources
        val sourceFiles = getSourceFiles(commandLineSources.asSequence())
        val units = sourceFiles.mapNotNull { parse(log, it) }

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
                .mapNotNull { parse(log, it) }
                // Ignore any files that contain duplicates of a class that was specified on the
                // command line. This is needed when merging annotations from other java files as
                // there may be duplicate definitions of the class on the source path.
                .filter { unit -> unit.mainClassQualifiedName !in commandLineClasses }

        // If any errors were reported during parsing then report them and abort.
        if (log.anyErrors()) {
            log.reportTo(codebase.reporter)
            throw TurbineError(ImmutableList.of())
        }

        // Combine all the units together.
        val allUnits = ImmutableList.builder<CompUnit>().addAll(units).addAll(extraUnits).build()

        try {
            // No annotation processors are used.
            val annotationProcessorInfo =
                ProcessorInfo.create(
                    ImmutableList.of(),
                    null,
                    ImmutableMap.of(),
                    SourceVersion.latest()
                )

            // Bind the units
            bindingResult =
                Binder.bind(
                    log,
                    allUnits,
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    annotationProcessorInfo,
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
        } catch (e: TurbineError) {
            // Catch the [TurbineError] and extract its diagnostics. An exception will be rethrown
            // below after reporting the diagnostics because [bindingResult] will not have been set.
            e.logAllDiagnostics(log)
        }

        // Report all the diagnostics, filtering those that relate to missing references.
        log.reportTo(codebase.reporter) { diagnostic ->
            // Ignore missing references.
            val errorKind = diagnostic.kind()
            when (errorKind) {
                TurbineError.ErrorKind.CANNOT_RESOLVE,
                TurbineError.ErrorKind.CANNOT_RESOLVE_FIELD,
                TurbineError.ErrorKind.EXPRESSION_ERROR,
                TurbineError.ErrorKind.SYMBOL_NOT_FOUND -> {
                    false
                }
                else -> true
            }
        }

        // Check to make sure that the binding was not aborted, if it was then abort this
        // processing.
        if (!::bindingResult.isInitialized) {
            throw TurbineError(ImmutableList.of())
        }

        // Get the top level index needed for creating TurbineElements.
        index = bindingResult.tli()

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

        // Create a cache from SourceFile to the TurbineSourceFile wrapper. The latter needs the
        // CompUnit associated with the SourceFile so pass in all the CompUnits so it can find it.
        sourceFileCache = TurbineSourceFileCache(codebase, allUnits)

        // Create the TurbineValueProviderFactory
        valueFactory = TurbineValueFactory(this)

        // Create a factory for creating annotations from AnnoInfo.
        annotationFactory = TurbineAnnotationFactory(this)

        // Create the global TurbineTypeItemFactory.
        globalTypeItemFactory =
            TurbineTypeItemFactory(this, annotationFactory, TypeParameterScope.empty)

        // Get the map from ClassSymbol to SourceTypeBoundClass for only those classes provided on
        // the command line as only those classes can contribute directly to the API.
        val commandLineSourceClasses =
            topLevelAccessibleCommandLineClasses(allSourceClassMap, commandLineSources)

        // Scan the files looking for package.html and overview.html files and extract the
        // documentation just in case they are needed during package creation.
        createInitialPackages(sourceSet)

        createAllCommandLineClasses(commandLineSourceClasses, apiPackages)
    }

    /**
     * Parse [sourceFile] and return the [CompUnit].
     *
     * If [Parser.parse] throws a [TurbineError] then add any diagnostics from that to [log] and
     * return `null`.
     */
    private fun parse(log: TurbineLog, sourceFile: SourceFile): CompUnit? =
        try {
            Parser.parse(sourceFile)
        } catch (e: TurbineError) {
            e.logAllDiagnostics(log)
            null
        }

    private fun TurbineError.logAllDiagnostics(log: TurbineLog) {
        for (diagnostic in diagnostics()) {
            log.add(diagnostic)
        }
    }

    /** Report all the diagnostics in this [TurbineLog], if any, to [reporter]. */
    private fun TurbineLog.reportTo(
        reporter: Reporter,
        predicate: (TurbineDiagnostic) -> Boolean = { true }
    ) {
        for (diagnostic in diagnostics()) {
            // Ignore any that do not match the predicate.
            if (!predicate(diagnostic)) continue

            val path = diagnostic.path()
            val location =
                FileLocation.createLocation(
                    Paths.get(path),
                    line = diagnostic.line(),
                    characterPosition = diagnostic.column()
                )
            reporter.report(Issues.INVALID_SYNTAX, null, diagnostic.message(), location)
        }
        clear()
    }

    /**
     * Compute the set of accessible, top level classes that were specified on the command line.
     *
     * @param allSourceClasses all the [SourceTypeBoundClass]s found during binding, includes those
     *   from the source path as well as those whose containing file was provided on the command
     *   line. Also, includes `package-info.java` classes.
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
        return allSourceClasses.filter { (symbol, sourceTypeBoundClass) ->
            // Ignore all `package-info.java` classes.
            if (symbol.simpleName() == "package-info") return@filter false

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
     * then look in the class path.
     */
    override fun typeBoundClassForSymbol(classSymbol: ClassSymbol): TypeBoundClass? =
        envClassMap.get(classSymbol)

    /**
     * Convert this qualified name consisting of a list of identifiers separated by '.' into a list
     * of identifiers.
     *
     * The empty string is converted to an empty list, otherwise it is just split on '.'.
     */
    private fun String.qualifiedNameToIdentifierList() = if (isEmpty()) emptyList() else split('.')

    override fun getPackageInfoFromSource(packageName: String): SourcePackageInfo? {
        // Make sure that the underlying package exists.
        if (!isValidPackage(packageName)) {
            if (packageName == "") return null else error("Unknown package '$packageName'")
        }

        // Construct the binary name for the package-info class.
        val packageInfoBinaryName = "${packageName.replace('.', '/')}/package-info"

        // The underlying package may have annotations if it had a package-info.java file so check
        // for the presence of the corresponding `package-info.class`.
        val packageInfoSym = ClassSymbol(packageInfoBinaryName)
        val packageInfoClass = envClassMap[packageInfoSym] ?: return null

        // Create a FieldResolver to use to resolve field references in package annotations.
        val fieldResolver = createFieldResolver(packageInfoSym, packageInfoClass)

        return when (packageInfoClass) {
            // Handle a package-info.java file.
            is SourceTypeBoundClass -> {
                val turbineSourceFile = sourceFileCache.turbineSourceFile(packageInfoClass.source())
                val unit = turbineSourceFile.compUnit
                val pkgDecl = unit.pkg().get()
                val annoInfos = packageInfoClass.annotations()
                SourcePackageInfo(
                    sourceFile = turbineSourceFile,
                    annotations = annotationFactory.createAnnotations(annoInfos, fieldResolver),
                    commentFactory = itemDocumentationFactoryForDecl(turbineSourceFile, pkgDecl),
                )
            }
            // Handle a package-info.class file.
            is BytecodeBoundClass -> {
                val annoInfos = packageInfoClass.annotations()
                val annotations = annotationFactory.createAnnotations(annoInfos, fieldResolver)
                SourcePackageInfo(annotations = annotations)
            }
            else -> error("Unknown package-info class: $packageInfoClass")
        }
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
                    typeBoundClass = sourceBoundClass,
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
        typeBoundClass: TypeBoundClass,
        origin: ClassOrigin,
    ): ClassItem {
        if (!classSymbol.isTopClass) error("$classSymbol is not a top level class")
        val classBuilder =
            TurbineClassBuilder(
                globalContext = this,
                classSymbol = classSymbol,
                typeBoundClass = typeBoundClass,
                origin = origin,
            )
        return classBuilder.createClass(
            containingClassItem = null,
            enclosingClassTypeItemFactory = globalTypeItemFactory,
        )
    }

    override fun isValidPackage(packageName: String) =
        index.lookupPackage(packageName.qualifiedNameToIdentifierList()) != null

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
                    val typeBoundClass =
                        typeBoundClassForSymbol(topClassSym)
                            ?: error("Cannot find type bound class for top class $topClassSym")
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

    override fun createFieldResolver(
        classSymbol: ClassSymbol,
        typeBoundClass: TypeBoundClass,
    ): FieldResolver? =
        when (typeBoundClass) {
            is SourceTypeBoundClass ->
                TurbineFieldResolver(
                    classSymbol,
                    classSymbol,
                    typeBoundClass.memberImports(),
                    typeBoundClass.scope(),
                    envClassMap,
                )
            else -> null
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
