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

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MetalavaApi
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_NAME_TYPE_ORDER
import com.android.tools.metalava.model.text.parser.SignatureFileLexer
import com.android.tools.metalava.model.text.parser.SignatureTokenType
import com.android.tools.metalava.model.type.TypeString
import com.android.tools.metalava.model.utils.extractSimpleName
import com.android.tools.metalava.reporter.FileLocation
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8

/** Encapsulates information needed to process a signature file. */
sealed class SignatureFile {
    /** The underlying signature [File]. */
    abstract val file: File

    /**
     * The [ClassOrigin] of classes and type aliases loaded from [file].
     *
     * Defaults to [ClassOrigin.COMMAND_LINE] so classes are marked to be emitted. Signature files
     * loaded from `--api-path` use [ClassOrigin.CLASS_PATH] so their classes can be resolved
     * without being emitted.
     */
    open val classOrigin: ClassOrigin
        get() = ClassOrigin.COMMAND_LINE

    /**
     * Indicates whether [file] is for the main API surface, i.e. the one that is being created, or
     * a base API surface that it extends.
     */
    protected open val forMainApiSurface: Boolean
        get() = true

    /** The [ApiVariantType] of the signature files. */
    protected open val apiVariantType: ApiVariantType
        get() = ApiVariantType.CORE

    /**
     * Get the [ApiVariant] that this signature file represents.
     *
     * If [forMainApiSurface] is `false` then [apiSurfaces] must provide a non-null value for
     * [ApiSurfaces.base]. An exception will be thrown if it is not.
     *
     * @param apiSurfaces the [ApiSurfaces] the returned [Codebase] is required to support.
     */
    fun apiVariantFor(apiSurfaces: ApiSurfaces): ApiVariant {
        val apiSurface =
            if (forMainApiSurface) apiSurfaces.main
            else
                apiSurfaces.base
                    ?: error("$file expects a base API surface to be available but it is not")
        return apiSurface.variantFor(apiVariantType)
    }

    /** Read the contents of this signature file. */
    abstract fun readContents(): String

    companion object {
        /** Create a list of [SignatureFile]s from a varargs array of [File]s. */
        fun fromFiles(vararg files: File): List<SignatureFile> =
            files.map {
                SignatureFileFromFile(
                    it,
                )
            }

        /**
         * Create a list of [SignatureFile]s from a list of [File]s.
         *
         * @param files the list of [File]s.
         * @param classOrigin the [ClassOrigin] of classes loaded from [files].
         * @param apiVariantTypeChooser A lambda that will be called with the [File] of each item in
         *   [files] and whose return value will be stored in [SignatureFile.apiVariantType].
         * @param forMainApiSurfacePredicate A predicate that will be called with the index and
         *   [File] of each item in [files] and whose return value will be stored in
         *   [SignatureFile.forMainApiSurface].
         */
        fun fromFiles(
            files: List<File>,
            classOrigin: ClassOrigin = ClassOrigin.COMMAND_LINE,
            apiVariantTypeChooser: (File) -> ApiVariantType = { ApiVariantType.CORE },
            forMainApiSurfacePredicate: (Int, File) -> Boolean = { _, _ -> true },
        ): List<SignatureFile> =
            files.mapIndexed { index, file ->
                SignatureFileFromFile(
                    file,
                    classOrigin = classOrigin,
                    forMainApiSurface = forMainApiSurfacePredicate(index, file),
                    apiVariantType = apiVariantTypeChooser(file),
                )
            }

        /** Create a [SignatureFile] that wraps an [InputStream]. */
        fun fromStream(filename: String, inputStream: InputStream): SignatureFile {
            return SignatureFileFromStream(File(filename), inputStream)
        }

        /**
         * Create a [SignatureFile] that wraps a [String].
         *
         * @param filename the name of the file, used for error reporting.
         * @param contents the contents of the file, will be trimmed using [String.trimIndent].
         * @param classOrigin the [ClassOrigin] of classes loaded from [contents].
         */
        fun fromText(
            filename: String,
            contents: String,
            classOrigin: ClassOrigin = ClassOrigin.COMMAND_LINE,
        ): SignatureFile {
            return SignatureFileFromText(File(filename), contents.trimIndent(), classOrigin)
        }
    }

    /** A [SignatureFile] that will read the text from the [file]. */
    private data class SignatureFileFromFile(
        override val file: File,
        override val classOrigin: ClassOrigin = ClassOrigin.COMMAND_LINE,
        override val forMainApiSurface: Boolean = true,
        override val apiVariantType: ApiVariantType = ApiVariantType.CORE,
    ) : SignatureFile() {
        override fun readContents() =
            try {
                file.readText(UTF_8)
            } catch (ex: IOException) {
                throw ApiParseException(
                        "Error reading API file",
                        location = FileLocation.createLocation(file.toPath()),
                    )
                    .apply { initCause(ex) }
            }
    }

    /** A [SignatureFile] that wraps an [InputStream]. */
    private data class SignatureFileFromStream(
        override val file: File,
        val inputStream: InputStream,
    ) : SignatureFile() {
        override fun readContents() = inputStream.bufferedReader().readText()
    }

    /** A [SignatureFile] that wraps a [String]. */
    private data class SignatureFileFromText(
        override val file: File,
        val contents: String,
        override val classOrigin: ClassOrigin = ClassOrigin.COMMAND_LINE,
    ) : SignatureFile() {
        override fun readContents() = contents
    }
}

