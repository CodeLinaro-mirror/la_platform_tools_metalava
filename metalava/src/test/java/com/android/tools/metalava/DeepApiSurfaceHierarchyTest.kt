/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.metalava.KnownApiSurface.Companion.TEST_MODULE_API_SURFACE
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_PUBLIC_API_SURFACE
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_SYSTEM_AND_MODULE_API_SURFACE
import com.android.tools.metalava.KnownApiSurface.Companion.TEST_SYSTEM_API_SURFACE
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

class DeepApiSurfaceHierarchyTest : DriverTest() {
    companion object {
        private val EXTRA_ARGS =
            errorIssues(
                Issues.UNAVAILABLE_SYMBOL,
                Issues.HIDDEN_TYPE_PARAMETER,
                Issues.REFERENCES_HIDDEN,
                Issues.OVERLAPPING_API_SURFACES,
                Issues.HIDING_API_METHOD_OVERRIDE,
            )

        private val SOURCE_FILES_A =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        @test.annotation.SystemApi
                        public class SystemClass {
                            public void noAnnotation() {
                            }

                            @test.annotation.Hide
                            public void hidden() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void system() {
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public void module() {
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            @test.annotation.SystemApi
                            public void moduleAndSystem() {
                            }

                            public static class NestedDefault {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }

                                @test.annotation.Hide
                                @test.annotation.SystemApi
                                public void system() {
                                }

                                @test.annotation.Hide
                                @test.annotation.ModuleApi
                                public void module() {
                                }
                            }

                            @test.annotation.Hide
                            public static class NestedHidden {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public static class NestedModule {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }

                                @test.annotation.Hide
                                @test.annotation.ModuleApi
                                public void module() {
                                }
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public void referFromModuleToSystem(SystemClass2 arg) {
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class PublicClass {
                            public void noAnnotation() {
                            }

                            @test.annotation.Hide
                            public void hidden() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void system() {
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public void module() {
                            }

                            public static class NestedDefault {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }

                                @test.annotation.Hide
                                @test.annotation.SystemApi
                                public void system() {
                                }

                                @test.annotation.Hide
                                @test.annotation.ModuleApi
                                public void module() {
                                }
                            }

                            @test.annotation.Hide
                            public static class NestedHidden {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public static class NestedModule {
                                public void noAnnotation() {
                                }

                                @test.annotation.Hide
                                public void hidden() {
                                }

                                @test.annotation.Hide
                                @test.annotation.ModuleApi
                                public void module() {
                                }
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        @test.annotation.SystemApi
                        public class SystemClass2 {
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        @test.annotation.ModuleApi
                        public class ModuleClass {
                            public void noAnnotation() {
                            }

                            @test.annotation.Hide
                            public void hidden() {
                            }

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public void module() {
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        public class HiddenClass {
                        }
                    """
                ),
            )

        private val SOURCE_FILES_B =
            arrayOf(
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        @test.annotation.ModuleApi
                        public class ModuleClass {
                            public void foo() {
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        @test.annotation.Hide
                        @test.annotation.ModuleApi
                        public class ModuleClassExtendingPublic extends PublicSubClass {
                            public void bar() {
                            }

                            // This is a @SystemApi, so this shouldn't show up in the signature file.
                            @Override
                            public void abstractMethodOverridden() {
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class PublicClass {
                            @test.annotation.Hide
                            public PublicClass() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public abstract void abstractMethodOverridden();

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public abstract void abstractMethodNotOverridden();

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public abstract void abstractMethodOverriddenByAbstract();

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void systemMethodOverridden() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void systemMethodNotOverridden() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void systemMethodHiddenInSub() {
                            }
                        }
                    """
                ),
                java(
                    """
                        package test.pkg;

                        public class PublicSubClass extends PublicClass {
                            @Override
                            public void abstractMethodOverridden() {
                            }

                            @Override
                            public abstract void abstractMethodOverriddenByAbstract();

                            @Override
                            public void systemMethodOverridden() {
                            }

                            @test.annotation.Hide
                            public void systemMethodHiddenInSub() {
                            }

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public void subMethod() {
                            }
                        }
                    """
                ),
            )
    }

    @Test
    fun `Hierarchy test - SystemApi + ModuleApi`() {
        check(
            apiSurface = TEST_SYSTEM_AND_MODULE_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_A,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ModuleClass {
                        ctor public ModuleClass();
                        method public void module();
                        method public void noAnnotation();
                      }
                      public class PublicClass {
                        method public void module();
                        method public void system();
                      }
                      public static class PublicClass.NestedDefault {
                        method public void module();
                        method public void system();
                      }
                      public static class PublicClass.NestedModule {
                        ctor public PublicClass.NestedModule();
                        method public void module();
                        method public void noAnnotation();
                      }
                      public class SystemClass {
                        ctor public SystemClass();
                        method public void module();
                        method public void moduleAndSystem();
                        method public void noAnnotation();
                        method public void referFromModuleToSystem(test.pkg.SystemClass2);
                        method public void system();
                      }
                      public static class SystemClass.NestedDefault {
                        ctor public SystemClass.NestedDefault();
                        method public void module();
                        method public void noAnnotation();
                        method public void system();
                      }
                      public static class SystemClass.NestedModule {
                        ctor public SystemClass.NestedModule();
                        method public void module();
                        method public void noAnnotation();
                      }
                      public class SystemClass2 {
                        ctor public SystemClass2();
                      }
                    }
                """
        )
    }

    @Test
    fun `Hierarchy test - SystemApi only`() {
        check(
            apiSurface = TEST_SYSTEM_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_A,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicClass {
                        method public void system();
                      }
                      public static class PublicClass.NestedDefault {
                        method public void system();
                      }
                      public class SystemClass {
                        ctor public SystemClass();
                        method public void moduleAndSystem();
                        method public void noAnnotation();
                        method public void system();
                      }
                      public static class SystemClass.NestedDefault {
                        ctor public SystemClass.NestedDefault();
                        method public void noAnnotation();
                        method public void system();
                      }
                      public class SystemClass2 {
                        ctor public SystemClass2();
                      }
                    }
                """
        )
    }

    @Test
    fun `Hierarchy test - ModuleApi Only, also check the stub files`() {
        check(
            apiSurface = TEST_MODULE_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_A,
            expectedIssues =
                """
                    src/test/pkg/SystemClass.java:24: error: Remove @test.annotation.ModuleApi from method test.pkg.SystemClass.moduleAndSystem() as it is superseded by @test.annotation.SystemApi [OverlappingApiSurfaces]
                """,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ModuleClass {
                        ctor public ModuleClass();
                        method public void module();
                        method public void noAnnotation();
                      }
                      public class PublicClass {
                        method public void module();
                      }
                      public static class PublicClass.NestedDefault {
                        method public void module();
                      }
                      public static class PublicClass.NestedModule {
                        ctor public PublicClass.NestedModule();
                        method public void module();
                        method public void noAnnotation();
                      }
                      public class SystemClass {
                        method public void module();
                        method public void referFromModuleToSystem(test.pkg.SystemClass2);
                      }
                      public static class SystemClass.NestedDefault {
                        method public void module();
                      }
                      public static class SystemClass.NestedModule {
                        ctor public SystemClass.NestedModule();
                        method public void module();
                        method public void noAnnotation();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class SystemClass {
                            public SystemClass() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void moduleAndSystem() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            public void referFromModuleToSystem(test.pkg.SystemClass2 arg) { throw new RuntimeException("Stub!"); }
                            public void system() { throw new RuntimeException("Stub!"); }
                            public static class NestedDefault {
                            public NestedDefault() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            public void system() { throw new RuntimeException("Stub!"); }
                            }
                            public static class NestedModule {
                            public NestedModule() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class PublicClass {
                            public PublicClass() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            public void system() { throw new RuntimeException("Stub!"); }
                            public static class NestedDefault {
                            public NestedDefault() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            public void system() { throw new RuntimeException("Stub!"); }
                            }
                            public static class NestedModule {
                            public NestedModule() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class SystemClass2 {
                            public SystemClass2() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class ModuleClass {
                            public ModuleClass() { throw new RuntimeException("Stub!"); }
                            public void module() { throw new RuntimeException("Stub!"); }
                            public void noAnnotation() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Hierarchy test - Can't refer from system to module`() {
        check(
            apiSurface = TEST_SYSTEM_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public class SystemClass {
                                public void foo(ModuleClass arg) {
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            @test.annotation.Hide
                            @test.annotation.ModuleApi
                            public class ModuleClass {
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/SystemClass.java:6: error: Parameter arg references hidden type test.pkg.ModuleClass. [HiddenTypeParameter]
                    src/test/pkg/SystemClass.java:6: error: Class test.pkg.ModuleClass is hidden but was referenced (in parameter type) from public parameter arg in test.pkg.SystemClass.foo(test.pkg.ModuleClass arg) [ReferencesHidden]
                    src/test/pkg/SystemClass.java:6: error: Parameter of unavailable type test.pkg.ModuleClass in test.pkg.SystemClass.foo() [UnavailableSymbol]
                """,
        )
    }

    private fun checkComplicatedCaseSystemAndModuleApi(
        apiSurface: KnownApiSurface,
        expectedApiSignature: String,
        expectedIssues: String = "",
    ) {
        check(
            apiSurface = apiSurface,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_B,
            expectedApiSignature = expectedApiSignature,
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `Complicated case - SystemApi + ModuleApi - public`() {
        checkComplicatedCaseSystemAndModuleApi(
            apiSurface = TEST_PUBLIC_API_SURFACE,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicClass {
                      }
                      public class PublicSubClass extends test.pkg.PublicClass {
                        ctor public PublicSubClass();
                        method public void abstractMethodOverridden();
                        method public abstract void abstractMethodOverriddenByAbstract();
                        method public void systemMethodOverridden();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Complicated case - SystemApi + ModuleApi - system and module`() {
        checkComplicatedCaseSystemAndModuleApi(
            apiSurface = TEST_SYSTEM_AND_MODULE_API_SURFACE,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ModuleClass {
                        ctor public ModuleClass();
                        method public void foo();
                      }
                      public class ModuleClassExtendingPublic extends test.pkg.PublicSubClass {
                        ctor public ModuleClassExtendingPublic();
                        method public void bar();
                      }
                      public class PublicClass {
                        method public abstract void abstractMethodNotOverridden();
                        method public abstract void abstractMethodOverridden();
                        method public abstract void abstractMethodOverriddenByAbstract();
                        method public void systemMethodHiddenInSub();
                        method public void systemMethodNotOverridden();
                        method public void systemMethodOverridden();
                      }
                      public class PublicSubClass extends test.pkg.PublicClass {
                        method public void subMethod();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/PublicSubClass.java:16: error: Attempting to hide method test.pkg.PublicSubClass.systemMethodHiddenInSub() which overrides method test.pkg.PublicClass.systemMethodHiddenInSub() which is already part of the API [HidingApiMethodOverride]
                """,
        )
    }

    private fun checkComplicatedCaseSystemApiOnly(
        apiSurface: KnownApiSurface,
        expectedApiSignature: String,
        expectedIssues: String = "",
    ) {
        check(
            apiSurface = apiSurface,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_B,
            expectedApiSignature = expectedApiSignature,
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `Complicated case - SystemApi only - public`() {
        checkComplicatedCaseSystemApiOnly(
            apiSurface = TEST_PUBLIC_API_SURFACE,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicClass {
                      }
                      public class PublicSubClass extends test.pkg.PublicClass {
                        ctor public PublicSubClass();
                        method public void abstractMethodOverridden();
                        method public abstract void abstractMethodOverriddenByAbstract();
                        method public void systemMethodOverridden();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Complicated case - SystemApi only - system`() {
        checkComplicatedCaseSystemApiOnly(
            apiSurface = TEST_SYSTEM_API_SURFACE,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class PublicClass {
                        method public abstract void abstractMethodNotOverridden();
                        method public abstract void abstractMethodOverridden();
                        method public abstract void abstractMethodOverriddenByAbstract();
                        method public void systemMethodHiddenInSub();
                        method public void systemMethodNotOverridden();
                        method public void systemMethodOverridden();
                      }
                      public class PublicSubClass extends test.pkg.PublicClass {
                        method public void subMethod();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/PublicSubClass.java:16: error: Attempting to hide method test.pkg.PublicSubClass.systemMethodHiddenInSub() which overrides method test.pkg.PublicClass.systemMethodHiddenInSub() which is already part of the API [HidingApiMethodOverride]
                """,
        )
    }

    @Test
    fun `Hierarchy test - Module API only`() {
        check(
            apiSurface = TEST_MODULE_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,
            sourceFiles = SOURCE_FILES_B,
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ModuleClass {
                        ctor public ModuleClass();
                        method public void foo();
                      }
                      public class ModuleClassExtendingPublic extends test.pkg.PublicSubClass {
                        ctor public ModuleClassExtendingPublic();
                        method public void bar();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/PublicSubClass.java:16: error: Attempting to hide method test.pkg.PublicSubClass.systemMethodHiddenInSub() which overrides method test.pkg.PublicClass.systemMethodHiddenInSub() which is already part of the API [HidingApiMethodOverride]
                """,
        )
    }

    @Test
    fun `Hierarchy test - Module API only with lint`() {
        check(
            apiSurface = TEST_MODULE_API_SURFACE,
            extraArguments = EXTRA_ARGS,
            format = FileFormat.V2,

            // The methods are missing nullability annotations, the lint shouldn't report for
            // the SystemApi one.
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            @test.annotation.Hide
                            @test.annotation.SystemApi
                            public class ModuleClassExtendingPublic extends PublicSubClass {
                                public Object method1() {
                                }

                                @test.annotation.Hide
                                @test.annotation.ModuleApi
                                public Object method2() {
                                }
                            }
                        """
                    )
                ),
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class ModuleClassExtendingPublic {
                        method public Object method2();
                      }
                    }
                """,
            apiLint = "",
            expectedIssues =
                """
                    src/test/pkg/ModuleClassExtendingPublic.java:11: error: Missing nullability on method `method2` return [MissingNullability]
                """
        )
    }
}
