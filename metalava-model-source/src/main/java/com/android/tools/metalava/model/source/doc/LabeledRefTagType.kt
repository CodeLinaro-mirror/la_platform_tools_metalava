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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.BaseTypeTransformer
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExternalReferencableItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.TypeComparator
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.source.doc.CallableSourceReference.SourceParameter
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.LocationSpecificReporter

/** [TagType] for labeled reference tags, e.g. `@link` and `@linkplain` inline tags. */
internal open class LabeledRefTagType(name: String, form: TagTypeForm) :
    TagType<LabeledRefTagData>(name, form) {
    /** Link tags can only contain text */
    override val containsTextOnly: Boolean
        get() = true

    /** Override to extract the source reference from the tag content. */
    override fun extractData(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        text: CharSequence
    ): ExtractDataResult<LabeledRefTagData>? {
        val referenceStart = text.skipForwardsOverLeadingWhitespace(0)
        val referenceEndExclusive = text.findEndOfReference(referenceStart)
        if (referenceEndExclusive == 0) return null

        // Find the start of the label.
        val labelStart = text.skipForwardsOverLeadingWhitespace(referenceEndExclusive)

        // Get the source reference from the text.
        val sourceReference =
            text
                .substring(referenceStart, referenceEndExclusive)
                // Normalize whitespace by replacing blocks of whitespace with a single space.
                // Ensures consistent formatting irrespective of how it was formatted in the source.
                .collapseSpaces()

        // Parse the source reference, reporting an error if it could not be done.
        val parsedReference = parseReference(sourceReference, context.docTypeParser)
        if (parsedReference == null) {
            reporter.report(
                Issues.MALFORMED_DOC_REFERENCE,
                "Malformed reference `$sourceReference`"
            )
        }

        // Resolve the parsed source reference, if available.
        val resolvedReference =
            // Resolve the reference.
            parsedReference?.resolveReference(context, reporter)?.also { resolved ->
                checkSourceReferenceValidForResolvedReference(reporter, sourceReference, resolved)
            }

        // Get a normalized form of the source reference for use as the label if the resolved
        // reference is different.
        val normalizedSourceReference = parsedReference?.normalizedForm ?: sourceReference

        return ExtractDataResult(
            LabeledRefTagData(name, normalizedSourceReference, resolvedReference),
            // The source reference and any following whitespace must be removed from the content as
            // they are part of [LinkTagData].
            consumedContent = labelStart
        )
    }

    /**
     * Check to make sure that the [sourceReference] is valid for [resolved].
     *
     * Resolving can handle references which are not valid, e.g. a qualified field reference without
     * a #. Make sure that the source reference is a valid form for the resolved item.
     */
    private fun checkSourceReferenceValidForResolvedReference(
        reporter: LocationSpecificReporter,
        sourceReference: String,
        resolved: ResolvedReference
    ) {
        when (resolved) {
            is FieldReference -> {
                // Check if the source reference was qualified.
                val lastDotIndex = sourceReference.lastIndexOf('.')
                if (lastDotIndex > -1) {
                    // The source reference was qualified so must have a '#'
                    val hashIndex = sourceReference.indexOf('#', lastDotIndex)
                    if (hashIndex == -1) {
                        reporter.report(
                            Issues.MALFORMED_DOC_REFERENCE,
                            "Malformed reference `$sourceReference`, missing '#', should be '${
                                sourceReference.replaceRange(
                                    lastDotIndex,
                                    lastDotIndex + 1,
                                    "#"
                                )
                            }"
                        )
                    }
                }
            }
            else -> {}
        }
    }

    companion object {
        /**
         * Parse [sourceReference] into a [ParsedReference], or `null` if it was not valid.
         *
         * Valid reference forms:
         * * `<qualified>`
         * * `<qualified>(<parameters>)`
         * * `<qualified>#<member>`
         * * `<qualified>#<method>(<parameters>)`
         * * `<qualified>##<uri-fragment>`
         * * `#<member>`
         * * `#<method>(<parameters>)`
         * * `##<uri-fragment>`
         *
         * The following do not have their own form in the above list as they overlap with one of
         * the others:
         * * `<member>` - overlaps with `<qualified>`
         * * `<method>(<parameters>)` - overlaps with `<qualified>(<parameters>)`
         *
         * Outside `(<parameters>)`, all tokens must be contiguous with no intervening whitespace.
         */
        internal fun parseReference(
            sourceReference: String,
            docTypeParser: DocTypeParser,
        ): ParsedReference? {
            if (sourceReference.isEmpty() || sourceReference[0].isWhitespace()) return null

            val tokens = DocRefLexer(sourceReference).tokenize()

            // Parse optional leading `<qualified>` (`<simple>('.' <simple>)*`).
            val qualified: String?
            var expectedOffset = 0
            if (tokens.peekType().canBeIdentifier) {
                val firstToken = tokens.consume()
                expectedOffset = firstToken.endOffset
                while (
                    tokens.peekType() == SharedTokenType.DOT &&
                        tokens.peek().startOffset == expectedOffset
                ) {
                    val dotToken = tokens.consume()
                    expectedOffset = dotToken.endOffset
                    val segmentToken = tokens.peek()
                    if (
                        !segmentToken.type.canBeIdentifier ||
                            segmentToken.startOffset != expectedOffset
                    ) {
                        return null
                    }
                    tokens.consume()
                    expectedOffset = segmentToken.endOffset
                }
                qualified = sourceReference.substring(firstToken.startOffset, expectedOffset)
            } else {
                qualified = null
            }

            // Parse the relative suffix (if any) immediately following `<qualified>`.
            val next = tokens.peek()
            if (next.startOffset != expectedOffset) return null

            return when (next.type) {
                SharedTokenType.EOF -> {
                    AmbiguousSourceReference(qualified ?: return null)
                }
                SharedTokenType.PAREN_OPEN -> {
                    if (qualified == null) return null
                    val parameters =
                        parseParameters(tokens, sourceReference, docTypeParser) ?: return null
                    CallableSourceReference(qualified, parameters)
                }
                DocRefTokenType.HASH -> {
                    val hashToken = tokens.consume()
                    val memberToken = tokens.peek()
                    if (
                        !memberToken.type.canBeIdentifier ||
                            memberToken.startOffset != hashToken.endOffset
                    ) {
                        return null
                    }
                    tokens.consume()
                    val memberName = memberToken.text(sourceReference)

                    val afterMember = tokens.peek()
                    if (afterMember.startOffset != memberToken.endOffset) return null

                    when (afterMember.type) {
                        SharedTokenType.EOF -> {
                            AmbiguousMemberSourceReference(memberName).qualifyIfNeeded(qualified)
                        }
                        SharedTokenType.PAREN_OPEN -> {
                            val parameters =
                                parseParameters(tokens, sourceReference, docTypeParser)
                                    ?: return null
                            CallableSourceReference(memberName, parameters)
                                .qualifyIfNeeded(qualified)
                        }
                        else -> null
                    }
                }
                DocRefTokenType.URI_FRAGMENT -> {
                    val fragmentToken = tokens.consume()
                    val fragmentStart = fragmentToken.startOffset + 2
                    if (
                        fragmentToken.endOffset <= fragmentStart ||
                            tokens.peekType() != SharedTokenType.EOF ||
                            fragmentToken.endOffset != sourceReference.length
                    ) {
                        return null
                    }
                    val fragment = sourceReference.substring(fragmentStart, fragmentToken.endOffset)
                    UriFragmentSourceReference(fragment).qualifyIfNeeded(qualified)
                }
                else -> null
            }
        }

        /**
         * Parse a parenthesized parameter list from [tokens] into a list of [SourceParameter]
         * objects, separating the parameter names and types, or `null` if the parameter list is
         * malformed or not followed immediately by the end of [sourceText].
         */
        private fun parseParameters(
            tokens: TokenStream,
            sourceText: String,
            docTypeParser: DocTypeParser,
        ): List<SourceParameter>? {
            if (tokens.peekType() != SharedTokenType.PAREN_OPEN) return null
            tokens.consume()

            if (tokens.peekType() == SharedTokenType.PAREN_CLOSE) {
                val closeParen = tokens.consume()
                if (
                    tokens.peekType() != SharedTokenType.EOF ||
                        closeParen.endOffset != sourceText.length
                ) {
                    return null
                }
                return emptyList()
            }

            val parameters = buildList {
                while (true) {
                    val nextType = tokens.peekType()
                    if (
                        nextType == SharedTokenType.PAREN_CLOSE ||
                            nextType == SharedTokenType.COMMA ||
                            nextType == SharedTokenType.EOF
                    ) {
                        return null
                    }

                    val parsedType = docTypeParser.parseFromStream(tokens, sourceText)
                    val name =
                        if (tokens.peekType().canBeIdentifier) {
                            tokens.consume().text(sourceText)
                        } else {
                            null
                        }
                    add(SourceParameter(parsedType, name))

                    if (tokens.peekType() == SharedTokenType.COMMA) {
                        tokens.consume()
                    } else {
                        break
                    }
                }
            }

            if (tokens.peekType() != SharedTokenType.PAREN_CLOSE) return null
            val closeParen = tokens.consume()
            if (
                tokens.peekType() != SharedTokenType.EOF ||
                    closeParen.endOffset != sourceText.length
            ) {
                return null
            }
            return parameters
        }
    }
}

