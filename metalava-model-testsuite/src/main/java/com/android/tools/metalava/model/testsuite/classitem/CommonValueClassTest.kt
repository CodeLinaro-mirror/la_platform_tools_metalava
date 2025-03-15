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

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CommonValueClassTest : BaseModelTest() {
    @Test
    fun `Constructor visibility`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg

                    @JvmInline
                    value class PublicConstructor(val value: Int)

                    @JvmInline
                    value class PrivateConstructor private constructor(val value: Int)
                """
            )
        ) {
            val publicConstructorClass = codebase.assertClass("test.pkg.PublicConstructor")
            val publicConstructor = publicConstructorClass.constructors().single()
            assertTrue(publicConstructor.isPrimary)
            assertTrue(publicConstructor.modifiers.isPublic())

            val publicConstructorProperty = publicConstructorClass.properties().single()
            assertTrue(publicConstructorProperty.isPublic)
            assertNotNull(publicConstructorProperty.constructorParameter)
            assertNotNull(publicConstructorProperty.backingField)

            val privateConstructorClass = codebase.assertClass("test.pkg.PrivateConstructor")
            val privateConstructor = privateConstructorClass.constructors().single()
            assertTrue(privateConstructor.isPrimary)
            assertTrue(privateConstructor.modifiers.isPrivate())

            val privateConstructorProperty = privateConstructorClass.properties().single()
            // The constructor is private, but the property is public
            assertTrue(privateConstructorProperty.isPublic)
            assertNotNull(privateConstructorProperty.constructorParameter)
            assertNotNull(privateConstructorProperty.backingField)
        }
    }

    @Test
    fun `Secondary constructor`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class ValueClass(val value: Int) {
                        constructor(v1: Int, v2: Int) : this(v1 + v2)
                    }
                """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            assertEquals(valueClass.constructors().size, 2)

            val primaryConstructor = valueClass.assertConstructor("int")
            assertTrue(primaryConstructor.isPrimary)
            assertTrue(primaryConstructor.modifiers.isPublic())

            val secondaryConstructor = valueClass.assertConstructor("int,int")
            assertFalse(secondaryConstructor.isPrimary)
            assertTrue(secondaryConstructor.modifiers.isPublic())
        }
    }

    fun `Constructor with optional value`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline
                value class ValueClass(val value: Int = 0)
            """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            assertEquals(valueClass.constructors().size, 1, "Expected exactly one constructor")
            assertNotNull(valueClass.primaryConstructor, "Expected a primary constructor")

            val primaryConstructor = valueClass.constructors().single()
            assertTrue(primaryConstructor.isPrimary, "Expected a primary constructor")
            val param = primaryConstructor.parameters().single()
            assertTrue(param.hasDefaultValue(), "Expected a default value")
            assertEquals(param.defaultValue.value(), "0", "Expected a default value of 0")
        }
    }

    @Test
    fun `Property accessors`() {
        // Value class property accessors for non-constructor properties can't be used from Java
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class ValueClass(val value: Int) {
                        var noAccessors: Int
                            get() = 0
                            set(v: Int) {}
                    }
                """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            val ctorProperty = valueClass.assertProperty("value")
            assertNotNull(ctorProperty.getter)
            assertNull(ctorProperty.setter)
            assertNotNull(ctorProperty.constructorParameter)
            assertNotNull(ctorProperty.backingField)

            val noAccessorsProperty = valueClass.assertProperty("noAccessors")
            assertNull(noAccessorsProperty.getter)
            assertNull(noAccessorsProperty.setter)
            assertNull(noAccessorsProperty.constructorParameter)
            assertNull(noAccessorsProperty.backingField)
        }
    }

    @Test
    fun `Modifiers on APIs using value classes in an interface`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class IntValue(private val value: Int)
                    interface Interface {
                        val abstractVal: IntValue
                        fun abstractFun(): IntValue
                        val defaultVal: IntValue
                            get() = IntValue(0)
                        fun defaultFun(): IntValue = IntValue(0)
                    }
                """
            )
        ) {
            val interfaceClass = codebase.assertClass("test.pkg.Interface")
            // Abstract APIs on interface
            assertTrue(interfaceClass.assertProperty("abstractVal").modifiers.isAbstract())
            assertTrue(interfaceClass.assertMethod("getAbstractVal", "").modifiers.isAbstract())
            assertTrue(interfaceClass.assertMethod("abstractFun", "").modifiers.isAbstract())
            // Default APIs on interface
            assertTrue(interfaceClass.assertProperty("defaultVal").modifiers.isDefault())
            assertTrue(interfaceClass.assertMethod("getDefaultVal", "").modifiers.isDefault())
            assertTrue(interfaceClass.assertMethod("defaultFun", "").modifiers.isDefault())
        }
    }

    @Test
    fun `Modifiers on APIs using value classes in an abstract class`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    @JvmInline
                    value class IntValue(private val value: Int)
                    abstract class AbstractClass {
                        abstract val abstractVal: IntValue
                        abstract fun abstractFun(): IntValue
                        val finalVal: IntValue = IntValue(0)
                        fun finalFun(): IntValue = IntValue(0)
                    }
                """
            )
        ) {
            val abstractClass = codebase.assertClass("test.pkg.AbstractClass")
            // Abstract APIs on abstract class
            assertTrue(abstractClass.assertProperty("abstractVal").modifiers.isAbstract())
            assertTrue(abstractClass.assertMethod("getAbstractVal", "").modifiers.isAbstract())
            assertTrue(abstractClass.assertMethod("abstractFun", "").modifiers.isAbstract())
            // Final APIs on abstract class
            assertTrue(abstractClass.assertProperty("finalVal").modifiers.isFinal())
            assertTrue(abstractClass.assertMethod("getFinalVal", "").modifiers.isFinal())
            assertTrue(abstractClass.assertMethod("finalFun", "").modifiers.isFinal())
        }
    }
}
