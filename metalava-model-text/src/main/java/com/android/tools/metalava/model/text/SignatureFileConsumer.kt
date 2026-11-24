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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.SourcePosition
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.type.TypeString
import java.nio.file.Path

/**
 * Consumer of events emitted by [ApiFile] and [SingleSignatureFileParser] while parsing one or more
 * signature files.
 *
 * Allows callers to either construct a full [com.android.tools.metalava.model.Codebase] (via
 * [SignatureFileCodebaseBuilder]) or process declarations directly from parsed syntactic data
 * without constructing model items.
 */
interface SignatureFileConsumer {
    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    val defaultTargetLanguageSet: Set<TargetLanguage>
        get() = TargetLanguageSet.ALL

    /**
     * Called before parsing each [signatureFile] at [path] with [lineMap] and [format].
     *
     * @param signatureFile the [SignatureFile] being parsed.
     * @param path the [Path] to [signatureFile].
     * @param lineMap the [LineMap] for mapping character offsets in [signatureFile] to line
     *   numbers.
     * @param format the [FileFormat] parsed from the header of [signatureFile].
     */
    fun startFile(
        signatureFile: SignatureFile,
        path: Path,
        lineMap: LineMap,
        format: FileFormat,
    )

    /** Called after finishing parsing the current signature file. */
    fun endFile() {}

    /** Called after all signature files have been parsed. */
    fun finish() {}

    /**
     * Parses sequential annotations from [tokenStream] (in [apiText]) and creates a
     * [MutableModifierList] with [visibilityLevel].
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * annotations.
     */
    fun parseModifierAnnotations(
        tokenStream: TokenStream,
        apiText: String,
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList

    /**
     * Called at the start of a `package` block at [sourcePosition] with [modifiers] and qualified
     * [name].
     */
    fun startPackage(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
    )

    /** Called at the end of the current `package` block. */
    fun endPackage() {}

    /**
     * Called before parsing a class, interface, enum, annotation, record, or typealias declaration
     * in the current package, before its modifiers and annotations are parsed.
     */
    fun beforeClass() {}

    /**
     * Called when a `typealias` declaration is parsed in the current package.
     *
     * @param sourcePosition the [SourcePosition] of the `typealias` keyword.
     * @param modifiers the modifiers and annotations on the type alias.
     * @param name the simple or outer-qualified name of the type alias within the current package.
     * @param typeParameters the parsed type parameters of the type alias.
     * @param aliasedTypeString the [TypeString] of the aliased type on the right-hand side of `=`.
     */
    fun consumeTypeAlias(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        aliasedTypeString: TypeString,
    )

    /**
     * Called at the start of a class, interface, enum, annotation, or record declaration in the
     * current package, after its header and any leading `record_component` declarations have been
     * parsed.
     *
     * @param sourcePosition the [SourcePosition] of the class kind keyword (`class`, `interface`,
     *   etc.).
     * @param modifiers the modifiers and annotations on the class.
     * @param targetLanguages the [TargetLanguage]s to which the class applies.
     * @param classKind the [ClassKind] of the declaration.
     * @param fullName the class name within the current package (including any `Outer.` prefix for
     *   nested classes).
     * @param typeParameters the parsed type parameters of the class.
     * @param superClassTypeString the [TypeString] from the `extends` clause for a non-interface
     *   class, or `null` if none was specified.
     * @param interfaceTypeStrings the [TypeString]s from the `implements` (or `extends` for an
     *   interface) clause.
     * @param permitTypeStrings the [TypeString]s from the `permits` clause.
     * @param recordComponents the parsed `record_component` declarations if [classKind] is
     *   [ClassKind.RECORD], or `null` otherwise.
     */
    fun startClass(
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
    )

    /** Called at the end of the current class declaration. */
    fun endClass() {}

    /**
     * Called before parsing a member (`ctor`, `enum_constant`, `field`, `method`, or `property`)
     * declaration in the current class, before its modifiers and annotations are parsed.
     */
    fun beforeMember() {}

    /**
     * Called when a `ctor` declaration is parsed in the current class.
     *
     * @param sourcePosition the [SourcePosition] of the constructor declaration.
     * @param modifiers the modifiers and annotations on the constructor.
     * @param targetLanguages the [TargetLanguage]s to which the constructor applies.
     * @param name the simple name of the constructor.
     * @param typeParameters the parsed type parameters of the constructor.
     * @param parameters the parsed parameters of the constructor.
     * @param throwsTypeStrings the [TypeString]s from the `throws` clause.
     */
    fun consumeConstructor(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
    )

    /**
     * Called when a `method` declaration is parsed in the current class.
     *
     * @param sourcePosition the [SourcePosition] of the method declaration.
     * @param modifiers the modifiers and annotations on the method.
     * @param targetLanguages the [TargetLanguage]s to which the method applies.
     * @param name the name of the method.
     * @param returnTypeString the [TypeString] of the method's return type.
     * @param typeParameters the parsed type parameters of the method.
     * @param parameters the parsed parameters of the method.
     * @param throwsTypeStrings the [TypeString]s from the `throws` clause.
     * @param defaultAnnotationMethodValue the raw `default` value expression for an annotation
     *   method, or `null` if none was specified.
     */
    fun consumeMethod(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        returnTypeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        parameters: List<ParsedParameter>,
        throwsTypeStrings: List<TypeString>,
        defaultAnnotationMethodValue: String?,
    )

    /**
     * Called when a `field` or `enum_constant` declaration is parsed in the current class.
     *
     * @param sourcePosition the [SourcePosition] of the field declaration.
     * @param modifiers the modifiers and annotations on the field.
     * @param targetLanguages the [TargetLanguage]s to which the field applies.
     * @param name the name of the field or enum constant.
     * @param typeString the [TypeString] of the field's type.
     * @param valueString the raw initial value expression following `=`, or `null` if none was
     *   specified.
     * @param isEnumConstant `true` for an `enum_constant` declaration, `false` for a `field`
     *   declaration.
     */
    fun consumeField(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        targetLanguages: Set<TargetLanguage>,
        name: String,
        typeString: TypeString,
        valueString: String?,
        isEnumConstant: Boolean,
    )

    /**
     * Called when a `property` declaration is parsed in the current class.
     *
     * @param sourcePosition the [SourcePosition] of the property declaration.
     * @param modifiers the modifiers and annotations on the property.
     * @param name the name of the property.
     * @param receiverTypeString the [TypeString] of the property's extension receiver, or `null` if
     *   none was specified.
     * @param typeString the [TypeString] of the property's type.
     * @param typeParameters the parsed type parameters of the property.
     * @param contextParameters the parsed context parameters of the property.
     */
    fun consumeProperty(
        sourcePosition: SourcePosition,
        modifiers: MutableModifierList,
        name: String,
        receiverTypeString: TypeString?,
        typeString: TypeString,
        typeParameters: List<ParsedTypeParameter>,
        contextParameters: List<ParsedParameter>,
    )
}
