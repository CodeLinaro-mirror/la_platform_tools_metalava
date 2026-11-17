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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.assertHasNonNullNullability
import com.android.tools.metalava.model.value.ClassObjectValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueStringConfiguration
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

/**
 * One off tests for [Value] related functionality that are not covered by the parameterized tests.
 */
class CommonValueTest : BaseModelTest() {
    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test reference to renamed companion object field`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.other

                        class Other {
                            companion object Friend {
                                const val FIELD = 1
                            }
                        }
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        import test.other.Other
                        annotation class Test(val attr: Int = Other.FIELD)
                    """
                ),
            )
        ) {
            val value = codebase.assertClass("test.pkg.Test").methods().single().defaultValue
            assertNotNull(value)
            assertEquals(
                "test.other.Other.Friend.FIELD",
                value.toValueString(ValueStringConfiguration(showKotlinCompanionClass = true))
            )
        }
    }

    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test class value reference for class with type parameters from kotlin`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                import kotlin.reflect.KClass
                class ClassWithTypeParam<T>

                annotation class AnnotationUsingClass(val classValue: KClass<*>)
                @JvmInline
                value class IntValue(val value: Int) {
                    @AnnotationUsingClass(classValue = ClassWithTypeParam::class)
                    fun foo() = Unit
                }
                """
            )
        ) {
            val intValueClass = codebase.assertClass("test.pkg.IntValue")
            val fooMethod = intValueClass.assertMethod("foo", emptyList())
            val anno = fooMethod.modifiers.annotations().single()
            val classValue = anno.attributes.single()
            assertEquals(classValue.name, "classValue")
            assertEquals(classValue.value.toValueString(), "test.pkg.ClassWithTypeParam.class")
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test use field reference in an annotation on a package`() {
        runCodebaseTest(
            inputSet(
                java(
                    "test/pkg/package-info.java",
                    """
                        @Anno(Anno.CONSTANT)
                        package test.pkg;
                    """,
                ),
                java(
                    """
                        package test.pkg;
                        public @interface Anno {
                            int value();

                            int CONSTANT = 37;
                        }
                    """
                )
            ),
        ) {
            val testItem = codebase.assertPackage("test.pkg")
            val annotationItem = testItem.modifiers.annotations().single()
            val annotationAttribute = annotationItem.attributes.single()
            val value = annotationAttribute.value
            assertEquals(fieldReferenceValue("test.pkg.Anno", "CONSTANT"), value)
            assertEquals(37, value.asLiteralValue()?.underlyingValue)
        }
    }

    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test use field reference in a type annotation`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;
                        @Target(ElementType.TYPE_USE)
                        public @interface Anno {
                            int value();

                            int CONSTANT = 37;
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public class Test {
                            public @Anno(Anno.CONSTANT) int field;
                        }
                    """,
                ),
            ),
        ) {
            val testItem = codebase.assertClass("test.pkg.Test")
            val fieldItem = testItem.fields().single()
            val annotationItem = fieldItem.type().modifiers.annotations.single()
            val annotationAttribute = annotationItem.attributes.single()
            val value = annotationAttribute.value
            assertEquals(fieldReferenceValue("test.pkg.Anno", "CONSTANT"), value)
            assertEquals(37, value.asLiteralValue()?.underlyingValue)
        }
    }

    @SupportedInputFormats(InputFormat.JAVA, InputFormat.SIGNATURE)
    @Test
    fun `Test array class literal nullability`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import java.util.BitSet;
                    public @interface Anno {
                        Class<?> value() default BitSet[].class;
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public @interface Anno {
                        method public abstract Class<?> value() default java.util.BitSet[].class;
                      }
                    }
                """
            ),
        ) {
            val anno = codebase.assertClass("test.pkg.Anno")
            val method = anno.methods().single()
            val value = method.defaultValue as ClassObjectValue
            val arrayType = value.typeItem as ArrayTypeItem

            // The array type of a class literal is non-null.
            arrayType.assertHasNonNullNullability(expectAnnotation = false)

            // The component type of an array class literal is also non-null.
            arrayType.componentType.assertHasNonNullNullability(expectAnnotation = false)
        }
    }
}
