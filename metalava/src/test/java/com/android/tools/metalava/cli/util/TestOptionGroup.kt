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

package com.android.tools.metalava.cli.util

import com.android.tools.metalava.ARG_API_SURFACE
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.testing.TemporaryFolderOwner
import org.intellij.lang.annotations.Language

/**
 * Encapsulates a set of CLI options for a test and validations to run after the test based on the
 * option values.
 */
abstract class TestOptionGroup {
    /** The CLI options for the test. */
    abstract val args: Array<String>

    /** Validation to run after the test command has executed. */
    abstract fun check()
}

/** Represents a set of [TestOptionGroup]s for a single [apiSurface]. */
class SingleSurfaceOptions
private constructor(
    val apiSurface: KnownApiSurface,
    private vararg val optionGroups: TestOptionGroup,
) {
    /** CLI arguments needed to run `SingleSurfaceCommand` for this [apiSurface]. */
    val args = buildList {
        add("single-surface")
        add(ARG_API_SURFACE)
        add(apiSurface.surface)
        for (optionGroup in optionGroups) {
            addAll(optionGroup.args)
        }
    }

    /** Validates that all expectations are met. */
    fun check() {
        for (optionGroup in optionGroups) {
            optionGroup.check()
        }
    }

    companion object {
        /** Creates [SingleSurfaceOptions] for an [apiSurface] based on the provided inputs. */
        fun TemporaryFolderOwner.optionsForSurface(
            apiSurface: KnownApiSurface,
            @Language("TEXT") expectedApiSignature: String? = null,
        ): SingleSurfaceOptions {
            return SingleSurfaceOptions(
                apiSurface,
                signatureOptions(
                    expectedApiSignature = expectedApiSignature,
                    name = apiSurface.surface,
                )
            )
        }
    }
}
