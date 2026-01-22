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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
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

        // Resolve the source reference, if it failed then still return a non-null result to ensure
        // that whitespace is normalized consistently.
        val resolvedReference =
            resolveReference(context, reporter, sourceReference)?.also { resolved ->
                checkSourceReferenceValidForResolvedReference(reporter, sourceReference, resolved)
            }

        return ExtractDataResult(
            LabeledRefTagData(name, sourceReference, resolvedReference),
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

    /**
     * Resolve [sourceReference] (which may be a reference to a package, class, type parameter,
     * constructor, method, or field) to a [ResolvedReference], if possible.
     */
    private fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        sourceReference: String
    ): ResolvedReference? {
        // Parse the source reference matches the expected pattern.
        val parsedReference = parseReference(sourceReference)
        if (parsedReference == null) {
            reporter.report(
                Issues.MALFORMED_DOC_REFERENCE,
                "Malformed reference `$sourceReference`"
            )
            return null
        }

        // Resolve the reference.
        return parsedReference.resolveReference(context, reporter)
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
        internal fun parseReference(sourceReference: String): ParsedReference? {
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
                        MethodSourceReference(qualified!!, parameters = relative)
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
                        val parameters = relative.substring(index)

                        MethodSourceReference(methodName, parameters).qualifyIfNeeded(qualified)
                    }
                    relative.startsWith("##") -> {
                        UriFragmentSourceReference(relative).qualifyIfNeeded(qualified)
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
    }
}

/**
 * Represents a reference that was parsed from a source reference and which can be resolved within a
 * [ReferencableNameScope].
 */
internal sealed interface ParsedReference {
    /**
     * Resolve this [ParsedReference], if possible, within [context], reporting any issues to
     * [reporter].
     */
    fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? =
        // TODO(b/447588621): Remove default after implementing in all sub-classes.
        null
}

/** An ambiguous reference to something by [name]. */
internal data class AmbiguousSourceReference(val name: String) : ParsedReference {
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

        return member.findIn(classItem)
    }
}

/** A [ParsedReference] that qualifies a [member] reference to the current class. */
internal data class CurrentClassSourceReference(val member: ClassMemberSourceReference) :
    ParsedReference {
    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedReference? {
        // TODO(b/447588621): Report issue when no class is available, member references are not
        //  allowed in packages.
        val classItem = context.containingClassItem ?: return null
        return member.findIn(classItem)
    }
}

/**
 * A reference that is resolved relative to either [QualifyingClassSourceReference] or
 * [CurrentClassSourceReference].
 */
internal sealed interface ClassMemberSourceReference {
    /**
     * Will wrap this in a [QualifyingClassSourceReference] if [className] is not-null otherwise
     * will wrap this in [CurrentClassSourceReference].
     */
    fun qualifyIfNeeded(className: String?): ParsedReference =
        className?.let { QualifyingClassSourceReference(it, this) }
            ?: CurrentClassSourceReference(this)

    /** Find this in [classItem]. */
    fun findIn(classItem: ClassItem): ResolvedReference? =
        // TODO(b/447588621): Remove default after implementing in all sub-classes.
        null
}

/** A reference to a member called [name], which could be a field or a method. */
internal data class AmbiguousMemberSourceReference(val name: String) : ClassMemberSourceReference {
    override fun findIn(classItem: ClassItem): ResolvedReference? =
        classItem.findField(name)?.toResolvedReference()
}

/**
 * A reference to a method called [name] with [parameters]. This is both a
 * [ClassMemberSourceReference] because it can be resolved relative to a class, and
 * [ParsedReference] because it can be resolved within a [ReferencableNameScope].
 */
internal data class MethodSourceReference(val name: String, val parameters: String) :
    ClassMemberSourceReference, ParsedReference

/** A reference to a [uriFragment]. */
internal data class UriFragmentSourceReference(val uriFragment: String) :
    ClassMemberSourceReference

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
    val sourceReference: String,
    /** The resolved reference, subclasses identify the specific part of the API it references. */
    val resolvedReference: ResolvedReference?,
) : TagData {
    /** Check whether the [sourceReference] was fully resolved. */
    fun wasReferenceFullyResolved(): Boolean = resolvedReference != null

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
