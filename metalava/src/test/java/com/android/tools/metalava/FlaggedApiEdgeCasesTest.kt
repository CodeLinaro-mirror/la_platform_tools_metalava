/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ARG_STUB_PACKAGES
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_REQUIRES_FLAG
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.FlaggedApiInheritance
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.FLAGGED_API_INHERITANCE
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.KnownSourceFiles.flaggedApiSource
import com.android.tools.metalava.testing.KnownSourceFiles.requiresFlagSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Edge case tests of [ANDROID_FLAGGED_API] that cannot be tested in [ParameterizedFlaggedApiTest].
 */
class FlaggedApiEdgeCasesTest : DriverTest() {
    @Test
    fun `Test FlaggedApi annotation is replaced by RequiresFlag annotation in stubs`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                @$ANDROID_FLAGGED_API("flag.name")
                                public void method();
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test combining FlaggedApi and RequiresFlag annotations results in a multipleFlagging error`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                @$ANDROID_FLAGGED_API("flag.name")
                                @$ANDROID_REQUIRES_FLAG("another.flag")
                                public void method();
                            }
                        """
                    ),
                    requiresFlagSource,
                    flaggedApiSource
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompilation = true,
            expectedIssues =
                """
                    src/test/pkg/Test.java:6: error: @RequiresFlag can not be placed on APIs that are already flagged. [MultipleFlagging]
                """,
        )
    }

    @Test
    fun `Test override flagged method from source path no previously released API`() {
        check(
            // Revert all FlaggedApi annotations.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            extraArguments =
                arrayOf(
                    // Ignore any classes other than test.pkg.
                    ARG_STUB_PACKAGES,
                    "test.pkg*"
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import $ANDROID_FLAGGED_API;

                            @$ANDROID_FLAGGED_API("flag.name")
                            public final class Test {
                                private Test() {}
                                public void method() {}
                                public int field = 0;
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            expectedIssues =
                """
                    src/test/pkg/Test.java:5: error: Cannot revert class test.pkg.Test (or any other API item) as no previously released API has been provided [NoPreviouslyReleasedApi]
                """,
        )
    }

    @Test
    fun `Test override flagged method from source path with previously released API`() {
        check(
            // Revert all FlaggedApi annotations.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            extraArguments =
                arrayOf(
                    // Ignore any classes other than test.pkg.
                    ARG_STUB_PACKAGES,
                    "test.pkg*"
                ),
            sourceFiles =
                arrayOf(
                    // A class that will be ignored during the initial codebase creation. However,
                    // as it is referenced from test.pkg.Test class below it will be loaded in later
                    // and that will result in it having an origin of ClassOrigin.SOURCE_PATH
                    // instead of ClassOrigin.COMMAND_LINE like test.pkg.Test.
                    java(
                        """
                            package other.pkg;

                            public abstract class Other {
                                @$ANDROID_FLAGGED_API("flag.name")
                                public abstract void method();
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public final class Test extends other.pkg.Other {
                                private Test() {}
                                // Overrides the flagged method in other.pkg.Other. The flagged
                                // status of the overridden method should not be ignored because
                                // while the containing class is not contributing to this API a
                                // previously released API is provided so reverting will result in
                                // the correct behavior.
                                @Override public void method() {}
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test extends other.pkg.Other {
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Test extends other.pkg.Other {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test reverting class with non-recursive annotation`() {
        check(
            apiSurface = KnownApiSurface.NON_RECURSIVE_SYSTEM,
            // Use an empty api flags which defaults all flags to disabled.
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @$ANDROID_FLAGGED_API("flag.name")
                            @$ANDROID_SYSTEM_API
                            public class Test {
                                // A member of a class that is annotated with a show annotation but
                                // is not marked as @hide. Usually, that would usually report an
                                // error but the show annotation is a non-recursive show annotation
                                // so the @hide is not required.
                                @$ANDROID_SYSTEM_API
                                public void method() {}
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        method public void method();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test javadoc for flagged class includes @apiSince`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                            * Javadoc for Test
                            */
                            @$ANDROID_FLAGGED_API("flag.name")
                            public class Test {
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                      <class name="test/pkg/Test" since="31">
                      </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * Javadoc for Test
                             * @apiSince 31
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test unresolvable flag field`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @$ANDROID_FLAGGED_API(UnresolvableFlag.FLAG_NAME)
                            public class Test {
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                      }
                    }
                """,
        )
    }

    /** Check [flaggedApiInheritance] behavior. */
    private fun checkFlaggedApiInheritance(
        flaggedApiInheritance: FlaggedApiInheritance,
        expectedApiSignature: String,
    ) {
        check(
            format =
                FileFormat.V6.buildCopy { this[FLAGGED_API_INHERITANCE] = flaggedApiInheritance },
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @$ANDROID_FLAGGED_API(Test.FLAG_NAME1)
                            public class Test {
                                private Test() {}

                                public static final String FLAG_NAME1 = "flag.name1";
                                public static final String FLAG_NAME2 = "flag.name2";

                                public class Nested {
                                    private Nested() {}

                                    public class NestedTwice {
                                        private NestedTwice() {}
                                    }
                                }

                                @$ANDROID_FLAGGED_API(Test.FLAG_NAME2)
                                public class FlaggedNested {
                                    private FlaggedNested() {}

                                    public class FlaggedNestedTwice {
                                        private FlaggedNestedTwice() {}
                                    }
                                }
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            expectedApiSignature = expectedApiSignature,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @$ANDROID_REQUIRES_FLAG("flag.name1")
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name1") public static final java.lang.String FLAG_NAME1 = "flag.name1";
                            @$ANDROID_REQUIRES_FLAG("flag.name1") public static final java.lang.String FLAG_NAME2 = "flag.name2";
                            @$ANDROID_REQUIRES_FLAG("flag.name2")
                            public class FlaggedNested {
                            FlaggedNested() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name2")
                            public class FlaggedNestedTwice {
                            FlaggedNestedTwice() { throw new RuntimeException("Stub!"); }
                            }
                            }
                            @$ANDROID_REQUIRES_FLAG("flag.name1")
                            public class Nested {
                            Nested() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name1")
                            public class NestedTwice {
                            NestedTwice() { throw new RuntimeException("Stub!"); }
                            }
                            }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test flagged API inheritance in signature files - no inheritance`() {
        checkFlaggedApiInheritance(
            flaggedApiInheritance = FlaggedApiInheritance.NONE,
            expectedApiSignature =
                """
                    // Signature format: 6.0
                    // - flagged-api-inheritance=none
                    package test.pkg {
                      @FlaggedApi("flag.name1") public class Test {
                        field public static final String FLAG_NAME1 = "flag.name1";
                        field public static final String FLAG_NAME2 = "flag.name2";
                      }
                      @FlaggedApi("flag.name2") public class Test.FlaggedNested {
                      }
                      public class Test.FlaggedNested.FlaggedNestedTwice {
                      }
                      public class Test.Nested {
                      }
                      public class Test.Nested.NestedTwice {
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test flagged API inheritance in signature files - nested-class inheritance`() {
        checkFlaggedApiInheritance(
            flaggedApiInheritance = FlaggedApiInheritance.NESTED_CLASSES,
            // TODO(b/362253909): Should be added to nested classes.
            expectedApiSignature =
                """
                    // Signature format: 6.0
                    package test.pkg {
                      @FlaggedApi("flag.name1") public class Test {
                        field public static final String FLAG_NAME1 = "flag.name1";
                        field public static final String FLAG_NAME2 = "flag.name2";
                      }
                      @FlaggedApi("flag.name2") public class Test.FlaggedNested {
                      }
                      @FlaggedApi("flag.name2") public class Test.FlaggedNested.FlaggedNestedTwice {
                      }
                      @FlaggedApi("flag.name1") public class Test.Nested {
                      }
                      @FlaggedApi("flag.name1") public class Test.Nested.NestedTwice {
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test RequiresFlag annotation in source appears in stubs`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Test {
                                @$ANDROID_REQUIRES_FLAG("flag.name")
                                public void method();
                            }
                        """
                    ),
                    requiresFlagSource,
                ),
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            @$ANDROID_REQUIRES_FLAG("flag.name")
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test FlaggedApi annotation is removed from stubs for finalized APIs that become deprecated`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;

                            public class Test {
                                /** @deprecated */
                                @Deprecated
                                @FlaggedApi("flag.name")
                                public void method() {}
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method public void method();
                  }
                }
            """,
            expectedApiSignature =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @Deprecated @FlaggedApi("flag.name") public void method();
                  }
                }
            """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            /** @deprecated */
                            @Deprecated
                            public void method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            extraArguments = arrayOf("--check-compatibility", "disabled")
        )
    }

    @Test
    fun `Test FlaggedApi annotation is removed from stubs for finalized API with annotation updates`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            import android.annotation.Nullable;

                            public class Test {
                                @FlaggedApi("flag.name")
                                @Nullable
                                public Object method() { return null; }
                            }
                        """
                    ),
                    flaggedApiSource,
                    nullableSource
                ),
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method public java.lang.Object method();
                  }
                }
            """,
            expectedApiSignature =
                """
                // Signature format: 5.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method @FlaggedApi("flag.name") public Object? method();
                  }
                }
            """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            @android.annotation.Nullable
                            public java.lang.Object method() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            extraArguments = arrayOf("--check-compatibility", "disabled")
        )
    }

    @Test
    fun `Test FlaggedApi annotation is removed from stubs for finalized Class Apis`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            /** @deprecated */
                            @Deprecated
                            @FlaggedApi("flag.name")
                            public class Test {
                                public Object method() { return null; }
                            }
                        """
                    ),
                    flaggedApiSource,
                    nullableSource
                ),
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method public java.lang.Object method();
                  }
                }
            """,
            expectedApiSignature =
                """
                // Signature format: 5.0
                package test.pkg {
                  @Deprecated @FlaggedApi("flag.name") public class Test {
                    ctor @Deprecated public Test();
                    method @Deprecated public Object! method();
                  }
                }
            """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                             package test.pkg;
                             /** @deprecated */
                             @SuppressWarnings({"unchecked", "deprecation", "all"})
                             @Deprecated
                             public class Test {
                             @Deprecated
                             public Test() { throw new RuntimeException("Stub!"); }
                             @Deprecated
                             public java.lang.Object method() { throw new RuntimeException("Stub!"); }
                             }
                        """
                    )
                ),
            extraArguments = arrayOf("--check-compatibility", "disabled")
        )
    }

    @Test
    fun `Test FlaggedApi is removed from class in stubs and RequiresFlag is added to new method but not old method`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;

                            @FlaggedApi("flag.name")
                            public class Test {
                                public Test() {}
                                public void oldMethod() {}
                                public void newMethod() {}
                            }
                        """
                    ),
                    flaggedApiSource
                ),
            checkCompatibilityApiReleased =
                """
                // Signature format: 2.0
                package test.pkg {
                  public class Test {
                    ctor public Test();
                    method public void oldMethod();
                  }
                }
            """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            @android.annotation.RequiresFlag("flag.name")
                            public void newMethod() { throw new RuntimeException("Stub!"); }
                            public void oldMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
            extraArguments = arrayOf("--check-compatibility", "disabled")
        )
    }
}
