/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidRestrictedForEnvironment
import com.android.tools.metalava.androidXRestrictedForEnvironment
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PACKAGE
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PACKAGE
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the handling the @RestrictedForEnvironment in [DocAnalyzer] */
class RestrictedForEnvironmentTest : DriverTest() {
    private fun checkRestrictedForEnvironmentHandling(
        import: String,
        envArgument: String,
        packageName: String = ANDROIDX_ANNOTATION_PACKAGE,
        restrictedForEnvironmentClass: TestFile = androidXRestrictedForEnvironment,
    ) {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import $packageName.RestrictedForEnvironment;
                            import static $import;
                            /**
                            * Javadoc for MyClass1
                            */
                            @RestrictedForEnvironment(environments = $envArgument, from = 14)
                            public class MyClass1 {
                            }
                        """
                    ),
                    restrictedForEnvironmentClass,
                ),
            api =
                """
                    package test.pkg {
                      @RestrictedForEnvironment(environments=$packageName.RestrictedForEnvironment.Environment.SDK_SANDBOX, from=14) public class MyClass1 {
                        ctor public MyClass1();
                      }
                    }
                """,
            docStubs = true,
            skipEmitPackages = listOf(packageName),
            extractAnnotations =
                mapOf(
                    "test.pkg" to
                        """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <root>
                              <item name="test.pkg.MyClass1">
                                <annotation name="androidx.annotation.RestrictedForEnvironment">
                                  <val name="environments" val="androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX" />
                                  <val name="from" val="14" />
                                </annotation>
                              </item>
                            </root>
                        """
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * Javadoc for MyClass1
                             * <br>
                             * Restricted for SDK Runtime environment in API level 14.
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class MyClass1 {
                            public MyClass1() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                )
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for SDK_SANDBOX env - android`() {
        checkRestrictedForEnvironmentHandling(
            "android.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX",
            "SDK_SANDBOX",
            packageName = ANDROID_ANNOTATION_PACKAGE,
            restrictedForEnvironmentClass = androidRestrictedForEnvironment
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for SDK_SANDBOX env - androidx`() {
        checkRestrictedForEnvironmentHandling(
            "androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX",
            "SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for partial nested SDK_SANDBOX env - androidx`() {
        checkRestrictedForEnvironmentHandling(
            "androidx.annotation.RestrictedForEnvironment",
            "RestrictedForEnvironment.Environment.SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for fully nested SDK_SANDBOX env - androidx`() {
        checkRestrictedForEnvironmentHandling(
            "androidx",
            "androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for multiple annotations - androidx`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.RestrictedForEnvironment;
                            import static androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX;
                            /**
                            * Javadoc for MyClass1
                            */
                            @RestrictedForEnvironment(environments = SDK_SANDBOX, from = 14)
                            @RestrictedForEnvironment(environments = SDK_SANDBOX, from = 16)
                            public class MyClass1 {
                            }
                        """
                    ),
                    androidXRestrictedForEnvironment,
                ),
            docStubs = true,
            skipEmitPackages = listOf("androidx.annotation"),
            extractAnnotations =
                mapOf(
                    "test.pkg" to
                        """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <root>
                              <item name="test.pkg.MyClass1">
                                <annotation name="androidx.annotation.RestrictedForEnvironment">
                                  <val name="environments" val="androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX" />
                                  <val name="from" val="14" />
                                </annotation>
                                <annotation name="androidx.annotation.RestrictedForEnvironment">
                                  <val name="environments" val="androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX" />
                                  <val name="from" val="16" />
                                </annotation>
                              </item>
                            </root>
                        """
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /**
                             * Javadoc for MyClass1
                             * <br>
                             * Restricted for SDK Runtime environment in API level 14.
                             * <br>
                             * Restricted for SDK Runtime environment in API level 16.
                             */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class MyClass1 {
                            public MyClass1() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }
}
