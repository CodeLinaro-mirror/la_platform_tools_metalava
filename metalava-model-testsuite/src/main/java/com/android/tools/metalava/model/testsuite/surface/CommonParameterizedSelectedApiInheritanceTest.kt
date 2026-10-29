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
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.MODULE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.REMOVED_FROM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.STANDALONE_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.SYSTEM_API
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicStandaloneRules
import com.android.tools.metalava.model.testing.surfaces.TestableApiSurfaces.publicSystemModuleRules
import com.android.tools.metalava.testing.java
import org.junit.runners.Parameterized

/**
 * Tests verifying selected API variants across type hierarchies and inheritance relationships.
 *
 * Add tests to this class for:
 * - Classes extending superclasses or implementing interfaces that belong to different API surfaces
 *   (e.g. public class extending a `SystemApi`, `ModuleLibApi`, standalone, or removed class).
 * - Method overrides across different API surfaces, including inheriting or not inheriting API
 *   variants from super methods.
 * - Method overrides involving `@Hide` on superclass or subclass methods, specialized return types,
 *   or interfaces.
 * - Inaccessible or package-private classes implementing public interface methods or extending
 *   public classes.
 *
 * For single-class or nested-class visibility without cross-surface inheritance, see
 * [CommonParameterizedSelectedApiVisibilityTest].
 */
class CommonParameterizedSelectedApiInheritanceTest : BaseCommonParameterizedSelectedApiTest() {

