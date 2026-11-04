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
import com.android.tools.metalava.model.ANDROID_SDK_EXTENSION_API
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.KnownSourceFiles.sdkExtensionApiSource
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
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
                        public static final int R = 30;
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
    fun `Test SdkExtensionApi emitted to signature file with inlined hexadecimal values and stripped from stubs`() {
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
            // TODO(b/507875039): Format always-inlined annotation int fields as decimal (30, 33,
            // 34, 1000000) instead of hexadecimal (0x1e, 0x21, 0x22, 0xf4240).
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(0x1e) public class Foo {
                        ctor @SdkExtensionApi(0x21) public Foo();
                        method @SdkExtensionApi(0x22) public void method();
                        field @SdkExtensionApi(0xf4240) public static final int CONST = 1; // 0x1
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
    fun `Test SdkExtensionApi emitted to signature file with inlined hexadecimal values from Kotlin source`() {
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
            // TODO(b/507875039): Format always-inlined annotation int fields as decimal (34)
            // instead of hexadecimal (0x22).
            expectedApiSignature =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @SdkExtensionApi(0x22) public final class KotlinFoo {
                        ctor public KotlinFoo();
                        method @SdkExtensionApi(0x22) public void bar();
                      }
                    }
                """,
        )
    }
}
