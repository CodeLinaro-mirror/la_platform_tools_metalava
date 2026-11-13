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

import com.android.tools.metalava.cli.common.ARG_STUB_PACKAGES
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANDROID_SDK_EXTENSION_API
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.KnownSourceFiles.flaggedApiSource
import com.android.tools.metalava.testing.KnownSourceFiles.sdkExtensionApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.xml
import org.junit.Test

class SdkExtensionApiTest : DriverTest() {
    private val versionCodesSource =
        java(
            """
                package android.os;

                public class Build {
                    private Build() {}
                    public static class VERSION_CODES {
                        private VERSION_CODES() {}
                        public static final int Q = 29;
                        public static final int R = 30;
                        public static final int S_V2 = 32;
                        public static final int TIRAMISU = 33;
                        public static final int UPSIDE_DOWN_CAKE = 34;
                    }
                }
            """
        )

    private val sdkExtensionsSource =
        java(
            """
                package android.os.ext;

                public class SdkExtensions {
                    private SdkExtensions() {}
                    public static final int AD_SERVICES = 1000000;
                }
            """
        )

    @Test
    fun `Test SdkExtensionApi emitted to signature file with inlined decimal values and stripped from stubs`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import android.os.ext.SdkExtensions;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.R)
                            public class Foo {
                                @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                                public Foo() {}

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void method() {}

                                @SdkExtensionApi(SdkExtensions.AD_SERVICES)
                                public static final int CONST = 1;
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionsSource,
                    sdkExtensionApiSource,
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(30) public class Foo {
                        ctor @SdkExtensionApi(33) public Foo();
                        method @SdkExtensionApi(34) public void method();
                        field @SdkExtensionApi(1000000) public static final int CONST = 1; // 0x1
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
                            public static final int CONST = 1;
                            }
                        """
                    )
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Test SdkExtensionApi emitted to signature file with inlined decimal values from Kotlin source`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            import android.os.Build
                            import $ANDROID_SDK_EXTENSION_API

                            @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            class KotlinFoo {
                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                fun bar() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(34) public final class KotlinFoo {
                        ctor public KotlinFoo();
                        method @SdkExtensionApi(34) public void bar();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test nested classes inherit closest enclosing SdkExtensionApi in signature files`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.R)
                            public class Outer {
                                public Outer() {}
                                public void outerMethod() {}

                                public static class UnannotatedNested {
                                    public UnannotatedNested() {}
                                    public void nestedMethod() {}

                                    public static class DeeplyNested {
                                        public DeeplyNested() {}
                                    }
                                }

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public static class OverriddenNested {
                                    public OverriddenNested() {}

                                    public static class DeepUnderOverridden {
                                        public DeepUnderOverridden() {}
                                    }
                                }
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(30) public class Outer {
                        ctor public Outer();
                        method public void outerMethod();
                      }
                      @SdkExtensionApi(34) public static class Outer.OverriddenNested {
                        ctor public Outer.OverriddenNested();
                      }
                      @SdkExtensionApi(34) public static class Outer.OverriddenNested.DeepUnderOverridden {
                        ctor public Outer.OverriddenNested.DeepUnderOverridden();
                      }
                      @SdkExtensionApi(30) public static class Outer.UnannotatedNested {
                        ctor public Outer.UnannotatedNested();
                        method public void nestedMethod();
                      }
                      @SdkExtensionApi(30) public static class Outer.UnannotatedNested.DeeplyNested {
                        ctor public Outer.UnannotatedNested.DeeplyNested();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test unflagged SdkExtensionApi changes on previously released API`() {
        check(
            extraArguments =
                arrayOf(ARG_STUB_PACKAGES, "test.pkg*") + warningIssues(Issues.UNFLAGGED_API),
            apiLint =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void addedAnnotation();
                        method @SdkExtensionApi(33) public void changedAnnotation();
                        method @SdkExtensionApi(33) public void removedAnnotation();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:10: warning: Changes to modifiers, from 'public' to '@android.annotation.SdkExtensionApi(34) public' must be flagged with @FlaggedApi: method test.pkg.Foo.addedAnnotation() [UnflaggedApi]
                    src/test/pkg/Foo.java:13: warning: Changes to modifiers, from '@android.annotation.SdkExtensionApi(33) public' to '@android.annotation.SdkExtensionApi(34) public' must be flagged with @FlaggedApi: method test.pkg.Foo.changedAnnotation() [UnflaggedApi]
                    src/test/pkg/Foo.java:15: warning: Changes to modifiers, from '@android.annotation.SdkExtensionApi(33) public' to 'public' must be flagged with @FlaggedApi: method test.pkg.Foo.removedAnnotation() [UnflaggedApi]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            public class Foo {
                                public Foo() {}

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void addedAnnotation() {}

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void changedAnnotation() {}

                                public void removedAnnotation() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
        )
    }

    @Test
    fun `Test FlaggedApi on SdkExtensionApi changes with KEEP, REVERT, and FINALIZE`() {
        val previouslyReleasedApi =
            """
                // Signature format: 5.0
                package test.pkg {
                  @SdkExtensionApi(30) public class ExistingExtensionOuter {
                    ctor public ExistingExtensionOuter();
                  }
                  @SdkExtensionApi(30) public static class ExistingExtensionOuter.UnchangedNested {
                    ctor public ExistingExtensionOuter.UnchangedNested();
                  }
                  public class Foo {
                    ctor public Foo();
                    method public void addedKept();
                    method public void addedFinalized();
                    method public void addedReverted();
                  }
                  public class Outer {
                    ctor public Outer();
                    method public void keptInnerMethod();
                  }
                  public static class Outer.UnannotatedNested {
                    ctor public Outer.UnannotatedNested();
                  }
                }
            """

        check(
            extraArguments =
                arrayOf(ARG_STUB_PACKAGES, "test.pkg*") + warningIssues(Issues.UNFLAGGED_API),
            configFiles =
                arrayOf(
                    xml(
                        "config-flags.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                                <api-flags>
                                    <api-flag package="test.pkg" name="kept_flag" mutability="mutable" status="enabled" is-exported="true"/>
                                    <api-flag package="test.pkg" name="finalized_flag" mutability="immutable" status="enabled" is-exported="true"/>
                                </api-flags>
                            </config>
                        """,
                    ),
                ),
            apiLint = previouslyReleasedApi,
            checkCompatibilityApiReleased = previouslyReleasedApi,
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /** @hide */
                            public class Flags {
                                public static final String FLAG_KEPT_FLAG = "test.pkg.kept_flag";
                                public static final String FLAG_FINALIZED_FLAG = "test.pkg.finalized_flag";
                                public static final String FLAG_REVERTED_FLAG = "test.pkg.reverted_flag";
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.R)
                            public class ExistingExtensionOuter {
                                public ExistingExtensionOuter() {}

                                public static class UnchangedNested {
                                    public UnchangedNested() {}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import $ANDROID_FLAGGED_API;
                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            public class Foo {
                                public Foo() {}

                                @FlaggedApi(Flags.FLAG_KEPT_FLAG)
                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void addedKept() {}

                                @FlaggedApi(Flags.FLAG_FINALIZED_FLAG)
                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void addedFinalized() {}

                                @FlaggedApi(Flags.FLAG_REVERTED_FLAG)
                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void addedReverted() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import $ANDROID_FLAGGED_API;
                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @FlaggedApi(Flags.FLAG_REVERTED_FLAG)
                            @SdkExtensionApi(Build.VERSION_CODES.R)
                            public class Outer {
                                public Outer() {}

                                @FlaggedApi(Flags.FLAG_KEPT_FLAG)
                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void keptInnerMethod() {}

                                public static class UnannotatedNested {
                                    public UnannotatedNested() {}
                                }
                            }
                        """
                    ),
                    flaggedApiSource,
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(30) public class ExistingExtensionOuter {
                        ctor public ExistingExtensionOuter();
                      }
                      @SdkExtensionApi(30) public static class ExistingExtensionOuter.UnchangedNested {
                        ctor public ExistingExtensionOuter.UnchangedNested();
                      }
                      public class Foo {
                        ctor public Foo();
                        method @SdkExtensionApi(34) public void addedFinalized();
                        method @FlaggedApi("test.pkg.kept_flag") @SdkExtensionApi(34) public void addedKept();
                        method public void addedReverted();
                      }
                      public class Outer {
                        ctor public Outer();
                        method @FlaggedApi("test.pkg.kept_flag") @SdkExtensionApi(34) public void keptInnerMethod();
                      }
                      public static class Outer.UnannotatedNested {
                        ctor public Outer.UnannotatedNested();
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test ApiLint rejects literal and invalid SdkExtensionApi values`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            apiLint =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void existingWithInvalidFloor();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:9: error: Invalid @SdkExtensionApi value 32: must be >= 30 (excluding 32) [InvalidSdkExtensionApi]
                    src/test/pkg/Foo.java:12: error: @SdkExtensionApi must reference a symbolic constant from android.os.Build.VERSION_CODES or android.os.ext.SdkExtensions, not a literal [InvalidSdkExtensionApi]
                    src/test/pkg/Foo.java:15: error: Invalid @SdkExtensionApi value 29: must be >= 30 (excluding 32) [InvalidSdkExtensionApi]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            public class Foo {
                                public Foo() {}

                                @SdkExtensionApi(Build.VERSION_CODES.S_V2)
                                public void existingWithInvalidFloor() {}

                                @SdkExtensionApi(34)
                                public void literalValue() {}

                                @SdkExtensionApi(Build.VERSION_CODES.Q)
                                public void preRFloor() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
        )
    }

    @Test
    fun `Test ApiLint rejects enclosing class SdkExtensionApi contradictions`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            apiLint = "",
            expectedIssues =
                """
                    src/test/pkg/Foo.java:11: error: @SdkExtensionApi(33) on method test.pkg.Foo.lowerFloorThanClass() cannot be lower than @SdkExtensionApi(34) on enclosing class test.pkg.Foo [InvalidSdkExtensionApi]
                    src/test/pkg/Foo.java:14: error: @SdkExtensionApi(1000000) on method test.pkg.Foo.mixedAdServices() conflicts with @SdkExtensionApi(34) on enclosing class test.pkg.Foo: cannot mix AD_SERVICES and dessert extension floors [InvalidSdkExtensionApi]
                    src/test/pkg/Foo.java:17: error: @SdkExtensionApi(30) on class test.pkg.Foo.LowerNested cannot be lower than @SdkExtensionApi(34) on enclosing class test.pkg.Foo [InvalidSdkExtensionApi]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import android.os.ext.SdkExtensions;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            public class Foo {
                                public Foo() {}

                                @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                                public void lowerFloorThanClass() {}

                                @SdkExtensionApi(SdkExtensions.AD_SERVICES)
                                public void mixedAdServices() {}

                                @SdkExtensionApi(Build.VERSION_CODES.R)
                                public static class LowerNested {
                                    public LowerNested() {}
                                }
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionsSource,
                    sdkExtensionApiSource,
                ),
        )
    }

    @Test
    fun `Test CompatibilityCheck warns on SdkExtensionApi changes`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Foo {
                        ctor public Foo();
                        method public void addedAnnotation();
                        method @SdkExtensionApi(33) public void droppedAnnotation();
                        method @SdkExtensionApi(34) public void loweredFloor();
                        method @SdkExtensionApi(33) public void raisedFloor();
                        method @SdkExtensionApi(33) public void switchedToAdServices();
                        method @SdkExtensionApi(33) public void unchangedFloor();
                      }
                      public class PromotedToClass {
                        ctor @SdkExtensionApi(33) public PromotedToClass();
                        method @SdkExtensionApi(33) public void keptViaClass();
                      }
                    }
                """,
            expectedIssues =
                """
                    src/test/pkg/Foo.java:12: warning: Method test.pkg.Foo.droppedAnnotation() has removed @SdkExtensionApi(33) [ChangedSdkExtensionApi]
                    src/test/pkg/Foo.java:14: warning: Method test.pkg.Foo.loweredFloor() has changed @SdkExtensionApi floor from 34 to 33 [ChangedSdkExtensionApi]
                    src/test/pkg/Foo.java:17: warning: Method test.pkg.Foo.raisedFloor() has changed @SdkExtensionApi floor from 33 to 34 [ChangedSdkExtensionApi]
                    src/test/pkg/Foo.java:20: warning: Method test.pkg.Foo.switchedToAdServices() has changed @SdkExtensionApi floor from 33 to 1000000 [ChangedSdkExtensionApi]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import android.os.ext.SdkExtensions;
                            import $ANDROID_SDK_EXTENSION_API;

                            public class Foo {
                                public Foo() {}

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void addedAnnotation() {}
                                public void droppedAnnotation() {}

                                @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                                public void loweredFloor() {}

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public void raisedFloor() {}

                                @SdkExtensionApi(SdkExtensions.AD_SERVICES)
                                public void switchedToAdServices() {}

                                @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                                public void unchangedFloor() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                            public class PromotedToClass {
                                public PromotedToClass() {}
                                public void keptViaClass() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionsSource,
                    sdkExtensionApiSource,
                ),
        )
    }

    @Test
    fun `Test CompatibilityCheck warns on class and field SdkExtensionApi changes`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      public class Fields {
                        ctor public Fields();
                        field @SdkExtensionApi(33) public static final int droppedField = 1; // 0x1
                        field @SdkExtensionApi(33) public static final int raisedField = 2; // 0x2
                        field @SdkExtensionApi(33) public static final int unchangedField = 3; // 0x3
                      }
                      @SdkExtensionApi(33) public class MemberDropsOverride {
                        ctor public MemberDropsOverride();
                        method @SdkExtensionApi(34) public void droppedOverride();
                      }
                      @SdkExtensionApi(33) public class RaisedClass {
                        ctor public RaisedClass();
                        method public void inheritsClassFloor();
                        field public static final int INHERITS_CLASS_FLOOR = 1; // 0x1
                      }
                      @SdkExtensionApi(33) public static class RaisedClass.Nested {
                        ctor public RaisedClass.Nested();
                      }
                      @SdkExtensionApi(33) public class RemovedClass {
                        ctor public RemovedClass();
                        method public void inheritsClassFloor();
                      }
                    }
                """,
            // Changes to a class floor are only reported on the class, not on the members and
            // nested classes that inherit the floor from it, e.g.
            // `RaisedClass.inheritsClassFloor()`,
            // `RaisedClass.INHERITS_CLASS_FLOOR`, `RaisedClass.Nested` and
            // `RemovedClass.inheritsClassFloor()`. Removing a member's own floor so that it
            // inherits
            // a different floor from its class, e.g. `MemberDropsOverride.droppedOverride()`, is
            // reported on the member. `Fields.unchangedField` is not reported.
            expectedIssues =
                """
                    src/test/pkg/Fields.java:9: warning: Field test.pkg.Fields.droppedField has removed @SdkExtensionApi(33) [ChangedSdkExtensionApi]
                    src/test/pkg/Fields.java:11: warning: Field test.pkg.Fields.raisedField has changed @SdkExtensionApi floor from 33 to 34 [ChangedSdkExtensionApi]
                    src/test/pkg/MemberDropsOverride.java:9: warning: Method test.pkg.MemberDropsOverride.droppedOverride() has changed @SdkExtensionApi floor from 34 to 33 [ChangedSdkExtensionApi]
                    src/test/pkg/RaisedClass.java:6: warning: Class test.pkg.RaisedClass has changed @SdkExtensionApi floor from 33 to 34 [ChangedSdkExtensionApi]
                    src/test/pkg/RemovedClass.java:3: warning: Class test.pkg.RemovedClass has removed @SdkExtensionApi(33) [ChangedSdkExtensionApi]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            public class Fields {
                                public Fields() {}

                                public static final int droppedField = 1;

                                @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                                public static final int raisedField = 2;

                                @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                                public static final int unchangedField = 3;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                            public class MemberDropsOverride {
                                public MemberDropsOverride() {}
                                public void droppedOverride() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            public class RaisedClass {
                                public RaisedClass() {}
                                public void inheritsClassFloor() {}
                                public static final int INHERITS_CLASS_FLOOR = 1;

                                public static class Nested {
                                    public Nested() {}
                                }
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class RemovedClass {
                                public RemovedClass() {}
                                public void inheritsClassFloor() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
        )
    }

    @Test
    fun `Test CompatibilityCheck warns on SdkExtensionApi changes when members move to a super class`() {
        check(
            extraArguments = arrayOf(ARG_STUB_PACKAGES, "test.pkg*"),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(34) public class AnnotatedBase {
                        ctor public AnnotatedBase();
                      }
                      @SdkExtensionApi(33) public class AnnotatedSub extends test.pkg.AnnotatedBase {
                        ctor public AnnotatedSub();
                        method public void movedToAnnotatedBase();
                      }
                      public class UnannotatedBase {
                        ctor public UnannotatedBase();
                      }
                      @SdkExtensionApi(33) public class UnannotatedSub extends test.pkg.UnannotatedBase {
                        ctor public UnannotatedSub();
                        method public void movedToUnannotatedBase();
                      }
                    }
                """,
            // When a member is moved to a super class, ComparisonVisitor compares the old member
            // with a duplicate of the super class member whose containing class is the sub class,
            // so the effective floor of the moved member is still inherited from the sub class and
            // is unchanged.
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            public class AnnotatedBase {
                                public AnnotatedBase() {}
                                public void movedToAnnotatedBase() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                            public class AnnotatedSub extends AnnotatedBase {
                                public AnnotatedSub() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            public class UnannotatedBase {
                                public UnannotatedBase() {}
                                public void movedToUnannotatedBase() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            import android.os.Build;
                            import $ANDROID_SDK_EXTENSION_API;

                            @SdkExtensionApi(Build.VERSION_CODES.TIRAMISU)
                            public class UnannotatedSub extends UnannotatedBase {
                                public UnannotatedSub() {}
                            }
                        """
                    ),
                    versionCodesSource,
                    sdkExtensionApiSource,
                ),
        )
    }
}
