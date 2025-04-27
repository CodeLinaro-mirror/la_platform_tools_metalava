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
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.javaEscapeString
import com.android.tools.metalava.model.value.Value.Companion.toString
import java.util.EnumSet
import java.util.Objects

/**
 * Represents a value in a [Codebase].
 *
 * A [Value]'s primary purpose is to ensure consistent behavior irrespective of the source
 * expression from which it was created, i.e. consumers of [Value]s should not have to worry about
 * the source expression. e.g. assuming they are being assigned to a `long` field the following
 * expressions should result in [Value] instances which are equal to each other:
 * * `3000`
 * * `3000L`
 * * `3_000L`
 *
 * However, there is also a need to create exactly the same string representations of a [Value] as
 * are currently produced by the various legacy source representations, which often is affected by
 * the original source expression. That will require additional information to be kept in the
 * [Value] about the original source expression. Eventually, the goal will be to deprecate, remove
 * and stop supporting consuming the legacy source representations but this is needed in the
 * meantime.
 *
 * These two requirements are in conflict and will be resolved on the basis that consistent behavior
 * is more important in the long term so it will be prioritized for convenience and simplicity.
 *
 * Supporting the two requirements will be done by splitting the [Value] state into two sets as
 * described below.
 *
 * ### "Normalized State" ###
 *
 * This is the state that is independent of the particular form of the original source expression
 * from which it was created. Another way of describing it is the value that would be used at
 * runtime after the compiler has processed the expression, with the caveat that constant fields
 * will be preserved.
 *
 * It has the following characteristics:
 * 1. It will be accessible through [Value] interfaces.
 * 2. It will be included in the default output of [Value.toValueString].
 * 3. It will be compared using [equals] and hashed using [hashCode].
 *
 * That last point means it will not be possible to use a [Value] as a key where its legacy
 * representation is important.
 *
 * ### "Legacy State" ###
 *
 * This is the state that is dependent on some aspect of the particular form of the original source
 * expression.
 *
 * It has the following characteristics:
 * 1. It will NOT be accessible through [Value] interfaces.
 * 2. It will affect the output of [Value.toValueString] when given an appropriate
 *    [ValueStringConfiguration].
 * 3. It will be provided via implementations of [debugStringForValue] and used by [toString].
 *
 * That last point means that the [toString] value can be used as a key into a cache when the legacy
 * representation of the cached data is important, e.g. in testing or when preserving legacy
 * behavior in the output.
 *
 * Special support has been added assert equality of [Value]s when the legacy state is important.
 * See `assertValuesAreStrictlyEqual(...)`.
 */
sealed interface Value {
    /** The kind of this [Value]. */
    val kind: ValueKind

    /**
     * Get this [Value] as a [LiteralValue], or return `null` if it cannot be represented as one.
     *
     * This will return `null` for every [Value] except [LiteralValue] and maybe
     * [ConstantFieldValue], which will return delegate this call to its
     * [ConstantFieldValue.constantValue].
     */
    fun asLiteralValue(): LiteralValue<*>? = null

    /** Get this [Value] as a flat list of [ArrayElementValue]s. */
    fun asFlatList(): List<ArrayElementValue>

    /**
     * Create a snapshot for this suitable for use in [targetCodebase].
     *
     * This is needed as some [Value]s will reference items in the [Codebase].
     */
    fun snapshot(targetCodebase: Codebase) = this

    /**
     * A string representation of the value.
     *
     * See [appendValueStringTo] for more details.
     */
    fun toValueString(
        configuration: ValueStringConfiguration = ValueStringConfiguration.DEFAULT
    ): String

