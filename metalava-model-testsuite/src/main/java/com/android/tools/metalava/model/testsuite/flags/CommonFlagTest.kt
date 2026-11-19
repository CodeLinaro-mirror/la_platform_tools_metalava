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
import com.android.tools.metalava.model.snapshot.CodebaseSnapshotTaker
import com.android.tools.metalava.model.snapshot.NonFilteringDelegatingVisitor
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

    /**
     * Verify [SelectedApi.flagState] on previously released vs newly added members and nested
     * classes (including extending API surfaces, `@doconly`, and `@removed` items) inside an
     * already-released `@FlaggedApi` class.
     */
    @RequiresCapabilities(Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test flagState on members in previously released FlaggedApi class`() {
        val apiSurfaces = ApiSurfaces.create(needsBase = true)
        val apiSurfaceRules =
            ApiSurfaceRules(
                apiSurfaces = apiSurfaces,
                byName =
                    mapOf(
                        "base" to listOf(SurfaceSelectionRule.unannotated),
                        "main" to
                            listOf(
                                SurfaceSelectionRule.createAnnotationRule("test.pkg.SystemApi"),
                            ),
                    ),
                variantRules =
                    listOf(
                        SurfaceSelectionRule.createAnnotationRule(
                            "test.pkg.DocOnly",
                            effect = SurfaceSelectionRule.Effect.DOC_ONLY,
                        ),
                    ),
            )
        val surfaceAnnotations =
            java(
                """
                    package test.pkg;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface SystemApi {}
                    @Retention(RetentionPolicy.SOURCE)
                    public @interface DocOnly {}
                """
            )

        runCodebaseTest(
            inputSet(
                surfaceAnnotations,
                java(
                    """
                        package test.pkg;
                        public class ReleasedOuter {
                            public void releasedMethod() {}
                            public static class ReleasedNested {
                                private ReleasedNested() {}
                                public void releasedNestedMethod() {}
                            }
                        }
                        /** @removed */
                        public class ReleasedRemovedOuter {
                            public void releasedRemovedMethod() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiSurfaceRules = apiSurfaceRules,
                    checkSupportedInputFormats = false,
                ),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                inputSet(
                    surfaceAnnotations,
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            @FlaggedApi("test.pkg.flags.outer_flag")
                            public class ReleasedOuter {
                                public void releasedMethod() {}
                                public void newUnflaggedMethod() {}
                                @FlaggedApi("test.pkg.flags.inner_flag")
                                public void newFlaggedMethod() {}
                                @SystemApi
                                public void newSystemMethod() {}
                                /** @removed */
                                public void unflaggedRemovedMethod() {}
                                public static class NewUnflaggedNested {
                                    public NewUnflaggedNested() {}
                                    public void method() {}
                                }
                                @SystemApi
                                public static class NewSystemNested {
                                    public NewSystemNested() {}
                                    public void method() {}
                                }
                                @DocOnly
                                public static class NewDocOnlyNested {
                                    public NewDocOnlyNested() {}
                                    public void method() {}
                                }
                                public static class ReleasedNested {
                                    private ReleasedNested() {}
                                    public void releasedNestedMethod() {}
                                    public void newNestedMethod() {}
                                }
                            }
                            /** @removed */
                            @FlaggedApi("test.pkg.flags.outer_flag")
                            public class ReleasedRemovedOuter {
                                public void releasedRemovedMethod() {}
                                @SystemApi
                                public void newSystemRemovedMethod() {}
                            }
                        """
                    ),
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                        apiSurfaceRules = apiSurfaceRules,
                        apiFlags =
                            ApiFlags(
                                listOf(
                                    ApiFlag("test.pkg.flags.outer_flag", KEEP),
                                    ApiFlag("test.pkg.flags.inner_flag", KEEP),
                                ),
                            ),
                        previouslyReleasedCodebase = previousCodebase,
                    ),
            ) {
                val outerFlagged = FlagState.additionFlag("test.pkg.flags.outer_flag", KEEP)
                val innerFlagged = FlagState.additionFlag("test.pkg.flags.inner_flag", KEEP)

                val outer = codebase.assertClass("test.pkg.ReleasedOuter")
                outer.assertItemApiVariants("ApiVariantSet[base(C)]")
                assertSame(FlagState.NONE, outer.selectedApi.flagState)
                assertSame(
                    FlagState.NONE,
                    outer.assertMethod("releasedMethod", emptyList()).selectedApi.flagState,
                )

                // New unflagged members and nested classes in a previously released @FlaggedApi
                // class inherit `outer_flag`.
                assertEquals(
                    outerFlagged,
                    outer.assertMethod("newUnflaggedMethod", emptyList()).selectedApi.flagState,
                )

                // Explicitly flagged new members in a previously released outer class are governed
                // solely by their own `@FlaggedApi` annotation (`inner_flag`), not `outer_flag`.
                assertEquals(
                    innerFlagged,
                    outer.assertMethod("newFlaggedMethod", emptyList()).selectedApi.flagState,
                )

                val newSystemMethod = outer.assertMethod("newSystemMethod", emptyList())
                newSystemMethod.assertItemApiVariants("ApiVariantSet[main(C)]")
                assertEquals(outerFlagged, newSystemMethod.selectedApi.flagState)

                // An unflagged `@removed` member inside a CORE-only `@FlaggedApi` class does not
                // inherit the CORE class's addition flag.
                val unflaggedRemovedMethod =
                    outer.assertMethod("unflaggedRemovedMethod", emptyList())
                unflaggedRemovedMethod.assertItemApiVariants("ApiVariantSet[base(R)]")
                assertSame(FlagState.REMOVED, unflaggedRemovedMethod.selectedApi.flagState)

                val newNested = codebase.assertClass("test.pkg.ReleasedOuter.NewUnflaggedNested")
                assertEquals(outerFlagged, newNested.selectedApi.flagState)
                assertEquals(
                    outerFlagged,
                    newNested.assertMethod("method", emptyList()).selectedApi.flagState,
                )

                val newSystemNested = codebase.assertClass("test.pkg.ReleasedOuter.NewSystemNested")
                newSystemNested.assertItemApiVariants("ApiVariantSet[main(C)]")
                assertEquals(outerFlagged, newSystemNested.selectedApi.flagState)
                assertEquals(
                    outerFlagged,
                    newSystemNested.assertMethod("method", emptyList()).selectedApi.flagState,
                )

                val newDocOnlyNested =
                    codebase.assertClass("test.pkg.ReleasedOuter.NewDocOnlyNested")
                newDocOnlyNested.assertItemApiVariants("ApiVariantSet[base(D)]")
                val newDocOnlyMethod = newDocOnlyNested.assertMethod("method", emptyList())
                newDocOnlyMethod.assertItemApiVariants("ApiVariantSet[base(D)]")
                assertEquals(outerFlagged, newDocOnlyNested.selectedApi.flagState)
                assertEquals(outerFlagged, newDocOnlyMethod.selectedApi.flagState)

                val releasedNested = codebase.assertClass("test.pkg.ReleasedOuter.ReleasedNested")
                assertSame(FlagState.NONE, releasedNested.selectedApi.flagState)
                assertSame(
                    FlagState.NONE,
                    releasedNested
                        .assertMethod("releasedNestedMethod", emptyList())
                        .selectedApi
                        .flagState,
                )
                assertEquals(
                    outerFlagged,
                    releasedNested
                        .assertMethod("newNestedMethod", emptyList())
                        .selectedApi
                        .flagState,
                )

                val releasedRemovedOuter = codebase.assertClass("test.pkg.ReleasedRemovedOuter")
                releasedRemovedOuter.assertItemApiVariants("ApiVariantSet[base(R)]")
                assertSame(FlagState.REMOVED, releasedRemovedOuter.selectedApi.flagState)
                assertSame(
                    FlagState.REMOVED,
                    releasedRemovedOuter
                        .assertMethod("releasedRemovedMethod", emptyList())
                        .selectedApi
                        .flagState,
                )

                val newSystemRemoved =
                    releasedRemovedOuter.assertMethod("newSystemRemovedMethod", emptyList())
                newSystemRemoved.assertItemApiVariants("ApiVariantSet[main(R)]")
                assertEquals(
                    FlagState(
                        addition = AdditionFlagState.Flagged(setOf("test.pkg.flags.outer_flag")),
                        removal = RemovalFlagState.Removed,
                    ),
                    newSystemRemoved.selectedApi.flagState,
                )
            }
        }
    }

    /**
     * Verify [SelectedApi.flagState] on unflagged method overrides of `@FlaggedApi` methods,
     * including transitive overrides and overrides of multiple super methods.
     */
    @Test
    fun `Test flagState on unflagged method overrides`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        public class Base {
                            @FlaggedApi("test.pkg.flags.flag_a")
                            public void flaggedMethod() {}
                        }
                        public class Sub extends Base {
                            @Override
                            public void flaggedMethod() {}
                        }
                        public class SubSub extends Sub {
                            @Override
                            public void flaggedMethod() {}
                        }
                        public class UnflaggedBase {
                            public void mixedMethod() {}
                        }
                        @FlaggedApi("test.pkg.flags.flag_a")
                        public interface FlaggedIfaceA {
                            void mixedMethod();
                            void bothFlagged();
                        }
                        @FlaggedApi("test.pkg.flags.flag_b")
                        public interface FlaggedIfaceB {
                            void bothFlagged();
                        }
                        public class MixedSub extends UnflaggedBase implements FlaggedIfaceA {
                            @Override
                            public void mixedMethod() {}
                        }
                        public class BothFlaggedSub implements FlaggedIfaceA, FlaggedIfaceB {
                            @Override
                            public void bothFlagged() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          public class Base {
                            ctor public Base();
                            method @FlaggedApi("test.pkg.flags.flag_a") public void flaggedMethod();
                          }
                          public class Sub extends test.pkg.Base {
                            ctor public Sub();
                            method public void flaggedMethod();
                          }
                          public class SubSub extends test.pkg.Sub {
                            ctor public SubSub();
                            method public void flaggedMethod();
                          }
                          public class UnflaggedBase {
                            ctor public UnflaggedBase();
                            method public void mixedMethod();
                          }
                          @FlaggedApi("test.pkg.flags.flag_a") public interface FlaggedIfaceA {
                            method public void bothFlagged();
                            method public void mixedMethod();
                          }
                          @FlaggedApi("test.pkg.flags.flag_b") public interface FlaggedIfaceB {
                            method public void bothFlagged();
                          }
                          public class MixedSub extends test.pkg.UnflaggedBase implements test.pkg.FlaggedIfaceA {
                            ctor public MixedSub();
                            method public void mixedMethod();
                          }
                          public class BothFlaggedSub implements test.pkg.FlaggedIfaceA, test.pkg.FlaggedIfaceB {
                            ctor public BothFlaggedSub();
                            method public void bothFlagged();
                          }
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag("test.pkg.flags.flag_a", KEEP),
                                ApiFlag("test.pkg.flags.flag_b", KEEP),
                            ),
                        ),
                ),
        ) {
            val flagA = FlagState.additionFlag("test.pkg.flags.flag_a", KEEP)
            val bothFlags =
                FlagState(
                    addition =
                        AdditionFlagState.Flagged(
                            setOf("test.pkg.flags.flag_a", "test.pkg.flags.flag_b"),
                        ),
                )
            assertEquals(
                flagA,
                codebase
                    .assertClass("test.pkg.Base")
                    .assertMethod("flaggedMethod", emptyList())
                    .selectedApi
                    .flagState,
            )

            // Unflagged overrides of a `@FlaggedApi` method inherit `flag_a` (including
            // transitively).
            assertEquals(
                flagA,
                codebase
                    .assertClass("test.pkg.Sub")
                    .assertMethod("flaggedMethod", emptyList())
                    .selectedApi
                    .flagState,
            )
            assertEquals(
                flagA,
                codebase
                    .assertClass("test.pkg.SubSub")
                    .assertMethod("flaggedMethod", emptyList())
                    .selectedApi
                    .flagState,
            )

            // Overriding both an unflagged method (`UnflaggedBase.mixedMethod`) and a flagged
            // interface method (`FlaggedIfaceA.mixedMethod`) remains finalized because the
            // unflagged super method keeps the override in the API even if `flag_a` is reverted.
            assertSame(
                FlagState.NONE,
                codebase
                    .assertClass("test.pkg.MixedSub")
                    .assertMethod("mixedMethod", emptyList())
                    .selectedApi
                    .flagState,
            )

            // When all overridden super methods are `@FlaggedApi`, the override inherits their
            // combined flags (`flag_a`, `flag_b`).
            assertEquals(
                bothFlags,
                codebase
                    .assertClass("test.pkg.BothFlaggedSub")
                    .assertMethod("bothFlagged", emptyList())
                    .selectedApi
                    .flagState,
            )
        }
    }

    /**
     * Verify that an unflagged override in a public subclass inherits `@FlaggedApi` from an
     * ancestor method even when a package-private intermediate class also overrides it.
     */
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test flagState on override through package-private intermediate class`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        import android.annotation.FlaggedApi;
                        public class PublicRoot {
                            @FlaggedApi("test.pkg.flags.flag_a")
                            public void foo() {}
                        }
                        class PkgPrivateMiddle extends PublicRoot {
                            @Override
                            public void foo() {}
                        }
                        public class PublicLeaf extends PkgPrivateMiddle {
                            @Override
                            public void foo() {}
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                    apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.flag_a", KEEP))),
                ),
        ) {
            val flagA = FlagState.additionFlag("test.pkg.flags.flag_a", KEEP)
            assertEquals(
                flagA,
                codebase
                    .assertClass("test.pkg.PublicLeaf")
                    .assertMethod("foo", emptyList())
                    .selectedApi
                    .flagState,
            )
        }
    }

    /**
     * Verify that Kotlin properties with exposed backing fields (`const val` and `@JvmField`)
     * delegate [SelectedApi.wasPreviouslyReleased] and [SelectedApi.flagState] to their backing
     * field against a `FieldItem`-only `previouslyReleasedCodebase` (such as Format 2.0 signature
     * files), independent of evaluation order.
     */
    @RequiresCapabilities(Capability.KOTLIN, Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test SelectedApi delegation on Kotlin exposed backing field properties`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Holder {
                            public static final int RELEASED_CONST = 1;
                            public static final int RELEASED_JVM_FIELD = 2;
                        }
                    """
                ),
                com.android.tools.metalava.testing.kotlin(
                    """
                        package test.pkg
                        class Unused
                    """
                ),
            ),
            testFixture = TestFixture(checkSupportedInputFormats = false),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                inputSet(
                    com.android.tools.metalava.testing.kotlin(
                        """
                            package test.pkg
                            import android.annotation.FlaggedApi
                            object Holder {
                                @FlaggedApi("test.pkg.flags.kept_flag")
                                const val RELEASED_CONST: Int = 1
                                @FlaggedApi("test.pkg.flags.kept_flag")
                                @JvmField
                                val RELEASED_JVM_FIELD: Int = 2
                                @FlaggedApi("test.pkg.flags.kept_flag")
                                const val UNRELEASED_CONST: Int = 3
                            }
                        """
                    ),
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(KnownJarFiles.stubAnnotationsJar),
                        apiFlags = ApiFlags(listOf(ApiFlag("test.pkg.flags.kept_flag", KEEP))),
                        previouslyReleasedCodebase = previousCodebase,
                    ),
            ) {
                val keptFlagged = FlagState.additionFlag("test.pkg.flags.kept_flag", KEEP)
                val holder = codebase.assertClass("test.pkg.Holder")

                // Query `property.selectedApi` BEFORE `backingField.selectedApi`.
                val constProp = holder.assertProperty("RELEASED_CONST")
                assertTrue(constProp.selectedApi.wasPreviouslyReleased())
                assertSame(FlagState.NONE, constProp.selectedApi.flagState)
                val constField = holder.assertField("RELEASED_CONST")
                assertTrue(constField.selectedApi.wasPreviouslyReleased())
                assertSame(FlagState.NONE, constField.selectedApi.flagState)

                // Query `backingField.selectedApi` BEFORE `property.selectedApi`.
                val jvmField = holder.assertField("RELEASED_JVM_FIELD")
                assertTrue(jvmField.selectedApi.wasPreviouslyReleased())
                assertSame(FlagState.NONE, jvmField.selectedApi.flagState)
                val jvmProp = holder.assertProperty("RELEASED_JVM_FIELD")
                assertTrue(jvmProp.selectedApi.wasPreviouslyReleased())
                assertSame(FlagState.NONE, jvmProp.selectedApi.flagState)

                val unreleasedProp = holder.assertProperty("UNRELEASED_CONST")
                assertFalse(unreleasedProp.selectedApi.wasPreviouslyReleased())
                assertEquals(keptFlagged, unreleasedProp.selectedApi.flagState)
            }
        }
    }

    /**
     * Verify [SelectedApi.wasPreviouslyDeprecated] across unreleased items, previously released
     * non-deprecated items, and previously released explicitly or implicitly deprecated items
     * (including across `CodebaseSnapshotTaker` snapshots).
     */
    @RequiresCapabilities(Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.JAVA)
    @Test
    fun `Test wasPreviouslyDeprecated`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class ReleasedUndeprecated {
                            public void undeprecatedMethod() {}
                        }
                        @Deprecated
                        public class ReleasedDeprecatedOuter {
                            @Deprecated
                            public void explicitlyDeprecatedMethod() {}
                            public void implicitlyDeprecatedMethod() {}
                        }
                    """
                ),
            ),
            testFixture = TestFixture(checkSupportedInputFormats = false),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                inputSet(
                    java(
                        """
                            package test.pkg;
                            @Deprecated
                            public class ReleasedUndeprecated {
                                @Deprecated
                                public void undeprecatedMethod() {}
                            }
                            @Deprecated
                            public class ReleasedDeprecatedOuter {
                                @Deprecated
                                public void explicitlyDeprecatedMethod() {}
                                public void implicitlyDeprecatedMethod() {}
                            }
                            @Deprecated
                            public class UnreleasedDeprecated {}
                        """
                    ),
                ),
                testFixture = TestFixture(previouslyReleasedCodebase = previousCodebase),
            ) {
                val snapshot =
                    CodebaseSnapshotTaker.takeSnapshot(
                        codebase,
                        definitionVisitorFactory = ::NonFilteringDelegatingVisitor,
                        referenceVisitorFactory = ::NonFilteringDelegatingVisitor,
                        includeDocumentation = false,
                    )
                for (target in listOf(codebase, snapshot)) {
                    val undeprecated = target.assertClass("test.pkg.ReleasedUndeprecated")
                    assertTrue(undeprecated.selectedApi.wasPreviouslyReleased())
                    assertFalse(undeprecated.selectedApi.wasPreviouslyDeprecated())
                    assertFalse(
                        undeprecated
                            .assertMethod("undeprecatedMethod", emptyList())
                            .selectedApi
                            .wasPreviouslyDeprecated(),
                    )

                    val deprecatedOuter = target.assertClass("test.pkg.ReleasedDeprecatedOuter")
                    assertTrue(deprecatedOuter.selectedApi.wasPreviouslyReleased())
                    assertTrue(deprecatedOuter.selectedApi.wasPreviouslyDeprecated())
                    assertTrue(
                        deprecatedOuter
                            .assertMethod("explicitlyDeprecatedMethod", emptyList())
                            .selectedApi
                            .wasPreviouslyDeprecated(),
                    )
                    assertTrue(
                        deprecatedOuter
                            .assertMethod("implicitlyDeprecatedMethod", emptyList())
                            .selectedApi
                            .wasPreviouslyDeprecated(),
                    )

                    val unreleased = target.assertClass("test.pkg.UnreleasedDeprecated")
                    assertFalse(unreleased.selectedApi.wasPreviouslyReleased())
                    assertFalse(unreleased.selectedApi.wasPreviouslyDeprecated())
                }
            }
        }
    }

    /**
     * Verify that Kotlin properties with exposed backing fields (`const val` and `@JvmField`)
     * delegate [SelectedApi.wasPreviouslyReleased] and [SelectedApi.wasPreviouslyDeprecated] to
     * their backing field even when `previouslyReleasedCodebase` only contains `FieldItem`s (such
     * as Format 2.0 signature files), independent of whether the property or field is queried
     * first.
     */
    @RequiresCapabilities(Capability.KOTLIN, Capability.REVERTED_ITEMS)
    @SupportedInputFormats(InputFormat.KOTLIN)
    @Test
    fun `Test wasPreviouslyDeprecated on Kotlin exposed backing field properties`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Holder {
                            public static final int CONST_UNDEPRECATED = 1;
                            @Deprecated
                            public static final int CONST_DEPRECATED = 2;
                            public static final int JVM_FIELD_UNDEPRECATED = 3;
                            @Deprecated
                            public static final int JVM_FIELD_DEPRECATED = 4;
                        }
                    """
                ),
                com.android.tools.metalava.testing.kotlin(
                    """
                        package test.pkg
                        class Unused
                    """
                ),
            ),
            testFixture = TestFixture(checkSupportedInputFormats = false),
        ) {
            val previousCodebase = codebase
            runCodebaseTest(
                inputSet(
                    com.android.tools.metalava.testing.kotlin(
                        """
                            package test.pkg
                            object Holder {
                                @Deprecated("newly deprecated")
                                const val CONST_UNDEPRECATED: Int = 1
                                @Deprecated("already deprecated")
                                const val CONST_DEPRECATED: Int = 2
                                @JvmField
                                @Deprecated("newly deprecated")
                                val JVM_FIELD_UNDEPRECATED: Int = 3
                                @JvmField
                                @Deprecated("already deprecated")
                                val JVM_FIELD_DEPRECATED: Int = 4
                                @Deprecated("unreleased")
                                const val CONST_UNRELEASED: Int = 5
                            }
                        """
                    ),
                ),
                testFixture = TestFixture(previouslyReleasedCodebase = previousCodebase),
            ) {
                val holder = codebase.assertClass("test.pkg.Holder")

                // Query property.selectedApi BEFORE backingField.selectedApi.
                val constUndepProp = holder.assertProperty("CONST_UNDEPRECATED")
                assertTrue(constUndepProp.selectedApi.wasPreviouslyReleased())
                assertFalse(constUndepProp.selectedApi.wasPreviouslyDeprecated())

                val constDepProp = holder.assertProperty("CONST_DEPRECATED")
                assertTrue(constDepProp.selectedApi.wasPreviouslyReleased())
                assertTrue(constDepProp.selectedApi.wasPreviouslyDeprecated())

                // Query backingField.selectedApi BEFORE property.selectedApi.
                val jvmUndepField = holder.assertField("JVM_FIELD_UNDEPRECATED")
                assertTrue(jvmUndepField.selectedApi.wasPreviouslyReleased())
                assertFalse(jvmUndepField.selectedApi.wasPreviouslyDeprecated())
                val jvmUndepProp = holder.assertProperty("JVM_FIELD_UNDEPRECATED")
                assertTrue(jvmUndepProp.selectedApi.wasPreviouslyReleased())
                assertFalse(jvmUndepProp.selectedApi.wasPreviouslyDeprecated())

                val jvmDepField = holder.assertField("JVM_FIELD_DEPRECATED")
                assertTrue(jvmDepField.selectedApi.wasPreviouslyReleased())
                assertTrue(jvmDepField.selectedApi.wasPreviouslyDeprecated())
                val jvmDepProp = holder.assertProperty("JVM_FIELD_DEPRECATED")
                assertTrue(jvmDepProp.selectedApi.wasPreviouslyReleased())
                assertTrue(jvmDepProp.selectedApi.wasPreviouslyDeprecated())

                val unreleasedProp = holder.assertProperty("CONST_UNRELEASED")
                assertFalse(unreleasedProp.selectedApi.wasPreviouslyReleased())
                assertFalse(unreleasedProp.selectedApi.wasPreviouslyDeprecated())
            }
        }
    }
}