@MetalavaApi
class ApiFile
private constructor(
    /** Location to use for the created [Codebase]. */
    codebaseLocation: File,
    /** Description to use for the created [Codebase]. */
    codebaseDescription: String,
    /** [Codebase.Config] to use for the created [Codebase]. */
    codebaseConfig: Codebase.Config,
    /** [ClassPathResolver] to use for the created [Codebase]. */
    classPathResolver: ClassPathResolver?,
    private val formatForLegacyFiles: FileFormat?,
    private val allowClassModifierChanges: Boolean,
    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    private val defaultTargetLanguageSet: Set<TargetLanguage> = TargetLanguageSet.ALL,
) {
    private val assembler =
        TextCodebaseAssembler.createAssembler(
            codebaseLocation,
            codebaseDescription,
            codebaseConfig,
            classPathResolver
        )

    private val codebase = assembler.codebase

    companion object {
        /**
         * Parse API signature files.
         *
         * Used by non-Metalava Kotlin code.
         */
        @MetalavaApi
        fun parseApi(
            files: List<File>,
        ) = parseApi(SignatureFile.fromFiles(files))

        /**
         * Read API signature files into a [DefaultCodebase].
         *
         * Note: when reading from them multiple files, [DefaultCodebase.location] would refer to
         * the first file specified. each [Item.fileLocation] would correctly point out the source
         * file of each item.
         *
         * @param signatureFiles input signature files
         */
        fun parseApi(
            signatureFiles: List<SignatureFile>,
            codebaseConfig: Codebase.Config = Codebase.Config.NOOP,
            description: String? = null,
            classPathResolver: ClassPathResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
            /** Whether different signature files can have non-equivalent modifiers for a class. */
            allowClassModifierChanges: Boolean = false,
            /** Provides the caller with access to the [TextTypeParser.Stats]. */
            apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
        ): Codebase {
            require(signatureFiles.isNotEmpty()) { "files must not be empty" }
            val actualDescription =
                description
                    ?: buildString {
                        append("Codebase loaded from ")
                        signatureFiles.joinTo(this)
                        if (classPathResolver == null) {
                            append(" without a class path resolver")
                        } else {
                            append(" with class path resolver ")
                            append(classPathResolver)
                        }
                    }
            val parser =
                ApiFile(
                    codebaseLocation = signatureFiles[0].file,
                    codebaseDescription = actualDescription,
                    codebaseConfig = codebaseConfig,
                    classPathResolver = classPathResolver,
                    formatForLegacyFiles = formatForLegacyFiles,
                    allowClassModifierChanges = allowClassModifierChanges
                )
            return parser.parseMultipleFiles(signatureFiles, apiStatsConsumer)
        }

        /**
         * Parses the [signatureFiles] into a [MultiplatformCodebase].
         *
         * Each signature file represents a source set. If there is a common signature file, all
         * other signature files are parsed as a delta on the common one.
         */
        fun parseMultiplatformApi(
            signatureFiles: List<SignatureFile>,
            codebaseConfig: Codebase.Config = Codebase.Config.NOOP,
        ): MultiplatformCodebase {
            // Find the common signature file, if it exists.
            val commonSignatureFile =
                signatureFiles.firstOrNull {
                    it.file.nameWithoutExtension ==
                        MultiplatformSignatureWriter.COMMON_SOURCE_SET_NAME
                }
            val sourceSetToCodebase =
                if (commonSignatureFile != null) {
                    // When there is a common source set, each other signature file is parsed as an
                    // extension on common.
                    val commonNameToSourceSet = parseSourceSet(codebaseConfig, commonSignatureFile)
                    signatureFiles.associate { signatureFile ->
                        if (signatureFile == commonSignatureFile) {
                            commonNameToSourceSet
                        } else {
                            parseSourceSet(codebaseConfig, signatureFile, commonSignatureFile)
                        }
                    }
                } else {
                    // When there is no common source set, each signature file is parsed separately.
                    signatureFiles.associate { signatureFile ->
                        parseSourceSet(codebaseConfig, signatureFile)
                    }
                }
            return MultiplatformCodebase(sourceSetToCodebase)
        }

        /**
         * Parses the [sourceSetSignatureFile], returning a pair of the name of the source set to
         * the [Codebase] for the source set.
         *
         * If [baseSignatureFile] is not null, it is parsed first with [sourceSetSignatureFile]
         * treated as an extension.
         */
        private fun parseSourceSet(
            codebaseConfig: Codebase.Config,
            sourceSetSignatureFile: SignatureFile,
            baseSignatureFile: SignatureFile? = null,
        ): Pair<String, Codebase> {
            val name = sourceSetSignatureFile.file.nameWithoutExtension
            val parser =
                ApiFile(
                    codebaseLocation = sourceSetSignatureFile.file,
                    codebaseDescription = "Codebase for source set $name",
                    codebaseConfig = codebaseConfig,
                    classPathResolver = null,
                    formatForLegacyFiles = null,
                    allowClassModifierChanges = true,
                    defaultTargetLanguageSet = TargetLanguageSet.KOTLIN_ONLY,
                )
            // Parse the base file first if it exists.
            val codebase =
                parser.parseMultipleFiles(listOfNotNull(baseSignatureFile) + sourceSetSignatureFile)
            return name to codebase
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
            val signatureFile = SignatureFile.fromStream(filename, inputStream)
            return parseApi(listOf(signatureFile))
        }

        /**
         * Parses [signatureFiles] and notifies [consumer] of all parsed packages, classes, type
         * aliases, and members without requiring a [Codebase] to be constructed.
         *
         * @param signatureFiles the signature files to parse; must not be empty.
         * @param consumer the [SignatureFileConsumer] notified of parsed declarations.
         * @param formatForLegacyFiles optional [FileFormat] to use when parsing legacy signature
         *   files without a format header.
         */
        @MetalavaApi
        fun consumeApi(
            signatureFiles: List<SignatureFile>,
            consumer: SignatureFileConsumer,
            formatForLegacyFiles: FileFormat? = null,
        ) {
            require(signatureFiles.isNotEmpty()) { "files must not be empty" }
            var appending = false
            for (signatureFile in signatureFiles) {
                // When we're appending, and the content is empty, there is nothing to do.
                val apiText = signatureFile.readContents()
                if (appending && apiText.isBlank()) {
                    continue
                }

                val path = signatureFile.file.toPath()

                // Parse the header of the signature file to determine the format. If the signature
                // file is empty then `parseHeader` will return null, so it will default to
                // `FileFormat.V2`.
                val format =
                    FileFormat.parseHeader(path, StringReader(apiText), formatForLegacyFiles)
                        ?: FileFormat.V2

                val lineMap = LineMap.create(apiText)
                consumer.startFile(signatureFile, path, lineMap, format)

                val parser =
                    SingleSignatureFileParser(
                        consumer = consumer,
                        path = path,
                        apiText = apiText,
                        lineMap = lineMap,
                        kotlinNameTypeOrder = format[KOTLIN_NAME_TYPE_ORDER],
                    )

                parser.parse()

                consumer.endFile()

                appending = true
            }

            consumer.finish()
        }
    }

    /**
     * Parses all the [signatureFiles], treating the first file as the base API and all other files
     * as extensions.
     */
    private fun parseMultipleFiles(
        signatureFiles: List<SignatureFile>,
        apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
    ): Codebase {
        val builder = createCodebaseBuilder(apiStatsConsumer)
        consumeApi(signatureFiles, builder, formatForLegacyFiles)
        return codebase
    }

    /**
     * Creates the [SignatureFileCodebaseBuilder] shared across all signature files parsed by
     * [parseMultipleFiles].
     *
     * @param apiStatsConsumer consumer of [TextTypeParser.Stats] after all signature files have
     *   been parsed.
     */
    private fun createCodebaseBuilder(
        apiStatsConsumer: (TextTypeParser.Stats) -> Unit,
    ): SignatureFileCodebaseBuilder {
        return SignatureFileCodebaseBuilder(
            assembler = assembler,
            defaultTargetLanguageSet = defaultTargetLanguageSet,
            classMerger = ClassMerger(allowClassModifierChanges),
            apiStatsConsumer = apiStatsConsumer,
        )
    }
}