    /**
     * Append a string representation of this to [builder] as required by [configuration].
     *
     * There can be many different representations of each value but the default version used here
     * should be the simplest source representation of the value.
     *
     * By default, i.e. when [configuration] is equal to [ValueStringConfiguration.DEFAULT], this
     * will only include "Normalized State" in the returned [String]. However, with a suitable
     * [ValueStringConfiguration] it may include "Legacy State".
     */
    fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration = ValueStringConfiguration.DEFAULT
    )

    /**
     * Whether this value is equal to [other].
     *
     * This is implemented on each sub-interface of [Value] instead of [equals] because interfaces
     * are not allowed to implement [equals].
     *
     * Note: This must only compare "Normalized State", see [Value] for more information.
     */
    fun equalToValue(other: Value): Boolean

    /**
     * Hashcode for the type.
     *
     * This is implemented on each sub-interface of [Value] instead of [hashCode] because interfaces
     * are not allowed to implement [hashCode].
     *
     * Note: This must only hash "Normalized State", see [Value] for more information.
     */
    fun hashCodeForValue(): Int

    /**
     * Provides a string representation of the complete internal state, both "Normalized" and
     * "Legacy"; useful for debugging and testing.
     *
     * See [appendDebugStringTo] for more details.
     */
    @Deprecated(message = "Do not call directly", replaceWith = ReplaceWith("toString()"))
    fun debugStringForValue(): String

    /**
     * Appends a string representation of the complete internal state, both "Normalized" and
     * "Legacy", to [builder]; useful for debugging and testing.
     *
     * See [Value] for an explanation of the terms "Normalized" and "Legacy".
     *
     * The [toString] method (which calls this) should be used instead of calling this directly. To
     * encourage that this is deprecated.
     *
     * As this will provide access to "Legacy State" which cannot be exposed through these
     * interfaces this will need to be implemented in the implementation classes.
     */
    @Deprecated(message = "Do not call directly", replaceWith = ReplaceWith("toString()"))
    fun appendDebugStringTo(builder: StringBuilder) =
        appendValueStringTo(builder, ValueStringConfiguration.DEBUG)

    /** Append this [Value]'s [toString] result to [builder]. */
    fun appendToStringTo(builder: StringBuilder)

    /**
     * The string representation of a [Value] that includes the implementation class name as well as
     * [debugStringForValue].
     */
    override fun toString(): String

    /**
     * Companion object implements [ValueFactory] to allow factory methods to be accessed for
     * testing purposes using the object, e.g. [Value.createLiteralValue].
     */
    companion object : ValueFactory
}

/** Get this [Value] as an [Any], or `null` if it cannot be represented as an [Any]. */
fun Value.asAny() = asLiteralValue()?.underlyingValue

/** Get this [Value] as a [Boolean], or `null` if it cannot be represented as a [Boolean]. */
fun Value.asBoolean() = (asLiteralValue() as? BooleanValue)?.underlyingValue

/** Get this [Value] as a [Float], or `null` if it cannot be represented as a [Float]. */
fun Value.asFloat() = (asLiteralValue() as? FloatValue)?.underlyingValue

/** Get this [Value] as an [Int], or `null` if it cannot be represented as a [Int]. */
fun Value.asInt() = (asLiteralValue() as? IntValue)?.underlyingValue

/** Get this [Value] as a [String], or `null` if it cannot be represented as a [String]. */
fun Value.asString() = (asLiteralValue() as? StringValue)?.underlyingValue

/**
 * Configuration options for how to represent a value as a string.
 *
 * @param nestedValueAppender The function to use to append nested [Value]s to a [StringBuilder].
 * @param treatAsIntIfOriginallySpecifiedAsInt Whether to treat a `double`, `float`, or `long` as an
 *   `int` if it was originally specified as an `int`.
 * @param unwrapSingleArrayElement Whether to add braces around an array that contains only a single
 *   element.
 */
data class ValueStringConfiguration(
    val nestedValueAppender: (Value, StringBuilder, ValueStringConfiguration) -> Unit =
        Value::appendValueStringTo,
    val treatAsIntIfOriginallySpecifiedAsInt: Boolean = false,
    val unwrapSingleArrayElement: Boolean = false,
) {
    /** Use the [nestedValueAppender] to append a string representation of [Value] to [builder]. */
    fun appendNestedValueTo(builder: StringBuilder, value: Value) {
        nestedValueAppender(value, builder, this)
    }

    companion object {
        /** Default configuration. */
        val DEFAULT = ValueStringConfiguration()

        /** Debug configuration. */
        val DEBUG: ValueStringConfiguration =
            ValueStringConfiguration(
                // Use [appendToStringTo] for nested values.
                nestedValueAppender = { value, builder, _ -> value.appendToStringTo(builder) },
            )
    }
}

