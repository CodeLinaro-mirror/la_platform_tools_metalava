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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.ModifierKeyword
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.testTypeString
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class CommonRecordClassTest : BaseModelTest() {

    /** Info available for a record component. */
    data class RecordComponentInfo(val name: String, val type: TypeItem)

    /** Assert that this [ClassItem] is a record class with [expectedComponents]. */
    fun ClassItem.assertRecord(vararg expectedComponents: RecordComponentInfo) {
        // Check the kind.
        assertEquals(ClassKind.RECORD, classKind, message = "class kind")

        // Check the modifiers.
        assertEquals(
            listOf(ModifierKeyword.PUBLIC_KEYWORD, ModifierKeyword.FINAL_KEYWORD),
            modifiers.keywordList,
            message = "keywords",
        )

        // Extract the components and check against the expected components.
        val components = recordComponents?.map { RecordComponentInfo(it.name, it.type) }
        assertEquals(expectedComponents.toList(), components, message = "components")

        // Find the canonical constructor.
        val canonicalConstructor =
            constructors().find {
                it.parameters().zip(expectedComponents).all { (parameter, component) ->
                    parameter.type() == component.type
                }
            }
        assertNotNull(canonicalConstructor, message = "canonical constructor")
        assertTrue(canonicalConstructor.isPrimary, message = "canonical constructor is primary")

        // Check for the accessor methods.
        for (component in expectedComponents) {
            val method = assertMethod(component.name, emptyList())
            assertEquals(
                component.type,
                method.returnType(),
                message = "method ${component.name} return type"
            )
        }
    }

    /** Create a [RecordComponentInfo]. */
    fun component(name: String, type: TypeItem) = RecordComponentInfo(name, type)

    @Test
    fun `Test empty record class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test() {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass.assertRecord()
        }
    }

    @Test
    fun `Test simple record class`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a, String b) {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test {
                        record_component #0 a: int;
                        record_component #1 b: String;
                        ctor public Test(int, String);
                        method public int a();
                        method public String b();
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            testClass.assertRecord(
                component("a", primitiveTypeForKind(PrimitiveTypeItem.Primitive.INT)),
                component("b", classTypeItem(JAVA_LANG_STRING)),
            )

            assertEquals(
                "record component test.pkg.Test#a",
                testClass.recordComponents!!["a"]!!.describe()
            )
        }
    }

    @Test
    fun `Test record class without explicit Object method overrides`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a) {
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodNames = testClass.methods().map { it.name() }.sorted()

            assertEquals(listOf("a"), methodNames)
        }
    }

    @Test
    fun `Test record class with explicit Object method overrides`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a) {
                        @Override public boolean equals(Object obj) {
                            return false;
                        }
                        @Override public int hashCode() {
                            return 0;
                        }
                        @Override public @NonNull String toString() {
                            return "";
                        }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            val methodNames = testClass.methods().map { it.name() }.sorted()

            assertEquals(listOf("a", "equals", "hashCode", "toString"), methodNames)
        }
    }

    @Test
    fun `Test record with compact constructor and implicit constructor parameters`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test(int a, String b) {
                        public Test {
                            if (a < 0 || b.length() > 5) {
                                throw IllegalArgumentException("blah");
                            }
                        }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertConstructor(listOf("int", "java.lang.String"))
        }
    }

    @Test
    fun `Test record with generic component type`() {
        runSourceCodebaseTest(
            java(
                """
                    package test.pkg;

                    public record Test<T>(T t) {
                    }
                """
            ),
            signature(
                """
                    // Signature format: 6.0
                    // - style=java
                    package test.pkg {
                      public record Test<T> {
                        record_component #0 c: T;
                        ctor public Test(T);
                        method public T c();
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            testClass.assertTypeParameter("T")
        }
    }

    @Test
    fun `Test record implements interface`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        public interface Interface {
                            int c();
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public record Test(int c) implements Interface {
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 6.0
                        // - style=java
                        package test.pkg {
                          public interface Interface {
                            method public int c();
                          }
                          public record Test implements test.pkg.Interface {
                            record_component #0 c: int;
                            ctor public Test(int);
                            method public int c();
                          }
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")
            assertEquals(listOf(classTypeItem("test.pkg.Interface")), testClass.interfaceTypes())
        }
    }

    @Test
    fun `Test record annotated component`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Target;

                        public record Test(
                            @RecordAnno
                            @FieldAnno
                            @MethodAnno
                            @ParameterAnno
                            @TypeAnno
                            @MixedAnno
                            int c
                        )  {
                        }

                        @Target(ElementType.FIELD)
                        public @interface FieldAnno {
                        }

                        @Target(ElementType.METHOD)
                        public @interface MethodAnno {
                        }

                        @Target(ElementType.PARAMETER)
                        public @interface ParameterAnno {
                        }

                        @Target(ElementType.RECORD_COMPONENT)
                        public @interface RecordAnno {
                        }

                        @Target(ElementType.TYPE_USE)
                        public @interface TypeAnno {
                        }

                        @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
                        public @interface MixedAnno {
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 6.0
                        // - style=java
                        // - include-type-use-annotations=yes
                        // - kotlin-name-type-order=yes
                        package test.pkg {
                          public record Test implements test.pkg.Interface {
                            record_component #0 @test.pkg.RecordAnno @test.pkg.MixedAnno c: @test.pkg.TypeAnno @test.pkg.MixedAnno int;
                            ctor public Test(@test.pkg.ParameterAnno @test.pkg.MixedAnno c: @test.pkg.TypeAnno @test.pkg.MixedAnno int);
                            method @test.pkg.MethodAnno @test.pkg.MixedAnno public c(): @test.pkg.TypeAnno @test.pkg.MixedAnno int;
                          }
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    javaLanguageLevel = "17",
                ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val annotations = buildString {
                testClass.accept(
                    object : BaseItemVisitor() {
                        override fun visitItem(item: Item) {
                            append(item.describe())
                            append("\n")
                            for (annotationName in item.annotationNames()) {
                                append("    @")
                                append(annotationName)
                                append("\n")
                            }

                            item.type()?.let { type ->
                                // Ignore type for class and constructor.
                                if (type is PrimitiveTypeItem) {
                                    append("    type: ")
                                    append(type.testTypeString(annotations = true))
                                    append("\n")
                                }
                            }
                        }
                    }
                )
            }

            assertEquals(
                """
                    class test.pkg.Test
                    constructor test.pkg.Test(int)
                    parameter c in test.pkg.Test(int c)
                        @test.pkg.ParameterAnno
                        @test.pkg.MixedAnno
                        type: @test.pkg.TypeAnno @test.pkg.MixedAnno int
                    method test.pkg.Test.c()
                        @test.pkg.MethodAnno
                        @test.pkg.MixedAnno
                        type: @test.pkg.TypeAnno @test.pkg.MixedAnno int
                    record component test.pkg.Test#c
                        @test.pkg.RecordAnno
                        @test.pkg.MixedAnno
                        type: @test.pkg.TypeAnno @test.pkg.MixedAnno int
                """
                    .trimIndent(),
                annotations.trim()
            )
        }
    }
}
