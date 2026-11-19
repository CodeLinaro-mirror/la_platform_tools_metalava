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

import androidx.tracing.Tracer
import com.android.tools.metalava.model.AnnotationItem
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
import com.google.common.collect.ImmutableList
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bytecode.BytecodeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.SimpleEnv
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.TopLevelIndex
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.type.AnnoInfo
import java.io.File

/**
 * This initializer acts as an adapter between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
internal class TurbineCodebaseInitialiser(
    codebaseFactory: DefaultCodebaseFactory,
) : SourceCodebaseAssembler(), TurbineGlobalContext {

    override val codebase = codebaseFactory(this)

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
        )

    override lateinit var valueFactory: TurbineValueFactory

    /**
     * Populates [codebase] from the [boundSources].
     *
     * Creates the packages, classes and their members, as well as sets up various class hierarchies
     * using the binder's output.
     */
    fun initialize(
        boundSources: BoundSources,
        apiPackages: PackageFilter?,
        tracer: Tracer,
    ) {
        val sourceSet = boundSources.sourceSet
        val allUnits = boundSources.allUnits
        val bindingResult = boundSources.bindingResult

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
        sourceFileCache =
            tracer.trace("turbine.createSourceFileCache") {
                TurbineSourceFileCache(codebase, allUnits)
            }

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
            tracer.trace("turbine.topLevelAccessibleCommandLineClasses") {
                topLevelAccessibleCommandLineClasses(allSourceClassMap, sourceSet.sources)
            }

        // Scan the files looking for package.html and overview.html files and extract the
        // documentation just in case they are needed during package creation.
        tracer.trace("turbine.createInitialPackages") {
            createInitialPackages(sourceSet, apiPackages)
        }

        tracer.trace("turbine.createAllCommandLineClasses") {
            createAllCommandLineClasses(commandLineSourceClasses, apiPackages)
        }

        // Copy type use only nullness annotations to items.
        tracer.trace("turbine.copyTypeUseOnlyNullnessAnnotationsToItems") {
            copyTypeUseOnlyNullnessAnnotationsToItems()
        }
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

            // Ignore classes whose paths were not specified on the command line.
            val path = sourceTypeBoundClass.source().path()
            path in commandLinePaths
        }
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
                    commentFactory = itemDocumentationFactoryForDecl(pkgDecl),
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