/**
 * Represents a reference that was parsed from a source reference and which can be resolved within a
 * [ReferencableNameScope].
 */
internal sealed interface ParsedReference {
    /**
     * Get the normalized form of this reference, including any '#' separator between a qualifying
     * class and a [ClassMemberSourceReference].
     */
    val normalizedForm: String

    /**
     * Resolve this [ParsedReference], if possible, within [context], reporting any issues to
     * [reporter].
     */
    fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference?
}

/** An ambiguous reference to something by [name]. */
internal data class AmbiguousSourceReference(val name: String) : ParsedReference {
    override val normalizedForm: String
        get() = name

    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? =
        // Resolve the reference.
        when (val resolved = context.resolveItemReference(name, NameClassification.AMBIGUOUS)) {
            is ClassItem -> resolved.toResolvedReference()
            is PackageItem -> resolved.toResolvedReference()
            is TypeParameterItem -> resolved.toResolvedReference()
            is FieldItem -> resolved.toResolvedReference()
            else -> null
        }
}

/** A [ParsedReference] that qualifies a [member] reference by [className]. */
internal data class QualifyingClassSourceReference(
    val className: String,
    val member: ClassMemberSourceReference
) : ParsedReference {
    override val normalizedForm: String
        get() = "$className#${member.normalizedForm}"

    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? {
        val resolved = context.resolveItemReference(className, NameClassification.CLASS)
        val classItem =
            when (resolved) {
                is ClassItem -> resolved
                is ExternalReferencableItem -> null
                is InvalidReferencableItem -> {
                    resolved.reportIssue(reporter)
                    null
                }
                // This should never happen as passing in NameClassification.CLASS above should
                // limit the returned types to ClassItem, ExternalReferencableItem, and
                // InvalidReferencableItem.
                else -> error("type '$className' was resolved to an unknown type $resolved")
            }

        return member.findIn(
            context,
            reporter,
            classItem,
            // Use the qualified class name if available, otherwise use the source name.
            classItem?.qualifiedName() ?: className,
        )
    }
}