/** Enumeration of the different types of [ValueKind]. */
enum class ValueKind(val primitiveKind: Primitive? = null) {
    ANNOTATION,
    ARRAY,
    BOOLEAN(
        primitiveKind = Primitive.BOOLEAN,
    ),
    BYTE(
        primitiveKind = Primitive.BYTE,
    ),
    CHAR(
        primitiveKind = Primitive.CHAR,
    ),
    CLASS,
    CONSTANT_FIELD,
    DOUBLE(
        primitiveKind = Primitive.DOUBLE,
    ),
    ENUM,
    FLOAT(
        primitiveKind = Primitive.FLOAT,
    ),
    INT(
        primitiveKind = Primitive.INT,
    ),
    LONG(
        primitiveKind = Primitive.LONG,
    ),
    SHORT(
        primitiveKind = Primitive.SHORT,
    ),
    STRING,
    ;

    override fun toString() = super.toString().lowercase()

    companion object {
        /** The set of [ValueKind]s that represent primitive values. */
        private val PRIMITIVE_KINDS: Set<ValueKind> =
            EnumSet.noneOf(ValueKind::class.java).apply {
                addAll(entries.filter { it.primitiveKind != null })
            }

        /** The set of [ValueKind]s that represent literal values. */
        val LITERAL_KINDS: Set<ValueKind> = EnumSet.of(STRING).apply { addAll(PRIMITIVE_KINDS) }
    }
}

/** A [Value] that is allowed to be used in [ArrayValue.elements]. */
sealed interface ArrayElementValue : Value {
    override fun asFlatList() = listOf(this)
}

/** A [Value] that can be used in a constant field as defined by JLS 15.28. */
sealed interface ConstantValue : ArrayElementValue

/**
 * A [Value] that encapsulates an [underlyingValue] that can be either a primitive or a String.
 *
 * There is one sub-interface of this for each possible [LiteralValue]. The reason for doing that
 * rather than having a single [LiteralValue] containing an [Any] is it will provide type safety.
 */
sealed interface LiteralValue<T : Any> : ConstantValue {
    /**
     * The underlying value.
     *
     * Will be a primitive type's object wrapper (e.g. [java.lang.Integer]) or a [String].
     */
    val underlyingValue: T

    /** This is a [LiteralValue]. */
    override fun asLiteralValue() = this

    /**
     * Default implementation just appends the underlying value's standard [String.toString] value.
     */
    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue)
    }
}

/** A [LiteralValue] that is of a primitive type. */
sealed interface PrimitiveValue<T : Any> : LiteralValue<T>

/** A [Value] that encapsulates a [Boolean]. */
sealed interface BooleanValue : PrimitiveValue<Boolean> {
    override val kind: ValueKind
        get() = ValueKind.BOOLEAN

    override fun equalToValue(other: Value) =
        other is BooleanValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Byte]. */
sealed interface ByteValue : PrimitiveValue<Byte> {
    override val kind: ValueKind
        get() = ValueKind.BYTE

    override fun equalToValue(other: Value) =
        other is ByteValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Char]. */
sealed interface CharValue : PrimitiveValue<Char> {
    override val kind: ValueKind
        get() = ValueKind.CHAR

    override fun equalToValue(other: Value) =
        other is CharValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append('\'').append(javaEscapeString(underlyingValue.toString())).append('\'')
    }
}

/** A [Value] that encapsulates a [Double]. */
sealed interface DoubleValue : PrimitiveValue<Double> {
    override val kind: ValueKind
        get() = ValueKind.DOUBLE

    override fun equalToValue(other: Value) =
        other is DoubleValue &&
            (underlyingValue == other.underlyingValue ||
                (underlyingValue.isNaN() && other.underlyingValue.isNaN()))

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        when {
            underlyingValue.isNaN() -> builder.append("(0.0/0.0)")
            underlyingValue == Double.NEGATIVE_INFINITY -> builder.append("(-1.0/0.0)")
            underlyingValue == Double.POSITIVE_INFINITY -> builder.append("(1.0/0.0)")
            else -> builder.append(underlyingValue)
        }
    }

    companion object {
        val NaN: DoubleValue = DefaultDoubleValue(Double.NaN)
        val NEGATIVE_INFINITY: DoubleValue = DefaultDoubleValue(Double.NEGATIVE_INFINITY)
        val POSITIVE_INFINITY: DoubleValue = DefaultDoubleValue(Double.POSITIVE_INFINITY)
    }
}

