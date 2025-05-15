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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeVisitor
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.javaEscapeString
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

                    createPrimitiveValueForKind(primitiveKind, primitiveValue, underlyingValue)
                }
                is ClassTypeItem -> {
                    // The only allowable class type is a String.
                    if (optionalTypeItem.isPossiblyUnresolvedString() && underlyingValue is String)
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
                                createPrimitiveValueForKind(
                                    primitiveKind,
                                    underlyingValue,
                                    underlyingValue
                                )
                            }
                            ?: error(
                                "Underlying value '$underlyingValue' of ${underlyingValue.javaClass} is not supported"
                            )
                    }
                }
                else -> null
            }

        literalValue
            ?: throw ValueProviderException(
                "Incompatible type '$optionalTypeItem', for underlying value `$underlyingValue` of ${underlyingValue.javaClass}"
            )
        return literalValue
    }

    /**
     * Create an [ArrayValue] containing [elements].
     *
     * Every call that supplies an empty [elements] will return the same instance of [ArrayValue].
     * It is the caller's responsibility to ensure that every [ArrayElementValue] in [elements] has
     * the same [Value.kind] (excluding [ValueKind.FIELD]). This will throw an exception if it does
     * not.
     */
    fun createArrayValue(
        elements: List<ArrayElementValue>,
        wasUnwrappedInSource: Boolean = false
    ): ArrayValue {
        if (elements.isEmpty()) return EMPTY_ARRAY
        if (wasUnwrappedInSource && elements.size != 1)
            error("wasUnwrappedInSource was set to true but array does not contain 1 element")
        val groupedByKind = elements.groupBy { it.kind }
        val kindCount = groupedByKind.size
        // Only allow 1 kind or 2 if one of them is field.
        if (kindCount == 1 || (kindCount == 2 && ValueKind.FIELD in groupedByKind))
            return DefaultArrayValue(elements, wasUnwrappedInSource)
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

    /**
     * Create a [ClassObjectValue] encapsulating [typeItem].
     *
     * [typeItem] must be one of the following:
     * * A [PrimitiveTypeItem].
     * * A [ClassTypeItem] with no [ClassTypeItem.arguments].
     * * An [ArrayTypeItem] of one of these (including [ArrayTypeItem]).
     */
    fun createClassObjectValue(typeItem: TypeItem, sourceExpression: String?): ClassObjectValue {
        typeItem.accept(classObjectValueTypeChecker)
        return DefaultClassObjectValue(typeItem, sourceExpression)
    }

    /**
     * Create a [FieldReferenceValue] called [fieldName] in [qualifiedClassName].
     *
     * If the field has a constant initializer then it will be retrieved when calling
     * [FieldReferenceValue.asLiteralValue].
     *
     * @param classResolver used to resolve [qualifiedClassName] to a [ClassItem] in
     *   [FieldReferenceValue.resolve]
     * @param qualifiedClassName the qualified name of the class containing the field. Is an empty
     *   string if the field is unqualified.
     * @param fieldName the name of the field.
     * @param optionalTypeItem the optional [TypeItem] determined by the context within which the
     *   [FieldReferenceValue] will be used.
     */
    fun createFieldReferenceValueWithDeferredConstantValue(
        classResolver: ClassResolver,
        qualifiedClassName: String,
        fieldName: String,
        optionalTypeItem: TypeItem?,
    ): ArrayElementValue {
        // Create a field.
        val fieldReferenceValue =
            LazyFieldReferenceValue(
                classResolver,
                qualifiedClassName,
                fieldName,
                optionalTypeItem,
            )

        // The field may need mapping to a constant value to eliminate differences between Kotlin
        // and Java.
        return normalizeFieldReferenceValue(fieldReferenceValue)
    }

    /**
     * Create a [FieldReferenceValue] called [fieldName] in [qualifiedClassName] with an optional
     * [constantValue].
     */
    fun createFieldReferenceValue(
        classResolver: ClassResolver,
        qualifiedClassName: String,
        fieldName: String,
        constantValue: ConstantValue? = null,
    ): ArrayElementValue {
        // Create a field.
        val fieldReferenceValue =
            DefaultFieldReferenceValue(
                classResolver,
                qualifiedClassName,
                fieldName,
                constantValue,
            )

        // The field may need mapping to a constant value to eliminate differences between Kotlin
        // and Java.
        return normalizeFieldReferenceValue(fieldReferenceValue)
    }

    /** Normalize [FieldReferenceValue]s to eliminate differences between Java and Kotlin. */
    private fun normalizeFieldReferenceValue(
        fieldReferenceValue: FieldReferenceValue
    ): ArrayElementValue {
        return specialFieldsToReplacementValue[fieldReferenceValue] ?: fieldReferenceValue
    }

    /** Create an [AnnotationValue] that wraps an [AnnotationItem]. */
    fun createAnnotationValue(annotationItem: AnnotationItem): AnnotationValue =
        DefaultAnnotationValue(annotationItem)

    /**
     * Check to see whether this [TypeItem] is `java.lang.String`.
     *
     * As the definition of `java.lang.String` may not have been provided to Metalava also check for
     * `String` as that is most likely to be an unresolved reference to `java.lang.String`. If it
     * was a custom class then presumably that would be defined somewhere in which case it would
     * have been resolved to the class and so would not be an unqualified name.
     */
    fun TypeItem.isPossiblyUnresolvedString() =
        isString() || (this is ClassTypeItem && qualifiedName == "String")

    /** Check if this [TypeItem] is a constant type, i.e. a [String] or a primitive type. */
    fun TypeItem.isConstantType() = isPossiblyUnresolvedString() || this is PrimitiveTypeItem

    companion object {
        /**
         * Map from [Primitive] to a [PrimitiveValueFactory] to use to create an appropriate
         * [DefaultLiteralValue] subclass.
         */
        val primitiveValueFactories =
            mapOf<Primitive, PrimitiveValueFactory<*>>(
                Primitive.BOOLEAN to
                    { underlyingValue, _ ->
                        DefaultBooleanValue(underlyingValue as Boolean)
                    },
                Primitive.BYTE to
                    { underlyingValue, _ ->
                        DefaultByteValue(underlyingValue as Byte)
                    },
                Primitive.CHAR to
                    { underlyingValue, _ ->
                        DefaultCharValue(underlyingValue as Char)
                    },
                Primitive.DOUBLE to
                    { underlyingValue, originalValue ->
                        DefaultDoubleValue(underlyingValue as Double, originalValue is Int)
                    },
                Primitive.FLOAT to
                    { underlyingValue, originalValue ->
                        DefaultFloatValue(underlyingValue as Float, originalValue is Int)
                    },
                Primitive.INT to { underlyingValue, _ -> DefaultIntValue(underlyingValue as Int) },
                Primitive.LONG to
                    { underlyingValue, originalValue ->
                        DefaultLongValue(underlyingValue as Long, originalValue is Int)
                    },
                Primitive.SHORT to
                    { underlyingValue, _ ->
                        DefaultShortValue(underlyingValue as Short)
                    },
            )

        /**
         * Create a simple [FieldReferenceValue] for [fieldName] in class [qualifiedName]
         *
         * Note: This does not work for fields in nested classes.
         */
        private fun fieldReference(qualifiedName: String, fieldName: String) =
            DefaultFieldReferenceValue(ClassResolver.THROWING, qualifiedName, fieldName)

        /**
         * Adds mappings for special fields [field] of [type] to [value].
         *
         * This adds a mapping for each of the Java and Kotlin special fields called [field] of
         * [type] to [value].
         */
        private fun MutableMap<FieldReferenceValue, ConstantValue>.addFieldMappings(
            type: String,
            field: String,
            value: ConstantValue
        ) {
            put(fieldReference("java.lang.$type", field), value)
            put(fieldReference("kotlin.jvm.internal.${type}CompanionObject", field), value)
        }

        /**
         * Map from [FieldReferenceValue] to a [ConstantValue] for some special fields which differ
         * between Java and Kotlin.
         */
        private val specialFieldsToReplacementValue = buildMap {
            addFieldMappings("Double", "NaN", DoubleValue.NaN)
            addFieldMappings("Double", "NEGATIVE_INFINITY", DoubleValue.NEGATIVE_INFINITY)
            addFieldMappings("Double", "POSITIVE_INFINITY", DoubleValue.POSITIVE_INFINITY)

            addFieldMappings("Float", "NaN", FloatValue.NaN)
            addFieldMappings("Float", "NEGATIVE_INFINITY", FloatValue.NEGATIVE_INFINITY)
            addFieldMappings("Float", "POSITIVE_INFINITY", FloatValue.POSITIVE_INFINITY)
        }

        /**
         * Create a [PrimitiveValue] for [primitiveKind] and [primitiveValue].
         *
         * The caller has already made sure that the [primitiveValue] is appropriate for
         * [primitiveKind].
         *
         * The [originalValue] is the original value that was retrieved from the expression before
         * any casting was performed to ensure it matches the [primitiveKind]. e.g. if the original
         * source expression was an `int` literal, e.g. `10` and [primitiveKind] is [Primitive.LONG]
         * then the [primitiveValue] will be a `java.lang.Long` instance with a value of `10L` but
         * the [originalValue] will be a `java.lang.Integer` instance with a value of `10`.
         *
         * It supports the [ValueStringConfiguration.treatAsIntIfOriginallySpecifiedAsInt] behavior.
         */
        private fun createPrimitiveValueForKind(
            primitiveKind: Primitive,
            primitiveValue: Any,
            originalValue: Any
        ) =
            primitiveValueFactories[primitiveKind]?.let { factory ->
                factory(primitiveValue, originalValue)
            } ?: error("Cannot create PrimitiveValue: unknown primitive kind: $primitiveKind")

        /** Normalize the [underlyingValue] to make it consistent with [primitiveKind]. */
        private fun normalizePrimitive(underlyingValue: Any, primitiveKind: Primitive): Any {
            val primitiveValue =
                when (underlyingValue) {
                    is Boolean -> {
                        if (primitiveKind == Primitive.BOOLEAN) underlyingValue else null
                    }
                    is Char -> {
                        val convertedValue: Any? =
                            when (primitiveKind) {
                                Primitive.BYTE ->
                                    convertInteger(underlyingValue.code) { it.toByte() }
                                Primitive.CHAR -> underlyingValue
                                Primitive.INT -> convertInteger(underlyingValue.code) { it.toInt() }
                                Primitive.LONG -> convertInteger(underlyingValue.code) { it }
                                Primitive.SHORT ->
                                    convertInteger(underlyingValue.code) { it.toShort() }
                                else -> null
                            }
                        if (convertedValue != null) {
                            checkLossyConversion(underlyingValue, primitiveKind, convertedValue)
                        }
                        convertedValue
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
        private fun checkLossyConversion(original: Any, targetKind: Primitive, converted: Any) {
            val convertedNumber =
                when (converted) {
                    is Number -> converted
                    is Char -> converted.code
                    else -> error("unknown converted $converted of ${converted.javaClass}")
                }

            val roundTrip =
                when (original) {
                    is Byte -> convertedNumber.toByte()
                    is Char -> convertedNumber.toInt().toChar()
                    is Double -> convertedNumber.toDouble()
                    is Float -> convertedNumber.toFloat()
                    is Int -> convertedNumber.toInt()
                    is Long -> convertedNumber.toLong()
                    is Short -> convertedNumber.toShort()
                    else -> error("unknown original $original of ${original.javaClass}")
                }

            if (roundTrip != original) {
                error(
                    "Conversion of ${javaEscapeString(original.toString())} to ${targetKind.primitiveName} is lossy and produces $converted; round trip value is ${javaEscapeString(roundTrip.toString())}"
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
        private val EMPTY_ARRAY = DefaultArrayValue(emptyList(), wasUnwrappedInSource = false)

        /** Checks the [TypeItem] supplied to [createClassObjectValue]. */
        val classObjectValueTypeChecker =
            object : TypeVisitor {
                private fun invalidType(typeItem: TypeItem): Nothing {
                    error("'$typeItem' is an invalid type for a class object value")
                }

                override fun visit(arrayType: ArrayTypeItem) {
                    arrayType.componentType.accept(this)
                }

                override fun visit(classType: ClassTypeItem) {
                    if (classType.arguments.isNotEmpty()) {
                        error(
                            "'$classType' is an invalid type for a class object value as it has type arguments"
                        )
                    }
                }

                override fun visit(variableType: VariableTypeItem) {
                    invalidType(variableType)
                }

                override fun visit(wildcardType: WildcardTypeItem) {
                    invalidType(wildcardType)
                }
            }
    }
}

/** Type of values in [primitiveValueFactories]. */
internal typealias PrimitiveValueFactory<T> = (Any, Any) -> PrimitiveValue<T>
