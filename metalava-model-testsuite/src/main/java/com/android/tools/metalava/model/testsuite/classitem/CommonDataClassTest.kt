/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Common tests for implementations of [ClassItem] that are `data` classes.
 *
 * Contains a couple of tests to give an overview of the members and then some more specific tests
 * for some synthetic methods. Although, they overlap with the overview tests they do make it easier
 * to track issues with the handling of the different forms of synthetic methods created as part of
 * a data class.
 */
@RequiresCapabilities(Capability.KOTLIN)
class CommonDataClassTest : BaseModelTest() {
    private val simpleDataClass =
        kotlin(
            """
                    package test.pkg
                    data class Foo(val i: Int, val s: String, var opt: String?)
                """
        )

    @Test
    fun `Test data class fields`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val fields =
                fooClass.fields().joinToString(separator = "\n") {
                    "${it.name()}: ${it.type().toTypeString(kotlinStyleNulls = true)}"
                }
            assertEquals(
                """
                    i: int
                    s: java.lang.String
                    opt: java.lang.String?
                """
                    .trimIndent(),
                fields
            )
        }
    }

    @Test
    fun `Test data class methods and constructors`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val constructorsAndMethods =
                fooClass.constructors().asSequence() + fooClass.methods().asSequence()
            val methods =
                constructorsAndMethods
                    .map { it.kotlinLikeDescription() }
                    .sorted()
                    .joinToString(separator = "\n")
            assertEquals(
                """
                    constructor Foo(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo
                    fun component1(): int
                    fun component2(): java.lang.String
                    fun component3(): java.lang.String?
                    fun copy(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo
                    fun equals(other: java.lang.Object?): boolean
                    fun getI(): int
                    fun getOpt(): java.lang.String?
                    fun getS(): java.lang.String
                    fun hashCode(): int
                    fun setOpt(<set-?>: java.lang.String?): void
                    fun toString(): java.lang.String
                """
                    .trimIndent(),
                methods
            )
        }
    }

    @Test
    fun `Test data class constructor`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val constructor = fooClass.constructors().single()
            assertThat(constructor.kotlinLikeDescription())
                .isEqualTo(
                    "constructor Foo(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo"
                )
        }
    }

    @Test
    fun `Test data class copy method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "copy" }
            assertThat(method.kotlinLikeDescription())
                .isEqualTo(
                    "fun copy(i: int, s: java.lang.String, opt: java.lang.String?): test.pkg.Foo"
                )
        }
    }

    @Test
    fun `Test data class getter method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "getOpt" }
            assertThat(method.kotlinLikeDescription()).isEqualTo("fun getOpt(): java.lang.String?")
        }
    }

    @Test
    fun `Test data class setter method`() {
        runCodebaseTest(
            simpleDataClass,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val method = fooClass.methods().single { it.name() == "setOpt" }
            assertThat(method.kotlinLikeDescription())
                .isEqualTo("fun setOpt(<set-?>: java.lang.String?): void")
        }
    }
}
