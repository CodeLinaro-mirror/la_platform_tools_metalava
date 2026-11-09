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

package com.android.tools.metalava

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.KnownJarFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.xml
import org.junit.Test

class OverridingMethodTest : DriverTest() {

    @Test
    fun `Test removed class overriding method from public interface marked as @Hide`() {
        check(
            apiSurface = KnownApiSurface.MODULE_LIB,
            extraArguments = arrayOf("--format-defaults", "add-additional-overrides=yes"),
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
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

                            import android.annotation.Hide;
                            import android.annotation.RemovedFromApi;
                            import android.annotation.SystemApi;

                            @RemovedFromApi
                            @SystemApi
                            public class RemovedClass implements PublicInterface {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature = "",
            // RemovedClass is part of the system API surface (not module-lib) and its
            // overriding method is marked @Hide, overriding a public interface method.
            // Therefore, neither the class nor the method is emitted into the module-lib
            // removed API signature file, even with add-additional-overrides=yes.
            removedApi = "",
        )
    }

    @Test
    fun `Test system class overriding method from removed public class marked as @Hide`() {
        check(
            apiSurface = KnownApiSurface.MODULE_LIB,
            extraArguments = arrayOf("--format-defaults", "add-additional-overrides=yes"),
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            public class RemovedPublicClass {
                                public void method() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;
                            import android.annotation.SystemApi;

                            @SystemApi
                            public class SystemClass extends RemovedPublicClass {
                                @Hide
                                @Override
                                public void method() {}
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/SystemClass.java:7: warning: Public class test.pkg.SystemClass stripped of unavailable superclass test.pkg.RemovedPublicClass [HiddenSuperclass]
                """,
            expectedApiSignature = "",
            // SystemClass is part of the system API surface (not module-lib) and its
            // overriding method is marked @Hide, overriding a method from a removed public
            // class. Therefore, neither the class nor the method is emitted into the
            // module-lib removed API signature file, even with add-additional-overrides=yes.
            removedApi = "",
        )
    }

    @Test
    fun `Test public class overriding method from superclass marked as @Hide with specialized return type`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Middle.java:8: error: Attempting to hide method test.pkg.Middle.method() which overrides method test.pkg.Base.method() which is already part of the API [HidingApiMethodOverride]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Base<T> {
                                public T method() {
                                    return null;
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.Hide;

                            public class Middle extends Base<String> {
                                @Hide
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
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Base<T> {
                        ctor public Base();
                        method public T method();
                      }
                      public class Middle extends test.pkg.Base<java.lang.String> {
                        ctor public Middle();
                        method public String method();
                      }
                      public class Sub extends test.pkg.Middle {
                        ctor public Sub();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test hiding override of public method from the classpath`() {
        check(
            expectedIssues =
                """
                src/test/pkg/Foo.java:8: error: Attempting to hide method test.pkg.Foo.toString() which overrides method java.lang.Object.toString() which is already part of the API [HidingApiMethodOverrideClasspath]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;

                        import android.annotation.Hide;

                        public class Foo {
                            @Hide
                            @Override
                            public String toString() { return "Foo"; }
                        }
                        """
                    ),
                ),
        )
    }

    /**
     * Verify that explicitly marking an override of a public method with `@RemovedFromApi` reports
     * `HidingApiMethodOverride` and keeps the override in the active API rather than moving it to
     * the removed API.
     */
    @Test
    fun `Test removing override of public method with @RemovedFromApi`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Child.java:8: error: Attempting to remove method test.pkg.Child.elidableOverride() which overrides method test.pkg.Parent.elidableOverride() which is already part of the API [HidingApiMethodOverride]
                    src/test/pkg/Child.java:12: error: Attempting to remove method test.pkg.Child.finalOverride() which overrides method test.pkg.Parent.finalOverride() which is already part of the API [HidingApiMethodOverride]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Parent {
                                public void elidableOverride() {}
                                public void finalOverride() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RemovedFromApi;

                            public class Child extends Parent {
                                @RemovedFromApi
                                @Override
                                public void elidableOverride() {}

                                @RemovedFromApi
                                @Override
                                public final void finalOverride() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public final void finalOverride();
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void elidableOverride();
                        method public void finalOverride();
                      }
                    }
                """,
            removedApi = "",
        )
    }

    /**
     * Specifically verify legacy `@removed` Javadoc tag behavior when placed on an override of an
     * active API method (omitting `apiSurface` so legacy Javadoc tags are honored).
     */
    @Test
    fun `Test removing override of public method with @removed doc tag`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Child.java:6: error: Attempting to remove method test.pkg.Child.method() which overrides method test.pkg.Parent.method() which is already part of the API [HidingApiMethodOverride]
                """,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
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
                                /** @removed */
                                @Override
                                public final void method() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public final void method();
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void method();
                      }
                    }
                """,
            removedApi = "",
        )
    }

    /**
     * Verify that attempting to remove an override of a public method inherited from the classpath
     * reports `HidingApiMethodOverrideClasspath`.
     */
    @Test
    fun `Test removing override of public method from the classpath`() {
        check(
            expectedIssues =
                """
                    src/test/pkg/Foo.java:8: error: Attempting to remove method test.pkg.Foo.toString() which overrides method java.lang.Object.toString() which is already part of the API [HidingApiMethodOverrideClasspath]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RemovedFromApi;

                            public class Foo {
                                @RemovedFromApi
                                @Override
                                public String toString() { return "Foo"; }
                            }
                        """
                    ),
                ),
            removedApi = "",
        )
    }

    /**
     * Verify that removing an entire class with `@RemovedFromApi` when that class happens to
     * override a public superclass method is allowed and does not report `HidingApiMethodOverride`.
     */
    @Test
    fun `Test removing class that overrides public method`() {
        check(
            expectedIssues = "",
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Parent {
                                public void elidableOverride() {}
                                public void finalOverride() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            public class Child extends Parent {
                                @Override
                                public void elidableOverride() {}

                                @Override
                                public final void finalOverride() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Parent {
                        ctor public Parent();
                        method public void elidableOverride();
                        method public void finalOverride();
                      }
                    }
                """,
            removedApi =
                """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public final void finalOverride();
                      }
                    }
                """,
        )
    }

    /**
     * Verify that `@RemovedFromApi` on an override of an active API method reports
     * `HidingApiMethodOverride` both when its `@FlaggedApi` is finalized and when it is still
     * unfinalized (kept), while a reverted `@FlaggedApi` does not report an error.
     */
    @Test
    fun `Test removing override of public method with @RemovedFromApi and @FlaggedApi`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../resources/schemas/config.xsd">
                        <api-flags>
                            <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled"/>
                            <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                            <api-flag package="test.pkg" name="reverted_flag" mutability="immutable" status="disabled"/>
                        </api-flags>
                    </config>
                """
            )

        check(
            configFiles = arrayOf(apiFlagsXmlFile),
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            // TODO(b/571827848): keptOverride() should also report HidingApiMethodOverride.
            expectedIssues =
                """
                    src/test/pkg/Child.java:10: error: Attempting to remove method test.pkg.Child.finalizedOverride() which overrides method test.pkg.Parent.finalizedOverride() which is already part of the API [HidingApiMethodOverride]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Parent {
                                public void finalizedOverride() {}
                                public void keptOverride() {}
                                public void revertedOverride() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            public class Child extends Parent {
                                @RemovedFromApi
                                @FlaggedApi("test.pkg.finalized_flag")
                                @Override
                                public final void finalizedOverride() {}

                                @RemovedFromApi
                                @FlaggedApi("test.pkg.kept_flag")
                                @Override
                                public final void keptOverride() {}

                                @RemovedFromApi
                                @FlaggedApi("test.pkg.reverted_flag")
                                @Override
                                public final void revertedOverride() {}
                            }
                        """
                    ),
                ),
            // TODO(b/571827848): keptOverride() should not have @ForRemoval.
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public final void finalizedOverride();
                        method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public final void keptOverride();
                        method public final void revertedOverride();
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void finalizedOverride();
                        method public void keptOverride();
                        method public void revertedOverride();
                      }
                    }
                """,
            removedApi = "",
        )
    }

    /**
     * Verify how `@RemovedFromApi` on an override interacts with an unfinalized (kept)
     * `@FlaggedApi` removal on the overridden superclass method or on the containing class:
     * - If both the superclass method and the override have kept `@FlaggedApi` removals, the
     *   removal is valid.
     * - If the containing class has a kept `@FlaggedApi` removal, an unannotated override (or an
     *   override that also has a kept `@FlaggedApi` removal) is valid.
     * - However, an *immediate* (unflagged) `@RemovedFromApi` on the override is invalid if the
     *   overridden superclass method or containing class only has a deferred (kept `@FlaggedApi`)
     *   removal, because the superclass method and containing class remain in the active API now.
     */
    @Test
    fun `Test removing override of public method when superclass method or class has kept @FlaggedApi removal`() {
        val apiFlagsXmlFile =
            xml(
                "config-api-flags.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../resources/schemas/config.xsd">
                        <api-flags>
                            <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled"/>
                        </api-flags>
                    </config>
                """
            )

        check(
            configFiles = arrayOf(apiFlagsXmlFile),
            classpath = arrayOf(KnownJarFiles.stubAnnotationsTestFile),
            expectedIssues =
                """
                    src/test/pkg/Child.java:14: error: Attempting to remove method test.pkg.Child.immediateOverrideOfKeptSuper() which overrides method test.pkg.Parent.immediateOverrideOfKeptSuper() which is already part of the API [HidingApiMethodOverride]
                    src/test/pkg/KeptRemovedChild.java:14: error: Attempting to remove method test.pkg.KeptRemovedChild.immediateOverrideInKeptClass() which overrides method test.pkg.Parent.immediateOverrideInKeptClass() which is already part of the API [HidingApiMethodOverride]
                """,
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            public class Parent {
                                @RemovedFromApi
                                @FlaggedApi("test.pkg.kept_flag")
                                public void keptOverrideOfKeptSuper() {}

                                @RemovedFromApi
                                @FlaggedApi("test.pkg.kept_flag")
                                public void immediateOverrideOfKeptSuper() {}

                                public void unannotatedOverrideInKeptClass() {}
                                public void immediateOverrideInKeptClass() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            public class Child extends Parent {
                                @RemovedFromApi
                                @FlaggedApi("test.pkg.kept_flag")
                                @Override
                                public final void keptOverrideOfKeptSuper() {}

                                @RemovedFromApi
                                @Override
                                public final void immediateOverrideOfKeptSuper() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.annotation.FlaggedApi;
                            import android.annotation.RemovedFromApi;

                            @RemovedFromApi
                            @FlaggedApi("test.pkg.kept_flag")
                            public class KeptRemovedChild extends Parent {
                                @Override
                                public final void unannotatedOverrideInKeptClass() {}

                                @RemovedFromApi
                                @Override
                                public final void immediateOverrideInKeptClass() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    package test.pkg {
                      public class Child extends test.pkg.Parent {
                        ctor public Child();
                        method public final void immediateOverrideOfKeptSuper();
                        method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public final void keptOverrideOfKeptSuper();
                      }
                      @ForRemoval @FlaggedApi("test.pkg.kept_flag") public class KeptRemovedChild extends test.pkg.Parent {
                        ctor public KeptRemovedChild();
                        method public final void immediateOverrideInKeptClass();
                        method public final void unannotatedOverrideInKeptClass();
                      }
                      public class Parent {
                        ctor public Parent();
                        method public void immediateOverrideInKeptClass();
                        method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public void immediateOverrideOfKeptSuper();
                        method @ForRemoval @FlaggedApi("test.pkg.kept_flag") public void keptOverrideOfKeptSuper();
                        method public void unannotatedOverrideInKeptClass();
                      }
                    }
                """,
            removedApi = "",
        )
    }
}
