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

package com.android.tools.metalava.model.testsuite.annotationitem

import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.annotation.AnnotationFilter
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.model.testing.value.annotationValue
import com.android.tools.metalava.model.testing.value.arrayValue
import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.assertValuesAreStrictlyEqual
import com.android.tools.metalava.model.testing.value.classObjectValue
import com.android.tools.metalava.model.testing.value.fieldReferenceValue
import com.android.tools.metalava.model.testing.value.literalValue
import com.android.tools.metalava.model.testing.value.primitiveValueForKind
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.RecordingReporter
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.Test

/** Annotation that is added on a line before the item being annotated. */
val lineBefore =
    java(
        """
            package test.pkg;

            public @interface LineBefore {
                String value();
            }
        """
    )

/** Annotation that is added on the same line as the item being annotated. */
val sameLine =
    java(
        """
            package test.pkg;

            public @interface SameLine {
                String value();
            }
        """
    )

/** Common tests for implementations of [AnnotationItem]. */
class CommonAnnotationItemTest : BaseModelTest() {

    /** Check the location information of the various parts of [item]. */
    private fun checkLocationInformation(item: Item, expectedLocations: String) {
        val details = mutableListOf<Pair<Int, String>>()
        val foo = item

        fun addDetails(fileLocation: FileLocation, description: String) {
            val line = fileLocation.line
            if (line == 0) return
            val detail = line to description
            if (detail !in details) {
                details.add(detail)
            }
        }

        foo.accept(
            object : BaseItemVisitor() {
                override fun visitItem(item: Item) {
                    item.modifiers.annotations().forEach {
                        addDetails(it.fileLocation, it.toString())
                    }
                    addDetails(item.fileLocation, item.describe())
                }
            }
        )
        val sorted = details.sortedWith(compareBy({ it.first }, { it.second }))
        val actualLocations = sorted.map { (line, details) -> "$line:$details" }.joinToString("\n")
        assertEquals(expectedLocations.trimIndent(), actualLocations)
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `annotation location (java)`() {
        runCodebaseTest(
            inputSet(
                lineBefore,
                sameLine,
                java(
                    """
                        package test.pkg;

                        @LineBefore("Foo")
                        @SameLine("Foo") public class Foo {
                            @LineBefore("constructor")
                            @SameLine("constructor") public Foo() {}
                            @LineBefore("field")
                            @SameLine("field") public int field;
                            @LineBefore("method")
                            @SameLine("method") public void method(
                                @LineBefore("parameter")
                                @SameLine("parameter") int p) {}
                        }
                    """
                ),
            ),
        ) {
            checkLocationInformation(
                codebase.assertClass("test.pkg.Foo"),
                """
                    3:@test.pkg.LineBefore("Foo")
                    4:@test.pkg.SameLine("Foo")
                    4:class test.pkg.Foo
                    5:@test.pkg.LineBefore("constructor")
                    6:@test.pkg.SameLine("constructor")
                    6:constructor test.pkg.Foo()
                    7:@test.pkg.LineBefore("field")
                    8:@test.pkg.SameLine("field")
                    8:field test.pkg.Foo.field
                    9:@test.pkg.LineBefore("method")
                    10:@test.pkg.SameLine("method")
                    10:method test.pkg.Foo.method(int)
                    11:@test.pkg.LineBefore("parameter")
                    12:@test.pkg.SameLine("parameter")
                    12:parameter p in test.pkg.Foo.method(int p)
                """
            )
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation location (kotlin)`() {
        runCodebaseTest(
            inputSet(
                lineBefore,
                sameLine,
                kotlin(
                    """
                        package test.pkg

                        @LineBefore("Foo")
                        @SameLine("Foo") class Foo {
                            @LineBefore("constructor")
                            @SameLine("constructor") constructor() {}
                            @LineBefore("field") @get:LineBefore("getter")
                            @SameLine("field") val field: Int
                            @LineBefore("method")
                            @SameLine("method") fun method(
                                @LineBefore("parameter")
                                @SameLine("parameter") p: Int) {}
                        }
                    """
                ),
            ),
        ) {
            checkLocationInformation(
                codebase.assertClass("test.pkg.Foo"),
                """
                    3:@test.pkg.LineBefore("Foo")
                    4:@test.pkg.SameLine("Foo")
                    4:class test.pkg.Foo
                    5:@test.pkg.LineBefore("constructor")
                    5:constructor test.pkg.Foo()
                    6:@test.pkg.SameLine("constructor")
                    7:@test.pkg.LineBefore("field")
                    7:@test.pkg.LineBefore("getter")
                    8:@test.pkg.SameLine("field")
                    8:field test.pkg.Foo.field
                    8:method test.pkg.Foo.getField()
                    8:property Foo.field
                    9:@test.pkg.LineBefore("method")
                    10:@test.pkg.SameLine("method")
                    10:method test.pkg.Foo.method(int)
                    11:@test.pkg.LineBefore("parameter")
                    12:@test.pkg.SameLine("parameter")
                    12:parameter p in test.pkg.Foo.method(int p)
                """
            )
        }
    }

    @Test
    fun `annotation with annotation values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                        annotationValue = @test.pkg.Other("other"),
                        annotationArrayValue = {@test.pkg.Other("other1"), @test.pkg.Other("other2")}
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public test.pkg.Other annotationValue();
                          method public test.pkg.Other[] annotationArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      annotationValue = @test.pkg.Other("other"),
                      annotationArrayValue = {@test.pkg.Other("other1"), @test.pkg.Other("other2")}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          Other annotationValue();
                          Other[] annotationArrayValue();
                        }
                    }

                    @interface Other {
                        String value();
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "annotationValue" to
                        annotationValue("test.pkg.Other", "value" to literalValue("other")),
                    "annotationArrayValue" to
                        arrayValue(
                            annotationValue("test.pkg.Other", "value" to literalValue("other1")),
                            annotationValue("test.pkg.Other", "value" to literalValue("other2")),
                        ),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with boolean values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          booleanValue = true,
                          booleanArrayValue = {true, false},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public boolean booleanValue();
                          method public boolean[] booleanArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      booleanValue = true,
                      booleanArrayValue = {true, false}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          boolean booleanValue();
                          boolean[] booleanArrayValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "booleanValue" to literalValue(true),
                    "booleanArrayValue" to arrayValueFromAny(true, false),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with char values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          charValue = 'a',
                          charArrayValue = {'a', '\uff00'},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public char charValue();
                          method public char[] charArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      charValue = 'a',
                      charArrayValue = {'a', '\uff00'}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          char charValue();
                          char[] charArrayValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "charValue" to literalValue('a'),
                    "charArrayValue" to arrayValueFromAny('a', '\uff00'),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with class values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          classValue = test.pkg.Test.class,
                          classArrayValue = {test.pkg.Test.class, test.pkg.Test.Anno.class}
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public Class<?> classValue();
                          method public Class<?>[] classArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      classValue = Test.class,
                      classArrayValue = {Test.class, Test.Anno.class}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          Class<?> classValue();
                          Class<?>[] classArrayValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val testClassTypeItem = classTypeItem("test.pkg.Test")
            val testClassObjectValue = classObjectValue(testClassTypeItem)
            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "classValue" to testClassObjectValue,
                    "classArrayValue" to
                        arrayValue(
                            testClassObjectValue,
                            classObjectValue(
                                classTypeItem(
                                    "test.pkg.Test.Anno",
                                    outerClassType = testClassTypeItem
                                )
                            ),
                        ),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with number values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          byteValue = 1,
                          byteArrayValue = {1, 2},

                          doubleValue = 1.5,
                          doubleArrayValue = {1.5, 2.5},

                          floatValue = 0.5f,
                          floatArrayValue = {0.5f, 1.5f},

                          intValue = 1,
                          intArrayValue = {1, 2, 3},

                          longValue = 2L,
                          longArrayValue = {2L, 4L},

                          shortValue = 3,
                          shortArrayValue = {3, 5},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public byte byteValue();
                          method public byte[] byteArrayValue();

                          method public double doubleValue();
                          method public double[] doubleArrayValue();

                          method public float floatValue();
                          method public float[] floatArrayValue();

                          method public int intValue();
                          method public int[] intArrayValue();

                          method public long longValue();
                          method public long[] longArrayValue();

                          method public short shortValue();
                          method public short[] shortArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      byteValue = 1,
                      byteArrayValue = {1, 2},

                      doubleValue = 1.5,
                      doubleArrayValue = {1.5, 2.5},

                      floatValue = 0.5F,
                      floatArrayValue = {0.5F, 1.5F},

                      intValue = 1,
                      intArrayValue = {1, 2, 3},

                      longValue = 2L,
                      longArrayValue = {2L, 4L},

                      shortValue = 3,
                      shortArrayValue = {3, 5}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          byte byteValue();
                          byte[] byteArrayValue();

                          double doubleValue();
                          double[] doubleArrayValue();

                          float floatValue();
                          float[] floatArrayValue();

                          int intValue();
                          int[] intArrayValue();

                          long longValue();
                          long[] longArrayValue();

                          short shortValue();
                          short[] shortArrayValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "byteValue" to literalValue(1.toByte()),
                    "byteArrayValue" to arrayValueFromAny(1.toByte(), 2.toByte()),
                    "doubleValue" to literalValue(1.5),
                    "doubleArrayValue" to arrayValueFromAny(1.5, 2.5),
                    "floatValue" to literalValue(0.5f),
                    "floatArrayValue" to arrayValueFromAny(0.5f, 1.5f),
                    "intValue" to literalValue(1),
                    "intArrayValue" to arrayValueFromAny(1, 2, 3),
                    "longValue" to literalValue(2L),
                    "longArrayValue" to arrayValueFromAny(2L, 4L),
                    "shortValue" to literalValue(3.toShort()),
                    "shortArrayValue" to arrayValueFromAny(3.toShort(), 5.toShort()),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with string values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          stringValue = "string",
                          stringArrayValue = {"string1", "string2"},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public String stringValue();
                          method public String[] stringArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      stringValue = "string",
                      stringArrayValue = {"string1", "string2"}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          String stringValue();
                          String[] stringArrayValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "stringValue" to literalValue("string"),
                    "stringArrayValue" to arrayValueFromAny("string1", "string2"),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation array values with single element`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno("string")
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public String[] value();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno("string")
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          String[] value();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            // It is expected to be of array type
            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to arrayValueFromAny("string"),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation array values with single array element`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno({"string"})
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public String[] value();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno({"string"})
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          String[] value();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            // It is expected to be of array type
            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to arrayValueFromAny("string"),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with enum values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          enumValue = test.pkg.Enum.ENUM1,
                          enumArrayValue = {test.pkg.Enum.ENUM1, test.pkg.Enum.ENUM2},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public Enum enumValue();
                          method public Enum[] enumArrayValue();
                      }

                      public enum Enum {
                        enum_constant public test.pkg.Enum ENUM1;
                        enum_constant public test.pkg.Enum ENUM2;
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      enumValue = Enum.ENUM1,
                      enumArrayValue = {Enum.ENUM1,Enum.ENUM2}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          Enum enumValue();
                          Enum[] enumArrayValue();
                        }
                    }

