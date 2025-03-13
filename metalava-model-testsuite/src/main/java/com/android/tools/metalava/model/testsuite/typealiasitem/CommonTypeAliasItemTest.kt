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

package com.android.tools.metalava.model.testsuite.typealiasitem

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.createAndroidModuleDescription
import com.android.tools.metalava.testing.createCommonModuleDescription
import com.android.tools.metalava.testing.createProjectDescription
import com.android.tools.metalava.testing.kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

// TODO(b/399628346): add signature file inputs
class CommonTypeAliasItemTest : BaseModelTest() {
    @Test
    fun `accessing type alias from codebase`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
        ) {
            codebase.assertTypeAlias("test.pkg.Foo")
        }
    }

    @Test
    fun `accessing type alias from package`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
        ) {
            val pkg = codebase.assertPackage("test.pkg")
            assertThat(pkg.typeAliases()).hasSize(1)
        }
    }

    @Test
    fun `type alias name`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                """
            ),
        ) {
            val typeAlias = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(typeAlias.qualifiedName).isEqualTo("test.pkg.Foo")
            assertThat(typeAlias.simpleName).isEqualTo("Foo")
        }
    }

    @Test
    fun `type alias visibility`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias PublicTypeAlias = String
                    internal typealias InternalTypeAlias = String
                    private typealias PrivateTypeAlias = String
                """
            ),
        ) {
            val publicTypeAlias = codebase.assertTypeAlias("test.pkg.PublicTypeAlias")
            assertThat(publicTypeAlias.modifiers.getVisibilityString()).isEqualTo("public")

            val internalTypeAlias = codebase.assertTypeAlias("test.pkg.InternalTypeAlias")
            assertThat(internalTypeAlias.modifiers.getVisibilityString()).isEqualTo("internal")

            val privateTypeAlias = codebase.assertTypeAlias("test.pkg.PrivateTypeAlias")
            assertThat(privateTypeAlias.modifiers.getVisibilityString()).isEqualTo("private")
        }
    }

    @Test
    fun `annotations on type alias`() {
        runCodebaseTest(
            inputSet(
                kotlin(
                    """
                        package test.pkg
                        typealias Unannotated = String
                        @AnnoA @AnnoB typealias Annotated = String
                    """
                ),
                kotlin(
                    """
                        package test.pkg
                        @Target(AnnotationTarget.TYPEALIAS)
                        annotation class AnnoA
                        @Target(AnnotationTarget.TYPEALIAS)
                        annotation class AnnoB
                    """
                )
            ),
        ) {
            val unannotated = codebase.assertTypeAlias("test.pkg.Unannotated")
            assertThat(unannotated.modifiers.annotations()).isEmpty()
            val annotated = codebase.assertTypeAlias("test.pkg.Annotated")
            val annotations = annotated.modifiers.annotations()
            assertThat(annotations).hasSize(2)
            assertThat(annotations.map { it.qualifiedName })
                .containsExactly("test.pkg.AnnoA", "test.pkg.AnnoB")
        }
    }

    @Test
    fun `basic type alias types`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias PrimitiveType = Int
                    typealias ArrayType = IntArray
                    typealias ClassType = String
                """
            ),
        ) {
            val primitiveType = codebase.assertTypeAlias("test.pkg.PrimitiveType").aliasedType
            primitiveType.assertPrimitiveTypeItem {
                assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
            }
            val arrayType = codebase.assertTypeAlias("test.pkg.ArrayType").aliasedType
            arrayType.assertArrayTypeItem {
                componentType.assertPrimitiveTypeItem {
                    assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
                }
            }
            val classType = codebase.assertTypeAlias("test.pkg.ClassType").aliasedType
            classType.assertClassTypeItem { assertThat(isString()).isTrue() }
        }
    }

    @Test
    fun `functional type alias type`() {
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias FunctionType = (String) -> Int
                """
            ),
        ) {
            val functionType = codebase.assertTypeAlias("test.pkg.FunctionType").aliasedType
            functionType.assertLambdaTypeItem {
                assertThat(parameterTypes).hasSize(1)
                assertThat(parameterTypes.single().isString()).isTrue()
                returnType.assertPrimitiveTypeItem {
                    assertThat(kind).isEqualTo(PrimitiveTypeItem.Primitive.INT)
                }
            }
        }
    }

    @Test
    fun `type alias referencing other type alias`() {
        // type aliases should be expanded to the underlying type
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias Foo = String
                    typealias Bar = List<Foo>
                """
            )
        ) {
            val foo = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(foo.aliasedType.isString()).isTrue()
            val bar = codebase.assertTypeAlias("test.pkg.Bar")
            bar.aliasedType.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                assertThat(arguments).hasSize(1)
                arguments.single().assertWildcardItem {
                    assertThat(extendsBound!!.isString()).isTrue()
                }
            }
        }
    }

    @Test
    fun `type parameter lists on type aliases`() {
        // Note: bounds are not allowed on type alias parameters. The aliased type is not allowed to
        // be a type parameter itself.
        runCodebaseTest(
            kotlin(
                """
                    package test.pkg
                    typealias NoTypeParameter = String
                    typealias OneTypeParameter<T> = List<T>
                    typealias TwoTypeParameter<K, V> = Map.Entry<K, V>
                """
            ),
        ) {
            val noTypeParameter = codebase.assertTypeAlias("test.pkg.NoTypeParameter")
            assertThat(noTypeParameter.typeParameterList).isEmpty()

            val oneTypeParameter = codebase.assertTypeAlias("test.pkg.OneTypeParameter")
            assertThat(oneTypeParameter.typeParameterList).hasSize(1)
            val t = oneTypeParameter.typeParameterList.single()
            assertThat(t.name()).isEqualTo("T")
            val listT = oneTypeParameter.aliasedType
            listT.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.List")
                assertThat(arguments).hasSize(1)
                arguments.single().assertWildcardItem {
                    extendsBound.assertVariableTypeItem {
                        assertThat(asTypeParameter).isEqualTo(t)
                        assertThat(t.type()).isEqualTo(this)
                    }
                }
            }

            val twoTypeParameter = codebase.assertTypeAlias("test.pkg.TwoTypeParameter")
            assertThat(twoTypeParameter.typeParameterList).hasSize(2)
            val k = twoTypeParameter.typeParameterList[0]
            assertThat(k.name()).isEqualTo("K")
            val v = twoTypeParameter.typeParameterList[1]
            assertThat(v.name()).isEqualTo("V")
            val mapEntryKV = twoTypeParameter.aliasedType
            mapEntryKV.assertClassTypeItem {
                assertThat(qualifiedName).isEqualTo("java.util.Map.Entry")
                assertThat(arguments).hasSize(2)
                arguments[0].assertWildcardItem {
                    extendsBound.assertVariableTypeItem {
                        assertThat(asTypeParameter).isEqualTo(k)
                        assertThat(k.type()).isEqualTo(this)
                    }
                }
                arguments[1].assertWildcardItem {
                    extendsBound.assertVariableTypeItem {
                        assertThat(asTypeParameter).isEqualTo(v)
                        assertThat(v.type()).isEqualTo(this)
                    }
                }
            }
        }
    }

    @Test
    fun `expect actual typealias`() {
        val commonSource =
            kotlin(
                "commonMain/src/test/pkg/Foo.kt",
                """
                    package test.pkg
                    expect class Foo
                """
            )
        val androidSource =
            kotlin(
                "androidMain/src/test/pkg/Foo.android.kt",
                """
                    package test.pkg
                    actual typealias Foo = String
                """
            )
        runCodebaseTest(
            inputSet(
                androidSource,
                commonSource,
            ),
            projectDescription =
                createProjectDescription(
                    createAndroidModuleDescription(arrayOf(androidSource)),
                    createCommonModuleDescription(arrayOf(commonSource)),
                ),
        ) {
            val fooAlias = codebase.assertTypeAlias("test.pkg.Foo")
            assertThat(fooAlias.aliasedType.isString()).isTrue()
        }
    }
}
