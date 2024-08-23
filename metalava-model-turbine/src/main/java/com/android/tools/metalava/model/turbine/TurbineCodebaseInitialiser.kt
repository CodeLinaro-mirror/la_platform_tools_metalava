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

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FixedFieldValue
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.FieldValue
import com.android.tools.metalava.model.source.SourceItemDocumentation
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.binder.bound.EnumConstantValue
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TurbineClassValue
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass.FieldInfo
import com.google.turbine.binder.bound.TypeBoundClass.MethodInfo
import com.google.turbine.binder.bound.TypeBoundClass.ParamInfo
import com.google.turbine.binder.bound.TypeBoundClass.TyVarInfo
import com.google.turbine.binder.bytecode.BytecodeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.env.SimpleEnv
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.TopLevelIndex
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.binder.sym.TyVarSymbol
import com.google.turbine.diag.SourceFile
import com.google.turbine.diag.TurbineLog
import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.Const.Kind
import com.google.turbine.model.Const.Value
import com.google.turbine.model.TurbineConstantTypeKind as PrimKind
import com.google.turbine.model.TurbineFlag
import com.google.turbine.model.TurbineTyKind
import com.google.turbine.processing.ModelFactory
import com.google.turbine.processing.TurbineElements
import com.google.turbine.processing.TurbineTypes
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.Assign
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.tree.Tree.Literal
import com.google.turbine.tree.Tree.MethDecl
import com.google.turbine.tree.Tree.TyDecl
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
internal open class TurbineCodebaseInitialiser(
    val units: List<CompUnit>,
    val codebase: TurbineBasedCodebase,
    val classpath: List<File>,
) {
    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /** Map between ClassSymbols and TurbineClass for classes present in source */
    private lateinit var sourceClassMap: ImmutableMap<ClassSymbol, SourceTypeBoundClass>

    /** Map between ClassSymbols and TurbineClass for classes present in classPath */
    private lateinit var envClassMap: CompoundEnv<ClassSymbol, BytecodeBoundClass>

    private lateinit var index: TopLevelIndex

    /** Map between Class declaration and the corresponding source CompUnit */
    private val classSourceMap: MutableMap<TyDecl, CompUnit> = mutableMapOf<TyDecl, CompUnit>()

    private val globalTypeItemFactory =
        TurbineTypeItemFactory(codebase, this, TypeParameterScope.empty)

    /** Creates [Item] instances for [codebase]. */
    private val itemFactory =
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
     * Binds the units with the help of Turbine's binder.
     *
     * Then creates the packages, classes and their members, as well as sets up various class
     * hierarchies using the binder's output
     */
    fun initialize(packageHtmlByPackageName: Map<String, File>) {
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
                    ImmutableList.copyOf(units),
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    procInfo,
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
            sourceClassMap = bindingResult.units()
            envClassMap = bindingResult.classPathEnv()
            index = bindingResult.tli()
        } catch (e: Throwable) {
            throw e
        }
        // maps class symbols to their source-based definitions
        val sourceEnv = SimpleEnv<ClassSymbol, SourceTypeBoundClass>(sourceClassMap)
        // maps class symbols to their classpath-based definitions
        val classpathEnv: CompoundEnv<ClassSymbol, TypeBoundClass> = CompoundEnv.of(envClassMap)
        // provides a unified view of both source and classpath classes
        val combinedEnv = classpathEnv.append(sourceEnv)

        // used to create language model elements for code analysis
        val factory = ModelFactory(combinedEnv, ClassLoader.getSystemClassLoader(), index)
        // provides type-related operations within the Turbine compiler context
        val turbineTypes = TurbineTypes(factory)
        // provides access to code elements (packages, types, members) for analysis.
        turbineElements = TurbineElements(factory, turbineTypes)

        createAllPackages(packageHtmlByPackageName)
        createAllClasses()
    }

    /** Map from file path to the [TurbineSourceFile]. */
    private val turbineSourceFiles = mutableMapOf<String, TurbineSourceFile>()

    /**
     * Create a [TurbineSourceFile] for the specified [compUnit].
     *
     * This may be called multiple times for the same [compUnit] in which case it will return the
     * same [TurbineSourceFile]. It will throw an exception if two [CompUnit]s have the same path.
     */
    private fun createTurbineSourceFile(compUnit: CompUnit): TurbineSourceFile {
        val path = compUnit.source().path()
        val existing = turbineSourceFiles[path]
        if (existing != null && existing.compUnit != compUnit) {
            error("duplicate source file found for $path")
        }
        return TurbineSourceFile(codebase, compUnit).also { turbineSourceFiles[path] = it }
    }

    /**
     * Get the [TurbineSourceFile] for a [SourceFile], failing if it could not be found.
     *
     * A [TurbineSourceFile] must be created by [createTurbineSourceFile] before calling this.
     */
    private fun turbineSourceFile(sourceFile: SourceFile): TurbineSourceFile =
        turbineSourceFiles[sourceFile.path()]
            ?: error("unrecognized source file: ${sourceFile.path()}")

    /** Check if this is for a `package-info.java` file or not. */
    private fun CompUnit.isPackageInfo() =
        source().path().let { it == JAVA_PACKAGE_INFO || it.endsWith("/" + JAVA_PACKAGE_INFO) }

    private fun createAllPackages(packageHtmlByPackageName: Map<String, File>) {
        // First, find all package-info.java files and create packages for them.
        for (unit in units) {
            // Only process package-info.java files in this loop.
            if (!unit.isPackageInfo()) continue

            val source = unit.source().source()
            val sourceFile = createTurbineSourceFile(unit)
            val doc = getHeaderComments(source)
            createPackage(getPackageName(unit), sourceFile, doc.toItemDocumentationFactory())
        }

        // Secondly, create package items for package.html files.
        for ((name, file) in packageHtmlByPackageName.entries) {
            codebase.findPackage(name)
                ?: createPackage(name, null, SourceItemDocumentation.fromHTML(file.readText()))
        }

        // Thirdly, find all classes and create or find a package for them.
        for (unit in units) {
            // Ignore package-info.java files in this loop.
            if (unit.isPackageInfo()) continue

            val name = getPackageName(unit)
            findOrCreatePackage(name)
            unit.decls().forEach { decl -> classSourceMap.put(decl, unit) }
        }

        // Finally, make sure that there is a root package.
        findOrCreatePackage("")
    }

    /**
     * Creates a package and registers it in the codebase's package map.
     *
     * Fails if there is a duplicate.
     */
    private fun createPackage(
        name: String,
        sourceFile: TurbineSourceFile?,
        documentationFactory: ItemDocumentationFactory,
    ): PackageItem {
        codebase.findPackage(name)?.let {
            error("Duplicate package-info.java files found for $name")
        }

        val modifiers = TurbineModifierItem.create(codebase, 0, null)
        val fileLocation = TurbineFileLocation.forTree(sourceFile)
        val turbinePkgItem =
            itemFactory.createPackageItem(fileLocation, modifiers, documentationFactory, name)
        codebase.addPackage(turbinePkgItem)
        return turbinePkgItem
    }

    /**
     * Searches for the package with supplied name in the codebase's package map and if not found
     * creates the corresponding TurbinePackageItem and adds it to the package map.
     */
    private fun findOrCreatePackage(name: String): DefaultPackageItem {
        codebase.findPackage(name)?.let {
            return it
        }

        val turbinePkgItem = itemFactory.createPackageItem(qualifiedName = name)
        codebase.addPackage(turbinePkgItem)
        return turbinePkgItem
    }

    private fun createAllClasses() {
        for ((classSymbol, sourceBoundClass) in sourceClassMap) {

            // Turbine considers package-info as class and creates one for empty packages which is
            // not consistent with Psi
            if (classSymbol.simpleName() == "package-info") {
                continue
            }

            // Ignore nested classes, they will be created when the outer class is created.
            if (sourceBoundClass.owner() != null) {
                continue
            }

            val classItem = createTopLevelClassAndContents(classSymbol)
            codebase.addTopLevelClassFromSource(classItem)
        }

        codebase.resolveSuperTypes()
    }

    val ClassSymbol.isTopClass
        get() = !binaryName().contains('$')

    /**
     * Create top level classes, their nested classes and all the other members.
     *
     * All the classes are registered by name and so can be found by [findOrCreateClass].
     */
    private fun createTopLevelClassAndContents(classSymbol: ClassSymbol): ClassItem {
        if (!classSymbol.isTopClass) error("$classSymbol is not a top level class")
        return createClass(classSymbol, null, globalTypeItemFactory)
    }

    /** Tries to create a class if not already present in codebase's classmap */
    internal fun findOrCreateClass(name: String): ClassItem? {
        var classItem = codebase.findClass(name)

        if (classItem == null) {
            // This will get the symbol for the top class even if the class name is for a nested
            // class.
            val topClassSym = getClassSymbol(name)

            // Create the top level class, if needed, along with any nested classes and register
            // them all by name.
            topClassSym?.let {
                // It is possible that the top level class has already been created but just did not
                // contain the requested nested class so check to make sure it exists before
                // creating it.
                val topClassName = getQualifiedName(topClassSym.binaryName())
                codebase.findClass(topClassName)
                    ?: let {
                        // Create and register the top level class and its nested classes.
                        createTopLevelClassAndContents(topClassSym)

                        // Now try and find the actual class that was requested by name. If it
                        // exists it
                        // should have been created in the previous call.
                        classItem = codebase.findClass(name)
                    }
            }
        }

        return classItem
    }

    private fun createClass(
        sym: ClassSymbol,
        containingClassItem: DefaultClassItem?,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ): ClassItem {

        var cls: TypeBoundClass? = sourceClassMap[sym]
        cls = if (cls != null) cls else envClassMap.get(sym)!!
        val decl = (cls as? SourceTypeBoundClass)?.decl()

        val isTopClass = cls.owner() == null
        val isFromClassPath = !(cls is SourceTypeBoundClass)

        // Get the package item
        val pkgName = sym.packageName().replace('/', '.')
        val pkgItem = findOrCreatePackage(pkgName)

        // Create the sourcefile
        val sourceFile =
            if (isTopClass && !isFromClassPath) {
                classSourceMap[(cls as SourceTypeBoundClass).decl()]?.let {
                    createTurbineSourceFile(it)
                }
            } else null
        val fileLocation =
            when {
                sourceFile != null -> TurbineFileLocation.forTree(sourceFile, decl)
                containingClassItem != null ->
                    TurbineFileLocation.forTree(containingClassItem, decl)
                else -> FileLocation.UNKNOWN
            }

        // Create class
        val qualifiedName = getQualifiedName(sym.binaryName())
        val simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        val fullName = sym.simpleName().replace('$', '.')
        val annotations = createAnnotations(cls.annotations())
        val documentation = javadoc(decl)
        val modifierItem =
            TurbineModifierItem.create(
                codebase,
                cls.access(),
                annotations,
            )
        val (typeParameters, classTypeItemFactory) =
            createTypeParameters(
                cls.typeParameterTypes(),
                enclosingClassTypeItemFactory,
                "class $qualifiedName",
            )
        val classItem =
            itemFactory.createClassItem(
                fileLocation = fileLocation,
                modifiers = modifierItem,
                documentationFactory = getCommentedDoc(documentation),
                source = sourceFile,
                classKind = getClassKind(cls.kind()),
                containingClass = containingClassItem,
                containingPackage = pkgItem,
                qualifiedName = qualifiedName,
                simpleName = simpleName,
                fullName = fullName,
                typeParameterList = typeParameters,
            )
        modifierItem.setSynchronized(false) // A class can not be synchronized in java

        // Setup the SuperClass
        if (!classItem.isInterface()) {
            val superClassType = cls.superClassType()
            val superClassTypeItem =
                if (superClassType == null) null
                else classTypeItemFactory.getSuperClassType(superClassType)
            classItem.setSuperClassType(superClassTypeItem)
        }

        // Set interface types
        classItem.setInterfaceTypes(
            cls.interfaceTypes().map { classTypeItemFactory.getInterfaceType(it) }
        )

        // Create fields
        createFields(classItem, cls.fields(), classTypeItemFactory)

        // Create methods
        createMethods(classItem, cls.methods(), classTypeItemFactory)

        // Create constructors
        createConstructors(classItem, cls.methods(), classTypeItemFactory)

        // Do not emit to signature file if it is from classpath
        if (isFromClassPath) {
            pkgItem.emit = false
            classItem.emit = false
        }

        // Create InnerClasses.
        val children = cls.children()
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

    /** Creates a list of AnnotationItems from given list of Turbine Annotations */
    internal fun createAnnotations(annotations: List<AnnoInfo>): List<AnnotationItem> {
        return annotations.mapNotNull { createAnnotation(it) }
    }

    private fun createAnnotation(annotation: AnnoInfo): AnnotationItem? {
        val tree = annotation.tree()
        val simpleName = tree?.let { extractNameFromIdent(it.name()) }
        val clsSym = annotation.sym()
        val qualifiedName =
            if (clsSym == null) simpleName!! else getQualifiedName(clsSym.binaryName())

        val fileLocation =
            annotation
                .source()
                ?.let { sourceFile -> turbineSourceFile(sourceFile) }
                ?.let { sourceFile -> TurbineFileLocation.forTree(sourceFile, tree) }
                ?: FileLocation.UNKNOWN

        return DefaultAnnotationItem.create(codebase, fileLocation, qualifiedName) {
            getAnnotationAttributes(annotation.values(), tree?.args())
        }
    }

    /** Creates a list of AnnotationAttribute from the map of name-value attribute pairs */
    private fun getAnnotationAttributes(
        attrs: ImmutableMap<String, Const>,
        exprs: ImmutableList<Expression>?
    ): List<AnnotationAttribute> {
        val attributes = mutableListOf<AnnotationAttribute>()
        if (exprs != null) {
            for (exp in exprs) {
                when (exp.kind()) {
                    Tree.Kind.ASSIGN -> {
                        exp as Assign
                        val name = exp.name().value()
                        val assignExp = exp.expr()
                        attributes.add(
                            DefaultAnnotationAttribute(
                                name,
                                createAttrValue(attrs[name]!!, assignExp)
                            )
                        )
                    }
                    else -> {
                        val name = ANNOTATION_ATTR_VALUE
                        val value =
                            attrs[name]
                                ?: (exp as? Literal)?.value()
                                    ?: error(
                                    "Cannot find value for default 'value' attribute from $exp"
                                )
                        attributes.add(
                            DefaultAnnotationAttribute(name, createAttrValue(value, exp))
                        )
                    }
                }
            }
        } else {
            for ((name, value) in attrs) {
                attributes.add(DefaultAnnotationAttribute(name, createAttrValue(value, null)))
            }
        }
        return attributes
    }

    private fun createAttrValue(const: Const, expr: Expression?): AnnotationAttributeValue {
        if (const.kind() == Kind.ARRAY) {
            const as ArrayInitValue
            if (const.elements().count() == 1 && expr != null && !(expr is ArrayInit)) {
                // This is case where defined type is array type but provided attribute value is
                // single non-array element
                // For e.g. @Anno(5) where Anno is @interfacce Anno {int [] value()}
                val constLiteral = const.elements().single()
                return DefaultAnnotationSingleAttributeValue(
                    { getSource(constLiteral, expr) },
                    { getValue(constLiteral) }
                )
            }
            return DefaultAnnotationArrayAttributeValue(
                { getSource(const, expr) },
                { const.elements().map { createAttrValue(it, null) } }
            )
        }
        return DefaultAnnotationSingleAttributeValue(
            { getSource(const, expr) },
            { getValue(const) }
        )
    }

    private fun getSource(const: Const, expr: Expression?): String {
        return when (const.kind()) {
            Kind.PRIMITIVE -> {
                when ((const as Value).constantTypeKind()) {
                    PrimKind.INT -> {
                        val value = (const as Const.IntValue).value()
                        if (value < 0 || (expr != null && expr.kind() == Tree.Kind.TYPE_CAST))
                            "0x" + value.toUInt().toString(16) // Hex Value
                        else value.toString()
                    }
                    PrimKind.SHORT -> {
                        val value = (const as Const.ShortValue).value()
                        if (value < 0) "0x" + value.toUInt().toString(16) else value.toString()
                    }
                    PrimKind.FLOAT -> {
                        val value = (const as Const.FloatValue).value()
                        when {
                            value == Float.POSITIVE_INFINITY -> "java.lang.Float.POSITIVE_INFINITY"
                            value == Float.NEGATIVE_INFINITY -> "java.lang.Float.NEGATIVE_INFINITY"
                            value < 0 -> value.toString() + "F" // Handling negative values
                            else -> value.toString() + "f" // Handling positive values
                        }
                    }
                    PrimKind.DOUBLE -> {
                        val value = (const as Const.DoubleValue).value()
                        when {
                            value == Double.POSITIVE_INFINITY ->
                                "java.lang.Double.POSITIVE_INFINITY"
                            value == Double.NEGATIVE_INFINITY ->
                                "java.lang.Double.NEGATIVE_INFINITY"
                            else -> const.toString()
                        }
                    }
                    PrimKind.BYTE -> const.getValue().toString()
                    else -> const.toString()
                }
            }
            Kind.ARRAY -> {
                const as ArrayInitValue
                val pairs =
                    if (expr != null) const.elements().zip((expr as ArrayInit).exprs())
                    else const.elements().map { Pair(it, null) }
                buildString {
                        append("{")
                        pairs.joinTo(this, ", ") { getSource(it.first, it.second) }
                        append("}")
                    }
                    .toString()
            }
            Kind.ENUM_CONSTANT -> getValue(const).toString()
            Kind.CLASS_LITERAL -> {
                if (expr != null) expr.toString() else getValue(const).toString()
            }
            else -> const.toString()
        }
    }

    private fun getValue(const: Const): Any? {
        when (const.kind()) {
            Kind.PRIMITIVE -> {
                val value = const as Value
                return value.getValue()
            }
            // For cases like AnyClass.class, return the qualified name of AnyClass
            Kind.CLASS_LITERAL -> {
                val value = const as TurbineClassValue
                return value.type().toString()
            }
            Kind.ENUM_CONSTANT -> {
                val value = const as EnumConstantValue
                val temp =
                    getQualifiedName(value.sym().owner().binaryName()) + "." + value.toString()
                return temp
            }
            else -> {
                return const.toString()
            }
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
            { typeItemFactory, item, (_, tParam) ->
                createTypeParameterBounds(tParam, typeItemFactory).also { item.bounds = it }
            },
        )
    }

    /**
     * Create the [DefaultTypeParameterItem] without any bounds and register it so that any uses of
     * it within the type bounds, e.g. `<E extends Enum<E>>`, or from other type parameters within
     * the same [TypeParameterList] can be resolved.
     */
    private fun createTypeParameter(sym: TyVarSymbol, param: TyVarInfo): DefaultTypeParameterItem {
        val modifiers =
            TurbineModifierItem.create(codebase, 0, createAnnotations(param.annotations()))
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
            createClass(nestedClassSymbol, classItem, enclosingClassTypeItemFactory)
        }
    }

    /** This methods creates and sets the fields of a class */
    private fun createFields(
        classItem: DefaultClassItem,
        fields: ImmutableList<FieldInfo>,
        typeItemFactory: TurbineTypeItemFactory,
    ) {
        for (field in fields) {
            val annotations = createAnnotations(field.annotations())
            val flags = field.access()
            val decl = field.decl()
            val fieldModifierItem =
                TurbineModifierItem.create(
                    codebase,
                    flags,
                    annotations,
                )
            val isEnumConstant = (flags and TurbineFlag.ACC_ENUM) != 0
            val fieldValue = createInitialValue(field)
            val type =
                typeItemFactory.getFieldType(
                    underlyingType = field.type(),
                    itemAnnotations = annotations,
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

            val annotations = createAnnotations(method.annotations())
            val decl: MethDecl? = method.decl()
            val methodModifierItem =
                TurbineModifierItem.create(
                    codebase,
                    method.access(),
                    annotations,
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
                    extractAnnotationDefaultValue(method.defaultValue()!!, defaultValueExpr)
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
        parameterDecls: List<Tree.VarDecl>?,
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
            val annotations = createAnnotations(parameter.annotations())
            val parameterModifierItem =
                TurbineModifierItem.create(codebase, parameter.access(), annotations)
            val type =
                typeItemFactory.getMethodParameterType(
                    underlyingParameterType = parameter.type(),
                    itemAnnotations = annotations,
                    fingerprint = fingerprint,
                    parameterIndex = idx,
                    isVarArg = parameterModifierItem.isVarArg(),
                )
            // Get the [Tree.VarDecl] corresponding to the [ParamInfo], if available.
            val decl =
                if (parameterDecls != null && idx >= declaredParameterOffset)
                    parameterDecls.get(idx - declaredParameterOffset)
                else null

            val parameterItem =
                itemFactory.createParameterItem(
                    TurbineFileLocation.forTree(containingCallable.containingClass(), decl),
                    parameterModifierItem,
                    parameter.name(),
                    { item ->
                        // Java: Look for @ParameterName annotation
                        val modifiers = item.modifiers
                        val annotation = modifiers.findAnnotation(AnnotationItem::isParameterName)
                        annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
                    },
                    containingCallable,
                    idx,
                    type,
                    TurbineDefaultValue(parameterModifierItem),
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

            val annotations = createAnnotations(constructor.annotations())
            val decl: MethDecl? = constructor.decl()
            val constructorModifierItem =
                TurbineModifierItem.create(
                    codebase,
                    constructor.access(),
                    annotations,
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

    internal fun getQualifiedName(binaryName: String): String {
        return binaryName.replace('/', '.').replace('$', '.')
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

    private fun javadoc(item: Tree.TyDecl?): String {
        if (!codebase.allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun javadoc(item: Tree.VarDecl?): String {
        if (!codebase.allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun javadoc(item: Tree.MethDecl?): String {
        if (!codebase.allowReadingComments) return ""
        return item?.javadoc() ?: ""
    }

    private fun isDeprecated(javadoc: String?): Boolean {
        return javadoc?.contains("@deprecated") ?: false
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
                            getValue((expr as Literal).value())
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
    private fun getAnnotationDefaultExpression(method: MethodInfo): Tree? {
        val optExpr = method.decl()?.defaultValue()
        return if (optExpr != null && optExpr.isPresent()) optExpr.get() else null
    }

    /**
     * Extracts the default value of an annotation method and returns it as a string.
     *
     * @param const The constant object representing the annotation value.
     * @param expr An optional expression tree that might provide additional context for value
     *   extraction.
     * @return The default value of the annotation method as a string.
     */
    private fun extractAnnotationDefaultValue(const: Const, expr: Tree?): String {
        return when (const.kind()) {
            Kind.PRIMITIVE -> {
                when ((const as Value).constantTypeKind()) {
                    PrimKind.FLOAT -> {
                        val value = (const as Const.FloatValue).value()
                        when {
                            value == Float.POSITIVE_INFINITY -> "java.lang.Float.POSITIVE_INFINITY"
                            value == Float.NEGATIVE_INFINITY -> "java.lang.Float.NEGATIVE_INFINITY"
                            else -> value.toString() + "f"
                        }
                    }
                    PrimKind.DOUBLE -> {
                        val value = (const as Const.DoubleValue).value()
                        when {
                            value == Double.POSITIVE_INFINITY ->
                                "java.lang.Double.POSITIVE_INFINITY"
                            value == Double.NEGATIVE_INFINITY ->
                                "java.lang.Double.NEGATIVE_INFINITY"
                            else -> const.toString()
                        }
                    }
                    PrimKind.BYTE -> const.getValue().toString()
                    else -> const.toString()
                }
            }
            Kind.ARRAY -> {
                const as ArrayInitValue
                // This is case where defined type is array type but default value is
                // single non-array element
                // For e.g. char[] letter() default 'a';
                if (const.elements().count() == 1 && expr != null && !(expr is ArrayInit)) {
                    extractAnnotationDefaultValue(const.elements().single(), expr)
                } else getValue(const).toString()
            }
            Kind.CLASS_LITERAL -> getValue(const).toString() + ".class"
            else -> getValue(const).toString()
        }
    }

    internal fun getTypeElement(name: String): TypeElement? = turbineElements.getTypeElement(name)
}