                    public enum Enum {
                      ENUM1,
                      ENUM2,
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "enumValue" to fieldReferenceValue("test.pkg.Enum", "ENUM1"),
                    "enumArrayValue" to
                        arrayValue(
                            fieldReferenceValue("test.pkg.Enum", "ENUM1"),
                            fieldReferenceValue("test.pkg.Enum", "ENUM2"),
                        ),
                )
            assertEquals(expectedAnno, anno)

            // Make sure that the enum value resolves to the enum field.
            val enumValue = anno.assertAttribute("enumValue").value as FieldReferenceValue
            val enum1Field = codebase.assertClass("test.pkg.Enum").assertField("ENUM1")
            assertSame(enum1Field, enumValue.resolve(), message = "enumValue.resolve()")

            val enumArrayValue =
                anno.assertAttribute("enumArrayValue").value.asFlatList().map {
                    it as FieldReferenceValue
                }
            assertSame(enum1Field, enumArrayValue[0].resolve(), "enumArrayValue[0].resolve()")
        }
    }

    @Test
    fun `annotation with constant literal values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(test.pkg.Test.FIELD)
                      public class Test {
                        ctor public Test();
                        field public static final int FIELD = 5;
                      }

                      public @interface Test.Anno {
                         method public int value();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(Test.FIELD)
                    public class Test {
                        public Test() {}

                        public static final int FIELD = 5;

                        public @interface Anno {
                          int value();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to fieldReferenceValue("test.pkg.Test", "FIELD"),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with unknown field`() {
        val reporter = RecordingReporter()
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          intValue = other.pkg.TestEnum.UNKNOWN,
                          intArrayValue = {TestEnum.UNKNOWN, UNKNOWN},
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public int intValue();
                          method public int[] intArrayValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;
                    import other.pkg.TestEnum;
                    import static other.pkg.TestEnum.UNKNOWN;

                    @Test.Anno(
                      intValue = other.pkg.TestEnum.UNKNOWN,
                      intArrayValue = {TestEnum.UNKNOWN, UNKNOWN}
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          int intValue();
                          int[] intArrayValue();
                        }
                    }
                """
            ),
            kotlin(
                """
                    package test.pkg
                    import other.pkg.TestEnum
                    import other.pkg.TestEnum.UNKNOWN

                    @Test.Anno(
                      intValue = other.pkg.TestEnum.UNKNOWN,
                      intArrayValue = [TestEnum.UNKNOWN, UNKNOWN]
                    )
                    class Test {
                        annotation class Anno(
                          val intValue: Int,
                          val intArrayValue: IntArray,
                        )
                    }
                """
            ),
            testFixture =
                TestFixture(
                    reporter = reporter,
                )
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val intValue = anno.assertAttribute("intValue").value
            assertValuesAreStrictlyEqual(
                intValue,
                fieldReferenceValue("other.pkg.TestEnum", "UNKNOWN")
            )

            val intArrayValue = anno.assertAttribute("intArrayValue").value
            assertValuesAreStrictlyEqual(
                intArrayValue,
                arrayValue(
                    fieldReferenceValue("TestEnum", "UNKNOWN"),
                    fieldReferenceValue("", "UNKNOWN"),
                )
            )
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `annotation with compound expression values`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    @Test.Anno(value = Test.FIELD1+Test.FIELD2, name = "FirstName"+"LastName", id = 1+Test.FIELD1)
                    public class Test {
                        public Test() {}

                        public static final int FIELD1 = 5;
                        public static final int FIELD2 = 7;

                        public @interface Anno {
                            int value();
                            String name();
                            int id();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to literalValue(12),
                    "name" to literalValue("FirstNameLastName"),
                    "id" to literalValue(6),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    private fun checkGetVsSetParamAnnotation(
        attributeType: String,
        attributePrimitive: Primitive,
        expectedAttributeString: String,
    ) {
        runCodebaseTest(
            kotlin(
                """
                        package test.pkg

                        annotation class Anno(val attr: $attributeType)

                        class Test {
                            @get:Anno(attr = 12) @setparam:Anno(attr = 12) var property = 0
                        }
                    """
            )
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val property = testClass.properties().single()

            val expectedValue = primitiveValueForKind(attributePrimitive, 12)

            // Test the annotation on the property (the @get:Anno).
            property.modifiers.annotations().single().let { anno ->
                assertValuesAreStrictlyEqual(
                    expectedValue,
                    anno.assertAttribute("attr").value,
                    message = "@get:Anno"
                )
            }

            // Test the annotation on the setter parameter (the @setparam:Anno).
            val setter = property.setter
            assertNotNull(setter, message = "setter method")
            val parameter = setter.parameters().single()
            parameter.modifiers.annotations().single().let { anno ->
                assertValuesAreStrictlyEqual(
                    expectedValue,
                    anno.assertAttribute("attr").value,
                    message = "@setparam:Anno"
                )
            }
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation on @get and @setparam annotations - byte`() {
        checkGetVsSetParamAnnotation(
            attributeType = "Byte",
            attributePrimitive = Primitive.BYTE,
            expectedAttributeString = "12",
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation on @get and @setparam annotations - short`() {
        checkGetVsSetParamAnnotation(
            attributeType = "Short",
            attributePrimitive = Primitive.SHORT,
            expectedAttributeString = "12",
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation on @get and @setparam annotations - long`() {
        checkGetVsSetParamAnnotation(
            attributeType = "Long",
            attributePrimitive = Primitive.LONG,
            expectedAttributeString = "12L",
        )
    }

    @Test
    fun `annotation with negative number values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno(
                          doubleValue = -1.5,
                          floatValue = -0.5F,
                          intValue = -1,
                          longValue = -2L,
                          shortValue = -3,
                      )
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public double doubleValue();
                          method public float floatValue();
                          method public int intValue();
                          method public long longValue();
                          method public short shortValue();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno(
                      doubleValue = -1.5,
                      floatValue = -0.5F,
                      intValue = -1,
                      longValue = -2L,
                      shortValue = -3
                    )
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          double doubleValue();
                          float floatValue();
                          int intValue();
                          long longValue();
                          short shortValue();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "doubleValue" to literalValue(-1.5),
                    "floatValue" to literalValue(-0.5F),
                    "intValue" to literalValue(-1),
                    "longValue" to literalValue(-2L),
                    "shortValue" to literalValue((-3).toShort()),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    // Does not work with signature files as they do not support casts.
    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `annotation with type cast values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno((int)5.6)
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public int value();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno((int)5.6f)
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          int value();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to literalValue(5),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @Test
    fun `annotation with infinity values`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno({java.lang.Double.POSITIVE_INFINITY,java.lang.Double.NEGATIVE_INFINITY})
                      public class Test {
                        ctor public Test();
                      }

                      public @interface Test.Anno {
                          method public double[] value();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    @Test.Anno({Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
                    public class Test {
                        public Test() {}

                        public @interface Anno {
                          double [] value();
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            val expectedAnno =
                annotationItem(
                    "test.pkg.Test.Anno",
                    "value" to
                        arrayValueFromAny(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                )
            assertEquals(expectedAnno, anno)
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation on @file`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        @file:RestrictTo(RestrictTo.Scope.LIBRARY)
                        package test.pkg

                        import androidx.annotation.RestrictTo

                        class Foo

                        const val CONSTANT = 1
                    """
                ),
                KnownSourceFiles.restrictToSource,
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.FooKt")
            val anno = testClass.modifiers.annotations().single()

            val attribute = anno.assertAttribute("value")
            val expected =
                arrayValue(
                    Value.createFieldReferenceValue(
                        codebase,
                        "androidx.annotation.RestrictTo.Scope",
                        "LIBRARY"
                    ),
                )
            val actual = attribute.value
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `annotation resolve`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @test.pkg.Test.Anno
                      public class Test {
                      }

                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Test.Anno {
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @Test.Anno
                    public class Test {
                        private Test() {}

                        @Retention(RetentionPolicy.CLASS)
                        public @interface Anno {
                        }
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val anno = testClass.modifiers.annotations().single()

            // Check that the annotation can be resolved to a class.
            val annoClass = anno.resolve()!!
            assertEquals("test.pkg.Test.Anno", annoClass.qualifiedName(), message = "anno class")

            // Check that the annotation can be resolved to a class.
            val retentionAnno = annoClass.modifiers.annotations().single()
            val retentionClass = retentionAnno.resolve()!!
            assertEquals(
                "java.lang.annotation.Retention",
                retentionClass.qualifiedName(),
                message = "retention class"
            )
        }
    }

    @Test
    fun `annotation targets - on source path`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        @SourcePathAnnotation
                        public class Test {
                            private Test() {}
                        }
                    """
                ),
                sourcePathFiles =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public @interface SourcePathAnnotation {}
                            """
                        ),
                    ),
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val annotationItem = testClass.modifiers.annotations().single()

            // Make sure that it correctly computes targets for an annotation class from the
            // source path.
            assertEquals(ANNOTATION_IN_ALL_STUBS, annotationItem.targets)
        }
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `annotation on internal`() {
        // Create a filter that will treat RestrictTo(Scope.LIBRARY) as a show annotation.
        val showFilter =
            AnnotationFilter.create(
                listOf(
                    "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY)",
                )
            )

        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg

                        import androidx.annotation.RestrictTo

                        // Defined during codebase construction as it is accessible because while it
                        // is internal it is annotated with a show annotation.
                        @RestrictTo(RestrictTo.Scope.LIBRARY)
                        internal class Foo

                        // Not defined during codebase construction as it is inaccessible because it
                        // is internal and while it has an annotation it is not a show annotation as
                        // the scope is incorrect.
                        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
                        internal class Bar

                        // Not defined during codebase construction as it is inaccessible because it
                        // is internal.
                        internal class Baz
                    """
                ),
                KnownSourceFiles.restrictToSource,
            ),
            testFixture =
                TestFixture(
                    DefaultAnnotationManager(
                        config =
                            DefaultAnnotationManager.Config(
                                allShowAnnotations = showFilter,
                                showAnnotations = showFilter,
                            )
                    )
                ),
        ) {
            // This should be defined.
            codebase.assertClass("test.pkg.Foo")
            // This should not be defined.
            codebase.assertResolvedClass("test.pkg.Bar")
            // This should not be defined.
            codebase.assertResolvedClass("test.pkg.Baz")
        }
    }
}
