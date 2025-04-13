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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.item.FieldValue
import com.android.tools.metalava.model.javaUnescapeString
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/** A [FieldValue] which defers the parsing of [text] until needed. */
class TextFieldValue(val type: TypeItem, val text: String) : FieldValue {
    private lateinit var cached: Optional<Any>

    override fun initialValue(requireConstant: Boolean): Any? {
        if (!::cached.isInitialized) {
            cached = Optional.ofNullable(parseValue(type, text))
        }
        return cached.getOrNull()
    }

    /** Override to return this so taking a snapshot does not fail if parsing fails. */
    override fun snapshot() = this

    private fun parseValue(
        type: TypeItem,
        value: String?,
    ): Any? {
        return if (value != null) {
            if (type is PrimitiveTypeItem) {
                parsePrimitiveValue(type, value)
            } else if (type.isString()) {
                if ("null" == value) {
                    null
                } else {
                    javaUnescapeString(value.substring(1, value.length - 1))
                }
            } else {
                value
            }
        } else null
    }

    private fun parsePrimitiveValue(
        type: PrimitiveTypeItem,
        value: String,
    ): Any {
        return when (type.kind) {
            Primitive.BOOLEAN ->
                if ("true" == value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
            Primitive.BYTE,
            Primitive.SHORT,
            Primitive.INT -> Integer.valueOf(value)
            Primitive.LONG -> java.lang.Long.valueOf(value.substring(0, value.length - 1))
            Primitive.FLOAT ->
                when (value) {
                    "(1.0f/0.0f)",
                    "(1.0f / 0.0f)" -> Float.POSITIVE_INFINITY
                    "(-1.0f/0.0f)",
                    "(-1.0f / 0.0f)" -> Float.NEGATIVE_INFINITY
                    "(0.0f/0.0f)",
                    "(0.0f / 0.0f)" -> Float.NaN
                    else -> java.lang.Float.valueOf(value)
                }
            Primitive.DOUBLE ->
                when (value) {
                    "(1.0/0.0)",
                    "(1.0 / 0.0)" -> Double.POSITIVE_INFINITY
                    "(-1.0/0.0)",
                    "(-1.0 / 0.0)" -> Double.NEGATIVE_INFINITY
                    "(0.0/0.0)",
                    "(0.0 / 0.0)" -> Double.NaN
                    else -> java.lang.Double.valueOf(value)
                }
            Primitive.CHAR -> value.toInt().toChar()
            Primitive.VOID -> error("Found value $value assigned to void type")
        }
    }
}
