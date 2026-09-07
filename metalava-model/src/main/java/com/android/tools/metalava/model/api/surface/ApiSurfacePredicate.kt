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

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.SelectableItem

/** Factory for creating [FilterPredicate] instances based on [ApiSurface]s and [ApiVariant]s. */
object ApiSurfacePredicate {
    /** Singleton instance of [WholeApiPredicate]. */
    private val WHOLE_API_PREDICATE: FilterPredicate = WholeApiPredicate()

    /**
     * Return a [FilterPredicate] that matches any item that belongs to at least one [ApiVariant]
     * across the whole API surface.
     *
     * Only matches items for which [SelectableItem.emit] is true, filtering out non-emittable items
     * such as external classpath dependencies (e.g. `java.lang.Object`) that are not part of the
     * emitted API even if they have been assigned API variants during traversal.
     */
    fun wholeApiPredicate() = WHOLE_API_PREDICATE

    /**
     * A [FilterPredicate] that matches an item if it belongs to at least one [ApiVariant] across
     * all [ApiSurface]s.
     *
     * Checks [SelectableItem.emit] first because:
     * 1. Only items belonging to the codebase being emitted (`emit == true`) can be part of the API
     *    surface. External classpath dependencies (e.g. `java.lang.Object`) have `emit == false`
     *    and must be excluded even if they were assigned [ApiVariant]s during traversal.
     * 2. Checking `emit` first acts as a fast filter that avoids inspecting
     *    [SelectableItem.selectedApi] on non-emittable items.
     */
    private class WholeApiPredicate : FilterPredicate {
        override fun test(t: SelectableItem) = t.emit && t.selectedApi.itemApiVariants.isNotEmpty()
    }
}
