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
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.testTypeString
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
                    "${it.name()}: ${it.type().testTypeString(kotlinStyleNulls = true)}"
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

    @Test
    fun `Test generic data class all members`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    data class Foo<T>(val t: T?)
                """
            ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            val allMembers =
                (fooClass.fields().asSequence().map {
                        "${it.name()}: ${it.type().testTypeString(kotlinStyleNulls = true)}"
                    } +
                        (fooClass.constructors().asSequence() + fooClass.methods().asSequence())
                            .map { it.kotlinLikeDescription() })
                    .sorted()
                    .joinToString("\n")
            assertEquals(
                """
                    constructor Foo(t: T?): test.pkg.Foo<T>
                    fun component1(): T?
                    fun copy(t: T?): test.pkg.Foo<T>
                    fun equals(other: java.lang.Object?): boolean
                    fun getT(): T?
                    fun hashCode(): int
                    fun toString(): java.lang.String
                    t: T?
                """
                    .trimIndent(),
                allMembers
            )
        }
    }

    @Test
    fun `Test data class copy method visibility without CopyVisibility annotations`() {
        /*
        Currently, the default visibility for a data class copy method is public, regardless of the
        constructor visibility, unless ConsistentCopyVisibility is used. In the future, the default
        will flip so that the copy method visibility matches the constructor unless
        ExposedCopyVisibility is used.
        See https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-exposed-copy-visibility/
         */
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                data class PublicConstructor(val value: Int)
                data class InternalConstructor internal constructor(val value: Int)
                data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                data class PrivateConstructor private constructor(val value: Int)
                """
            )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor("int")
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", "int")
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor("int")
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalCtorCopy = internalCtorClass.assertMethod("copy", "int")
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor("int")
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            val internalPublishedCtorCopy = internalPublishedCtorClass.assertMethod("copy", "int")
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor("int")
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", "int")
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
        }
    }

    @Test
    fun `Test data class copy method visibility with ConsistentCopyVisibility`() {
        // @ConsistentCopyVisibility makes the copy method visibility match the constructor
        // b/414785453: @ConsistentCopyVisibility with internal constructor isn't working
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @ConsistentCopyVisibility
                data class PublicConstructor(val value: Int)
                @ConsistentCopyVisibility
                data class InternalConstructor internal constructor(val value: Int)
                @ConsistentCopyVisibility
                data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                @ConsistentCopyVisibility
                data class PrivateConstructor private constructor(val value: Int)
                """
            )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor("int")
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", "int")
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor("int")
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            // The copy method gets a mangled name with K2 (copy$<module name>).
            val internalCtorCopy =
                internalCtorClass.methods().single { it.name().startsWith("copy") }
            // TODO(b/414785453): copy should be internal
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor("int")
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            // The copy method gets a mangled name with K2 (copy$<module name>).
            val internalPublishedCtorCopy =
                internalPublishedCtorClass.methods().single { it.name().startsWith("copy") }
            // TODO(b/414785453): copy should be internal
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            // Note: @ConsistentCopyVisibility on an internal @PublishedApi constructor does not
            // make the copy method @PublishedApi, just internal.
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor("int")
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", "int")
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
        }
    }

    @Test
    fun `Test data class copy method visibility with ExposedCopyVisibility`() {
        // With @ExposedCopyVisibility, the copy method is always public.
        runCodebaseTest(
            kotlin(
                """
                        package test.pkg
                        @ExposedCopyVisibility
                        data class PublicConstructor(val value: Int)
                        @ExposedCopyVisibility
                        data class InternalConstructor internal constructor(val value: Int)
                        @ExposedCopyVisibility
                        data class InternalPublishedConstructor @PublishedApi internal constructor(val value: Int)
                        @ExposedCopyVisibility
                        data class PrivateConstructor private constructor(val value: Int)
                        """
            )
        ) {
            val publicCtorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicCtor = publicCtorClass.assertConstructor("int")
            assertThat(publicCtor.modifiers.getVisibilityLevel()).isEqualTo(VisibilityLevel.PUBLIC)
            val publicCtorCopy = publicCtorClass.assertMethod("copy", "int")
            assertThat(publicCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalCtorClass = codebase.assertClass("test.pkg.InternalConstructor")
            val internalCtor = internalCtorClass.assertConstructor("int")
            assertThat(internalCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            val internalCtorCopy = internalCtorClass.assertMethod("copy", "int")
            assertThat(internalCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)

            val internalPublishedCtorClass =
                codebase.assertClass("test.pkg.InternalPublishedConstructor")
            val internalPublishedCtor = internalPublishedCtorClass.assertConstructor("int")
            assertThat(internalPublishedCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.INTERNAL)
            assertThat(internalPublishedCtor.annotationNames()).contains("kotlin.PublishedApi")
            val internalPublishedCtorCopy = internalPublishedCtorClass.assertMethod("copy", "int")
            assertThat(internalPublishedCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
            assertThat(internalPublishedCtorCopy.annotationNames())
                .doesNotContain("kotlin.PublishedApi")

            val privateCtorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateCtor = privateCtorClass.assertConstructor("int")
            assertThat(privateCtor.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PRIVATE)
            val privateCtorCopy = privateCtorClass.assertMethod("copy", "int")
            assertThat(privateCtorCopy.modifiers.getVisibilityLevel())
                .isEqualTo(VisibilityLevel.PUBLIC)
        }
    }
}
