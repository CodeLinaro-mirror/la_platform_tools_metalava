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
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.flags.AdditionFlagState
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction.*
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.flags.FlagState
import com.android.tools.metalava.model.api.flags.RemovalFlagState
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

@SupportedInputFormats(InputFormat.SIGNATURE, InputFormat.JAVA)
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
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiFlags = apiFlags,
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
            assertEquals(ApiFlag.KEEP_ALL, apiFlag, "apiFlag")
            assertEquals(ANNOTATION_IN_ALL_STUBS, annotation.targets, "targets")

            // An unconfigured @FlaggedApi has no effect on showability (showability is NO_EFFECT).
            assertFalse(annotation.isShowabilityAnnotation(), "isShowabilityAnnotation")
        }
    }

    @RequiresCapabilities(Capability.REVERTED_ITEMS)
    @Test
    fun `Test empty flags`() {
        runFlagsTest(
            apiFlags = ApiFlags(emptyList()),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            val expectedApiFlag = ApiFlag("test.pkg.flags.flag_name", REVERT, isKnown = false)
            assertEquals(expectedApiFlag, apiFlag, "apiFlag")
            assertEquals(NO_ANNOTATION_TARGETS, annotation.targets, "targets")

            // A reverted flag has no effect on showability (a flagged API is neither a show nor
            // a hide annotation).
            assertFalse(annotation.isShowabilityAnnotation(), "isShowabilityAnnotation")

            assertTrue(fooClass.selectedApi.revert, message = "class showability revert")

            fooClass.assertItemApiVariants("ApiVariantSet[]")

            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Foo.java:4: error: Cannot revert class test.pkg.Foo (or any other API item) as no previously released API has been provided [NoPreviouslyReleasedApi]
                """,
                message = "reporter issues"
            )
        }
    }

    @Test
    fun `Test with flags finalized`() {
        runFlagsTest(
            apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.flag_name", FINALIZE))),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertEquals(ApiFlag("test.pkg.flags.flag_name", FINALIZE), apiFlag, "apiFlag")
            assertEquals(NO_ANNOTATION_TARGETS, annotation.targets, "targets")

            // A finalized flag has no effect on showability (showability is NO_EFFECT).
            assertFalse(annotation.isShowabilityAnnotation(), "isShowabilityAnnotation")
        }
    }

    @Test
    fun `Test with flags keep`() {
        runFlagsTest(
            apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.flag_name", KEEP))),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            val annotation = fooClass.assertAnnotation(ANDROID_FLAGGED_API)

            val apiFlag = annotation.apiFlag
            assertEquals(ApiFlag("test.pkg.flags.flag_name", KEEP), apiFlag, "apiFlag")
            assertEquals(ANNOTATION_IN_ALL_STUBS, annotation.targets, "targets")

            // A kept flag has no effect on showability (showability is NO_EFFECT).
            assertFalse(annotation.isShowabilityAnnotation(), "isShowabilityAnnotation")
        }
    }

    /**
     * Verify that `@hide` and package-private members inside a visible `@FlaggedApi` class inherit
     * their enclosing class's [FlagState], whereas members of a `@hide` `@FlaggedApi` class have
     * [FlagState.NONE].
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test flagState on hidden members in FlaggedApi class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        @FlaggedApi("test.pkg.flags.flag_name")
                        public class VisibleFlagged {
                            /** @hide */
                            public void hiddenMethod() {}
                            void packagePrivateMethod() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        /** @hide */
                        @FlaggedApi("test.pkg.flags.flag_name")
                        public class HiddenFlagged {
                            public void member() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.flag_name", KEEP))),
                ),
        ) {
            val expectedFlagged = FlagState.additionFlag("test.pkg.flags.flag_name", KEEP)
            val visibleFlagged = codebase.assertClass("test.pkg.VisibleFlagged")
            assertEquals(expectedFlagged, visibleFlagged.selectedApi.flagState)
            assertEquals(
                expectedFlagged,
                visibleFlagged.assertMethod("hiddenMethod", emptyList()).selectedApi.flagState,
            )
            assertEquals(
                expectedFlagged,
                visibleFlagged
                    .assertMethod("packagePrivateMethod", emptyList())
                    .selectedApi
                    .flagState,
            )

            // Hidden enclosing classes (`rawItemApiVariants.isEmpty()`) do not propagate flags.
            val hiddenFlagged = codebase.assertClass("test.pkg.HiddenFlagged")
            assertSame(
                FlagState.NONE,
                hiddenFlagged.assertMethod("member", emptyList()).selectedApi.flagState,
            )
        }
    }

    /**
     * Verify [SelectedApi.flagState] edge cases for packages containing only `@removed` classes,
     * `@removed` items annotated with an addition `@FlaggedApi`, and invalid `@Removed`
     * `@FlaggedApi` method overrides.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test flagState on removed packages, removed FlaggedApi items, and invalid removed overrides`() {
        val apiSurfaces = ApiSurfaces.create()
        val apiSurfaceRules =
            ApiSurfaceRules(
                apiSurfaces,
                mapOf("main" to listOf(SurfaceSelectionRule.unannotated)),
                listOf(
                    SurfaceSelectionRule.createAnnotationRule(
                        "test.pkg.Removed",
                        effect = SurfaceSelectionRule.Effect.REMOVED,
                    )
                ),
            )

        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        @Retention(RetentionPolicy.SOURCE)
                        public @interface Removed {}
                    """
                ),
                java(
                    """
                        package test.removedpkg;
                        import android.annotation.FlaggedApi;
                        /** @removed */
                        @FlaggedApi("test.pkg.flags.flag_name")
                        public class RemovedFlaggedClass {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        public class Base {
                            public void activeMethod() {}
                        }
                        public class Sub extends Base {
                            @Removed
                            @FlaggedApi("test.pkg.flags.flag_name")
                            @Override
                            public void activeMethod() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiSurfaceRules = apiSurfaceRules,
                    apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.flag_name", KEEP))),
                ),
        ) {
            // A package containing only `@removed` classes has only `REMOVED` variants, but the
            // package itself must still have `FlagState.NONE`.
            val removedPkg = codebase.assertPackage("test.removedpkg")
            assertSame(FlagState.NONE, removedPkg.selectedApi.flagState)

            // A `@removed` item with an addition `@FlaggedApi` retains both its addition flag and
            // `RemovalFlagState.Removed`.
            val removedFlaggedClass = codebase.assertClass("test.removedpkg.RemovedFlaggedClass")
            assertEquals(
                FlagState(
                    addition = AdditionFlagState.Flagged(setOf("test.pkg.flags.flag_name")),
                    removal = RemovalFlagState.Removed,
                ),
                removedFlaggedClass.selectedApi.flagState,
            )

            // An invalid `@Removed @FlaggedApi` override of an active superclass method has its
            // removal cleared and ignores its own `@FlaggedApi` annotation.
            val subMethod =
                codebase.assertClass("test.pkg.Sub").assertMethod("activeMethod", emptyList())
            assertSame(FlagState.NONE, subMethod.selectedApi.flagState)
            assertAndRemoveReportedIssues(
                """
                    MAIN_SRC/src/test/pkg/Base.java:10: hidden: Attempting to remove method test.pkg.Sub.activeMethod() which overrides method test.pkg.Base.activeMethod() which is already part of the API [HidingApiMethodOverride]
                """
            )
        }
    }

    /**
     * Verify that an item with `@removed` and a reverted `@FlaggedApi` that reverts to an active
     * item in the previously released API has `removed == false` and [FlagState.NONE].
     */
    @RequiresCapabilities(Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test flagState on reverted item with removed doc tag`() {
        runCodebaseTest(
            java(
                """
                    package test.pkg;
                    public class Foo {
                        public void bar() {}
                    }
                """
            ),
            testFixture = TestFixture(checkSupportedInputFormats = false),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        public class Foo {
                            /** @removed */
                            @FlaggedApi("test.pkg.flags.reverted_flag")
                            public void bar() {}
                        }
                    """
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                        apiFlags =
                            ApiFlags(listOf(ApiFlag("test.pkg.flags.reverted_flag", REVERT))),
                        previouslyReleasedCodebase = previousCodebase,
                    ),
            ) {
                val barMethod =
                    codebase.assertClass("test.pkg.Foo").assertMethod("bar", emptyList())
                barMethod.assertItemApiVariants("ApiVariantSet[main(C)]")
                assertSame(FlagState.NONE, barMethod.selectedApi.flagState)
            }
        }
    }
}
