/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.metalava.model.AnnotationItem.Companion.unshortenAnnotation
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.JAVA_LANG_ENUM
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.JAVA_LANG_THROWABLE
import com.android.tools.metalava.model.MetalavaApi
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ThrowableType
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.isNullableAnnotation
import com.android.tools.metalava.model.isNullnessAnnotation
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.noOpAnnotationManager
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import kotlin.text.Charsets.UTF_8

@MetalavaApi
class ApiFile
private constructor(
    private val formatForLegacyFiles: FileFormat?,
) : ResolverContext {

    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. ? suffix means nullable, !
     * suffix means unknown, and absence of a suffix means not nullable.
     *
     * Updated based on the header of the signature file being parsed.
     */
    private var kotlinStyleNulls: Boolean = false

    /** The file format of the file being parsed. */
    lateinit var format: FileFormat

    private val mClassToSuper = HashMap<TextClassItem, String>(30000)
    private val mClassToInterface = HashMap<TextClassItem, MutableSet<String>>(10000)

    companion object {
        /**
         * Same as `parseApi(List<File>, ...)`, but takes a single file for convenience.
         *
         * @param file input signature file
         */
        fun parseApi(
            file: File,
            annotationManager: AnnotationManager,
            description: String? = null,
        ) =
            parseApi(
                files = listOf(file),
                annotationManager = annotationManager,
                description = description,
            )

        /**
         * Read API signature files into a [TextCodebase].
         *
         * Note: when reading from them multiple files, [TextCodebase.location] would refer to the
         * first file specified. each [com.android.tools.metalava.model.text.TextItem.position]
         * would correctly point out the source file of each item.
         *
         * @param files input signature files
         */
        fun parseApi(
            files: List<File>,
            annotationManager: AnnotationManager = noOpAnnotationManager,
            description: String? = null,
            classResolver: ClassResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
        ): Codebase {
            require(files.isNotEmpty()) { "files must not be empty" }
            val api =
                TextCodebase(
                    location = files[0],
                    annotationManager = annotationManager,
                    classResolver = classResolver,
                )
            val actualDescription =
                description
                    ?: buildString {
                        append("Codebase loaded from ")
                        files.joinTo(this)
                    }
            val parser = ApiFile(formatForLegacyFiles)
            var first = true
            for (file in files) {
                val apiText: String =
                    try {
                        file.readText(UTF_8)
                    } catch (ex: IOException) {
                        throw ApiParseException(
                            "Error reading API file",
                            file = file.path,
                            cause = ex
                        )
                    }
                parser.parseApiSingleFile(api, !first, file.path, apiText)
                first = false
            }
            api.description = actualDescription
            parser.postProcess(api)
            return api
        }

        /** <p>DO NOT MODIFY - used by com/android/gts/api/ApprovedApis.java */
        @Deprecated("Exists only for external callers.")
        @JvmStatic
        @MetalavaApi
        @Throws(ApiParseException::class)
        fun parseApi(
            filename: String,
            apiText: String,
            @Suppress("UNUSED_PARAMETER") kotlinStyleNulls: Boolean?,
        ): Codebase {
            return parseApi(
                filename,
                apiText,
            )
        }

        /**
         * Parse the API signature file from the [inputStream].
         *
         * This will consume the whole contents of the [inputStream] but it is the caller's
         * responsibility to close it.
         */
        @JvmStatic
        @MetalavaApi
        @Throws(ApiParseException::class)
        fun parseApi(filename: String, inputStream: InputStream): Codebase {
            val apiText = inputStream.bufferedReader().readText()
            return parseApi(filename, apiText)
        }

        /** Entry point for testing. Take a filename and content separately. */
        fun parseApi(
            filename: String,
            apiText: String,
            classResolver: ClassResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
        ): Codebase {
            val api =
                TextCodebase(
                    location = File(filename),
                    annotationManager = noOpAnnotationManager,
                    classResolver = classResolver,
                )
            api.description = "Codebase loaded from $filename"
            val parser = ApiFile(formatForLegacyFiles)
            parser.parseApiSingleFile(api, false, filename, apiText)
            parser.postProcess(api)
            return api
        }
    }

    /**
     * Perform any final steps to initialize the [TextCodebase] after parsing the signature files.
     */
    private fun postProcess(api: TextCodebase) {
        // Use this as the context for resolving references.
        ReferenceResolver.resolveReferences(this, api)
    }

    private fun parseApiSingleFile(
        api: TextCodebase,
        appending: Boolean,
        filename: String,
        apiText: String,
    ) {
        // Parse the header of the signature file to determine the format. If the signature file is
        // empty then `parseHeader` will return null, so it will default to `FileFormat.V2`.
        format =
            FileFormat.parseHeader(filename, StringReader(apiText), formatForLegacyFiles)
                ?: FileFormat.V2
        kotlinStyleNulls = format.kotlinStyleNulls
        api.typeResolver.kotlinStyleNulls = kotlinStyleNulls

        if (appending) {
            // When we're appending, and the content is empty, nothing to do.
            if (apiText.isBlank()) {
                return
            }
        }

        val tokenizer = Tokenizer(filename, apiText.toCharArray())
        while (true) {
            val token = tokenizer.getToken() ?: break
            // TODO: Accept annotations on packages.
            if ("package" == token) {
                parsePackage(api, tokenizer)
            } else {
                throw ApiParseException("expected package got $token", tokenizer)
            }
        }
    }

    private fun parsePackage(api: TextCodebase, tokenizer: Tokenizer) {
        var token: String = tokenizer.requireToken()

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        val modifiers = DefaultModifierList(api, DefaultModifierList.PUBLIC, null)
        modifiers.addAnnotations(annotations)
        token = tokenizer.current
        tokenizer.assertIdent(token)
        val name: String = token

        // If the same package showed up multiple times, make sure they have the same modifiers.
        // (Packages can't have public/private/etc., but they can have annotations, which are part
        // of ModifierList.)
        val existing = api.findPackage(name)
        val pkg =
            if (existing != null) {
                if (modifiers != existing.modifiers) {
                    throw ApiParseException(
                        String.format(
                            "Contradicting declaration of package %s. Previously seen with modifiers \"%s\", but now with \"%s\"",
                            name,
                            existing.modifiers,
                            modifiers
                        ),
                        tokenizer
                    )
                }
                existing
            } else {
                val newPackageItem = TextPackageItem(api, name, modifiers, tokenizer.pos())
                api.addPackage(newPackageItem)
                newPackageItem
            }

        token = tokenizer.requireToken()
        if ("{" != token) {
            throw ApiParseException("expected '{' got $token", tokenizer)
        }
        while (true) {
            token = tokenizer.requireToken()
            if ("}" == token) {
                break
            } else {
                parseClass(api, pkg, tokenizer, token)
            }
        }
    }

    private fun mapClassToSuper(classInfo: TextClassItem, superclass: String?) {
        superclass?.let { mClassToSuper.put(classInfo, superclass) }
    }

    private fun mapClassToInterface(classInfo: TextClassItem, interfaceTypeString: String) {
        mClassToInterface.computeIfAbsent(classInfo) { mutableSetOf() }.add(interfaceTypeString)
    }

    /** Implements [ResolverContext] interface */
    override fun superInterfaceTypeStrings(cl: ClassItem): Set<String>? = mClassToInterface[cl]

    /** Implements [ResolverContext] interface */
    override fun superClassTypeString(cl: ClassItem): String? = mClassToSuper[cl]

    private fun parseClass(
        api: TextCodebase,
        pkg: TextPackageItem,
        tokenizer: Tokenizer,
        startingToken: String
    ) {
        var token = startingToken
        var classKind = ClassKind.CLASS
        var superClassTypeString: String? = null

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(api, tokenizer, token, annotations)

        // Remember this position as this seems like a good place to use to report issues with the
        // class item.
        val classPosition = tokenizer.pos()

        token = tokenizer.current
        when (token) {
            "class" -> {
                token = tokenizer.requireToken()
            }
            "interface" -> {
                classKind = ClassKind.INTERFACE
                modifiers.setAbstract(true)
                token = tokenizer.requireToken()
            }
            "@interface" -> {
                classKind = ClassKind.ANNOTATION_TYPE
                modifiers.setAbstract(true)
                token = tokenizer.requireToken()
            }
            "enum" -> {
                classKind = ClassKind.ENUM
                modifiers.setFinal(true)
                modifiers.setStatic(true)
                superClassTypeString = JAVA_LANG_ENUM
                token = tokenizer.requireToken()
            }
            else -> {
                throw ApiParseException("missing class or interface. got: $token", tokenizer)
            }
        }
        tokenizer.assertIdent(token)

        // The declaredClassType consists of the full name (i.e. preceded by the containing class's
        // full name followed by a '.' if there is one) plus the type parameter string.
        val declaredClassType: String = token

        token = tokenizer.requireToken()

        if ("extends" == token && classKind != ClassKind.INTERFACE) {
            superClassTypeString = parseSuperTypeString(tokenizer, tokenizer.requireToken())
            token = tokenizer.current
        }

        val interfaceTypeStrings = mutableSetOf<String>()
        if ("implements" == token || "extends" == token) {
            token = tokenizer.requireToken()
            while (true) {
                if ("{" == token) {
                    break
                } else if ("," != token) {
                    val interfaceTypeString = parseSuperTypeString(tokenizer, token)
                    interfaceTypeStrings.add(interfaceTypeString)
                    token = tokenizer.current
                } else {
                    token = tokenizer.requireToken()
                }
            }
        }
        if (JAVA_LANG_ENUM == superClassTypeString) {
            // This can be taken either for an enum class, or a normal class that extends
            // java.lang.Enum (which was the old way of representing an enum in the API signature
            // files.
            classKind = ClassKind.ENUM
        } else if (classKind == ClassKind.ANNOTATION_TYPE) {
            // If the annotation was defined using @interface that add the implicit
            // "implements java.lang.annotation.Annotation".
            interfaceTypeStrings.add(JAVA_LANG_ANNOTATION)
        } else if (JAVA_LANG_ANNOTATION in interfaceTypeStrings) {
            // This can be taken either for a normal class that implements
            // java.lang.annotation.Annotation which was the old way of representing an annotation
            // in the API signature files.
            classKind = ClassKind.ANNOTATION_TYPE
        }

        if ("{" != token) {
            throw ApiParseException("expected {, was $token", tokenizer)
        }

        // Extract the full name and type parameters from declaredClassType.
        val (className, fullName, qualifiedClassName, outerClass, typeParameters) =
            parseDeclaredClassType(api, declaredClassType, pkg)

        // Above we marked all enums as static but for a top level class it's implicit
        if (classKind == ClassKind.ENUM && !fullName.contains(".")) {
            modifiers.setStatic(false)
        }

        // Get the characteristics of the class being added as they may be needed to compare against
        // the characteristics of the same class from a previously processed signature file.
        val newClassCharacteristics =
            ClassCharacteristics(
                position = classPosition,
                qualifiedName = qualifiedClassName,
                fullName = fullName,
                classKind = classKind,
                modifiers = modifiers,
                superClassTypeString = superClassTypeString,
            )

        // Check to see if there is an existing class, if so merge this class definition into that
        // one and return. Otherwise, drop through and create a whole new class.
        if (tryMergingIntoExistingClass(api, tokenizer, newClassCharacteristics)) {
            return
        }

        // Create the TextClassItem and set its package but do not add it to the package or
        // register it.
        val cl =
            TextClassItem(
                codebase = api,
                position = classPosition,
                modifiers = modifiers,
                classKind = classKind,
                qualifiedName = qualifiedClassName,
                simpleName = className,
                fullName = fullName,
                annotations = annotations,
                typeParameterList = typeParameters,
            )
        cl.setContainingPackage(pkg)
        cl.containingClass = outerClass
        if (outerClass == null) {
            // Add the class to the package, it will only be added to the TextCodebase once the
            // package
            // body has been parsed.
            pkg.addClass(cl)
        } else {
            outerClass.addInnerClass(cl)
        }
        api.registerClass(cl)

        // Record the super class type string as needing to be resolved for this class.
        mapClassToSuper(cl, superClassTypeString)

        // Add the interface type strings to the set that need to be resolved for this class. This
        // is added before possibly replacing the newly created class with an existing one in which
        // case these interface type strings will be ignored.
        for (interfaceTypeString in interfaceTypeStrings) {
            mapClassToInterface(cl, interfaceTypeString)
        }

        // Parse the class body adding each member created to the class item being populated.
        parseClassBody(api, tokenizer, cl)
    }

    /**
     * Try merging the new class into an existing class that was previously loaded from a separate
     * signature file.
     *
     * Will throw an exception if there is an existing class but it is not compatible with the new
     * class.
     *
     * @return `false` if there is no existing class, `true` if there is and the merge succeeded.
     */
    private fun tryMergingIntoExistingClass(
        api: TextCodebase,
        tokenizer: Tokenizer,
        newClassCharacteristics: ClassCharacteristics,
    ): Boolean {
        // Check for the existing class from a previously parsed file. If it could not be found
        // then return.
        val existingClass =
            api.findClassInCodebase(newClassCharacteristics.qualifiedName) ?: return false

        // Make sure the new class characteristics are compatible with the old class
        // characteristic.
        val existingCharacteristics = ClassCharacteristics.of(existingClass)
        if (!existingCharacteristics.isCompatible(newClassCharacteristics)) {
            throw ApiParseException(
                "Incompatible $existingClass definitions",
                newClassCharacteristics.position
            )
        }

        // Use the latest super class.
        val newSuperClassTypeString = newClassCharacteristics.superClassTypeString
        if (mClassToSuper[existingClass] != newSuperClassTypeString) {
            // Duplicate class with conflicting superclass names are found. Since the class
            // definition found later should be prioritized, overwrite the superclass name.
            mapClassToSuper(existingClass, newSuperClassTypeString)
        }

        // Parse the class body adding each member created to the existing class.
        parseClassBody(api, tokenizer, existingClass)

        return true
    }

    /** Parse the class body, adding members to [cl]. */
    private fun parseClassBody(
        api: TextCodebase,
        tokenizer: Tokenizer,
        cl: TextClassItem,
    ) {
        var token = tokenizer.requireToken()
        val classTypeParameterScope = TypeParameterScope.from(cl)
        while (true) {
            if ("}" == token) {
                break
            } else if ("ctor" == token) {
                token = tokenizer.requireToken()
                parseConstructor(api, tokenizer, cl, classTypeParameterScope, token)
            } else if ("method" == token) {
                token = tokenizer.requireToken()
                parseMethod(api, tokenizer, cl, classTypeParameterScope, token)
            } else if ("field" == token) {
                token = tokenizer.requireToken()
                parseField(api, tokenizer, cl, classTypeParameterScope, token, false)
            } else if ("enum_constant" == token) {
                token = tokenizer.requireToken()
                parseField(api, tokenizer, cl, classTypeParameterScope, token, true)
            } else if ("property" == token) {
                token = tokenizer.requireToken()
                parseProperty(api, tokenizer, cl, classTypeParameterScope, token)
            } else {
                throw ApiParseException("expected ctor, enum_constant, field or method", tokenizer)
            }
            token = tokenizer.requireToken()
        }
    }

    /**
     * Parse a super type string, i.e. a string representing a super class type or a super interface
     * type.
     */
    private fun parseSuperTypeString(tokenizer: Tokenizer, initialToken: String): String {
        var token = getAnnotationCompleteToken(tokenizer, initialToken)

        // Use the token directly if it is complete, otherwise construct the super class type
        // string from as many tokens as necessary.
        return if (!isIncompleteTypeToken(token)) {
            token
        } else {
            buildString {
                append(token)

                // Make sure full super class name is found if there are type use
                // annotations. This can't use [parseType] because the next token might be a
                // separate type (classes only have a single `extends` type, but all
                // interface supertypes are listed as `extends` instead of `implements`).
                // However, this type cannot be an array, so unlike [parseType] this does
                // not need to check if the next token has annotations.
                do {
                    token = getAnnotationCompleteToken(tokenizer, tokenizer.current)
                    append(" ")
                    append(token)
                } while (isIncompleteTypeToken(token))
            }
        }
    }

    /** Encapsulates multiple return values from [parseDeclaredClassType]. */
    private data class DeclaredClassTypeComponents(
        /** The simple name of the class, i.e. not including any outer class prefix. */
        val simpleName: String,
        /** The full name of the class, including outer class prefix. */
        val fullName: String,
        /** The fully qualified name, including package and full name. */
        val qualifiedName: String,
        val outerClass: ClassItem?,
        /** The set of type parameters. */
        val typeParameters: TypeParameterList,
    )

    /**
     * Splits the declared class type into [DeclaredClassTypeComponents].
     *
     * For example "Foo" would split into full name "Foo" and an empty type parameter list, while
     * `"Foo.Bar<A, B extends java.lang.String, C>"` would split into full name `"Foo.Bar"` and type
     * parameter list with `"A"`,`"B extends java.lang.String"`, and `"C"` as type parameters.
     */
    private fun parseDeclaredClassType(
        api: TextCodebase,
        declaredClassType: String,
        pkg: TextPackageItem,
    ): DeclaredClassTypeComponents {
        // Split the declared class type into full name and type parameters.
        val paramIndex = declaredClassType.indexOf('<')
        val (fullName, typeParameterString) =
            if (paramIndex == -1) {
                Pair(declaredClassType, "")
            } else {
                Pair(
                    declaredClassType.substring(0, paramIndex),
                    declaredClassType.substring(paramIndex)
                )
            }
        val pkgName = pkg.name()
        val qualifiedName = qualifiedName(pkgName, fullName)

        // Split the full name into an optional outer class and a simple name.
        val nestedClassIndex = fullName.lastIndexOf('.')
        val (outerClass, simpleName) =
            if (nestedClassIndex == -1) {
                Pair(null, fullName)
            } else {
                val outerClassFullName = fullName.substring(0, nestedClassIndex)
                val qualifiedOuterClassName = qualifiedName(pkgName, outerClassFullName)

                // Search for the outer class in the codebase. This is safe as the outer class
                // always precedes its nested classes.
                val outerClass = api.getOrCreateClass(qualifiedOuterClassName, isOuterClass = true)

                val innerClassName = fullName.substring(nestedClassIndex + 1)
                Pair(outerClass, innerClassName)
            }

        return DeclaredClassTypeComponents(
            simpleName = simpleName,
            fullName = fullName,
            qualifiedName = qualifiedName,
            outerClass = outerClass,
            typeParameters =
                if (typeParameterString == "") TypeParameterList.NONE
                else TextTypeParameterList.create(api, typeParameterString)
        )
    }

    /**
     * If the [startingToken] contains the beginning of an annotation, pulls additional tokens from
     * [tokenizer] to complete the annotation, returning the full token. If there isn't an
     * annotation, returns the original [startingToken].
     *
     * When the method returns, the [tokenizer] will point to the token after the end of the
     * returned string.
     */
    private fun getAnnotationCompleteToken(tokenizer: Tokenizer, startingToken: String): String {
        return if (startingToken.contains('@')) {
            val prefix = startingToken.substringBefore('@')
            val annotationStart = startingToken.substring(startingToken.indexOf('@'))
            val annotation = getAnnotation(tokenizer, annotationStart)
            "$prefix$annotation"
        } else {
            tokenizer.requireToken()
            startingToken
        }
    }

    /**
     * If the [startingToken] is the beginning of an annotation, returns the annotation parsed from
     * the [tokenizer]. Returns null otherwise.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation.
     */
    private fun getAnnotation(tokenizer: Tokenizer, startingToken: String): String? {
        var token = startingToken
        if (token.startsWith('@')) {
            // Annotation
            var annotation = token

            // Restore annotations that were shortened on export
            annotation = unshortenAnnotation(annotation)
            token = tokenizer.requireToken()
            if (token == "(") {
                // Annotation arguments; potentially nested
                var balance = 0
                val start = tokenizer.offset() - 1
                while (true) {
                    if (token == "(") {
                        balance++
                    } else if (token == ")") {
                        balance--
                        if (balance == 0) {
                            break
                        }
                    }
                    token = tokenizer.requireToken()
                }
                annotation += tokenizer.getStringFromOffset(start)
                // Move the tokenizer so that when the method returns it points to the token after
                // the end of the annotation.
                tokenizer.requireToken()
            }
            return annotation
        } else {
            return null
        }
    }

    /**
     * Collects all the sequential annotations from the [tokenizer] beginning with [startingToken],
     * returning them as a (possibly empty) mutable list.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation list.
     */
    private fun getAnnotations(tokenizer: Tokenizer, startingToken: String): MutableList<String> {
        val annotations: MutableList<String> = mutableListOf()
        var token = startingToken
        while (true) {
            val annotation = getAnnotation(tokenizer, token) ?: break
            token = tokenizer.current
            annotations.add(annotation)
        }
        return annotations
    }

    private fun parseConstructor(
        api: TextCodebase,
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken
        val method: TextConstructorItem
        var typeParameterList = TypeParameterList.NONE

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(api, tokenizer, token, annotations)
        token = tokenizer.current
        if ("<" == token) {
            typeParameterList = parseTypeParameterList(api, tokenizer)
            token = tokenizer.requireToken()
        }
        tokenizer.assertIdent(token)
        val name: String =
            token.substring(
                token.lastIndexOf('.') + 1
            ) // For inner classes, strip outer classes from name
        // Collect all type parameters in scope into one list
        val typeParameterScope =
            classTypeParameterScope.nestedScope(typeParameterList.typeParameters())
        val parameters = parseParameterList(api, tokenizer, typeParameterScope)
        // Constructors cannot return null.
        val ctorReturn = cl.type().duplicate(TypeNullability.NONNULL)
        method =
            TextConstructorItem(api, name, cl, modifiers, ctorReturn, parameters, tokenizer.pos())
        method.setTypeParameterList(typeParameterList)
        if (typeParameterList is TextTypeParameterList) {
            typeParameterList.setOwner(method)
        }
        token = tokenizer.requireToken()
        if ("throws" == token) {
            token = parseThrows(tokenizer, method)
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        if (!cl.constructors().contains(method)) {
            cl.addConstructor(method)
        }
    }

    private fun parseMethod(
        api: TextCodebase,
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken
        val method: TextMethodItem
        var typeParameterList = TypeParameterList.NONE

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(api, tokenizer, token, null)
        token = tokenizer.current
        if ("<" == token) {
            typeParameterList = parseTypeParameterList(api, tokenizer)
            token = tokenizer.requireToken()
        }
        tokenizer.assertIdent(token)
        // Collect all type parameters in scope into one list
        val typeParameterScope =
            classTypeParameterScope.nestedScope(typeParameterList.typeParameters())

        val returnType: TextTypeItem
        val parameters: List<TextParameterItem>
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, then the return type.
            name = token
            parameters = parseParameterList(api, tokenizer, typeParameterScope)
            token = tokenizer.requireToken()
            if (token != ":") {
                throw ApiParseException(
                    "Expecting \":\" after parameter list, found $token.",
                    tokenizer
                )
            }
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            returnType = parseType(api, tokenizer, token, typeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnType = parseType(api, tokenizer, token, typeParameterScope, annotations)
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            parameters = parseParameterList(api, tokenizer, typeParameterScope)
            token = tokenizer.requireToken()
        }

        if (cl.isInterface() && !modifiers.isDefault() && !modifiers.isStatic()) {
            modifiers.setAbstract(true)
        }
        method = TextMethodItem(api, name, cl, modifiers, returnType, parameters, tokenizer.pos())
        method.setTypeParameterList(typeParameterList)
        if (typeParameterList is TextTypeParameterList) {
            typeParameterList.setOwner(method)
        }
        if ("throws" == token) {
            token = parseThrows(tokenizer, method)
        }
        if ("default" == token) {
            token = parseDefault(tokenizer, method)
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        if (!cl.methods().contains(method)) {
            cl.addMethod(method)
        }
    }

    private fun mergeAnnotations(
        annotations: MutableList<String>,
        annotation: String
    ): MutableList<String> {
        // Reverse effect of TypeItem.shortenTypes(...)
        val qualifiedName =
            if (annotation.indexOf('.') == -1) "@androidx.annotation$annotation" else "@$annotation"
        annotations.add(qualifiedName)
        return annotations
    }

    private fun parseField(
        api: TextCodebase,
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String,
        isEnum: Boolean
    ) {
        var token = startingToken
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(api, tokenizer, token, null)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        var type: TextTypeItem
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            type = parseType(api, tokenizer, token, classTypeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the name, then the type.
            type = parseType(api, tokenizer, token, classTypeParameterScope, annotations)
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }

        var value: Any? = null
        if ("=" == token) {
            token = tokenizer.requireToken(false)
            value = parseValue(type, token, tokenizer)
            token = tokenizer.requireToken()
            // If this is an implicitly null constant, add the nullability.
            if (
                !kotlinStyleNulls &&
                    modifiers.isFinal() &&
                    value != null &&
                    type.modifiers.nullability() != TypeNullability.NONNULL
            ) {
                type = type.duplicate(TypeNullability.NONNULL)
            }
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val field = TextFieldItem(api, name, cl, modifiers, type, value, tokenizer.pos())
        if (isEnum) {
            cl.addEnumConstant(field)
        } else {
            cl.addField(field)
        }
    }

    private fun parseModifiers(
        api: TextCodebase,
        tokenizer: Tokenizer,
        startingToken: String?,
        annotations: List<String>?
    ): DefaultModifierList {
        var token = startingToken
        val modifiers = DefaultModifierList(api, DefaultModifierList.PACKAGE_PRIVATE, null)
        processModifiers@ while (true) {
            token =
                when (token) {
                    "public" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                        tokenizer.requireToken()
                    }
                    "protected" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PROTECTED)
                        tokenizer.requireToken()
                    }
                    "private" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                        tokenizer.requireToken()
                    }
                    "internal" -> {
                        modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
                        tokenizer.requireToken()
                    }
                    "static" -> {
                        modifiers.setStatic(true)
                        tokenizer.requireToken()
                    }
                    "final" -> {
                        modifiers.setFinal(true)
                        tokenizer.requireToken()
                    }
                    "deprecated" -> {
                        modifiers.setDeprecated(true)
                        tokenizer.requireToken()
                    }
                    "abstract" -> {
                        modifiers.setAbstract(true)
                        tokenizer.requireToken()
                    }
                    "transient" -> {
                        modifiers.setTransient(true)
                        tokenizer.requireToken()
                    }
                    "volatile" -> {
                        modifiers.setVolatile(true)
                        tokenizer.requireToken()
                    }
                    "sealed" -> {
                        modifiers.setSealed(true)
                        tokenizer.requireToken()
                    }
                    "default" -> {
                        modifiers.setDefault(true)
                        tokenizer.requireToken()
                    }
                    "synchronized" -> {
                        modifiers.setSynchronized(true)
                        tokenizer.requireToken()
                    }
                    "native" -> {
                        modifiers.setNative(true)
                        tokenizer.requireToken()
                    }
                    "strictfp" -> {
                        modifiers.setStrictFp(true)
                        tokenizer.requireToken()
                    }
                    "infix" -> {
                        modifiers.setInfix(true)
                        tokenizer.requireToken()
                    }
                    "operator" -> {
                        modifiers.setOperator(true)
                        tokenizer.requireToken()
                    }
                    "inline" -> {
                        modifiers.setInline(true)
                        tokenizer.requireToken()
                    }
                    "value" -> {
                        modifiers.setValue(true)
                        tokenizer.requireToken()
                    }
                    "suspend" -> {
                        modifiers.setSuspend(true)
                        tokenizer.requireToken()
                    }
                    "vararg" -> {
                        modifiers.setVarArg(true)
                        tokenizer.requireToken()
                    }
                    "fun" -> {
                        modifiers.setFunctional(true)
                        tokenizer.requireToken()
                    }
                    "data" -> {
                        modifiers.setData(true)
                        tokenizer.requireToken()
                    }
                    else -> break@processModifiers
                }
        }
        if (annotations != null) {
            modifiers.addAnnotations(annotations)
        }
        return modifiers
    }

    private fun parseValue(type: TextTypeItem, value: String?, tokenizer: Tokenizer): Any? {
        return if (value != null) {
            if (type is PrimitiveTypeItem) {
                parsePrimitiveValue(type, value, tokenizer)
            } else if (type.isString()) {
                if ("null" == value) {
                    null
                } else {
                    javaUnescapeString(value.substring(1, value.length - 1))
                }
            } else {
                value
            }
        } else null
    }

    private fun parsePrimitiveValue(
        type: PrimitiveTypeItem,
        value: String,
        tokenizer: Tokenizer
    ): Any {
        return when (type.kind) {
            Primitive.BOOLEAN ->
                if ("true" == value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
            Primitive.BYTE,
            Primitive.SHORT,
            Primitive.INT -> Integer.valueOf(value)
            Primitive.LONG -> java.lang.Long.valueOf(value.substring(0, value.length - 1))
            Primitive.FLOAT ->
                when (value) {
                    "(1.0f/0.0f)",
                    "(1.0f / 0.0f)" -> Float.POSITIVE_INFINITY
                    "(-1.0f/0.0f)",
                    "(-1.0f / 0.0f)" -> Float.NEGATIVE_INFINITY
                    "(0.0f/0.0f)",
                    "(0.0f / 0.0f)" -> Float.NaN
                    else -> java.lang.Float.valueOf(value)
                }
            Primitive.DOUBLE ->
                when (value) {
                    "(1.0/0.0)",
                    "(1.0 / 0.0)" -> Double.POSITIVE_INFINITY
                    "(-1.0/0.0)",
                    "(-1.0 / 0.0)" -> Double.NEGATIVE_INFINITY
                    "(0.0/0.0)",
                    "(0.0 / 0.0)" -> Double.NaN
                    else -> java.lang.Double.valueOf(value)
                }
            Primitive.CHAR -> value.toInt().toChar()
            Primitive.VOID ->
                throw ApiParseException("Found value $value assigned to void type", tokenizer)
        }
    }

    private fun parseProperty(
        api: TextCodebase,
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(api, tokenizer, token, null)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        val type: TextTypeItem
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            type = parseType(api, tokenizer, token, classTypeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the type, then the name.
            type = parseType(api, tokenizer, token, classTypeParameterScope, annotations)
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val property = TextPropertyItem(api, name, cl, modifiers, type, tokenizer.pos())
        cl.addProperty(property)
    }

    private fun parseTypeParameterList(
        codebase: TextCodebase,
        tokenizer: Tokenizer
    ): TypeParameterList {
        var token: String
        val start = tokenizer.offset() - 1
        var balance = 1
        while (balance > 0) {
            token = tokenizer.requireToken()
            if (token == "<") {
                balance++
            } else if (token == ">") {
                balance--
            }
        }
        val typeParameterList = tokenizer.getStringFromOffset(start)
        return if (typeParameterList.isEmpty()) {
            TypeParameterList.NONE
        } else {
            TextTypeParameterList.create(codebase, typeParameterList)
        }
    }

    /**
     * Parses a list of parameters. Before calling, [tokenizer] should point to the token *before*
     * the opening `(` of the parameter list (the method starts by calling
     * [Tokenizer.requireToken]).
     *
     * When the method returns, [tokenizer] will point to the closing `)` of the parameter list.
     */
    private fun parseParameterList(
        api: TextCodebase,
        tokenizer: Tokenizer,
        typeParameterScope: TypeParameterScope
    ): List<TextParameterItem> {
        val parameters = mutableListOf<TextParameterItem>()
        var token: String = tokenizer.requireToken()
        if ("(" != token) {
            throw ApiParseException("expected (, was $token", tokenizer)
        }
        token = tokenizer.requireToken()
        var index = 0
        while (true) {
            if (")" == token) {
                return parameters
            }

            // Each item can be
            // optional annotations optional-modifiers type-with-use-annotations-and-generics
            // optional-name optional-equals-default-value

            // Used to represent the presence of a default value, instead of showing the entire
            // default value
            var hasDefaultValue = token == "optional"
            if (hasDefaultValue) {
                token = tokenizer.requireToken()
            }

            // Metalava: including annotations in file now
            val annotations = getAnnotations(tokenizer, token)
            token = tokenizer.current
            val modifiers = parseModifiers(api, tokenizer, token, null)
            token = tokenizer.current

            val type: TextTypeItem
            val name: String
            val publicName: String?
            if (format.kotlinNameTypeOrder) {
                // Kotlin style: parse the name (only considered a public name if it is not `_`,
                // which is used as a placeholder for params without public names), then the type.
                name = parseNameWithColon(token, tokenizer)
                publicName =
                    if (name == "_") {
                        null
                    } else {
                        name
                    }
                token = tokenizer.requireToken()
                // Token should now represent the type
                type = parseType(api, tokenizer, token, typeParameterScope, annotations)
                // TODO(b/300081840): update nullability handling
                modifiers.addAnnotations(annotations)
                token = tokenizer.current
            } else {
                // Java style: parse the type, then the public name if it has one.
                type = parseType(api, tokenizer, token, typeParameterScope, annotations)
                modifiers.addAnnotations(annotations)
                token = tokenizer.current
                if (Tokenizer.isIdent(token) && token != "=") {
                    name = token
                    publicName = name
                    token = tokenizer.requireToken()
                } else {
                    name = "arg" + (index + 1)
                    publicName = null
                }
            }
            if (type is ArrayTypeItem && type.isVarargs) {
                modifiers.setVarArg(true)
            }

            var defaultValue = UNKNOWN_DEFAULT_VALUE
            if ("=" == token) {
                defaultValue = tokenizer.requireToken(true)
                val sb = StringBuilder(defaultValue)
                if (defaultValue == "{") {
                    var balance = 1
                    while (balance > 0) {
                        token = tokenizer.requireToken(parenIsSep = false, eatWhitespace = false)
                        sb.append(token)
                        if (token == "{") {
                            balance++
                        } else if (token == "}") {
                            balance--
                            if (balance == 0) {
                                break
                            }
                        }
                    }
                    token = tokenizer.requireToken()
                } else {
                    var balance = if (defaultValue == "(") 1 else 0
                    while (true) {
                        token = tokenizer.requireToken(parenIsSep = true, eatWhitespace = false)
                        if ((token.endsWith(",") || token.endsWith(")")) && balance <= 0) {
                            if (token.length > 1) {
                                sb.append(token, 0, token.length - 1)
                                token = token[token.length - 1].toString()
                            }
                            break
                        }
                        sb.append(token)
                        if (token == "(") {
                            balance++
                        } else if (token == ")") {
                            balance--
                        }
                    }
                }
                defaultValue = sb.toString()
            }
            if (defaultValue != UNKNOWN_DEFAULT_VALUE) {
                hasDefaultValue = true
            }
            when (token) {
                "," -> {
                    token = tokenizer.requireToken()
                }
                ")" -> {
                    // closing parenthesis
                }
                else -> {
                    throw ApiParseException("expected , or ), found $token", tokenizer)
                }
            }
            parameters.add(
                TextParameterItem(
                    api,
                    name,
                    publicName,
                    hasDefaultValue,
                    defaultValue,
                    index,
                    type,
                    modifiers,
                    tokenizer.pos()
                )
            )
            index++
        }
    }

    private fun parseDefault(tokenizer: Tokenizer, method: TextMethodItem): String {
        val sb = StringBuilder()
        while (true) {
            val token = tokenizer.requireToken()
            if (";" == token) {
                method.setAnnotationDefault(sb.toString())
                return token
            } else {
                sb.append(token)
            }
        }
    }

    private fun parseThrows(tokenizer: Tokenizer, method: TextMethodItem): String {
        var token = tokenizer.requireToken()
        var comma = true
        while (true) {
            when (token) {
                ";" -> {
                    return token
                }
                "," -> {
                    if (comma) {
                        throw ApiParseException("Expected exception, got ','", tokenizer)
                    }
                    comma = true
                }
                else -> {
                    if (!comma) {
                        throw ApiParseException("Expected ',' or ';' got $token", tokenizer)
                    }
                    comma = false
                    method.addException(token)
                }
            }
            token = tokenizer.requireToken()
        }
    }

    /**
     * Parses a [TextTypeItem] from the [tokenizer], starting with the [startingToken] and ensuring
     * that the full type string is gathered, even when there are type-use annotations. Once the
     * full type string is found, this parses the type in the context of the [typeParameterScope].
     *
     * If the type string uses a Kotlin nullabililty suffix, this adds an annotation representing
     * that nullability to [annotations].
     *
     * After this method is called, `tokenizer.current` will point to the token after the type.
     *
     * Note: this **should not** be used when the token after the type could contain annotations,
     * such as when multiple types appear as consecutive tokens. (This happens in the `implements`
     * list of a class definition, e.g. `class Foo implements test.pkg.Bar test.pkg.@A Baz`.)
     *
     * To handle arrays with type-use annotations, this looks forward at the next token and includes
     * it if it contains an annotation. This is necessary to handle type strings like "Foo @A []".
     */
    private fun parseType(
        api: TextCodebase,
        tokenizer: Tokenizer,
        startingToken: String,
        typeParameterScope: TypeParameterScope,
        annotations: MutableList<String>
    ): TextTypeItem {
        var prev = getAnnotationCompleteToken(tokenizer, startingToken)
        var type = prev
        var token = tokenizer.current
        // Look both at the last used token and the next one:
        // If the last token has annotations, the type string was broken up by annotations, and the
        // next token is also part of the type.
        // If the next token has annotations, this is an array type like "Foo @A []", so the next
        // token is part of the type.
        while (isIncompleteTypeToken(prev) || isIncompleteTypeToken(token)) {
            token = getAnnotationCompleteToken(tokenizer, token)
            type += " $token"
            prev = token
            token = tokenizer.current
        }

        val parsedType = api.typeResolver.obtainTypeFromString(type, typeParameterScope)
        if (kotlinStyleNulls) {
            // Treat varargs as non-null for consistency with the psi model.
            if (parsedType is ArrayTypeItem && parsedType.isVarargs) {
                mergeAnnotations(annotations, ANDROIDX_NONNULL)
            } else {
                // Add an annotation to the context item for the type's nullability if applicable.
                val nullability = parsedType.modifiers.nullability()
                if (parsedType !is PrimitiveTypeItem && nullability == TypeNullability.NONNULL) {
                    mergeAnnotations(annotations, ANDROIDX_NONNULL)
                } else if (nullability == TypeNullability.NULLABLE) {
                    mergeAnnotations(annotations, ANDROIDX_NULLABLE)
                }
            }
        } else if (parsedType.modifiers.nullability() == TypeNullability.PLATFORM) {
            // See if the type has nullability from the context item annotations.
            val nullabilityFromContext =
                annotations
                    .singleOrNull { isNullnessAnnotation(it) }
                    ?.let {
                        if (isNullableAnnotation(it)) {
                            TypeNullability.NULLABLE
                        } else {
                            TypeNullability.NONNULL
                        }
                    }
            if (nullabilityFromContext != null) {
                return parsedType.duplicate(nullabilityFromContext)
            }
        }
        return parsedType
    }

    /**
     * Determines whether the [type] is an incomplete type string broken up by annotations. This is
     * the case when there's an annotation that isn't contained within a parameter list (because
     * [Tokenizer.requireToken] handles not breaking in the middle of a parameter list).
     */
    private fun isIncompleteTypeToken(type: String): Boolean {
        val firstAnnotationIndex = type.indexOf('@')
        val paramStartIndex = type.indexOf('<')
        val lastAnnotationIndex = type.lastIndexOf('@')
        val paramEndIndex = type.lastIndexOf('>')
        return firstAnnotationIndex != -1 &&
            (paramStartIndex == -1 ||
                firstAnnotationIndex < paramStartIndex ||
                paramEndIndex == -1 ||
                paramEndIndex < lastAnnotationIndex)
    }

    /**
     * For Kotlin-style name/type ordering in signature files, the name is generally followed by a
     * colon (besides methods, where the colon comes after the parameter list). This method takes
     * the name [token] and removes the trailing colon, throwing an [ApiParseException] if one isn't
     * present (the [tokenizer] is only used for context for the error, if needed).
     */
    private fun parseNameWithColon(token: String, tokenizer: Tokenizer): String {
        if (!token.endsWith(':')) {
            throw ApiParseException("Expecting name ending with \":\" but found $token.", tokenizer)
        }
        return token.removeSuffix(":")
    }

    private fun qualifiedName(pkg: String, className: String): String {
        return "$pkg.$className"
    }
}

/**
 * Provides access to information that is needed by the [ReferenceResolver].
 *
 * This is provided by [ApiFile] which tracks the names of interfaces and super classes that each
 * class implements/extends respectively before they are resolved.
 */
internal interface ResolverContext {
    /**
     * Get the string representations of the super interface types of the supplied class, returns
     * null if there were no super interface types specified.
     */
    fun superInterfaceTypeStrings(cl: ClassItem): Set<String>?

    /**
     * Get the string representation of the super class type extended by the supplied class, returns
     * null if there was no specified super class type.
     */
    fun superClassTypeString(cl: ClassItem): String?
}

/** Resolves any references in the codebase, e.g. to superclasses, interfaces, etc. */
internal class ReferenceResolver(
    private val context: ResolverContext,
    private val codebase: TextCodebase,
) {
    /**
     * A list of all the classes in the text codebase.
     *
     * This takes a copy of the `values` collection rather than use it correctly to avoid
     * [ConcurrentModificationException].
     */
    private val classes = codebase.mAllClasses.values.toList()

    companion object {
        fun resolveReferences(context: ResolverContext, codebase: TextCodebase) {
            val resolver = ReferenceResolver(context, codebase)
            resolver.resolveReferences()
        }
    }

    fun resolveReferences() {
        resolveSuperclasses()
        resolveInterfaces()
        resolveThrowsClasses()
    }

    private fun resolveSuperclasses() {
        for (cl in classes) {
            // java.lang.Object has no superclass and neither do interfaces
            if (cl.isJavaLangObject() || cl.isInterface()) {
                continue
            }
            val superClassTypeString: String =
                context.superClassTypeString(cl)
                    ?: when {
                        cl.isEnum() -> JAVA_LANG_ENUM
                        cl.isAnnotationType() -> JAVA_LANG_ANNOTATION
                        // Interfaces do not extend java.lang.Object so drop out before the else
                        // clause.
                        cl.isInterface() -> return
                        else -> JAVA_LANG_OBJECT
                    }

            val superClassType =
                codebase.typeResolver.obtainTypeFromString(
                    superClassTypeString,
                    TypeParameterScope.from(cl)
                ) as TextClassTypeItem

            // Force the creation of the super class if it does not exist in the codebase.
            val superclass = codebase.getOrCreateClass(superClassType.qualifiedName)
            cl.setSuperClass(superclass, superClassType)
        }
    }

    private fun resolveInterfaces() {
        for (cl in classes) {
            val typeParameterScope = TypeParameterScope.from(cl)

            val interfaces = context.superInterfaceTypeStrings(cl) ?: continue
            for (interfaceName in interfaces) {
                val typeItem =
                    codebase.typeResolver.obtainTypeFromString(interfaceName, typeParameterScope)
                        as TextClassTypeItem
                cl.addInterface(typeItem)

                // Force the creation of the interface class if it does not exist in the codebase.
                codebase.getOrCreateClass(typeItem.qualifiedName, isInterface = true)
            }
        }
    }

    private fun resolveThrowsClasses() {
        for (cl in classes) {
            val classTypeParameterScope = TypeParameterScope.from(cl)
            for (methodItem in cl.constructors()) {
                resolveThrowsClasses(classTypeParameterScope, methodItem)
            }
            for (methodItem in cl.methods()) {
                resolveThrowsClasses(classTypeParameterScope, methodItem)
            }
        }
    }

    private fun resolveThrowsClasses(
        classTypeParameterScope: TypeParameterScope,
        methodItem: MethodItem
    ) {
        val methodInfo = methodItem as TextMethodItem
        val names = methodInfo.throwsTypeNames()
        if (names.isNotEmpty()) {
            val typeParameterScope =
                classTypeParameterScope.nestedScope(methodItem.typeParameterList().typeParameters())
            val throwsList =
                names.map { exception ->
                    // Search in this codebase, then possibly check for a type parameter, if not
                    // found then fall back to searching in a base codebase and finally creating a
                    // stub.
                    codebase.findClassInCodebase(exception)?.let { ThrowableType.ofClass(it) }
                        ?: typeParameterScope.findTypeParameter(exception)?.let {
                            ThrowableType.ofTypeParameter(it)
                        }
                            ?: getOrCreateThrowableClass(exception)
                }
            methodInfo.setThrowsList(throwsList)
        }
    }

    private fun getOrCreateThrowableClass(exception: String): ThrowableType {
        // Exception not provided by this codebase. Either try and retrieve it from a base codebase
        // or create a stub.
        val exceptionClass = codebase.getOrCreateClass(exception)

        // A class retrieved from another codebase is assumed to have been fully resolved by the
        // codebase. However, a stub that has just been created will need some additional work. A
        // stub can be differentiated from a ClassItem retrieved from another codebase because it
        // belongs to this codebase and is a TextClassItem.
        if (exceptionClass.codebase == codebase && exceptionClass is TextClassItem) {
            // An exception class needs to extend Throwable, unless it is Throwable in
            // which case it does not need modifying.
            if (exception != JAVA_LANG_THROWABLE) {
                val throwableClass = codebase.getOrCreateClass(JAVA_LANG_THROWABLE)
                exceptionClass.setSuperClass(throwableClass, throwableClass.type())
            }
        }

        return ThrowableType.ofClass(exceptionClass)
    }
}

private fun DefaultModifierList.addAnnotations(annotationSources: List<String>) {
    if (annotationSources.isEmpty()) {
        return
    }

    annotationSources.forEach { source ->
        val item = codebase.createAnnotation(source)

        // @Deprecated is also treated as a "modifier"
        if (item.qualifiedName == JAVA_LANG_DEPRECATED) {
            setDeprecated(true)
        }

        addAnnotation(item)
    }
}
