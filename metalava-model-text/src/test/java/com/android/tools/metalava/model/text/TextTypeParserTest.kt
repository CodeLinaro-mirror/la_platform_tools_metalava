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

package com.android.tools.metalava.model.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextTypeParserTest {
    @Test
    fun testTypeParameterStrings() {
        assertThat(TextTypeParser.typeParameterStrings(null).toString()).isEqualTo("[]")
        assertThat(TextTypeParser.typeParameterStrings("").toString()).isEqualTo("[]")
        assertThat(TextTypeParser.typeParameterStrings("<X>").toString()).isEqualTo("[X]")
        assertThat(TextTypeParser.typeParameterStrings("<ABC,DEF extends T>").toString())
            .isEqualTo("[ABC, DEF extends T]")
        assertThat(
                TextTypeParser.typeParameterStrings("<T extends java.lang.Comparable<? super T>>")
                    .toString()
            )
            .isEqualTo("[T extends java.lang.Comparable<? super T>]")
    }

    @Test
    fun `Test caching of type variables`() {
        val codebase =
            ApiFile.parseApi(
                "test",
                """
                    // Signature format: 4.0
                    package test.pkg {
                      public class Foo<A> {
                        method public void bar1<B extends java.lang.String>(B p0);
                        method public void bar2<B extends java.lang.String>(B p0);
                        method public void bar3<C>(java.util.List<C> p0);
                        method public void bar4<C>(java.util.List<C> p0);
                      }
                    }
                """
                    .trimIndent()
            )
        val foo = codebase.findClass("test.pkg.Foo")
        assertThat(foo).isNotNull()
        assertThat(foo!!.methods()).hasSize(4)

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
