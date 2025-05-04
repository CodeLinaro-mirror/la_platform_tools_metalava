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
import com.android.tools.metalava.model.Assertions.Companion.assertAttribute
import com.android.tools.metalava.model.Assertions.Companion.assertField
import com.android.tools.metalava.model.Assertions.Companion.assertMethod
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.testsuite.value.BaseCommonParameterizedValueTest.TestCaseContext
import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.ATTRIBUTE_NAME
import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.FIELD_NAME
import com.android.tools.metalava.model.value.ValueUseSite
import java.util.EnumSet

/**
 * The possible places where values can be provided.
 *
 * @param valueUseSite the [ValueUseSite] that will replace the [LegacyValueUseSite].
 * @param legacySourceGetter gets the legacy source representation as expected by
 *   [ValueExample.expectedLegacySourceFor].
 * @param legacyValueGetter get the legacy value as expected by
 *   [ValueExample.expectedLegacyValueFor].
 */
enum class LegacyValueUseSite(
    val valueUseSite: ValueUseSite,
    val legacySourceGetter: (TestCaseContext.() -> String?)? = null,
    val legacyValueGetter: (TestCaseContext.() -> Any?)? = null,
) {
    /** The default value specified on an annotation class's method. */
    ATTRIBUTE_DEFAULT_VALUE(
        ValueUseSite.ANNOTATION,
        legacySourceGetter = {
            val annotationMethod = testClassItem.assertMethod(ATTRIBUTE_NAME, "")

            annotationMethod.legacyDefaultValue()
        },
    ),

    /** An annotation attribute value specified in an annotation instance. */
    ATTRIBUTE_VALUE(
        ValueUseSite.ANNOTATION,
        legacySourceGetter = {
            val annotation = testClassItem.modifiers.annotations().first()
            val annotationAttribute = annotation.assertAttribute(ATTRIBUTE_NAME)

            annotationAttribute.legacyValue.toSource()
        },
        legacyValueGetter = {
            val annotation = testClassItem.modifiers.annotations().first()
            val annotationAttribute = annotation.assertAttribute(ATTRIBUTE_NAME)

            annotationAttribute.legacyValue.value()
        }
    ),

    /**
     * An annotation attribute value produced by [AnnotationItem.toSource] called on an annotation
     * instance.
     */
    ANNOTATION_TO_SOURCE(
        ValueUseSite.ANNOTATION,
        legacySourceGetter = {
            // Get the annotation to test.
            val annotation = testClassItem.modifiers.annotations().first()

            // Generate the whole annotation representation, not including default values.
            val wholeAnnotation = annotation.toSource()

            // Extract the value from the whole annotation.
            wholeAnnotation.substringAfter("=").substringBeforeLast(")")
        },
    ),

    /** The value of a field. */
    FIELD_VALUE(
        ValueUseSite.FIELD,
    ),

    /** The value of a field written out by [FieldItem.writeValueWithSemicolon]. */
    FIELD_WRITE_WITH_SEMICOLON(
        ValueUseSite.FIELD,
        legacySourceGetter = {
            val field = testClassItem.assertField(FIELD_NAME)

            // Print the field with semicolon.
            val stringWriter = java.io.StringWriter()
            java.io.PrintWriter(stringWriter).use { writer ->
                field.writeValueWithSemicolon(writer)
            }
            val withSemicolon = stringWriter.toString()

            // Extract the value from the " = ...; // ...." string.
            if (withSemicolon == ";") null
            else withSemicolon.substringAfter(" = ").substringBefore(";")
        },
    ),
}

/**
 * The set of all [LegacyValueUseSite]s.
 *
 * Default for [ValueExample.suitableFor].
 */
internal val allLegacyValueUseSites = EnumSet.allOf(LegacyValueUseSite::class.java)

/**
 * The set of all field [LegacyValueUseSite]s, i.e. [LegacyValueUseSite.FIELD_VALUE] and
 * [LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON].
 */
internal val allFieldLegacyValueUseSites =
    EnumSet.of(LegacyValueUseSite.FIELD_VALUE, LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON)

/**
 * The set of all [LegacyValueUseSite]s except [LegacyValueUseSite.FIELD_VALUE] and
 * [LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON].
 *
 * Stored in [ValueExample.suitableFor] for any [ValueExample] that does not work on fields.
 */
internal val allLegacyValueUseSitesExceptFields =
    allLegacyValueUseSites - allFieldLegacyValueUseSites
