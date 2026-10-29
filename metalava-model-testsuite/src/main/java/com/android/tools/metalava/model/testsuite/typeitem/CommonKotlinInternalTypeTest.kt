/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runners.Parameterized

class CommonKotlinInternalTypeTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    data class TestParams(val kotlinType: String, val expectedJvmType: String)

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun test() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline value class IntValue(val value: Int)
                interface Foo {
                    fun foo(intValue: IntValue): ${params.kotlinType}
                }
                """
            )
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val fooMethod = fooClass.assertMethod("foo", listOf("test.pkg.IntValue"))
            assertThat(fooMethod.returnType().toTypeString()).isEqualTo(params.expectedJvmType)
        }
    }

    companion object {
        // Copied from [kotlin.jvm.internal.ClassReference.Companion.classFqNameOf]
        private val params =
            listOf(
                TestParams(
                    "kotlin.Boolean.Companion",
                    "kotlin.jvm.internal.BooleanCompanionObject"
                ),
                TestParams("kotlin.Char.Companion", "kotlin.jvm.internal.CharCompanionObject"),
                TestParams("kotlin.Double.Companion", "kotlin.jvm.internal.DoubleCompanionObject"),
                TestParams("kotlin.Enum.Companion", "kotlin.jvm.internal.EnumCompanionObject"),
                TestParams("kotlin.Float.Companion", "kotlin.jvm.internal.FloatCompanionObject"),
                TestParams("kotlin.Int.Companion", "kotlin.jvm.internal.IntCompanionObject"),
                TestParams("kotlin.Long.Companion", "kotlin.jvm.internal.LongCompanionObject"),
                TestParams("kotlin.Short.Companion", "kotlin.jvm.internal.ShortCompanionObject"),
                TestParams("kotlin.String.Companion", "kotlin.jvm.internal.StringCompanionObject"),
            )

        @JvmStatic @Parameterized.Parameters fun data() = params
    }
}
