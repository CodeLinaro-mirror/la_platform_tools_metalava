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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.FixedFieldValue

interface FieldValue {
    fun initialValue(requireConstant: Boolean): Any?

    /**
     * Creates a snapshot of this.
     *
     * The default implementation assumes that this is either dependent on a model or the codebase
     * and so creates a new [FixedFieldValue] based on the functions above.
     */
    fun snapshot(): FieldValue = FixedFieldValue(initialValue(true), initialValue(false))
}