/** Parser for a single signature file. */
internal class SingleSignatureFileParser(
    /** The [SignatureFileConsumer] notified of parsed declarations in this file. */
    private val consumer: SignatureFileConsumer,

    /** The [Path] to the signature file being parsed. */
    private val path: Path,

    /** The contents of the signature file being parsed. */
    private val apiText: String,

    /** Maps character offsets in [apiText] to line numbers. */
    private val lineMap: LineMap,

    /** See [KOTLIN_NAME_TYPE_ORDER]. */
    private val kotlinNameTypeOrder: Boolean,
) {
    private val defaultTargetLanguageSet = consumer.defaultTargetLanguageSet

    /** The [TokenStream] of tokens from [apiText]. */
    private val tokenStream: TokenStream = SignatureFileLexer(apiText).tokenize()

    /** Get the [FileLocation] at the 0-based [charOffset]. */
    fun fileLocation(charOffset: Int): FileLocation = lineMap.fileLocation(path, charOffset)

    /** Get the [FileLocation] of the start of [token]. */
    private fun fileLocation(token: Token): FileLocation = fileLocation(token.startOffset)

    /** Get the [SourcePosition] of the start of [token]. */
    private fun sourcePositionForToken(token: Token): SourcePosition =
        SourcePosition(token.startOffset)

    /** Get the contents of the file being parsed from [start] to [end]. */
    private fun fileSubstring(start: Int, end: Int): String = apiText.substring(start, end)

    /** Extract the text of [token] from [apiText]. */
    private fun text(token: Token): String = token.text(apiText)

    /** Returns the next [Token] in [tokenStream] without consuming it. */
    private fun peek(): Token = tokenStream.peek()

    /** Returns the [TokenType] of the next [Token] in [tokenStream] without consuming it. */
    private fun peekType(): TokenType = tokenStream.peekType()

    /** Consumes and returns the next [Token] from [tokenStream]. */
    private fun consume(): Token = tokenStream.consume()

    /** Consumes the next [Token] in [tokenStream] if its type equals [type], returning `true`. */
    private fun match(type: TokenType): Boolean = tokenStream.match(type)

    /**
     * Consumes and returns the next [Token] from [tokenStream], throwing an [ApiParseException] if
     * the end of the file has been reached.
     */
    private fun requireNonEofToken(): Token {
        val token = peek()
        if (token.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", token)
        }
        return consume()
    }

    /**
     * Checks that [token] can be used as an identifier, throwing an [ApiParseException] at the
     * location of [token] if it cannot.
     */
    private fun assertIdent(token: Token) {
        if (!token.type.canBeIdentifier) {
            throw parseException("Expected identifier: ${text(token)}", token)
        }
    }

    /** Creates an [ApiParseException] with [message] at the location of [token]. */
    private fun parseException(message: String, token: Token): ApiParseException =
        ApiParseException(
            message,
            fileLocation(token),
        )

    /**
     * Parses a dot-separated identifier (such as a package, class, or constructor name) from
     * [tokenStream] and returns the complete qualified name.
     */
    private fun parseQualifiedName(): String {
        // Consume the first identifier segment.
        val firstToken = requireNonEofToken()
        assertIdent(firstToken)
        var endOffset = firstToken.endOffset

        // Consume any subsequent '.<identifier>' segments.
        while (peekType() == SharedTokenType.DOT) {
            consume()
            val nextToken = requireNonEofToken()
            assertIdent(nextToken)
            endOffset = nextToken.endOffset
        }

        return fileSubstring(firstToken.startOffset, endOffset)
    }

    /** Parse the signature file from [tokenStream], notifying [consumer] of each declaration. */
    fun parse() {
        // Consume each top-level `package` block until the end of the file is reached.
        while (peekType() != SharedTokenType.EOF) {
            val token = consume()
            // TODO: Accept annotations on packages.
            if (token.type == SignatureTokenType.PACKAGE) {
                parsePackage(token)
            } else {
                throw parseException("expected package got ${text(token)}", token)
            }
        }
    }

    private fun parsePackage(packageToken: Token) {
        // Metalava: including annotations in file now
        val modifiers = parseModifierAnnotations(VisibilityLevel.PACKAGE_PRIVATE)
        val name: String = parseQualifiedName()

        consumer.startPackage(sourcePositionForToken(packageToken), modifiers, name)

        // Consume the `{` opening the package body, then parse each class/typealias until `}`.
        val openBrace = requireNonEofToken()
        if (openBrace.type != SharedTokenType.BRACE_OPEN) {
            throw parseException("expected '{' got ${text(openBrace)}", openBrace)
        }
        while (!match(SharedTokenType.BRACE_CLOSE)) {
            parseClass()
        }

        consumer.endPackage()
    }

    /**
     * Parses a `typealias` declaration with [modifiers] at [sourcePosition].
     *
     * Before calling, the `typealias` keyword should have been consumed from [tokenStream], and the
     * next token will be the name and optional type parameter list.
     *
     * When the method returns, [tokenStream] will have consumed the `;` at the end of the typealias
     * line.
     */
    private fun parseTypeAlias(
        modifiers: MutableModifierList,
        sourcePosition: SourcePosition,
    ) {
        // Parse the typealias name and optional `<...>` type parameter list.
        val name = parseQualifiedName()
        val parsedTypeParameters = parseTypeParameterList()

        // Consume `=`, scan the aliased type, and consume the terminating `;`.
        val equalsToken = requireNonEofToken()
        if (equalsToken.type != SharedTokenType.EQUALS) {
            throw parseException("expected = found ${text(equalsToken)}", equalsToken)
        }

        val typeString = scanForTypeString()
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        consumer.consumeTypeAlias(
            sourcePosition = sourcePosition,
            modifiers = modifiers,
            name = name,
            typeParameters = parsedTypeParameters,
            aliasedTypeString = typeString,
        )
    }

    /** Parse a class or typealias in the current package. */
    private fun parseClass() {
        // Notify the consumer before parsing the class or typealias's modifiers and annotations.
        consumer.beforeClass()
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()
        val kindToken = requireNonEofToken()
        // Remember this position as this seems like a good place to use to report issues with the
        // class item.
        val classPosition = sourcePositionForToken(kindToken)

        val classKind =
            when (kindToken.type) {
                SharedTokenType.CLASS -> ClassKind.CLASS
                SignatureTokenType.INTERFACE -> ClassKind.INTERFACE
                SignatureTokenType.ENUM -> ClassKind.ENUM
                SignatureTokenType.ANNOTATION_INTERFACE -> ClassKind.ANNOTATION_TYPE
                SignatureTokenType.RECORD -> ClassKind.RECORD
                SignatureTokenType.TYPEALIAS -> ClassKind.TYPEALIAS
                else ->
                    throw parseException(
                        "expected one of ${ClassKind.entries.joinToString { it.signatureKeyword }}; found: ${text(kindToken)}",
                        kindToken,
                    )
            }

        if (classKind == ClassKind.TYPEALIAS) {
            // Type aliases aren't classes, but they are defined at the same level as classes
            parseTypeAlias(modifiers, classPosition)
            // Don't continue creating a class item
            return
        }

        classKind.setImplicitModifiers(modifiers)

        // Parse the declared class name and optional `<...>` type parameter list.
        val fullName = parseQualifiedName()
        val parsedTypeParameters = parseTypeParameterList()

        val superClassTypeString = parseSuperClassTypeString(classKind)
        val interfaceTypeStrings = parseInterfaceTypeStrings()
        val permitTypeStrings = parsePermitTypeStrings()

        val openBrace = requireNonEofToken()
        if (openBrace.type != SharedTokenType.BRACE_OPEN) {
            throw parseException("expected {, was ${text(openBrace)}", openBrace)
        }

        // Above we marked all enums as static but for a top level class it's implicit
        if (classKind == ClassKind.ENUM && !fullName.contains(".")) {
            modifiers.setStatic(false)
        }

        val recordComponents =
            if (classKind == ClassKind.RECORD) {
                // Parse record components
                parseRecordComponents()
            } else {
                null
            }

        consumer.startClass(
            sourcePosition = classPosition,
            modifiers = modifiers,
            targetLanguages = targetLanguages,
            classKind = classKind,
            fullName = fullName,
            typeParameters = parsedTypeParameters,
            superClassTypeString = superClassTypeString,
            interfaceTypeStrings = interfaceTypeStrings,
            permitTypeStrings = permitTypeStrings,
            recordComponents = recordComponents,
        )

        // Parse the class body adding each member created to the class item being populated.
        parseClassBody()

        consumer.endClass()
    }

    /**
     * Parse the class body, notifying [consumer] of each member declaration.
     *
     * When the method returns, [tokenStream] will have consumed the closing `}` of the class body.
     */
    private fun parseClassBody() {
        // Dispatch each member declaration by its leading keyword token type until the closing `}`
        // is consumed.
        while (!match(SharedTokenType.BRACE_CLOSE)) {
            // Notify the consumer before parsing the member's modifiers and annotations.
            consumer.beforeMember()
            val memberToken = requireNonEofToken()
            when (memberToken.type) {
                SignatureTokenType.CTOR -> {
                    parseConstructor()
                }
                SignatureTokenType.ENUM_CONSTANT -> {
                    parseEnumConstant()
                }
                SignatureTokenType.FIELD -> {
                    parseField()
                }
                SignatureTokenType.METHOD -> {
                    parseMethod()
                }
                SignatureTokenType.PROPERTY -> {
                    parseProperty()
                }
                else ->
                    throw parseException(
                        "expected one of ctor, enum_constant, field, method, property",
                        memberToken,
                    )
            }
        }
    }

    /**
     * Parses the optional `extends <superclass>` clause for a non-interface class, returning the
     * raw [TypeString] if present or `null` otherwise.
     */
    private fun parseSuperClassTypeString(
        classKind: ClassKind,
    ): TypeString? {
        // Interfaces use `extends` for super-interfaces rather than a superclass.
        if (peekType() == SharedTokenType.EXTENDS && classKind != ClassKind.INTERFACE) {
            consume()
            return parseSuperTypeString()
        }
        return null
    }

    /**
     * Parses the optional `implements` (or `extends` for interfaces) clause from [tokenStream],
     * returning the list of raw super-interface [TypeString]s.
     */
    private fun parseInterfaceTypeStrings(): List<TypeString> {
        if (peekType() != SignatureTokenType.IMPLEMENTS && peekType() != SharedTokenType.EXTENDS) {
            return emptyList()
        }
        consume()
        return buildList {
            // Consume super-interface types separated by optional commas until the class body `{`
            // or a `permits` clause is reached.
            while (true) {
                val nextType = peekType()
                if (
                    nextType == SharedTokenType.BRACE_OPEN || nextType == SignatureTokenType.PERMITS
                ) {
                    break
                } else if (nextType != SharedTokenType.COMMA) {
                    add(parseSuperTypeString())
                } else {
                    consume()
                }
            }
        }
    }

    /**
     * Parses the optional `permits` clause for a sealed class or interface from [tokenStream],
     * returning the list of raw permitted subclass [TypeString]s.
     */
    private fun parsePermitTypeStrings(): List<TypeString> {
        if (!match(SignatureTokenType.PERMITS)) {
            return emptyList()
        }
        return buildList {
            // Consume permitted subclass types up to the opening `{` of the class body.
            while (peekType() != SharedTokenType.BRACE_OPEN) {
                add(parseSuperTypeString())
            }
        }
    }

    /**
     * Skips any leading `@...` type-use annotations and consumes the following identifier token,
     * returning the `endOffset` of that identifier token.
     */
    private fun skipAnnotatedIdentifier(): Int {
        while (peekType() == SharedTokenType.AT) {
            skipAnnotation()
        }
        val identToken = requireNonEofToken()
        assertIdent(identToken)
        return identToken.endOffset
    }

    /**
     * Skips a balanced `<...>` type argument list starting at the next `<` token in [tokenStream],
     * along with any immediately adjacent identifier token following `>`, returning the `endOffset`
     * of the last consumed token.
     */
    private fun skipTypeArgumentList(): Int {
        var endOffset = skipAngleBracketList()
        // Include any identifier token immediately adjacent to the closing `>` (without intervening
        // whitespace).
        if (peek().startOffset == endOffset && peekType().canBeIdentifier) {
            endOffset = consume().endOffset
        }
        return endOffset
    }

    /**
     * Parse a super type string, i.e. a string representing a super class type, super interface
     * type, or permitted subclass type.
     */
    private fun parseSuperTypeString(): TypeString {
        val firstToken = peek()
        if (firstToken.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", firstToken)
        }
        val startOffset = firstToken.startOffset

        // Consume the initial (possibly annotated) identifier segment of the type.
        var endOffset = skipAnnotatedIdentifier()

        // Continue consuming qualified segments (`.Foo`), type argument lists (`<...>`), and
        // Kotlin nullability suffixes (`?` or `!`).
        while (true) {
            when (peekType()) {
                SharedTokenType.DOT -> {
                    consume()
                    endOffset = skipAnnotatedIdentifier()
                }
                SharedTokenType.ANGLE_OPEN -> {
                    endOffset = skipTypeArgumentList()
                }
                SharedTokenType.QUESTION,
                SharedTokenType.EXCLAMATION -> {
                    endOffset = consume().endOffset
                }
                else -> break
            }
        }

        return TypeString(fileSubstring(startOffset, endOffset), startOffset)
    }

    /**
     * Skips a `@QualifiedName(...)` annotation starting at the current `@` token in [tokenStream],
     * returning the `endOffset` of the last token of the annotation.
     */
    private fun skipAnnotation(): Int {
        // Consume the leading `@` token.
        val atToken = requireNonEofToken()
        var endOffset = atToken.endOffset

        // Consume the dot-separated qualified name of the annotation class.
        val firstIdent = requireNonEofToken()
        assertIdent(firstIdent)
        endOffset = firstIdent.endOffset
        while (peekType() == SharedTokenType.DOT) {
            consume()
            val nextIdent = requireNonEofToken()
            assertIdent(nextIdent)
            endOffset = nextIdent.endOffset
        }

        // If the annotation has an argument list, consume balanced `(...)` tokens.
        if (peekType() == SharedTokenType.PAREN_OPEN) {
            consume()
            var balance = 1
            while (balance > 0) {
                val tok = requireNonEofToken()
                endOffset = tok.endOffset
                if (tok.type == SharedTokenType.PAREN_OPEN) {
                    balance++
                } else if (tok.type == SharedTokenType.PAREN_CLOSE) {
                    balance--
                }
            }
        }
        return endOffset
    }

    /** Parse a constructor member of the current class. */
    private fun parseConstructor() {
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        val parsedTypeParameters = parseTypeParameterList()

        // For nested classes, strip outer classes from name
        val name: String = parseQualifiedName().extractSimpleName()

        val parameters = parseParameterList()
        // Parse the optional `throws` clause and terminating `;`.
        val throwsTypeStrings =
            if (match(SignatureTokenType.THROWS)) {
                parseThrowsTypeStrings()
            } else {
                emptyList()
            }
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        consumer.consumeConstructor(
            sourcePosition = sourcePositionForToken(semicolon),
            modifiers = modifiers,
            targetLanguages = targetLanguages,
            name = name,
            typeParameters = parsedTypeParameters,
            parameters = parameters,
            throwsTypeStrings = throwsTypeStrings,
        )
    }

    /** Parse a method member of the current class. */
    private fun parseMethod() {
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        val parsedTypeParameters = parseTypeParameterList()

        val returnTypeString: TypeString
        val parameters: List<ParsedParameter>
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, the `:` separator, then the return
            // type.
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
            parameters = parseParameterList()
            val colonToken = requireNonEofToken()
            if (colonToken.type != SharedTokenType.COLON) {
                throw parseException(
                    "Expecting \":\" after parameter list, found ${text(colonToken)}.",
                    colonToken,
                )
            }
            returnTypeString = scanForTypeString()
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnTypeString = scanForTypeString()
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
            parameters = parseParameterList()
        }

        var throwsTypeStrings = emptyList<TypeString>()
        var defaultAnnotationMethodValue: String? = null

        // Parse an optional `throws` clause or annotation method `default` value before the
        // terminating `;`.
        when {
            match(SignatureTokenType.THROWS) -> {
                throwsTypeStrings = parseThrowsTypeStrings()
            }
            match(SignatureTokenType.DEFAULT) -> {
                defaultAnnotationMethodValue = scanValueUntilSemicolon()
            }
        }
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        consumer.consumeMethod(
            sourcePosition = sourcePositionForToken(semicolon),
            modifiers = modifiers,
            targetLanguages = targetLanguages,
            name = name,
            returnTypeString = returnTypeString,
            typeParameters = parsedTypeParameters,
            parameters = parameters,
            throwsTypeStrings = throwsTypeStrings,
            defaultAnnotationMethodValue = defaultAnnotationMethodValue,
        )
    }

    /** Parse a field member of the current class. */
    private fun parseField() {
        parseFieldOrEnumConstant(isEnumConstant = false)
    }

    /** Parse an enum constant member of the current class. */
    private fun parseEnumConstant() {
        parseFieldOrEnumConstant(isEnumConstant = true)
    }

    /** Parse a field or enum constant of the current class. */
    private fun parseFieldOrEnumConstant(isEnumConstant: Boolean) {
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        val typeString: TypeString
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon()
            typeString = scanForTypeString()
        } else {
            // Java style: parse the type, then the name.
            typeString = scanForTypeString()
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
        }
        var token = requireNonEofToken()

        // If an `=` follows, scan the field's initial value up to the terminating `;` and then
        // consume that `;`.
        val valueString =
            if (token.type == SharedTokenType.EQUALS) {
                scanValueUntilSemicolon().also { token = requireNonEofToken() }
            } else null

        if (token.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(token)}", token)
        }

        consumer.consumeField(
            sourcePosition = sourcePositionForToken(token),
            modifiers = modifiers,
            targetLanguages = targetLanguages,
            name = name,
            typeString = typeString,
            valueString = valueString,
            isEnumConstant = isEnumConstant,
        )
    }

    /**
     * Parses and creates an optional target language set and modifiers (see [parseModifiers]).
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseModifiersAndTargetLanguages(): Pair<MutableModifierList, Set<TargetLanguage>> {
        // Check if there's a token describing the target languages of the item. If there is,
        // consume it, if not, use the set of all languages.
        val targetLanguages =
            if (peekType() == SignatureTokenType.TARGET_LANGUAGE) {
                val token = consume()
                TargetLanguageSet.signatureFileRepresentationToTargetLanguageSet[text(token)]
                    ?: defaultTargetLanguageSet
            } else {
                defaultTargetLanguageSet
            }

        val modifiers = parseModifiers()
        return modifiers to targetLanguages
    }

    /**
     * Parses and creates modifiers, including annotations and keyword modifiers.
     *
     * If there is no visibility modifier, [VisibilityLevel.PACKAGE_PRIVATE] is used.
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseModifiers(): MutableModifierList {
        val modifiers = parseModifierAnnotations(VisibilityLevel.PACKAGE_PRIVATE)
        parseKeywordModifiers(modifiers)
        return modifiers
    }

    /**
     * Updates the [modifiers] to reflect all modifier keywords parsed from [tokenStream].
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseKeywordModifiers(modifiers: MutableModifierList) {
        // Peek at each subsequent token and update `modifiers` while modifier keywords are seen.
        while (true) {
            when (peekType()) {
                SignatureTokenType.PUBLIC -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                }
                SignatureTokenType.PROTECTED -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PROTECTED)
                }
                SignatureTokenType.PRIVATE -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                }
                SignatureTokenType.INTERNAL -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
                }
                SignatureTokenType.STATIC -> {
                    modifiers.setStatic(true)
                }
                SignatureTokenType.FINAL -> {
                    modifiers.setFinal(true)
                }
                SignatureTokenType.DEPRECATED -> {
                    modifiers.setDeprecated(true)
                }
                SignatureTokenType.ABSTRACT -> {
                    modifiers.setAbstract(true)
                }
                SignatureTokenType.TRANSIENT -> {
                    modifiers.setTransient(true)
                }
                SignatureTokenType.VOLATILE -> {
                    modifiers.setVolatile(true)
                }
                SignatureTokenType.SEALED -> {
                    modifiers.setSealed(true)
                    // When reading in a sealed class, for backwards compatibility we want
                    // to label it as non-exhaustive (for more details on what this means,
                    // see b/447143803) in case the signature file doesn't have one of
                    // "exhaustive" or "nonexhaustive" after the "sealed" modifier. This
                    // allows compatibility checks to not raise unnecessary errors for
                    // sealed classes without an exhaustivity modifier. If the class is indeed
                    // labeled with an exhaustivity modifier in the signature file, the class's
                    // exhaustivity will be adjusted accordingly in the following match
                    // statements.
                    modifiers.setExhaustive(false)
                }
                SignatureTokenType.NON_SEALED -> {
                    modifiers.setNonSealed(true)
                }
                SignatureTokenType.EXHAUSTIVE -> {
                    modifiers.setExhaustive(true)
                }
                SignatureTokenType.NON_EXHAUSTIVE -> {
                    modifiers.setExhaustive(false)
                }
                SignatureTokenType.DEFAULT -> {
                    modifiers.setDefault(true)
                }
                SignatureTokenType.SYNCHRONIZED -> {
                    modifiers.setSynchronized(true)
                }
                SignatureTokenType.NATIVE -> {
                    modifiers.setNative(true)
                }
                SignatureTokenType.STRICTFP -> {
                    modifiers.setStrictFp(true)
                }
                SignatureTokenType.INFIX -> {
                    modifiers.setInfix(true)
                }
                SignatureTokenType.OPERATOR -> {
                    modifiers.setOperator(true)
                }
                SignatureTokenType.INLINE -> {
                    modifiers.setInline(true)
                }
                SignatureTokenType.VALUE -> {
                    modifiers.setValue(true)
                }
                SignatureTokenType.SUSPEND -> {
                    modifiers.setSuspend(true)
                }
                SignatureTokenType.VARARG -> {
                    modifiers.setVarArg(true)
                }
                SignatureTokenType.FUN -> {
                    modifiers.setFunctional(true)
                }
                SignatureTokenType.DATA -> {
                    modifiers.setData(true)
                }
                // Stop without consuming once a non-modifier token is reached.
                else -> break
            }

            // Consume the matched modifier keyword token and continue to the next token.
            consume()
        }
    }

    /**
     * Parses and creates modifiers, including annotations but not keyword modifiers.
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseModifierAnnotations(
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList =
        consumer.parseModifierAnnotations(tokenStream, apiText, visibilityLevel)

    /** Parse a property member of the current class. */
    private fun parseProperty() {
        val modifiers = parseModifiers()

        val parsedTypeParameters = parseTypeParameterList()

        val typeString: TypeString
        val (receiverTypeString, name) =
            if (kotlinNameTypeOrder) {
                // Kotlin style: parse the name, then the type.
                val receiverAndName = parsePropertyReceiverAndName()
                typeString = scanForTypeString()
                receiverAndName
            } else {
                // Java style: parse the type, then the name.
                typeString = scanForTypeString()
                parsePropertyReceiverAndName()
            }

        // If an opening `(` follows the property declaration, parse its context parameter list.
        val contextParameters =
            if (peekType() == SharedTokenType.PAREN_OPEN) {
                parseParameterList(
                    useUnderscoreAsDefaultName = true,
                )
            } else {
                emptyList()
            }

        // Consume the terminating `;` of the property declaration.
        val token = requireNonEofToken()
        if (token.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(token)}", token)
        }

        consumer.consumeProperty(
            sourcePosition = sourcePositionForToken(token),
            modifiers = modifiers,
            name = name,
            receiverTypeString = receiverTypeString,
            typeString = typeString,
            typeParameters = parsedTypeParameters,
            contextParameters = contextParameters,
        )
    }

    /**
     * Parses the optional receiver type and then the name of a property from [tokenStream].
     *
     * After the method returns, the caller should continue processing at the next token in
     * [tokenStream].
     */
    private fun parsePropertyReceiverAndName(): Pair<TypeString?, String> {
        // If there's no receiver, scanning for the type string should just return the name.
        // If there is a receiver, it will return "receiver.name", which can then be split on the
        // last "." to the receiver and name.
        val receiverAndName = scanForTypeString()
        val namePossiblyWithColon: String
        val receiverTypeString: TypeString?
        if (receiverAndName.type.contains(".")) {
            namePossiblyWithColon = receiverAndName.type.substringAfterLast(".")
            receiverTypeString =
                TypeString(
                    receiverAndName.type.substringBeforeLast("."),
                    receiverAndName.offset,
                )
        } else {
            namePossiblyWithColon = receiverAndName.type
            receiverTypeString = null
        }

        val name =
            if (kotlinNameTypeOrder) {
                parseNameWithColon(namePossiblyWithColon)
            } else {
                namePossiblyWithColon
            }

        return receiverTypeString to name
    }

    /** Parse `#<record-component-index>` from [tokenStream]. */
    private fun parseRecordComponentIndex(): Int {
        // `#<index>` is tokenized as a `#` token immediately adjacent to a non-negative integer
        // literal token.
        val firstToken = requireNonEofToken()
        if (
            firstToken.type == SignatureTokenType.HASH &&
                peekType() == SharedTokenType.NUMBER_LITERAL &&
                peek().startOffset == firstToken.endOffset
        ) {
            val numberToken = requireNonEofToken()
            val index = text(numberToken).toIntOrNull()
            if (index != null && index >= 0) {
                return index
            }
        }
        throw parseException(
            "Expected record component index #<index> but found '${text(firstToken)}'",
            firstToken,
        )
    }

    /**
     * Parse record components, returning them as a list of [ParsedRecordComponent].
     *
     * Consumes all consecutive `record_component` declarations from [tokenStream].
     */
    private fun parseRecordComponents(): List<ParsedRecordComponent> = buildList {
        while (peekType() == SignatureTokenType.RECORD_COMPONENT) {
            val recordComponent = parseRecordComponent()
            add(recordComponent)
        }
    }

    /** Parse a record component class member into a [ParsedRecordComponent]. */
    private fun parseRecordComponent(): ParsedRecordComponent {
        // Consume the `record_component` keyword and record its location.
        val recordComponentToken = requireNonEofToken()
        val sourcePosition = sourcePositionForToken(recordComponentToken)

        // Parse a record component index.
        val recordComponentIndex = parseRecordComponentIndex()

        // Parse the modifiers, which will really just be annotations. Record components are always
        // public.
        val modifiers = parseModifierAnnotations(VisibilityLevel.PUBLIC)

        // Parse the component name.
        val name = parseNameWithColon()

        // Parse the type.
        val typeString = scanForTypeString()

        // Make sure that the whole record component was parsed.
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        return ParsedRecordComponent(
            sourcePosition,
            modifiers,
            name,
            typeString,
            recordComponentIndex,
        )
    }

    /**
     * Skips a balanced `<...>` list starting at the next `<` token in [tokenStream], returning the
     * `endOffset` of the closing `>` token.
     */
    private fun skipAngleBracketList(): Int {
        // Consume the opening `<` token and track nesting depth until the matching `>` is consumed.
        val startToken = requireNonEofToken()
        var endOffset = startToken.endOffset
        var balance = 1
        while (balance > 0) {
            val token = requireNonEofToken()
            endOffset = token.endOffset
            if (token.type == SharedTokenType.ANGLE_OPEN) {
                balance++
            } else if (token.type == SharedTokenType.ANGLE_CLOSE) {
                balance--
            }
        }
        return endOffset
    }

    /**
     * Parses a `<...>` type parameter list from [tokenStream] if the next token is `<`, returning
     * the list of [ParsedTypeParameter]s.
     *
     * If the next token in [tokenStream] is not `<`, returns an empty list without consuming any
     * tokens.
     */
    private fun parseTypeParameterList(): List<ParsedTypeParameter> {
        if (!match(SharedTokenType.ANGLE_OPEN)) {
            return emptyList()
        }

        if (match(SharedTokenType.ANGLE_CLOSE)) {
            return emptyList()
        }

        return buildList {
            while (true) {
                val isReified = match(SharedTokenType.REIFIED)
                val nameToken = requireNonEofToken()
                assertIdent(nameToken)
                val name = text(nameToken)

                // Parse any `&`-separated bound types following `extends`.
                val bounds =
                    if (match(SharedTokenType.EXTENDS)) {
                        buildList {
                            do {
                                add(scanForTypeString())
                            } while (match(SharedTokenType.AMPERSAND))
                        }
                    } else {
                        emptyList()
                    }

                add(ParsedTypeParameter(name, isReified, bounds))

                // Consume `,` (with optional trailing `>`) or the closing `>` of the list.
                val delimiter = requireNonEofToken()
                when (delimiter.type) {
                    SharedTokenType.COMMA -> {
                        if (match(SharedTokenType.ANGLE_CLOSE)) {
                            break
                        }
                    }
                    SharedTokenType.ANGLE_CLOSE -> break
                    else -> {
                        throw parseException(
                            "expected , or >, found ${text(delimiter)}",
                            delimiter,
                        )
                    }
                }
            }
        }
    }

    /**
     * Parses a list of parameters.
     *
     * Before calling, [tokenStream] should point to the opening `(` of the parameter list.
     *
     * If [useUnderscoreAsDefaultName] is true, parameters without a public name will have "_" as
     * their name. If it is false, they will have "arg<index>" as their name.
     *
     * When the method returns, [tokenStream] will have consumed the closing `)` of the parameter
     * list.
     */
    private fun parseParameterList(
        useUnderscoreAsDefaultName: Boolean = false,
    ): List<ParsedParameter> {
        val parameters = mutableListOf<ParsedParameter>()
        // Consume the opening `(` of the parameter list.
        val openParen = requireNonEofToken()
        if (openParen.type != SharedTokenType.PAREN_OPEN) {
            throw parseException("expected (, was ${text(openParen)}", openParen)
        }
        var index = 0
        while (true) {
            if (match(SharedTokenType.PAREN_CLOSE)) {
                // All parameters are parsed, return them.
                return parameters
            }

            // Each item can be:
            //   optional-"optional" annotations optional-modifiers
            //   type-with-use-annotations-and-generics optional-name

            // Used to represent the presence of a default value, instead of showing the entire
            // default value
            val hasOptionalKeyword = match(SignatureTokenType.OPTIONAL)

            // The kind of the parameter might be specified.
            val optionalKind =
                when (peekType()) {
                    SignatureTokenType.CONTEXT -> {
                        consume()
                        ParameterKind.CONTEXT
                    }
                    SignatureTokenType.RECEIVER -> {
                        consume()
                        ParameterKind.RECEIVER
                    }
                    else -> null
                }

            val modifiers = parseModifiers()

            val typeString: TypeString
            val publicName: String?
            if (kotlinNameTypeOrder) {
                // Kotlin style: parse the name (only considered a public name if it is not `_`,
                // which is used as a placeholder for params without public names), then the type.
                val nameOrPlaceholder = parseNameWithColon()
                publicName =
                    if (nameOrPlaceholder == "_") {
                        null
                    } else {
                        nameOrPlaceholder
                    }
                // Token should now represent the type
                typeString = scanForTypeString()
            } else {
                // Java style: parse the type, then the public name if an identifier follows before
                // the `,` or `)` delimiter.
                typeString = scanForTypeString()
                if (peekType().canBeIdentifier) {
                    publicName = text(consume())
                } else {
                    publicName = null
                }
            }

            // Consume the parameter delimiter (`,` if more parameters follow, or `)` at the end of
            // the parameter list).
            val delimiter = requireNonEofToken()
            val isDone =
                when (delimiter.type) {
                    SharedTokenType.COMMA -> false
                    SharedTokenType.PAREN_CLOSE -> true
                    else -> {
                        throw parseException(
                            "expected , or ), found ${text(delimiter)}",
                            delimiter,
                        )
                    }
                }

            val name = publicName ?: (if (useUnderscoreAsDefaultName) "_" else "arg${index + 1}")
            parameters.add(
                ParsedParameter(
                    name,
                    publicName,
                    // The optional keyword indicates whether a parameter has a default value
                    hasDefaultValue = hasOptionalKeyword,
                    typeString,
                    modifiers,
                    sourcePositionForToken(delimiter),
                    index,
                    optionalKind,
                )
            )
            index++
            if (isDone) {
                return parameters
            }
        }
    }

    /**
     * Scans a field or annotation method default value expression from [tokenStream] up to (but not
     * consuming) the terminating `;`.
     */
    private fun scanValueUntilSemicolon(): String {
        // Consume the first token of the value expression.
        val firstToken = requireNonEofToken()
        val startOffset = firstToken.startOffset
        var endOffset = firstToken.endOffset

        // Track nesting depth of parentheses, braces, and angle brackets so that tokens inside
        // nested expressions (such as array initializers or annotation arguments) are included.
        var parenDepth = 0
        var braceDepth = 0
        var angleDepth = 0
        var current = firstToken
        while (true) {
            when (current.type) {
                SharedTokenType.PAREN_OPEN -> parenDepth++
                SharedTokenType.PAREN_CLOSE -> if (parenDepth > 0) parenDepth--
                SharedTokenType.BRACE_OPEN -> braceDepth++
                SharedTokenType.BRACE_CLOSE -> if (braceDepth > 0) braceDepth--
                SharedTokenType.ANGLE_OPEN -> angleDepth++
                SharedTokenType.ANGLE_CLOSE -> if (angleDepth > 0) angleDepth--
                else -> {}
            }
            val nextType = peekType()
            // Stop before consuming ';' when all delimiters are balanced (or at EOF).
            if (
                nextType == SharedTokenType.EOF ||
                    (nextType == SignatureTokenType.SEMICOLON &&
                        parenDepth == 0 &&
                        braceDepth == 0 &&
                        angleDepth == 0)
            ) {
                break
            }
            current = requireNonEofToken()
            endOffset = current.endOffset
        }

        return fileSubstring(startOffset, endOffset)
    }

    /**
     * Parses a comma-separated list of exception types in a `throws` clause up to (but not
     * consuming) the terminating `;`, returning their [TypeString]s.
     */
    private fun parseThrowsTypeStrings(): List<TypeString> {
        return buildList {
            // Tracks whether an exception type is expected next (true at the start of the list and
            // immediately after a comma).
            var comma = true
            while (true) {
                when (peekType()) {
                    SignatureTokenType.SEMICOLON -> {
                        // Leave the terminating ';' in the stream for the caller to consume.
                        break
                    }
                    SharedTokenType.COMMA -> {
                        val commaToken = consume()
                        if (comma) {
                            throw parseException("Expected exception, got ','", commaToken)
                        }
                        comma = true
                    }
                    else -> {
                        if (!comma) {
                            val unexpected = requireNonEofToken()
                            throw parseException(
                                "Expected ',' or ';' got ${text(unexpected)}",
                                unexpected,
                            )
                        }
                        comma = false
                        // Parse the qualified name of the thrown exception.
                        val startOffset = peek().startOffset
                        add(TypeString(parseQualifiedName(), startOffset))
                    }
                }
            }
        }
    }

    /**
     * Scans the token stream from [tokenStream] for a type string, ensuring that the full type
     * string is gathered, even when there are type-use annotations.
     *
     * Note: this **should not** be used when the token after the type could contain annotations,
     * such as when multiple types appear as consecutive tokens. (This happens in the `implements`
     * list of a class definition, e.g. `class Foo implements test.pkg.Bar test.pkg.@A Baz`.)
     *
     * @return the complete [TypeString].
     */
    private fun scanForTypeString(): TypeString {
        val firstToken = peek()
        if (firstToken.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", firstToken)
        }
        val startOffset = firstToken.startOffset

        // Consume the initial (possibly annotated) identifier segment of the type.
        var endOffset = skipAnnotatedIdentifier()

        // Continue consuming qualified segments (`.Foo`), type argument lists (`<...>`),
        // nullability/vararg suffixes (`?`, `!`, `...`), array dimensions (`[]`), and type-use
        // annotations on array dimensions (`Foo @A []`).
        while (true) {
            when (peekType()) {
                SharedTokenType.DOT -> {
                    consume()
                    endOffset = skipAnnotatedIdentifier()
                }
                SharedTokenType.ANGLE_OPEN -> {
                    endOffset = skipTypeArgumentList()
                }
                SharedTokenType.QUESTION,
                SharedTokenType.EXCLAMATION,
                SharedTokenType.ELLIPSIS -> {
                    endOffset = consume().endOffset
                }
                SharedTokenType.BRACKET_OPEN -> {
                    consume()
                    val closeBracket = requireNonEofToken()
                    if (closeBracket.type != SharedTokenType.BRACKET_CLOSE) {
                        throw parseException(
                            "expected ], was ${text(closeBracket)}",
                            closeBracket,
                        )
                    }
                    endOffset = closeBracket.endOffset
                }
                SharedTokenType.AT -> {
                    // Type-use annotation before an array dimension or varargs suffix (e.g.
                    // `Foo @A []`).
                    endOffset = skipAnnotation()
                }
                else -> break
            }
        }

        return TypeString(fileSubstring(startOffset, endOffset), startOffset)
    }

    /**
     * Parses an identifier token followed by a `:` token from [tokenStream], returning the
     * identifier text and throwing an [ApiParseException] if either is missing.
     */
    private fun parseNameWithColon(): String {
        val nameToken = requireNonEofToken()
        assertIdent(nameToken)
        return parseNameWithColon(text(nameToken))
    }

    /**
     * For Kotlin-style name/type ordering in signature files, the name is generally followed by a
     * colon (besides methods, where the colon comes after the parameter list). This method verifies
     * that a colon token follows [token] in [tokenStream] and consumes it, throwing an
     * [ApiParseException] if one isn't present.
     */
    private fun parseNameWithColon(token: String): String {
        if (!match(SharedTokenType.COLON)) {
            throw parseException(
                "Expecting name ending with \":\" but found $token.",
                peek(),
            )
        }
        return token
    }
}
