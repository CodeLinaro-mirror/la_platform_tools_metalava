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

import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.api.surface.ApiVariant

/**
 * A [MutableSelectedApi] for a [MemberItem].
 *
 * When an API variant is added to this member, it propagates the variant to its containing class
 * via [MutableClassSelectedApi.propagateFromMember], which updates the class's content variants and
 * propagates up to the containing package.
 */
internal open class MutableMemberSelectedApi(
    item: MemberItem,
) : MutableSelectedApi<MemberItem>(item) {
    override fun addItemApiVariant(value: ApiVariant) {
        super.addItemApiVariant(value)
        // Propagate the member's variant to its containing class.
        val containingClass = item.containingClass()
        (containingClass.selectedApi as? MutableClassSelectedApi)?.propagateFromMember(value)
    }
}
