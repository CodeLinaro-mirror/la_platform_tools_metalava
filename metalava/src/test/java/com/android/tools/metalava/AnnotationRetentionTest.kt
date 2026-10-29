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

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class AnnotationRetentionTest : DriverTest() {
    @Test
    fun `Annotation retention - java`() {
        // For annotations where the java.lang.annotation classes themselves are not
        // part of the source tree, ensure that we compute the right retention (runtime, meaning
        // it should show up in the stubs file.).
        check(
            format = FileFormat.V4,
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public @interface Foo {
                                String value();
                            }
                        """
                    ),
                    java(
                        """
                            package android.annotation;
                            import static java.lang.annotation.ElementType.*;
                            import java.lang.annotation.*;
                            @Target({TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE})
                            @Retention(RetentionPolicy.CLASS)
                            @SuppressWarnings("ALL")
                            public @interface SuppressLint {
                                String[] value();
                            }
                        """
                    ),
                ),
            // Override default to emit android.annotation classes.
            skipEmitPackages = emptyList(),
            expectedApiSignature =
                """
                    // Signature format: 4.0
                    package android.annotation {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE}) public @interface SuppressLint {
                        method public abstract String[] value();
                      }
                    }
                    package test.pkg {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) public @interface Foo {
                        method public abstract String value();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    // For annotations where the java.lang.annotation classes themselves are not
                    // part of the source tree, ensure that we compute the right retention (runtime,
                    // meaning it should show up in the stubs file.).
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                            public @interface Foo {
                            public java.lang.String value();
                            }
                        """
                    ),
                    java(
                        """
                            package android.annotation;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                            @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.LOCAL_VARIABLE})
                            public @interface SuppressLint {
                            public java.lang.String[] value();
                            }
                        """
                    )
                )
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Annotation retention - kotlin`() {
        // For annotations where the java.lang.annotation classes themselves are not
        // part of the source tree, ensure that we compute the right retention (runtime, meaning
        // it should show up in the stubs file.).
        check(
            format = FileFormat.V4,
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            @DslMarker
                            annotation class ImplicitRuntimeRetention

                            @Retention(AnnotationRetention.RUNTIME)
                            annotation class ExplicitRuntimeRetention
                        """
                    )
                ),
            expectedApiSignature =
                """
                    // Signature format: 4.0
                    package test.pkg {
                      @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.RUNTIME) public @interface ExplicitRuntimeRetention {
                      }
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @kotlin.DslMarker public @interface ImplicitRuntimeRetention {
                      }
                    }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Should write annotation retention on annotation class even when explicitly annotated with non-retention annotation - kotlin source`() {
        check(
            format = FileFormat.V4,
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        annotation class MyMetaAnnotation

                        @MyMetaAnnotation
                        annotation class MyAnnotation
                        """
                    )
                ),
            expectedApiSignature =
                // MyAnnotation should have a RUNTIME retention written to it (as that is the Kotlin
                // default)
                """
                package test.pkg {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @test.pkg.MyMetaAnnotation public @interface MyAnnotation {
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface MyMetaAnnotation {
                  }
                }
                """,
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Don't write annotation retention twice to signature file if annotation is explicitly marked with retention in source`() {
        check(
            format = FileFormat.V4,
            extraArguments = arrayOf(ARG_EXCLUDE_ALL_ANNOTATIONS),
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg

                        @Retention(AnnotationRetention.SOURCE)
                        public annotation class MyAnnotation
                        """
                    )
                ),
            expectedApiSignature =
                """
                package test.pkg {
                  @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.SOURCE) public @interface MyAnnotation {
                  }
                }
                """,
        )
    }
}
