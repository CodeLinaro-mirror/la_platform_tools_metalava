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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.text.ParsedTypeParameter
import com.android.tools.metalava.model.type.TypeString
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [ErasedTypeDescriptorResolver]. */
class ErasedTypeDescriptorResolverTest {

    private val resolver = ErasedTypeDescriptorResolver()

    @Test
    fun `Primitive types`() {
        assertEquals("Z", resolver.resolveDescriptor("boolean"))
        assertEquals("B", resolver.resolveDescriptor("byte"))
        assertEquals("C", resolver.resolveDescriptor("char"))
        assertEquals("D", resolver.resolveDescriptor("double"))
        assertEquals("F", resolver.resolveDescriptor("float"))
        assertEquals("I", resolver.resolveDescriptor("int"))
        assertEquals("J", resolver.resolveDescriptor("long"))
        assertEquals("S", resolver.resolveDescriptor("short"))
        assertEquals("V", resolver.resolveDescriptor("void"))
    }

    @Test
    fun `Unqualified java_lang types`() {
        assertEquals("java/lang/Object", resolver.resolveInternalName("Object"))
        assertEquals("java/lang/String", resolver.resolveInternalName("String"))
        assertEquals("java/lang/Comparable", resolver.resolveInternalName("Comparable<String>"))
        assertEquals("java/lang/Thread\$State", resolver.resolveInternalName("Thread.State"))

        assertEquals("Ljava/lang/Object;", resolver.resolveDescriptor("Object"))
        assertEquals("Ljava/lang/String;", resolver.resolveDescriptor("String?"))
        assertEquals("Ljava/lang/Thread\$State;", resolver.resolveDescriptor("Thread.State!"))
    }

    @Test
    fun `Qualified classes and nested classes`() {
        assertEquals("test/pkg/Foo", resolver.resolveInternalName("test.pkg.Foo"))
        assertEquals("test/pkg/Outer\$Inner", resolver.resolveInternalName("test.pkg.Outer.Inner"))
        assertEquals(
            "test/pkg/Outer\$Middle\$Inner",
            resolver.resolveInternalName("test.pkg.Outer<String>.Middle<Integer>.Inner"),
        )
        assertEquals(
            "android/Manifest\$permission",
            resolver.resolveInternalName("android.Manifest.permission"),
        )
        assertEquals(
            "android/R\$style",
            resolver.resolveInternalName("android.R.style"),
        )

        assertEquals("Ltest/pkg/Foo;", resolver.resolveDescriptor("test.pkg.Foo"))
        assertEquals("Ltest/pkg/Outer\$Inner;", resolver.resolveDescriptor("test.pkg.Outer.Inner"))
        assertEquals(
            "Landroid/Manifest\$permission;",
            resolver.resolveDescriptor("android.Manifest.permission"),
        )
    }

    @Test
    fun `Annotations generics and nullability markers`() {
        assertEquals(
            "Ljava/util/List;",
            resolver.resolveDescriptor(
                "@androidx.annotation.NonNull java.util.List<@Nullable String?>?"
            ),
        )
        assertEquals(
            "Ltest/pkg/Outer\$Inner;",
            resolver.resolveDescriptor(
                "test.pkg.@test.pkg.Anno(value = \"a(b)<c>\") Outer<java.util.Map<String, int[]>>.@NonNull Inner!"
            ),
        )
        assertEquals(
            "java/util/Map\$Entry",
            resolver.resolveInternalName("java.util.Map<K, V>.@NonNull Entry<K, V>"),
        )
    }

    @Test
    fun `Arrays and varargs`() {
        assertEquals("[I", resolver.resolveDescriptor("int[]"))
        assertEquals("[[I", resolver.resolveDescriptor("int[][]"))
        assertEquals("[I", resolver.resolveDescriptor("int..."))
        assertEquals("[I", resolver.resolveDescriptor("int", isVarargs = true))
        assertEquals("[I", resolver.resolveDescriptor("int...", isVarargs = true))
        assertEquals("[[Ljava/lang/String;", resolver.resolveDescriptor("String?[]..."))
        assertEquals(
            "[[Ltest/pkg/Outer\$Inner;",
            resolver.resolveDescriptor("test.pkg.Outer<T>.Inner @NonNull [] @Nullable []"),
        )
    }

