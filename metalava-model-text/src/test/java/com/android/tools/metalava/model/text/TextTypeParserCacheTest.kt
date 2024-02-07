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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.testing.getAndroidTxt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Test the behavior of [TextTypeParser]#s caching. */
class TextTypeParserCacheTest : BaseTextCodebaseTest() {

    private data class Context(
        val codebase: TextCodebase,
        val parser: TextTypeParser,
        val emptyScope: TypeParameterScope,
        val nonEmptyScope: TypeParameterScope,
    )

    private fun runTextTypeParserTest(test: Context.() -> Unit) {
        runSignatureTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Generic<T> {
                        }
                    }
                """
            ),
        ) {
            val textCodebase = codebase as TextCodebase
            val parser =
                TextTypeParser(
                    textCodebase,
                    kotlinStyleNulls = false,
                )
            val nonEmptyScope = TypeParameterScope.from(codebase.assertClass("test.pkg.Generic"))
            val context =
                Context(
                    textCodebase,
                    parser,
                    TypeParameterScope.empty,
                    nonEmptyScope,
                )
            context.test()
        }
    }

    @Test
    fun `Test loading previously released public API`() {
        val androidTxtFiles =
            listOf("public", "system", "module-lib").map { surface -> getAndroidTxt(34, surface) }
        ApiFile.parseApi(
            androidTxtFiles,
            apiStatsConsumer = { stats ->
                assertThat(stats)
                    .isEqualTo(
                        ApiFile.Stats(
                            totalClasses = 7315,
                            typeCacheRequests = 179041,
                            typeCacheSkip = 9383,
                            typeCacheHit = 161407,
                            typeCacheSize = 8251,
                        )
                    )
            }
        )
    }

    @Test
    fun `Test empty scope is cached`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("int", emptyScope)
            val second = parser.obtainTypeFromString("int", emptyScope)

            assertThat(first).isSameInstanceAs(second)
        }
    }

    @Test
    fun `Test non-empty scope is not cached but could be`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("int", nonEmptyScope)
            val second = parser.obtainTypeFromString("int", nonEmptyScope)

            assertThat(first).isNotSameInstanceAs(second)
        }
    }

    @Test
    fun `Test type that references a type parameter is not cached`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("T", nonEmptyScope)
            val second = parser.obtainTypeFromString("T", nonEmptyScope)

            assertThat(first).isNotSameInstanceAs(second)
        }
    }

    @Test
    fun `Test caching of type variables`() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo<A> {
                        method public <B extends java.lang.String> void bar1(B p0);
                        method public <B extends java.lang.String> void bar2(B p0);
                        method public <C> void bar3(java.util.List<C> p0);
                        method public <C> void bar4(java.util.List<C> p0);
                      }
                    }
                """
            ),
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")
            assertThat(foo.methods()).hasSize(4)

            val bar1Param = foo.methods()[0].parameters()[0].type()
            val bar2Param = foo.methods()[1].parameters()[0].type()

            // The type variable should not be reused between methods
            assertThat(bar1Param).isNotSameInstanceAs(bar2Param)

            val bar3Param = foo.methods()[2].parameters()[0].type()
            val bar4Param = foo.methods()[3].parameters()[0].type()

            // The type referencing a type variable should not be reused between methods
            assertThat(bar3Param).isNotSameInstanceAs(bar4Param)
        }
    }

    @Test
    fun `Test caching of type variables collide with String`() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo {
                        method public void bar1(String);
                        method public <String> void bar2(String);
                        method public void bar3(String);
                      }
                    }
                """
            ),
        ) {
            val foo = codebase.assertClass("test.pkg.Foo")

            // Get the type of the parameter of all the methods.
            val (bar1Param, bar2Param, bar3Param) = foo.methods().map { it.parameters()[0].type() }

            // Even though all the method's parameter types are the same string representation they
            // have two different types.
            assertThat(bar1Param).isInstanceOf(ClassTypeItem::class.java)
            assertThat(bar2Param).isInstanceOf(VariableTypeItem::class.java)
            assertThat(bar3Param).isInstanceOf(ClassTypeItem::class.java)

            assertThat(bar1Param).isSameInstanceAs(bar3Param)
        }
    }

    @Test
    fun `Test caching of array components`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("String", emptyScope)
            val second = parser.obtainTypeFromString("String[]", emptyScope) as ArrayTypeItem
            val third = parser.obtainTypeFromString("String[][]", emptyScope) as ArrayTypeItem

            assertWithMessage("String === [] of String[]")
                .that(second.componentType)
                .isSameInstanceAs(first)
            assertWithMessage("String === [][] of String[][]")
                .that((third.componentType as ArrayTypeItem).componentType)
                .isSameInstanceAs(first)
            assertWithMessage("String[] !== [] of String[][]")
                .that(third.componentType)
                .isNotSameInstanceAs(second)
        }
    }

    @Test
    fun `Test caching of array with component annotations`() {
        runTextTypeParserTest {
            fun arrayTypeItem(type: String) =
                parser.obtainTypeFromString(type, emptyScope) as ArrayTypeItem

            val noAnno = arrayTypeItem("String[]")
            val withAnno1 = arrayTypeItem("@Anno1 String[]")
            val withAnno2 = arrayTypeItem("@Anno2 String[]")
            val withAnno1Again = arrayTypeItem("@Anno1 String[]")
            val withAnno1TwoDims = arrayTypeItem("@Anno1 String[][]")

            // Type without an annotation can never match a type with as annotation.
            for (withAnno in listOf(withAnno1, withAnno2, withAnno1Again)) {
                assertWithMessage("$noAnno not same as $withAnno")
                    .that(noAnno.componentType)
                    .isNotSameInstanceAs(withAnno1)
            }

            // Type with one annotation can never match a type with a different annotation.
            for (withAnno in listOf(withAnno1, withAnno1Again)) {
                assertWithMessage("$withAnno2 not same as $withAnno")
                    .that(noAnno.componentType)
                    .isNotSameInstanceAs(withAnno1)
            }

            // The exact same top level type are the same.
            assertWithMessage("withAnno1 and withAnno1Again")
                .that(withAnno1)
                .isSameInstanceAs(withAnno1Again)

            // Check the deepest components of withAnno1 and withAnnot1TwoDIms
            fun TypeItem.deepestComponent(): TypeItem =
                if (this is ArrayTypeItem) componentType.deepestComponent() else this

            // Their strings representations are the same.
            assertWithMessage(
                    "string representation of withAnno1.deepestComponent() and withAnno1TwoDims.deepestComponent()"
                )
                .that(withAnno1TwoDims.deepestComponent().toTypeString(annotations = true))
                .isEqualTo(withAnno1.deepestComponent().toTypeString(annotations = true))

            // But they are different instances as types with annotations are not cached..
            assertWithMessage(
                    "identity of withAnno1.deepestComponent() and withAnno1TwoDims.deepestComponent()"
                )
                .that(withAnno1TwoDims.deepestComponent())
                .isNotSameInstanceAs(withAnno1.deepestComponent())
        }
    }

    @Test
    fun `Test caching of generic type arguments`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("Number", emptyScope)
            val second = parser.obtainTypeFromString("List<Number>", emptyScope) as ClassTypeItem

            assertThat(second.arguments[0]).isSameInstanceAs(first)
        }
    }

    @Test
    fun `Test caching of wildcard extends bounds`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("Number", emptyScope)
            val second =
                parser.obtainTypeFromString("List<? extends Number>", emptyScope) as ClassTypeItem

            assertThat((second.arguments[0] as WildcardTypeItem).extendsBound)
                .isSameInstanceAs(first)
        }
    }

    @Test
    fun `Test caching of wildcard super bounds`() {
        runTextTypeParserTest {
            val first = parser.obtainTypeFromString("Number", emptyScope)
            val second =
                parser.obtainTypeFromString("List<? super Number>", emptyScope) as ClassTypeItem

            assertThat((second.arguments[0] as WildcardTypeItem).superBound).isSameInstanceAs(first)
        }
    }
}
