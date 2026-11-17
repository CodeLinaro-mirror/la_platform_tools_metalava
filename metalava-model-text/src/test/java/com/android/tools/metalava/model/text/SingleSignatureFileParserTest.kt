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

package com.android.tools.metalava.model.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SingleSignatureFileParserTest : BaseTextCodebaseTest() {

    @Test
    fun testTypeParameterNames() {
        runSignatureTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                        public class Foo<X, DEF extends X, T extends java.lang.Comparable<? super T>, U extends java.util.List<java.lang.Number> & java.util.RandomAccess> {
                        }
                    }
                """
            ),
        ) {
            val typeParams = codebase.assertClass("test.pkg.Foo").typeParameterList
            assertThat(typeParams.map { it.name() }).containsExactly("X", "DEF", "T", "U").inOrder()
            assertThat(typeParams[0].typeBounds().map { it.toTypeString() })
                .containsExactly("java.lang.Object")
            assertThat(typeParams[1].typeBounds().map { it.toTypeString() }).containsExactly("X")
            assertThat(typeParams[2].typeBounds().map { it.toTypeString() })
                .containsExactly("java.lang.Comparable<? super T>")
            assertThat(typeParams[3].typeBounds().map { it.toTypeString() })
                .containsExactly("java.util.List<java.lang.Number>", "java.util.RandomAccess")
                .inOrder()
        }
    }
}
