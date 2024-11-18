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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

@Suppress("JavadocDeclaration")
class CommonApiSurfacesTest : BaseModelTest() {

    @Test
    fun `Test Codebase apiSurfaces default`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val apiSurfaces = codebase.apiSurfaces
            assertEquals("main", apiSurfaces.main.name, "main name")
            assertNull(apiSurfaces.base, "base not expected")
        }
    }

    @Test
    fun `Test Codebase apiSurfaces with base`() {
        val fixtureApiSurfaces = ApiSurfaces.create(needsBase = true)
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
            testFixture =
                TestFixture(
                    apiSurfaces = fixtureApiSurfaces,
                ),
        ) {
            val apiSurfaces = codebase.apiSurfaces
            assertEquals("main", apiSurfaces.main.name, "main name")
            assertEquals("base", apiSurfaces.base?.name, "base name")
        }
    }

    @Test
    fun `Test mutating selectedApiVariants`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            // Make sure that the selectedApiVariants is empty.
            testClass.mutateSelectedApiVariants { clear() }

            assertEquals(
                "ApiVariantSet[]",
                testClass.selectedApiVariants.toString(),
                "empty selectedApiVariants"
            )

            val mainStubsApiVariant = codebase.apiSurfaces.main.variantFor(ApiVariantType.DOC_ONLY)
            testClass.mutateSelectedApiVariants { add(mainStubsApiVariant) }
            assertEquals(
                "ApiVariantSet[main(D)]",
                testClass.selectedApiVariants.toString(),
                "mutated selectedApiVariants"
            )
        }
    }

    @Test
    fun `Test updating selectedApiVariants`() {
        runCodebaseTest(
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Test {
                            ctor public Test();
                            field public int field;
                            method public void foo(int);
                          }
                          public static class Test.Nested {
                            ctor public Test.Nested();
                          }
                        }
                    """
                ),
                signature(
                    "removed.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Test {
                            field public int removed;
                          }
                          public static class Test.Removed {
                            ctor public Test.Removed();
                          }
                        }
                    """
                ),
            ),
        ) {
            codebase.assertSelectedApiVariants(
                """
                    package test.pkg - ApiVariantSet[main(CR)]
                      class test.pkg.Test - ApiVariantSet[main(CR)]
                        constructor test.pkg.Test() - ApiVariantSet[main(C)]
                        method test.pkg.Test.foo(int) - ApiVariantSet[main(C)]
                        field test.pkg.Test.field - ApiVariantSet[main(C)]
                        field test.pkg.Test.removed - ApiVariantSet[main(R)]
                        class test.pkg.Test.Nested - ApiVariantSet[main(C)]
                          constructor test.pkg.Test.Nested() - ApiVariantSet[main(C)]
                        class test.pkg.Test.Removed - ApiVariantSet[main(R)]
                          constructor test.pkg.Test.Removed() - ApiVariantSet[main(R)]
                """
            )
        }
    }
}
