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
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ExternalReferencableItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.InvalidReferencableItem
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ReferencableMethodSet
import com.android.tools.metalava.model.TypeComparator
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.api.surface.ReferenceCheckResult
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.scope.NameClassification
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.source.doc.CallableSourceReference.SourceParameter
import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocInlineTag
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

        // KDoc uses `[...]` links rather than Javadoc reference tags, and Kotlin stubs are not
        // supported, so leave labeled reference tags in Kotlin comments unresolved.
        if (context.isKotlin) {
            return ExtractDataResult(
                LabeledRefTagData(name, sourceReference, null),
                consumedContent = labelStart,
            )
        }

        // Parse the source reference, reporting an error if it could not be done.
        val parsedReference = parseReference(sourceReference, context.docTypeParser)
        if (parsedReference == null) {
            reporter.report(
                Issues.MALFORMED_DOC_REFERENCE,
                malformedReferenceMessage(sourceReference),
            )
        }

        // Resolve the parsed source reference, if available, including any `{@if (flag(...))}`
        // scope covering `[referenceStart, referenceEndExclusive)`.
        val referenceContext = context.forTextRange(text, referenceStart, referenceEndExclusive)
        val resolved =
            // Resolve the reference.
            parsedReference?.resolveReference(referenceContext, reporter)?.also {
                checkSourceReferenceValidForResolvedReference(
                    reporter,
                    sourceReference,
                    it.resolvedReference,
                )
            }

        // Get a normalized form of the source reference for use as the label if the resolved
        // reference is different.
        val normalizedSourceReference =
            resolved?.normalizedSourceReference
                ?: parsedReference?.normalizedForm
                ?: sourceReference

        return ExtractDataResult(
            LabeledRefTagData(
                name,
                normalizedSourceReference,
                resolved?.resolvedReference,
                rawSourceReference = sourceReference,
            ),

            // The source reference and any following whitespace must be removed from the content as
            // they are part of [LinkTagData].
            consumedContent = labelStart,
        )
    }

    /**
     * Build the [Issues.MALFORMED_DOC_REFERENCE] message for an unparseable [sourceReference],
     * providing a `<a href="...">` hint when [sourceReference] is a URL.
     */
    private fun malformedReferenceMessage(sourceReference: String): String {
        if (!sourceReference.isUrlReference()) {
            return "Malformed reference `$sourceReference`"
        }
        val replacement =
            if (form == TagTypeForm.BLOCK) {
                "`@$name <a href=\"$sourceReference\">...</a>`"
            } else {
                "`<a href=\"$sourceReference\">...</a>`"
            }
        return "Malformed reference `$sourceReference`, URLs are not valid in `@$name`, use $replacement instead"
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
         * Return `true` if this [String] starts with an RFC 3986 URI scheme followed by `://` (such
         * as `http://` or `https://`).
         */
        private fun String.isUrlReference(): Boolean {
            val schemeEnd = indexOf("://")
            if (schemeEnd <= 0 || !this[0].isLetter()) return false
            for (i in 1 until schemeEnd) {
                val c = this[i]
                if (!c.isLetterOrDigit() && c != '+' && c != '-' && c != '.') return false
            }
            return true
        }

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
    ): ResolvedSourceReference?
}

/**
 * Result of resolving a [ParsedReference]: pairs the [resolvedReference] with the
 * [normalizedSourceReference] to use when synthesizing a fallback label (with any fully qualified
 * mapped annotation name rewritten so the hidden source package is not leaked into the label).
 */
internal data class ResolvedSourceReference(
    val normalizedSourceReference: String,
    val resolvedReference: ResolvedReference,
)

/**
 * Rewrite [sourceClassName] to [resolvedClassName] when the author already wrote the full original
 * qualified name of [classItem], so mapped annotations (e.g. `android.annotation.Nullable` ->
 * `androidx.annotation.Nullable`) do not synthesize a fallback label containing the unmapped name.
 */
private fun rewriteMappedSourceClassName(
    sourceClassName: String,
    classItem: ClassItem,
    resolvedClassName: String,
): String = if (sourceClassName == classItem.qualifiedName()) resolvedClassName else sourceClassName

