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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability.IMMUTABLE
import com.android.tools.metalava.config.ApiFlagActionConfig.Mutability.MUTABLE
import com.android.tools.metalava.config.ApiFlagActionConfig.Status.DISABLED
import com.android.tools.metalava.config.ApiFlagActionConfig.Status.ENABLED
import com.android.tools.metalava.config.ApiFlagConfig
import com.android.tools.metalava.config.ApiFlagsConfig
import com.android.tools.metalava.config.Config
import com.android.tools.metalava.config.toTestFile
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.api.flags.ApiFlagAction
import com.android.tools.metalava.model.api.flags.ApiFlagAction.FINALIZE
import com.android.tools.metalava.model.api.flags.ApiFlagAction.KEEP
import com.android.tools.metalava.model.api.flags.ApiFlagAction.REVERT
import com.android.tools.metalava.testing.KnownSourceFiles.flaggedApiSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Tests for nested `@FlaggedApi` annotations, e.g. `outer` on an outer class and `nested` on a
 * nested class or class member.
 */
class NestedFlaggedApiTest : DriverTest() {
    /** Create an [ApiFlagConfig] for flag [name] that produces [action]. */
    private fun createApiFlagConfig(name: String, action: ApiFlagAction): ApiFlagConfig =
        when (action) {
            REVERT ->
                ApiFlagConfig(
                    pkg = "test.pkg",
                    name = name,
                    mutability = IMMUTABLE,
                    status = DISABLED,
                    isExported = true,
                )
            KEEP ->
                ApiFlagConfig(
                    pkg = "test.pkg",
                    name = name,
                    mutability = MUTABLE,
                    status = ENABLED,
                    isExported = true,
                )
            FINALIZE ->
                ApiFlagConfig(
                    pkg = "test.pkg",
                    name = name,
                    mutability = IMMUTABLE,
                    status = ENABLED,
                    isExported = true,
                )
        }

    /**
     * Create a flags configuration [TestFile] for [flags] (pairs of flag name and [ApiFlagAction]).
     */
    private fun flagsConfigFile(vararg flags: Pair<String, ApiFlagAction>): TestFile {
        val config =
            Config(
                apiFlags =
                    ApiFlagsConfig(
                        flags = flags.map { (name, action) -> createApiFlagConfig(name, action) }
                    )
            )
        return config.toTestFile("flags-config.xml")
    }

