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

@file:Suppress("JavaDoc", "DanglingJavadoc")

package com.android.tools.metalava

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.KnownSourceFiles
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.xml
import org.junit.Test

/** Test to explore hidden versus public APIs via annotations */
class CoreApiTest : DriverTest() {
    @Test
    fun `Hidden with --hide-annotation`() {
        check(
            apiSurface = INTRA_CORE_API,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            /**
                             * Hide everything in this package:
                             */
                            @android.annotation.Hide
                            package test.pkg;
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Not included: hidden by default from package annotation
                            public class NotExposed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import libcore.api.IntraCoreApi;

                            /**
                             * Included because it is annotated with a non-recursive @IntraCoreApi
                             */
                            @android.annotation.Hide
                            @IntraCoreApi
                            public class Exposed {
                                public void stillHidden() { }
                                public String stillHidden;
                                @IntraCoreApi
                                public void exposed() { }
                                @IntraCoreApi
                                public String exposed;

                                public class StillHidden {
                                }
                            }
                        """
                    ),
                    libcoreCoreApi,
                    KnownSourceFiles.hideAnnotation,
                ),
            expectedApiSignature =
                """
                    package libcore.api {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.PACKAGE}) @libcore.api.IntraCoreApi public @interface IntraCoreApi {
                      }
                    }
                    package test.pkg {
                      @libcore.api.IntraCoreApi public class Exposed {
                        method @libcore.api.IntraCoreApi public void exposed();
                        field @libcore.api.IntraCoreApi public String exposed;
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            /** Hide everything in this package: */
                            package test.pkg;
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** Included because it is annotated with a non-recursive @IntraCoreApi */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Exposed {
                            Exposed() { throw new RuntimeException("Stub!"); }
                            public void exposed() { throw new RuntimeException("Stub!"); }
                            public java.lang.String exposed;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Hidden with package javadoc and hiding default constructor explicitly`() {
        check(
            apiSurface = INTRA_CORE_API,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            /** Hide everything in this package: */
                            @android.annotation.Hide
                            package test.pkg;
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            // Not included: hidden by default from package annotation
                            public class NotExposed {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import libcore.api.IntraCoreApi;

                            /**
                             * Included because it is annotated with a non-recursive @IntraCoreApi
                             */
                            @IntraCoreApi
                            @android.annotation.Hide
                            public class Exposed {
                                @android.annotation.Hide
                                public Exposed() { }
                                public void stillHidden() { }
                                @IntraCoreApi
                                public void exposed() { }

                                public class StillHidden {
                                }
                            }
                        """
                    ),
                    libcoreCoreApi,
                    KnownSourceFiles.hideAnnotation,
                ),
            expectedApiSignature =
                """
                    package libcore.api {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.PACKAGE}) @libcore.api.IntraCoreApi public @interface IntraCoreApi {
                      }
                    }
                    package test.pkg {
                      @libcore.api.IntraCoreApi public class Exposed {
                        method @libcore.api.IntraCoreApi public void exposed();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            /** Hide everything in this package: */
                            package test.pkg;
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            /** Included because it is annotated with a non-recursive @IntraCoreApi */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Exposed {
                            Exposed() { throw new RuntimeException("Stub!"); }
                            public void exposed() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
            docStubs = true,
        )
    }

    @Test
    fun `Complain if annotating a member and the surrounding class is not included`() {
        check(
            apiSurface = INTRA_CORE_API,
            format = FileFormat.V2,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            @android.annotation.Hide
                            package test.pkg;
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            import libcore.api.IntraCoreApi;

                            @android.annotation.Hide
                            public class Exposed {
                                public void stillHidden() { }
                                public String stillHidden;
                                @IntraCoreApi // error: can only expose methods in class also exposed
                                public void exposed() { }

                                @IntraCoreApi
                                public String exposed;

                                @IntraCoreApi // error: can only expose inner classes in exported outer class
                                public class StillHidden {
                                }
                            }
                        """
                    ),
                    libcoreCoreApi,
                    KnownSourceFiles.hideAnnotation,
                ),
            expectedApiSignature =
                """
                package libcore.api {
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE) @java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.CONSTRUCTOR, java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.PACKAGE}) @libcore.api.IntraCoreApi public @interface IntraCoreApi {
                  }
                }
                """,
            expectedIssues =
                """
                    src/test/pkg/Exposed.java:9: error: Attempting to unhide method test.pkg.Exposed.exposed(), but surrounding class test.pkg.Exposed is hidden and should also be annotated with @libcore.api.IntraCoreApi [ShowingMemberInHiddenClass]
                    src/test/pkg/Exposed.java:12: error: Attempting to unhide field test.pkg.Exposed.exposed, but surrounding class test.pkg.Exposed is hidden and should also be annotated with @libcore.api.IntraCoreApi [ShowingMemberInHiddenClass]
                    src/test/pkg/Exposed.java:15: error: Attempting to unhide class test.pkg.Exposed.StillHidden, but surrounding class test.pkg.Exposed is hidden and should also be annotated with @libcore.api.IntraCoreApi [ShowingMemberInHiddenClass]
                """,
        )
    }
}

private val INTRA_CORE_API =
    KnownApiSurface(
        "intra-core",
        xml(
            "config-public-and-system-surfaces.xml",
            """
                <config xmlns="http://www.google.com/tools/metalava/config"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../resources/schemas/config.xsd">
                    <api-surfaces>
                        <api-surface name="intra-core">
                            <selection-criteria>
                                <annotation-rule pattern="android.annotation.Hide" effect="hide"/>
                                <annotation-rule pattern="libcore.api.IntraCoreApi" recursive="false"/>
                            </selection-criteria>
                        </api-surface>
                    </api-surfaces>
                </config>
            """
        ),
    )

val libcoreCoreApi: TestFile =
    java(
        """
            package libcore.api;

            import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
            import static java.lang.annotation.ElementType.CONSTRUCTOR;
            import static java.lang.annotation.ElementType.FIELD;
            import static java.lang.annotation.ElementType.METHOD;
            import static java.lang.annotation.ElementType.PACKAGE;
            import static java.lang.annotation.ElementType.TYPE;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @SuppressWarnings("ALL")
            @IntraCoreApi // @IntraCoreApi is itself part of the intra-core API
            @Target({TYPE, FIELD, METHOD, CONSTRUCTOR, ANNOTATION_TYPE, PACKAGE})
            @Retention(RetentionPolicy.SOURCE)
            public @interface IntraCoreApi {
            }
        """
    )
