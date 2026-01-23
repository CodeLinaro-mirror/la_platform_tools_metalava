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

package com.android.tools.metalava

import com.android.tools.metalava.config.ApiFlagActionConfig
import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability
import com.android.tools.metalava.config.ApiFlagActionConfig.Status
import com.android.tools.metalava.config.ApiFlagConfig
import com.android.tools.metalava.config.ApiFlagsConfig
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags

/** Create [ApiFlags] from some source of information about the flags. */
object ApiFlagsCreator {
    /** Create [ApiFlags] from [apiFlagsConfig]. */
    fun createFromConfig(apiFlagsConfig: ApiFlagsConfig?) = apiFlagsConfig?.createApiFlags()

    /** Create [ApiFlags] from [ApiFlagsConfig]. */
    private fun ApiFlagsConfig.createApiFlags(): ApiFlags {
        val unknownFlagAction = unknownFlags?.toApiFlagAction() ?: ApiFlagAction.REVERT
        return ApiFlags(flags.map { config -> config.createApiFlag() }, unknownFlagAction)
    }

    /** Create [Pair] of qualified flag name and [ApiFlag] from [ApiFlagConfig]. */
    private fun ApiFlagConfig.createApiFlag(): ApiFlag {
        val action = toApiFlagAction()

        val qualifiedName = "$pkg.$name"
        return ApiFlag(qualifiedName, action, isExported)
    }

    /** Map from [ApiFlagActionConfig] to [ApiFlagAction]. */
    private fun ApiFlagActionConfig.toApiFlagAction() =
        when (mutability) {
            Mutability.MUTABLE -> ApiFlagAction.KEEP
            Mutability.IMMUTABLE ->
                when (status) {
                    Status.ENABLED -> ApiFlagAction.FINALIZE
                    Status.DISABLED -> ApiFlagAction.REVERT
                }
        }
}
