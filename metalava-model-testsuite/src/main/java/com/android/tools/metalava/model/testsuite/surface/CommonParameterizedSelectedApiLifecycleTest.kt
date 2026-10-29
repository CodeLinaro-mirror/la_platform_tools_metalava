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

package com.android.tools.metalava.model.testsuite.surface

import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlagAction.REVERT
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.DOC_ONLY
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.REMOVED_FROM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants for API lifecycle status, evolution, and stability.
 *
 * Add tests to this class for:
 * - `@doconly` annotations/tags and the `DOC_ONLY` API surface.
 * - `@removed` annotations/tags and the `REMOVED_FROM_API` API surface.
 * - Interactions between `doconly` and `removed` statuses.
 * - Flagged APIs using [ApiFlags] (e.g. `@FlaggedApi`).
 * - API stability and reverting flagged API items against previously released codebase sources.
 *
 * For tests focusing on general declaration visibility, see
 * [CommonParameterizedSelectedApiVisibilityTest].
 */
class CommonParameterizedSelectedApiLifecycleTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            buildTests(
                name = "flagged APIs",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                import android.annotation.FlaggedApi;
                                public class Outer {
                                    @FlaggedApi("reverted_flag")
                                    public void revertedMethod() {}

                                    @FlaggedApi("removed_flag")
                                    public void removedMethod() {}
                                }
                            """
                        ),
                    ),
                apiFlags =
                    ApiFlags(
                        listOf(
                            ApiFlag("reverted_flag", REVERT),
                            ApiFlag("removed_flag", REVERT),
                        )
                    ),
                previouslyReleasedSources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    public void revertedMethod() {}
                                }
                            """
                        )
                    ),
                expectedContainsRevertedItem = true,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.removedMethod()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "revert item with different variants",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                import android.annotation.FlaggedApi;
                                public class Outer {
                                    @FlaggedApi("reverted_flag")
                                    $SYSTEM_API
                                    public void revertedMethod() {}
                                }
                            """
                        ),
                    ),
                apiFlags =
                    ApiFlags(
                        listOf(
                            ApiFlag("reverted_flag", REVERT),
                        )
                    ),
                previouslyReleasedSources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    public void revertedMethod() {}
                                }
                            """
                        )
                    ),
                expectedContainsRevertedItem = true,
            ) {
                surfaceTest(
                    surface = "public",
                    // In the public surface, $SYSTEM_API hides revertedMethod() before its
                    // reverted @FlaggedApi annotation is evaluated.
                    expectedContainsRevertedItem = false,
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[]
                        """,
                )

                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Outer.revertedMethod()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "reverted class with unannotated override",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Base {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                import android.annotation.FlaggedApi;
                                @FlaggedApi("reverted_flag")
                                public class Foo extends Base {
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                    ),
                apiFlags =
                    ApiFlags(
                        listOf(
                            ApiFlag("reverted_flag", REVERT),
                        )
                    ),
                previouslyReleasedSources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Base {}
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Foo extends Base {}
                            """
                        ),
                    ),
                expectedContainsRevertedItem = true,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Base
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Base()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Base.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Foo
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Foo()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Foo.method()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "doconly",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $DOC_ONLY
                                public class DocOnlyClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    $DOC_ONLY
                                    public void docOnlyMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(CD)]
                              class test.pkg.DocOnlyClass
                                     self - ApiVariantSet[public(D)]
                                constructor test.pkg.DocOnlyClass()
                                       self - ApiVariantSet[public(D)]
                                method test.pkg.DocOnlyClass.method()
                                       self - ApiVariantSet[public(D)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.docOnlyMethod()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "removed",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $REMOVED_FROM_API
                                public class RemovedClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    $REMOVED_FROM_API
                                    public void removedMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(CR)]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[public(R)]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[public(R)]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.removedMethod()
                                       self - ApiVariantSet[public(R)]
                        """,
                )
            }

            buildTests(
                name = "doconly and removed",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $DOC_ONLY
                                $REMOVED_FROM_API
                                public class DocOnlyAndRemovedClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    $DOC_ONLY
                                    $REMOVED_FROM_API
                                    public void docOnlyAndRemovedMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(CR)]
                              class test.pkg.DocOnlyAndRemovedClass
                                     self - ApiVariantSet[public(R)]
                                constructor test.pkg.DocOnlyAndRemovedClass()
                                       self - ApiVariantSet[public(R)]
                                method test.pkg.DocOnlyAndRemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.docOnlyAndRemovedMethod()
                                       self - ApiVariantSet[public(R)]
                        """,
                )
            }

            buildTests(
                name = "@removed doctag",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                /** @removed */
                                public class RemovedClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    /** @removed */
                                    public void removedMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(CR)]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[public(R)]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[public(R)]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(R)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                  content - ApiVariantSet[public(R)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.removedMethod()
                                       self - ApiVariantSet[public(R)]
                        """,
                )
            }
        }
    }
}
