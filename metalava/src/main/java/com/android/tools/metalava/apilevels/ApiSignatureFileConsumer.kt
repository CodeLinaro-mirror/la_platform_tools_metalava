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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.text.ParsedParameter
import com.android.tools.metalava.model.text.ParsedRecordComponent
import com.android.tools.metalava.model.text.ParsedTypeParameter
import com.android.tools.metalava.model.text.SignatureFile
import com.android.tools.metalava.model.text.SignatureFileConsumer
import com.android.tools.metalava.model.type.TypeString
import java.nio.file.Path

/**
 * [SignatureFileConsumer] that populates an [Api] directly from parsed signature files using
 * [updater] without constructing a full [com.android.tools.metalava.model.Codebase].
 *
 * @param api the [Api] to populate.
 * @param updater the [ApiHistoryUpdater] for the API version being loaded.
 */
internal class ApiSignatureFileConsumer(
    private val api: Api,
    private val updater: ApiHistoryUpdater,
) : SignatureFileConsumer {

    /**
     * Tracks per-class state accumulated while parsing one or more signature files for a version.
     *
     * @property apiClass the [ApiClass] created or updated in [api].
     * @property classKind the [ClassKind] of the class.
     * @property isStatic whether the class has the `static` modifier.
     * @property outerInternalName the JVM internal name of the enclosing class if this is a nested
     *   class, or `null` for a top-level class.
     * @property effectivelyDeprecated whether the class or any enclosing class is deprecated.
     * @property typeScope the [ErasedTypeScope] including any type parameters declared on this
     *   class and its enclosing classes.
     * @property superClassName the internal name of the class's superclass, or `null` for
     *   `java/lang/Object`.
     * @property interfaceNames the internal names of the interfaces implemented by the class.
     */
    private class ClassContext(
        val apiClass: ApiClass,
        val classKind: ClassKind,
        val isStatic: Boolean,
        val outerInternalName: String?,
        var effectivelyDeprecated: Boolean,
        val typeScope: ErasedTypeScope,
        var superClassName: String?,
        var interfaceNames: List<String>,
    )

    /** Resolver for converting raw type strings into erased JVM internal names and descriptors. */
    private val typeDescriptorResolver = ErasedTypeDescriptorResolver()

    /** Whether [updater] has already updated [api] for this version. */
    private var updatedApi = false

    /** The dot-separated qualified name of the current `package` block. */
    private var currentPackageName: String = ""

    /** The `/`‑separated internal name of the current `package` block. */
    private var currentPackageInternalName: String = ""

    /** The [ClassContext] of the class currently being parsed. */
    private lateinit var currentClass: ClassContext

    /**
     * Map from dot-separated qualified class name to [ClassContext] for all classes parsed in this
     * version.
     */
    private val classesByQualifiedName = mutableMapOf<String, ClassContext>()

    override fun startFile(
        signatureFile: SignatureFile,
        path: Path,
        lineMap: LineMap,
        format: FileFormat,
    ) {
        // Record the version on the Api once before processing any files for this version.
        if (!updatedApi) {
            updater.update(api)
            updatedApi = true
        }
    }

    override fun finish() {
        // Apply the superclass and super-interfaces accumulated for each class.
        for (classContext in classesByQualifiedName.values) {
            val apiClass = classContext.apiClass
            classContext.superClassName?.let { superClass ->
                apiClass.updateSuperClass(superClass, updater)
            }
            for (interfaceName in classContext.interfaceNames) {
                apiClass.updateInterface(interfaceName, updater)
            }
        }
    }

    override fun parseModifierAnnotations(
        tokenStream: TokenStream,
        apiText: String,
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList {
        // Skip all modifier annotations without allocating AnnotationItem instances, while still
        // recognizing @Deprecated and @java.lang.Deprecated.
        var deprecated = false
        while (tokenStream.peekType() == SharedTokenType.AT) {
            if (skipAnnotationAndCheckDeprecated(tokenStream, apiText)) {
                deprecated = true
            }
        }
        val modifiers = createMutableModifiers(visibilityLevel)
        if (deprecated) {
            modifiers.setDeprecated(true)
        }
        return modifiers
    }

    /**
     * Skips a single `@QualifiedName(...)` annotation starting at the current `@` token in
     * [tokenStream], returning `true` if the annotation is `@Deprecated` or
     * `@java.lang.Deprecated`.
     */
    private fun skipAnnotationAndCheckDeprecated(
        tokenStream: TokenStream,
        apiText: String,
    ): Boolean {
        // Consume `@` and the first identifier of the annotation name.
        tokenStream.consume()
        val firstNameToken = tokenStream.consume()
        val nameStart = firstNameToken.startOffset
        var nameEnd = firstNameToken.endOffset
        // Consume any `.Identifier` segments of a qualified annotation name.
        while (tokenStream.match(SharedTokenType.DOT)) {
            nameEnd = tokenStream.consume().endOffset
        }
        // If the annotation has an argument list, consume balanced `(...)` tokens.
        if (tokenStream.match(SharedTokenType.PAREN_OPEN)) {
            var balance = 1
            while (balance > 0 && tokenStream.peekType() != SharedTokenType.EOF) {
                when (tokenStream.consume().type) {
                    SharedTokenType.PAREN_OPEN -> balance++
                    SharedTokenType.PAREN_CLOSE -> balance--
                    else -> {}
                }
            }
        }

        // Check whether the annotation name matches `Deprecated` or `java.lang.Deprecated` without
        // allocating a substring.
        val nameLength = nameEnd - nameStart
        return (nameLength == DEPRECATED_SIMPLE_NAME.length &&
            apiText.regionMatches(nameStart, DEPRECATED_SIMPLE_NAME, 0, nameLength)) ||
            (nameLength == DEPRECATED_QUALIFIED_NAME.length &&
                apiText.regionMatches(nameStart, DEPRECATED_QUALIFIED_NAME, 0, nameLength))
    }

    override fun startPackage(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
    ) {
        // Record both the dot-separated package name and its `/`‑separated JVM internal prefix.
        currentPackageName = name
        currentPackageInternalName = name.replace('.', '/')
    }

    override fun consumeTypeAlias(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        aliasedTypeString: TypeString,
    ) {
        // Kotlin type aliases do not exist in bytecode and are not tracked in Api.
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
        val qualifiedName =
            if (currentPackageName.isEmpty()) {
                fullName
            } else {
                "$currentPackageName.$fullName"
            }

        // If this class was already declared in an earlier signature file for the same version
        // (e.g., `public.txt` followed by `system.txt`), merge the delta declaration into the
        // existing ClassContext instead of replacing it.
        classesByQualifiedName[qualifiedName]?.let { existingClass ->
            if (modifiers.isDeprecated() && !existingClass.effectivelyDeprecated) {
                existingClass.effectivelyDeprecated = true
                api.updateClass(
                    existingClass.apiClass.name,
                    updater,
                    true,
                    classKind == ClassKind.ENUM,
                )
            }
            if (superClassTypeString != null) {
                existingClass.superClassName =
                    resolveClassTypeName(
                        superClassTypeString.type,
                        existingClass.typeScope,
                    )
            }
            if (interfaceTypeStrings.isNotEmpty()) {
                existingClass.interfaceNames =
                    resolveInterfaceNames(
                        classKind,
                        interfaceTypeStrings,
                        existingClass.typeScope,
                    )
            }
            currentClass = existingClass
            return
        }

        val lastDot = fullName.lastIndexOf('.')
        val internalFullName = if (lastDot == -1) fullName else fullName.replace('.', '$')
        val internalName =
            if (currentPackageInternalName.isEmpty()) {
                internalFullName
            } else {
                "$currentPackageInternalName/$internalFullName"
            }

        // If this is a nested class (`Outer.Inner`), resolve its enclosing class's internal name
        // and ClassContext so inner class constructors and inherited deprecation work accurately.
        val outerClass: ClassContext?
        val outerInternalName: String?
        if (lastDot == -1) {
            outerClass = null
            outerInternalName = null
        } else {
            val outerFullName = fullName.substring(0, lastDot)
            val outerQualifiedName =
                if (currentPackageName.isEmpty()) {
                    outerFullName
                } else {
                    "$currentPackageName.$outerFullName"
                }
            outerClass = classesByQualifiedName[outerQualifiedName]
            val outerInternalFullName = outerFullName.replace('.', '$')
            outerInternalName =
                if (currentPackageInternalName.isEmpty()) {
                    outerInternalFullName
                } else {
                    "$currentPackageInternalName/$outerInternalFullName"
                }
        }

        val parentScope = outerClass?.typeScope ?: ErasedTypeScope.Empty
        val classTypeScope = parentScope.extend(typeParameters, typeDescriptorResolver)

        val effectivelyDeprecated =
            modifiers.isDeprecated() || (outerClass?.effectivelyDeprecated == true)
        val classNameInApi = if (api.useInternalNames) internalName else qualifiedName
        val apiClass =
            api.updateClass(
                classNameInApi,
                updater,
                effectivelyDeprecated,
                classKind == ClassKind.ENUM,
            )

        // Resolve the explicit superclass if specified, or fall back to the default binary
        // superclass for this class kind. Every class kind except java/lang/Object itself has a
        // binary superclass (java/lang/Object, java/lang/Enum, or java/lang/Record).
        val superClassName =
            if (superClassTypeString != null) {
                resolveClassTypeName(
                    superClassTypeString.type,
                    classTypeScope,
                )
            } else {
                val useInternal = api.useInternalNames
                when (classKind) {
                    ClassKind.ENUM ->
                        if (useInternal) JAVA_LANG_ENUM_INTERNAL_NAME
                        else JAVA_LANG_ENUM_QUALIFIED_NAME
                    ClassKind.RECORD ->
                        if (useInternal) JAVA_LANG_RECORD_INTERNAL_NAME
                        else JAVA_LANG_RECORD_QUALIFIED_NAME
                    ClassKind.CLASS ->
                        if (qualifiedName == JAVA_LANG_OBJECT_QUALIFIED_NAME) null
                        else if (useInternal) JAVA_LANG_OBJECT_INTERNAL_NAME
                        else JAVA_LANG_OBJECT_QUALIFIED_NAME
                    ClassKind.INTERFACE,
                    ClassKind.ANNOTATION_TYPE ->
                        if (useInternal) JAVA_LANG_OBJECT_INTERNAL_NAME
                        else JAVA_LANG_OBJECT_QUALIFIED_NAME
                    ClassKind.TYPEALIAS -> null
                }
            }

        val interfaceNames =
            resolveInterfaceNames(
                classKind,
                interfaceTypeStrings,
                classTypeScope,
            )

        val classContext =
            ClassContext(
                apiClass = apiClass,
                classKind = classKind,
                isStatic = modifiers.isStatic(),
                outerInternalName = outerInternalName,
                effectivelyDeprecated = effectivelyDeprecated,
                typeScope = classTypeScope,
                superClassName = superClassName,
                interfaceNames = interfaceNames,
            )
        currentClass = classContext
        classesByQualifiedName[qualifiedName] = classContext
    }

    /**
     * Resolves a class or interface [rawType] string in [scope] into either an erased JVM internal
     * name (`pkg/Outer$Inner`) or a dot-separated qualified name (`pkg.Outer.Inner`), depending on
     * [Api.useInternalNames].
     */
    private fun resolveClassTypeName(
        rawType: String,
        scope: ErasedTypeScope,
    ): String =
        if (api.useInternalNames) {
            typeDescriptorResolver.resolveInternalName(rawType, scope)
        } else {
            typeDescriptorResolver.resolveQualifiedName(rawType, scope)
        }

    /**
     * Resolves any explicit [interfaceTypeStrings] in [classTypeScope], combining them with
     * `java.lang.annotation.Annotation` for annotation types.
     */
    private fun resolveInterfaceNames(
        classKind: ClassKind,
        interfaceTypeStrings: List<TypeString>,
        classTypeScope: ErasedTypeScope,
    ): List<String> {
        val implicitAnnotationInterface =
            if (api.useInternalNames) {
                JAVA_LANG_ANNOTATION_INTERNAL_NAME
            } else {
                JAVA_LANG_ANNOTATION_QUALIFIED_NAME
            }
        return if (interfaceTypeStrings.isEmpty()) {
            if (classKind == ClassKind.ANNOTATION_TYPE) {
                listOf(implicitAnnotationInterface)
            } else {
                emptyList()
            }
        } else {
            buildList(interfaceTypeStrings.size + 1) {
                if (classKind == ClassKind.ANNOTATION_TYPE) {
                    add(implicitAnnotationInterface)
                }
                for (interfaceTypeString in interfaceTypeStrings) {
                    add(
                        resolveClassTypeName(
                            interfaceTypeString.type,
                            classTypeScope,
                        )
                    )
                }
            }
        }
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
        // Private and package-private constructors are not included in the API history.
        if (modifiers.isPrivate() || modifiers.isPackagePrivate()) {
            return
        }

        val constructorScope = currentClass.typeScope.extend(typeParameters, typeDescriptorResolver)

        val signature =
            if (api.useInternalNames) {
                buildString {
                    append("<init>(")
                    // Non-static inner classes have an implicit leading parameter for their
                    // enclosing class instance in bytecode.
                    if (!currentClass.isStatic) {
                        currentClass.outerInternalName?.let { outerInternalName ->
                            append('L').append(outerInternalName).append(';')
                        }
                    }
                    for (parameter in parameters) {
                        typeDescriptorResolver.appendDescriptor(
                            this,
                            parameter.typeString.type,
                            scope = constructorScope,
                            isVarargs = parameter.modifiers.isVarArg(),
                        )
                    }
                    append(")V")
                }
            } else {
                formatSourceCallableSignature(name, typeParameters, parameters, constructorScope)
            }

        val effectivelyDeprecated = modifiers.isDeprecated() || currentClass.effectivelyDeprecated
        currentClass.apiClass.updateMethod(signature, updater, effectivelyDeprecated)
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
        // Private and package-private methods are not included in the API history.
        if (modifiers.isPrivate() || modifiers.isPackagePrivate()) {
            return
        }

        val methodScope = currentClass.typeScope.extend(typeParameters, typeDescriptorResolver)

        // Ignore synthetic enum methods (`values()`, `getEntries()`, and `valueOf(String)`),
        // matching SignatureFileCodebaseBuilder. Api.updateClass already adds `values()` and
        // `valueOf(String)` automatically for enums.
        if (currentClass.classKind == ClassKind.ENUM) {
            if (parameters.isEmpty() && (name == "values" || name == "getEntries")) {
                return
            }
            if (
                parameters.size == 1 &&
                    name == "valueOf" &&
                    !parameters[0].modifiers.isVarArg() &&
                    typeDescriptorResolver.resolveDescriptor(
                        parameters[0].typeString.type,
                        scope = methodScope,
                    ) == JAVA_LANG_STRING_DESCRIPTOR
            ) {
                return
            }
        }

        val signature =
            if (api.useInternalNames) {
                buildString {
                    append(name)
                    append('(')
                    for (parameter in parameters) {
                        typeDescriptorResolver.appendDescriptor(
                            this,
                            parameter.typeString.type,
                            scope = methodScope,
                            isVarargs = parameter.modifiers.isVarArg(),
                        )
                    }
                    append(')')
                    typeDescriptorResolver.appendDescriptor(
                        this,
                        returnTypeString.type,
                        scope = methodScope,
                    )
                }
            } else {
                formatSourceCallableSignature(name, typeParameters, parameters, methodScope)
            }

        val effectivelyDeprecated = modifiers.isDeprecated() || currentClass.effectivelyDeprecated
        currentClass.apiClass.updateMethod(signature, updater, effectivelyDeprecated)
    }

    /**
     * Formats a constructor or method signature in source style (`name<TypeParams>(param1,param2)`)
     * for [Api] instances where [Api.useInternalNames] is `false`.
     */
    private fun formatSourceCallableSignature(
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        scope: ErasedTypeScope,
    ): String = buildString {
        append(name)
        appendSourceTypeParameterList(this, typeParameters, scope)
        append('(')
        for ((index, parameter) in parameters.withIndex()) {
            if (index > 0) {
                append(',')
            }
            typeDescriptorResolver.appendSourceType(
                this,
                parameter.typeString.type,
                scope = scope,
                isVarargs = parameter.modifiers.isVarArg(),
                spaceBetweenTypeArguments = false,
            )
        }
        append(')')
    }

    /**
     * Appends the source-style representation of [typeParameters] (matching
     * [com.android.tools.metalava.model.TypeParameterList.toSource]) to [out].
     */
    private fun appendSourceTypeParameterList(
        out: StringBuilder,
        typeParameters: List<ParsedTypeParameter>,
        scope: ErasedTypeScope,
    ) {
        if (typeParameters.isEmpty()) return
        out.append('<')
        for ((index, param) in typeParameters.withIndex()) {
            if (index > 0) {
                out.append(", ")
            }
            if (param.isReified) {
                out.append("reified ")
            }
            out.append(param.name)
            if (param.bounds.isNotEmpty()) {
                val extendsStart = out.length
                out.append(" extends ")
                val firstBoundStart = out.length
                for ((boundIndex, bound) in param.bounds.withIndex()) {
                    if (boundIndex > 0) {
                        out.append(" & ")
                    }
                    typeDescriptorResolver.appendSourceType(
                        out,
                        bound.type,
                        scope = scope,
                        spaceBetweenTypeArguments = true,
                    )
                }
                if (
                    param.bounds.size == 1 &&
                        out.length - firstBoundStart == JAVA_LANG_OBJECT_QUALIFIED_NAME.length &&
                        out.regionMatches(
                            firstBoundStart,
                            JAVA_LANG_OBJECT_QUALIFIED_NAME,
                            0,
                            JAVA_LANG_OBJECT_QUALIFIED_NAME.length,
                        )
                ) {
                    out.setLength(extendsStart)
                }
            }
        }
        out.append('>')
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
        // Private and package-private fields are not included in the API history.
        if (modifiers.isPrivate() || modifiers.isPackagePrivate()) {
            return
        }

        // Field names in Api do not include their type descriptor or constant value, so updateField
        // can be called directly without parsing typeString or valueString.
        val effectivelyDeprecated = modifiers.isDeprecated() || currentClass.effectivelyDeprecated
        currentClass.apiClass.updateField(name, updater, effectivelyDeprecated)
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
        // Kotlin properties are represented by their getter/setter methods and backing fields in
        // bytecode and are not tracked separately in Api.
    }

    companion object {
        private const val DEPRECATED_SIMPLE_NAME = "Deprecated"
        private const val DEPRECATED_QUALIFIED_NAME = "java.lang.Deprecated"
        private const val JAVA_LANG_OBJECT_INTERNAL_NAME = "java/lang/Object"
        private const val JAVA_LANG_OBJECT_QUALIFIED_NAME = "java.lang.Object"
        private const val JAVA_LANG_ENUM_INTERNAL_NAME = "java/lang/Enum"
        private const val JAVA_LANG_ENUM_QUALIFIED_NAME = "java.lang.Enum"
        private const val JAVA_LANG_RECORD_INTERNAL_NAME = "java/lang/Record"
        private const val JAVA_LANG_RECORD_QUALIFIED_NAME = "java.lang.Record"
        private const val JAVA_LANG_ANNOTATION_INTERNAL_NAME = "java/lang/annotation/Annotation"
        private const val JAVA_LANG_ANNOTATION_QUALIFIED_NAME = "java.lang.annotation.Annotation"
        private const val JAVA_LANG_STRING_DESCRIPTOR = "Ljava/lang/String;"
    }
}
