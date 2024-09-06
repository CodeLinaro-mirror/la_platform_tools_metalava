/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AbstractCodebase
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.CLASS_ESTIMATE
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.item.PackageTracker
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.gatherPackageJavadoc
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.TypeAnnotationProvider
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

const val PACKAGE_ESTIMATE = 500
const val METHOD_ESTIMATE = 1000

/**
 * A codebase containing Java, Kotlin, or UAST PSI classes
 *
 * After creation, a list of PSI file is passed to [initializeFromSources] or a JAR file is passed
 * to [initializeFromJar]. This creates package and class items along with their members. This
 * process is broken into two phases:
 *
 * First, [initializing] is set to true, and class items are created from the supplied sources. If
 * [fromClasspath] is false, these are main classes of the codebase and have [ClassItem.emit] set to
 * true and [ClassItem.isFromClassPath] set to false.
 *
 * If [fromClasspath] is true, all classes are assumed to be from the classpath, so [ClassItem.emit]
 * is set to false and [ClassItem.isFromClassPath] is set to true for all classes created.
 *
 * Then [initializing] is set to false and the second pass begins. This path iteratively resolves
 * supertypes of class items until all are fully resolved, creating new class and package items as
 * needed. Since all the source class and package items have been created, new items are assumed to
 * originate from the classpath and have [Item.emit] set to false and [Item.isFromClassPath] set to
 * true.
 */