/**
 * Rewrite [sourceCallableName] from `<unmappedClass>.[callableName]` to
 * `[resolvedClassName].[callableName]` when the author wrote the full original qualified name of
 * [containingClass] (e.g. `android.annotation.IntDef.flag` -> `androidx.annotation.IntDef.flag`),
 * so mapped annotations do not synthesize a fallback label containing the unmapped name.
 */
private fun rewriteMappedSourceCallableName(
    sourceCallableName: String,
    containingClass: ClassItem,
    callableName: String,
    resolvedClassName: String,
): String {
    val unmappedQualifiedCallable = "${containingClass.qualifiedName()}.$callableName"
    return if (sourceCallableName == unmappedQualifiedCallable) {
        "$resolvedClassName.$callableName"
    } else {
        sourceCallableName
    }
}

/**
 * A no-op [LocationSpecificReporter] used to probe candidate callable overloads via
 * [DocCommentContext.checkReference] without emitting duplicate diagnostics.
 */
private val NOOP_LOCATION_REPORTER =
    object : LocationSpecificReporter {
        override fun report(issue: Issues.Issue, message: String) {}
    }

/**
 * Collect implicit `java.lang.Object` methods named [methodName] for types whose
 * [ClassItem.superClass] is `null` (such as interfaces and annotation types), excluding
 * [ClassItem.isJavaLangObject], [ClassKind.TYPEALIAS], and [ClassItem.isFileFacade].
 *
 * Per JLS 9.2, interfaces and annotation types only implicitly declare `public` non-`static`
 * methods of `java.lang.Object`.
 */
private fun ClassItem.findImplicitObjectMethodsByName(methodName: String): List<MethodItem> {
    if (
        superClass() != null ||
            isJavaLangObject() ||
            classKind == ClassKind.TYPEALIAS ||
            isFileFacade
    ) {
        return emptyList()
    }
    val publicInstanceOnly = isInterface() || isAnnotationType()
    val objectClass = codebase.resolveClass(JAVA_LANG_OBJECT) ?: return emptyList()
    return objectClass.methods().filter { method ->
        method.name() == methodName &&
            (!publicInstanceOnly || (method.modifiers.isPublic() && !method.modifiers.isStatic()))
    }
}

/**
 * Collect all methods named [methodName] declared on this [ClassItem] or inherited from its
 * superclasses, superinterfaces (excluding `static` methods on strict superinterfaces per JLS 8.4.8
 * / 9.4.1), or `java.lang.Object` (for interfaces and annotation types, whose
 * [ClassItem.superClass] is `null`).
 */
private fun ClassItem.findAllMethodsByName(methodName: String): List<MethodItem> = buildList {
    val visited = HashSet<ClassItem>()
    fun collectFrom(cls: ClassItem, includeStatic: Boolean = true) {
        if (!visited.add(cls)) return
        for (method in cls.methods()) {
            if (method.name() == methodName && (includeStatic || !method.modifiers.isStatic())) {
                add(method)
            }
        }
    }

    collectFrom(this@findAllMethodsByName)
    for (superClass in allSuperClasses()) {
        collectFrom(superClass)
    }
    for (superInterface in allInterfaces()) {
        collectFrom(superInterface, includeStatic = false)
    }
    addAll(findImplicitObjectMethodsByName(methodName))
}

/**
 * Find the nearest field or non-constructor method named [memberName] on this [ClassItem] or its
 * supertypes, checking direct fields before direct methods at each level of the inheritance
 * hierarchy (this class -> superclass -> superinterfaces) while skipping `static` methods on strict
 * superinterfaces (which are not inherited per JLS 8.4.8 / 9.4.1).
 *
 * Returns the matched [FieldItem] when a field wins, a representative [MethodItem] when a method
 * wins, or `null` if no field or method named [memberName] exists in the hierarchy.
 */
