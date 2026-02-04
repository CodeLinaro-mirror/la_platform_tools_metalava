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
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.source.doc.MethodSourceReference.SourceParameter
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
                .replace(SOME_WHITESPACE, " ")

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
        /** Regex that matches one or more whitespace characters. */
        private val SOME_WHITESPACE = Regex("""\s+""")

        /** A simple, unqualified, java name. */
        private const val SIMPLE = """(?:\p{javaJavaIdentifierStart}\p{javaJavaIdentifierPart}*)"""

        /**
         * A member name.
         *
         * An alias for [SIMPLE] to help clarify the meaning of [VALID_REFERENCE].
         */
        private const val MEMBER = SIMPLE

        /**
         * A method name.
         *
         * An alias for [SIMPLE] to help clarify the meaning of [VALID_REFERENCE].
         */
        private const val METHOD = SIMPLE

        /**
         * A qualified name.
         *
         * Can be one of:
         * * `<simple>`
         * * `<qualified>.<simple>`
         */
        private const val QUALIFIED = """(?:$SIMPLE(?:\.$SIMPLE)*)"""

        /** A list of parameters. */
        private const val PARAMETERS = """(?:\([^)]*\))"""

        /** A URI fragment, consists of most characters except `#` and white spaces. */
        private const val FRAGMENT = """[^ #]+"""

        /**
         * A valid reference.
         *
         * Can be one of:
         * * `<qualified>`
         * * `<qualified>(<parameters>)`
         * * `<qualified>#<member>`
         * * `<qualified>#<method>(<parameters>)`
         * * `<qualified>##<uri-fragment>`
         * * `#<member>`
         * * `#<method>(<parameters>)`
         * * `##<uri-fragment>`
         *
         * The following do not have their own pattern in the above list as they overlap with one of
         * the others:
         * * `<member>` - overlaps with `<qualified>`
         * * `<method>(<parameters>)` - overlaps with `<qualified>(<parameters>)`
         *
         * This can also match an empty string and `(<parameters>)` but they are excluded by the
         * [parseReference] method as that keeps the pattern as simple as possible.
         */
        private val VALID_REFERENCE =
            Regex("""($QUALIFIED)?(#$MEMBER|(?:#$METHOD)?$PARAMETERS|##$FRAGMENT)?""")

        /** The index of the qualified name group in [VALID_REFERENCE]. */
        private const val QUALIFIED_INDEX = 1

        /**
         * The index of the relative name group in [VALID_REFERENCE], i.e. anything that can come
         * after [QUALIFIED] in [VALID_REFERENCE].
         */
        private const val RELATIVE_INDEX = 2

        /** Parse [sourceReference], to a [ParsedReference], or `null` if it was not valid. */
        internal fun parseReference(
            sourceReference: String,
            docTypeParser: DocTypeParser
        ): ParsedReference? {
            // Check some edge cases that are not caught by the pattrern.
            if (sourceReference == "" || sourceReference[0] == '(') return null

            // Apply the pattern and extract the qualified and relative parts.
            val result = VALID_REFERENCE.matchEntire(sourceReference) ?: return null
            val qualified = result.groups[QUALIFIED_INDEX]?.value
            val relative = result.groups[RELATIVE_INDEX]?.value

            // Construct the appropriate [ParsedReference] instance, if possible.
            val parsedReference =
                when {
                    relative == null -> {
                        // qualified cannot be `null` as the only way for relative and qualified to
                        // be `null` is if sourceReference is empty but that is rejected above.
                        AmbiguousSourceReference(qualified!!)
                    }
                    relative[0] == '(' -> {
                        // qualified cannot be `null` as the only way for relative to start with `(`
                        // and qualified to be `null` is if sourceReference starts with '(' but that
                        // is rejected above.
                        val parameters = parseParameters(relative, docTypeParser)
                        MethodSourceReference(qualified!!, parameters)
                    }
                    relative.last() == ')' -> {
                        require(relative[0] == '#') {
                            // This should never happen as the pattern will only match a trailing
                            // ')' if there was a starting '('.
                            "internal error: inconsistency between reference pattern definition and use: relative '$relative' should have started with a #"
                        }
                        val index = relative.indexOf('(')
                        require(index != -1) {
                            // This should never happen as the pattern will only match a trailing
                            // ')' if there was a starting '('.
                            "internal error: inconsistency between reference pattern definition and use: relative '$relative' should have contained a ("
                        }

                        val methodName = relative.substring(1, index)
                        val parameters = parseParameters(relative.substring(index), docTypeParser)

                        MethodSourceReference(methodName, parameters).qualifyIfNeeded(qualified)
                    }
                    relative.startsWith("##") -> {
                        UriFragmentSourceReference(relative.substring(2)).qualifyIfNeeded(qualified)
                    }
                    relative[0] == '#' -> {
                        AmbiguousMemberSourceReference(relative.substring(1))
                            .qualifyIfNeeded(qualified)
                    }
                    else ->
                        error(
                            "internal error: could not handle qualified='$qualified' and relative='$relative'"
                        )
                }

            return parsedReference
        }

        /**
         * Parse [parametersWithParentheses] into a list of [SourceParameter] objects, separating
         * the parameter names and types.
         */
        private fun parseParameters(
            parametersWithParentheses: String,
            docTypeParser: DocTypeParser
        ): List<SourceParameter> {
            require(
                parametersWithParentheses.first() == '(' && parametersWithParentheses.last() == ')'
            ) {
                "internal error: parameters should start with `(` and end with `)` but was '$parametersWithParentheses'"
            }

            var startInclusive = 1
            return buildList {
                while (true) {
                    // Get the next parameter, if any. Exiting the loop if there was none.
                    val (parameter, endExclusive) =
                        parametersWithParentheses.nextParameter(startInclusive, docTypeParser)
                            ?: break

                    // Add the parameter to the list.
                    add(parameter)

                    // Move onto the next parameter.
                    startInclusive = endExclusive + 1
                }
            }
        }

        /**
         * Get the next parameter from this [String] starting from [startInclusive].
         *
         * If there is no next parameter then this returns `null`. Otherwise, it returns the
         * [SourceParameter] for it and the index of the character (',' or ')') immediately
         * following the parameter.
         */
        fun String.nextParameter(
            startInclusive: Int,
            docTypeParser: DocTypeParser
        ): Pair<SourceParameter, Int>? {
            var inTypeArgumentList = 0
            for (index in startInclusive until length) {
                val c = this[index]

                // Track whether inside a type argument list as a ',' inside that does not end the
                // parameter.
                if (c == '<') {
                    inTypeArgumentList += 1
                    continue
                } else if (c == '>') {
                    inTypeArgumentList -= 1
                    continue
                } else if (inTypeArgumentList > 0) {
                    continue
                }

                // Check for the end of the parameter.
                if (c == ',' || c == ')') {
                    // This is the end of the parameter.

                    // Trim any leading whitespace from the start of the parameter.
                    val parameterStartInclusive = skipForwardsOverLeadingWhitespace(startInclusive)
                    if (parameterStartInclusive == index) {
                        // There is no parameter.
                        return null
                    }

                    // Trim any trailing whitespace from the end.
                    val parameterEndExclusive = skipBackwardsOverTrailingWhitespace(index - 1) + 1

                    // See if the parameter ends with a name.
                    val nameStartInclusive =
                        skipBackwardsOverParameterName(
                            parameterEndExclusive - 1,
                            parameterStartInclusive
                        )

                    val parameter =
                        if (nameStartInclusive > parameterStartInclusive) {
                            val typeEndExclusive =
                                skipBackwardsOverTrailingWhitespace(nameStartInclusive - 1) + 1
                            val typeString = substring(parameterStartInclusive, typeEndExclusive)
                            val name = substring(nameStartInclusive, parameterEndExclusive)
                            val parsedType = docTypeParser.parse(typeString)
                            SourceParameter(parsedType, name)
                        } else {
                            val typeString =
                                substring(parameterStartInclusive, parameterEndExclusive)
                            val parsedType = docTypeParser.parse(typeString)
                            SourceParameter(parsedType)
                        }

                    return parameter to index
                }
            }

            return null
        }

        /**
         * Starting with the character at position [endInclusive] and searching backwards, return
         * the position of the beginning of a parameter name, or -1 if none could be found.
         */
        internal fun CharSequence.skipBackwardsOverParameterName(
            endInclusive: Int,
            toInclusive: Int
        ): Int {
            var end = endInclusive
            while (end >= toInclusive) {
                val c = this[end]
                // Skip back over anything that could be part of a parameter name.
                if (!c.isJavaIdentifierPart()) {
                    // Check to see if anything that looked like a parameter (i.e. a java identifier
                    // of length > 0) was found. If it was not then there is no parameter.
                    if (end == endInclusive) {
                        return -1
                    }

                    // An identifier was found at the end of the type which could be a parameter so
                    // check if it is.

                    // If it was preceded by something that is the end of an array or generic type
                    // then it must be a parameter.
                    if (c == ']' || c == '>') {
                        return end + 1
                    }

                    // If it is a whitespace then the identifier at the end of the parameter could
                    // be a parameter name but first check to make sure that it is not part of a
                    // qualified type name.
                    if (c.isWhitespace()) {
                        // Skip backwards over any whitespace.
                        val lastIndex = skipBackwardsOverTrailingWhitespace(end - 1)

                        // If the character is '.' then the identifier is part of a qualified type
                        // name, otherwise it is a parameter name.
                        if (this[lastIndex] != '.') {
                            return end + 1
                        }
                    }

                    // No parameter was found.
                    return -1
                }
                end -= 1
            }

            // Reached the beginning and no parameter was found.
            return -1
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
                is InvalidReferencableItem -> {
                    resolved.reportIssue(reporter)
                    return null
                }
                // This should never happen as passing in NameClassification.CLASS above should
                // limit
                // the returned types to ClassItem and InvalidReferencableItem.
                else -> error("type '$className' was resolved to an unknown type $resolved")
            }

        return member.findIn(context, reporter, classItem)
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
        val classItem = context.containingClassItem ?: return null
        return member.findIn(context, reporter, classItem)
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

    /** Find this in [classItem]. */
    fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem
    ): ResolvedReference? =
        // TODO(b/447588621): Remove default after implementing in all sub-classes.
        null
}

