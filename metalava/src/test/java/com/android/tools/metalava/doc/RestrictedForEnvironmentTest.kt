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

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.restrictedForEnvironment
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the handling the @RestrictedForEnvironment in [DocAnalyzer] */
class RestrictedForEnvironmentTest : DriverTest() {
    private fun checkRestrictedForEnvironmentHandling(import: String, envArgument: String) {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import androidx.annotation.RestrictedForEnvironment;
                            import static $import
                            /**
                            * Javadoc for MyClass1
                            */
                            @RestrictedForEnvironment(environments = $envArgument, from = 14)
                            public class MyClass1 {
                            }
                        """
                    ),
                    restrictedForEnvironment,
                ),
            api =
                """
                    package test.pkg {
                      @RestrictedForEnvironment(environments=androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX, from=14) public class MyClass1 {
                        ctor public MyClass1();
                      }
                    }
                """,
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
    fun `Check RestrictedForEnvironment handling for SDK_SANDBOX env`() {
        checkRestrictedForEnvironmentHandling(
            "androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX",
            "SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for partial nested SDK_SANDBOX env`() {
        checkRestrictedForEnvironmentHandling(
            "androidx.annotation.RestrictedForEnvironment",
            "RestrictedForEnvironment.Environment.SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for fully nested SDK_SANDBOX env`() {
        checkRestrictedForEnvironmentHandling(
            "androidx",
            "androidx.annotation.RestrictedForEnvironment.Environment.SDK_SANDBOX"
        )
    }

    @Test
    fun `Check RestrictedForEnvironment handling for multiple annotations`() {
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
                    restrictedForEnvironment,
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