private fun ClassItem.findNearestFieldOrMethod(
    memberName: String,
    isSuperInterface: Boolean = false,
    visited: MutableSet<ClassItem> = HashSet(),
): MemberItem? {
    if (!visited.add(this)) return null

    findField(memberName)?.let {
        return it
    }

    methods()
        .firstOrNull { it.name() == memberName && (!isSuperInterface || !it.modifiers.isStatic()) }
        ?.let {
            return it
        }

    superClass()?.findNearestFieldOrMethod(memberName, isSuperInterface = false, visited)?.let {
        return it
    }

    for (interfaceType in interfaceTypes()) {
        val superInterface = interfaceType.resolveClass(codebase) ?: continue
        superInterface.findNearestFieldOrMethod(memberName, isSuperInterface = true, visited)?.let {
            return it
        }
    }

    return null
}

/**
 * Validate an unparameterized callable reference against [containingClass] and [candidates].
 *
 * First checks that [containingClass] itself is accessible (so references to members of a hidden
 * class report the hidden class rather than an arbitrary member). When [candidates] is non-empty,
 * the reference is valid if at least one candidate overload is accessible via
 * [DocCommentContext.checkReference]; otherwise, the most informative invalid candidate is
 * re-checked with [reporter] to emit a single diagnostic.
 */
private fun checkCallableCandidates(
    context: DocCommentContext,
    reporter: LocationSpecificReporter,
    containingClass: ClassItem,
    candidates: List<CallableItem>,
): ReferenceCheckResult {
    val classCheck = context.checkReference(containingClass, reporter)
    if (!classCheck.isValid || candidates.isEmpty()) return classCheck

    var bestCandidate: CallableItem? = null
    var bestRank = Int.MAX_VALUE
    for (candidate in candidates) {
        val result = context.checkReference(candidate, NOOP_LOCATION_REPORTER)
        if (result.isValid) {
            return ReferenceCheckResult.Valid
        }
        val rank = invalidResultRank(result as ReferenceCheckResult.Invalid)
        if (rank < bestRank) {
            bestRank = rank
            bestCandidate = candidate
        }
    }

    return context.checkReference(bestCandidate!!, reporter)
}

/**
 * Rank [ReferenceCheckResult.Invalid] reasons from most informative (lowest number) to least
 * informative when all candidate overloads of an unparameterized callable reference are invalid.
 */
private fun invalidResultRank(result: ReferenceCheckResult.Invalid): Int =
    when (result) {
        is ReferenceCheckResult.WiderSurface -> 1
        is ReferenceCheckResult.UnguardedFlaggedApi -> 2
        is ReferenceCheckResult.FlaggedForRemoval -> 3
        is ReferenceCheckResult.DocOnly -> 4
        is ReferenceCheckResult.Removed -> 5
        is ReferenceCheckResult.Hidden -> if (result.isPublicOrProtected) 6 else 7
    }

/** An ambiguous reference to something by [name]. */
internal data class AmbiguousSourceReference(val name: String) : ParsedReference {
    override val normalizedForm: String
        get() = name