    companion object : BaseCompanion() {
        @JvmStatic
        @Parameterized.Parameters
        fun params() = buildList {
            buildTests(
                name = "public class extending system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                public class SystemClass {
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicClass extends SystemClass {
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
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                               superClass - ApiVariantSet[system(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "public class extending module class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $MODULE_API
                                public class ModuleClass {
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicClass extends ModuleClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),module(C)]
                              class test.pkg.ModuleClass
                                     self - ApiVariantSet[module(C)]
                                constructor test.pkg.ModuleClass()
                                       self - ApiVariantSet[module(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                               superClass - ApiVariantSet[module(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "public class extending standalone class",
                surfaceRules = publicStandaloneRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $STANDALONE_API
                                public class StandaloneClass {
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicClass extends StandaloneClass {
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "standalone",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[standalone(C)]
                              class test.pkg.StandaloneClass
                                     self - ApiVariantSet[standalone(C)]
                                constructor test.pkg.StandaloneClass()
                                       self - ApiVariantSet[standalone(C)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[standalone(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[standalone(C)]
                        """,
                )
            }

            buildTests(
                name = "removed public class extending removed system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class SystemClass {
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                $REMOVED_FROM_API
                                public class PublicClass extends SystemClass {
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
                                   self - ApiVariantSet[public(R),system(R)]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(R)]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(R)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(R)]
                               superClass - ApiVariantSet[system(R)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(R)]
                        """,
                )
            }

            buildTests(
                name = "public class extending removed system class",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;
                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class SystemClass {
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;
                                public class PublicClass extends SystemClass {
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
                                   self - ApiVariantSet[public(C),system(R)]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(R)]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(R)]
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "removed class overriding method from public interface marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public interface PublicInterface {
                                    void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                $REMOVED_FROM_API
                                public class RemovedClass implements PublicInterface {
                                    $HIDE
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(C),system(R)]
                              class test.pkg.PublicInterface
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.PublicInterface.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.RemovedClass
                                     self - ApiVariantSet[system(R)]
                                constructor test.pkg.RemovedClass()
                                       self - ApiVariantSet[system(R)]
                                method test.pkg.RemovedClass.method()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name =
                    "public class overriding method from superclass marked as @Hide with specialized return type",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Middle.java: hidden: Attempting to hide method test.pkg.Middle.method() which overrides method test.pkg.Base.method() which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public abstract class Base<T> {
                                    public abstract T method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Middle extends Base<String> {
                                    $HIDE
                                    @Override
                                    public String method() {
                                        return null;
                                    }
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Sub extends Middle {
                                    @Override
                                    public String method() {
                                        return null;
                                    }
                                }
                            """
                        ),
                    ),
            ) {
                surfaceTest(
                    surface = "module",
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
                              class test.pkg.Middle
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Middle()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Middle.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Sub
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Sub()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Sub.method()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C),system(C),module(C)]
                        """,
                )
            }

            buildTests(
                name = "system class overriding method from removed public class marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                $REMOVED_FROM_API
                                public class RemovedPublicClass {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class SystemClass extends RemovedPublicClass {
                                    $HIDE
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                    ),
            ) {
                // The removed status is not inherited by the overriding method.
                surfaceTest(
                    surface = "module",
                    expected =
                        """
                            package test.pkg
                                   self - ApiVariantSet[public(R),system(C)]
                              class test.pkg.RemovedPublicClass
                                     self - ApiVariantSet[public(R)]
                                constructor test.pkg.RemovedPublicClass()
                                       self - ApiVariantSet[public(R)]
                                method test.pkg.RemovedPublicClass.method()
                                       self - ApiVariantSet[public(R)]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.SystemClass.method()
                                       self - ApiVariantSet[]
                                   elidable - ApiVariantSet[public(R),system(R),module(R)]
                        """,
                )
            }

            buildTests(
                name =
                    "inaccessible class implementing method from public class and public subclass overriding it",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public abstract class PublicClass {
                                    public abstract void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                class InaccessibleClass extends PublicClass {
                                    @Override
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class PublicSubClass extends InaccessibleClass {
                                    @Override
                                    public void method() {}
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
                              class test.pkg.PublicClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicClass.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.InaccessibleClass
                                     self - ApiVariantSet[]
                                constructor test.pkg.InaccessibleClass()
                                       self - ApiVariantSet[]
                                method test.pkg.InaccessibleClass.method()
                                       self - ApiVariantSet[]
                              class test.pkg.PublicSubClass
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.PublicSubClass()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.PublicSubClass.method()
                                       self - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "class implementing multiple interfaces from different API surfaces",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public interface PublicInterface {
                                    void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public interface SystemInterface {
                                    void method();
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class SystemClass implements PublicInterface, SystemInterface {
                                    @Override
                                    public void method() {}
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
                              class test.pkg.PublicInterface
                                     self - ApiVariantSet[public(C)]
                                method test.pkg.PublicInterface.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.SystemInterface
                                     self - ApiVariantSet[system(C)]
                                method test.pkg.SystemInterface.method()
                                       self - ApiVariantSet[system(C)]
                              class test.pkg.SystemClass
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.SystemClass()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.SystemClass.method()
                                       self - ApiVariantSet[system(C)]
                        """,
                )
            }

            buildTests(
                name = "public class overriding public method marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Child.java: hidden: Attempting to hide method test.pkg.Child.method() which overrides method test.pkg.Parent.method() which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public class Parent {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Child extends Parent {
                                    $HIDE
                                    @Override
                                    public void method() {}
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
                                method test.pkg.Parent.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.method()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name = "final public class overriding protected method marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public class Parent {
                                    protected void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public final class Child extends Parent {
                                    $HIDE
                                    @Override
                                    protected void method() {}
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
                                method test.pkg.Parent.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.method()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name =
                    "public class overriding system method from system superclass marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Child.java: hidden: Attempting to hide method test.pkg.Child.method() which overrides method test.pkg.Parent.method() which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class Parent {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Child extends Parent {
                                    $HIDE
                                    @Override
                                    public void method() {}
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
                              class test.pkg.Parent
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.Parent.method()
                                       self - ApiVariantSet[system(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                               superClass - ApiVariantSet[system(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.method()
                                       self - ApiVariantSet[system(C)]
                                   elidable - ApiVariantSet[system(C)]
                        """,
                )
            }

            buildTests(
                name = "public class overriding public method marked as @SystemApi",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Child.java: hidden: Attempting to hide method test.pkg.Child.method() which overrides method test.pkg.Parent.method() which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                public class Parent {
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                public class Child extends Parent {
                                    $SYSTEM_API
                                    @Override
                                    public void method() {}
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
                                method test.pkg.Parent.method()
                                       self - ApiVariantSet[public(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[public(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[public(C)]
                                method test.pkg.Child.method()
                                       self - ApiVariantSet[public(C)]
                                   elidable - ApiVariantSet[public(C)]
                        """,
                )
            }

            buildTests(
                name =
                    "system class overriding system method from system superclass marked as @Hide",
                surfaceRules = publicSystemModuleRules,
                expectedIssues =
                    """
                        MAIN_SRC/src/test/pkg/Child.java: hidden: Attempting to hide method test.pkg.Child.method() which overrides method test.pkg.Parent.method() which is already part of the API [HidingApiMethodOverride]
                    """,
                sources =
                    listOf(
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class Parent {
                                    $SYSTEM_API
                                    public void method() {}
                                }
                            """
                        ),
                        java(
                            """
                                package test.pkg;

                                $SYSTEM_API
                                public class Child extends Parent {
                                    $HIDE
                                    @Override
                                    public void method() {}
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
                                   self - ApiVariantSet[system(C)]
                              class test.pkg.Parent
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.Parent()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.Parent.method()
                                       self - ApiVariantSet[system(C)]
                              class test.pkg.Child
                                     self - ApiVariantSet[system(C)]
                                constructor test.pkg.Child()
                                       self - ApiVariantSet[system(C)]
                                method test.pkg.Child.method()
                                       self - ApiVariantSet[system(C)]
                                   elidable - ApiVariantSet[system(C)]
                        """,
                )
            }
        }
    }
}
