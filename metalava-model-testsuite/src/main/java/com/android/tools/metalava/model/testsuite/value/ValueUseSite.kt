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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.FieldItem
import java.util.EnumSet

/** The possible places where values can be provided. */
enum class ValueUseSite {
    /** The default value specified on an annotation class's method. */
    ATTRIBUTE_DEFAULT_VALUE,

    /** An annotation attribute value specified in an annotation instance. */
    ATTRIBUTE_VALUE,

    /**
     * An annotation attribute value produced by [AnnotationItem.toSource] called on an annotation
     * instance.
     */
    ANNOTATION_TO_SOURCE,

    /** The value of a field. */
    FIELD_VALUE,

    /** The value of a field written out by [FieldItem.writeValueWithSemicolon]. */
    FIELD_WRITE_WITH_SEMICOLON,
}

/**
 * The set of all [ValueUseSite]s.
 *
 * Default for [ValueExample.suitableFor].
 */
internal val allValueUseSites = EnumSet.allOf(ValueUseSite::class.java)

/**
 * The set of all [ValueUseSite]s except [ValueUseSite.FIELD_VALUE] and
 * [ValueUseSite.FIELD_WRITE_WITH_SEMICOLON].
 *
 * Stored in [ValueExample.suitableFor] for any [ValueExample] that does not work on fields.
 */
internal val allValueUseSitesExceptFields =
    allValueUseSites - ValueUseSite.FIELD_VALUE - ValueUseSite.FIELD_WRITE_WITH_SEMICOLON
