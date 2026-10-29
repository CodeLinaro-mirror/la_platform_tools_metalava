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

import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.HIDE
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.testing.java
import org.junit.runners.Parameterized

/**
 * Tests verifying [SelectedApi.elidableApiVariants] for method overrides across different hierarchy
 * relationships and API surfaces, parameterized by [AdditionalOverrides] to test with and without
 * `addAdditionalOverrides`.
 */
class CommonParameterizedSelectedApiElidingTest : BaseCommonParameterizedSelectedApiTest() {

    /** Parameter indicating whether additional overrides should be considered. */
    @Parameterized.Parameter(1) lateinit var addAdditionalOverrides: AdditionalOverrides

    /**
     * Values for the `addAdditionalOverrides` parameter.
     *
     * @property value the boolean value corresponding to this parameter choice.
     */
    enum class AdditionalOverrides(val value: Boolean) {
        WITH(value = true),
        WITHOUT(value = false),
    }

    companion object : BaseCompanion() {
        /** List of tests before computing the cross product with [AdditionalOverrides]. */
        private val tests = buildList {
            buildTests(
                name = "same-surface duplicate override",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Parent {
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Child extends Parent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "cross-surface override narrower over wider",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                public class SystemParent {
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicChild extends SystemParent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.SystemParent
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.SystemParent()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.SystemParent.foo()
                                       self - ApiVariantSet[system(C)]
                              class test.pkg.PublicChild
                                     self - ApiVariantSet[public(C)]
                               superClass - ApiVariantSet[system(C)]
                                constructor test.pkg.PublicChild()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicChild.foo()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[system(C)]
                        """,
                )
            }

            buildTests(
                name = "cross-surface override wider over narrower",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class PublicParent {
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                public class SystemChild extends PublicParent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.PublicParent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicParent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicParent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.SystemChild
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.SystemChild()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.SystemChild.foo()
                                       self - ApiVariantSet[system(C)]
                                   elidable - ApiVariantSet[public(C),system(C)]
                        """,
                )
            }

            buildTests(
                name = "re-abstracted method",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class ConcreteParent {
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public abstract class AbstractChild extends ConcreteParent {
                                    @Override
                                    public abstract void foo();
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
                              class test.pkg.ConcreteParent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.ConcreteParent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.ConcreteParent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.AbstractChild
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.AbstractChild()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.AbstractChild.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "intermediate inaccessible class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class PublicGrandParent {
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                class InaccessibleParent extends PublicGrandParent {
                                    @Override
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicChild extends InaccessibleParent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.PublicGrandParent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicGrandParent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicGrandParent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.InaccessibleParent
                                     self - ApiVariantSet[]
                                constructor test.pkg.InaccessibleParent()
                                       self - ApiVariantSet[]
                                method test.pkg.InaccessibleParent.foo()
                                       self - ApiVariantSet[]
                              class test.pkg.PublicChild
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicChild()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicChild.foo()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "specialized return type",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Parent {
                                    public Object foo() { return null; }
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Child extends Parent {
                                    @Override
                                    public String foo() { return null; }
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "hidden method with specialized return type",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Child.java: hidden: Attempting to hide method test.pkg.Child.method(int,String) which overrides method test.pkg.Parent.method(int,String) which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Parent {
                                    protected Object method(int p1, String p2) { return null; }
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Child extends Parent {
                                    $HIDE
                                    @Override
                                    public String method(int q1, String q2) { return null; }
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.method(int,String)
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.method(int,String)
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "required override for text stubs",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public interface InterfaceA {
                                    default void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public interface InterfaceB {
                                    void foo();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Child implements InterfaceA, InterfaceB {
                                    @Override
                                    public void foo() {}
                                }
                            """
                        ),
                    ),
            ) {
                additionalOverridesTest(
                    surface = "public",
                    expectedWithoutAdditionalOverrides =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.InterfaceA
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.InterfaceA.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.InterfaceB
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.InterfaceB.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                    expectedWithAdditionalOverrides =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C)]
                              class test.pkg.InterfaceA
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.InterfaceA.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.InterfaceB
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.InterfaceB.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "override of javadoc deprecated method is not elided",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Parent {
                                    /**
                                     * @deprecated
                                     */
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class Child extends Parent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name =
                    "override in deprecated class of javadoc deprecated method in deprecated superclass is not elided",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                @Deprecated
                                public class Parent {
                                    /**
                                     * @deprecated
                                     */
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                @Deprecated
                                public class Child extends Parent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name =
                    "override in deprecated class of javadoc deprecated method in non-deprecated superclass is not elided",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                public class Parent {
                                    /**
                                     * @deprecated
                                     */
                                    public void foo() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                @Deprecated
                                public class Child extends Parent {
                                    @Override
                                    public void foo() {}
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Parent.foo()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.foo()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }
        }

        /**
         * Compute the Cartesian product of [tests] and [AdditionalOverrides.entries] to create
         * parameterized test instances.
         */
        @JvmStatic
        @Parameterized.Parameters
        fun params(): List<Array<Any>> {
            return AdditionalOverrides.entries.flatMap { addAdditionalOverrides ->
                tests.map { p -> arrayOf(p, addAdditionalOverrides) }
            }
        }

        /**
         * Filter out any test parameter combinations that are not valid for a specific
         * [additionalOverrides] or [CodebaseCreatorConfig].
         * - Tests created via [BaseCommonParameterizedSelectedApiTest.Builder.surfaceTest] have
         *   `testParams.addAdditionalOverrides == null` and run under both
         *   [AdditionalOverrides.WITH] and [AdditionalOverrides.WITHOUT].
         * - Tests created via
         *   [BaseCommonParameterizedSelectedApiTest.Builder.additionalOverridesTest] have
         *   `testParams.addAdditionalOverrides` set to a specific boolean value and only run under
         *   the matching [AdditionalOverrides] setting.
         */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            testParams: TestParams,
            additionalOverrides: AdditionalOverrides,
        ) =
            BaseCommonParameterizedSelectedApiTest.parameterFilter(config, testParams) &&
                (testParams.addAdditionalOverrides == null ||
                    testParams.addAdditionalOverrides == additionalOverrides.value)
    }
}
