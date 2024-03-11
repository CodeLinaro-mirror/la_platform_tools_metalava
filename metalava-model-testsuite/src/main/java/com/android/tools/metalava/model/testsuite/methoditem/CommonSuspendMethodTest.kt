/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.methoditem

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Common tests for implementations of [MethodItem]. */
class CommonSuspendMethodTest : BaseModelTest() {

    @Test
    fun `Test suspend top level fun with nullable return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    suspend fun foo(): String? { error("") }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.TestKt").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.String?>): java.lang.Object?"
                )
        }
    }

    @Test
    fun `Test suspend top level fun with non-nullable return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    suspend fun foo(): String { error("") }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.TestKt").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.String>): java.lang.Object?"
                )
        }
    }

    @Test
    fun `Test suspend interface fun with nullable return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    interface Foo {
                        suspend fun foo(): String?
                    }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.String?>): java.lang.Object?"
                )
        }
    }

    @Test
    fun `Test suspend interface fun with non-nullable return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    interface Foo {
                        suspend fun foo(): String
                    }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.String>): java.lang.Object?"
                )
        }
    }

    @Test
    fun `Test suspend interface fun with primitive nullable return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    interface Foo {
                        suspend fun foo(): Int?
                    }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.Integer?>): java.lang.Object?"
                )
        }
    }

    @Test
    fun `Test suspend interface fun with primitive return type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    interface Foo {
                        suspend fun foo(): Int
                    }
                """
            ),
        ) {
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun foo(\$completion: kotlin.coroutines.Continuation<? super java.lang.Integer>): java.lang.Object?"
                )
        }
    }
}