    override fun resolveReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter
    ): ResolvedSourceReference? =
        // Resolve the reference.
        when (val resolved = context.resolveItemReference(name, NameClassification.AMBIGUOUS)) {
            is ClassItem -> {
                val resolvedRef = resolved.toResolvedReference(context, reporter)
                val sourceName =
                    rewriteMappedSourceClassName(name, resolved, resolvedRef.fullyQualifiedForm)
                ResolvedSourceReference(sourceName, resolvedRef)
            }
            is PackageItem ->
                ResolvedSourceReference(name, resolved.toResolvedReference(context, reporter))
            // Type parameters are scoped to the declaring item/class and cannot be hidden
            // independently, so they do not need reference visibility checking.
            is TypeParameterItem -> ResolvedSourceReference(name, resolved.toResolvedReference())
            is FieldItem ->
                ResolvedSourceReference(name, resolved.toResolvedReference(context, reporter))
            is ReferencableMethodSet -> {
                // When `name` is dot-qualified (`<Qualifier>.<method>`), preserve and validate the
                // explicit qualifying class. When `name` is unqualified and inherited, target the
                // innermost enclosing class inheriting `resolved.containingClass` so overloads
                // across all supertypes participate and hidden intermediate supertypes do not
                // shadow public inherited overloads, while leaving static imports and implicit
                // `java.lang.Object` methods on interfaces/annotations on
                // `resolved.containingClass`.
                val qualifierClass =
                    name
                        .lastIndexOf('.')
                        .takeIf { it != -1 }
                        ?.let { lastDot ->
                            context.resolveItemReference(
                                name.substring(0, lastDot),
                                NameClassification.CLASS,
                            ) as? ClassItem
                        }
                val targetClass =
                    qualifierClass
                        ?: generateSequence(context.containingClassItem) { it.containingClass() }
                            .firstOrNull { scopeClass ->
                                scopeClass == resolved.containingClass ||
                                    resolved.containingClass in scopeClass.allSuperClasses() ||
                                    resolved.containingClass in scopeClass.allInterfaces()
                            }
                        ?: resolved.containingClass
                val candidates = targetClass.findAllMethodsByName(resolved.name)
                val checkResult =
                    checkCallableCandidates(context, reporter, targetClass, candidates)
                val resolvedClassName = context.resolveClassReferenceName(targetClass)
                val sourceCallableName =
                    rewriteMappedSourceCallableName(
                        name,
                        targetClass,
                        resolved.name,
                        resolvedClassName,
                    )
                ResolvedSourceReference(
                    sourceCallableName,
                    CallableReference(
                        resolvedClassName,
                        resolved.name,
                        checkResult = checkResult,
                    ),
                )
            }
            is ExternalReferencableItem -> null
            is InvalidReferencableItem -> {
                resolved.reportIssue(reporter)
                null
            }
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
    ): ResolvedSourceReference? {
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

        // Use the resolved (possibly mapped) class name if available, otherwise use the source
        // name.
        val resolvedClassName =
            classItem?.let { context.resolveClassReferenceName(it) } ?: className
        val resolvedReference = member.findIn(context, reporter, classItem, resolvedClassName)
        val sourceClassName =
            if (classItem != null) {
                rewriteMappedSourceClassName(className, classItem, resolvedClassName)
            } else {
                className
            }
        return ResolvedSourceReference(
            "$sourceClassName#${member.normalizedForm}",
            resolvedReference
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
    ): ResolvedSourceReference? {
        val classItem = context.containingClassItem
        if (classItem == null && member !is UriFragmentSourceReference) {
            reporter.report(
                Issues.UNRESOLVED_LINK,
                "Could not resolve '$normalizedForm' as there is no containing class",
            )
        }
        val resolvedClassName = classItem?.let { context.resolveClassReferenceName(it) } ?: ""
        val resolvedReference = member.findIn(context, reporter, classItem, resolvedClassName)
        return ResolvedSourceReference(normalizedForm, resolvedReference)
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
    ): ResolvedReference {
        if (classItem == null) {
            return FieldReference(className, name, checkResult = ReferenceCheckResult.Valid)
        }

        // Walk `classItem` and its enclosing classes (matching `javadoc` and Doclava member
        // lookup). Keep `className` when resolved on `classItem` (so `SubClass#superMember`
        // preserves its source qualification), and rewrite to `scopeClass` when resolved on an
        // enclosing class so generated stubs target the enclosing class rather than a non-existent
        // member on the nested class.
        for (scopeClass in generateSequence(classItem) { it.containingClass() }) {
            val targetClassName =
                if (scopeClass == classItem) {
                    className
                } else {
                    context.resolveClassReferenceName(scopeClass)
                }

            // On `classItem` itself, declared fields take precedence over same-class constructors,
            // which in turn take precedence over methods and inherited members (matching `javadoc`
            // and avoiding falling back to same-named methods when all constructors are hidden).
            if (scopeClass == classItem && name == classItem.simpleName()) {
                classItem.findField(name)?.let { field ->
                    val checkResult = context.checkReference(field, reporter)
                    return FieldReference(className, field.name(), checkResult = checkResult)
                }
                val constructors = classItem.constructors()
                if (constructors.isNotEmpty()) {
                    val checkResult =
                        checkCallableCandidates(context, reporter, classItem, constructors)
                    return CallableReference(className, name, checkResult = checkResult)
                }
            }

            val member = scopeClass.findNearestFieldOrMethod(name)
            if (member is FieldItem) {
                val classCheck =
                    if (member.containingClass() != classItem) {
                        context.checkReference(classItem, reporter)
                    } else {
                        ReferenceCheckResult.Valid
                    }
                val checkResult =
                    if (classCheck.isValid) {
                        context.checkReference(member, reporter)
                    } else {
                        classCheck
                    }
                return FieldReference(targetClassName, member.name(), checkResult = checkResult)
            }

            val methods =
                if (member is MethodItem) {
                    scopeClass.findAllMethodsByName(name)
                } else {
                    scopeClass.findImplicitObjectMethodsByName(name)
                }
            if (methods.isNotEmpty()) {
                val classCheck =
                    if (scopeClass != classItem) {
                        context.checkReference(classItem, reporter)
                    } else {
                        ReferenceCheckResult.Valid
                    }
                val checkResult =
                    if (classCheck.isValid) {
                        checkCallableCandidates(context, reporter, scopeClass, methods)
                    } else {
                        classCheck
                    }
                return CallableReference(targetClassName, name, checkResult = checkResult)
            }
        }

        // When no matching member is found, still validate that the qualifying class itself is
        // accessible.
        val checkResult = context.checkReference(classItem, reporter)

        val nestedClassOwner = findAccessibleNestedClassOwner(context, classItem)
        val hint =
            if (nestedClassOwner != null) {
                val ownerName =
                    if (
                        nestedClassOwner.containingPackage() ==
                            context.containingClassItem?.containingPackage()
                    ) {
                        nestedClassOwner.fullName()
                    } else {
                        context.resolveClassReferenceName(nestedClassOwner)
                    }
                "; did you mean nested class '$ownerName.$name'?"
            } else {
                ""
            }
        reporter.report(Issues.UNRESOLVED_LINK, "Could not resolve '$name' in '$classItem'$hint")

        // If the member could not be found then fallback to a field reference so that at least
        // the resolved class will be fully qualified.
        return FieldReference(className, name, checkResult = checkResult)
    }

    /**
     * Find the innermost enclosing class of [classItem] that directly declares or inherits an
     * accessible nested class named [name], or `null` if none exists.
     *
     * Restricts supertype nested classes to inheritable declarations (per JLS 8.2 / 8.5: `public`,
     * `protected`, or package-private when every class from `scope` up to the declaring superclass
     * is in the same package) and requires both the owner scope and matched nested class to pass
     * [DocCommentContext.checkReference] so inaccessible or hidden nested classes never produce
     * unusable hints or shadow valid enclosing-class matches.
     */
    private fun findAccessibleNestedClassOwner(
        context: DocCommentContext,
        classItem: ClassItem,
    ): ClassItem? =
        generateSequence(classItem) { it.containingClass() }
            .firstOrNull { scope ->
                if (!context.checkReference(scope, NOOP_LOCATION_REPORTER).isValid) {
                    return@firstOrNull false
                }
                if (
                    scope.nestedClasses().any { nested ->
                        nested.simpleName() == name &&
                            context.checkReference(nested, NOOP_LOCATION_REPORTER).isValid
                    }
                ) {
                    return@firstOrNull true
                }

                // Walk superclasses while tracking whether every class in the chain stays in
                // `scope`'s package (required for inheriting package-private nested classes).
                var samePackageChain = true
                for (superClass in scope.allSuperClasses()) {
                    samePackageChain =
                        samePackageChain &&
                            superClass.containingPackage() == scope.containingPackage()
                    if (
                        superClass.nestedClasses().any { nested ->
                            nested.simpleName() == name &&
                                (nested.modifiers.isPublic() ||
                                    nested.modifiers.isProtected() ||
                                    (samePackageChain && nested.modifiers.isPackagePrivate())) &&
                                context.checkReference(nested, NOOP_LOCATION_REPORTER).isValid
                        }
                    ) {
                        return@firstOrNull true
                    }
                }

                scope.allInterfaces().any { superInterface ->
                    superInterface.nestedClasses().any { nested ->
                        nested.simpleName() == name &&
                            nested.modifiers.isPublic() &&
                            context.checkReference(nested, NOOP_LOCATION_REPORTER).isValid
                    }
                }
            }
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
    ): ResolvedSourceReference? {
        val resolved = context.resolveItemReference(name, NameClassification.CALLABLE_SET)
        return when (resolved) {
            is ReferencableMethodSet -> {
                // TODO(b/447588621): Try and find a callable that matches the resolved
                //  [parameters].
                // Callable overloads are not yet resolved to a single CallableItem, so at least
                // validate that their containing class is accessible.
                val containingClass = resolved.containingClass
                val classCheckResult = context.checkReference(containingClass, reporter)

                val resolvedClassName = context.resolveClassReferenceName(containingClass)
                val sourceCallableName =
                    rewriteMappedSourceCallableName(
                        name,
                        containingClass,
                        resolved.name,
                        resolvedClassName,
                    )
                ResolvedSourceReference(
                    formatSignature(sourceCallableName, parameters, eraseGenericArguments = false),
                    createCallableReference(
                        context,
                        reporter,
                        resolvedClassName,
                        // Use the resolved name as the source name may be qualified.
                        resolved.name,
                        classCheckResult,
                    ),
                )
            }
            is ClassItem -> {
                // Resolving a callable can return the class in lieu of a set of constructor so
                // treat it as one, validate that the class is accessible, and create a reference
                // to the constructor.
                val classCheckResult = context.checkReference(resolved, reporter)

                val resolvedClassName = context.resolveClassReferenceName(resolved)
                val sourceCallableName =
                    rewriteMappedSourceClassName(name, resolved, resolvedClassName)
                ResolvedSourceReference(
                    formatSignature(sourceCallableName, parameters, eraseGenericArguments = false),
                    createCallableReference(
                        context,
                        reporter,
                        resolvedClassName,
                        // Use the class's simple name as the constructor name.
                        resolved.simpleName(),
                        classCheckResult,
                    ),
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
    ): CallableReference {
        // Validate that the qualifying class of the callable reference is accessible.
        val classCheckResult =
            if (classItem == null) ReferenceCheckResult.Valid
            else context.checkReference(classItem, reporter)

        return createCallableReference(
            context,
            reporter,
            className,
            // The source name will always be the simple callable name in this case, it will never
            // be qualified so it is safe to use in the callable reference.
            name,
            classCheckResult,
        )
    }

    /**
     * Return a [CallableReference] that uses the fully qualified name of the containing class and
     * fully qualified parameter types.
     */
    private fun createCallableReference(
        context: DocCommentContext,
        reporter: LocationSpecificReporter,
        className: String,
        name: String,
        classCheckResult: ReferenceCheckResult,
    ): CallableReference {
        var checkResult = classCheckResult
        val qualifiedParameters =
            parameters.map { sourceParameter ->
                val (fullyQualifiedType, parameterCheckResult) =
                    sourceParameter.type.fullyQualify(context, reporter)
                checkResult = checkResult.combineWith(parameterCheckResult)
                SourceParameter(fullyQualifiedType, sourceParameter.name)
            }
        return CallableReference(
            className,
            formatSignature(
                name,
                qualifiedParameters,

                // Erase generic arguments from the reference part of this.
                eraseGenericArguments = true,
            ),
            checkResult = checkResult,
        )
    }

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

/**
 * Combine two [ReferenceCheckResult]s, retaining the first [ReferenceCheckResult.Invalid] if either
 * result is invalid.
 */
private fun ReferenceCheckResult.combineWith(other: ReferenceCheckResult): ReferenceCheckResult =
    if (isValid) other else this

/**
 * Fully qualify this [TypeItem] within [context], reporting any issues to [reporter], and return
 * the qualified [TypeItem] paired with the combined [ReferenceCheckResult] of all resolved classes
 * in the type.
 */
private fun TypeItem.fullyQualify(
    context: DocCommentContext,
    reporter: LocationSpecificReporter,
): Pair<TypeItem, ReferenceCheckResult> {
    var checkResult: ReferenceCheckResult = ReferenceCheckResult.Valid
    val qualifiedType =
        transform(
            object : BaseTypeTransformer() {
                override fun transform(typeItem: ClassTypeItem): ClassTypeItem {
                    // Resolve the qualified name (which may in fact be a simple name, or a
                    // partially qualified name) as a class.
                    val resolved =
                        context.resolveItemReference(
                            typeItem.qualifiedName,
                            NameClassification.CLASS,
                        )

                    val resolvedType =
                        when (resolved) {
                            is ClassItem -> {
                                // Validate each parameter class referenced in the callable
                                // signature.
                                checkResult =
                                    checkResult.combineWith(
                                        context.checkReference(resolved, reporter)
                                    )

                                // Use the resolved class's type.
                                resolved.type()
                            }
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
    return qualifiedType to checkResult
}

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
    ): UriFragmentReference {
        // Validate that the qualifying class of `Class##uri-fragment` is accessible.
        val checkResult =
            if (classItem == null) ReferenceCheckResult.Valid
            else context.checkReference(classItem, reporter)

        // TODO(b/447588621): If the source for classItem is available then check it for an
        //  id="<uriFragment>" attribute.
        return UriFragmentReference(className, uriFragment, checkResult = checkResult)
    }
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

    /**
     * The original reference written in the source comment (prior to any mapped annotation
     * rewriting), used when reporting diagnostics on the source comment.
     */
    private val rawSourceReference: String = sourceReference,
) : TagData {
    /**
     * Report [Issues.MALFORMED_DOC_REFERENCE] if this references a [UriFragmentReference] without a
     * label ([remainder] is `null`).
     */
    override fun checkExtractedContent(
        reporter: LocationSpecificReporter,
        remainder: JavadocContent?,
    ) {
        if (resolvedReference is UriFragmentReference && remainder == null) {
            reporter.report(
                Issues.MALFORMED_DOC_REFERENCE,
                "Malformed reference `$rawSourceReference`, URI fragment reference requires a label",
            )
        }
    }

    /**
     * Print [inlineTag] to [contentPrinter], replacing the tag with `ERROR[<reason>: <link>]` or
     * `ERROR[<reason>: <link> <label>]` when [resolvedReference] failed
     * [DocCommentContext.checkReference] ([ReferenceCheckResult.Invalid]).
     */
    override fun printInlineTag(
        inlineTag: JavadocInlineTag,
        contentPrinter: JavadocContentPrinter,
    ) {
        val checkResult = resolvedReference?.checkResult
        if (checkResult !is ReferenceCheckResult.Invalid) {
            super.printInlineTag(inlineTag, contentPrinter)
            return
        }

        printErrorReference(contentPrinter, checkResult, inlineTag.content)
    }

    /**
     * Print the tag contents which consists of the [sourceReference] and the [content] which is the
     * optional label.
     *
     * If the [resolvedReference] is different to the [sourceReference] and [content] is `null` then
     * this will use the [sourceReference] as the label.
     */
    override fun printTagContents(contentPrinter: JavadocContentPrinter, content: JavadocContent?) {
        val writer = contentPrinter.writer
        val checkResult = resolvedReference?.checkResult
        if (checkResult is ReferenceCheckResult.Invalid) {
            writer.print(" \"")
            printErrorReference(contentPrinter, checkResult, content)
            writer.print("\"")
            return
        }

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
     * Print `ERROR[<reason>: <link>]` or `ERROR[<reason>: <link> <label>]` to [contentPrinter],
     * using [invalidResult]'s [ReferenceCheckResult.Invalid.reasonLabel] as `<reason>`,
     * [sourceReference] as `<link>`, and [content] (if present) as `<label>`.
     */
    private fun printErrorReference(
        contentPrinter: JavadocContentPrinter,
        invalidResult: ReferenceCheckResult.Invalid,
        content: JavadocContent?,
    ) {
        val writer = contentPrinter.writer
        writer.print("ERROR[")
        writer.print(invalidResult.reasonLabel)
        writer.print(": ")
        writer.print(sourceReference)
        content?.printWithLeadingSpaceTo(contentPrinter)
        writer.print("]")
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