/** A [ParsedReference] that qualifies a [member] reference to the current class. */
internal data class CurrentClassSourceReference(val member: ClassMemberSourceReference) :
    ParsedReference {
    override val normalizedForm: String
        get() = "#${member.normalizedForm}"

    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? {
        // TODO(b/447588621): Report issue when no class is available, member references are not
        //  allowed in packages.
        val classItem = context.containingClassItem
        return member.findIn(context, reporter, classItem, classItem?.qualifiedName() ?: "")
    }
}

/**
 * A reference that is resolved relative to either [QualifyingClassSourceReference] or
 * [CurrentClassSourceReference].
 */
internal sealed interface ClassMemberSourceReference {
    /**
     * Get the normalized form of this reference, not including the '#' separator from the
     * qualifying class.
     */
    val normalizedForm: String

    /**
     * Will wrap this in a [QualifyingClassSourceReference] if [className] is not-null otherwise
     * will wrap this in [CurrentClassSourceReference].
     */
    fun qualifyIfNeeded(className: String?): ParsedReference =
        className?.let { QualifyingClassSourceReference(it, this) }
            ?: CurrentClassSourceReference(this)

    /**
     * Find the class member that this is referencing in [classItem], if provided.
     *
     * If the qualifying class could be resolved then [classItem] will be provided and [className]
     * will be the fully qualified class name. However, if the qualifying class could not be
     * resolved then [classItem] will be `null` and [className] will be the name provided in the
     * source.
     */
    fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem?,
        className: String,
    ): ResolvedReference
}

