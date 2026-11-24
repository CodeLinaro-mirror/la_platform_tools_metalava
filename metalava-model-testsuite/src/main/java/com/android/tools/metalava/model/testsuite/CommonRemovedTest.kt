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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.signature
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for verifying the behavior of configured removed annotations. */
@SupportedInputFormats(InputFormat.JAVA, InputFormat.SIGNATURE)
class CommonRemovedTest : BaseModelTest() {

    private val removedAnnotationSource =
        java(
            """
                package test.pkg;

                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                @Retention(RetentionPolicy.SOURCE)
                @interface Removed {}
            """
        )

    private fun createApiSurfaceRules(): ApiSurfaceRules {
        val apiSurfaces = ApiSurfaces.create()
        val rulesByName = mapOf("main" to listOf(SurfaceSelectionRule.unannotated))
        val variantRules =
            listOf(
                SurfaceSelectionRule.createAnnotationRule(
                    "test.pkg.Removed",
                    effect = SurfaceSelectionRule.Effect.REMOVED,
                ),
            )
        return ApiSurfaceRules(apiSurfaces, rulesByName, variantRules)
    }

    /**
     * Assert that [SelectableItem.selectedApi]'s [SelectedApi.hasRemovedAnnotation] equals
     * [expected].
     */
    private fun SelectableItem.assertHasRemovedAnnotation(expected: Boolean) {
        assertEquals(
            expected,
            selectedApi.hasRemovedAnnotation(),
            message = "$this hasRemovedAnnotation()",
        )
    }

    @Test
    fun `Test class annotated with configured removed annotation is marked as removed`() {
        runCodebaseTest(
            inputSet(
                removedAnnotationSource,
                java(
                    """
                        package test.pkg;

                        @Removed
                        public class Foo {
                            public void method() {}
                        }
                    """
                ),
            ),
            inputSet(
                // Split Foo across two signature files to test merging an existing @ForRemoval
                // class.
                signature(
                    "first.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @ForRemoval public class Foo {
                            ctor public Foo();
                          }
                        }
                    """,
                ),
                signature(
                    "second.txt",
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @ForRemoval public class Foo {
                            method public void method();
                          }
                        }
                    """,
                ),
            ),
            testFixture = TestFixture(apiSurfaceRules = createApiSurfaceRules()),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")

            // Ensure that the synthetic @ForRemoval annotation from signature files is not retained
            // in the class's annotations, even when the class is merged across multiple files.
            val expectedAnnotations =
                if (inputFormat == InputFormat.SIGNATURE) emptyList()
                else listOf("test.pkg.Removed")
            assertEquals(expectedAnnotations, fooClass.annotationNames())
            fooClass.assertHasRemovedAnnotation(true)
            fooClass.assertItemApiVariants(
                "ApiVariantSet[main(R)]",
                message = "Foo should be removed",
            )
            val method = fooClass.assertMethod("method", emptyList())
            method.assertHasRemovedAnnotation(false)

