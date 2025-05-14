/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.javaEscapeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/** Utility methods */
class CodePrinter {
    companion object {
        fun constantToSource(value: Any?): String {
            if (value == null) {
                return "null"
            }

            when (value) {
                is Int -> {
                    return value.toString()
                }
                is String -> {
                    return "\"${javaEscapeString(value)}\""
                }
                is Long -> {
                    return value.toString() + "L"
                }
                is Boolean -> {
                    return value.toString()
                }
                is Byte -> {
                    return value.toString()
                }
                is Short -> {
                    return value.toString()
                }
                is Float -> {
                    return when {
                        value == Float.POSITIVE_INFINITY -> "(1.0f/0.0f)"
                        value == Float.NEGATIVE_INFINITY -> "(-1.0f/0.0f)"
                        java.lang.Float.isNaN(value) -> "(0.0f/0.0f)"
                        else -> {
                            value.toString() + "f"
                        }
                    }
                }
                is Double -> {
                    return when {
                        value == Double.POSITIVE_INFINITY -> "(1.0/0.0)"
                        value == Double.NEGATIVE_INFINITY -> "(-1.0/0.0)"
                        java.lang.Double.isNaN(value) -> "(0.0/0.0)"
                        else -> {
                            value.toString()
                        }
                    }
                }
                is Char -> {
                    return String.format("'%s'", javaEscapeString(value.toString()))
                }
                is Pair<*, *> -> {
                    val first = value.first
                    val second = value.second
                    if (first is ClassId) {
                        val qualifiedName =
                            first.packageFqName.asString() +
                                "." +
                                first.relativeClassName.asString()
                        return if (second is Name) {
                            qualifiedName + "." + second.asString()
                        } else {
                            qualifiedName
                        }
                    }
                }
            }

            return value.toString()
        }

        internal fun constantToExpression(constant: Any?): String? {
            return when (constant) {
                is Int -> "0x${Integer.toHexString(constant)}"
                is String -> "\"${javaEscapeString(constant)}\""
                is Long -> "${constant}L"
                is Boolean -> constant.toString()
                is Byte -> Integer.toHexString(constant.toInt())
                is Short -> Integer.toHexString(constant.toInt())
                is Float -> {
                    when {
                        constant == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
                        constant == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
                        java.lang.Float.isNaN(constant) -> "Float.NaN"
                        else -> {
                            "${constant.toString()}F"
                        }
                    }
                }
                is Double -> {
                    when {
                        constant == Double.POSITIVE_INFINITY -> "Double.POSITIVE_INFINITY"
                        constant == Double.NEGATIVE_INFINITY -> "Double.NEGATIVE_INFINITY"
                        java.lang.Double.isNaN(constant) -> "Double.NaN"
                        else -> {
                            constant.toString()
                        }
                    }
                }
                is Char -> {
                    "'${javaEscapeString(constant.toString())}'"
                }
                else -> {
                    null
                }
            }
        }
    }
}