/** A reference to a member called [name], which could be a field or a callable. */
internal data class AmbiguousMemberSourceReference(val name: String) : ClassMemberSourceReference {
    override val normalizedForm: String
        get() = name

    override fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem?,
        className: String,
    ) =
        // TODO(b/447588621): Check for methods and constructors not just fields.
        classItem?.findField(name)?.toResolvedReference()
            // TODO(b/447588621): Report that the field could not be found.
            // If the field could not be found then fallback to a field reference so that at least
            // the resolved class will be fully qualified.
            ?: FieldReference(className, name)
}

/**
 * A reference to a callable called [name] with [parameters]. This is both a
 * [ClassMemberSourceReference] because it can be resolved relative to a class, and
 * [ParsedReference] because it can be resolved within a [ReferencableNameScope].
 */
internal data class CallableSourceReference(
    val name: String,
    val parameters: List<SourceParameter>
) : ClassMemberSourceReference, ParsedReference {

    override val normalizedForm: String
        get() =
            formatSignature(
                name,
                parameters,
                // Preserve generic arguments in the label part of this.
                eraseGenericArguments = false,
            )

    /**
     * Format [name] and [parameters] into a callable signature for use in [normalizedForm] and
     * [CallableReference.signature].
     */
    private fun formatSignature(
        name: String,
        parameters: List<SourceParameter>,
        eraseGenericArguments: Boolean,
    ) = buildString {
        val typeStringConfiguration =
            if (eraseGenericArguments) ERASE_GENERICS_TYPE_STRING_CONFIGURATION
            else TypeStringConfiguration.DEFAULT
        append(name)
        append('(')
        parameters.joinTo(this, ",") { it.type.toTypeString(typeStringConfiguration) }
        append(')')
    }

    /**
     * Resolve [name] reference to a callable directly within [context].
     *
     * This differs from [findIn] as that finds a callable within a class that has already been
     * resolved but this resolves the name directly within the containing scope. The key difference
     * is that the former uses `#` to unambiguously separate the class from the callable but the
     * latter does not.
     *
     * e.g. [findIn] is used for references like `{@link #method()}` and {@link Class#method()}`
     * while this is used for references like `{@link method()}` and `{@link Class.method()}`.
     */
    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? {
        val resolved = context.resolveItemReference(name, NameClassification.CALLABLE_SET)
        return when (resolved) {
            is ReferencableMethodSet -> {
                // TODO(b/447588621): Try and find a callable that matches the resolved
                //  [parameters].
                val containingClass = resolved.containingClass
                createCallableReference(
                    context,
                    reporter,
                    containingClass.qualifiedName(),
                    // Use the resolved name as the source name may be qualified.
                    resolved.name,
                )
            }
            is ClassItem -> {
                // Resolving a callable can return the class in lieu of a set of constructor so
                // treat it as one and create a reference to the constructor.
                createCallableReference(
                    context,
                    reporter,
                    resolved.qualifiedName(),
                    // Use the class's simple name as the constructor name.
                    resolved.simpleName(),
                )
            }
            is ExternalReferencableItem -> null
            // Report an error and return `null`.
            is InvalidReferencableItem -> {
                resolved.reportIssue(reporter)
                null
            }
            // This should never happen as passing in NameClassification.CALLABLE_SET above should
            // limit the returned types to ReferencableMethodSet, ClassItem,
            // ExternalReferencableItem, or InvalidReferencableItem.
            else -> error("callable name '$name' was resolved to an unknown type $resolved")
        }
    }

    override fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem?,
        className: String,
    ) =
        createCallableReference(
            context,
            reporter,
            className,
            // The source name will always be the simple callable name in this case, it will never
            // be qualified so it is safe to use in the callable reference.
            name,
        )

    /**
     * Return a [CallableReference] that uses the fully qualified name of the containing class and
     * fully qualified parameter types.
     */
    private fun createCallableReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        className: String,
        name: String,
    ) =
        CallableReference(
            className,
            formatSignature(
                name,
                parameters.map { sourceParameter ->
                    val fullyQualifiedType = sourceParameter.type.fullyQualify(context, reporter)
                    SourceParameter(fullyQualifiedType, sourceParameter.name)
                },
                // Erase generic arguments from the reference part of this.
                eraseGenericArguments = true,
            )
        )

    data class SourceParameter(
        val type: TypeItem,
        val name: String? = null,
    ) {
        override fun toString() = if (name == null) type.toString() else "$name: $type"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SourceParameter) return false
            return name == other.name && TypeComparator.IGNORE_NULLABILITY.compare(type, other.type)
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + TypeComparator.IGNORE_NULLABILITY.hash(type)
            return result
        }
    }

    companion object {
        /** Configuration used in [formatSignature] when `eraseGenericArguments` is `true`. */
        private val ERASE_GENERICS_TYPE_STRING_CONFIGURATION =
            TypeStringConfiguration.DEFAULT.copy(eraseGenerics = true)
    }
}

