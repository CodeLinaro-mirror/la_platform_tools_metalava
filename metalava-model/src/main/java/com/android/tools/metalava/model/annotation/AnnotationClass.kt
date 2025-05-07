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

package com.android.tools.metalava.model.annotation

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind

/**
 * Encapsulates information extracted from a [ClassItem] whose [ClassItem.classKind] is
 * [ClassKind.ANNOTATION_TYPE].
 */
interface AnnotationClass {
    /**
     * The retention of the associated [ClassItem], if none is specified then uses a language
     * specific default.
     */
    val retention: AnnotationRetention

    /** Get the annotation defaults for the associated [ClassItem]. */
    val defaults: AnnotationDefaults
}
