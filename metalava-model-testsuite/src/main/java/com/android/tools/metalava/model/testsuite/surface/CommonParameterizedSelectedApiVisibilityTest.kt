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

import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.PUBLIC_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.UNANNOTATED_NON_RECURSIVE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.annotatedOnlyPublicSystemModuleRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.annotatedOnlyRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import org.junit.ClassRule
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants based on declaration visibility, scoping, and explicit hide
 * markers within a single type or nested type hierarchy.
 *
 * Add tests to this class for:
 * - Basic class/interface and member visibility in public API.
 * - `@hide` annotations and `@hide` Javadoc/KDoc tags on classes or members.
 * - Annotated-only and non-recursive API surface selection rules.
 * - Lexical enclosure and visibility scoping (e.g. public classes nested in package-private
 *   classes, package-private classes nested in public classes).
 * - Member visibility within unannotated or inner classes.
 * - Java record components and accessor visibility.
 *
 * For tests involving inheritance across surfaces or overridden methods, see
 * [CommonParameterizedSelectedApiInheritanceTest]. For Kotlin-specific visibility (such as
 * `internal` or `@PublishedApi`), see [CommonParameterizedSelectedApiKotlinTest].
 */
class CommonParameterizedSelectedApiVisibilityTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /**
         * A jar containing a public class (`test.pkg.PublicClass`) to be placed on the classpath.
         *
         * Cached across tests using [testFileCacheRule] to test how classpath classes (which have
         * `emit = false`) interact with API surface selection.
         */
        private val publicClasspathJar =
            jarFromSources(
                    "public-class.jar",
                    java(
                        """
                            package test.pkg;
                            public class PublicClass {
                            }
                        """
                    ),
                )
                .cacheIn(testFileCacheRule)

        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            buildTests(
                name = "basic class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public interface Test {
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
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "@hide doctag class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test;
                                /** @hide */
                                public interface Hidden {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test
                                   self - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "@hide annotation class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test;
                                $HIDE
                                public interface Hidden {
                                    $PUBLIC_API
                                    void method();
                                }
                            """
                        ),
                    ),
                expectedIssues =
                    """
                        MAIN_SRC/src/test/Hidden.java: error: Attempting to unhide method test.Hidden.method(), but surrounding class test.Hidden is hidden and should also be annotated with @test.api.PublicApi [ShowingMemberInHiddenClass]
                    """,
            ) {
                surfaceTest(
                    surface = "public",
                    // TODO(b/512093496): A hidden class cannot contain any non-hidden members.
                    expected =
                        """
                            package test
                                   self - ApiVariantSet[]
                              class test.Hidden
                                     self - ApiVariantSet[]
                                method test.Hidden.method()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "annotated only",
                surfaceRules = annotatedOnlyRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_API
                                public class Test {
                                    public Test(int a) {}

                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_NON_RECURSIVE_API
                                public class ClassOnly {
                                    public ClassOnly() {}

                                    public void notIncluded() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public interface Unannotated {
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
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Test(int)
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Test.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.ClassOnly
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.ClassOnly()
                                       self - ApiVariantSet[]
                                method test.pkg.ClassOnly.notIncluded()
                                       self - ApiVariantSet[]
                              class test.pkg.Unannotated
                                     self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "public class in package private class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                class Outer {
                                    public class Inner {
                                        public Inner() {}
                                        public void method() {}
                                    }
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
                                   self - ApiVariantSet[]
                              class test.pkg.Outer
                                     self - ApiVariantSet[]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "package private class in public class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    class Inner {
                                        public Inner() {}
                                        public void method() {}
                                    }
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
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[]
                                  method test.pkg.Outer.Inner.method()
                                         self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "record component",
                surfaceRules = annotatedOnlyRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $UNANNOTATED_NON_RECURSIVE_API
                                public record MyRecord(int x) {}
                            """
                        ),
                    ),
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/MyRecord.java: error: Cannot hide canonical constructor test.pkg.MyRecord(int) as it is an indivisible part of a record class [HidingRecordComponent]
                        MAIN_SRC/src/test/pkg/MyRecord.java: error: Cannot hide record component getter method test.pkg.MyRecord.x() as it is an indivisible part of a record class [HidingRecordComponent]
                    """,
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.MyRecord
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.MyRecord(int)
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.MyRecord.x()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "nested class in public class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Outer {
                                    $SYSTEM_API
                                    public class Inner {
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                              class test.pkg.Outer
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Outer()
                                       self - ApiVariantSet[public(C)]
                                class test.pkg.Outer.Inner
                                       self - ApiVariantSet[system(C)]
                                  constructor test.pkg.Outer.Inner()
                                         self - ApiVariantSet[system(C)]
                        """,
                )
            }

            buildTests(
                name = "system method in unannotated class",
                surfaceRules = annotatedOnlyPublicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    $SYSTEM_API
                                    public void systemMethod() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.Test
                                     self - ApiVariantSet[]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[]
                                method test.pkg.Test.systemMethod()
                                       self - ApiVariantSet[]
                        """,
                )
            }

            buildTests(
                name = "system method in public inner class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Test {
                                    public class Inner {
                                        $SYSTEM_API
                                        public void systemMethod() {}
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                // TODO(b/512093496): The behavior shown below is not correct as propagating
                //  variants from members to the containing package is broken and will be fixed in
                //  follow up changes.
                surfaceTest(
                    surface = "system",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(C)]
                              class test.pkg.Test
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Test()
                                       self - ApiVariantSet[public(C)]
                                class test.pkg.Test.Inner
                                       self - ApiVariantSet[public(C)]
                                    content - ApiVariantSet[system(C)]
                                  constructor test.pkg.Test.Inner()
                                         self - ApiVariantSet[public(C)]
                                  method test.pkg.Test.Inner.systemMethod()
                                         self - ApiVariantSet[system(C)]
                        """,
                )
            }

            buildTests(
                name = "hidden source class and public class on classpath",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $HIDE
                                public class Hidden extends PublicClass {
                                }
                            """
                        ),
                    ),
                classpath = listOf(publicClasspathJar),
            ) {
                surfaceTest(
                    surface = "public",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[]
                              class test.pkg.Hidden
                                     self - ApiVariantSet[]
                                constructor test.pkg.Hidden()
                                       self - ApiVariantSet[]
                              class test.pkg.PublicClass
                                     emit - false
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }
        }
    }
}