/** Fully qualify this [TypeItem] within [context], reporting any issues to [reporter]. */
private fun TypeItem.fullyQualify(context: DocCommentContext, reporter: LocationSpecificReporter) =
    transform(
        object : BaseTypeTransformer() {
            override fun transform(typeItem: ClassTypeItem): ClassTypeItem {
                // Resolve the qualified name (which may in fact be a simple name, or a partially
                // qualified name) as a class.
                val resolved =
                    context.resolveItemReference(typeItem.qualifiedName, NameClassification.CLASS)

                val resolvedType =
                    when (resolved) {
                        is ClassItem ->
                            // Use the resolved class's type.
                            resolved.type()
                        else -> {
                            // Report any issues with resolving the class.
                            if (resolved is InvalidReferencableItem) {
                                resolved.reportIssue(reporter)
                            }

                            // Default to just using this type
                            typeItem
                        }
                    }

                // Fully qualify the type arguments, reporting any issues with class references.
                val fullyQualifiedTypeArguments = typeItem.arguments.map { it.transform(this) }

                // Use the resolved type with the fully qualified type arguments provided in the
                // source.
                return resolvedType.substitute(arguments = fullyQualifiedTypeArguments)
            }
        }
    )

/** A reference to a [uriFragment]. */
internal data class UriFragmentSourceReference(val uriFragment: String) :
    ClassMemberSourceReference {
    /**
     * The normalized form of this includes a leading `#`. Coupled with the `#` added by the
     * containing [QualifyingClassSourceReference] or [CurrentClassSourceReference] that gives the
     * double `##` that identifies the reference as a URI fragment.
     */
    override val normalizedForm: String
        get() = "#$uriFragment"

    override fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem?,
        className: String,
    ) =
        // TODO(b/447588621): If the source for classItem is available then check it for an
        //  id="<uriFragment>" attribute.
        UriFragmentReference(className, uriFragment)
}

/**
 * Find the end of the reference.
 *
 * A reference can contain whitespace but only within parentheses. The parentheses must be balanced,
 * i.e. for every `(` have a corresponding `)`.
 *
 * @param startInclusive the start of the reference, must be non-whitespace otherwise this will fail
 *   to find a reference.
 */
internal fun CharSequence.findEndOfReference(startInclusive: Int): Int {
    require(!this[startInclusive].isWhitespace()) {
        "startInclusive must not point to a whitespace character"
    }
    // Keep track of the parenthesis nesting level. This should not really be necessary as the only
    // way to have multiple levels of parentheses is to have Kotlin lambda types which are not
    // supported in Java. However, this is a simple way to track it.
    var nesting = 0

    // Scan forward trying to find the end of the reference.
    for (index in startInclusive until length) {
        val c = this[index]
        when {
            c == '(' -> {
                nesting += 1
            }
            c == ')' -> {
                // Increase the nesting level.
                nesting -= 1
            }
            // If whitespace is encountered then stop only if outside parentheses.
            c.isWhitespace() -> {
                if (nesting == 0) return index
            }
        }
    }

    // TODO(b/456188750): Report issues with unbalanced parentheses.

    return length
}

