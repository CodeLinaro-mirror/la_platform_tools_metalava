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
import com.android.tools.metalava.model.AnnotationFormatter
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.optionalFlagName
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.text.SignatureWriter
import com.android.tools.metalava.model.value.ValueKind
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues.FLAGGED_API_LITERAL
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Issues.UNEXPORTED_FLAGGED_API
import com.android.tools.metalava.reporter.Issues.UNFLAGGED_API
import com.android.tools.metalava.reporter.Reporter
import java.io.StringWriter

/**
 * The [FlaggedApiLint] analyzer checks the API against a known set of preferred `@FlaggedApi`
 * practices established by the Android API council.
 */
object FlaggedApiLint {
    /**
     * Run `@FlaggedApi` lint checks against [coreCodebaseFragment] and, when available,
     * [removedCodebaseFragment].
     *
     * @param apiSurface the [ApiSurface] currently being checked.
     * @param previouslyReleasedCodebase the previously released core API codebase against which
     *   newly added, modified, and removed APIs are compared, or `null` if no previous API was
     *   provided.
     * @param reporter the [Reporter] to which lint issues are reported.
     * @param coreCodebaseFragment the core (`ApiType.CORE`) API fragment to check via
     *   [CoreFlaggedApiLint].
     * @param coreApiFilters the [ApiFilters] selecting emitted and referenced items in the core
     *   API.
     * @param removedCodebaseFragment the removed (`ApiType.REMOVED`) API fragment to check via
     *   [RemovedFlaggedApiLint] when [previouslyReleasedCodebase] is non-null, or `null` if removed
     *   API checks are not applicable.
     * @param removedApiFilters the [ApiFilters] selecting emitted and referenced items in the
     *   removed API, or `null` if not applicable.
     */
    fun check(
        apiSurface: ApiSurface,
        previouslyReleasedCodebase: Codebase?,
        reporter: Reporter,
        coreCodebaseFragment: CodebaseFragment,
        coreApiFilters: ApiFilters,
        removedCodebaseFragment: CodebaseFragment? = null,
        removedApiFilters: ApiFilters? = null,
    ) {
        coreCodebaseFragment.accept(
            CoreFlaggedApiLint(
                previouslyReleasedCodebase,
                reporter,
                coreApiFilters,
            )
        )

        // Check newly removed APIs only when a previously released codebase and removed fragment
        // are available. Without `previouslyReleasedCodebase`, newly removed APIs cannot be
        // distinguished from never-released items or APIs already removed in earlier releases.
        if (
            previouslyReleasedCodebase != null &&
                removedCodebaseFragment != null &&
                removedApiFilters != null
        ) {
            removedCodebaseFragment.accept(
                RemovedFlaggedApiLint(
                    previouslyReleasedCodebase,
                    reporter,
                    removedApiFilters,
                    apiSurface.variantFor(ApiVariantType.REMOVED),
                )
            )
        }
    }
}

/**
 * Base [DelegatedVisitor] shared by `@FlaggedApi` checks on core and removed API fragments.
 *
 * Provides common issue reporting via [Reporter] as well as validation of `@FlaggedApi` annotations
 * that remain present in the codebase ([checkFlaggedApiLiteral] and [checkFlaggedApiIsExported]),
 * while delegating `@FlaggedApi` presence requirements ([checkHasFlaggedApi]) to subclasses.
 */
private abstract class BaseFlaggedApiLint(
    private val reporter: Reporter,
    apiFilters: ApiFilters,
) : DelegatedVisitor {
    /** Predicate that checks if the item appears in the signature file. */
    private val filterEmit = apiFilters.emit

    /** Report [id] on [item] through [reporter]. */
    protected fun report(
        id: Issue,
        item: Item,
        message: String,
        location: FileLocation = FileLocation.UNKNOWN,
    ) {
        reporter.report(id, item, message, location)
    }

    override fun visitClass(cls: ClassItem) {
        visitSelectableItem(cls)
    }

    override fun visitMethod(method: MethodItem) {
        visitSelectableItem(method)
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        visitSelectableItem(constructor)
    }

    override fun visitField(field: FieldItem) {
        visitSelectableItem(field)
    }

    /**
     * Skip any [item] that is not itself emitted in the signature file (such as enclosing container
     * classes visited only so their emitted members or nested classes can be reached) before
     * delegating to [checkSelectableItem].
     */
    private fun visitSelectableItem(item: SelectableItem) {
        if (!filterEmit.test(item)) return
        checkSelectableItem(item)
    }

    /**
     * Perform `@FlaggedApi` lint checks on [item], verifying that:
     * 1. `@FlaggedApi` is present when required ([checkHasFlaggedApi]).
     * 2. Any `@FlaggedApi` annotation references an `aconfig` constant field rather than a raw
     *    string literal ([checkFlaggedApiLiteral]).
     * 3. Any `@FlaggedApi` annotation references an exported flag ([checkFlaggedApiIsExported]).
     */
    protected open fun checkSelectableItem(item: SelectableItem) {
        checkHasFlaggedApi(item)
        checkFlaggedApiLiteral(item)
        checkFlaggedApiIsExported(item)
    }

    /**
     * Check whether [item] requires a `@FlaggedApi` annotation and report [UNFLAGGED_API] if it is
     * missing.
     */
    protected abstract fun checkHasFlaggedApi(item: SelectableItem)

    /**
     * Verify that if [item] is annotated with `@FlaggedApi`, the referenced flag is marked as
     * exported in the flag configuration; otherwise report [UNEXPORTED_FLAGGED_API].
     */
    private fun checkFlaggedApiIsExported(item: Item) {
        val annotation =
            item.modifiers.findAnnotation { it.qualifiedName == ANDROID_FLAGGED_API } ?: return
        annotation.apiFlag?.let { apiFlag ->
            if (!apiFlag.isExported) {
                report(
                    UNEXPORTED_FLAGGED_API,
                    item,
                    "@FlaggedApi flag ${annotation.optionalFlagName} is not exported",
                    location = annotation.fileLocation,
                )
            }
        }
    }

    /**
     * Verify that if [item] is annotated with `@FlaggedApi` (in a non-pre-filtered source
     * codebase), its argument is a reference to an `aconfig`-generated `Flags.FLAG_*` constant
     * field rather than a raw string literal; otherwise report [FLAGGED_API_LITERAL].
     */
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
}

