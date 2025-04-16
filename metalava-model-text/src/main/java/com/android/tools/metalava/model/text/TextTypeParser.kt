/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeVisitor
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.text.TextTypeParser.CacheEntry
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.type.UnqualifiedClassHandler
import com.android.tools.metalava.reporter.Issues

/** Parses and caches types within a [annotationContext]. */
internal class TextTypeParser
private constructor(
    annotationContext: AnnotationContext,
    kotlinStyleNulls: Boolean,
    private val countingErrorReporter: CountingErrorReporter,
) :
    TypeItemParser(
        annotationContext,
        UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        kotlinStyleNulls,
        countingErrorReporter,
    ) {

    /**
     * Secondary constructor that will wrap the [errorReporter] it is given in a
     * [CountingErrorReporter] before calling the primary constructor.
     */
    constructor(
        annotationContext: AnnotationContext,
        kotlinStyleNulls: Boolean = false,
        errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
    ) : this(annotationContext, kotlinStyleNulls, CountingErrorReporter(errorReporter))

    /**
     * The cache key, incorporates some information from [ContextNullability] and [kotlinStyleNulls]
     * as well as the type string as they can all affect the created [TypeItem].
     *
     * e.g. [ContextNullability.forceNonNull] will cause the type to always be
     * [TypeNullability.NONNULL] even if [kotlinStyleNulls] is `false` which would normally cause it
     * to be [TypeNullability.PLATFORM]. However, when [kotlinStyleNulls] is `true` then there is no
     * difference between [ContextNullability.forceNonNull] and [ContextNullability.none] as they
     * will both cause a class type with no nullability suffix to be treated as
     * [TypeNullability.NONNULL].
     *
     * That information is encapsulated in the [forceClassToBeNonNull] property.
     */
    private data class Key(val forceClassToBeNonNull: Boolean, val type: String)

    /** The cache from [Key] to [CacheEntry]. */
    private val typeCache = HashMap<Key, CacheEntry>()

    internal var requests = 0
    internal var cacheSkip = 0
    internal var cacheHit = 0
    internal var cacheSize = 0

    /** Override [parseType] to cache the result, if possible. */
    override fun parseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        forceClassToBeNonNull: Boolean,
    ): TypeItem {
        requests++
        // Don't use the cache when there are type-use annotations not contained in the string.
        return if (annotations.isEmpty()) {
            val key = Key(forceClassToBeNonNull, type)

            // Get the cache entry for the supplied type and forceClassToBeNonNull.
            val result =
                typeCache.computeIfAbsent(key) { CacheEntry(it.type, it.forceClassToBeNonNull) }

            // Get the appropriate [TypeItem], creating one if necessary.
            result.getTypeItem(typeParameterScope)
        } else {
            cacheSkip++
            unCachedParseType(type, typeParameterScope, annotations, forceClassToBeNonNull)
        }
    }

    /** Delegates to super, non-caching [parseType] method. */
    private fun unCachedParseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        annotations: List<AnnotationItem>,
        forceClassToBeNonNull: Boolean,
    ) = super.parseType(type, typeParameterScope, annotations, forceClassToBeNonNull)

    /**
     * The cache entry, that contains the [TypeItem] that has been produced from the [type] and
     * [forceClassToBeNonNull] properties.
     */
    internal inner class CacheEntry(
        /** The string type from which the [TypeItem] will be parsed. */
        private val type: String,

        /**
         * Indicates whether an outermost [ClassTypeItem] is forced to be [TypeNullability.NONNULL].
         *
         * It is passed into [parseType] and if `true` it will cause the top level class type to be
         * treated as if it was being parsed when [kotlinStyleNulls] is `true` as that sets
         * [TypeNullability.NONNULL] by default.
         */
        private val forceClassToBeNonNull: Boolean,
    ) {
        /**
         * Map from [TypeParameterScope] to the [TypeItem] created for it.
         *
         * The [TypeParameterScope] that will be used to cache a type depends on the unqualified
         * names used in the type. It will use the closest enclosing scope of the one supplied that
         * adds at least one type parameter whose name is used in the type.
         *
         * See [TypeParameterScope.findSignificantScope].
         */
        private val scopeToItem = mutableMapOf<TypeParameterScope, TypeItem>()

        /**
         * The set of unqualified names used by [type].
         *
         * This is determined solely by the contents of the [type] string and so will be the same
         * for all [TypeItem]s cached in this entry.
         *
         * If this has not been set then no type items have been cached in this entry. It is set the
         * first time that a [TypeItem] is cached.
         */
        private lateinit var unqualifiedNamesInType: Set<String>

        /** Get the [TypeItem] for this type depending on the setting of [forceClassToBeNonNull]. */
        fun getTypeItem(typeParameterScope: TypeParameterScope): TypeItem {
            // If this is not the first time through then check to see if anything suitable has been
            // cached.
            val scopeForCachingOrNull =
                if (::unqualifiedNamesInType.isInitialized) {
                    // Find the scope to use for caching this type and then check to see if a
                    // [TypeItem]
                    // has been cached for that scope and if so return it. Otherwise, drop out.
                    typeParameterScope.findSignificantScope(unqualifiedNamesInType).also {
                        scopeForCaching ->
                        scopeToItem[scopeForCaching]?.let {
                            cacheHit++
                            return it
                        }
                    }
                } else {
                    // This is the first time through, so [unqualifiedNamesInType] is not available
                    // so drop through and initialize later.
                    null
                }

            // Remember the number of errors that have been reported so far.
            val startErrorCount = countingErrorReporter.errorCount

            // Parse the [type] to produce a [TypeItem]. This may report errors.
            val typeItem = createTypeItem(typeParameterScope)

            // If the error count is different then do not cache this.
            if (countingErrorReporter.errorCount != startErrorCount) {
                return typeItem
            }

            cacheSize++

            // Find the scope for caching if it was not found above.
            val scopeForCaching =
                scopeForCachingOrNull
                    ?: let {
                        // This will only happen if [unqualifiedNamesInType] is uninitialized so
                        // make sure to initialize it.
                        unqualifiedNamesInType = unqualifiedNameGatherer.gatherFrom(typeItem)

                        // Find the scope for caching. It could not be found before because
                        // [unqualifiedNamesInType] was not initialized.
                        typeParameterScope.findSignificantScope(unqualifiedNamesInType)
                    }

            // Store the type item in the scope selected for caching.
            scopeToItem[scopeForCaching] = typeItem

            // Return it.
            return typeItem
        }

        /**
         * Create a new [TypeItem] for [type] with the given [forceClassToBeNonNull] setting and for
         * the requested [typeParameterScope].
         */
        private fun createTypeItem(typeParameterScope: TypeParameterScope): TypeItem {
            return unCachedParseType(type, typeParameterScope, emptyList(), forceClassToBeNonNull)
        }
    }

    /**
     * A [TypeVisitor] that will extract all unqualified names from the type.
     *
     * These are the names that could be used as a type parameter name and so whose meaning could
     * change depending on the [TypeParameterScope], i.e. the set of type parameters currently in
     * scope.
     */
    private class UnqualifiedNameGatherer : BaseTypeVisitor() {

        private val unqualifiedNames = mutableSetOf<String>()

        override fun visit(primitiveType: PrimitiveTypeItem) {
            // Primitive type names are added because Kotlin allows them to be shadowed by a type
            // parameter.
            unqualifiedNames.add(primitiveType.kind.primitiveName)
        }

        override fun visitClassType(classType: ClassTypeItem) {
            // Classes in java.lang package can be represented in the type without the leading
            // package, all other types must be fully qualified. At this point it is not clear
            // whether the type used in the input type string was qualified or not as the package
            // has been prepended so this assumes that they all are just to be on the safe side.
            val name = classType.qualifiedName
            if (!name.contains('.')) {
                unqualifiedNames.add(name)
            } else {
                if (classType.classNamePrefix == JAVA_LANG_PREFIX) {
                    unqualifiedNames.add(classType.className)
                }
            }
        }

        override fun visitVariableType(variableType: VariableTypeItem) {
            unqualifiedNames.add(variableType.name)
        }

        /** Gather the names from [typeItem] returning an immutable set of the unqualified names. */
        fun gatherFrom(typeItem: TypeItem): Set<String> {
            unqualifiedNames.clear()
            typeItem.accept(this)
            return unqualifiedNames.toSet()
        }
    }

    /**
     * An instance of [UnqualifiedNameGatherer] used for gathering all the unqualified names from
     * all the [TypeItem]s cached by this.
     */
    private val unqualifiedNameGatherer = UnqualifiedNameGatherer()
}

/**
 * Report a recoverable error.
 *
 * This keeps a count of how many were reported so that [CacheEntry.getTypeItem] can use that to
 * determine if any errors were found while parsing a type ([errorCount] increased) and so prevent
 * it from being cached which would suppress any more errors with that type string.
 */
private class CountingErrorReporter(
    private val delegateErrorReporter: TypeItemParserErrorReporter
) : TypeItemParserErrorReporter {
    /**
     * A count of the errors reported through this.
     *
     * This is used to prevent caching [TypeItem]s that reported errors to make sure that every such
     * case is reported.
     */
    var errorCount = 0

    override fun report(
        issue: Issues.Issue,
        message: String,
    ) {
        delegateErrorReporter.report(issue, message)
        errorCount += 1
    }
}