/**
 * Encapsulates information about a labeled reference tag, e.g. `@link` and `@linkplain` inline tags
 * and `@see` block tag.
 */
internal data class LabeledRefTagData(
    /** The tag type for which this was created. */
    private val tagType: String,
    /** The reference from the source; used as the label if necessary. */
    private val sourceReference: String,
    /** The resolved reference, subclasses identify the specific part of the API it references. */
    private val resolvedReference: ResolvedReference?,
) : TagData {
    /**
     * Print the tag contents which consists of the [sourceReference] and the [content] which is the
     * optional label.
     *
     * If the [resolvedReference] is different to the [sourceReference] and [content] is `null` then
     * this will use the [sourceReference] as the label.
     */
    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        writer.print(" ")
        val formattedReference =
            resolvedReference?.formatForTagReference(contentPrinter.containingClassName)
                ?: sourceReference

        writer.print(formattedReference)

        // The content is the label of the link tag, print it if it exists.
        if (content != null) {
            // Print the remaining content. Always preceded by a space as any leading whitespace has
            // been trimmed from it.
            content.printWithLeadingSpaceTo(contentPrinter)

            // Return immediately.
            return
        }

        // Check to see whether it is necessary to add a label to try and preserve the developer's
        // original intent.

        // If the formatted reference is the same as the source reference then there is no point in
        // duplicating the source reference as the label. This will also be the case if resolved
        // reference is `null`. It is explicitly checked here to allow the remaining code to take
        // advantage of smart casting.
        if (formattedReference == sourceReference || resolvedReference == null) {
            return
        }

        // If the fully qualified reference is the same as the source reference then there is no
        // point in duplicating the source reference as the label. That is because if the formatted
        // version is not the same as the source reference (checked above) but the fully qualified
        // form is the same as the source reference then the formatted reference must be a shortened
        // form of the source reference. The shortening rules implemented here are those mandated by
        // Javadoc when determining how to display absolute references so the shortened form and the
        // fully qualified form will have identical representation in the final documentation.
        // e.g. if the source reference is `test.pkg.Class#FIELD` and it is in the `test.pkg.Class`
        // then `{@link test.pkg.Class#FIELD}` and `{@link #FIELD}` are identical and will behave as
        // `{@link test.pkg.Class#FIELD FIELD}`. Using the shorter version saves space and matches
        // the legacy behavior of the Psi specific qualification process so reduces insignificant
        // differences in the generated documentation making it easier to see any significant
        // differences.
        if (resolvedReference.fullyQualifiedForm == sourceReference) {
            return
        }

        // If the source reference and formatted reference would evaluate to the same label then
        // there is no point in using source reference as the label. This is needed as multiple
        // references can map to the same label, e.g. `#field` and `field` both map to a label of
        // `field`.
        val sourceReferenceAsLabel = sourceReference.referenceAsLabel()
        val formattedReferenceAsLabel = formattedReference.referenceAsLabel()
        if (formattedReferenceAsLabel == sourceReferenceAsLabel) {
            return
        }

        // Use the source reference as the label.
        writer.print(" ")
        writer.print(sourceReferenceAsLabel)
    }

    /**
     * Convert a doc reference of the form `<type>?#<name>?(...)?` to a label.
     *
     * That involves:
     * * If it does not contain a `#` then just use the reference directly.
     * * If it starts with a `#` then remove it.
     * * Otherwise, replace the first '#' with a `.`.
     */
    fun String.referenceAsLabel(): String {
        val hashIndex = indexOf('#')
        if (hashIndex + 1 < length && this[hashIndex + 1] == '#') {
            return substring(hashIndex + 1)
        }
        return when (hashIndex) {
            -1 -> this
            0 -> substring(1)
            else -> "${substring(0, hashIndex)}.${substring(hashIndex + 1)}"
        }
    }

    /**
     * Make sure that the [sourceReference] is searchable just like it would be if it was part of
     * the content.
     */
    override fun textMatches(predicate: (String) -> Boolean) = predicate(sourceReference)

    override fun toString() =
        "LabeledRefTagData(sourceReference=$sourceReference, resolvedReference=$resolvedReference)"
}
