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

package com.android.tools.metalava.doc

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.ARG_API_VERSION_FOR_SOURCES
import com.android.tools.metalava.ARG_API_VERSION_LABEL
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.KnownApiSurface
import com.android.tools.metalava.KnownConfigFiles
import com.android.tools.metalava.intRangeAnnotationSource
import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.testing.KnownSourceFiles.flaggedApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.uiThreadSource
import org.junit.Test
import org.junit.runners.Parameterized

/** Tests for [DocAnalyzer] applying API history from `api-versions.xml` to documentation stubs. */
class DocApiHistoryTest : DriverTest() {
    @Parameterized.Parameter(0)
    override lateinit var apiHistoryProviderLoader: ApiHistoryProvider.Loader

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testParameters() =
            listOf(
                LintApiHistoryProvider.loader,
            )
    }

    @Test
    fun `Check construct ApiLookup works correctly`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public Foo() {}
                                public Foo(int i) { this.i = i; }

                                private int i;
                            }
                        """
                    ),
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Foo" since="17">
                            <method name="&lt;init>()V" since="18"/>
                            <method name="&lt;init>(I)V" since="19"/>
                        </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 17 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 18 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 19 */
                            public Foo(int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Check construct ApiLookup works correctly for major-minor`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {
                                public Foo() {}
                                public Foo(int i) { this.i = i; }

                                private int i;
                            }
                        """
                    ),
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="4">
                        <class name="test/pkg/Foo" since="17">
                            <method name="&lt;init>()V" since="18.0"/>
                            <method name="&lt;init>(I)V" since="19.3"/>
                        </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 17 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 18 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 19.3 */
                            public Foo(int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Merge Multiple sections`() {
        check(
            expectedIssues =
                "src/android/widget/Toolbar2.java:18: error: Documentation should not specify @apiSince manually; it's computed and injected at build time by metalava [ForbiddenTag]",
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;
                            import androidx.annotation.UiThread;

                            public class Toolbar2 {
                                /**
                                 * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                                 * @return blah blah blah
                                 */
                                @UiThread
                                public int getCurrentContentInsetEnd() {
                                    return 0;
                                }

                                /**
                                 * @apiSince 15
                                 */
                                @UiThread
                                public int getCurrentContentInsetRight() {
                                    return 0;
                                }
                            }
                        """
                    ),
                    uiThreadSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar2" since="21">
                            <method name="&lt;init>(Landroid/content/Context;)V"/>
                            <method name="collapseActionView()V"/>
                            <method name="getContentInsetStartWithNavigation()I" since="24"/>
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                            <method name="getCurrentContentInsetLeft()I" since="24"/>
                            <method name="getCurrentContentInsetRight()I" since="24"/>
                            <method name="getCurrentContentInsetStart()I" since="24"/>
                        </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;
                            /** @apiSince 21 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Toolbar2 {
                            public Toolbar2() { throw new RuntimeException("Stub!"); }
                            /**
                             * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                             * <br>
                             * This method must be called on the thread that originally created
                             *            this UI element. This is typically the main thread of your app.
                             *
                             * @return blah blah blah
                             * @apiSince 24
                             */
                            public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                            /**
                             * This method must be called on the thread that originally created
                             *            this UI element. This is typically the main thread of your app.
                             * @apiSince 24
                             */
                            public int getCurrentContentInsetRight() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Merge deprecation levels`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.hardware;
                            /**
                             * The Camera class is used to set image capture settings, start/stop preview.
                             *
                             * @deprecated We recommend using the new {@link android.hardware.camera2} API for new
                             *             applications.*
                             */
                            @Deprecated
                            public class Camera {
                                /** @deprecated Use something else. */
                                public static final String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
                            }
                        """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/hardware/Camera" since="1" deprecated="21">
                            <method name="&lt;init>()V"/>
                            <method name="addCallbackBuffer([B)V" since="8"/>
                            <method name="getLogo()Landroid/graphics/drawable/Drawable;"/>
                            <field name="ACTION_NEW_VIDEO" since="14" deprecated="19"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.hardware;
                            /**
                             * The Camera class is used to set image capture settings, start/stop preview.
                             *
                             * @deprecated We recommend using the new {@link android.hardware.camera2} API for new
                             *             applications.*
                             * @apiSince 1
                             * @deprecatedSince 21
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Camera {
                            @Deprecated
                            public Camera() { throw new RuntimeException("Stub!"); }
                            /**
                             * @deprecated Use something else.
                             * @apiSince 14
                             * @deprecatedSince 19
                             */
                            @Deprecated public static final java.lang.String ACTION_NEW_VIDEO = "android.hardware.action.NEW_VIDEO";
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Deprecated constructor`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public Foo() {}
                                /** @deprecated Use {@link #Foo()} instead. */
                                @Deprecated
                                public Foo(int i) {}
                            }
                        """
                    ),
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Foo" since="17">
                            <method name="&lt;init>()V" since="18"/>
                            <method name="&lt;init>(I)V" since="18" deprecated="19"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 17 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            /** @apiSince 18 */
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /**
                             * @deprecated Use {@link #Foo()} instead.
                             * @apiSince 18
                             * @deprecatedSince 19
                             */
                            @Deprecated
                            public Foo(int i) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Deprecated class members with omitted deprecated attribute`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated Do not use. */
                            @Deprecated
                            public class Foo {
                                public Foo() {}
                                public void bar() {}
                                public static final int FIELD = 1;
                            }
                        """
                    ),
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Foo" since="5" deprecated="30">
                            <method name="&lt;init>()V"/>
                            <method name="bar()V"/>
                            <field name="FIELD"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * @deprecated Do not use.
                             * @apiSince 5
                             * @deprecatedSince 30
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Foo {
                            /**
                             * @apiSince 5
                             * @deprecatedSince 30
                             */
                            @Deprecated
                            public Foo() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 5
                             * @deprecatedSince 30
                             */
                            @Deprecated
                            public void bar() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 5
                             * @deprecatedSince 30
                             */
                            @Deprecated public static final int FIELD = 1;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Api levels around current and preview`() {
        check(
            extraArguments =
                arrayOf(ARG_API_VERSION_FOR_SOURCES, "36", ARG_API_VERSION_LABEL, "36:FOO"),
            apiSurface = KnownApiSurface.SYSTEM,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            import android.annotation.SystemApi;
                            public class Test {
                                public static final String UNIT_TEST_1 = "unit.test.1";
                                @SystemApi
                                public static final String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    ),
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg/Test" since="1">
                            <field name="UNIT_TEST_1" since="35"/>
                            <field name="UNIT_TEST_2" since="36"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            /** @apiSince 35 */
                            public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                            /** @apiSince FOO */
                            public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Api levels no current version`() {
        check(
            extraArguments = arrayOf(),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            public class Test {
                                public static final String UNIT_TEST_1 = "unit.test.1";
                                public static final String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    ),
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg/Test" since="1">
                            <field name="UNIT_TEST_1" since="24" deprecated="30"/>
                            <field name="UNIT_TEST_2" since="36"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 24
                             * @deprecatedSince 30
                             */
                            public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                            /** @apiSince 36 */
                            public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Originally hidden elements get deprecatedSince`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            public class Test {
                                @FlaggedApi("flag.name")
                                public static final String FLAGGED_FIELD = "flagged.field";
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Test" since="1">
                            <field name="FLAGGED_FIELD" since="20" deprecated="30"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 20
                             * @deprecatedSince 30
                             */
                            public static final java.lang.String FLAGGED_FIELD = "flagged.field";
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                        field public static final String FLAGGED_FIELD = "flagged.field";
                      }
                    }
                """,
        )
    }

    @Test
    fun `ForbiddenTag reported for deprecatedSince on originally hidden elements`() {
        check(
            configFiles = arrayOf(KnownConfigFiles.configEmptyApiFlags),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FlaggedApi;
                            public class Test {
                                /**
                                 * @deprecatedSince 30
                                 */
                                @FlaggedApi("flag.name")
                                public static final String FLAGGED_FIELD = "flagged.field";
                            }
                        """
                    ),
                    flaggedApiSource,
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="test/pkg/Test" since="1">
                            <field name="FLAGGED_FIELD" since="20" deprecated="30"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedIssues =
                "src/test/pkg/Test.java:8: error: Documentation should not specify @deprecatedSince manually; it's computed and injected at build time by metalava [ForbiddenTag]",
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            public Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 20
                             * @deprecatedSince 30
                             */
                            public static final java.lang.String FLAGGED_FIELD = "flagged.field";
                            }
                        """
                    )
                ),
            checkCompatibilityApiReleased =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                        field public static final String FLAGGED_FIELD = "flagged.field";
                      }
                    }
                """,
        )
    }

    @Test
    fun `No api levels on SystemApi only elements`() {
        // @SystemApi, @TestApi etc cannot get api versions since we don't have
        // accurate android.jar files (or even reliable api.txt/api.xml files) for them.
        check(
            extraArguments = arrayOf(ARG_API_VERSION_FOR_SOURCES, "36"),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            public class Test {
                                public Test(int i) {}
                                public static final String UNIT_TEST_1 = "unit.test.1";
                                public static final String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg/Test" since="1">
                            <method name="&lt;init>(I)V"/>
                            <field name="UNIT_TEST_1" since="35"/>
                            <field name="UNIT_TEST_2" since="36"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /** @apiSince 1 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            /** @apiSince 1 */
                            public Test(int i) { throw new RuntimeException("Stub!"); }
                            /** @apiSince 35 */
                            public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                            /** @apiSince 36 */
                            public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Generate API level javadocs`() {
        // TODO: Check package-info.java conflict
        // TODO: Test merging
        // TODO: Test non-merging
        check(
            extraArguments =
                arrayOf(
                    ARG_API_VERSION_FOR_SOURCES,
                    "36" // not real api level of Z
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg1;
                            public class Test1 {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg1;
                            public class Test2 {
                            }
                        """
                    ),
                    TestFiles.source(
                            "src/android/pkg2/package.html",
                            """
                                <body bgcolor="white">
                                Some existing doc here.
                                @deprecated
                                <!-- comment -->
                                </body>
                            """
                        )
                        .indented(),
                    java(
                        """
                            package android.pkg2;
                            public class Test1 {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg2;
                            public class Test2 {
                            }
                        """
                    ),
                    java(
                        """
                            package android.pkg3;
                            public class Test1 {
                            }
                        """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/pkg1/Test1" since="15"/>
                        <class name="android/pkg3/Test1" since="20"/>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg1;
                            /** @apiSince 15 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test1 {
                            public Test1() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                    java(
                        """
                            /** @apiSince 15 */
                            package android.pkg1;
                        """
                    ),
                    java(
                        """
                            /**
                             * Some existing doc here.
                             * @deprecated <!-- comment -->
                             */
                            package android.pkg2;
                        """
                    ),
                    java(
                        """
                            /** @apiSince 20 */
                            package android.pkg3;
                        """
                    )
                ),
        )
    }

    object SdkExtSinceConstants {
        val sourceFiles =
            arrayOf(
                java(
                    """
                        package android.pkg;
                        public class Test {
                            public static final String UNIT_TEST_1 = "unit.test.1";
                            public static final String UNIT_TEST_2 = "unit.test.2";
                            public static final String UNIT_TEST_3 = "unit.test.3";
                            public Test() {}
                            public void foo() {}
                            public class Inner {
                                public Inner() {}
                                public static final boolean UNIT_TEST_4 = true;
                            }
                        }
                    """
                )
            )

        const val apiVersionsXml =
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                    <sdk id="33" shortname="T-ext" name="T Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />
                    <sdk id="1000000" shortname="standalone-ext" name="Standalone Extensions" reference="some/other/CONST" />
                    <class name="android/pkg/Test" since="1" sdks="0:1,30:2,31:2,33:2">
                        <method name="foo()V"/>
                        <method name="&lt;init>()V"/>
                        <field name="UNIT_TEST_1"/>
                        <field name="UNIT_TEST_2" since="2" sdks="1000000:3,31:3,33:3,0:2"/>
                        <!--
                         ! TODO(b/283062196) - This relies on an api-versions.xml structure that is
                         !     not yet created. If the resolution of this bug is to not support this
                         !     structure then this test will need updating.
                         !-->
                        <field name="UNIT_TEST_3" since="31" sdks="1000000:4,0:31"/>
                    </class>
                    <class name="android/pkg/Test${'$'}Inner" since="1" sdks="0:1,30:2,31:2,33:2">
                        <method name="&lt;init>()V"/>
                        <field name="UNIT_TEST_4"/>
                    </class>
                </api>
            """
    }

    @Test
    fun `@sdkExtSince - finalized`() {
        check(
            extraArguments =
                arrayOf(
                    ARG_API_VERSION_FOR_SOURCES,
                    "31",
                ),
            sourceFiles = SdkExtSinceConstants.sourceFiles,
            applyApiLevelsXml = SdkExtSinceConstants.apiVersionsXml,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public void foo() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                            /**
                             * @apiSince 2
                             * @sdkExtSince Standalone Extensions 3
                             */
                            public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                            /**
                             * @apiSince 31
                             * @sdkExtSince Standalone Extensions 4
                             */
                            public static final java.lang.String UNIT_TEST_3 = "unit.test.3";
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public class Inner {
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public Inner() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public static final boolean UNIT_TEST_4 = true;
                            }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `@sdkExtSince - not finalized`() {
        check(
            sourceFiles = SdkExtSinceConstants.sourceFiles,
            applyApiLevelsXml = SdkExtSinceConstants.apiVersionsXml,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public void foo() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public static final java.lang.String UNIT_TEST_1 = "unit.test.1";
                            /**
                             * @apiSince 2
                             * @sdkExtSince Standalone Extensions 3
                             */
                            public static final java.lang.String UNIT_TEST_2 = "unit.test.2";
                            /**
                             * @apiSince 31
                             * @sdkExtSince Standalone Extensions 4
                             */
                            public static final java.lang.String UNIT_TEST_3 = "unit.test.3";
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public class Inner {
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public Inner() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 1
                             * @sdkExtSince R Extensions 2
                             */
                            public static final boolean UNIT_TEST_4 = true;
                            }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `@sdkExtSince - overloaded methods`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            public class Test {
                                private Test() {}
                                public void foo(int a) {}
                                public void foo(String b) {}
                            }
                        """
                    )
                ),
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="3">
                        <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                        <class name="android/pkg/Test" since="30">
                            <method name="foo(I)V" since="30" sdks="30:2,0:30"/>
                            <method name="foo(Ljava/lang/String;)V" since="31" sdks="30:5,0:31"/>
                        </class>
                    </api>
                """,
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            /** @apiSince 30 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Test {
                            Test() { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 30
                             * @sdkExtSince R Extensions 2
                             */
                            public void foo(int a) { throw new RuntimeException("Stub!"); }
                            /**
                             * @apiSince 31
                             * @sdkExtSince R Extensions 5
                             */
                            public void foo(java.lang.String b) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Merge API levels`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;

                            public class Toolbar {
                                /**
                                 * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                                 * @return blah blah blah
                                 */
                                public int getCurrentContentInsetEnd() {
                                    return 0;
                                }
                            }
                        """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
                            <method name="&lt;init>(Landroid/content/Context;)V"/>
                            <method name="collapseActionView()V"/>
                            <method name="getContentInsetStartWithNavigation()I" since="24"/>
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                            <method name="getCurrentContentInsetLeft()I" since="24"/>
                            <method name="getCurrentContentInsetRight()I" since="24"/>
                            <method name="getCurrentContentInsetStart()I" since="24"/>
                        </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;
                            /** @apiSince 21 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Toolbar {
                            public Toolbar() { throw new RuntimeException("Stub!"); }
                            /**
                             * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                             *
                             * @return blah blah blah
                             * @apiSince 24
                             */
                            public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Trailing comment close`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;

                            public class Toolbar {
                                /**
                                 * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here. */
                                public int getCurrentContentInsetEnd() {
                                    return 0;
                                }
                            }
                        """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                        </class>
                    </api>
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package android.widget;
                            /** @apiSince 21 */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Toolbar {
                            public Toolbar() { throw new RuntimeException("Stub!"); }
                            /**
                             * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                             * @apiSince 24
                             */
                            public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
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
}
