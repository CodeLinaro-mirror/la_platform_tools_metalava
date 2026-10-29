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

import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

@Suppress("JavadocDeclaration")
class CommonApiSurfacesTest : BaseModelTest() {
    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
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
            assertTrue(apiSurfaces.main.isMain, "main is main")
            assertNull(apiSurfaces.base, "base not expected")
        }
    }

    @SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
    @Test
    fun `Test Codebase apiSurfaces with base`() {
        val apiSurfaceRules = ApiSurfaceRules.create(needsBase = true)
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
                    apiSurfaceRules = apiSurfaceRules,
                ),
        ) {
            val apiSurfaces = codebase.apiSurfaces
            // No need to check the state of the ApiSurfaces, just that it is passed through to the
            // codebase untouched.
            assertSame(apiSurfaceRules.apiSurfaces, apiSurfaces, "api surfaces gets passed through")
        }
    }

    @RequiresCapabilities(Capability.MUTATE_SELECTED_API)
    @SupportedInputFormats(InputFormat.SIGNATURE)
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
        ) {
            val testClass = codebase.assertClass("test.pkg.Test")

            val selectedApi = testClass.selectedApi

            assertEquals(
                "ApiVariantSet[main(C)]",
                selectedApi.itemApiVariants.formatFor(codebase.apiSurfaces),
                "initial itemApiVariants"
            )

            val mainDocOnlyVariant = codebase.apiSurfaces.main.variantFor(ApiVariantType.DOC_ONLY)
            selectedApi.addItemApiVariant(mainDocOnlyVariant)
            assertEquals(
                "ApiVariantSet[main(CD)]",
                selectedApi.itemApiVariants.formatFor(codebase.apiSurfaces),
                "mutated itemApiVariants"
            )
        }
    }
}