/** A [Value] that encapsulates a [Float]. */
sealed interface FloatValue : PrimitiveValue<Float> {
    override val kind: ValueKind
        get() = ValueKind.FLOAT

    override fun equalToValue(other: Value) =
        other is FloatValue &&
            (underlyingValue == other.underlyingValue ||
                (underlyingValue.isNaN() && other.underlyingValue.isNaN()))

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        when {
            underlyingValue.isNaN() -> builder.append("(0.0f/0.0f)")
            underlyingValue == Float.NEGATIVE_INFINITY -> builder.append("(-1.0f/0.0f)")
            underlyingValue == Float.POSITIVE_INFINITY -> builder.append("(1.0f/0.0f)")
            else -> builder.append(underlyingValue).append("f")
        }
    }

    companion object {
        val NaN: FloatValue = DefaultFloatValue(Float.NaN)
        val NEGATIVE_INFINITY: FloatValue = DefaultFloatValue(Float.NEGATIVE_INFINITY)
        val POSITIVE_INFINITY: FloatValue = DefaultFloatValue(Float.POSITIVE_INFINITY)
    }
}

/** A [Value] that encapsulates a [Int]. */
sealed interface IntValue : PrimitiveValue<Int> {
    override val kind: ValueKind
        get() = ValueKind.INT

    override fun equalToValue(other: Value) =
        other is IntValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [Long]. */
sealed interface LongValue : PrimitiveValue<Long> {
    override val kind: ValueKind
        get() = ValueKind.LONG

    override fun equalToValue(other: Value) =
        other is LongValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(underlyingValue).append('L')
    }
}

/** A [Value] that encapsulates a [Short]. */
sealed interface ShortValue : PrimitiveValue<Short> {
    override val kind: ValueKind
        get() = ValueKind.SHORT

    override fun equalToValue(other: Value) =
        other is ShortValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()
}

/** A [Value] that encapsulates a [String]. */
sealed interface StringValue : LiteralValue<String> {
    override val kind: ValueKind
        get() = ValueKind.STRING

    override fun equalToValue(other: Value) =
        other is StringValue && underlyingValue == other.underlyingValue

    override fun hashCodeForValue() = underlyingValue.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append('"').append(javaEscapeString(underlyingValue)).append('"')
    }
}

/**
 * A [Value] that references a field in [classTypeItem] with name [fieldName].
 *
 * Sub-interfaces specialize this for [EnumConstantValue] and [ConstantFieldValue]. The reasons why
 * they are modelled as two separate interfaces are:
 * 1. They have different behavior, e.g. [ConstantFieldValue] has an optional [ConstantValue] but
 *    [EnumConstantValue] does not.
 * 2. The underlying models treat them differently, e.g. they need to do extra work to provide the
 *    [ConstantValue] for the [ConstantFieldValue].
 * 3. They are processed differently, e.g. sometimes the [ConstantFieldValue] is used directly,
 *    other times its [ConstantValue] is used.
 */
sealed interface FieldReferenceValue : ArrayElementValue {
    /** The class type that contains the field. */
    val classTypeItem: ClassTypeItem

    /** The name of the field. */
    val fieldName: String

    fun equalToFieldReferenceValue(other: FieldReferenceValue): Boolean {
        return classTypeItem == other.classTypeItem && fieldName == other.fieldName
    }

    fun hashCodeForFieldReferenceValue() = Objects.hash(classTypeItem, fieldName)

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(classTypeItem).append('.').append(fieldName)
    }
}

/** A [Value] that represents the initial value of a constant field. */
sealed interface ConstantFieldValue : FieldReferenceValue {
    override val kind: ValueKind
        get() = ValueKind.CONSTANT_FIELD

    /**
     * The optional constant value of this field.
     *
     * Is `null` if the field does not reference a constant value.
     */
    val constantValue: ConstantValue?

    /** The [constantValue], if present, may be a [LiteralValue]. */
    override fun asLiteralValue() = constantValue?.asLiteralValue()

    override fun equalToValue(other: Value) =
        other is ConstantFieldValue &&
            equalToFieldReferenceValue(other) &&
            constantValue == other.constantValue

