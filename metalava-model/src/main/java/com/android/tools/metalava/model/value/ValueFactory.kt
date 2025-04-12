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

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.value.ValueFactory.Companion.primitiveValueFactories

/**
 * Provides support for creating instances of [Value]s.
 *
 * This is implemented on [Value.Companion] to make it easy to create instances using something like
 * [Value.createLiteralValue] and is intended to be implemented by a model specific class that maps
 * from model specific classes to [Value]s.
 */
interface ValueFactory {
    /**
     * Create a [LiteralValue] if possible, otherwise throw an exception.
     *
     * If [optionalTypeItem] is provided then this checks to make sure that it and [underlyingValue]
     * are consistent, converting the latter to match the former if possible without losing
     * information . e.g.
     * * If [optionalTypeItem] is an integer type then [underlyingValue] is expected to be an
     *   integer type, and it will be converted to the appropriate type. If [optionalTypeItem] is
     *   [Primitive.BYTE] and [underlyingValue] is an integer with value 30 then the conversion will
     *   succeed. If it has a value of 300 it will not succeed.
     * * Similarly, if [optionalTypeItem] is a floating point type then [underlyingValue] is
     *   expected to be an integer or floating point type.
     *
     * The resulting [LiteralValue.underlyingValue] has the following types for [Primitive]:
     * * [Primitive.BOOLEAN] -> [java.lang.Boolean]
     * * [Primitive.BYTE] -> [java.lang.Byte]
     * * [Primitive.CHAR] -> [java.lang.Character]
     * * [Primitive.DOUBLE] -> [java.lang.Double]
     * * [Primitive.FLOAT] -> [java.lang.Float]
     * * [Primitive.INT] -> [java.lang.Integer]
     * * [Primitive.LONG] -> [java.lang.Long]
     * * [Primitive.SHORT] -> [java.lang.Short]
     * * [ClassTypeItem] of [java.lang.String] -> [java.lang.String]
     *
     * If [optionalTypeItem] is not provided then the [underlyingValue] type will be used to
     * determine which [LiteralValue] is returned.
     *
     * The [underlyingValue] (after possibly undergoing type conversion to another `java.lang`
     * object) is mapped to a subclass of [LiteralValue] as follows:
     * * [java.lang.Boolean] -> [BooleanValue]
     * * [java.lang.Byte] -> [ByteValue]
     * * [java.lang.Character] -> [CharValue]
     * * [java.lang.Double] -> [DoubleValue]
     * * [java.lang.Float] -> [FloatValue]
     * * [java.lang.Integer] -> [IntValue]
     * * [java.lang.Long] -> [LongValue]
     * * [java.lang.Short] -> [ShortValue]
     * * [java.lang.String] -> [StringValue]
     *
     * @param optionalTypeItem the optional [TypeItem] for the context in which the value is used,
     *   e.g. [MethodItem.returnType] for [MethodItem.defaultValue]. It should be available unless
     *   the source is incomplete, e.g. missing annotation class definitions.
     */
    fun createLiteralValue(optionalTypeItem: TypeItem?, underlyingValue: Any): LiteralValue<*> {
        val literalValue =
            when (optionalTypeItem) {
                is PrimitiveTypeItem -> {
                    // Normalized the primitive value to ensure that they are consistent with the
                    // type.
                    val primitiveKind = optionalTypeItem.kind
                    val primitiveValue = normalizePrimitive(underlyingValue, primitiveKind)

                    createPrimitiveValueForKind(primitiveKind, primitiveValue)
                }
                is ClassTypeItem -> {
                    // The only allowable class type is a String.
                    if (optionalTypeItem.isString() && underlyingValue is String)
                        DefaultStringValue(underlyingValue)
                    else null
                }
                null -> {
                    // No type was provided so just wrap the underlyingValue in the appropriate
                    // LiteralValue wrapper.
                    if (underlyingValue is String) {
                        DefaultStringValue(underlyingValue)
                    } else {
                        Primitive.entries
                            .find {
                                it.wrapperClass.isInstance(underlyingValue) && it != Primitive.VOID
                            }
                            ?.let { primitiveKind ->
                                createPrimitiveValueForKind(primitiveKind, underlyingValue)
                            }
                            ?: error(
                                "Underlying value '$underlyingValue' of ${underlyingValue.javaClass} is not supported"
                            )
                    }
                }
                else -> null
            }

        literalValue
            ?: error(
                "Incompatible type '$optionalTypeItem', for underlying value `$underlyingValue` of ${underlyingValue.javaClass}"
            )
        return literalValue
    }

