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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem

/**
 * Enumeration of the different sites where [Value]s can be used and which have unique restrictions.
 */
enum class ValueUseSite {
    /**
     * Represents either [AnnotationAttribute.value] or [MethodItem.defaultValue] as they both allow
     * all [Value]s.
     */
    ANNOTATION,
    /** Represents [FieldItem.constantValue] as it only allows [ConstantValue]s. */
    FIELD,
}
