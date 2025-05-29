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

/** Base class for all [LiteralValue] implementations. */
internal sealed class DefaultLiteralValue<U : Any> : DefaultValue(), LiteralValue<U>

internal sealed class DefaultPrimitiveValue<U : Any> : DefaultLiteralValue<U>(), PrimitiveValue<U>

internal sealed class DefaultNumericValue<U : Number>(
    /**
     * True if the original value of this from the source, was specified as an integer, e.g. `3`
     * instead of `3.0`, or `3.0f` or `3L`. This is used to tweak formatting to match legacy
     * behavior.
     */
    private val wasOriginallySpecifiedAsInt: Boolean,

    /**
     * True if the source representation of this was a non-literal, i.e. not a literal expression
     * but some other expression. This includes negative and explicitly positive numbers as they
     * both are represented as a unary minus/plus expression respectively. For jars this is
     * generally false but may be true for some values that cannot be represented as a literal, e.g.
     * `Float.NaN`, etc.
     */
    protected val nonLiteralInSource: Boolean,
) : DefaultPrimitiveValue<U>() {

    /**
     * If the [configuration] has [ValueStringConfiguration.treatAsIntIfOriginallySpecifiedAsInt]
     * set to `true` and [wasOriginallySpecifiedAsInt] is also `true` then this will append
     * [underlyingValue] as if it was an `int`, otherwise it will invoke [appendNumericValueTo] to
     * append the value as normal.
     */
    final override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        configuration.specialValues[this]?.let {
            builder.append(it)
            return
        }

        if (configuration.treatAsIntIfOriginallySpecifiedAsInt && wasOriginallySpecifiedAsInt) {
            val intValue = underlyingValue.toInt()
            appendIntegerValueTo(builder, configuration, intValue)
            return
        }

        // Append the default numeric value to builder.
        appendNumericValueTo(builder, configuration)
    }

    internal fun appendIntegerValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration,
        intValue: Int,
    ) {
        val format =
            if (nonLiteralInSource) configuration.nonLiteralIntFormat else IntFormat.DECIMAL
        when (format) {
            IntFormat.HEXADECIMAL -> {
                builder.append("0x").append(Integer.toHexString(intValue))
            }
            else -> {
                builder.append(intValue)
            }
        }
    }

    internal open fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue)
    }

    override fun appendLegacyStateTo(builder: StringBuilder) {
        val expectToBeInt = javaClass === DefaultIntValue::class.java
        if (wasOriginallySpecifiedAsInt != expectToBeInt) {
            if (expectToBeInt) builder.append(",!asInt") else builder.append(",asInt")
        }
        if (nonLiteralInSource) builder.append(",nonLiteral")
    }
}

internal class DefaultBooleanValue(override val underlyingValue: Boolean) :
    DefaultPrimitiveValue<Boolean>(), BooleanValue

internal class DefaultByteValue(
    override val underlyingValue: Byte,
    wasOriginallySpecifiedAsInt: Boolean = false,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Byte>(wasOriginallySpecifiedAsInt, nonLiteralInSource), ByteValue

internal class DefaultCharValue(override val underlyingValue: Char) :
    DefaultPrimitiveValue<Char>(), CharValue

internal class DefaultDoubleValue(
    override val underlyingValue: Double,
    wasOriginallySpecifiedAsInt: Boolean = false,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Double>(wasOriginallySpecifiedAsInt, nonLiteralInSource), DoubleValue

internal class DefaultFloatValue(
    override val underlyingValue: Float,
    wasOriginallySpecifiedAsInt: Boolean = false,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Float>(wasOriginallySpecifiedAsInt, nonLiteralInSource), FloatValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        // If it was not a literal then use the non-literal suffix. This is mutually
        // exclusive with it being specified as an int so it does not matter which one is
        // performed first.
        val suffix = if (nonLiteralInSource) configuration.nonLiteralFloatSuffix else 'f'
        builder.append(underlyingValue).append(suffix)
    }
}

internal class DefaultIntValue(
    override val underlyingValue: Int,
    wasOriginallySpecifiedAsInt: Boolean = true,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Int>(wasOriginallySpecifiedAsInt, nonLiteralInSource), IntValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        appendIntegerValueTo(builder, configuration, underlyingValue)
    }
}

internal class DefaultLongValue(
    override val underlyingValue: Long,
    wasOriginallySpecifiedAsInt: Boolean = false,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Long>(wasOriginallySpecifiedAsInt, nonLiteralInSource), LongValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue).append('L')
    }
}

internal class DefaultShortValue(
    override val underlyingValue: Short,
    wasOriginallySpecifiedAsInt: Boolean = false,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Short>(wasOriginallySpecifiedAsInt, nonLiteralInSource), ShortValue

internal class DefaultStringValue(override val underlyingValue: String) :
    DefaultLiteralValue<String>(), StringValue
