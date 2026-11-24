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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType

/**
 * Types of APIs that can be processed.
 *
 * This correlates closely with the [ApiVariantType] type except while that relates to individual
 * [ApiVariant]s this relates to the whole API.
 */
enum class ApiType(
    /**
     * The [ApiVariantType]s of items to emit for this [ApiType].
     *
     * Also provides the default for [referenceVariantTypes].
     */
    val emitVariantTypes: List<ApiVariantType>,

    /**
     * The [ApiVariantType]s that can be referenced by APIs of this [ApiType] across the target API
     * surface and any surfaces it extends.
     *
     * Defaults to [emitVariantTypes].
     */
    val referenceVariantTypes: List<ApiVariantType> = emitVariantTypes,
) {
    /**
     * The core API, i.e. the core part used by apps.
     *
     * It emits and can reference [SelectableItem]s with [ApiVariantType.CORE] [ApiVariant]s.
     */
    CORE(
        emitVariantTypes = listOf(ApiVariantType.CORE),
    ),

    /**
     * Parts of the API that used to be in [CORE] but have since been removed.
     *
     * It emits [SelectableItem]s with [ApiVariantType.REMOVED] [ApiVariant]s and can reference
     * those with either [ApiVariantType.CORE] or [ApiVariantType.REMOVED] [ApiVariant]s.
     */
    REMOVED(
        emitVariantTypes = listOf(ApiVariantType.REMOVED),
        referenceVariantTypes = listOf(ApiVariantType.CORE, ApiVariantType.REMOVED),
    ),

    /**
     * The core API plus additional [SelectableItem]s with [ApiVariantType.DOC_ONLY] [ApiVariant]s,
     * i.e. are included only for documentation purposes.
     */
    CORE_PLUS_DOC_ONLY(
        emitVariantTypes = listOf(ApiVariantType.CORE, ApiVariantType.DOC_ONLY),
    ),
}
