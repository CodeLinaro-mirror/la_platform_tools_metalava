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

package com.android.tools.metalava.model.testing.value

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.ConstantValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.LiteralValue
import com.android.tools.metalava.model.value.PrimitiveValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueKind
import com.android.tools.metalava.model.value.ValueProviderException
import com.android.tools.metalava.model.value.provider
import com.android.tools.metalava.reporter.FileLocation
import java.util.EnumSet
import kotlin.test.assertEquals
import org.junit.AssumptionViolatedException

/** Create a [LiteralValue] from the [underlyingValue]. */
fun literalValue(underlyingValue: Any, nonLiteralInSource: Boolean = false) =
    Value.createLiteralValue(null, underlyingValue, nonLiteralInSource)

/** Create a [PrimitiveValue] of [kind] from the [underlyingValue]. */
fun primitiveValueForKind(
    kind: Primitive,
    underlyingValue: Any,
    nonLiteralInSource: Boolean = false,
) = Value.createLiteralValue(primitiveTypeForKind(kind), underlyingValue, nonLiteralInSource)

/** Create an [ArrayValue] containing [literals]. */
fun arrayValueFromAny(vararg literals: Any) =
    Value.createArrayValue(literals.map { literalValue(it) })

/** Create an [ArrayValue] containing [values]. */
fun arrayValue(vararg values: ArrayElementValue) = Value.createArrayValue(values.toList())

/** Create a [ClassObjectValue] containing [typeItem]. */
fun classObjectValue(typeItem: TypeItem, sourceExpression: String? = null) =
    Value.createClassObjectValue(typeItem, sourceExpression)

/**
 * Create a [FieldReferenceValue] called [fieldName] in [qualifiedClassName] with an optional
 * [constantValue].
 */
fun fieldReferenceValue(
    qualifiedClassName: String,
    fieldName: String,
    constantValue: ConstantValue? = null,
    kotlinCompanionClass: String? = null,
    explicitConversionTo: Primitive? = null,
) =
    Value.createFieldReferenceValue(
        ClassResolver.RETURN_NULL,
        qualifiedClassName,
        fieldName,
        constantValue,
        kotlinCompanionClass,
        explicitConversionTo,
    )

/**
 * Create a [FieldReferenceValue] called [fieldName] in [qualifiedClassName] whose [ConstantValue]
 * is determined lazily.
 */
fun lazyFieldReferenceValue(
    classResolver: ClassResolver,
    qualifiedClassName: String,
    fieldName: String,
    optionalTypeItem: TypeItem? = null,
) =
    Value.createFieldReferenceValueWithDeferredConstantValue(
        classResolver,
        qualifiedClassName,
        fieldName,
        optionalTypeItem,
    )

/** Create an [AnnotationValue] from [source]. */
fun annotationValueFromSource(source: String) =
    Value.createAnnotationValue(
        DefaultAnnotationItem.createFromSource(AnnotationContext.DEFAULT_RESOLVE_NULL, source)!!
    )

fun annotationValue(qualifiedClassName: String, vararg attributes: Pair<String, Value>) =
    Value.createAnnotationValue(annotationItem(qualifiedClassName, *attributes))

fun annotationItem(qualifiedClassName: String, vararg attributes: Pair<String, Value>) =
    DefaultAnnotationItem.createAttributesLazily(
        AnnotationContext.DEFAULT_RESOLVE_NULL,
        FileLocation.UNKNOWN,
        qualifiedClassName
    ) {
        attributes.map { (name, value) ->
            DefaultAnnotationAttribute(
                name,
                value.provider(),
            )
        }
    }!!

/**
 * The set of [ValueKind]s that are fully supported across models and so will be tested rigorously,
 * i.e. will not ignore [ValueProviderException]
 *
 * As each additional [ValueKind] is supported across the models they will be added here to ensure
 * that there are no regressions.
 */
private val fullySupportedValueKinds =
    EnumSet.noneOf(ValueKind::class.java).apply {
        addAll(ValueKind.LITERAL_KINDS)
        add(ValueKind.ANNOTATION)
        add(ValueKind.ARRAY)
        add(ValueKind.CLASS)
        add(ValueKind.FIELD)
    }

/**
 * Run a test on this [Value] ignoring any [ValueProviderException]s if its [Value.kind] is not
 * fully supported across model implementations.
 */
fun Value?.runValueTest(body: (Value?) -> Unit) {
    // Check whether this kind is fully supported, assume they are if this is null.
    val fullySupported = this?.kind?.let { kind -> kind in fullySupportedValueKinds } ?: true

    // ValueProviderExceptions are not treated as test failures if the value kind is not fully
    // supported to avoid having to keep updating baseline files while expanding Value support
    // across the models.
    // TODO(b/354633349): Stop ignoring exceptions.
    try {
        body(this)
    } catch (e: ValueProviderException) {
        if (fullySupported) {
            throw e
        } else {
            throw AssumptionViolatedException("Ignoring exception thrown while retrieving value", e)
        }
    }
}

/**
 * A special assertion check that performs strict testing of the values.
 *
 * It first uses [assertEquals] on the values to verify that the normalized value is equal according
 * to [Value.equals]. It then uses [assertEquals] on the [Value.toString] output which will include
 * any internal state, e.g. information preserved from the original source.
 */
fun assertValuesAreStrictlyEqual(v1: Value?, v2: Value?, message: String? = null) {
    val prefix = if (message == null) "" else "$message: "
    assertEquals(v1, v2, "${prefix}use equals()")
    assertEquals(v1?.toString(), v2?.toString(), "${prefix}compare toString()")
}
