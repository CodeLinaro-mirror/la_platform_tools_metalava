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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.api.surface.ApiVariant

/**
 * A [MutableSelectedApi] for a [PackageItem].
 *
 * Packages do not belong to an API surface in their own right; instead, they belong to the union of
 * all API surfaces to which their contained classes and members belong. Therefore,
 * [addItemApiVariant] allows adding variants from multiple API surfaces.
 */
internal class MutablePackageSelectedApi(
    item: PackageItem,
) : MutableSelectedApi<PackageItem>(item) {
    override fun addItemApiVariant(value: ApiVariant) {
        // Packages do not belong to an API surface in their own right, but belong to the union of
        // all API surfaces to which their contained classes/members belong.
        itemApiVariants += value
    }
}
