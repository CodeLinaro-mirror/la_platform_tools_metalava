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

package com.android.tools.metalava.model.psi

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.annotations.Extractor
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.psi.kotlin.KaCodebaseAssembler
import com.android.tools.metalava.model.source.SourceCodebaseAssembler
import com.android.tools.metalava.model.source.SourcePackageInfo
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.Issues
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.collections.forEach
import kotlin.collections.set
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade

internal class PsiCodebaseAssembler(
    private val uastEnvironment: UastEnvironment,
    codebaseFactory: (PsiCodebaseAssembler) -> PsiBasedCodebase
) : SourceCodebaseAssembler(), PsiGlobalContext {

    override val psiCodebase = codebaseFactory(this)

    override val codebase: DefaultCodebase
        get() = psiCodebase

    override val itemFactory: DefaultItemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Psi can process Java and Kotlin so use unknown as the default.
            defaultSourceLanguage = SourceLanguage.UNKNOWN,
            // Source files need to track which parts belong to which API surface variants, so they
            // need to create an ApiVariantSelectors instance that can be used to track that.
            defaultVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        )

    override val globalTypeItemFactory = PsiTypeItemFactory(this, TypeParameterScope.empty)

    internal val project: Project = uastEnvironment.ideaProject

    private val projectSearchScope = GlobalSearchScope.allScope(project)

    private val reporter
        get() = codebase.reporter

    /** Provides an interface for using the Kotlin analysis API. */
    private var kaCodebaseAssembler: KaCodebaseAssembler? = null

    /**
     * Map from qualified class name to the heavyweight [PsiClass] implementations corresponding to
     * a source class.
     *
     * Psi can represent classes with a number of different implementations of [PsiClass] that have
     * different capabilities and provide different, and inconsistent, information. This keeps track
     * of the heavyweight [PsiClass] implementations for source classes which do not contribute
     * directly to an API surface (and so do not have a [ClassItem] created in the initialization of
     * the [PsiBasedCodebase]) but which may contribute indirectly, e.g. through inherited methods.
     * If a [ClassItem] needs to be created during processing, e.g. because it is a super type, then
     * the [PsiClass] corresponding to it will be removed from this map (if it exists) and used. If
     * it does not exist then it will be looked up using [JavaPsiFacade].
     */
    private val deferredHeavyweightPsiClasses = mutableMapOf<String, PsiClass>()

    /** If [PsiSourceParser.mergeFromJar] is used, this is the environment used to load the jar. */
    var mergedJarEnvironment: UastEnvironment? = null

    fun dispose() {
        uastEnvironment.dispose()
        mergedJarEnvironment?.dispose()
    }

    private fun getFactory() = JavaPsiFacade.getElementFactory(project)

    override fun getClassType(psiClass: PsiClass) =
        getFactory().createType(psiClass, PsiSubstitutor.EMPTY)

    override fun createPsiType(sourceType: String, context: PsiElement?) =
        getFactory().createTypeFromText(sourceType, context)

    override fun findPsiPackage(packageName: String) =
        JavaPsiFacade.getInstance(project).findPackage(packageName)

    override fun getPackageInfoFromSource(packageName: String): SourcePackageInfo? {
        // The root package can never have a corresponding package-info.java and so cannot have
        // any annotations or documentation so return immediately.
        if (packageName == "") {
            return null
        }

        val psiPackage = findPsiPackage(packageName) ?: return null
        val annotations = PsiModifierItem.create(psiCodebase, psiPackage).annotations()

        // Try and find a package-info.java file for the package in the project files.
        val psiJavaFile =
            psiPackage.getFiles(projectSearchScope).find {
                // Make sure that the file is a PsiJavaFile with the correct package name.
                it is PsiJavaFile && it.name == JAVA_PACKAGE_INFO && it.packageName == packageName
            } as? PsiJavaFile

        return if (psiJavaFile == null) {
            SourcePackageInfo(
                annotations = annotations,
            )
        } else {
            val documentationFactory =
                psiJavaFile.packageStatement?.let { it.createItemDocumentation(psiCodebase) }
            val sourceFile = PsiSourceFile(psiCodebase, psiJavaFile)
            SourcePackageInfo(
                sourceFile = sourceFile,
                annotations = annotations,
                commentFactory = documentationFactory,
            )
        }
    }

    override fun isValidPackage(packageName: String) = findPsiPackage(packageName) != null

    override fun createClassFromUnderlyingModel(qualifiedName: String) =
        findOrCreateClass(qualifiedName)

    /** Check if the [BaseModifierList] is accsssible. */
    private val BaseModifierList.hasApiVisibilityOrShowAnnotation
        get() =
            when (getVisibilityLevel()) {
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PROTECTED -> true
                VisibilityLevel.INTERNAL -> annotations().any { it.showability.show() }
                else -> false
            }

    /**
     * Create a possible API class, i.e. a class that has a possibility of being part of an API
     * surface.
     *
     * This will ignore any class that is inaccessible as it cannot be part of the API. A
     * [ClassItem] may be created for it later if needed, e.g. if it is a super class of an
     * accessible class.
     */
    private fun createPossibleApiClass(
        psiClass: PsiClass,
        origin: ClassOrigin,
    ): ClassItem? {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")

        // Ignore inaccessible classes.
        val modifiers = PsiModifierItem.create(psiCodebase, psiClass)
        if (!modifiers.hasApiVisibilityOrShowAnnotation) {
            deferredHeavyweightPsiClasses[psiClass.qualifiedName!!] = psiClass
            return null
        }

        return createTopLevelClassAndContents(psiClass, origin, modifiers)
    }

    /** Create a top level class, their inner classes and all the other members. */
    private fun createTopLevelClassAndContents(
        psiClass: PsiClass,
        origin: ClassOrigin,
        modifiers: MutableModifierList = PsiModifierItem.create(psiCodebase, psiClass),
    ): SkeletonClassItem {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")
        return createClass(
            psiClass,
            null,
            globalTypeItemFactory,
            origin,
            modifiers = modifiers,
        )
    }

    private fun createClass(
        psiClass: PsiClass,
        containingClassItem: ClassItem?,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        origin: ClassOrigin,
        modifiers: MutableModifierList = PsiModifierItem.create(psiCodebase, psiClass),
    ): SkeletonClassItem {
        val builder =
            PsiClassBuilder(
                globalContext = this,
                psiClass,
                origin,
            )
        return builder.createClass(
            containingClassItem,
            enclosingClassTypeItemFactory,
            modifiers,
        )
    }

    private fun findOrCreateClass(qualifiedName: String): ClassItem? {
        // Check to see if the class has already been seen and if so return it immediately.
        codebase.findClass(qualifiedName)?.let {
            return it
        }

        return findPsiClass(qualifiedName)?.let {
            // Remove it, if it was a heavyweight PsiClass.
            deferredHeavyweightPsiClasses.remove(qualifiedName)
            findOrCreateClass(it)
        }
    }

    internal fun findPsiClass(qualifiedName: String): PsiClass? {
        // Return a heavyweight PsiClass, if available.
        deferredHeavyweightPsiClasses[qualifiedName]?.let {
            return it
        }

        // The following cannot find a class whose name does not correspond to the file name, e.g.
        // in Java a class that is a second top level class.
        val finder = JavaPsiFacade.getInstance(project)
        // When working with a multiplatform project, perform the class search in a limited scope
        // from the `kaCodebaseAssembler`, which is important for multiplatform projects to avoid
        // searching the classpath of unrelated modules.
        return if (uastEnvironment.isKMP && kaCodebaseAssembler != null) {
            kaCodebaseAssembler!!.findClassInModule(finder, qualifiedName)
        } else {
            finder.findClass(qualifiedName, projectSearchScope)
        }
    }

    /**
     * Identifies a point in the [ClassItem] nesting structure where new [ClassItem]s need
     * inserting.
     */
    data class NewClassInsertionPoint(
        /**
         * The [PsiClass] that is the root of the nested classes that need creation, is a top level
         * class if [containingClassItem] is `null`.
         */
        val missingPsiClass: PsiClass,

        /** The containing class item, or `null` if the top level. */
        val containingClassItem: ClassItem?,
    )

    /**
     * Called when no [ClassItem] was found by [PsiBasedCodebase.findClass]`([PsiClass]) when called
     * on [psiClass].
     *
     * The purpose of this is to find where a new [ClassItem] should be inserted in the nested class
     * structure. It finds the outermost [PsiClass] with no associated [ClassItem] but which is
     * either a top level class or whose containing [PsiClass] does have an associated [ClassItem].
     * That is the point where new classes need to be created.
     *
     * e.g. if the nesting structure is `A.B.C` and `A` has already been created then the insertion
     * point would consist of [ClassItem] for `A` (the containing class item) and the [PsiClass] for
     * `B` (the outermost [PsiClass] with no associated item).
     *
     * If none had already been created then it would return an insertion point consisting of no
     * containing class item and the [PsiClass] for `A`.
     */
    private fun findNewClassInsertionPoint(psiClass: PsiClass): NewClassInsertionPoint {
        var current = psiClass
        do {
            // If the current has no containing class then it has reached the top level class so
            // return an insertion point that has no containing class item and the current class.
            val containing = current.containingClass ?: return NewClassInsertionPoint(current, null)

            // If the containing class has a matching class item then return an insertion point that
            // uses that containing class item and the current class.
            psiCodebase.findClass(containing)?.let { containingClassItem ->
                return NewClassInsertionPoint(current, containingClassItem)
            }
            current = containing
        } while (true)
    }

    internal fun findOrCreateClass(psiClass: PsiClass): ClassItem {
        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; call findOrCreateTypeParameter(...) instead"
            )
        }

        // If it has already been created then return it.
        psiCodebase.findClass(psiClass)?.let {
            return it
        }

        // Otherwise, find an insertion point at which new classes should be created.
        val (missingPsiClass, containingClassItem) = findNewClassInsertionPoint(psiClass)

        // Create a top level or nested class as appropriate.
        val createdClassItem =
            if (containingClassItem == null) {
                // Try and determine the origin of the class.
                val containingFile = missingPsiClass.containingFile
                val origin =
                    if (containingFile == null || containingFile.name.endsWith(".class"))
                        ClassOrigin.CLASS_PATH
                    else ClassOrigin.SOURCE_PATH

                createTopLevelClassAndContents(
                    missingPsiClass,
                    origin,
                )
            } else {
                createClass(
                    missingPsiClass,
                    containingClassItem,
                    globalTypeItemFactory.from(containingClassItem),
                    origin = containingClassItem.origin,
                )
            }

        // Add any Kotlin properties to the class.
        kaCodebaseAssembler?.addPropertiesToClassFromClasspath(createdClassItem)

        // Select the class item to return.
        return if (missingPsiClass == psiClass) {
            // The created class item was what was requested so just return it.
            createdClassItem
        } else {
            // Otherwise, a nested class was requested so find it. It was created when its
            // containing class was created.
            psiCodebase.findClass(psiClass)!!
        }
    }

    internal fun initializeFromJar(jarFile: File) {
        // Extract the list of class names from the jar file.
        val classNames = buildList {
            try {
                ZipFile(jarFile).use { jar ->
                    for (entry in jar.entries().iterator()) {
                        val fileName = entry.name
                        if (fileName.contains("$")) {
                            // skip inner classes
                            continue
                        }
                        if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
                            // skip entries that are not .class files.
                            continue
                        }

                        val qualifiedName =
                            fileName.removeSuffix(SdkConstants.DOT_CLASS).replace('/', '.')
                        if (qualifiedName.endsWith(".package-info")) {
                            // skip package-info files.
                            continue
                        }

                        add(qualifiedName)
                    }
                }
            } catch (e: IOException) {
                reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
            }
        }

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val isFromClassPath = codebase.isFromClassPath()
        val origin = if (isFromClassPath) ClassOrigin.CLASS_PATH else ClassOrigin.COMMAND_LINE
        for (className in classNames) {
            val psiClass = facade.findClass(className, scope) ?: continue

            val classItem = createPossibleApiClass(psiClass, origin) ?: continue
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    /** Lists all packages in the psi project. */
    private fun allPackages(): Set<String> {
        fun listPackages(psiPackage: PsiPackage): List<String> {
            return listOf(psiPackage.qualifiedName) +
                psiPackage.subPackages.flatMap { listPackages(it) }
        }
        val rootPackage = findPsiPackage("") ?: return emptySet()
        return listPackages(rootPackage).toSet()
    }

    internal fun initializeFromSources(
        sourceSet: SourceSet,
        apiPackages: PackageFilter?,
    ) {
        // Get the list of `PsiFile`s from the `SourceSet`.
        val psiFiles = Extractor.createUnitsForFiles(uastEnvironment.ideaProject, sourceSet.sources)

        // Get the `PsiClass`es from the `PsiFile`s.
        val psiClasses = getPsiClassesFromPsiFiles(psiFiles)

        // Create the initial set of packages that were found in the source files.
        createInitialPackages(sourceSet)

        // Add type aliases.
        val kotlinFiles = psiFiles.filterIsInstance<KtFile>()
        kaCodebaseAssembler =
            psiCodebase.mainAnalysisModule?.let { KaCodebaseAssembler(kotlinFiles, psiCodebase) }
        kaCodebaseAssembler?.let { kaCodebaseAssembler ->
            // Provide a list of all packages when all typealiases are needed in order to inline
            // usages. If that isn't necessary, just typealiases from source will be processed.
            val allPackages =
                if (psiCodebase.inlineTypeAliasUsages) {
                    allPackages()
                } else {
                    null
                }
            kaCodebaseAssembler.createTypeAliases(allPackages)
        }

        // Tracker for which source files of `@JvmMultifileClass`es have already been processed.
        val multiFileClasses = HashMap<FqName, Set<PsiFile>>()
        // Process the `PsiClass`es.
        for (psiClass in psiClasses) {
            initializeClassFromSources(psiClass, multiFileClasses, apiPackages)
        }

        // Determining sealed class exhaustivity is done here because it requires looking at
        // classes that are private and won't be turned into ClassItems, and these classes are
        // only all available here during codebase assembly. Doing this at a later stage (for
        // example in ApiAnalyzer) wouldn't be possible because non-visible classes are no longer
        // accessible from there.
        determineIfInaccessibleClassesMakeSuperClassesNonExhaustive(psiClasses)

        // Add kotlin-only APIs.
        kaCodebaseAssembler?.assemble()
    }

    // Instances of sealed classes can be matched using `when` statements. If all the subclasses
    // of a sealed class are available to API consumers, then new subclasses can't be added
    // to the sealed class because doing so would be a breaking change (clients' `when`
    // statements would no longer be exhaustive). In this case, we label the sealed class as
    // exhaustive. If there is an inaccessible class that extends a sealed class, however, then
    // the sealed class is not exhaustive. For more details, see b/447143803
    private fun determineIfInaccessibleClassesMakeSuperClassesNonExhaustive(
        psiClasses: List<PsiClass>
    ) {
        psiClasses.forEach { psiClass -> sealedClassExhaustivityHelper(psiClass, false) }
    }

    /**
     * Recursively traverses the inner classes of [psiClass] to determine if any sealed super
     * classes should be marked as non-exhaustive.
     *
     * A sealed class is considered non-exhaustive if it has at least one inaccessible subclass.
     *
     * @param psiClass The current [PsiClass] being checked.
     * @param parentWasNotVisible True if any containing class of [psiClass] was not visible.
     */
    private fun sealedClassExhaustivityHelper(
        psiClass: PsiClass,
        parentWasNotVisible: Boolean,
    ) {
        val qualifiedName = psiClass.qualifiedName
        if (qualifiedName != null) {

            // If a ClassItem already exists for this psiClass, use its modifiers. Otherwise, create
            // new ones.
            val modifiers =
                psiCodebase.findClass(psiClass)?.modifiers
                    ?: PsiModifierItem.create(psiCodebase, psiClass)
            val curClassNotVisible =
                modifiers.annotations().any { it.showability.hide() } ||
                    !modifiers.hasApiVisibilityOrShowAnnotation

            if (curClassNotVisible || parentWasNotVisible) {
                val superClassName = psiClass.superClass?.qualifiedName
                if (superClassName != null) {
                    codebase.findClass(superClassName)?.mutateModifiers { setExhaustive(false) }
                }
                psiClass.interfaces
                    .mapNotNull { it?.qualifiedName }
                    .forEach { name ->
                        codebase.findClass(name)?.mutateModifiers { setExhaustive(false) }
                    }
            }

            psiClass.innerClasses.forEach { innerClass ->
                sealedClassExhaustivityHelper(
                    innerClass,
                    parentWasNotVisible || curClassNotVisible,
                )
            }
        }
    }

    /**
     * Adds a class to the codebase based on the [psiClass].
     *
     * For handling of [JvmMultifileClass]es, [multiFileClasses] is a map from qualified class name
     * to the set of source files which have already been processed for a class. If [psiClass] is a
     * multi-file class present in the map, only the class members which come from files which have
     * not already been processed will be added to the existing class definition.
     *
     * [apiPackages] is a filter for which packages should not be added to the codebase.
     */
    private fun initializeClassFromSources(
        psiClass: PsiClass,
        multiFileClasses: HashMap<FqName, Set<PsiFile>>,
        apiPackages: PackageFilter?
    ) {
        // Multi file classes appear from each file they're defined in. When the class parts are
        // defined in the same source set, the PsiClass from each file is identical, but if the
        // class parts are in different source sets, the members of each PsiClass will contain
        // a subset of all class members based on the structure of source set dependencies.
        val multiFileClassName = getOptionalMultiFileClassName(psiClass)
        if (multiFileClassName != null) {
            // Find which source files of this multi file class have already been processed.
            val previouslyProcessedFiles = multiFileClasses[multiFileClassName] ?: emptySet()
            // Assemble the set of source files which were used to create this PsiClass.
            val filesForCurrentPsiClass =
                (psiClass.methods.map { it.containingFile } +
                        psiClass.fields.map { it.containingFile })
                    .toSet()
            // Update the tracking with the new set of source files.
            multiFileClasses[multiFileClassName] =
                previouslyProcessedFiles + filesForCurrentPsiClass

            // If this class was already processed, there is already a ClassItem defined.
            if (previouslyProcessedFiles.isNotEmpty()) {
                val existingClassItem =
                    codebase.findClass(multiFileClassName.toString()) as SkeletonClassItem
                // Only add the methods and fields which defined in files which have not been
                // previously processed.
                val builder =
                    PsiClassBuilder(
                        globalContext = this,
                        psiClass,
                        existingClassItem.origin,
                    )
                builder.addMembersToClassItem(
                    classItem = existingClassItem,
                    psiMethods =
                        psiClass.methods.filter { it.containingFile !in previouslyProcessedFiles },
                    psiFields =
                        psiClass.fields.filter { it.containingFile !in previouslyProcessedFiles },
                    classTypeItemFactory = globalTypeItemFactory.from(existingClassItem),
                )
                // Skip the step below of adding a new ClassItem as one already exists.
                return
            }
        }

        // If a package filter is supplied then ignore any classes that do not match it.
        if (apiPackages != null) {
            val packageName = psiClass.packageName
            if (!apiPackages.matches(packageName)) return
        }

        val classItem =
            createPossibleApiClass(
                psiClass,
                // Sources always come from the command line.
                ClassOrigin.COMMAND_LINE,
            ) ?: return
        codebase.addTopLevelClassFromSource(classItem)
    }

    /**
     * Extract all the top level classes from [psiFiles].
     *
     * During the processing this checks each [PsiFile] for unresolved imports and syntax errors.
     */
    private fun getPsiClassesFromPsiFiles(psiFiles: List<PsiFile>): List<PsiClass> {
        // Make sure we only process the files once; sometimes there's overlap in the source lists
        return psiFiles
            .asSequence()
            .distinct()
            .flatMap { psiFile ->
                // Check for syntax errors across the whole file.
                checkForSyntaxErrors(psiFile)

                checkForUnresolvedImports(psiFile)

                getPsiClassesFromPsiFile(psiFile)
            }
            .toList()
    }

    /** Check to see if [psiFile] contains any unresolved imports. */
    private fun checkForUnresolvedImports(psiFile: PsiFile?) {
        // Visiting psiFile directly would eagerly load the entire file even though we only need
        // the importList here.
        (psiFile as? PsiJavaFile)
            ?.importList
            ?.accept(
                object : JavaRecursiveElementVisitor() {
                    override fun visitImportStatement(element: PsiImportStatement) {
                        super.visitImportStatement(element)
                        if (element.resolve() == null) {
                            reporter.report(
                                Issues.UNRESOLVED_IMPORT,
                                element,
                                "Unresolved import: `${element.qualifiedName}`"
                            )
                        }
                    }
                }
            )
    }

    /** Get, the possibly empty, list of [PsiClass]es from the [psiFile]. */
    private fun getPsiClassesFromPsiFile(psiFile: PsiFile): List<PsiClass> {
        // First, check for Java classes, return any that are found.
        (psiFile as? PsiClassOwner)?.classes?.toList()?.let { if (it.isNotEmpty()) return it }

        // Then, check for Kotlin classes, returning any that are found, or an empty list.
        val uFile = UastFacade.convertElementWithParent(psiFile, UFile::class.java) as? UFile?
        return uFile?.classes?.map { it }?.toList() ?: emptyList()
    }

    /** Check the [psiFile] for any syntax errors. */
    private fun checkForSyntaxErrors(psiFile: PsiFile) {
        psiFile.accept(
            object : JavaRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    super.visitErrorElement(element)
                    reporter.report(
                        Issues.INVALID_SYNTAX,
                        element,
                        "Syntax error: `${element.errorDescription}`"
                    )
                }

                override fun visitCodeBlock(block: PsiCodeBlock) {
                    // Ignore to avoid eagerly parsing all method bodies.
                }

                override fun visitDocComment(comment: PsiDocComment) {
                    // Ignore to avoid eagerly parsing all doc comments.
                    // Doc comments cannot contain error elements.
                }
            }
        )
    }

    /** Get the optional multi file class name. */
    private fun getOptionalMultiFileClassName(psiClass: PsiClass): FqName? {
        val ktLightClass = (psiClass as? UClass)?.javaPsi as? KtLightClassForFacade
        val multiFileClassName =
            if (ktLightClass?.multiFileClass == true) {
                ktLightClass.facadeClassFqName
            } else {
                null
            }
        return multiFileClassName
    }
}
