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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner

/** A [ModelSuiteRunner] that delegates to [delegate] unless it needs to override something. */
class SnapshotTestSuiteRunner(val delegate: ModelSuiteRunner) : ModelSuiteRunner by delegate {
    override val capabilities: Set<Capability> =
        delegate.capabilities -
            // Snapshot does not support the following capabilities:
            setOf(
                Capability.IMPORTS,
                Capability.PACKAGE_HTML_FILES,
                Capability.HIDDEN_ITEMS,
                Capability.API_VARIANT_SELECTORS,
            )
}