internal class PsiBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    override val reporter: Reporter,
    val allowReadingComments: Boolean,
    val fromClasspath: Boolean = false,
) :
    AbstractCodebase(
        location = location,
        description = description,
        preFiltered = false,
        annotationManager = annotationManager,
        trustedApi = false,
        supportsDocumentation = true,
    ) {
    private lateinit var uastEnvironment: UastEnvironment
    internal val project: Project
        get() = uastEnvironment.ideaProject

    /**
     * Returns the compilation units used in this codebase (may be empty when the codebase is not
     * loaded from source, such as from .jar files or from signature files)
     */
    private var units: List<PsiFile> = emptyList()

    /**
     * Printer which can convert PSI, UAST and constants into source code, with ability to filter
     * out elements that are not part of a codebase etc
     */
    internal val printer = CodePrinter(this, reporter)

    /** Map from class name to class item. Classes are added via [registerClass] */
    private val classMap: MutableMap<String, PsiClassItem> = HashMap(CLASS_ESTIMATE)

    /**
     * Map from classes to the set of callables for each (but only for classes where we've called
     * [findCallableByPsiMethod]
     */
    private lateinit var methodMap: MutableMap<PsiClassItem, MutableMap<PsiMethod, PsiCallableItem>>

    /** Map from package name to the corresponding package item */
    private val packageTracker = PackageTracker { packageName, packageDoc, containingPackage ->
        val psiPackage =
            findPsiPackage(packageName)
                ?: run {
                    // This can happen if a class's package statement does not match its file path.
                    // In that case, this fakes up a PsiPackageImpl that matches the package
                    // statement as that is the source of truth.
                    val manager = PsiManager.getInstance(project)
                    PsiPackageImpl(manager, packageName)
                }
        PsiPackageItem.create(
            codebase = this@PsiBasedCodebase,
            psiPackage = psiPackage,
            packageDoc = packageDoc,
            containingPackage = containingPackage,
        )
    }

    /**
     * A list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    private lateinit var topLevelClassesFromSource: MutableList<PsiClassItem>

    /**
     * Set to true in [initializeFromJar] and [initializeFromSources] for the first pass of creating
     * class items for all classes in the codebase sources and false for the second pass of creating
     * class items for the supertypes of the codebase classes. New class items created in the
     * supertypes pass must come from the classpath (dependencies) since all source classes have
     * been created.
     *
     * This information is used in [createClass] to set [ClassItem.emit] to true for source classes
     * and [ClassItem.isFromClassPath] to true for classpath classes. It is also used in
     * [findOrCreatePackage] to set [PackageItem.emit] to true for source packages.
     */
    private var initializing = false

    /** [PsiTypeItemFactory] used to create [PsiTypeItem]s. */
    internal val globalTypeItemFactory = PsiTypeItemFactory(this, TypeParameterScope.empty)

    internal fun initializeFromSources(
        uastEnvironment: UastEnvironment,
        sourceSet: SourceSet,
    ) {
        initializing = true

        this.uastEnvironment = uastEnvironment

        this.methodMap = HashMap(METHOD_ESTIMATE)
        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)

        // Get the list of `PsiFile`s from the `SourceSet`.
        val psiFiles = Extractor.createUnitsForFiles(uastEnvironment.ideaProject, sourceSet.sources)

        // Split the `PsiFile`s into `PsiClass`es and `package-info.java` `PsiJavaFile`s.
        val (packageInfoFiles, psiClasses) = splitPsiFilesIntoClassesAndPackageInfoFiles(psiFiles)

        // Gather all package related javadoc.
        val packageDocs =
            gatherPackageJavadoc(
                reporter,
                sourceSet,
                packageNameFilter = { findPsiPackage(it) != null },
                packageInfoFiles,
                packageInfoDocExtractor = { getOptionalPackageDocFromPackageInfoFile(it) },
            )

        // Create the initial set of packages that were found in the source files.
        packageTracker.createInitialPackages(packageDocs)

        // Process the `PsiClass`es.
        for (psiClass in psiClasses) {
            topLevelClassesFromSource += createTopLevelClassAndContents(psiClass)
        }

        finishInitialization()
    }

    /**
     * Split the [psiFiles] into separate `package-info.java` [PsiJavaFile]s and [PsiClass]es.
     *
     * During the processing this checks each [PsiFile] for unresolved imports and each [PsiClass]
     * for syntax errors.
     */
    private fun splitPsiFilesIntoClassesAndPackageInfoFiles(
        psiFiles: List<PsiFile>
    ): Pair<List<PsiJavaFile>, List<PsiClass>> {
        // A set to track `@JvmMultifileClass`es that have already been added to psiClasses.
        val multiFileClassNames = HashSet<FqName>()

        val psiClasses = mutableListOf<PsiClass>()
        val packageInfoFiles = mutableListOf<PsiJavaFile>()

        // Make sure we only process the files once; sometimes there's overlap in the source lists
        for (psiFile in psiFiles.asSequence().distinct()) {
            checkForUnresolvedImports(psiFile)

            val classes = getPsiClassesFromPsiFile(psiFile)
            when {
                classes.isEmpty() && psiFile is PsiJavaFile -> {
                    if (psiFile.name == JAVA_PACKAGE_INFO) {
                        packageInfoFiles.add(psiFile)
                    }
                }
                else -> {
                    for (psiClass in classes) {
                        checkForSyntaxErrors(psiClass)

                        // Multi file classes appear identically from each file they're defined in,
                        // don't add duplicates
                        val multiFileClassName = getOptionalMultiFileClassName(psiClass)
                        if (multiFileClassName != null) {
                            if (multiFileClassName in multiFileClassNames) {
                                continue
                            } else {
                                multiFileClassNames.add(multiFileClassName)
                            }
                        }

                        psiClasses.add(psiClass)
                    }
                }
            }
        }
        return Pair(packageInfoFiles, psiClasses)
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

    /**
     * Get the optional [MutablePackageDoc] from [psiFile].
     *
     * @param psiFile must be a `package-info.java` file.
     */
    private fun getOptionalPackageDocFromPackageInfoFile(psiFile: PsiJavaFile): MutablePackageDoc? {
        val packageStatement = psiFile.packageStatement ?: return null
        val packageName = packageStatement.packageName

        // Make sure that this is actually a package.
        findPsiPackage(packageName) ?: return null

        // Look for javadoc on the package statement; this is NOT handed to us on the PsiPackage!
        val comment = PsiTreeUtil.getPrevSiblingOfType(packageStatement, PsiDocComment::class.java)
        if (comment != null) {
            return MutablePackageDoc(
                qualifiedName = packageName,
                fileLocation = PsiFileLocation.fromPsiElement(psiFile),
                commentFactory = PsiItemDocumentation.factory(packageStatement, this, comment.text),
            )
        }

        // No comment could be found.
        return null
    }

    /** Check the [psiClass] for any syntax errors. */
    private fun checkForSyntaxErrors(psiClass: PsiClass) {
        psiClass.accept(
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

    /**
     * Finish initialising this codebase.
     *
     * Involves:
     * * Constructing packages.
     * * Finalizing [PsiClassItem]s which may involve creating some more, e.g. super classes and
     *   interfaces referenced from the source code but provided on the class path.
     */
    private fun finishInitialization() {

        initializing = false

        // Resolve the super types of all the classes that have been loaded.
        resolveSuperTypes()
    }

    override fun dispose() {
        uastEnvironment.dispose()
        super.dispose()
    }

    private fun findOrCreatePackage(
        psiPackage: PsiPackage,
    ): DefaultPackageItem {
        val pkgName = psiPackage.qualifiedName
        return packageTracker.findOrCreatePackage(pkgName, emit = !fromClasspath && initializing)
    }

    internal fun initializeFromJar(
        uastEnvironment: UastEnvironment,
        jarFile: File,
    ) {
        initializing = true

        this.uastEnvironment = uastEnvironment

        // Create the initial set of packages that were found in the jar files. When loading from a
        // jar there is no package documentation so this will only create the root package.
        packageTracker.createInitialPackages(PackageDocs.EMPTY)

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        this.methodMap = HashMap(1000)

        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)

        try {
            ZipFile(jarFile).use { jar ->
                val enumeration = jar.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    val fileName = entry.name
                    if (fileName.contains("$")) {
                        // skip inner classes
                        continue
                    }
                    if (fileName.endsWith(SdkConstants.DOT_CLASS)) {
                        val qualifiedName =
                            fileName.removeSuffix(SdkConstants.DOT_CLASS).replace('/', '.')
                        if (!qualifiedName.endsWith(".package-info")) {
                            val psiClass = facade.findClass(qualifiedName, scope) ?: continue

                            val classItem = createTopLevelClassAndContents(psiClass)
                            topLevelClassesFromSource.add(classItem)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
        }

        finishInitialization()
    }

    /**
     * Create top level classes, their inner classes and all the other members.
     *
     * All the classes are registered by name and so can be found by [findOrCreateClass].
     */
    private fun createTopLevelClassAndContents(psiClass: PsiClass): PsiClassItem {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")
        return createClass(psiClass, null, globalTypeItemFactory)
    }

    internal fun createClass(
        psiClass: PsiClass,
        containingClassItem: PsiClassItem?,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
    ): PsiClassItem {
        val packageName = getPackageName(psiClass)

        // Find the package and a flag to indicate whether it is a fake package as fake packages
        // should not appear in the API for legacy reasons.
        val (psiPackage, fake) =
        // If the package could be found then a fake package was not created.
        findPsiPackage(packageName)?.to(false)
                ?: run {
                    val directory =
                        psiClass.containingFile.containingDirectory.virtualFile.canonicalPath
                    reporter.report(
                        Issues.INVALID_PACKAGE,
                        psiClass,
                        "Could not find package $packageName for class ${psiClass.qualifiedName}." +
                            " This is most likely due to a mismatch between the package statement" +
                            " and the directory $directory"
                    )
                    // Fake up a PsiPackageImpl that matches the package statement as that is the
                    // source of truth.
                    PsiPackageImpl(psiClass.manager, packageName) to true
                }

        val packageItem = findOrCreatePackage(psiPackage)

        // If initializing is true, this class is from source
        val classItem =
            PsiClassItem.create(
                this,
                psiClass,
                containingClassItem,
                packageItem,
                enclosingClassTypeItemFactory,
                fromClassPath = fromClasspath || !initializing,
            )
        // If the package is fake then it must never be emitted. Otherwise, set emit to `true` for
        // source classes but `false` for classpath classes.
        classItem.emit = !classItem.isFromClassPath() && !fake

        return classItem
    }

    override fun getPackages() = packageTracker.getPackages()

    override fun size() = packageTracker.size

    override fun findPackage(pkgName: String) = packageTracker.findPackage(pkgName)

    internal fun findPsiPackage(pkgName: String): PsiPackage? {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName)
    }

    override fun findClass(className: String): PsiClassItem? {
        return classMap[className]
    }

    override fun resolveClass(className: String): ClassItem? = findOrCreateClass(className)

    fun findClass(psiClass: PsiClass): PsiClassItem? {
        val qualifiedName: String = psiClass.qualifiedName ?: psiClass.name!!
        return classMap[qualifiedName]
    }

    internal fun findOrCreateClass(qualifiedName: String): PsiClassItem? {
        // Check to see if the class has already been seen and if so return it immediately.
        findClass(qualifiedName)?.let {
            return it
        }

        // The following cannot find a class whose name does not correspond to the file name, e.g.
        // in Java a class that is a second top level class.
        val finder = JavaPsiFacade.getInstance(project)
        val psiClass =
            finder.findClass(qualifiedName, GlobalSearchScope.allScope(project)) ?: return null
        return findOrCreateClass(psiClass)
    }

    /**
     * Identifies a point in the [PsiClassItem] nesting structure where new [PsiClassItem]s need
     * inserting.
     */
    data class NewClassInsertionPoint(
        /**
         * The [PsiClass] that is the root of the nested classes that need creation, is a top level
         * class if [containingClassItem] is `null`.
         */
        val missingPsiClass: PsiClass,

        /** The containing class item, or `null` if the top level. */
        val containingClassItem: PsiClassItem?,
    )

    /**
     * Called when no [PsiClassItem] was found by [findClass]`([PsiClass]) when called on
     * [psiClass].
     *
     * The purpose of this is to find where a new [PsiClassItem] should be inserted in the nested
     * class structure. It finds the outermost [PsiClass] with no associated [PsiClassItem] but
     * which is either a top level class or whose containing [PsiClass] does have an associated
     * [PsiClassItem]. That is the point where new classes need to be created.
     *
     * e.g. if the nesting structure is `A.B.C` and `A` has already been created then the insertion
     * point would consist of [PsiClassItem] for `A` (the containing class item) and the [PsiClass]
     * for `B` (the outermost [PsiClass] with no associated item).
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
            findClass(containing)?.let { containingClassItem ->
                return NewClassInsertionPoint(current, containingClassItem)
            }
            current = containing
        } while (true)
    }

    internal fun findOrCreateClass(psiClass: PsiClass): PsiClassItem {
        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; call findOrCreateTypeParameter(...) instead"
            )
        }

        // If it has already been created then return it.
        findClass(psiClass)?.let {
            return it
        }

        // Otherwise, find an insertion point at which new classes should be created.
        val (missingPsiClass, containingClassItem) = findNewClassInsertionPoint(psiClass)

        // Create a top level or nested class as appropriate.
        val createdClassItem =
            if (containingClassItem == null) {
                createTopLevelClassAndContents(missingPsiClass)
            } else {
                createClass(
                    missingPsiClass,
                    containingClassItem,
                    globalTypeItemFactory.from(containingClassItem)
                )
            }

        // Select the class item to return.
        return if (missingPsiClass == psiClass) {
            // The created class item was what was requested so just return it.
            createdClassItem
        } else {
            // Otherwise, a nested class was requested so find it. It was created when its
            // containing class was created.
            findClass(psiClass)!!
        }
    }

    internal fun findClass(psiType: PsiType): PsiClassItem? {
        if (psiType is PsiClassType) {
            val cls = psiType.resolve() ?: return null
            return findOrCreateClass(cls)
        } else if (psiType is PsiArrayType) {
            var componentType = psiType.componentType
            // We repeatedly get the component type because the array may have multiple dimensions
            while (componentType is PsiArrayType) {
                componentType = componentType.componentType
            }
            if (componentType is PsiClassType) {
                val cls = componentType.resolve() ?: return null
                return findOrCreateClass(cls)
            }
        }
        return null
    }

    internal fun getClassType(cls: PsiClass): PsiClassType =
        getFactory().createType(cls, PsiSubstitutor.EMPTY)

    internal fun getComment(documentation: String, parent: PsiElement? = null): PsiDocComment =
        getFactory().createDocCommentFromText(documentation, parent)

    private fun getPackageName(clz: PsiClass): String {
        var top: PsiClass? = clz
        while (top?.containingClass != null) {
            top = top.containingClass
        }
        top ?: return ""

        val name = top.name
        val fullName = top.qualifiedName ?: return ""

        if (name == fullName) {
            return ""
        }

        return fullName.substring(0, fullName.length - 1 - name!!.length)
    }

    internal fun findCallableByPsiMethod(method: PsiMethod): PsiCallableItem {
        val containingClass = method.containingClass
        val cls = findOrCreateClass(containingClass!!)

        // Ensure initialized/registered via [#registerMethods]
        if (methodMap[cls] == null) {
            val map = HashMap<PsiMethod, PsiCallableItem>(40)
            registerCallablesByPsiMethod(cls.methods(), map)
            registerCallablesByPsiMethod(cls.constructors(), map)
            methodMap[cls] = map
        }

        val methods = methodMap[cls]!!
        val methodItem = methods[method]
        if (methodItem == null) {
            // Probably switched psi classes (e.g. used source PsiClass in registry but
            // found duplicate class in .jar library and we're now pointing to it; in that
            // case, find the equivalent method by signature
            val psiClass = cls.psiClass
            val updatedMethod = psiClass.findMethodBySignature(method, true)
            val result = methods[updatedMethod!!]
            if (result == null) {
                val extra =
                    PsiMethodItem.create(this, cls, updatedMethod, globalTypeItemFactory.from(cls))
                methods[method] = extra
                methods[updatedMethod] = extra

                return extra
            }
            return result
        }

        return methodItem
    }

    internal fun findField(field: PsiField): FieldItem? {
        val containingClass = field.containingClass ?: return null
        val cls = findOrCreateClass(containingClass)
        return cls.findField(field.name)
    }

    private fun registerCallablesByPsiMethod(
        callables: List<CallableItem>,
        map: MutableMap<PsiMethod, PsiCallableItem>
    ) {
        for (callable in callables) {
            val psiMethod = (callable as PsiCallableItem).psiMethod
            map[psiMethod] = callable
            if (psiMethod is UMethod) {
                // Register LC method as a key too
                // so that we can find the corresponding [CallableItem]
                // Otherwise, we will end up creating a new [CallableItem]
                // without source PSI, resulting in wrong modifier.
                map[psiMethod.javaPsi] = callable
            }
        }
    }

    override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    override fun isFromClassPath() = fromClasspath

    internal fun createPsiType(s: String, parent: PsiElement? = null): PsiType =
        getFactory().createTypeFromText(s, parent)

    private fun createPsiAnnotation(s: String, parent: PsiElement? = null): PsiAnnotation =
        getFactory().createAnnotationFromText(s, parent)

    private fun getFactory() = JavaPsiFacade.getElementFactory(project)

    private var nonNullAnnotationProvider: TypeAnnotationProvider? = null
    private var nullableAnnotationProvider: TypeAnnotationProvider? = null

    /** Type annotation provider which provides androidx.annotation.NonNull */
    internal fun getNonNullAnnotationProvider(): TypeAnnotationProvider {
        return nonNullAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NONNULL"))
                    )
                nonNullAnnotationProvider
                provider
            }
    }

    /** Type annotation provider which provides androidx.annotation.Nullable */
    internal fun getNullableAnnotationProvider(): TypeAnnotationProvider {
        return nullableAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NULLABLE"))
                    )
                nullableAnnotationProvider
                provider
            }
    }

    override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        val psiAnnotation = createPsiAnnotation(source, (context as? PsiItem)?.psi())
        return PsiAnnotationItem.create(this, psiAnnotation)
    }

    /** Add a class to the codebase. Called from [PsiClassItem.create]. */
    internal fun registerClass(classItem: PsiClassItem) {
        val qualifiedName = classItem.qualifiedName()
        val existing = classMap.put(qualifiedName, classItem)
        if (existing != null) {
            reporter.report(
                Issues.DUPLICATE_SOURCE_CLASS,
                classItem,
                "Ignoring this duplicate definition of $qualifiedName; previous definition was loaded from ${existing.fileLocation.path}"
            )
            return
        }

        addClass(classItem)
    }

    internal val uastResolveService: BaseKotlinUastResolveProviderService? by lazy {
        ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
    }
}
