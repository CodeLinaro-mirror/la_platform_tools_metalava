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

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.xml
import org.junit.Test

class AnnotationOutputTest : DriverTest() {

    @Test
    fun `Annotation excluded via annotation-classes config`() {
        check(
            apiSurface = KnownApiSurface.PUBLIC,
            format = FileFormat.V2,
            configFiles =
                arrayOf(
                    xml(
                        "annotation-classes-config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config">
                              <annotation-classes>
                                <annotation-class name="test.pkg.ExcludedAnno" targets="none"/>
                              </annotation-classes>
                            </config>
                        """,
                    )
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;

                            @Retention(RetentionPolicy.RUNTIME)
                            public @interface ExcludedAnno {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            @ExcludedAnno
                            public class TestClass {
                                @ExcludedAnno
                                public void testMethod() {}
                            }
                        """
                    ),
                ),
            expectedApiSignature =
                """
                    // Signature format: 2.0
                    package test.pkg {
                      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface ExcludedAnno {
                      }
                      public class TestClass {
                        ctor public TestClass();
                        method public void testMethod();
                      }
                    }
                """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                            public @interface ExcludedAnno {
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class TestClass {
                            public TestClass() { throw new RuntimeException("Stub!"); }
                            public void testMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }
}