    @Test
    fun `Type parameter scopes and bounds`() {
        val outerScope =
            ErasedTypeScope.Empty.extend(
                listOf(
                    ParsedTypeParameter(name = "T", isReified = false, bounds = emptyList()),
                    ParsedTypeParameter(
                        name = "S",
                        isReified = false,
                        bounds =
                            listOf(
                                TypeString("CharSequence", 0),
                                TypeString("java.io.Serializable", 0),
                            ),
                    ),
                    ParsedTypeParameter(
                        name = "R",
                        isReified = false,
                        bounds = listOf(TypeString("Comparable<R>", 0)),
                    ),
                ),
                resolver,
            )

        assertEquals("Ljava/lang/Object;", resolver.resolveDescriptor("T", outerScope))
        assertEquals("[Ljava/lang/Object;", resolver.resolveDescriptor("T[]", outerScope))
        assertEquals("Ljava/lang/CharSequence;", resolver.resolveDescriptor("S", outerScope))
        assertEquals("[Ljava/lang/CharSequence;", resolver.resolveDescriptor("S...", outerScope))
        assertEquals("Ljava/lang/Comparable;", resolver.resolveDescriptor("R", outerScope))

        // Chained forward-referenced bounds (`<U extends V, V extends S>`) and shadowing (`<T
        // extends Number>`).
        val innerScope =
            outerScope.extend(
                listOf(
                    ParsedTypeParameter(
                        name = "U",
                        isReified = false,
                        bounds = listOf(TypeString("V", 0)),
                    ),
                    ParsedTypeParameter(
                        name = "V",
                        isReified = false,
                        bounds = listOf(TypeString("S", 0)),
                    ),
                    ParsedTypeParameter(
                        name = "T",
                        isReified = false,
                        bounds = listOf(TypeString("Number", 0)),
                    ),
                ),
                resolver,
            )

        assertEquals("Ljava/lang/CharSequence;", resolver.resolveDescriptor("U", innerScope))
        assertEquals("[Ljava/lang/CharSequence;", resolver.resolveDescriptor("U[]", innerScope))
        assertEquals("Ljava/lang/CharSequence;", resolver.resolveDescriptor("V", innerScope))
        assertEquals("Ljava/lang/Number;", resolver.resolveDescriptor("T", innerScope))
    }

    @Test
    fun `Source-style type formatting and qualified names`() {
        val scope =
            ErasedTypeScope.Empty.extend(
                listOf(ParsedTypeParameter(name = "T", isReified = false, bounds = emptyList())),
                resolver,
            )

        assertEquals("java.lang.Object", resolver.resolveQualifiedName("Object"))
        assertEquals(
            "test.pkg.Outer.Inner",
            resolver.resolveQualifiedName("test.pkg.Outer<String>.@NonNull Inner"),
        )
        assertEquals(
            "java.lang.Thread.State",
            resolver.resolveQualifiedName("Thread.State"),
        )

        fun formatSource(
            type: String,
            isVarargs: Boolean = false,
            spaceBetweenTypeArguments: Boolean = false,
        ): String = buildString {
            resolver.appendSourceType(
                this,
                type,
                scope = scope,
                isVarargs = isVarargs,
                spaceBetweenTypeArguments = spaceBetweenTypeArguments,
            )
        }

        assertEquals("int", formatSource("int"))
        assertEquals("java.lang.String", formatSource("String?"))
        assertEquals("T", formatSource("@Nullable T?"))
        assertEquals("java.lang.String...", formatSource("String?", isVarargs = true))
        assertEquals("java.lang.String[][]", formatSource("String? @NonNull [] @Nullable []"))
        assertEquals(
            "java.util.Map<java.lang.String,? extends T>",
            formatSource("java.util.Map<String?, ? extends T>"),
        )
        assertEquals(
            "java.util.Map<java.lang.String, ? extends T>",
            formatSource("java.util.Map<String?, ? extends T>", spaceBetweenTypeArguments = true),
        )
        assertEquals(
            "java.util.List<?>",
            formatSource("java.util.List<? extends Object>"),
        )
        assertEquals(
            "java.util.List<? super java.lang.String>",
            formatSource("java.util.List<? super String>"),
        )
    }
}
