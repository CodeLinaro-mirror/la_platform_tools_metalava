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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.value.Value.Companion.toString
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** Base class for [FieldReferenceValue] implementations. */
internal abstract class BaseFieldReferenceValue(
    private val classResolver: ClassResolver,
    override val qualifiedClassName: String,
    override val fieldName: String,
    private val kotlinCompanionClass: String?,
    private val explicitConversionTo: Primitive?,
) : DefaultValue(), FieldReferenceValue {

    init {
        // Make sure that `explicitConversionTo`, if specified, has a numeric conversion function.
        require(
            explicitConversionTo == null ||
                explicitConversionTo.kotlinNumericConversionFunction != null
        ) {
            "Unexpected explicitConversionTo of $explicitConversionTo"
        }
    }

    override fun appendValueStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration
    ) {
        // If an inline field reference checker has been provided then invoke it to see whether the
        // field needs inlining. If it does, and it has a constant value that can be used instead
        // then inline it, otherwise drop through and append the field reference as normal.
        configuration.inlineFieldReferenceChecker?.let { inlineFieldReferenceValue ->
            if (inlineFieldReferenceValue(this)) {
                asLiteralValue()?.let { constantValue ->
                    configuration.nestedValueAppender(constantValue, builder, configuration)
                    return
                }
            }
        }

        if (kotlinCompanionClass != null && configuration.showKotlinCompanionClass) {
            builder.append(kotlinCompanionClass).append('.').append(fieldName)
        } else {
            super.appendValueStringTo(builder, configuration)
        }

        // If required, add an explicit conversion function call to the value representation.
        if (configuration.showKotlinConversionFunction && explicitConversionTo != null) {
            val conversionFunction = explicitConversionTo.kotlinNumericConversionFunction
            builder.append('.').append(conversionFunction).append("()")
        }
    }

    private lateinit var optionalFieldItem: Optional<FieldItem>

    /**
     * The optional constant value of this field.
     *
     * Is `null` if the field does not reference a constant value.
     *
     * Note: This is NOT used in [equals], [hashCode] or [toString]. That is because this may be
     * provided lazily and accessing it may have side effects but those methods are not expected to
     * have side effects.
     */
    protected abstract val constantValue: ConstantValue?

    /**
     * Implement this here rather than in [FieldReferenceValue] as it needs to access
     * [constantValue] which is an implementation detail.
     */
    override fun snapshot(targetContext: ValueContext) =
        Value.createFieldReferenceValue(
            targetContext,
            qualifiedClassName,
            fieldName,
            constantValue,
            kotlinCompanionClass,
            explicitConversionTo,
        )

    override fun resolve(): FieldItem? {
        if (!::optionalFieldItem.isInitialized) {
            if (qualifiedClassName == "") {
                optionalFieldItem = Optional.empty()
            } else {
                optionalFieldItem =
                    Optional.ofNullable(
                        classResolver
                            .resolveClass(qualifiedClassName)
                            ?.findField(
                                fieldName,
                                includeSuperClasses = true,
                                includeInterfaces = true,
                            )
                    )
            }
        }
        return optionalFieldItem.getOrNull()
    }
}

internal class DefaultFieldReferenceValue(
    classResolver: ClassResolver,
    qualifiedClassName: String,
    fieldName: String,
    override val constantValue: ConstantValue? = null,
    kotlinCompanionClass: String? = null,
    explicitConversionTo: Primitive? = null,
) :
    BaseFieldReferenceValue(
        classResolver,
        qualifiedClassName,
        fieldName,
        kotlinCompanionClass,
        explicitConversionTo,
    ) {

    /** The [constantValue], if present, may be a [LiteralValue]. */
    override fun asLiteralValue() = constantValue?.asLiteralValue()
}

internal class LazyFieldReferenceValue(
    classResolver: ClassResolver,
    qualifiedClassName: String,
    fieldName: String,
    private val optionalTypeItem: TypeItem?,
    kotlinCompanionClass: String?,
    explicitConversionTo: Primitive?,
) :
    BaseFieldReferenceValue(
        classResolver,
        qualifiedClassName,
        fieldName,
        kotlinCompanionClass,
        explicitConversionTo,
    ) {

    private lateinit var optionalConstantValue: Optional<ConstantValue>

    override val constantValue: ConstantValue?
        get() {
            if (!::optionalConstantValue.isInitialized) {
                optionalConstantValue = Optional.ofNullable(retrieveConstantValue())
            }

            return optionalConstantValue.getOrNull()
        }

    private fun retrieveConstantValue(): ConstantValue? {
        val fieldItem = resolve() ?: return null
        if (fieldItem.isEnumConstant()) return null

        // The actual constant value of a field reference is affected by the type of where it is
        // used, just as it would if the field reference was replaced by its constant value. So,
        // an `int` constant field that is used where a `long` is expected will be represented
        // as a `LongValue` that was originally specified as an int.
        //
        // A field reference is not a literal so a value retrieved from a field must always be
        // marked as non-literal.
        return fieldItem.constantValue?.convertToType(
            optionalTypeItem,
            forceNonLiteralInSource = true,
        )
    }

    /** The [optionalConstantValue], if present, may be a [LiteralValue]. */
    override fun asLiteralValue() = constantValue?.asLiteralValue()
}
