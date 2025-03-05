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

package com.android.tools.metalava.model.testsuite.flags

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

class CommonFlagTest : BaseModelTest() {

    private fun runFlagsTest(
        apiFlags: ApiFlags?,
        test: CodebaseContext.() -> Unit,
    ) {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    import android.annotation.FlaggedApi;
                    @FlaggedApi("test.pkg.flags.flag_name")
                    public class Foo {
                        private Foo() {}
                    }
                """
            ),
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @FlaggedApi("test.pkg.flags.flag_name") public class Foo {
                      }
                    }
                """
            ),
            testFixture =
                TestFixture(
                    annotationManager =
                        DefaultAnnotationManager(
                            DefaultAnnotationManager.Config(
                                apiFlags = apiFlags,
                            )
                        ),
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                ),
            test = test,
        )
    }

    @Test
    fun `Test no flags`() {
        runFlagsTest(
            apiFlags = null,
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertNull(apiFlag, "apiFlag")
            assertEquals(Showability.NO_EFFECT, annotation.showability, "showability")
            assertEquals(ANNOTATION_IN_ALL_STUBS, annotation.targets, "targets")
        }
    }

    @Test
    fun `Test empty flags`() {
        runFlagsTest(
            apiFlags = ApiFlags(byQualifiedName = emptyMap()),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertSame(ApiFlag.REVERT_FLAGGED_API, apiFlag, "apiFlag")
            assertEquals(Showability.REVERT_UNSTABLE_API, annotation.showability, "showability")
            assertEquals(NO_ANNOTATION_TARGETS, annotation.targets, "targets")
        }
    }

    @Test
    fun `Test with flags finalized`() {
        runFlagsTest(
            apiFlags =
                ApiFlags(
                    byQualifiedName =
                        mapOf("test.pkg.flags.flag_name" to ApiFlag.FINALIZE_FLAGGED_API)
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertSame(ApiFlag.FINALIZE_FLAGGED_API, apiFlag, "apiFlag")
            assertEquals(Showability.NO_EFFECT, annotation.showability, "showability")
            assertEquals(NO_ANNOTATION_TARGETS, annotation.targets, "targets")
        }
    }

    @Test
    fun `Test with flags keep`() {
        runFlagsTest(
            apiFlags =
                ApiFlags(
                    byQualifiedName = mapOf("test.pkg.flags.flag_name" to ApiFlag.KEEP_FLAGGED_API)
                ),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertSame(ApiFlag.KEEP_FLAGGED_API, apiFlag, "apiFlag")
            assertEquals(Showability.NO_EFFECT, annotation.showability, "showability")
            assertEquals(ANNOTATION_IN_ALL_STUBS, annotation.targets, "targets")
        }
    }
}
