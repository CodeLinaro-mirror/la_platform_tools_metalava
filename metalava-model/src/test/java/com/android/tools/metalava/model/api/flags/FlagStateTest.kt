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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

/** Unit tests for [FlagState], [AdditionFlagState], and [RemovalFlagState]. */
class FlagStateTest {
    @Test
    fun `Test additionFlag factory and isIncluded`() {
        // Reverted addition flags exclude the item from the API surface.
        val reverted = FlagState.additionFlag("test.pkg.f1", ApiFlagAction.REVERT)
        assertEquals(
            FlagState(addition = AdditionFlagState.Reverted(setOf("test.pkg.f1"))),
            reverted
        )
        assertFalse(reverted.isIncluded)

        // Kept and unconfigured addition flags keep the item guarded in the API surface.
        val kept = FlagState.additionFlag("test.pkg.f2", ApiFlagAction.KEEP)
        assertEquals(FlagState(addition = AdditionFlagState.Flagged(setOf("test.pkg.f2"))), kept)
        assertTrue(kept.isIncluded)

        val unconfigured = FlagState.additionFlag("test.pkg.f3", null)
        assertEquals(
            FlagState(addition = AdditionFlagState.Flagged(setOf("test.pkg.f3"))),
            unconfigured,
        )
        assertTrue(unconfigured.isIncluded)

        // Finalized addition flags canonicalize to `FlagState.NONE`.
        val finalized = FlagState.additionFlag("test.pkg.f4", ApiFlagAction.FINALIZE)
        assertSame(FlagState.NONE, finalized)
        assertTrue(finalized.isIncluded)
    }

    @Test
    fun `Test AdditionFlagState ordering and combineWith`() {
        val reverted1 = FlagState.additionFlag("f1", ApiFlagAction.REVERT)
        val reverted2 = FlagState.additionFlag("f2", ApiFlagAction.REVERT)
        val flagged1 = FlagState.additionFlag("f3", ApiFlagAction.KEEP)
        val flagged2 = FlagState.additionFlag("f4", ApiFlagAction.KEEP)

        // Permanence ordering: `Reverted < Flagged < Finalized`.
        assertTrue(reverted1.addition < flagged1.addition)
        assertTrue(flagged1.addition < AdditionFlagState.Finalized)

        // `NONE` is the identity element for `combineWith`, and `FlagState()` canonicalizes to
        // `FlagState.NONE`.
        assertSame(flagged1, flagged1 combineWith FlagState.NONE)
        assertSame(flagged1, FlagState.NONE combineWith flagged1)
        assertSame(FlagState.NONE, FlagState())
        assertSame(FlagState.NONE, FlagState() combineWith FlagState())

        // Combining equal ranks unions flag sets; combining different ranks picks the less
        // permanent state.
        assertEquals(
            FlagState(addition = AdditionFlagState.Flagged(setOf("f3", "f4"))),
            flagged1 combineWith flagged2,
        )
        assertEquals(reverted1, reverted1 combineWith flagged1)
        assertEquals(reverted1, flagged1 combineWith reverted1)
        assertEquals(
            FlagState(addition = AdditionFlagState.Reverted(setOf("f1", "f2"))),
            reverted1 combineWith reverted2,
        )
    }

    @Test
    fun `Test removalFlag factory and isIncluded`() {
        // Finalized removal flags and `FlagState(removal = Removed)` canonicalize to
        // `FlagState.REMOVED`.
        val finalized = FlagState.removalFlag("test.pkg.r1", ApiFlagAction.FINALIZE)
        assertSame(FlagState.REMOVED, finalized)
        assertSame(FlagState.REMOVED, FlagState(removal = RemovalFlagState.Removed))
        assertFalse(finalized.isIncluded)

        // Kept and unconfigured removal flags keep the item in CORE as `FlaggedForRemoval`.
        val kept = FlagState.removalFlag("test.pkg.r2", ApiFlagAction.KEEP)
        assertEquals(
            FlagState(removal = RemovalFlagState.FlaggedForRemoval(setOf("test.pkg.r2"))),
            kept,
        )
        assertTrue(kept.isIncluded)

        val unconfigured = FlagState.removalFlag("test.pkg.r3", null)
        assertEquals(
            FlagState(removal = RemovalFlagState.FlaggedForRemoval(setOf("test.pkg.r3"))),
            unconfigured,
        )
        assertTrue(unconfigured.isIncluded)

        // Reverted removal flags cancel removal and canonicalize to `FlagState.NONE`.
        val reverted = FlagState.removalFlag("test.pkg.r4", ApiFlagAction.REVERT)
        assertSame(FlagState.NONE, reverted)
        assertTrue(reverted.isIncluded)
    }

    @Test
    fun `Test RemovalFlagState ordering and combineWith`() {
        val removal1 = FlagState.removalFlag("r1", ApiFlagAction.KEEP)
        val removal2 = FlagState.removalFlag("r2", ApiFlagAction.KEEP)
        val addition1 = FlagState.additionFlag("f1", ApiFlagAction.KEEP)

        // Permanence ordering: `Removed < FlaggedForRemoval < Retained`.
        assertTrue(RemovalFlagState.Removed < removal1.removal)
        assertTrue(removal1.removal < RemovalFlagState.Retained)

        // Combining scheduled-removal flags unions flag sets; `REMOVED` dominates (preserving
        // canonical `FlagState.REMOVED`), and addition + removal dimensions combine orthogonally.
        assertEquals(
            FlagState(removal = RemovalFlagState.FlaggedForRemoval(setOf("r1", "r2"))),
            removal1 combineWith removal2,
        )
        assertSame(FlagState.REMOVED, FlagState.REMOVED combineWith removal1)
        assertSame(FlagState.REMOVED, removal1 combineWith FlagState.REMOVED)
        assertEquals(
            FlagState(
                addition = AdditionFlagState.Flagged(setOf("f1")),
                removal = RemovalFlagState.FlaggedForRemoval(setOf("r1")),
            ),
            addition1 combineWith removal1,
        )
    }
}
