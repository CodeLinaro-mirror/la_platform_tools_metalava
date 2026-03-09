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

package com.android.tools.metalava.model

/**
 * An extension of [TypeParameterItem] that is used when initially constructing [TypeParameterItem].
 *
 * Exposes the `var` [bounds] to be used in the second stage of the two stage [TypeParameterItem]
 * creation process to the set the [bounds] for a [TypeParameterItem].
 */
interface SkeletonTypeParameterItem : TypeParameterItem {
    /** Used to initialize the bounds after creation of the [TypeParameterItem]. */
    var bounds: List<BoundsTypeItem>
}