/**
 * Checks `@FlaggedApi` usage on items in the core (`ApiType.CORE`) API fragment, requiring
 * `@FlaggedApi` (on the item itself or an enclosing class) for newly added APIs and for deprecation
 * or modifier changes on previously released APIs.
 */
private class CoreFlaggedApiLint(
    private val oldCodebase: Codebase?,
    reporter: Reporter,
    apiFilters: ApiFilters,
) : BaseFlaggedApiLint(reporter, apiFilters) {
    /** The filter to use to determine if we should emit a reference to an item. */
    private val filterReference = apiFilters.reference

    override fun checkHasFlaggedApi(item: SelectableItem) {
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
        if (!itemOrAnyContainingClasses(FlaggedApiPredicate)) {
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
        if (item is FieldItem) {
            val inheritedFrom = item.inheritedFrom
            // The field gets elided if we're able to reference the original class, but not emit
            // it; this happens e.g. when inheriting from a public API interface into an
            // @SystemApi class.
            // The only edge-case we don't handle well here is if the inheritance itself is new,
            // because that can't be flagged.
            // TODO(b/299659989): adjust comment once flagging inheritance is possible.
            if (inheritedFrom != null && filterReference.test(inheritedFrom)) {
                // This API wouldn't appear in the signature file, so we don't know here if the API
                // is pre-existing.
                // Since the base API is either new and subject to flagging rules, or preexisting
                // and therefore stable, the elided API is not required to be flagged.
                // The only edge-case we don't handle well here is if the inheritance itself is new,
                // because that can't be flagged.
                // TODO(b/299659989): adjust comment once flagging inheritance is possible.
                return
            }
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
                ModifierListWriter(
                    writer = writer,
                    config = NORMALIZING_MODIFIER_LIST_WRITER_CONFIG,
                )
            var annotations = item.modifiers.annotations()
            SignatureWriter.sdkExtensionApiAnnotationToInherit(item, annotations)?.let {
                inheritedAnnotation ->
                annotations = annotations + inheritedAnnotation
            }
            modifierListWriter.write(item, annotations)
            val normalizedModifiers = writer.toString().trim()
            normalizedModifiers
        }
    }

    companion object {
        /** [ModifierListWriter.Config] suitable for use when normalizing modifiers. */
        private val NORMALIZING_MODIFIER_LIST_WRITER_CONFIG =
            ModifierListWriter.Config(
                target = AnnotationTarget.SIGNATURE_FILE,
                annotationFormatter = AnnotationFormatter.normalizingFormatter(),
                runtimeAnnotationsOnly = false,
                skipNullnessAnnotations = true,
            )
    }
}

/**
 * Checks `@FlaggedApi` usage on newly removed items in the removed (`ApiType.REMOVED`) fragment.
 *
 * When an API is marked for removal via `@RemovedFromApi` alongside an unfinalized (`KEEP` or
 * `REVERT`) `@FlaggedApi` annotation, `SelectedApiUpdater` keeps the item in the core
 * (`ApiType.CORE`) API, where [CoreFlaggedApiLint] checks it. An item enters the removed fragment
 * only when:
 * 1. It is marked with a configured removed annotation (e.g. `@RemovedFromApi`) **without**
 *    `@FlaggedApi`,
 * 2. It is marked with a configured removed annotation alongside a **finalized**
 *    (`ApiFlagAction.FINALIZE`) `@FlaggedApi` annotation, or
 * 3. It is marked with a legacy `@removed` Javadoc tag (which is never gated by `@FlaggedApi`).
 *
 * For items that were present in [oldCodebase] (the previously released core API) and now belong to
 * [removedVariant], this visitor reports [UNFLAGGED_API] on unflagged removals (cases 1 and 3) and
 * validates `@FlaggedApi` literal and exported-flag rules on finalized removals (case 2).
 */
private class RemovedFlaggedApiLint(
    private val oldCodebase: Codebase,
    reporter: Reporter,
    apiFilters: ApiFilters,
    private val removedVariant: ApiVariant,
) : BaseFlaggedApiLint(reporter, apiFilters) {
    override fun checkSelectableItem(item: SelectableItem) {
        // ApiSurfacePredicate.forDelta (used by filterEmit) also matches classes via
        // superClassApiVariants when a superclass belongs to the delta. Require `removedVariant`
        // directly in `itemApiVariants` so only items actually removed in this specific API surface
        // delta are checked (excluding base-surface removals when checking an extended surface such
        // as `@SystemApi`).
        if (removedVariant !in item.selectedApi.itemApiVariants) return

        // Only check items that existed in `oldCodebase` (the previously released core API, i.e.
        // `current.txt` rather than `removed.txt`) and are therefore being newly removed in this
        // release:
        // - Brand-new items marked `@RemovedFromApi` or `@removed` that were never released return
        //   `null` and do not require `@FlaggedApi`.
        // - Items that were already removed in an earlier release are absent from `oldCodebase` and
        //   return `null`, avoiding duplicate reports across releases.
        // - `inherit = true` (the default) is preserved so that if both a superclass method and an
        //   overriding subclass method are removed while the subclass itself remains in the API,
        //   the subclass override still matches the inherited superclass method in `oldCodebase`
        //   even when it was elided from the previous signature file.
        Codebase.findPreviouslyReleased(oldCodebase, item) ?: return

        super.checkSelectableItem(item)
    }

    override fun checkHasFlaggedApi(item: SelectableItem) {
        // Cannot flag an implicit constructor directly; its enclosing removed class is checked
        // instead.
        if (item is ConstructorItem && item.isImplicitConstructor()) return

        // When a class is removed, `SelectedApiUpdater` propagates removed status to all of its
        // constructors, methods, fields, and nested classes (across all nesting depths). Only
        // report `UnflaggedApi` on the outermost removed class itself rather than flooding every
        // enclosed member and nested class. Checking `ApiVariantSet.ALL_REMOVED` also handles a
        // `@SystemApi` member inside a `@RemovedFromApi @FlaggedApi` public class: because the
        // enclosing public class is already in `public(R)`, the enclosed `@SystemApi` member does
        // not require a separate `@FlaggedApi` annotation.
        if (
            item
                .containingClass()
                ?.selectedApi
                ?.itemApiVariants
                ?.containsAny(ApiVariantSet.ALL_REMOVED) == true
        ) {
            return
        }

        // `SelectedApiUpdater` only gates removal on `@FlaggedApi` when BOTH:
        // 1. The item has a configured removed annotation (`hasRemovedAnnotation() == true`, e.g.
        //    `@RemovedFromApi`), NOT a legacy `@removed` Javadoc tag (`@removed` removes the item
        //    unconditionally regardless of any `@FlaggedApi` annotation), AND
        // 2. `@FlaggedApi` is present directly on `item` itself (`FlaggedApiPredicate.test(item)`),
        //    NOT merely on an enclosing non-removed CORE class (an enclosing class's `@FlaggedApi`
        //    does not gate `@RemovedFromApi` on individual members).
        if (item.selectedApi.hasRemovedAnnotation() && FlaggedApiPredicate.test(item)) {
            return
        }

        report(
            UNFLAGGED_API,
            item,
            "Removed API must be flagged with @FlaggedApi: ${item.describe()}",
        )
    }
}

/**
 * Heuristically converts the given string [literal] into a reference to the equivalent
 * `aconfig`-generated `Flags.java` field.
 *
 * @return a pair of the field reference as Java / Kotlin source, and the referenced field item (if
 *   found in [codebase]); or `null` if the literal cannot be converted.
 */
private fun aconfigFlagLiteralToFieldOrNull(
    codebase: Codebase,
    literal: String,
): Pair<String, FieldItem?>? {
    if (literal.contains('/')) {
        return null
    }
    val parts = literal.split('.')

    val flag = parts.lastOrNull() ?: return null
    val flagField = "FLAG_" + flag.uppercase()
    val pkg = parts.dropLast(1).joinToString(separator = ".")
    val className = "$pkg.Flags"
    val fieldSource = "$className.$flagField"

    val clazzOrNull = codebase.findClass(className)
    val fieldOrNull =
        clazzOrNull?.findField(flagField, includeSuperClasses = true, includeInterfaces = true)
    return fieldSource to fieldOrNull
}

/** [FilterPredicate] that matches items annotated with `@FlaggedApi`. */
private object FlaggedApiPredicate : FilterPredicate() {
    override fun test(t: SelectableItem): Boolean =
        t.modifiers.hasAnnotation { it.qualifiedName == ANDROID_FLAGGED_API }
}
