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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.Item

/**
 * A [BaseItemVisitor] that will delegate to [delegate] only for [Item]'s whose [emit] is `true`.
 *
 * Preserves class nesting as required by the [delegate]'s [DelegatedVisitor.requiresClassNesting]
 * property.
 */
class EmittableDelegatingVisitor(private val delegate: DelegatedVisitor) :
    NonFilteringDelegatingVisitor(delegate) {

    override fun skip(item: Item): Boolean {
        return !item.emit
    }
}
