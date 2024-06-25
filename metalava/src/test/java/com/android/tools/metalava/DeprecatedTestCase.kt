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

import com.android.tools.metalava.testing.java
import org.junit.Test

/** Test some inconsistent behavior around deprecated status. */
class DeprecatedTestCase : DriverTest() {

    @Test
    fun `Test deprecated not written out for parameter unless explicitly deprecated`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /** @deprecated */
                            @Deprecated
                            public class Foo {
                                public Foo(int p1, @Deprecated int p2) {}
                                public void method(int p1, @Deprecated int p2) {}
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @Deprecated public class Foo {
                        ctor @Deprecated public Foo(int, @Deprecated int);
                        method @Deprecated public void method(int, @Deprecated int);
                      }
                    }
                """,
        )
    }

    @Test
    fun `Test deprecated not written out for field inherited from hidden class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            interface Constants {
                                int INHERITED = 0;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

                            /** @deprecated */
                            @Deprecated
                            public class Foo implements Constants {
                                private Foo() {}
                                public static final int CONSTANT = 1;
                            }
                        """
                    ),
                ),
            api =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @Deprecated public class Foo {
                        field @Deprecated public static final int CONSTANT = 1; // 0x1
                        field public static final int INHERITED = 0; // 0x0
                      }
                    }
                """,
        )
    }
}
