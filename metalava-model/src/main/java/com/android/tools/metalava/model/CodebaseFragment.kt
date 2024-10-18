/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.snapshot.CodebaseSnapshotTaker
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor

/**
 * Encapsulates [codebase] to visit and a [visitorFactory] that if given a [DelegatedVisitor] will
 * return an [ItemVisitor] that can be used to visit some fragment of the [codebase].
 */
abstract class CodebaseFragment private constructor() {

    /** The [Codebase] whose fragment will be visited. */
    abstract val codebase: Codebase

    /**
     * A factory for creating an [ItemVisitor] that delegates to a [DelegatedVisitor].
     *
     * The [ItemVisitor] is used to determine which parts of [codebase] are considered to be defined
     * within and emitted from this fragment.
     */
    protected abstract val visitorFactory: (DelegatedVisitor) -> ItemVisitor

    /**
     * Create an [ItemVisitor] that will visit this fragment and delegate its contents to
     * [delegate].
     */
    fun createVisitor(delegate: DelegatedVisitor) = visitorFactory(delegate)

    /**
     * Return a [CodebaseFragment] that will take a snapshot of this [CodebaseFragment].
     *
     * @param referenceVisitorFactory a factory for creating an [ItemVisitor] that delegates to a
     *   [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase] will
     *   be referenced from within but not emitted from the snapshot.
     */
    fun snapshotIncludingRevertedItems(
        referenceVisitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ): CodebaseFragment {
        return LazyCodebaseFragment(
            {
                CodebaseSnapshotTaker.takeSnapshot(
                    codebase,
                    definitionVisitorFactory = visitorFactory,
                    referenceVisitorFactory = referenceVisitorFactory,
                )
            },
            ::EmittableDelegatingVisitor,
        )
    }

    /** Visit this fragment, delegating to [delegate]. */
    fun accept(delegate: DelegatedVisitor) {
        val visitor = visitorFactory(delegate)
        codebase.accept(visitor)
    }

    companion object {
        /**
         * Create a [CodebaseFragment] from an existing [Codebase].
         *
         * @param factory a factory for creating an [ItemVisitor] that delegates to a
         *   [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase]
         *   are considered to be defined within and emitted from this fragment.
         */
        fun create(
            codebase: Codebase,
            factory: (DelegatedVisitor) -> ItemVisitor,
        ): CodebaseFragment = ExistingCodebaseFragment(codebase, factory)
    }

    /** A [CodebaseFragment] of an existing [Codebase]. */
    private class ExistingCodebaseFragment(
        override val codebase: Codebase,
        override val visitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ) : CodebaseFragment()

    /** A [CodebaseFragment] of a [Codebase] that will be provided lazily. */
    private class LazyCodebaseFragment(
        codebaseProvider: () -> Codebase,
        override val visitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ) : CodebaseFragment() {

        override val codebase by lazy(LazyThreadSafetyMode.NONE) { codebaseProvider() }
    }
}
