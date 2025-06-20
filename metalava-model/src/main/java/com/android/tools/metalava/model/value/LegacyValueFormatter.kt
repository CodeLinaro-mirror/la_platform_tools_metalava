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

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.ClassContentItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.javaEscapeString
import java.lang.StringBuilder

/**
 * Provide support for formatting [Value]s consistently with various legacy string representations.
 *
 * Legacy string representations of values are extremely inconsistent and vary by:
 * * The legacy use site, e.g. [FieldItem.writeValueWithSemicolon], [MethodItem.legacyDefaultValue],
 *   what was `AnnotationItem.toSource(...)`.
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
    javaSettings: Settings,
    kotlinSettings: Settings = javaSettings,
    jarSettings: Settings = javaSettings,
) {
    /**
     * Copy the [javaSettings] and bind it to this [LegacyValueFormatter] so that
     * [ appendFormattedValue] will be called for nested [Value]s.
     */
    private val javaSettings = javaSettings.bindTo(this)

    /**
     * Copy the [kotlinSettings] and bind it to this [LegacyValueFormatter] so that
     * [ appendFormattedValue] will be called for nested [Value]s.
     */
    private val kotlinSettings = kotlinSettings.bindTo(this)

    /**
     * Copy the [jarSettings] and bind it to this [LegacyValueFormatter] so that
     * [ appendFormattedValue] will be called for nested [Value]s.
     */
    private val jarSettings = jarSettings.bindTo(this)

    /**
     * Determines whether to inline a [FieldReferenceValue] with its [ConstantValue], if available.
     *
     * If the field does not have a [ConstantValue], e.g. because it is an enum, unresolvable, or
     * not a constant field then just format it as a normal field reference. That is probably wrong
     * but the formatter MUST always write something out.
     */
    enum class InlineFieldValue {
        /** Always inline the [FieldReferenceValue], if possible. */
        ALWAYS,

        /**
         * Only inline the [FieldReferenceValue], if it is hidden or removed (as determined by
         * [SelectableItem.isHiddenOrRemoved]).
         */
        WHEN_HIDDEN_OR_REMOVED,

        /**
         * Only inline the [FieldReferenceValue], if it is inaccessible, i.e. hidden, removed or not
         * public.
         */
        WHEN_INACCESSIBLE,
    }

    /** Settings that affect the formatting of a [Value]. */
    data class Settings(
        /** The configuration that is used as the basis for [boundConfiguration]. */
        private val valueStringConfiguration: ValueStringConfiguration =
            ValueStringConfiguration.DEFAULT,

        /**
         * A map from [Value] to the string representation to use in place of the [Value]'s string
         * representation.
         *
         * Used to provide special string representations for specific values, e.g. special floating
         * point numbers.
         */
        val stringReplacement: Map<Value, String> = emptyMap(),

        /** The lambda that will be invoked to append nested [Value]s. */
        val nestedValueAppender: (Value, StringBuilder, Settings) -> Unit = { value, builder, _ ->
            value.appendValueStringTo(builder)
        },

        /** Determines whether to inline a [FieldReferenceValue]. */
        val inlineFields: InlineFieldValue = InlineFieldValue.WHEN_INACCESSIBLE,

        /**
         * If `true` then just use the [Number.toString] method for [LiteralValue.underlyingValue]s
         * that are [Number]s.
         */
        val dropLongAndFloatTypeSuffix: Boolean = false,

        /**
         * If `true` then just use double quotes for [CharValue] not single quotes, which is the
         * default.
         */
        val useDoubleQuotesForChar: Boolean = false,
    ) {
        /**
         * The configuration that must be used when calling [Value.toValueString].
         *
         * This is a copy of [valueStringConfiguration] with its
         * [ValueStringConfiguration.nestedValueAppender] set to redirect the call to
         * [nestedValueAppender].
         */
        val boundConfiguration =
            valueStringConfiguration.copy(
                nestedValueAppender = { value, builder, _ ->
                    nestedValueAppender(value, builder, this)
                }
            )

        /**
         * Create a copy of this which delegates calls to [nestedValueAppender] to
         * [nestedFormatter]'s [LegacyValueFormatter.appendFormattedValue] method.
         */
        fun bindTo(nestedFormatter: LegacyValueFormatter): Settings {
            return copy(nestedValueAppender = nestedFormatter::appendFormattedValue)
        }
    }

    /**
     * Format [value] within the [context].
     *
     * The [context] must be provided as follows:
     * * When formatting a [Value] from [FieldItem.constantValue] it must be the [FieldItem].
     * * When formatting a [Value] from [MethodItem.defaultValue] it must be the [MethodItem].
     *
     * This is not suitable for formatting a [Value] from [AnnotationAttribute.value].
     */
    fun format(value: Value, context: MemberItem): String {
        val settings = selectSettingsForContext(context)
        return format(settings, value)
    }

    /**
     * Select the settings to use based on whether it is from the classpath (a jar) or sources. That
     * determination is made using [context]. If that is `null` then this will use the
     * [javaSettings] by default.
     */
    private fun selectSettingsForContext(context: Item?) =
        when {
            context == null -> javaSettings
            context is ClassContentItem && context.origin == ClassOrigin.CLASS_PATH -> jarSettings
            context.sourceLanguage == SourceLanguage.KOTLIN -> kotlinSettings
            else -> javaSettings
        }

    /** Format [value] according to [settings]. */
    private fun format(settings: Settings, value: Value) = buildString {
        appendFormattedValue(value, this, settings)
    }

    /** Append the formatted [value] to [builder] according to [settings]. */
    private fun appendFormattedValue(value: Value, builder: StringBuilder, settings: Settings) {
        // If there is a string replacement then return it.
        settings.stringReplacement[value]?.let { replacement ->
            builder.append(replacement)
            return
        }

        if (settings.useDoubleQuotesForChar && value is CharValue) {
            val underlyingValue = value.underlyingValue
            builder.append('"').append(javaEscapeString(underlyingValue.toString())).append('"')
            return
        }

        // If the value is a field that should always be inlined, or is unresolvable or inaccessible
        // then use its value, if available. Otherwise, fall back to using it anyway as a value must
        // be formatted.
        val valueToAppend =
            (value as? FieldReferenceValue)?.let { field ->
                when (settings.inlineFields) {
                    // The field should always be inlined, if possible.
                    InlineFieldValue.ALWAYS -> field.asLiteralValue()

                    // The field should be inlined only when it is inaccessible.
                    InlineFieldValue.WHEN_INACCESSIBLE ->
                        if (field.resolve().isAccessible()) field else field.asLiteralValue()

                    // The field should be inlined only when it is hidden or removed.
                    InlineFieldValue.WHEN_HIDDEN_OR_REMOVED ->
                        if (field.resolve()?.isHiddenOrRemoved() != true) field
                        else field.asLiteralValue()
                }
            } ?: value

        // Fallback to just using the default value representation according to the settings. This
        // passes in the [Settings.boundConfiguration] as that has a `nestedValueAppender` that
        // will call back into this method for nested values, i.e. values in an array and attribute
        // values of nested annotations.
        valueToAppend.appendValueStringTo(builder, settings.boundConfiguration)

        if (settings.dropLongAndFloatTypeSuffix) {
            val lastCharIndex = builder.length - 1
            if (
                (valueToAppend is LongValue && builder[lastCharIndex] == 'L') ||
                    (valueToAppend is FloatValue && builder[lastCharIndex] == 'f')
            ) {
                builder.setLength(lastCharIndex)
            }
        }
    }

    /** True if this [FieldItem] is not-null, is not hidden or removed and is public. */
    private fun FieldItem?.isAccessible() = this != null && !isHiddenOrRemoved() && isPublic

    /** Get the annotation specific settings that incorporate [target] and [alwaysInlineFields]. */
    private fun annotationSpecificSetting(
        settings: Settings,
        target: AnnotationTarget,
        alwaysInlineFields: Boolean,
    ) =
        settings.copy(
            valueStringConfiguration =
                // Incorporate the target into the [ValueStringConfiguration]. This uses the
                // [Settings.boundConfiguration] as the [Settings.valueStringConfiguration] is
                // intentionally inaccessible as it must not be used directly. That is not an issue
                // as the `boundConfiguration` is identical to `valueStringConfiguration` apart from
                // the `nestedValueAppender` and that will be updated by [Settings]'s initializer.
                settings.boundConfiguration.copy(
                    annotationQualifiedNameGetter = { annotationItem ->
                        annotationItem.annotationContext.annotationManager.normalizeOutputName(
                            annotationItem.qualifiedName,
                            target
                        )
                    },
                ),
            inlineFields =
                if (alwaysInlineFields) InlineFieldValue.ALWAYS else settings.inlineFields,
        )

    fun appendFormatAnnotation(
        builder: StringBuilder,
        annotationItem: AnnotationItem,
        target: AnnotationTarget,
        context: Item?
    ) {
        val settings = selectSettingsForContext(context)

        val alwaysInlineFields = annotationItem.qualifiedName == ANDROID_FLAGGED_API

        val annotationSpecificSetting =
            annotationSpecificSetting(settings, target, alwaysInlineFields)

        // Append the annotation item.  This passes in the [Settings.boundConfiguration] as that
        // has a `nestedValueAppender` that will call back into [appendFormattedValue] for
        // nested values, i.e. values in an array and attribute values of nested annotations.
        annotationItem.appendAnnotationStringTo(
            builder,
            annotationSpecificSetting.boundConfiguration,
            annotationIsValue = false
        )
    }

    companion object {
        /** Setting for formatting [MethodItem.defaultValue] from Java sources. */
        private val ATTRIBUTE_DEFAULT_JAVA_SETTINGS =
            Settings(
                valueStringConfiguration =
                    ValueStringConfiguration(
                        // Use the source representation of a single array element when formatting.
                        singleArrayElementFormat =
                            @Suppress("DEPRECATION") SingleArrayElementFormat.SOURCE,

                        // Annotation attributes are not sorted in the default values.
                        sortAnnotationAttributes = false,

                        // Some values are treated specially in [MethodItem.defaultValue].
                        specialValues =
                            mapOf(
                                DoubleValue.NaN to "java.lang.Double.NaN",
                                DoubleValue.NEGATIVE_INFINITY to
                                    "java.lang.Double.NEGATIVE_INFINITY",
                                DoubleValue.POSITIVE_INFINITY to
                                    "java.lang.Double.POSITIVE_INFINITY",
                                FloatValue.NaN to "java.lang.Float.NaN",
                                FloatValue.NEGATIVE_INFINITY to "java.lang.Float.NEGATIVE_INFINITY",
                                FloatValue.POSITIVE_INFINITY to "java.lang.Float.POSITIVE_INFINITY",
                            ),

                        // In the source, values that were written as ints were formatted as ints
                        // even if they were `double`, `float`, or `long`.
                        useOriginalValueForNumbers = true,
                    ),
            )

        /** Setting for formatting [MethodItem.defaultValue] from Kotlin sources. */
        private val ATTRIBUTE_DEFAULT_KOTLIN_SETTINGS =
            Settings(
                valueStringConfiguration =
                    ValueStringConfiguration(
                        // Legacy formatting of annotations in Kotlin default methods do not use
                        // spaces in the separator between attribute name and value.
                        annotationAttributeNameValueSeparator =
                            AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,

                        // ClassObjectValues are output using their source expression in Kotlin.
                        classObjectValueFormat = ClassObjectValueFormat.SOURCE,

                        // Use the source representation of a single array element when formatting.
                        singleArrayElementFormat =
                            @Suppress("DEPRECATION") SingleArrayElementFormat.SOURCE,

                        // Annotation attributes are not sorted in the default values.
                        sortAnnotationAttributes = false,

                        // Some values are treated differently in Kotlin in
                        // [MethodItem.defaultValue].
                        specialValues =
                            mapOf(
                                DoubleValue.NaN to "kotlin.jvm.internal.DoubleCompanionObject.NaN",
                                DoubleValue.NEGATIVE_INFINITY to
                                    "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY",
                                DoubleValue.POSITIVE_INFINITY to
                                    "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY",
                                FloatValue.NaN to "kotlin.jvm.internal.FloatCompanionObject.NaN",
                                FloatValue.NEGATIVE_INFINITY to
                                    "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY",
                                FloatValue.POSITIVE_INFINITY to
                                    "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY",
                            ),

                        // In the source, values that were written as ints were formatted as ints
                        // even if they were `double`, `float`, or `long`.
                        useOriginalValueForNumbers = true,

                        // Use Kotlin formatting of values.
                        valueLanguage = ValueLanguage.KOTLIN,
                    ),
                stringReplacement =
                    mapOf(
                        // Ignore an empty array as that is the legacy behavior for method default
                        // values created from Kotlin sources.
                        Value.createArrayValue(emptyList()) to "",
                    ),

                // Method default values from Kotlin sources do not add a type suffix character for
                // long or float.
                dropLongAndFloatTypeSuffix = true,

                // Chars are wrapped in double quotes for method default values created from
                // Kotlin sources.
                useDoubleQuotesForChar = true,
            )

        /** Setting for formatting [MethodItem.defaultValue] from Jar classes. */
        private val ATTRIBUTE_DEFAULT_JAR_SETTINGS =
            Settings(
                valueStringConfiguration =
                    ValueStringConfiguration(
                        // Do not unwrap a single array element when formatting a value from a jar
                        // as they were never unwrapped.
                        singleArrayElementFormat = SingleArrayElementFormat.WRAP,

                        // Annotation attributes are not sorted in the default values.
                        sortAnnotationAttributes = false,

                        // In the jar file special values were always stored as their constant value
                        // so they
                        // were never formatted as their fields.
                        specialValues =
                            mapOf(
                                DoubleValue.NaN to "(0.0/0.0)",
                                DoubleValue.NEGATIVE_INFINITY to "(-1.0/0.0)",
                                DoubleValue.POSITIVE_INFINITY to "(1.0/0.0)",
                                FloatValue.NaN to "(0.0/0.0)",
                                FloatValue.NEGATIVE_INFINITY to "(-1.0/0.0)",
                                FloatValue.POSITIVE_INFINITY to "(1.0/0.0)",
                            ),

                        // In the jar, values are always stored as their actual type so were never
                        // represented as an int.
                        useOriginalValueForNumbers = false,
                    ),
            )

        /** A [LegacyValueFormatter] for formatting [MethodItem.defaultValue]s. */
        val ATTRIBUTE_DEFAULT_FORMATTER =
            LegacyValueFormatter(
                javaSettings = ATTRIBUTE_DEFAULT_JAVA_SETTINGS,
                kotlinSettings = ATTRIBUTE_DEFAULT_KOTLIN_SETTINGS,
                jarSettings = ATTRIBUTE_DEFAULT_JAR_SETTINGS,
            )

        /** Settings for [ANNOTATION_SOURCE_FORMATTER] for Java sources. */
        private val ANNOTATION_SOURCE_JAVA_SETTINGS =
            ATTRIBUTE_DEFAULT_JAVA_SETTINGS.copy(
                valueStringConfiguration =
                    ATTRIBUTE_DEFAULT_JAVA_SETTINGS.boundConfiguration.copy(
                        // Legacy AnnotationItem.toSource() formats Java annotations without spaces
                        // around the `=` in `attr=value`.
                        annotationAttributeNameValueSeparator =
                            AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,

                        // Legacy AnnotationItem.toSource() formats class references as they were
                        // specified in the source.
                        classObjectValueFormat = ClassObjectValueFormat.SOURCE,

                        // Legacy AnnotationItem.toSource() uses `F` as the suffix for floats that
                        // were not specified as literals in the source.
                        nonLiteralFloatSuffix = 'F',

                        // Legacy AnnotationItem.toSource() formats ints as hexadecimals if they
                        // were not specified as literals in the source.
                        nonLiteralIntFormat = IntFormat.HEXADECIMAL,
                    ),
            )

        /** Settings for [ANNOTATION_SOURCE_FORMATTER] for Kotlin sources. */
        private val ANNOTATION_SOURCE_KOTLIN_SETTINGS =
            ATTRIBUTE_DEFAULT_KOTLIN_SETTINGS.copy(
                valueStringConfiguration =
                    ATTRIBUTE_DEFAULT_KOTLIN_SETTINGS.boundConfiguration.copy(
                        // Legacy AnnotationItem.toSource() formats Kotlin annotations with spaces
                        // around the `=` in `attr = value`.
                        annotationAttributeNameValueSeparator =
                            AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,

                        // Legacy AnnotationItem.toSource() formats ints as hexadecimals if they
                        // were not specified as literals in the source.
                        nonLiteralIntFormat = IntFormat.HEXADECIMAL,

                        // Legacy AnnotationItem.toSource() includes the companion object's class
                        // name in references to fields defined in a companion object.
                        showKotlinCompanionClass = true,

                        // Legacy AnnotationItem.toSource() includes a call to a numeric conversion
                        // function, e.g. `INT_FIELD.toLong()`.
                        showKotlinConversionFunction = true,

                        // Legacy AnnotationItem.toSource() formats numbers based on the literal
                        // they used in the source/constant type in class constant pool.
                        useOriginalValueForNumbers = false,
                    ),

                // Override [ATTRIBUTE_DEFAULT_KOTLIN_SETTINGS] which does not handle empty arrays
                // correctly while this does.
                stringReplacement = emptyMap(),

                // Legacy AnnotationItem.toSource() adds double quotes around chars from Kotlin
                // sources.
                useDoubleQuotesForChar = false,

                // Legacy AnnotationItem.toSource() does not add long or float suffixes for values
                // obtained from Kotlin sources.
                dropLongAndFloatTypeSuffix = false,

                // Legacy AnnotationItem.toSource() only inlined hidden or removed fields used in
                // Kotlin sources. It would keep non-public fields.
                inlineFields = InlineFieldValue.WHEN_HIDDEN_OR_REMOVED,
            )

        /** Settings for [ANNOTATION_SOURCE_FORMATTER] for Jar classes. */
        private val ANNOTATION_SOURCE_JAR_SETTINGS =
            Settings(
                valueStringConfiguration =
                    ValueStringConfiguration(
                        // Legacy AnnotationItem.toSource() formats jar annotations without spaces
                        // around the `=` in `attr=value`.
                        annotationAttributeNameValueSeparator =
                            AnnotationAttributeNameValueSeparator.WITHOUT_SPACES,

                        // Legacy AnnotationItem.toSource() uses `F` as the suffix for negative
                        // floats in the jar.
                        nonLiteralFloatSuffix = 'F',

                        // Legacy AnnotationItem.toSource() formats negative ints as hexadecimals
                        // when they came from a jar.
                        nonLiteralIntFormat = IntFormat.HEXADECIMAL,

                        // Do not unwrap a single array element when formatting a value from a jar
                        // as they were never unwrapped.
                        singleArrayElementFormat = SingleArrayElementFormat.WRAP,

                        // Annotation attributes are not sorted in the default values.
                        sortAnnotationAttributes = false,

                        // In the jar, while values are always stored as their actual type bytes and
                        // shorts do not have their own constant type and so are stored as ints.
                        useOriginalValueForNumbers = true,
                    ),
                // In the jar file special values were always stored as their constant value so they
                // were never formatted as their fields.
                stringReplacement =
                    mapOf(
                        DoubleValue.NaN to "0.0 / 0.0",
                        DoubleValue.NEGATIVE_INFINITY to "-1.0 / 0.0",
                        DoubleValue.POSITIVE_INFINITY to "1.0 / 0.0",
                        FloatValue.NaN to "0.0f / 0.0",
                        FloatValue.NEGATIVE_INFINITY to "-1.0F / 0.0",
                        FloatValue.POSITIVE_INFINITY to "1.0f / 0.0",
                    ),
            )

        /** Legacy formatting of [AnnotationItem]s. */
        internal val ANNOTATION_SOURCE_FORMATTER =
            LegacyValueFormatter(
                javaSettings = ANNOTATION_SOURCE_JAVA_SETTINGS,
                kotlinSettings = ANNOTATION_SOURCE_KOTLIN_SETTINGS,
                jarSettings = ANNOTATION_SOURCE_JAR_SETTINGS,
            )
    }
}