/** A reference to a member called [name], which could be a field or a method. */
internal data class AmbiguousMemberSourceReference(val name: String) : ClassMemberSourceReference {
    override val normalizedForm: String
        get() = name

    override fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem
    ): ResolvedReference? = classItem.findField(name)?.toResolvedReference()
}

/**
 * A reference to a method called [name] with [parameters]. This is both a
 * [ClassMemberSourceReference] because it can be resolved relative to a class, and
 * [ParsedReference] because it can be resolved within a [ReferencableNameScope].
 */
internal data class MethodSourceReference(val name: String, val parameters: List<SourceParameter>) :
    ClassMemberSourceReference, ParsedReference {

    override val normalizedForm: String
        get() =
            formatSignature(
                parameters,
                // Preserve generic arguments in the label part of this.
                eraseGenericArguments = false,
            )

    /**
     * Format [name] and [parameters] into a method signature for use in [normalizedForm] and
     * [MethodReference.signature].
     */
    private fun formatSignature(
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
     * Resolve [name] reference to a method directly within [context].
     *
     * This differs from [findIn] as that finds a method within a class that has already been
     * resolved but this resolves the name directly within the containing scope. The key difference
     * is that the former uses `#` to unambiguously separate the class from the method but the
     * latter does not.
     *
     * e.g. [findIn] is used for references like `{@link #method()}` and {@link Class#method()}`
     * while this is used for references like `{@link method()}` and `{@link Class.method()}`.
     */
    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? {
        val resolved = context.resolveItemReference(name, NameClassification.METHOD_SET)
        return when (resolved) {
            is ReferencableMethodSet ->
                // TODO(b/447588621): Try and find a method that matches the resolved [parameters].
                createMethodReference(context, reporter, resolved.containingClass)
            // Report an error and return `null`.
            is InvalidReferencableItem -> {
                resolved.reportIssue(reporter)
                null
            }
            // This should never happen as passing in NameClassification.METHOD above should limit
            // the returned types to MethodItemSet or InvalidReferencableItem.
            else -> error("method name '$name' was resolved to an unknown type $resolved")
        }
    }

    override fun findIn(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem
    ) = createMethodReference(context, reporter, classItem)

    /**
     * Return a method reference that uses the fully qualified name of the containing class and
     * fully qualified parameter types.
     */
    private fun createMethodReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        classItem: ClassItem
    ) =
        MethodReference(
            classItem.qualifiedName(),
            formatSignature(
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
     * Check whether the references could possibly rely on the [importedName].
     *
     * Returns `true` if the reference has not been fully resolved and the partially resolved parts
     * contain [importedName] as a separate word.
     */
    fun referenceCouldRelyOnImportedName(importedName: String) =
        resolvedReference?.referenceCouldRelyOnImportedName(importedName)
            ?: sourceReference.containsWord(importedName)

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

        // Do not add custom labels to @see tags. This matches the behavior of the Psi reference
        // resolution code and keeping them consistent simplifies migration to this reference
        // resolving code.
        // TODO(b/447588621): Remove once this replaces the Psi reference resolving code completely.
        if (tagType == "see") return

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
