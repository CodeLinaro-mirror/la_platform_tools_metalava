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
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FixedFieldValue
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
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
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.FieldValue
import com.android.tools.metalava.model.item.ParameterDefaultValue
import com.android.tools.metalava.model.type.MethodFingerprint
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
import com.google.turbine.tree.Tree.TyDecl
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
 */
internal class TurbineClassBuilder(
    private val globalContext: TurbineGlobalContext,
    private val classSymbol: ClassSymbol,
    private val typeBoundClass: TypeBoundClass,
) : TurbineGlobalContext by globalContext {
    /** The [SourceTypeBoundClass] if this is a source class. */
    private val sourceTypeBoundClass = typeBoundClass as? SourceTypeBoundClass

    /**
     * The [TurbineFieldResolver] used for resolving [Tree.ConstVarName] to
     * [TypeBoundClass.FieldInfo].
     */
    private var fieldResolver: TurbineFieldResolver?

    init {
        if (sourceTypeBoundClass == null) {
            // Only source classes need to resolve fields.
            fieldResolver = null
        } else {
            // Source files need
            fieldResolver = createFieldResolver(classSymbol, sourceTypeBoundClass)
        }
    }

    /**
     * Create a [ClassItem] for the [classSymbol]/[typeBoundClass] pair.
     *
     * @param containingClassItem the containing [DefaultClassItem] to which the created [ClassItem]
     *   will belong, if any.
     * @param enclosingClassTypeItemFactory the [TurbineTypeItemFactory] that is used to create
     *   [TypeItem]s and tracks the in scope type parameters.
     */
    internal fun createClass(
        containingClassItem: DefaultClassItem?,
        enclosingClassTypeItemFactory: TurbineTypeItemFactory,
        origin: ClassOrigin,
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

    private fun getClassKind(type: TurbineTyKind): ClassKind {
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
            val nestedTypeBoundClass = typeBoundClassForSymbol(nestedClassSymbol)
            val nestedClassBuilder =
                TurbineClassBuilder(
                    globalContext = globalContext,
                    classSymbol = nestedClassSymbol,
                    typeBoundClass = nestedTypeBoundClass,
                )
            nestedClassBuilder.createClass(
                containingClassItem = classItem,
                enclosingClassTypeItemFactory = enclosingClassTypeItemFactory,
                origin = classItem.origin,
            )
        }
    }

    /** This method creates and sets the fields of a class */
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

            val initialFieldValueProvider =
                field.value()?.let { const ->
                    val turbineValue = TurbineValue(const, field.decl()?.init()?.getOrNull())
                    valueFactory.providerFor(type, turbineValue)
                }

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
                    initialValueProvider = initialFieldValueProvider,
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
            val defaultTurbineValue =
                method.defaultValue()?.let { defaultConst ->
                    TurbineValue(defaultConst, defaultValueExpr, fieldResolver)
                }

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

            val defaultValue = defaultTurbineValue?.getSourceForMethodDefault() ?: ""
            val defaultValueProvider =
                defaultTurbineValue?.let { valueFactory.providerFor(returnType, it) }

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
                    defaultValueProvider = defaultValueProvider,
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
                    defaultValueFactory = { ParameterDefaultValue.NONE },
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
}
