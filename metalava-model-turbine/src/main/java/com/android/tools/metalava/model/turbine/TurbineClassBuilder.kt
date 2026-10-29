/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrVariableTypeItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ItemKind
import com.android.tools.metalava.model.ModifierContext
import com.android.tools.metalava.model.ModifierFlags
import com.android.tools.metalava.model.ModifierFlags.Companion.ABSTRACT
import com.android.tools.metalava.model.ModifierFlags.Companion.DEFAULT
import com.android.tools.metalava.model.ModifierFlags.Companion.FINAL
import com.android.tools.metalava.model.ModifierFlags.Companion.NATIVE
import com.android.tools.metalava.model.ModifierFlags.Companion.NON_SEALED
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
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SkeletonTypeParameterItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.WellKnownTypes
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.reporter.FileLocation
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass.FieldInfo
import com.google.turbine.binder.bound.TypeBoundClass.MethodInfo
import com.google.turbine.binder.bound.TypeBoundClass.ParamInfo
import com.google.turbine.binder.bound.TypeBoundClass.TyVarInfo
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.binder.sym.TyVarSymbol
import com.google.turbine.model.TurbineFlag
import com.google.turbine.model.TurbineTyKind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.Anno
import com.google.turbine.tree.Tree.AnnoExpr
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.tree.Tree.Literal
import com.google.turbine.tree.Tree.MethDecl
import com.google.turbine.tree.Tree.VarDecl
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type
import kotlin.jvm.optionals.getOrNull

/**
 * Responsible for creating [ClassItem]s from either source or binary [ClassSymbol] and
 * [TypeBoundClass] pairs.
 *
 * @param globalContext provides access to various pieces of data that apply across all classes.
 * @param classSymbol the unique identifier for the [TypeBoundClass].
 * @param typeBoundClass the definition of the class as recorded by Turbine.
 * @param origin the [ClassOrigin] of the class.
 */
