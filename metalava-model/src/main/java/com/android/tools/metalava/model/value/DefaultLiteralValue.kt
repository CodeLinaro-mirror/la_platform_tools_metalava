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

internal class DefaultBooleanValue(override val underlyingValue: Boolean) :
    DefaultPrimitiveValue<Boolean>(), BooleanValue

internal class DefaultByteValue(override val underlyingValue: Byte) :
    DefaultPrimitiveValue<Byte>(), ByteValue

internal class DefaultCharValue(override val underlyingValue: Char) :
    DefaultPrimitiveValue<Char>(), CharValue

internal class DefaultDoubleValue(
    override val underlyingValue: Double,
) : DefaultPrimitiveValue<Double>(), DoubleValue

internal class DefaultFloatValue(
    override val underlyingValue: Float,
) : DefaultPrimitiveValue<Float>(), FloatValue

internal class DefaultIntValue(override val underlyingValue: Int) :
    DefaultPrimitiveValue<Int>(), IntValue

internal class DefaultLongValue(
    override val underlyingValue: Long,
) : DefaultPrimitiveValue<Long>(), LongValue

internal class DefaultShortValue(override val underlyingValue: Short) :
    DefaultPrimitiveValue<Short>(), ShortValue

internal class DefaultStringValue(override val underlyingValue: String) :
    DefaultLiteralValue<String>(), StringValue
