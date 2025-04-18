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

package com.android.tools.metalava.flag

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.optionalFlagName

/**
 * Produces an [ApiFlagReport] by visiting the [Codebase] and collating the names of all the flags
 * used and then querying their status.
 */
object ApiFlagReportProducer : BaseItemVisitor(visitParameterItems = false) {
    /** The set of all the flag names. */
    private val allFlagNames = mutableSetOf<String>()

    /**
     * Visitor for a [SelectableItem] that may be annotated with an [ANDROID_FLAGGED_API]
     * annotation. Extracts the flag name if available and adds it to the [allFlagNames] set.
     */
    override fun visitSelectableItem(item: SelectableItem) {
        val flaggedApiAnnotation = item.modifiers.findAnnotation(ANDROID_FLAGGED_API) ?: return
        val flagName = flaggedApiAnnotation.optionalFlagName ?: return
        allFlagNames += flagName
    }

    /**
     * Produce an [ApiFlagReport] by extracting all the flag names used in [codebase] and then
     * finding their status from [apiFlags].
     */
    fun produceFlagReport(codebase: Codebase, apiFlags: ApiFlags): ApiFlagReport {
        // Gather the names of all the flags used in this codebase.
        codebase.accept(this)

        val flagStatusByName =
            allFlagNames.sorted().associateWith { flagName -> apiFlags.byQualifiedName[flagName] }
        return ApiFlagReport(flagStatusByName)
    }
}