    /**
     * Create an [ArrayValue] containing [elements].
     *
     * Every call that supplies an empty [elements] will return the same instance of [ArrayValue].
     * It is the caller's responsibility to ensure that every [ArrayElementValue] in [elements] has
     * the same [Value.kind]. This will throw an exception if it does not.
     */
    fun createArrayValue(elements: List<ArrayElementValue>): Value {
        if (elements.isEmpty()) return EMPTY_ARRAY
        val groupedByKind = elements.groupBy { it.kind }
        val kindCount = groupedByKind.size
        if (kindCount == 1) return DefaultArrayValue(elements)
        val message = buildString {
            append("Expected array elements to be all of the same kind but found ")
            append(kindCount)
            append(" different kinds of value:")
            for (entry in groupedByKind) {
                append("\n    ")
                append(entry.key)
                append(" -> ")
                entry.value.joinTo(this)
            }
        }
        error(message)
    }

    companion object {
        /**
         * Map from [Primitive] to a [PrimitiveValueFactory] to use to create an appropriate
         * [DefaultLiteralValue] subclass.
         */
        val primitiveValueFactories =
            mapOf<Primitive, PrimitiveValueFactory<*>>(
                Primitive.BOOLEAN to
                    { underlyingValue ->
                        DefaultBooleanValue(underlyingValue as Boolean)
                    },
                Primitive.BYTE to { underlyingValue -> DefaultByteValue(underlyingValue as Byte) },
                Primitive.CHAR to { underlyingValue -> DefaultCharValue(underlyingValue as Char) },
                Primitive.DOUBLE to
                    { underlyingValue ->
                        DefaultDoubleValue(underlyingValue as Double)
                    },
                Primitive.FLOAT to
                    { underlyingValue ->
                        DefaultFloatValue(underlyingValue as Float)
                    },
                Primitive.INT to { underlyingValue -> DefaultIntValue(underlyingValue as Int) },
                Primitive.LONG to { underlyingValue -> DefaultLongValue(underlyingValue as Long) },
                Primitive.SHORT to
                    { underlyingValue ->
                        DefaultShortValue(underlyingValue as Short)
                    },
            )

        /**
         * Create a [PrimitiveValue] for [primitiveKind] and [primitiveValue].
         *
         * The caller has already made sure that the [primitiveValue] is appropriate for
         * [primitiveKind].
         */
        private fun createPrimitiveValueForKind(primitiveKind: Primitive, primitiveValue: Any) =
            primitiveValueFactories[primitiveKind]?.let { factory -> factory(primitiveValue) }
                ?: error("Cannot create PrimitiveValue: unknown primitive kind: $primitiveKind")

        /** Normalize the [underlyingValue] to make it consistent with [primitiveKind]. */
        private fun normalizePrimitive(underlyingValue: Any, primitiveKind: Primitive): Any {
            val primitiveValue =
                when (underlyingValue) {
                    is Boolean -> {
                        if (primitiveKind == Primitive.BOOLEAN) underlyingValue else null
                    }
                    is Char -> {
                        if (primitiveKind == Primitive.CHAR) underlyingValue else null
                    }
                    is String -> {
                        // A single character string can be used as a char.
                        if (primitiveKind == Primitive.CHAR && underlyingValue.length == 1)
                            underlyingValue[0]
                        else null
                    }
                    is Number -> {
                        val convertedValue: Any? =
                            when (primitiveKind) {
                                Primitive.BYTE -> convertInteger(underlyingValue) { it.toByte() }
                                Primitive.CHAR ->
                                    if (underlyingValue.isIntegerNumber())
                                        underlyingValue.toInt().toChar()
                                    else null
                                Primitive.DOUBLE -> convertFloating(underlyingValue) { it }
                                Primitive.FLOAT -> convertFloating(underlyingValue) { it.toFloat() }
                                Primitive.INT -> convertInteger(underlyingValue) { it.toInt() }
                                Primitive.LONG -> convertInteger(underlyingValue) { it }
                                Primitive.SHORT -> convertInteger(underlyingValue) { it.toShort() }
                                else -> null
                            }

                        if (convertedValue != null) {
                            checkLossyConversion(underlyingValue, primitiveKind, convertedValue)
                        }
                        convertedValue
                    }
                    else -> null
                }

            primitiveValue
                ?: error(
                    "Unsupported primitive type: ${primitiveKind.primitiveName}, for underlying value `$underlyingValue` of ${underlyingValue.javaClass}"
                )
            return primitiveValue
        }

        /** True if this [Number] is an integer (in the general sense). */
        private fun Number.isIntegerNumber() =
            this is Byte || this is Int || this is Long || this is Short

        /** True if this [Number] is a floating point number. */
        private fun Number.isFloatingNumber() = this is Float || this is Double

        /**
         * Check to see [converted] which was the result of converting from [original] to
         * [targetKind] can be converted back to [original] without loss. If it cannot then throw an
         * exception.
         */
        private fun checkLossyConversion(original: Number, targetKind: Primitive, converted: Any) {
            val convertedNumber =
                when (converted) {
                    is Number -> converted
                    is Char -> converted.code
                    else -> error("unknown converted $converted of ${converted.javaClass}")
                }

            val roundTrip =
                when (original) {
                    is Byte -> convertedNumber.toByte()
                    is Double -> convertedNumber.toDouble()
                    is Float -> convertedNumber.toFloat()
                    is Int -> convertedNumber.toInt()
                    is Long -> convertedNumber.toLong()
                    is Short -> convertedNumber.toShort()
                    else -> error("unknown original $original of ${original.javaClass}")
                }

            if (roundTrip != original) {
                error(
                    "Conversion of $original to ${targetKind.primitiveName} is lossy and produces $converted; round trip value is $roundTrip"
                )
            }
        }

        /**
         * Convert an integer [Number] to another integer [Number] by first converting it to [Long]
         * and then using [convert] to convert to another integer [Number].
         */
        private inline fun convertInteger(number: Number, convert: (Long) -> Number): Number? {
            if (!number.isIntegerNumber()) {
                return null
            }

            // Convert it to a long value as that is not lossy.
            val longValue = number.toLong()

            // Convert it to the correct type for the primitive kind.
            return convert(longValue)
        }

        /**
         * Convert a floating point or integer [Number] to another floating point [Number] by first
         * converting it to [Double] and then using [convert] to convert to another floating point
         * [Number].
         */
        private inline fun convertFloating(number: Number, convert: (Double) -> Number): Number? {
            if (!number.isFloatingNumber() && !number.isIntegerNumber()) {
                return null
            }

            // Convert it to a double value as that is not lossy.
            val doubleValue = number.toDouble()

            // Convert it to the correct type for the primitive kind.
            return convert(doubleValue)
        }

        /** An empty [ArrayValue]. */
        private val EMPTY_ARRAY = DefaultArrayValue(emptyList())
    }
}

/** Type of values in [primitiveValueFactories]. */
internal typealias PrimitiveValueFactory<T> = (Any) -> PrimitiveValue<T>
