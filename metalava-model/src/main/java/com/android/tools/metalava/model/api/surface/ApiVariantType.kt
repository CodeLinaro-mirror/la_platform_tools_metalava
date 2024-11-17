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

package com.android.tools.metalava.model.api.surface

import com.android.tools.metalava.model.Item

/**
 * The types of API variants.
 *
 * Each of these refers to a different variant of the API, where each variant has a unique set of
 * criteria that determines which [Item]s in the API are part of the variant. e.g. [DOC_ONLY] only
 * includes [Item]s that have `@doconly` specified. The intent is that every traversal of the API,
 * e.g. when generating output files, will just specify the set of variants that it needs to visit.
 *
 * e.g. When generating the public API it will visit [CORE] in [ApiSurfaces.main] and there will be
 * no [ApiSurfaces.base]. When generating the system API delta it will also visit [CORE] in
 * [ApiSurfaces.main] even though there will be an [ApiSurfaces.base] (which represents the public
 * API).
 *
 * Similarly, when generate the removed public API it will visit [REMOVED] in [ApiSurfaces.main] and
 * there will be no [ApiSurfaces.base]. When generating the system API delta it will also visit
 * [REMOVED] in [ApiSurfaces.main] even though there will be an [ApiSurfaces.base] (which represents
 * the public API).
 *
 * When generating the public stubs it will visit [CORE] in [ApiSurfaces.main]. However, when
 * generating the system stubs (which have to include the public stubs) it will visit [CORE] in both
 * [ApiSurfaces.main] and [ApiSurfaces.base].
 *
 * When generating documentation stubs it will visit the same set as for stubs plus [DOC_ONLY] for
 * each of the [ApiSurface]s.
 */
enum class ApiVariantType(
    /**
     * Used in [ApiVariantSet.toString] to reduce the size of the string representation when
     * debugging.
     */
    val shortCode: Char,
) {
    /** The core API that is used everywhere. */
    CORE(shortCode = 'C'),

    /** The removed API items. */
    REMOVED(shortCode = 'R'),

    /** Doc stub only items. */
    DOC_ONLY(shortCode = 'D'),
}
