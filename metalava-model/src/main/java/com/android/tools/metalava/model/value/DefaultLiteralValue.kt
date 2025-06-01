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

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem

/** Base class for all [LiteralValue] implementations. */
internal sealed class DefaultLiteralValue<U : Any> : DefaultValue(), LiteralValue<U> {
    // Implement this in the class not the interface as it requires implementation details.

    override fun convertToType(
        optionalTypeItem: TypeItem?,
        forceNonLiteralInSource: Boolean,
    ): LiteralValue<*> {
        optionalTypeItem ?: return this
        if (optionalTypeItem.isString() && underlyingValue is String) return this
        if (optionalTypeItem !is PrimitiveTypeItem)
            error("Cannot convert $this to a $optionalTypeItem")

        // If the underlying value is already of the correct type and this is not being force to be
        // non-literal, or if it is then it is already non-literal, then just return this.
        // Otherwise, just drop through and perform the conversion.
        if (
            optionalTypeItem.kind.wrapperClass.isInstance(underlyingValue) &&
                (!forceNonLiteralInSource || nonLiteralInSource)
        )
            return this

        // Use the original value and original non-literal status.
        return Value.createLiteralValue(
            optionalTypeItem,
            originalValue,
            // Mark this as non-literal if it is being forced of this is already non-literal.
            forceNonLiteralInSource || nonLiteralInSource
        )
    }

    /**
     * The original value of this from the source.
     *
     * This will differ from [underlyingValue] if this needed to be converted by
     * [Value.createLiteralValue] to match the [TypeItem] for where this will be used. This is used
     * to tweak formatting to match legacy behavior.
     *
     * This is [Any] instead of [Number] because the original value could be a [Char].
     */
    open val originalValue: Any
        get() = underlyingValue

    /**
     * True if the source representation of this was a non-literal, i.e. not a literal expression
     * but some other expression. This includes negative and explicitly positive numbers as they
     * both are represented as a unary minus/plus expression respectively. For jars this is
     * generally false but may be true for some values that cannot be represented as a literal, e.g.
     * `Float.NaN`, etc.
     */
    open val nonLiteralInSource: Boolean
        get() = false
}

internal sealed class DefaultPrimitiveValue<U : Any> : DefaultLiteralValue<U>(), PrimitiveValue<U>

internal sealed class DefaultNumericValue<U : Number>(
    override val originalValue: Any,
    override val nonLiteralInSource: Boolean,
) : DefaultPrimitiveValue<U>() {
    /**
     * If the [configuration] has [ValueStringConfiguration.useOriginalValueForNumbers] set to
     * `true` and [originalValue] is also `true` then this will append [underlyingValue] as if it
     * was an `int`, otherwise it will invoke [appendNumericValueTo] to append the value as normal.
     */
    final override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        configuration.specialValues[this]?.let {
            builder.append(it)
            return
        }

        if (configuration.useOriginalValueForNumbers) {
            when (val originalValue = originalValue) {
                is Int -> {
                    appendIntegerValueTo(builder, configuration, originalValue)
                    return
                }
                is Float -> {
                    appendFloatValueTo(builder, configuration, originalValue)
                    return
                }
            }
        }

        // Append the default numeric value to builder.
        appendNumericValueTo(builder, configuration)
    }

    /**
     * Append [intValue] to [builder] taking into account all relevant properties in
     * [configuration].
     */
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

    /**
     * Append [floatValue] to [builder] taking into account all relevant properties in
     * [configuration].
     */
    internal fun appendFloatValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration,
        floatValue: Float,
    ) {
        // If it was not a literal then use the non-literal suffix. This is mutually
        // exclusive with it being specified as an int so it does not matter which one is
        // performed first.
        val suffix = if (nonLiteralInSource) configuration.nonLiteralFloatSuffix else 'f'
        builder.append(floatValue).append(suffix)
    }

    internal open fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue)
    }

    override fun appendLegacyStateTo(builder: StringBuilder) {
        // If the original value class does not match the underlying value class then that could
        // affect the legacy behavior of this so make sure that information is included in the
        // legacy state string representation.
        val expectedOriginalValueClass = underlyingValue.javaClass
        val actualOriginalValueClass = originalValue.javaClass
        if (actualOriginalValueClass != expectedOriginalValueClass) {
            when (actualOriginalValueClass) {
                intWrapperClass -> builder.append(",asInt")
                floatWrapperClass -> builder.append(",asFloat")
                else ->
                    // If the value expected an int, but it was not an int then include that in the
                    // state.
                    if (expectedOriginalValueClass == intWrapperClass) builder.append(",!asInt")
                    // If the value expected a float, but it was not a float then include that in
                    // the state.
                    else if (
                        expectedOriginalValueClass == floatWrapperClass &&
                            !originalValue.isSpecialDouble()
                    )
                        builder.append(",!asFloat")
            }
        }
        if (nonLiteralInSource) builder.append(",nonLiteral")
    }

    /**
     * Checks to see whether this is a special [Double].
     *
     * This is needed as Psi has some special handling of floating point values which do not have a
     * literal representation. It represents such values that are retrieved from a class constant
     * pool similar to how they are represented in the source, i.e. as a division-by-zero
     * expression. Unfortunately, it does not do that in exactly the same way, i.e. it uses
     * `(0.0f/0.0)` to represent `Float.NaN`. Unfortunately, that actually evaluates to a `Double`.
     * The source uses `(0.0f/0.0f)` which evaluates to a `Float`.
     */
    private fun Any.isSpecialDouble() = this is Double && (isInfinite() || isNaN())

    companion object {
        private val intWrapperClass = Int::class.javaObjectType
        private val floatWrapperClass = Float::class.javaObjectType
    }
}

internal class DefaultBooleanValue(override val underlyingValue: Boolean) :
    DefaultPrimitiveValue<Boolean>(), BooleanValue

internal class DefaultByteValue(
    override val underlyingValue: Byte,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Byte>(originalValue, nonLiteralInSource), ByteValue

internal class DefaultCharValue(override val underlyingValue: Char) :
    DefaultPrimitiveValue<Char>(), CharValue

internal class DefaultDoubleValue(
    override val underlyingValue: Double,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Double>(originalValue, nonLiteralInSource), DoubleValue

internal class DefaultFloatValue(
    override val underlyingValue: Float,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Float>(originalValue, nonLiteralInSource), FloatValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        appendFloatValueTo(builder, configuration, underlyingValue)
    }
}

internal class DefaultIntValue(
    override val underlyingValue: Int,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Int>(originalValue, nonLiteralInSource), IntValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        appendIntegerValueTo(builder, configuration, underlyingValue)
    }
}

internal class DefaultLongValue(
    override val underlyingValue: Long,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Long>(originalValue, nonLiteralInSource), LongValue {

    override fun appendNumericValueTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue).append('L')
    }
}

internal class DefaultShortValue(
    override val underlyingValue: Short,
    originalValue: Any = underlyingValue,
    nonLiteralInSource: Boolean = false,
) : DefaultNumericValue<Short>(originalValue, nonLiteralInSource), ShortValue

internal class DefaultStringValue(override val underlyingValue: String) :
    DefaultLiteralValue<String>(), StringValue