    override fun hashCodeForValue() =
        hashCodeForFieldReferenceValue() * 31 + constantValue.hashCode()
}

/** A [Value] that represents an enum constant. */
sealed interface EnumConstantValue : FieldReferenceValue {
    override val kind: ValueKind
        get() = ValueKind.ENUM

    override fun equalToValue(other: Value) =
        other is EnumConstantValue && equalToFieldReferenceValue(other)

    override fun hashCodeForValue() = hashCodeForFieldReferenceValue() * 31
}

/** A [Value] wrapper around an [annotationItem]. */
sealed interface AnnotationValue : ArrayElementValue {
    override val kind: ValueKind
        get() = ValueKind.ANNOTATION

    /**
     * An annotation, used as a value in other annotations, including the default value of an
     * annotation's attribute method.
     */
    val annotationItem: AnnotationItem

    /**
     * Get this [AnnotationItem]'s [AnnotationItem.attributes] as a map from
     * [AnnotationAttribute.name] to [AnnotationAttribute.value].
     *
     * Used to implement [equalToValue] and [hashCodeForValue] to
     */
    private fun AnnotationItem.attributesMap() = attributes.associateBy({ it.name }) { it.value }

    override fun equalToValue(other: Value) =
        other is AnnotationValue &&
            annotationItem.attributesMap() == other.annotationItem.attributesMap()

    override fun hashCodeForValue() =
        annotationItem.qualifiedName.hashCode() * 31 + annotationItem.attributesMap().hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append("@")
        builder.append(annotationItem.qualifiedName)
        val attributes = annotationItem.attributes
        if (attributes.isNotEmpty()) {
            builder.append("(")
            var separator = ""
            for (attribute in attributes.sortedBy { it.name }) {
                builder.append(separator).append(attribute.name).append(" = ")

                configuration.appendNestedValueTo(builder, attribute.value)
                separator = ", "
            }
            builder.append(")")
        }
    }
}

/** A [Value] reference to a [Class] object. */
sealed interface ClassObjectValue : ArrayElementValue {
    override val kind: ValueKind
        get() = ValueKind.CLASS

    /**
     * The type whose [Class] object this encapsulates.
     *
     * Must be one of:
     * * A [PrimitiveTypeItem].
     * * A [ClassTypeItem] with no [ClassTypeItem.arguments].
     * * An [ArrayTypeItem] of one of these (including [ArrayTypeItem]).
     */
    val typeItem: TypeItem

    override fun equalToValue(other: Value) =
        other is ClassObjectValue && typeItem == other.typeItem

    override fun hashCodeForValue() = typeItem.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        builder.append(typeItem).append(".class")
    }
}

/** A [Value] that is an array whose contents are [elements]. */
sealed interface ArrayValue : Value {
    override val kind: ValueKind
        get() = ValueKind.ARRAY

    /** The array elements. */
    val elements: List<ArrayElementValue>

    override fun asFlatList() = elements

    override fun equalToValue(other: Value) = other is ArrayValue && elements == other.elements

    override fun hashCodeForValue() = elements.hashCode()

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        if (configuration.unwrapSingleArrayElement && elements.size == 1) {
            configuration.appendNestedValueTo(builder, elements[0])
        } else {
            builder.append('{')
            for ((index, element) in elements.withIndex()) {
                if (index > 0) {
                    builder.append(", ")
                }
                configuration.appendNestedValueTo(builder, element)
            }
            builder.append('}')
        }
    }
}

/** Base implementation of [Value]. */
internal sealed class DefaultValue : Value {
    final override fun equals(other: Any?): Boolean {
        if (other !is Value) return false
        return equalToValue(other)
    }

    final override fun hashCode(): Int = hashCodeForValue()

    final override fun toValueString(configuration: ValueStringConfiguration) = buildString {
        appendValueStringTo(this, configuration)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Do not call directly", replaceWith = ReplaceWith("toString()"))
    final override fun debugStringForValue() = buildString { appendDebugStringTo(this) }

    @Suppress("DEPRECATION")
    final override fun appendToStringTo(builder: StringBuilder) {
        builder.append(javaClass.simpleName)
        builder.append("(")
        builder.append(debugStringForValue())
        builder.append(")")
    }

    final override fun toString() = buildString { appendToStringTo(this) }
}
