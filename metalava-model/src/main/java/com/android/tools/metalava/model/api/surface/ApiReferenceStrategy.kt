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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.FlagState

/** Distinguishes references in API signatures/code from references inside documentation. */
enum class ReferenceKind {
    /**
     * Reference in code (supertypes, field/method/parameter types, throws lists, annotations). Only
     * CORE targets are valid for CORE referencers.
     */
    CODE,

    /**
     * Reference in documentation (`{@link}`, `@see`, `@throws`). Both CORE and DOC_ONLY targets are
     * valid for CORE/DOC_ONLY referencers.
     */
    DOCUMENTATION,
}

/** Result of checking whether a reference from one item to another item is valid. */
sealed interface ReferenceCheckResult {
    /** True if the reference is valid, false otherwise. */
    val isValid: Boolean

    /** Indicates that the reference is valid. */
    data object Valid : ReferenceCheckResult {
        override val isValid: Boolean = true
    }

    /** Base interface for all invalid reference results. */
    sealed interface Invalid : ReferenceCheckResult {
        override val isValid: Boolean
            get() = false

        /**
         * Short description of why the referenced item is unavailable (e.g. "hidden", "doconly").
         */
        val reasonLabel: String
    }

    /**
     * Target item is not part of any API surface (either `@hide` or package-private/private).
     *
     * @property isPublicOrProtected True if the target item has `public` or `protected` visibility
     *   modifiers (meaning it was excluded via `@hide` rather than language visibility).
     */
    data class Hidden(val isPublicOrProtected: Boolean) : Invalid {
        override val reasonLabel: String
            // If the item is public or protected, it would normally be visible in the API,
            // but has been excluded from this API surface (e.g. via `@hide`), so it is "hidden".
            // Otherwise, it is excluded because of its language-level visibility ("not public").
            get() = if (isPublicOrProtected) "hidden" else "not public"
    }

    /**
     * Target item belongs to an [ApiVariantType.REMOVED] variant (`@removed`) in an included API
     * surface, which is invalid except in [ReferenceKind.CODE] from another `@removed` item.
     */
    data object Removed : Invalid {
        override val reasonLabel: String = "removed"
    }

    /** Target item is `@doconly`, which is invalid when referenced from CORE code. */
    data object DocOnly : Invalid {
        override val reasonLabel: String = "doconly"
    }

    /**
     * Target item is only available in a wider API surface ([toSurface]) than the target API
     * surface ([fromSurface]).
     *
     * @property fromSurface The target API surface against which the reference was checked.
     * @property toSurface The wider API surface in which the referenced item is available.
     */
    data class WiderSurface(
        val fromSurface: ApiSurface,
        val toSurface: ApiSurface,
    ) : Invalid {
        override val reasonLabel: String =
            "in a wider API surface (${toSurface.name}) than ${fromSurface.name}"
    }

    /**
     * Target item is guarded by unfinalized `@FlaggedApi` addition flag(s) [additionFlags] while
     * the referencing item and enclosing conditional context are finalized/unflagged.
     *
     * @property additionFlags The unfinalized flag(s) guarding addition of the target item.
     */
    data class UnguardedFlaggedApi(
        val additionFlags: Set<String>,
    ) : Invalid {
        override val reasonLabel: String = "guarded by @FlaggedApi(${additionFlags.joinToString()})"
    }

    /**
     * Target item is scheduled for removal under unfinalized `@FlaggedApi` flag(s) [removalFlags]
     * while the referencing item and enclosing conditional context are retained.
     *
     * @property removalFlags The unfinalized flag(s) under which the target item is scheduled for
     *   removal.
     */
    data class FlaggedForRemoval(
        val removalFlags: Set<String>,
    ) : Invalid {
        override val reasonLabel: String =
            "scheduled for removal by @FlaggedApi(${removalFlags.joinToString()})"
    }
}

/** Strategy for checking whether a reference from one [Item] to a [SelectableItem] is valid. */
interface ApiReferenceStrategy {
    /**
     * Check whether a reference of [kind] from [from] to [to] is valid within [conditionFlagState].
     *
     * @param from The [Item] containing the reference, or `null` when checking against a global
     *   surface without a specific referencing item (e.g. `SdkFileWriter`).
     * @param to The referenced [SelectableItem].
     * @param kind Whether the reference is in [ReferenceKind.CODE] or
     *   [ReferenceKind.DOCUMENTATION].
     * @param conditionFlagState Active conditional [FlagState] enclosing a
     *   [ReferenceKind.DOCUMENTATION] reference (e.g. from `{@if (flag(...))}` in Javadoc); ignored
     *   for [ReferenceKind.CODE] references.
     */
    fun checkReference(
        from: Item?,
        to: SelectableItem,
        kind: ReferenceKind,
        conditionFlagState: FlagState = FlagState.NONE,
    ): ReferenceCheckResult

    /**
     * Convenience boolean helper for callers that only need a valid/invalid check.
     *
     * @param from The [Item] containing the reference, or `null` when checking against a global
     *   surface without a specific referencing item.
     * @param to The referenced [SelectableItem].
     * @param kind Whether the reference is in [ReferenceKind.CODE] or
     *   [ReferenceKind.DOCUMENTATION].
     * @param conditionFlagState Active conditional [FlagState] enclosing a
     *   [ReferenceKind.DOCUMENTATION] reference (e.g. from `{@if (flag(...))}` in Javadoc); ignored
     *   for [ReferenceKind.CODE] references.
     */
    fun isValidReference(
        from: Item?,
        to: SelectableItem,
        kind: ReferenceKind,
        conditionFlagState: FlagState = FlagState.NONE,
    ): Boolean = checkReference(from, to, kind, conditionFlagState).isValid
}
