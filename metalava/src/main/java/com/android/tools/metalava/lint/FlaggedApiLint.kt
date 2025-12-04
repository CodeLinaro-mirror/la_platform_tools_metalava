/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.lint

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.value.ValueKind
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.model.visitors.ApiVisitor.Companion.addTargetLanguageCheck
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues.FLAGGED_API_LITERAL
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Issues.UNFLAGGED_API
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import java.io.StringWriter
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

/**
 * The [FlaggedApiLint] analyzer checks the API against a known set of preferred FlaggedAPI
 * practices by the Android API council.
 */
class FlaggedApiLint(
    private val oldCodebase: Codebase?,
    reporter: Reporter,
    apiPredicateConfig: ApiPredicate.Config,
) : DelegatedVisitor {

    /** Predicate that checks if the item appears in the signature file. */
    private val elidingFilterEmit = ApiType.PUBLIC_API.getEmitFilter(apiPredicateConfig)
    private val apiFilters = ApiType.PUBLIC_API.getNonElidingApiFilters(apiPredicateConfig)
    private val apiFiltersReference = apiFilters.reference
    private val targetLanguages = com.android.tools.metalava.model.TargetLanguageSet.SOURCE
    private val filterEmit = addTargetLanguageCheck(apiFilters.emit, targetLanguages)
    private val filteredReporter = FilteringReporter(reporter, oldCodebase, filterEmit)

    /** The filter to use to determine if we should emit a reference to an item */
    private val filterReference = addTargetLanguageCheck(apiFiltersReference, targetLanguages)

    private fun report(
        id: Issue,
        item: Item,
        message: String,
        location: FileLocation = FileLocation.UNKNOWN,
        maximumSeverity: Severity = Severity.UNLIMITED,
    ) {
        filteredReporter.withContext(item) {
            filteredReporter.report(id, item, message, location, maximumSeverity)
        }
    }

    override fun visitClass(cls: ClassItem) {
        checkClass(cls)
    }

    private fun visitCallable(callable: CallableItem) {
        checkHasFlaggedApi(callable)
        checkFlaggedApiLiteral(callable)
    }

    override fun visitMethod(method: MethodItem) {
        visitCallable(method)
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        visitCallable(constructor)
    }

    override fun visitField(field: FieldItem) {
        checkField(field)
    }

    private fun checkClass(
        cls: ClassItem,
    ) {
        checkHasFlaggedApi(cls)
        checkFlaggedApiLiteral(cls)
    }

    private fun checkField(field: FieldItem) {
        checkHasFlaggedApi(field)
        checkFlaggedApiLiteral(field)
    }

    private fun checkFlaggedApiLiteral(item: Item) {
        if (item.codebase.preFiltered) {
            // Flag constants aren't ever API, so prefiltered codebases would always only contain
            // literals.
            return
        }

        val annotation =
            item.modifiers.findAnnotation { it.qualifiedName == ANDROID_FLAGGED_API } ?: return
        val attr = annotation.attributes.find { attr -> attr.name == "value" } ?: return

        // Get the flag value, should be a reference to a constant field.
        val flagValue = attr.value
        if (flagValue.kind != ValueKind.FIELD) {
            // It is not a reference to a field so get the string value and try and see if the field
            // could be found.
            val value = flagValue.asString()

            // Reverse engineer the string value to a field reference and resolve it to a FieldItem,
            // if possible.
            val field = value?.let { aconfigFlagLiteralToFieldOrNull(item.codebase, it) }

            // Generate some helpful text so the developer knows what to do to fix it.
            val replacement =
                if (field != null) {
                    val (fieldSource, fieldItem) = field
                    if (fieldItem != null) {
                        fieldSource
                    } else {
                        "$fieldSource, however this flag doesn't seem to exist"
                    }
                } else {
                    "furthermore, the current flag literal seems to be malformed"
                }

            report(
                FLAGGED_API_LITERAL,
                item,
                "@FlaggedApi contains a string literal, but should reference the field generated by aconfig ($replacement).",
                location = annotation.fileLocation,
            )
        }
    }

    private fun checkHasFlaggedApi(item: SelectableItem) {
        // Cannot flag an implicit constructor.
        if (item is ConstructorItem && item.isImplicitConstructor()) return

        fun itemOrAnyContainingClasses(predicate: FilterPredicate): Boolean {
            var it: SelectableItem? = item
            while (it != null) {
                if (predicate.test(it)) {
                    return true
                }
                it = it.containingClass()
            }
            return false
        }
        if (
            !itemOrAnyContainingClasses {
                it.modifiers.hasAnnotation { it.qualifiedName == ANDROID_FLAGGED_API }
            }
        ) {
            val previouslyReleasedItem = Codebase.findPreviouslyReleased(oldCodebase, item)
            if (previouslyReleasedItem == null) {
                checkFlaggedApiOnNewApi(item)
            } else {
                checkFlaggedApiOnPreviouslyReleasedApi(previouslyReleasedItem, item)
            }
        }
    }

    /**
     * Check whether an `@FlaggedApi` annotation is required on a new [Item], i.e. one that has not
     * previously been released.
     */
    private fun checkFlaggedApiOnNewApi(item: SelectableItem) {
        val elidedField =
            if (item is FieldItem) {
                val inheritedFrom = item.inheritedFrom
                // The field gets elided if we're able to reference the original class, but not emit
                // it; this happens e.g. when inheriting from a public API interface into an
                // @SystemApi class.
                // The only edge-case we don't handle well here is if the inheritance itself is new,
                // because that can't be flagged.
                // TODO(b/299659989): adjust comment once flagging inheritance is possible.
                inheritedFrom != null && filterReference.test(inheritedFrom)
            } else {
                false
            }
        if (!elidingFilterEmit.test(item) || elidedField) {
            // This API wouldn't appear in the signature file, so we don't know here if the API is
            // pre-existing.
            // Since the base API is either new and subject to flagging rules, or preexisting and
            // therefore stable, the elided API is not required to be flagged.
            // The only edge-case we don't handle well here is if the inheritance itself is new,
            // because that can't be flagged.
            // TODO(b/299659989): adjust comment once flagging inheritance is possible.
            return
        }
        report(UNFLAGGED_API, item, "New API must be flagged with @FlaggedApi: ${item.describe()}")
    }

    /**
     * Check to see whether a `FlaggedApi` annotation is required due to changes on an existing API.
     */
    private fun checkFlaggedApiOnPreviouslyReleasedApi(previousItem: Item, currentItem: Item) {
        // Check the deprecated status, if it has changed
        val previousDeprecated = previousItem.effectivelyDeprecated
        val currentDeprecated = currentItem.effectivelyDeprecated
        if (
            currentDeprecated != previousDeprecated &&
                currentItem.originallyDeprecated != previousItem.originallyDeprecated
        ) {
            val location =
                if (currentItem.originallyDeprecated)
                    currentItem.modifiers.findAnnotation(JAVA_LANG_DEPRECATED)?.fileLocation
                else null
            fun deprecatedStatus(b: Boolean): String {
                return if (b) "deprecated" else "not deprecated"
            }
            val current = deprecatedStatus(currentDeprecated)
            val previous = deprecatedStatus(previousDeprecated)
            report(
                UNFLAGGED_API,
                currentItem,
                "Changes from $previous to $current must be flagged with @FlaggedApi: ${currentItem.describe()}",
                location = location ?: FileLocation.UNKNOWN,
                maximumSeverity = Severity.WARNING_ERROR_WHEN_NEW,
            )
            // Reporting the same issue on the same Item is pointless as the first report will
            // update the baseline and so suppress the second report so return immediately.
            return
        }

        // Generate the modifiers from the previous API.
        val previousModifiers = normalizeModifiers(previousItem)
        // Generate the modifiers from the current API.
        val currentModifiers = normalizeModifiers(currentItem)

        if (currentModifiers != previousModifiers) {
            report(
                UNFLAGGED_API,
                currentItem,
                "Changes to modifiers, from '$previousModifiers' to '$currentModifiers' must be flagged with @FlaggedApi: ${currentItem.describe()}",
                maximumSeverity = Severity.WARNING_ERROR_WHEN_NEW
            )
            // Reporting the same issue on the same Item is pointless as the first report will
            // update the baseline and so suppress the second report so return immediately.
            return
        }
    }

    /**
     * Normalize the modifiers for the [Item].
     *
     * This uses the [ModifierListWriter] for signature files as that already has a lot of logic for
     * handling signature files and ultimately it is changes to the signature files that need to be
     * flagged.
     */
    private fun normalizeModifiers(item: Item): String {
        return StringWriter().use { writer ->
            val modifierListWriter =
                ModifierListWriter.forSignature(
                    writer,
                    skipNullnessAnnotations = true,
                )
            modifierListWriter.write(item, normalizeFinal = true, skipRequiresPermission = true)
            val normalizedModifiers = writer.toString().trim()
            normalizedModifiers
        }
    }

    companion object {
        /**
         * Heuristically converts the given string [literal] into a reference to the equivalent
         * `aconfig`-generated `Flags.java` field.
         *
         * @return a pair of the field reference as Java / Kotlin source, and the referenced field
         *   item (if found in [codebase]); or `null` if the literal cannot be converted.
         */
        private fun aconfigFlagLiteralToFieldOrNull(
            codebase: Codebase,
            literal: String
        ): Pair<String, FieldItem?>? {
            if (literal.contains('/')) {
                return null
            }
            val parts = literal.split('.')

            val flag = parts.lastOrNull() ?: return null
            val flagField = "FLAG_" + flag.toUpperCaseAsciiOnly()
            val pkg = parts.dropLast(1).joinToString(separator = ".")
            val className = "$pkg.Flags"
            val fieldSource = "$className.$flagField"

            val clazzOrNull = codebase.findClass(className)
            val fieldOrNull =
                clazzOrNull?.findField(
                    flagField,
                    includeSuperClasses = true,
                    includeInterfaces = true
                )
            return fieldSource to fieldOrNull
        }
    }
}
