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

class TextTypeItemTest {
    @Test
    fun `check erasure`() {
        // When a type variable is on a member and the type variable is defined on the surrounding
        // class, look up the bound on the class type parameter:
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package androidx.navigation {
              public final class NavDestination {
                ctor public NavDestination();
              }
              public class NavDestinationBuilder<D extends androidx.navigation.NavDestination> {
                ctor public NavDestinationBuilder(int id);
                method public D build();
              }
            }
            """
                    .trimIndent(),
            )
        val cls = codebase.findClass("androidx.navigation.NavDestinationBuilder")
        val method = cls?.findMethod("build", "") as TextMethodItem
        assertThat(method).isNotNull()
        assertThat(TextTypeParameterItem.bounds("D", method).toString())
            .isEqualTo("[androidx.navigation.NavDestination]")
    }

    @Test
    fun `check erasure from object`() {
        // When a type variable is on a member and the type variable is defined on the surrounding
        // class, look up the bound on the class type parameter:
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package test.pkg {
              public final class TestClass<D> {
                method public D build();
              }
            }
            """
                    .trimIndent(),
            )
        val cls = codebase.findClass("test.pkg.TestClass")
        val method = cls?.findMethod("build", "") as TextMethodItem
        assertThat(method).isNotNull()
    }

    @Test
    fun `check erasure from enums`() {
        // When a type variable is on a member and the type variable is defined on the surrounding
        // class, look up the bound on the class type parameter:
        val codebase =
            ApiFile.parseApi(
                "test",
                """
            // Signature format: 2.0
            package test.pkg {
              public class EnumMap<K extends java.lang.Enum<K>, V> extends java.util.AbstractMap implements java.lang.Cloneable java.io.Serializable {
                method public java.util.EnumMap<K, V> clone();
                method public java.util.Set<java.util.Map.Entry<K, V>> entrySet();
              }
            }
            """
                    .trimIndent(),
            )
        val cls = codebase.findClass("test.pkg.EnumMap")
        val method = cls?.findMethod("clone", "") as TextMethodItem
        assertThat(method).isNotNull()
    }

    @Test
    fun stripKotlinChars() {
        assertThat(TextTypeItem.stripKotlinNullChars("String?")).isEqualTo("String")
        assertThat(TextTypeItem.stripKotlinNullChars("String!")).isEqualTo("String")
        assertThat(TextTypeItem.stripKotlinNullChars("List<String?>")).isEqualTo("List<String>")
        assertThat(TextTypeItem.stripKotlinNullChars("Map<? extends K, ? extends V>"))
            .isEqualTo("Map<? extends K, ? extends V>")
        assertThat(TextTypeItem.stripKotlinNullChars("Map<?extends K,?extends V>"))
            .isEqualTo("Map<?extends K,?extends V>")
    }
}