            // TODO(b/571827848): checkForExistingClass sets currentClassApiVariant to
            //   currentApiVariant without checking @ForRemoval, so Foo.method() in second.txt is
            //   placed in main(C) instead of main(R).
            val expectedMethodVariants =
                if (inputFormat == InputFormat.SIGNATURE) "ApiVariantSet[main(C)]"
                else "ApiVariantSet[main(R)]"
            method.assertItemApiVariants(
                expectedMethodVariants,
                message = "Foo.method() should be removed",
            )
        }
    }

    @Test
    fun `Test items annotated with configured removed annotation and FlaggedApi`() {
        runCodebaseTest(
            inputSet(
                removedAnnotationSource,
                KnownSourceFiles.flaggedApiSource,
                java(
                    """
                        package test.pkg;

                        import android.annotation.FlaggedApi;

                        @Removed
                        @FlaggedApi("test.pkg.flags.finalized_flag")
                        public class FinalizedRemovedClass {
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        import android.annotation.FlaggedApi;

                        @Removed
                        @FlaggedApi("test.pkg.flags.kept_flag")
                        public class KeptRemovedClass {
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        import android.annotation.FlaggedApi;

                        @Removed
                        @FlaggedApi("test.pkg.flags.reverted_flag")
                        public class RevertedRemovedClass {
                            public void method() {}
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        import android.annotation.FlaggedApi;

                        public class Foo {
                            @Removed
                            @FlaggedApi("test.pkg.flags.finalized_flag")
                            public void finalizedRemovedMethod() {}

                            @Removed
                            @FlaggedApi("test.pkg.flags.kept_flag")
                            public void keptRemovedMethod() {}

                            @Removed
                            @FlaggedApi("test.pkg.flags.reverted_flag")
                            public void revertedRemovedMethod() {}

                            @Removed
                            @FlaggedApi("test.pkg.flags.finalized_flag")
                            public int finalizedRemovedField;

                            @Removed
                            @FlaggedApi("test.pkg.flags.kept_flag")
                            public int keptRemovedField;

                            @Removed
                            @FlaggedApi("test.pkg.flags.reverted_flag")
                            public int revertedRemovedField;
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @ForRemoval @FlaggedApi("test.pkg.flags.finalized_flag") public class FinalizedRemovedClass {
                            ctor public FinalizedRemovedClass();
                            method public void method();
                          }
                          @ForRemoval @FlaggedApi("test.pkg.flags.kept_flag") public class KeptRemovedClass {
                            ctor public KeptRemovedClass();
                            method public void method();
                          }
                          @ForRemoval @FlaggedApi("test.pkg.flags.reverted_flag") public class RevertedRemovedClass {
                            ctor public RevertedRemovedClass();
                            method public void method();
                          }
                          public class Foo {
                            ctor public Foo();
                            method @ForRemoval @FlaggedApi("test.pkg.flags.finalized_flag") public void finalizedRemovedMethod();
                            method @ForRemoval @FlaggedApi("test.pkg.flags.kept_flag") public void keptRemovedMethod();
                            method @ForRemoval @FlaggedApi("test.pkg.flags.reverted_flag") public void revertedRemovedMethod();
                            field @ForRemoval @FlaggedApi("test.pkg.flags.finalized_flag") public int finalizedRemovedField;
                            field @ForRemoval @FlaggedApi("test.pkg.flags.kept_flag") public int keptRemovedField;
                            field @ForRemoval @FlaggedApi("test.pkg.flags.reverted_flag") public int revertedRemovedField;
                          }
                        }
                    """
                ),
            ),
            testFixture =
                TestFixture(
                    apiSurfaceRules = createApiSurfaceRules(),
                    apiFlags =
                        ApiFlags(
                            listOf(
                                ApiFlag("test.pkg.flags.finalized_flag", ApiFlagAction.FINALIZE),
                                ApiFlag("test.pkg.flags.kept_flag", ApiFlagAction.KEEP),
                                ApiFlag("test.pkg.flags.reverted_flag", ApiFlagAction.REVERT),
                            )
                        ),
                ),
        ) {
            val finalizedRemovedClass = codebase.assertClass("test.pkg.FinalizedRemovedClass")
            finalizedRemovedClass.assertHasRemovedAnnotation(true)
            finalizedRemovedClass.assertItemApiVariants(
                "ApiVariantSet[main(R)]",
                message = "FinalizedRemovedClass should be removed",
            )
            finalizedRemovedClass.assertMethod("method", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(false)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(R)]",
                    message = "FinalizedRemovedClass.method() should be removed",
                )
            }

            val keptRemovedClass = codebase.assertClass("test.pkg.KeptRemovedClass")
            keptRemovedClass.assertHasRemovedAnnotation(true)
            keptRemovedClass.assertItemApiVariants(
                "ApiVariantSet[main(C)]",
                message = "KeptRemovedClass should be in main(C)",
            )
            keptRemovedClass.assertMethod("method", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(false)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "KeptRemovedClass.method() should be in main(C)",
                )
            }

            val revertedRemovedClass = codebase.assertClass("test.pkg.RevertedRemovedClass")
            revertedRemovedClass.assertHasRemovedAnnotation(true)
            revertedRemovedClass.assertItemApiVariants(
                "ApiVariantSet[main(C)]",
                message = "RevertedRemovedClass should be in main(C)",
            )
            revertedRemovedClass.assertMethod("method", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(false)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "RevertedRemovedClass.method() should be in main(C)",
                )
            }

            val fooClass = codebase.assertClass("test.pkg.Foo")
            fooClass.assertHasRemovedAnnotation(false)
            fooClass.assertItemApiVariants(
                "ApiVariantSet[main(C)]",
                message = "Foo should be in main(C)",
            )
            fooClass.assertMethod("finalizedRemovedMethod", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(true)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(R)]",
                    message = "Foo.finalizedRemovedMethod() should be removed",
                )
            }
            fooClass.assertMethod("keptRemovedMethod", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(true)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "Foo.keptRemovedMethod() should be in main(C)",
                )
            }
            fooClass.assertMethod("revertedRemovedMethod", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(true)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "Foo.revertedRemovedMethod() should be in main(C)",
                )
            }
            fooClass.assertField("finalizedRemovedField").let { field ->
                field.assertHasRemovedAnnotation(true)
                field.assertItemApiVariants(
                    "ApiVariantSet[main(R)]",
                    message = "Foo.finalizedRemovedField should be removed",
                )
            }
            fooClass.assertField("keptRemovedField").let { field ->
                field.assertHasRemovedAnnotation(true)
                field.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "Foo.keptRemovedField should be in main(C)",
                )
            }
            fooClass.assertField("revertedRemovedField").let { field ->
                field.assertHasRemovedAnnotation(true)
                field.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "Foo.revertedRemovedField should be in main(C)",
                )
            }
        }
    }

    @Test
    fun `Test items annotated with configured removed annotation and FlaggedApi with no ApiFlags`() {
        runCodebaseTest(
            inputSet(
                removedAnnotationSource,
                KnownSourceFiles.flaggedApiSource,
                java(
                    """
                        package test.pkg;

                        import android.annotation.FlaggedApi;

                        @Removed
                        @FlaggedApi("test.pkg.flags.flag")
                        public class Foo {
                            public void method() {}
                        }
                    """
                ),
            ),
            inputSet(
                signature(
                    """
                        // Signature format: 2.0
                        package test.pkg {
                          @ForRemoval @FlaggedApi("test.pkg.flags.flag") public class Foo {
                            ctor public Foo();
                            method public void method();
                          }
                        }
                    """
                ),
            ),
            testFixture = TestFixture(apiSurfaceRules = createApiSurfaceRules()),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            fooClass.assertHasRemovedAnnotation(true)
            fooClass.assertItemApiVariants(
                "ApiVariantSet[main(C)]",
                message = "Foo should be in main(C)",
            )
            fooClass.assertMethod("method", emptyList()).let { method ->
                method.assertHasRemovedAnnotation(false)
                method.assertItemApiVariants(
                    "ApiVariantSet[main(C)]",
                    message = "Foo.method() should be in main(C)",
                )
            }
        }
    }
}
