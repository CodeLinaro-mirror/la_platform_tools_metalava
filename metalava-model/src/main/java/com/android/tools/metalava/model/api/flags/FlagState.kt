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

package com.android.tools.metalava.model.api.flags

/**
 * Tracks the effective `@FlaggedApi` addition state of an API element (and its enclosing classes)
 * or a conditional documentation branch in a target API surface, ordered by increasing permanence
 * in the API (`Reverted < Flagged < Finalized`).
 */
sealed interface AdditionFlagState : Comparable<AdditionFlagState> {
    override fun compareTo(other: AdditionFlagState): Int = rank.compareTo(other.rank)

    /**
     * The element or conditional branch is gated on an addition flag with [ApiFlagAction.REVERT],
     * excluding it from the API surface.
     *
     * @property flags The reverted flag name(s) excluding this element or conditional branch.
     */
    data class Reverted(val flags: Set<String>) : AdditionFlagState

    /**
     * The element (or an enclosing class in an included surface) or conditional branch is guarded
     * by one or more unfinalized ([ApiFlagAction.KEEP]) addition flags.
     *
     * All [Flagged] instances share the same permanence rank (`compareTo == 0`) regardless of their
     * [flags] sets because during trunk development all active flags have [ApiFlagAction.KEEP];
     * cross-flag dependencies are validated when individual flags transition to
     * [ApiFlagAction.REVERT] or [ApiFlagAction.FINALIZE] in the release flag configuration
     * (matching `SelectedApiUpdater.checkFlaggedApi`).
     *
     * @property flags The unfinalized addition flag name(s) guarding this element or branch.
     */
    data class Flagged(val flags: Set<String>) : AdditionFlagState

    /**
     * The element or conditional branch is unconditionally present with respect to addition
     * (unannotated, previously released, or all addition flags have [ApiFlagAction.FINALIZE]).
     */
    data object Finalized : AdditionFlagState

    /**
     * Combine this state with an enclosing class's or conditional documentation context's [other]
     * state.
     *
     * Because an enclosed element or nested conditional branch is only present when both it and its
     * enclosing context are present, the combined state is the minimum (least permanent) of the
     * two, unioning flag names when both have the same rank.
     */
    infix fun combineWith(other: AdditionFlagState): AdditionFlagState =
        when {
            // The strictly less permanent state dominates.
            this < other -> this
            other < this -> other

            // Equal non-finalized ranks union their flag sets.
            this is Reverted && other is Reverted -> Reverted(this.flags + other.flags)
            this is Flagged && other is Flagged -> Flagged(this.flags + other.flags)
            else -> Finalized
        }
}

/** Numeric permanence rank (`0 = Reverted < 1 = Flagged < 2 = Finalized`) for [compareTo]. */
private val AdditionFlagState.rank: Int
    get() =
        when (this) {
            is AdditionFlagState.Reverted -> 0
            is AdditionFlagState.Flagged -> 1
            AdditionFlagState.Finalized -> 2
        }

/**
 * Tracks the effective `@FlaggedApi` state of an API element (including flags inherited from its
 * visible enclosing classes) or a conditional documentation branch (such as `{@if (flag(...))}`).
 *
 * Constructed via [FlagState.invoke] or [additionFlag] so canonical states ([NONE]) always reuse
 * their singleton instances for fast `===` checks.
 */
class FlagState
private constructor(
    val addition: AdditionFlagState,
) {
    /**
     * `true` if an API element or conditional documentation branch with this state is included in
     * the API surface (not [AdditionFlagState.Reverted]).
     */
    val isIncluded: Boolean
        get() = addition !is AdditionFlagState.Reverted

    /**
     * Combine this state with an enclosing class's or enclosing conditional documentation branch's
     * [other] state (logical `&&` / minimum permanence).
     */
    infix fun combineWith(other: FlagState): FlagState {
        // Fast path when either operand has no active flags.
        if (other === NONE) return this
        if (this === NONE) return other
        return invoke(
            addition = addition combineWith other.addition,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is FlagState && addition == other.addition)

    override fun hashCode(): Int = addition.hashCode()

    override fun toString(): String = "FlagState(addition=$addition)"

    companion object {
        /** Singleton [FlagState] with no active flags (`Finalized`). */
        val NONE = FlagState(AdditionFlagState.Finalized)

        /** Obtain a canonical [FlagState] for [addition]. */
        operator fun invoke(
            addition: AdditionFlagState = AdditionFlagState.Finalized,
        ): FlagState =
            if (addition === AdditionFlagState.Finalized) {
                NONE
            } else {
                FlagState(addition)
            }

        /**
         * Resolve a positive/addition flag guard (`@FlaggedApi(flagName)` or `{@if
         * (flag(flagName))}`).
         */
        fun additionFlag(flagName: String, action: ApiFlagAction?): FlagState =
            when (action) {
                ApiFlagAction.REVERT ->
                    FlagState(addition = AdditionFlagState.Reverted(setOf(flagName)))
                // Unconfigured flags (`action == null`) are treated conservatively as unfinalized.
                null,
                ApiFlagAction.KEEP ->
                    FlagState(addition = AdditionFlagState.Flagged(setOf(flagName)))
                ApiFlagAction.FINALIZE -> NONE
            }
    }
}
