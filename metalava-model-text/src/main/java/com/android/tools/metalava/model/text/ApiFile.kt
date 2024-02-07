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
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
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
import java.util.IdentityHashMap
import kotlin.text.Charsets.UTF_8

@MetalavaApi
class ApiFile
private constructor(
    private val codebase: TextCodebase,
    private val formatForLegacyFiles: FileFormat?,
) : ResolverContext {

    /**
     * Provides support for parsing and caching `TypeItem` instances.
     *
     * Defer creation until after the first file has been read and [kotlinStyleNulls] has been set
     * to a non-null value to ensure that it picks up the correct setting of [kotlinStyleNulls].
     */
    private val typeParser by
        lazy(LazyThreadSafetyMode.NONE) { TextTypeParser(codebase, kotlinStyleNulls!!) }

    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. ? suffix means nullable, !
     * suffix means unknown, and absence of a suffix means not nullable.
     *
     * Updated based on the header of the signature file being parsed.
     */
    private var kotlinStyleNulls: Boolean? = null

    /** The file format of the file being parsed. */
    lateinit var format: FileFormat

    /** Map from [ClassItem] to [TypeParameterScope]. */
    private val classToTypeParameterScope = IdentityHashMap<ClassItem, TypeParameterScope>()

    /**
     * The set of interface types needed for later resolution.
     *
     * TODO(b/323516595): Find a better way.
     */
    private val interfaceTypesForResolution = mutableSetOf<ClassTypeItem>()

    private val mClassToSuper = HashMap<TextClassItem, String>(30000)

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
            // Provides the called with access to the ApiFile.
            apiStatsConsumer: (Stats) -> Unit = {},
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
            val parser = ApiFile(api, formatForLegacyFiles)
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
                parser.parseApiSingleFile(!first, file.path, apiText)
                first = false
            }
            api.description = actualDescription
            parser.postProcess()

            apiStatsConsumer(parser.stats)

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
            val parser = ApiFile(api, formatForLegacyFiles)
            parser.parseApiSingleFile(false, filename, apiText)
            parser.postProcess()
            return api
        }

        /**
         * Extracts the bounds string list from the [typeParameterString].
         *
         * Given `T extends a.B & b.C<? super T>` this will return a list of `a.B` and `b.C<? super
         * T>`.
         */
        fun extractTypeParameterBoundsStringList(typeParameterString: String?): List<String> {
            val s = typeParameterString ?: return emptyList()
            val index = s.indexOf("extends ")
            if (index == -1) {
                return emptyList()
            }
            val list = mutableListOf<String>()
            var angleBracketBalance = 0
            var start = index + "extends ".length
            val length = s.length
            for (i in start until length) {
                val c = s[i]
                if (c == '&' && angleBracketBalance == 0) {
                    addNonBlankStringToList(list, typeParameterString, start, i)
                    start = i + 1
                } else if (c == '<') {
                    angleBracketBalance++
                } else if (c == '>') {
                    angleBracketBalance--
                    if (angleBracketBalance == 0) {
                        addNonBlankStringToList(list, typeParameterString, start, i + 1)
                        start = i + 1
                    }
                }
            }
            if (start < length) {
                addNonBlankStringToList(list, typeParameterString, start, length)
            }
            return list
        }

        private fun addNonBlankStringToList(
            list: MutableList<String>,
            s: String,
            from: Int,
            to: Int
        ) {
            val element = s.substring(from, to).trim()
            if (element.isNotEmpty()) list.add(element)
        }
    }

    /**
     * Perform any final steps to initialize the [TextCodebase] after parsing the signature files.
     */
    private fun postProcess() {
        // Use this as the context for resolving references.
        ReferenceResolver.resolveReferences(this, codebase, typeParser) {
            typeParameterScopeForClass(it)
        }

        // Resolve all interface types that were found in the signature file.
        // TODO(b/323516595): Find a better way.
        for (interfaceType in interfaceTypesForResolution) {
            // Resolve the interface type to a class.
            interfaceType.asClass()
        }
    }

    private fun parseApiSingleFile(
        appending: Boolean,
        filename: String,
        apiText: String,
    ) {
        // Parse the header of the signature file to determine the format. If the signature file is
        // empty then `parseHeader` will return null, so it will default to `FileFormat.V2`.
        format =
            FileFormat.parseHeader(filename, StringReader(apiText), formatForLegacyFiles)
                ?: FileFormat.V2

        // Disallow a mixture of kotlinStyleNulls settings.
        if (kotlinStyleNulls == null) {
            kotlinStyleNulls = format.kotlinStyleNulls
        } else if (kotlinStyleNulls != format.kotlinStyleNulls) {
            throw ApiParseException(
                "Cannot mix signature files with different settings of kotlinStyleNulls"
            )
        }

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
                parsePackage(tokenizer)
            } else {
                throw ApiParseException("expected package got $token", tokenizer)
            }
        }
    }

    private fun parsePackage(tokenizer: Tokenizer) {
        var token: String = tokenizer.requireToken()

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        val modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC, null)
        modifiers.addAnnotations(annotations)
        token = tokenizer.current
        tokenizer.assertIdent(token)
        val name: String = token

        // If the same package showed up multiple times, make sure they have the same modifiers.
        // (Packages can't have public/private/etc., but they can have annotations, which are part
        // of ModifierList.)
        val existing = codebase.findPackage(name)
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
                val newPackageItem = TextPackageItem(codebase, name, modifiers, tokenizer.pos())
                codebase.addPackage(newPackageItem)
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
                parseClass(pkg, tokenizer, token)
            }
        }
    }

    private fun mapClassToSuper(classInfo: TextClassItem, superclass: String?) {
        superclass?.let { mClassToSuper.put(classInfo, superclass) }
    }

    /** Implements [ResolverContext] interface */
    override fun superClassTypeString(cl: ClassItem): String? = mClassToSuper[cl]

    private fun parseClass(pkg: TextPackageItem, tokenizer: Tokenizer, startingToken: String) {
        var token = startingToken
        var classKind = ClassKind.CLASS
        var superClassTypeString: String? = null

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)

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

        // Extract lots of information from the declared class type.
        val (
            className,
            fullName,
            qualifiedClassName,
            outerClass,
            typeParameterList,
            typeParameterScope,
        ) = parseDeclaredClassType(pkg, declaredClassType, classPosition)

        token = tokenizer.requireToken()

        if ("extends" == token && classKind != ClassKind.INTERFACE) {
            superClassTypeString = parseSuperTypeString(tokenizer, tokenizer.requireToken())
            token = tokenizer.current
        }

        val interfaceTypes = mutableSetOf<ClassTypeItem>()
        if ("implements" == token || "extends" == token) {
            token = tokenizer.requireToken()
            while (true) {
                if ("{" == token) {
                    break
                } else if ("," != token) {
                    val interfaceTypeString = parseSuperTypeString(tokenizer, token)
                    val interfaceType =
                        typeParser.getSuperType(interfaceTypeString, typeParameterScope)
                    interfaceTypes.add(interfaceType)
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
            // If the annotation was defined using @interface then add the implicit
            // "implements java.lang.annotation.Annotation".
            interfaceTypes.add(typeParser.superAnnotationType)
        } else if (typeParser.superAnnotationType in interfaceTypes) {
            // A normal class that implements java.lang.annotation.Annotation which was the old way
            // of representing an annotation in the API signature files. So, update the class kind
            // to match.
            classKind = ClassKind.ANNOTATION_TYPE
        }

        if ("{" != token) {
            throw ApiParseException("expected {, was $token", tokenizer)
        }

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
        if (tryMergingIntoExistingClass(tokenizer, newClassCharacteristics)) {
            return
        }

        // Create the TextClassItem and set its package but do not add it to the package or
        // register it.
        val cl =
            TextClassItem(
                codebase = codebase,
                position = classPosition,
                modifiers = modifiers,
                classKind = classKind,
                qualifiedName = qualifiedClassName,
                simpleName = className,
                fullName = fullName,
                annotations = annotations,
                typeParameterList = typeParameterList,
            )

        cl.setInterfaceTypes(interfaceTypes.toList())

        // Save the interface types to later when they will be resolved. That is needed to avoid
        // later changes to the model which would/could cause concurrent modification issues.
        // TODO(b/323516595): Find a better way.
        interfaceTypesForResolution.addAll(interfaceTypes)

        // Store the [TypeParameterScope] for this [ClassItem] so it can be retrieved later in
        // [typeParameterScopeFromClass].
        if (!typeParameterScope.isEmpty()) {
            classToTypeParameterScope[cl] = typeParameterScope
        }

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
        codebase.registerClass(cl)

        // Record the super class type string as needing to be resolved for this class.
        mapClassToSuper(cl, superClassTypeString)

        // Parse the class body adding each member created to the class item being populated.
        parseClassBody(tokenizer, cl, typeParameterScope)
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
        tokenizer: Tokenizer,
        newClassCharacteristics: ClassCharacteristics,
    ): Boolean {
        // Check for the existing class from a previously parsed file. If it could not be found
        // then return.
        val existingClass =
            codebase.findClassInCodebase(newClassCharacteristics.qualifiedName) ?: return false

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
        parseClassBody(tokenizer, existingClass, typeParameterScopeForClass(existingClass))

        return true
    }

    /** Get the [TypeParameterScope] for a previously created [ClassItem]. */
    private fun typeParameterScopeForClass(classItem: ClassItem?): TypeParameterScope =
        classItem?.let { classToTypeParameterScope[classItem] } ?: TypeParameterScope.empty

    /** Parse the class body, adding members to [cl]. */
    private fun parseClassBody(
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
    ) {
        var token = tokenizer.requireToken()
        while (true) {
            if ("}" == token) {
                break
            } else if ("ctor" == token) {
                token = tokenizer.requireToken()
                parseConstructor(tokenizer, cl, classTypeParameterScope, token)
            } else if ("method" == token) {
                token = tokenizer.requireToken()
                parseMethod(tokenizer, cl, classTypeParameterScope, token)
            } else if ("field" == token) {
                token = tokenizer.requireToken()
                parseField(tokenizer, cl, classTypeParameterScope, token, false)
            } else if ("enum_constant" == token) {
                token = tokenizer.requireToken()
                parseField(tokenizer, cl, classTypeParameterScope, token, true)
            } else if ("property" == token) {
                token = tokenizer.requireToken()
                parseProperty(tokenizer, cl, classTypeParameterScope, token)
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
        /** The optional, resolved outer [ClassItem]. */
        val outerClass: ClassItem?,
        /** The set of type parameters. */
        val typeParameterList: TypeParameterList,
        /** The [TypeParameterScope] including [typeParameterList]. */
        val typeParameterScope: TypeParameterScope,
    )

    /**
     * Splits the declared class type into [DeclaredClassTypeComponents].
     *
     * For example "Foo" would split into full name "Foo" and an empty type parameter list, while
     * `"Foo.Bar<A, B extends java.lang.String, C>"` would split into full name `"Foo.Bar"` and type
     * parameter list with `"A"`,`"B extends java.lang.String"`, and `"C"` as type parameters.
     *
     * If the qualified name matches an existing class then return its information.
     */
    private fun parseDeclaredClassType(
        pkg: TextPackageItem,
        declaredClassType: String,
        classPosition: SourcePositionInfo,
    ): DeclaredClassTypeComponents {
        // Split the declared class type into full name and type parameters.
        val paramIndex = declaredClassType.indexOf('<')
        val (fullName, typeParameterListString) =
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
                val outerClass =
                    codebase.getOrCreateClass(qualifiedOuterClassName, isOuterClass = true)

                val innerClassName = fullName.substring(nestedClassIndex + 1)
                Pair(outerClass, innerClassName)
            }

        // Get the [TypeParameterScope] for the outer class, if any, from a previously stored one,
        // otherwise use the empty scope as the [ClassItem] is a stub and so has no type parameters.
        val outerClassTypeParameterScope = typeParameterScopeForClass(outerClass)

        // Create type parameter list and scope from the string and optional outer class scope.
        val (typeParameterList, typeParameterScope) =
            if (typeParameterListString == "")
                Pair(TypeParameterList.NONE, outerClassTypeParameterScope)
            else createTypeParameterList(outerClassTypeParameterScope, typeParameterListString)

        // Decide which type parameter list and scope to actually use.
        //
        // If the class already exists then reuse its type parameter list and scope, otherwise use
        // the newly created one.
        //
        // The reason for this is that otherwise any types parsed with the newly created scope would
        // reference type parameters in the newly created list which are different to the ones
        // belonging to the existing class.
        val (actualTypeParameterList, actualTypeParameterScope) =
            codebase.findClassInCodebase(qualifiedName)?.let { existingClass ->
                // Check to make sure that the type parameter lists are the same.
                val existingTypeParameterList = existingClass.typeParameterList
                val existingTypeParameterListString = existingTypeParameterList.toString()
                val normalizedTypeParameterListString = typeParameterList.toString()
                if (!normalizedTypeParameterListString.equals(existingTypeParameterListString)) {
                    val location = existingClass.location()
                    throw ApiParseException(
                        "Inconsistent type parameter list for $qualifiedName, this has $normalizedTypeParameterListString but it was previously defined as $existingTypeParameterListString at ${location.path}:${location.line}",
                        classPosition
                    )
                }

                Pair(existingTypeParameterList, typeParameterScopeForClass(existingClass))
            }
                ?: Pair(typeParameterList, typeParameterScope)

        return DeclaredClassTypeComponents(
            simpleName = simpleName,
            fullName = fullName,
            qualifiedName = qualifiedName,
            outerClass = outerClass,
            typeParameterList = actualTypeParameterList,
            typeParameterScope = actualTypeParameterScope,
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
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken
        val method: TextConstructorItem

        // Metalava: including annotations in file now
        val annotations: List<String> = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, annotations)
        token = tokenizer.current

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeParameterScope) =
            if ("<" == token) {
                parseTypeParameterList(tokenizer, classTypeParameterScope).also {
                    token = tokenizer.requireToken()
                }
            } else {
                Pair(TypeParameterList.NONE, classTypeParameterScope)
            }

        tokenizer.assertIdent(token)
        val name: String =
            token.substring(
                token.lastIndexOf('.') + 1
            ) // For inner classes, strip outer classes from name
        val parameters = parseParameterList(tokenizer, typeParameterScope)
        // Constructors cannot return null.
        val ctorReturn = cl.type().duplicate(TypeNullability.NONNULL)
        method =
            TextConstructorItem(
                codebase,
                name,
                cl,
                modifiers,
                ctorReturn,
                parameters,
                tokenizer.pos()
            )
        method.setTypeParameterList(typeParameterList)
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
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken
        val method: TextMethodItem

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, null)
        token = tokenizer.current

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeParameterScope) =
            if ("<" == token) {
                parseTypeParameterList(tokenizer, classTypeParameterScope).also {
                    token = tokenizer.requireToken()
                }
            } else {
                Pair(TypeParameterList.NONE, classTypeParameterScope)
            }

        tokenizer.assertIdent(token)

        val returnType: TextTypeItem
        val parameters: List<TextParameterItem>
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, then the return type.
            name = token
            parameters = parseParameterList(tokenizer, typeParameterScope)
            token = tokenizer.requireToken()
            if (token != ":") {
                throw ApiParseException(
                    "Expecting \":\" after parameter list, found $token.",
                    tokenizer
                )
            }
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            returnType = parseType(tokenizer, token, typeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnType = parseType(tokenizer, token, typeParameterScope, annotations)
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            parameters = parseParameterList(tokenizer, typeParameterScope)
            token = tokenizer.requireToken()
        }

        if (cl.isInterface() && !modifiers.isDefault() && !modifiers.isStatic()) {
            modifiers.setAbstract(true)
        }
        method =
            TextMethodItem(codebase, name, cl, modifiers, returnType, parameters, tokenizer.pos())
        method.setTypeParameterList(typeParameterList)
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
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String,
        isEnum: Boolean
    ) {
        var token = startingToken
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, null)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        var type: TextTypeItem
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            type = parseType(tokenizer, token, classTypeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the name, then the type.
            type = parseType(tokenizer, token, classTypeParameterScope, annotations)
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
                !typeParser.kotlinStyleNulls &&
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
        val field = TextFieldItem(codebase, name, cl, modifiers, type, value, tokenizer.pos())
        if (isEnum) {
            cl.addEnumConstant(field)
        } else {
            cl.addField(field)
        }
    }

    private fun parseModifiers(
        tokenizer: Tokenizer,
        startingToken: String?,
        annotations: List<String>?
    ): DefaultModifierList {
        var token = startingToken
        val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
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
        tokenizer: Tokenizer,
        cl: TextClassItem,
        classTypeParameterScope: TypeParameterScope,
        startingToken: String
    ) {
        var token = startingToken

        // Metalava: including annotations in file now
        val annotations = getAnnotations(tokenizer, token)
        token = tokenizer.current
        val modifiers = parseModifiers(tokenizer, token, null)
        token = tokenizer.current
        tokenizer.assertIdent(token)

        val type: TextTypeItem
        val name: String
        if (format.kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token, tokenizer)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            type = parseType(tokenizer, token, classTypeParameterScope, annotations)
            // TODO(b/300081840): update nullability handling
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
        } else {
            // Java style: parse the type, then the name.
            type = parseType(tokenizer, token, classTypeParameterScope, annotations)
            modifiers.addAnnotations(annotations)
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val property = TextPropertyItem(codebase, name, cl, modifiers, type, tokenizer.pos())
        cl.addProperty(property)
    }

    private fun parseTypeParameterList(
        tokenizer: Tokenizer,
        enclosingTypeParameterScope: TypeParameterScope,
    ): Pair<TypeParameterList, TypeParameterScope> {
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
        val typeParameterListString = tokenizer.getStringFromOffset(start)
        return if (typeParameterListString.isEmpty()) {
            Pair(TypeParameterList.NONE, enclosingTypeParameterScope)
        } else {
            createTypeParameterList(enclosingTypeParameterScope, typeParameterListString)
        }
    }

    /**
     * Creates a [TextTypeParameterList].
     *
     * The [typeParameterListString] should be the string representation of a list of type
     * parameters, like "<A>" or "<A, B extends java.lang.String, C>".
     *
     * @return a [Pair] of [TypeParameterList] and [TypeParameterScope] that contains those type
     *   parameters.
     */
    private fun createTypeParameterList(
        enclosingTypeParameterScope: TypeParameterScope,
        typeParameterListString: String
    ): Pair<TypeParameterList, TypeParameterScope> {
        // A type parameter list can contain cycles between its type parameters, e.g.
        //     class Node<L extends Node<L, R>, R extends Node<L, R>>
        // Parsing that requires a multi-stage approach.
        // 1. Separate the list into a mapping from `TextTypeParameterItem` that have not yet
        //    had their `bounds` property initialized to the bounds string list.
        // 2. Create a nested scope of the enclosing scope which includes the type parameters.
        //    That will allow references between them to be resolved.
        // 3. Completing the initialization by converting each bounds string into a TypeItem.

        // Split the type parameter list string into a list of strings, one for each type
        // parameter.
        val typeParameterStrings = TextTypeParser.typeParameterStrings(typeParameterListString)

        // Creating a mapping from a `TextTypeParameterItem` to the bounds string list.
        val itemToBoundsList =
            typeParameterStrings.associateBy({ TextTypeParameterItem.create(codebase, it) }) {
                extractTypeParameterBoundsStringList(it)
            }

        // Extract the `TextTypeParameterItem`s into a list and then use that to construct a
        // scope that can be used to resolve the type parameters, including self references
        // between the ones in this list.
        val typeParameters = itemToBoundsList.keys.toList()
        val scope = enclosingTypeParameterScope.nestedScope(typeParameters)

        // Complete the initialization of the `TextTypeParameterItem`s by converting each bounds
        // string into a `TypeItem`.
        for ((typeParameterItem, boundsStringList) in itemToBoundsList) {
            typeParameterItem.bounds =
                boundsStringList.map {
                    typeParser.obtainTypeFromString(it, scope) as BoundsTypeItem
                }
        }

        return Pair(TextTypeParameterList.create(codebase, typeParameters), scope)
    }

    /**
     * Parses a list of parameters. Before calling, [tokenizer] should point to the token *before*
     * the opening `(` of the parameter list (the method starts by calling
     * [Tokenizer.requireToken]).
     *
     * When the method returns, [tokenizer] will point to the closing `)` of the parameter list.
     */
    private fun parseParameterList(
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
            val modifiers = parseModifiers(tokenizer, token, null)
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
                type = parseType(tokenizer, token, typeParameterScope, annotations)
                // TODO(b/300081840): update nullability handling
                modifiers.addAnnotations(annotations)
                token = tokenizer.current
            } else {
                // Java style: parse the type, then the public name if it has one.
                type = parseType(tokenizer, token, typeParameterScope, annotations)
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
                    codebase,
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

        val parsedType = typeParser.obtainTypeFromString(type, typeParameterScope)
        if (typeParser.kotlinStyleNulls) {
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

    private val stats
        get() =
            Stats(
                codebase.getPackages().allClasses().count(),
                typeParser.requests,
                typeParser.cacheSkip,
                typeParser.cacheHit,
                typeParser.cacheSize,
            )

    data class Stats(
        val totalClasses: Int,
        val typeCacheRequests: Int,
        val typeCacheSkip: Int,
        val typeCacheHit: Int,
        val typeCacheSize: Int,
    )
}

/**
 * Provides access to information that is needed by the [ReferenceResolver].
 *
 * This is provided by [ApiFile] which tracks the names of interfaces and super classes that each
 * class implements/extends respectively before they are resolved.
 */
internal interface ResolverContext {
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
    private val typeParser: TextTypeParser,
    private val classScopeProvider: (ClassItem) -> TypeParameterScope,
) {
    /**
     * A list of all the classes in the text codebase.
     *
     * This takes a copy of the `values` collection rather than use it correctly to avoid
     * [ConcurrentModificationException].
     */
    private val classes = codebase.mAllClasses.values.toList()

    companion object {
        fun resolveReferences(
            context: ResolverContext,
            codebase: TextCodebase,
            typeParser: TextTypeParser,
            classScopeProvider: (ClassItem) -> TypeParameterScope = { TypeParameterScope.empty },
        ) {
            val resolver = ReferenceResolver(context, codebase, typeParser, classScopeProvider)
            resolver.resolveReferences()
        }
    }

    fun resolveReferences() {
        resolveSuperclasses()
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
                    ?: when (cl.classKind) {
                        ClassKind.ENUM -> JAVA_LANG_ENUM
                        // Interfaces and annotations do not have super classes so drop out before
                        // the else clause.
                        ClassKind.ANNOTATION_TYPE,
                        ClassKind.INTERFACE -> continue
                        else -> JAVA_LANG_OBJECT
                    }

            val superClassType =
                typeParser.getSuperType(superClassTypeString, classScopeProvider(cl))
            cl.setSuperClassType(superClassType)

            // Resolve super class types. This is needed because otherwise code that manipulates
            // the codebase while visiting the codebase can cause concurrent modification
            // exceptions.
            // TODO(b/323516595): Find a better way.
            cl.superClass()
        }
    }

    private fun resolveThrowsClasses() {
        for (cl in classes) {
            val classTypeParameterScope = classScopeProvider(cl)
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
                exceptionClass.setSuperClassType(throwableClass.type())
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
