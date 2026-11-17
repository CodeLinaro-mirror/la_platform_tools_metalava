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
 * Tracks the effective `@RemovedFromApi` / `@FlaggedApi` removal state of an API element (and its
 * enclosing classes) or a conditional documentation branch in a target API surface, ordered by
 * increasing permanence in the API (`Removed < FlaggedForRemoval < Retained`).
 */
sealed interface RemovalFlagState : Comparable<RemovalFlagState> {
    override fun compareTo(other: RemovalFlagState): Int = rank.compareTo(other.rank)

    /**
     * The element (or an enclosing class) or conditional branch is excluded by removal (`@removed`
     * or a removal flag with [ApiFlagAction.FINALIZE]).
     */
    data object Removed : RemovalFlagState

    /**
     * The element (or an enclosing class in an included surface) or conditional branch is scheduled
     * for removal by one or more unfinalized ([ApiFlagAction.KEEP]) flags.
     *
     * @property flags The unfinalized removal flag name(s) scheduling removal of this element.
     */
    data class FlaggedForRemoval(val flags: Set<String>) : RemovalFlagState

    /**
     * The element or conditional branch is retained unconditionally with respect to removal (no
     * `@RemovedFromApi`, or its removal flag has [ApiFlagAction.REVERT]).
     */
    data object Retained : RemovalFlagState

    /**
     * Combine this state with an enclosing class's or conditional documentation context's [other]
     * state.
     *
     * Because an enclosed element or nested conditional branch is removed whenever either it or its
     * enclosing context is removed, the combined state is the minimum (least permanent) of the two,
     * unioning flag names when both are [FlaggedForRemoval].
     */
    infix fun combineWith(other: RemovalFlagState): RemovalFlagState =
        when {
            // The strictly less permanent state dominates (`Removed` beats `FlaggedForRemoval`).
            this < other -> this
            other < this -> other

            // Equal `FlaggedForRemoval` ranks union their scheduled-removal flag sets.
            this is FlaggedForRemoval && other is FlaggedForRemoval ->
                FlaggedForRemoval(this.flags + other.flags)
            else -> this
        }
}

/**
 * Numeric permanence rank (`0 = Removed < 1 = FlaggedForRemoval < 2 = Retained`) for [compareTo].
 */
private val RemovalFlagState.rank: Int
    get() =
        when (this) {
            RemovalFlagState.Removed -> 0
            is RemovalFlagState.FlaggedForRemoval -> 1
            RemovalFlagState.Retained -> 2
        }

/**
 * Tracks the effective `@FlaggedApi` state of an API element (including flags inherited from its
 * visible enclosing classes) or a conditional documentation branch (such as `{@if (flag(...))}`).
 *
 * Constructed via [FlagState.invoke], [additionFlag], or [removalFlag] so canonical states ([NONE]
 * and [REMOVED]) always reuse their singleton instances for fast `===` checks.
 */
class FlagState
private constructor(
    val addition: AdditionFlagState,
    val removal: RemovalFlagState,
) {
    /**
     * `true` if an API element or conditional documentation branch with this state is included in
     * the API surface (neither [AdditionFlagState.Reverted] nor [RemovalFlagState.Removed]).
     */
    val isIncluded: Boolean
        get() = addition !is AdditionFlagState.Reverted && removal !is RemovalFlagState.Removed

    /**
     * Combine this state with an enclosing class's or enclosing conditional documentation branch's
     * [other] state (logical `&&` / minimum permanence on both dimensions).
     */
    infix fun combineWith(other: FlagState): FlagState {
        // Fast path when either operand has no active flags.
        if (other === NONE) return this
        if (this === NONE) return other
        return invoke(
            addition = addition combineWith other.addition,
            removal = removal combineWith other.removal,
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is FlagState && addition == other.addition && removal == other.removal)

    override fun hashCode(): Int = 31 * addition.hashCode() + removal.hashCode()

    override fun toString(): String = "FlagState(addition=$addition, removal=$removal)"

    companion object {
        /** Singleton [FlagState] with no active flags (`(Finalized, Retained)`). */
        val NONE = FlagState(AdditionFlagState.Finalized, RemovalFlagState.Retained)

        /**
         * Singleton [FlagState] for elements or branches excluded by removal (`(Finalized,
         * Removed)`).
         */
        val REMOVED = FlagState(AdditionFlagState.Finalized, RemovalFlagState.Removed)

        /** Obtain a canonical [FlagState] for [addition] and [removal]. */
        operator fun invoke(
            addition: AdditionFlagState = AdditionFlagState.Finalized,
            removal: RemovalFlagState = RemovalFlagState.Retained,
        ): FlagState =
            when {
                addition === AdditionFlagState.Finalized && removal === RemovalFlagState.Retained ->
                    NONE
                addition === AdditionFlagState.Finalized && removal === RemovalFlagState.Removed ->
                    REMOVED
                else -> FlagState(addition, removal)
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

        /**
         * Resolve a scheduled-removal flag guard (`@RemovedFromApi @FlaggedApi(flagName)` or a
         * conditional documentation branch active until [flagName] is finalized).
         */
        fun removalFlag(flagName: String, action: ApiFlagAction?): FlagState =
            when (action) {
                // Unconfigured or kept removal flags leave the element in CORE until finalized.
                null,
                ApiFlagAction.KEEP ->
                    FlagState(removal = RemovalFlagState.FlaggedForRemoval(setOf(flagName)))

                // Reverting a removal flag cancels the scheduled removal, retaining the element.
                ApiFlagAction.REVERT -> NONE

                // Finalizing a removal flag moves the element out of CORE into REMOVED.
                ApiFlagAction.FINALIZE -> REMOVED
            }
    }
}
