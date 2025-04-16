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

/**
 * A [PrimitiveValue] whose [Value.toValueString] is affected by
 * [ValueStringConfiguration.treatAsIntIfOriginallySpecifiedAsInt].
 */
internal sealed interface ToValueStringDependsOnSourceForm<T : Any> : PrimitiveValue<T> {
    /**
     * True if the original value of this from the source, was specified as an integer, e.g. `3`
     * instead of `3.0`, or `3.0f` or `3L`. This is used to tweak formatting to match legacy
     * behavior.
     */
    val wasOriginallySpecifiedAsInt: Boolean

    @Deprecated("Do not call directly", replaceWith = ReplaceWith("toString()"))
    override fun debugStringForValue(): String {
        val suffix = if (wasOriginallySpecifiedAsInt) ",asInt" else ""
        return toValueString() + suffix
    }
}

/**
 * If the [configuration] has [ValueStringConfiguration.treatAsIntIfOriginallySpecifiedAsInt] set to
 * `true` and [ToValueStringDependsOnSourceForm.wasOriginallySpecifiedAsInt] is also `true` then
 * this will return [ToValueStringDependsOnSourceForm.underlyingValue] as if it was an `int`,
 * otherwise it will return the value returned by [otherwise].
 */
internal inline fun <T : Number> ToValueStringDependsOnSourceForm<T>.treatAsIntIfRequired(
    configuration: ValueStringConfiguration,
    otherwise: (T) -> String
): String {
    if (configuration.treatAsIntIfOriginallySpecifiedAsInt && wasOriginallySpecifiedAsInt) {
        val intValue = underlyingValue.toInt()
        return intValue.toString()
    }

    return otherwise(underlyingValue)
}

internal class DefaultBooleanValue(override val underlyingValue: Boolean) :
    DefaultPrimitiveValue<Boolean>(), BooleanValue

internal class DefaultByteValue(override val underlyingValue: Byte) :
    DefaultPrimitiveValue<Byte>(), ByteValue

internal class DefaultCharValue(override val underlyingValue: Char) :
    DefaultPrimitiveValue<Char>(), CharValue

internal class DefaultDoubleValue(
    override val underlyingValue: Double,
    override val wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultPrimitiveValue<Double>(), DoubleValue, ToValueStringDependsOnSourceForm<Double> {

    override fun toValueString(configuration: ValueStringConfiguration) =
        treatAsIntIfRequired(configuration) { super<DoubleValue>.toValueString(configuration) }
}

internal class DefaultFloatValue(
    override val underlyingValue: Float,
    override val wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultPrimitiveValue<Float>(), FloatValue, ToValueStringDependsOnSourceForm<Float> {
    override fun toValueString(configuration: ValueStringConfiguration) =
        treatAsIntIfRequired(configuration) { super<FloatValue>.toValueString(configuration) }
}

internal class DefaultIntValue(override val underlyingValue: Int) :
    DefaultPrimitiveValue<Int>(), IntValue

internal class DefaultLongValue(
    override val underlyingValue: Long,
    override val wasOriginallySpecifiedAsInt: Boolean = false,
) : DefaultPrimitiveValue<Long>(), LongValue, ToValueStringDependsOnSourceForm<Long> {
    override fun toValueString(configuration: ValueStringConfiguration) =
        treatAsIntIfRequired(configuration) { super<LongValue>.toValueString(configuration) }
}

internal class DefaultShortValue(override val underlyingValue: Short) :
    DefaultPrimitiveValue<Short>(), ShortValue

internal class DefaultStringValue(override val underlyingValue: String) :
    DefaultLiteralValue<String>(), StringValue