    /**
     * Check the behavior of nested `@FlaggedApi` annotations (`outer` on the outer class `Foo` and
     * `nested` on a nested class and method) when configured with [outerAction] and [nestedAction].
     */
    private fun checkNestedFlags(
        outerAction: ApiFlagAction,
        nestedAction: ApiFlagAction,
        expectedApiSignature: String,
        expectedStubFiles: Array<TestFile> = emptyArray(),
        expectedIssues: String = "",
    ) {
        check(
            configFiles =
                arrayOf(flagsConfigFile("outer" to outerAction, "nested" to nestedAction)),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import $ANDROID_FLAGGED_API;

                            @FlaggedApi("test.pkg.outer")
                            public class Foo {
                                public Foo() {}

                                public void method() {}

                                @FlaggedApi("test.pkg.nested")
                                public void flaggedMethod() {}

                                public static class Nested {
                                    public Nested() {}
                                    public void method() {}
                                }

                                @FlaggedApi("test.pkg.nested")
                                public static class FlaggedNested {
                                    public FlaggedNested() {}
                                    public void method() {}
                                }
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                """,
            expectedApiSignature = expectedApiSignature,
            expectedStubFiles = expectedStubFiles,
            stubPaths = expectedStubFiles.map { it.targetRelativePath }.toTypedArray(),
            expectedIssues = expectedIssues,
        )
    }

    @Test
    fun `Test outer revert and nested revert`() {
        checkNestedFlags(
            outerAction = REVERT,
            nestedAction = REVERT,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                """,
            expectedStubFiles = emptyArray(),
        )
    }

    @Test
    fun `Test outer revert and nested keep`() {
        checkNestedFlags(
            outerAction = REVERT,
            nestedAction = KEEP,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                """,
            expectedStubFiles = emptyArray(),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: @FlaggedApi flag test.pkg.outer is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:11: error: @FlaggedApi flag test.pkg.nested is not-finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:19: error: @FlaggedApi flag test.pkg.nested is not-finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                """,
        )
    }

    @Test
    fun `Test outer revert and nested finalize`() {
        checkNestedFlags(
            outerAction = REVERT,
            nestedAction = FINALIZE,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                """,
            expectedStubFiles = emptyArray(),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: @FlaggedApi flag test.pkg.outer is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:11: error: @FlaggedApi flag test.pkg.nested is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:19: error: @FlaggedApi flag test.pkg.nested is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                """,
        )
    }

    @Test
    fun `Test outer keep and nested revert`() {
        checkNestedFlags(
            outerAction = KEEP,
            nestedAction = REVERT,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @FlaggedApi("test.pkg.outer") public class Foo {
                        ctor public Foo();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public class Foo {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Foo() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public static class Nested {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Nested() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test outer keep and nested keep`() {
        checkNestedFlags(
            outerAction = KEEP,
            nestedAction = KEEP,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @FlaggedApi("test.pkg.outer") public class Foo {
                        ctor public Foo();
                        method @FlaggedApi("test.pkg.nested") public void flaggedMethod();
                        method public void method();
                      }
                      @FlaggedApi("test.pkg.nested") public static class Foo.FlaggedNested {
                        ctor public Foo.FlaggedNested();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public class Foo {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Foo() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public void flaggedMethod() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public static class FlaggedNested {
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public FlaggedNested() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public static class Nested {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Nested() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test outer keep and nested finalize`() {
        checkNestedFlags(
            outerAction = KEEP,
            nestedAction = FINALIZE,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @FlaggedApi("test.pkg.outer") public class Foo {
                        ctor public Foo();
                        method public void flaggedMethod();
                        method public void method();
                      }
                      public static class Foo.FlaggedNested {
                        ctor public Foo.FlaggedNested();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public class Foo {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public void flaggedMethod() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            public static class FlaggedNested {
                            public FlaggedNested() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public static class Nested {
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public Nested() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.outer")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: @FlaggedApi flag test.pkg.outer is not-finalized but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:11: error: @FlaggedApi flag test.pkg.nested is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:19: error: @FlaggedApi flag test.pkg.nested is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                """,
        )
    }

    @Test
    fun `Test outer finalize and nested revert`() {
        checkNestedFlags(
            outerAction = FINALIZE,
            nestedAction = REVERT,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            public static class Nested {
                            public Nested() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test outer finalize and nested keep`() {
        checkNestedFlags(
            outerAction = FINALIZE,
            nestedAction = KEEP,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method @FlaggedApi("test.pkg.nested") public void flaggedMethod();
                        method public void method();
                      }
                      @FlaggedApi("test.pkg.nested") public static class Foo.FlaggedNested {
                        ctor public Foo.FlaggedNested();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public void flaggedMethod() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public static class FlaggedNested {
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public FlaggedNested() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("test.pkg.nested")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            public static class Nested {
                            public Nested() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test outer finalize and nested finalize`() {
        checkNestedFlags(
            outerAction = FINALIZE,
            nestedAction = FINALIZE,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void flaggedMethod();
                        method public void method();
                      }
                      public static class Foo.FlaggedNested {
                        ctor public Foo.FlaggedNested();
                        method public void method();
                      }
                      public static class Foo.Nested {
                        ctor public Foo.Nested();
                        method public void method();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public void flaggedMethod() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            public static class FlaggedNested {
                            public FlaggedNested() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            public static class Nested {
                            public Nested() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test outer revert, nested1 finalize, nested2 revert, nested3 finalize`() {
        check(
            configFiles =
                arrayOf(
                    flagsConfigFile(
                        "outer" to REVERT,
                        "nested1" to FINALIZE,
                        "nested2" to REVERT,
                        "nested3" to FINALIZE,
                    )
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import $ANDROID_FLAGGED_API;

                            @FlaggedApi("test.pkg.outer")
                            public class Foo {
                                public Foo() {}

                                @FlaggedApi("test.pkg.nested1")
                                public static class Nested1 {
                                    public Nested1() {}

                                    @FlaggedApi("test.pkg.nested2")
                                    public static class Nested2 {
                                        public Nested2() {}

                                        @FlaggedApi("test.pkg.nested3")
                                        public void method() {}
                                    }
                                }
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                """,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                """,
            expectedStubFiles = emptyArray(),
            stubPaths = emptyArray(),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: @FlaggedApi flag test.pkg.outer is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:9: error: @FlaggedApi flag test.pkg.nested1 is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:13: error: @FlaggedApi flag test.pkg.nested2 is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:17: error: @FlaggedApi flag test.pkg.nested3 is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                """,
        )
    }

    @Test
    fun `Test outer revert, nested1 revert, nested2 finalize`() {
        check(
            configFiles =
                arrayOf(
                    flagsConfigFile(
                        "outer" to REVERT,
                        "nested1" to REVERT,
                        "nested2" to FINALIZE,
                    )
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import $ANDROID_FLAGGED_API;

                            @FlaggedApi("test.pkg.outer")
                            public class Foo {
                                public Foo() {}

                                @FlaggedApi("test.pkg.nested1")
                                public static class Nested1 {
                                    public Nested1() {}

                                    @FlaggedApi("test.pkg.nested2")
                                    public void method() {}
                                }
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                """,
            expectedApiSignature =
                """
                    // Signature format: 5.0
                """,
            expectedStubFiles = emptyArray(),
            stubPaths = emptyArray(),
            expectedIssues =
                """
                    src/test/pkg/Foo.java:5: error: @FlaggedApi flag test.pkg.outer is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:9: error: @FlaggedApi flag test.pkg.nested1 is reverted but contains flags in a conflicting state [InvalidFlagNesting]
                    src/test/pkg/Foo.java:13: error: @FlaggedApi flag test.pkg.nested2 is finalized but is contained by a flag in a conflicting state [InvalidFlagNesting]
                """,
        )
    }
}