internal class TurbineClassBuilder(
    private val globalContext: TurbineGlobalContext,
    private val classSymbol: ClassSymbol,
    private val typeBoundClass: TypeBoundClass,
    private val origin: ClassOrigin,
) : TurbineGlobalContext by globalContext {
    /** The [SourceTypeBoundClass] if this is a source class. */
    private val sourceTypeBoundClass = typeBoundClass as? SourceTypeBoundClass

    /** The [FieldResolver] used for resolving [Tree.ConstVarName] to [TypeBoundClass.FieldInfo]. */
    private var fieldResolver = createFieldResolver(classSymbol, typeBoundClass)

    /**
     * Create a [ClassItem] for the [classSymbol]/[typeBoundClass] pair.
     *
     * The parameters are on this method rather than the [TurbineClassBuilder] constructor because
     * these only apply to the [ClassItem] that this builds, not to all the members or the nested
     * classes. Adding these as constructor properties would confuse that code and possibly lead to
     * errors if the wrong instance was used.
     *
     * @param containingClassItem the containing [ClassItem] to which the created [ClassItem] will
     *   belong, if any.
     * @param enclosingClassTypeItemFactory the [TurbineTypeItemFactory] that is used to create
     *   [TypeItem]s and tracks the in scope type parameters.
     */
    internal fun createClass(
        containingClassItem: ClassItem?,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ): ClassItem {
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
        val classKind = getClassKind(typeBoundClass.kind())
        val modifiers =
            createModifiers(
                ModifierContext.forClassKind(classKind),
                typeBoundClass.access(),
                typeBoundClass.annotations(),
            )
        val (typeParameters, classTypeItemFactory) =
            createTypeParameters(
                typeBoundClass.typeParameterTypes(),
                enclosingClassTypeItemFactory,
                "class $qualifiedName",
            )

        modifiers.setSynchronized(false) // A class can not be synchronized in java

        if (classKind == ClassKind.ANNOTATION_TYPE) {
            if (!modifiers.hasAnnotation(AnnotationItem::isRetention)) {
                modifiers.addDefaultRetentionPolicyAnnotation(codebase, isKotlin = false)
            }
        }

        // Set up the SuperClass
        val superClassType =
            // Only use the super class type if the class kind allows explicit super class type to
            // be specified, or it has an implicit super class.
            if (classKind.allowsExplicitSuperClass || classKind.implicitSuperClassType != null) {
                typeBoundClass.superClassType()?.let { classTypeItemFactory.getSuperClassType(it) }
            } else {
                null
            }

        // Set interface types
        val interfaceTypes =
            typeBoundClass.interfaceTypes().map { classTypeItemFactory.getInterfaceType(it) }

        // The sorted permits list.
        val permitTypes =
            typeBoundClass
                .permits()
                .map {
                    val type = Type.ClassTy.asNonParametricClassTy(it)
                    classTypeItemFactory.getHierarchicalClassType(type)
                }
                .sortedWith(TypeItem.qualifiedComparator)

        val classItem =
            itemFactory.createClassItem(
                fileLocation = fileLocation,
                modifiers = modifiers,
                documentationFactory = itemDocumentationFactoryForDecl(decl),
                source = sourceFile,
                classKind = classKind,
                containingClass = containingClassItem,
                containingPackage = pkgItem,
                qualifiedName = qualifiedName,
                typeParameterList = typeParameters,
                origin = origin,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
                permitTypes = permitTypes,
                recordComponentItemsFactory =
                    if (classKind == ClassKind.RECORD)
                        { classItem ->
                            createRecordComponents(
                                classItem,
                                typeBoundClass.components(),
                                classTypeItemFactory
                            )
                        }
                    else {
                        null
                    },
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

    /**
     * Create modifiers for [modifierContext] from the set of access flags [flag] and [annoInfos].
     */
    private fun createModifiers(
        modifierContext: ModifierContext,
        flag: Int,
        annoInfos: List<AnnoInfo>,
    ): MutableModifierList {
        val annotations = annotationFactory.createAnnotations(annoInfos, fieldResolver)
        val modifiers =
            when (flag) {
                0 -> { // No Modifier. Default modifier is PACKAGE_PRIVATE in such case
                    createMutableModifiers(
                        visibility = VisibilityLevel.PACKAGE_PRIVATE,
                        annotations = annotations,
                    )
                }
                else -> {
                    createMutableModifiers(
                        modifierContext.normalizeFlags(computeFlag(flag), SourceLanguage.JAVA),
                        annotations,
                    )
                }
            }
        modifiers.setDeprecated(isDeprecated(annotations))

        // Set exhaustivity as true until proven otherwise by an inaccessible subclass.
        if (modifiers.isSealed()) {
            modifiers.setExhaustive(true)
        }

        return modifiers
    }

    /**
     * Given flag value corresponding to Turbine modifiers compute the equivalent [ModifierFlags].
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
        if (flag and TurbineFlag.ACC_NON_SEALED != 0) {
            result = result or NON_SEALED
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

    private fun getClassKind(type: TurbineTyKind): ClassKind {
        return when (type) {
            TurbineTyKind.INTERFACE -> ClassKind.INTERFACE
            TurbineTyKind.ENUM -> ClassKind.ENUM
            TurbineTyKind.ANNOTATION -> ClassKind.ANNOTATION_TYPE
            TurbineTyKind.RECORD -> ClassKind.RECORD
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
        return enclosingClassTypeItemFactory.createTypeParameterItemsAndFactory(
            description,
            tyParams.toList(),
            { (sym, tyParam) -> createTypeParameter(sym, tyParam) },
            { typeItemFactory, (_, tParam) -> createTypeParameterBounds(tParam, typeItemFactory) },
        )
    }

    /**
     * Create the [SkeletonTypeParameterItem] without any bounds and register it so that any uses of
     * it within the type bounds, e.g. `<E extends Enum<E>>`, or from other type parameters within
     * the same [TypeParameterList] can be resolved.
     */
    private fun createTypeParameter(sym: TyVarSymbol, param: TyVarInfo): SkeletonTypeParameterItem {
        val modifiers =
            createModifiers(
                ModifierContext.forItemKind(ItemKind.TYPE_PARAMETER),
                0,
                param.annotations(),
            )
        val typeParamItem =
            itemFactory.createTypeParameterItem(
                modifiers,
                name = sym.name(),
                // Java does not supports reified generics
                isReified = false,
            )
        return typeParamItem
    }

    /** Create the bounds of a [SkeletonTypeParameterItem]. */
    private fun createTypeParameterBounds(
        param: TyVarInfo,
        typeItemFactory: TurbineTypeItemFactory,
    ): List<BoundsTypeItem> {
        val upperBounds = param.upperBound().bounds()
        val lowerBound = param.lowerBound()

        if (upperBounds.isEmpty() && lowerBound == null) {
            return WellKnownTypes.defaultTypeParameterBounds(forKotlin = false)
        }

        return buildList {
            upperBounds.mapTo(this) { typeItemFactory.getBoundsType(it) }
            lowerBound?.let { add(typeItemFactory.getBoundsType(it)) }
        }
    }

    /** This method sets up the nested class hierarchy. */
    private fun createNestedClasses(
        classItem: SkeletonClassItem,
        nestedClasses: ImmutableList<ClassSymbol>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        for (nestedClassSymbol in nestedClasses) {
            val nestedTypeBoundClass =
                typeBoundClassForSymbol(nestedClassSymbol)
                    ?: error("Cannot find type bound class for nested class $nestedClassSymbol")
            val nestedClassBuilder =
                TurbineClassBuilder(
                    globalContext = globalContext,
                    classSymbol = nestedClassSymbol,
                    typeBoundClass = nestedTypeBoundClass,
                    origin = origin,
                )
            nestedClassBuilder.createClass(
                containingClassItem = classItem,
                enclosingClassTypeItemFactory = enclosingClassTypeItemFactory,
            )
        }
    }

    /** This method creates and sets the fields of a class */
    private fun createFields(
        classItem: SkeletonClassItem,
        fields: ImmutableList<FieldInfo>,
        typeItemFactory: TurbineTypeItemFactory,
    ) {
        val ignorePrivateMemberFields = classItem.classKind == ClassKind.RECORD
        for (field in fields) {
            val flags = field.access()
            val decl = field.decl()
            val fieldmodifiers =
                createModifiers(
                    ModifierContext.forItemKind(ItemKind.FIELD),
                    flags,
                    field.annotations(),
                )

            // Ignore private member fields in records.
            if (
                ignorePrivateMemberFields &&
                    fieldmodifiers.isPrivate() &&
                    !fieldmodifiers.isStatic()
            )
                continue

            val isEnumConstant = (flags and TurbineFlag.ACC_ENUM) != 0
            val type =
                typeItemFactory.getFieldType(
                    underlyingType = field.type(),
                    itemAnnotations = fieldmodifiers.annotations(),
                    isEnumConstant = isEnumConstant,
                    isFinal = fieldmodifiers.isFinal(),
                    isInitialValueNonNull = {
                        // The initial value is non-null if the value is a literal which is not
                        // null.
                        isInitialValueNonNull(field)
                    }
                )

            val constantValueProvider =
                field.value()?.let { const ->
                    // In Java fields have to be static and final in order for them to have a
                    // constant value
                    if (!fieldmodifiers.isStatic() || !fieldmodifiers.isFinal()) {
                        return@let null
                    }
                    val expr = field.decl()?.init()?.getOrNull()
                    val turbineValue = TurbineValue(const, expr, fieldResolver)
                    valueFactory.providerFor(type, turbineValue, ValueUseSite.FIELD)
                }

            val fieldItem =
                itemFactory.createFieldItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = fieldmodifiers,
                    documentationFactory = itemDocumentationFactoryForDecl(decl),
                    name = field.name(),
                    containingClass = classItem,
                    type = type,
                    isEnumConstant = isEnumConstant,
                    constantValueProvider = constantValueProvider,
                )

            classItem.addField(fieldItem)
        }
    }

    /** Check if this [MethodInfo] is one of the methods defined in the [Record] class. */
    private fun MethodInfo.isRecordClassMethod(): Boolean {
        val name = name()
        val parameters = parameters()
        return when (name) {
            "hashCode",
            "toString" -> parameters.isEmpty()
            "equals" -> parameters.size == 1 && parameters[0].type() == Type.ClassTy.OBJECT
            else -> false
        }
    }

    private fun createMethods(
        classItem: SkeletonClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        for (method in methods) {
            // Ignore constructors.
            if (method.sym().name() == "<init>") continue

            val decl: MethDecl? = method.decl()

            // Ignore any implicit implementations of Record class methods.
            val isRecordClass = classItem.classKind == ClassKind.RECORD
            if (isRecordClass && decl == null && method.isRecordClassMethod()) {
                continue
            }

            val methodmodifiers =
                createModifiers(
                    ModifierContext.forItemKind(ItemKind.METHOD),
                    method.access(),
                    method.annotations(),
                )

            // Final modifier is superfluous on a method in a final class.
            if (methodmodifiers.isFinal() && classItem.modifiers.isFinal()) {
                methodmodifiers.setFinal(false)
            }

            val name = method.name()
            val (typeParams, methodTypeItemFactory) =
                createTypeParameters(
                    method.tyParams(),
                    enclosingClassTypeItemFactory,
                    name,
                )
            val defaultValueExpr = getAnnotationDefaultExpression(method)
            val defaultTurbineValue =
                method.defaultValue()?.let { defaultConst ->
                    TurbineValue(defaultConst, defaultValueExpr, fieldResolver)
                }

            val parameters = method.parameters()
            val fingerprint = MethodFingerprint(name, parameters.size)
            val isAnnotationElement = classItem.isAnnotationType() && !methodmodifiers.isStatic()
            val returnType =
                methodTypeItemFactory.getMethodReturnType(
                    underlyingReturnType = method.returnType(),
                    itemAnnotations = methodmodifiers.annotations(),
                    fingerprint = fingerprint,
                    isAnnotationElement = isAnnotationElement,
                )

            val defaultValueProvider =
                defaultTurbineValue?.let {
                    valueFactory.providerFor(returnType, it, ValueUseSite.ANNOTATION)
                }

            val methodItem =
                itemFactory.createMethodItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = methodmodifiers,
                    documentationFactory = itemDocumentationFactoryForDecl(decl),
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
                    defaultValueProvider = defaultValueProvider,
                    isExtensionMethod = false, // Java does not support extension methods
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
        val ignoreSynthetic = containingCallable is ConstructorItem
        return buildList {
            var parameterIndex = 0
            for (parameter in parameters) {
                if (ignoreSynthetic && parameter.synthetic()) continue
                val parametermodifiers =
                    createModifiers(
                            ModifierContext.forItemKind(ItemKind.PARAMETER),
                            parameter.access(),
                            parameter.annotations(),
                        )
                        .toImmutable()
                val type =
                    typeItemFactory.getMethodParameterType(
                        underlyingParameterType = parameter.type(),
                        itemAnnotations = parametermodifiers.annotations(),
                        fingerprint = fingerprint,
                        parameterIndex = parameterIndex,
                        isVarArg = parametermodifiers.isVarArg(),
                    )
                // Get the [Tree.VarDecl] corresponding to the [ParamInfo], if available.
                // [parameterDecls] will be null for a binary class. It will be empty for a
                // record class.
                val decl =
                    if (parameterDecls != null && parameterIndex < parameterDecls.size)
                        parameterDecls.get(parameterIndex)
                    else null

                val fileLocation =
                    TurbineFileLocation.forTree(containingCallable.containingClass(), decl)
                val parameterItem =
                    itemFactory.createParameterItem(
                        fileLocation = fileLocation,
                        modifiers = parametermodifiers,
                        name = parameter.name(),
                        publicName = null,
                        containingItem = containingCallable,
                        parameterIndex = parameterIndex,
                        type = type,
                        // Java parameters can't have default values
                        hasDefaultValue = false,
                        // Java only has value parameters
                        kind = ParameterKind.VALUE,
                    )
                add(parameterItem)
                parameterIndex += 1
            }
        }
    }

    /**
     * Return true if constructors for [classItem] will have an implicit first parameter that needs
     * removing.
     *
     * Constructors of inner classes that are loaded from the class path will have an implicit
     * parameter that is used to supply the reference to the instance of the containing class to
     * which the inner class instance belongs.
     */
    private fun willConstructorFirstParameterBeImplicit(classItem: SkeletonClassItem): Boolean {
        // Check to see whether the class is an inner class, i.e. is a non-static class nested
        // inside another.
        val isInnerClass = classItem.containingClass() != null && !classItem.modifiers.isStatic()

        return isInnerClass && classItem.origin == ClassOrigin.CLASS_PATH
    }

    private fun createConstructors(
        classItem: SkeletonClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
    ) {
        // An abstract sealed class cannot be instantiated directly so treat its constructors as if
        // they were private.
        val treatConstructorsAsPrivate =
            classItem.modifiers.let { modifiers -> modifiers.isSealed() && modifiers.isAbstract() }

        // Check to see if the first parameter of the constructors is implicit and need removing.
        val firstParameterIsImplicit = willConstructorFirstParameterBeImplicit(classItem)

        for (constructor in methods) {
            // Skip real methods.
            if (constructor.sym().name() != "<init>") continue

            // Get the source declaration for the constructor. Will be null for constructors loaded
            // from the class path.
            val decl: MethDecl? = constructor.decl()

            // Get the constructor parameters, removing an implicit first parameter if necessary.
            val constructorParameters =
                constructor.parameters().let { parameters ->
                    if (firstParameterIsImplicit) {
                        parameters.subList(1, parameters.size)
                    } else {
                        parameters
                    }
                }

            val modifiers =
                createModifiers(
                    ModifierContext.forItemKind(ItemKind.CONSTRUCTOR),
                    constructor.access(),
                    constructor.annotations(),
                )
            if (treatConstructorsAsPrivate) {
                modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
            }
            val (typeParams, constructorTypeItemFactory) =
                createTypeParameters(
                    constructor.tyParams(),
                    enclosingClassTypeItemFactory,
                    constructor.name(),
                )
            val isImplicitDefaultConstructor =
                (constructor.access() and TurbineFlag.ACC_SYNTH_CTOR) != 0
            val name = classItem.simpleName()

            val constructorItem =
                itemFactory.createConstructorItem(
                    fileLocation = TurbineFileLocation.forTree(classItem, decl),
                    modifiers = modifiers,
                    documentationFactory = itemDocumentationFactoryForDecl(decl),
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
                            constructorParameters,
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

    private fun getThrowsList(
        throwsTypes: List<Type>,
        enclosingTypeItemFactory: TurbineTypeItemFactory
    ) =
        throwsTypes
            .map { type -> enclosingTypeItemFactory.getExceptionType(type) }
            // We're sorting the names here even though outputs typically do their own sorting,
            // since for example the MethodItem.sameSignature check wants to do an
            // element-by-element comparison to see if the signature matches, and that should match
            // overrides even if they specify their elements in different orders.
            .sortedWith(ClassOrVariableTypeItem.fullNameComparator)

    /**
     * Create the [PropertyItem]s used to model record components.
     *
     * Must be called before creating any other members of [classItem].
     */
    private fun createRecordComponents(
        classItem: ClassItem,
        components: List<TypeBoundClass.RecordComponentInfo>,
        classTypeItemFactory: TurbineTypeItemFactory,
    ) =
        components.mapIndexed { index, componentInfo ->
            val modifiers =
                createModifiers(
                    ModifierContext.forItemKind(ItemKind.RECORD_COMPONENT),
                    componentInfo.access(),
                    componentInfo.annotations(),
                )
            modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)

            val type = classTypeItemFactory.getGeneralType(componentInfo.type())

            itemFactory.createRecordComponentItem(
                fileLocation = classItem.fileLocation,
                modifiers = modifiers,
                name = componentInfo.name(),
                containingClass = classItem,
                type = type,
                recordComponentIndex = index,
            )
        }

    /**
     * Check to see whether the initial value for [field] is non-null.
     *
     * If it is `non-null` then the field itself can be treated as if it is non-null, i.e. as if it
     * had an `@NonNull` annotation.
     */
    private fun isInitialValueNonNull(field: FieldInfo): Boolean {
        val optExpr = field.decl()?.init()
        val expr = if (optExpr != null && optExpr.isPresent) optExpr.get() else null
        val constantValue = field.value()?.value

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

        return initialValueWithoutRequiredConstant != null
    }

    /**
     * Extracts the expression corresponding to the default value of a given annotation method. If
     * the method does not have a default value, returns null.
     */
    private fun getAnnotationDefaultExpression(method: MethodInfo) =
        method.decl()?.defaultValue()?.getOrNull()?.let { defaultTree ->

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
}
