/*
 * Copyright (C) 2023 The Android Open Source Project
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

@file:Suppress("PrivatePropertyName", "MayBeConstant")

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.text.CustomizableProperty.Companion.JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.JAVA_SEALED_CLASSES
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.STYLE

private val FILE_FORMAT_PROPERTY_NAMES =
    listOf(
        "add-additional-overrides",
        "flagged-api-inheritance",
        "include-default-parameter-values",
        "include-type-use-annotations",
        "java-record-classes",
        "java-sealed-classes",
        "kotlin-name-type-order",
        "kotlin-style-nulls",
        "migrating",
        "name",
        "normalize-abstract-modifier",
        "normalize-final-modifier",
        "overloaded-method-order",
        "sort-whole-extends-list",
        "strip-java-lang-prefix",
        "style",
        "surface",
        "type-argument-spacing",
    )

val FILE_FORMAT_PROPERTIES = FILE_FORMAT_PROPERTY_NAMES.joinToString { "'$it'" }

val FORMAT_V5_WITH_JAVA_STYLE = FileFormat.V5.buildCopy { this[STYLE] = FileFormat.NamedStyle.JAVA }

val FORMAT_V6_WITH_JAVA_STYLE = FileFormat.V6.buildCopy { this[STYLE] = FileFormat.NamedStyle.JAVA }

val FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES =
    FORMAT_V6_WITH_JAVA_STYLE.buildCopy { this[JAVA_RECORD_CLASSES] = false }

val FORMAT_V6_WITH_JAVA_SEALED_CLASSES =
    FORMAT_V6_WITH_JAVA_STYLE.buildCopy { this[JAVA_SEALED_CLASSES] = true }

val FORMAT_V6_WITHOUT_JAVA_SEALED_CLASSES =
    FORMAT_V6_WITH_JAVA_STYLE.buildCopy { this[JAVA_SEALED_CLASSES] = false }
