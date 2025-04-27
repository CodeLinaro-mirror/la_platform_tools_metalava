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
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SourceLanguage

/**
 * Provide support for formatting [Value]s consistently with various legacy string representations.
 *
 * Legacy string representations of values are extremely inconsistent and vary by:
 * * The legacy use site, e.g. [FieldItem.writeValueWithSemicolon], [MethodItem.legacyDefaultValue],
 *   [AnnotationAttribute.legacyValue]. [AnnotationItem.toSource].
 * * The [ClassItem.origin], i.e. sources or jars.
 * * The source language, i.e. Kotlin or Java. Signature files are not a factor because they
 *   preserve what was written into them from sources.
 *
 * The purpose of this is to take all those factors into account so that it can produce the same
 * output from a [Value] as is currently produced by the legacy use site. Ultimately, the plan is to
 * implement the legacy string representations by applying one of these to the [Value].
 *
 * There will be one instance of this created per legacy use site.
 *
 * @param javaSettings the [Settings] to use when formatting a value whose context [MemberItem] is
 *   loaded from Java sources, will also apply to Kotlin sources unless [kotlinSettings] is provided
 *   and [MemberItem]s loaded from a Jar when [jarSettings] is not provided.
 * @param kotlinSettings the [Settings] to use when formatting a value whose context [MemberItem] is
 *   loaded from Kotlin sources; defaults to [javaSettings].
 * @param jarSettings the [Settings] to use when formatting a value whose context [MemberItem] is
 *   loaded from a jar; defaults to [javaSettings].
 */
class LegacyValueFormatter(
    private val javaSettings: Settings,
    private val kotlinSettings: Settings = javaSettings,
    private val jarSettings: Settings = javaSettings,
) {
    /** Settings that affect the formatting of a [Value]. */
    data class Settings(
        /** The configuration that is used when calling [Value.toValueString]. */
        val valueStringConfiguration: ValueStringConfiguration = ValueStringConfiguration.DEFAULT,

        /**
         * A map from [Value] to the string representation to use in place of the [Value]'s string
         * representation.
         *
         * Used to provide special string representations for specific values, e.g. special floating
         * point numbers.
         */
        val stringReplacement: Map<Value, String> = emptyMap(),
    )

    /**
     * Format [value] within the optional [context].
     *
     * The [context] must be provided as follows:
     * * When formatting a [Value] from [FieldItem.constantValue] it must be the [FieldItem].
     * * When formatting a [Value] from [MethodItem.defaultValue] it must be the [MethodItem].
     *
     * This is not suitable for formatting a [Value] from [AnnotationAttribute.value].
     */
    fun format(value: Value, context: MemberItem): String {
        // Select the settings to use based on whether it is from the classpath (a jar) or sources.
        val settings =
            when {
                context.containingClass().origin == ClassOrigin.CLASS_PATH -> jarSettings
                context.sourceLanguage == SourceLanguage.KOTLIN -> kotlinSettings
                else -> javaSettings
            }

        // If there is a string replacement then return it.
        settings.stringReplacement[value]?.let { replacement ->
            return replacement
        }

        // Fallback to just using the default value representation according to the settings.
        return value.toValueString(settings.valueStringConfiguration)
    }
}
