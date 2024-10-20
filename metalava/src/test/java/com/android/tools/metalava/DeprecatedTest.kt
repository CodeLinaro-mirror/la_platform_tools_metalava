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
class DeprecatedTest : DriverTest() {

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
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Foo {
                            @Deprecated
                            public Foo(int p1, @Deprecated int p2) { throw new RuntimeException("Stub!"); }
                            @Deprecated
                            public void method(int p1, @Deprecated int p2) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test deprecated is not written out for field inherited from deprecated hidden class`() {
        // Makes sure that deprecated status is not copied when the inherited field is only
        // deprecated implicitly because it is contained with a class that is deprecated.
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /** @deprecated */
                            @Deprecated
                            interface Constants {
                                int INHERITED = 0;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

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
                      public class Foo {
                        field public static final int CONSTANT = 1; // 0x1
                        field public static final int INHERITED = 0; // 0x0
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            Foo() { throw new RuntimeException("Stub!"); }
                            public static final int CONSTANT = 1; // 0x1
                            public static final int INHERITED = 0; // 0x0
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test deprecated is written out for deprecated field inherited from hidden class`() {
        // Makes sure that deprecated status is copied when the inherited field is explicitly
        // deprecated.
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            interface Constants {
                                /** @deprecated */
                                @Deprecated
                                int INHERITED = 0;
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;

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
                      public class Foo {
                        field public static final int CONSTANT = 1; // 0x1
                        field @Deprecated public static final int INHERITED = 0; // 0x0
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            Foo() { throw new RuntimeException("Stub!"); }
                            public static final int CONSTANT = 1; // 0x1
                            /** @deprecated */
                            @Deprecated public static final int INHERITED = 0; // 0x0
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test deprecated is written out for field inherited into deprecated class from hidden class`() {
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
                        field @Deprecated public static final int INHERITED = 0; // 0x0
                      }
                    }
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            /** @deprecated */
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            @Deprecated
                            public class Foo {
                            @Deprecated
                            Foo() { throw new RuntimeException("Stub!"); }
                            @Deprecated public static final int CONSTANT = 1; // 0x1
                            @Deprecated public static final int INHERITED = 0; // 0x0
                            }
                        """
                    )
                ),
        )
    }

    @Test
    fun `Test deprecated not written out for contents of a removed class`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            /**
                             * @deprecated
                             * @removed
                             */
                            @Deprecated
                            public class Foo {
                                public Foo() {}
                                public static final int CONSTANT = 1;
                                public void method(int p1) {}
                                @Deprecated public void deprecatedMethod() {}
                                public static class Nested {
                                    public Nested() {}
                                    public static final int NESTED_CONSTANT = 1;
                                    public void nestedMethod(int p1) {}
                                    @Deprecated public void deprecatedNestedMethod() {}
                                }
                            }
                        """
                    ),
                ),
            removedApi =
                """
                    // Signature format: 5.0
                    package test.pkg {
                      @Deprecated public class Foo {
                        ctor @Deprecated public Foo();
                        method @Deprecated public void deprecatedMethod();
                        method @Deprecated public void method(int);
                        field @Deprecated public static final int CONSTANT = 1; // 0x1
                      }
                      @Deprecated public static class Foo.Nested {
                        ctor @Deprecated public Foo.Nested();
                        method @Deprecated public void deprecatedNestedMethod();
                        method @Deprecated public void nestedMethod(int);
                        field @Deprecated public static final int NESTED_CONSTANT = 1; // 0x1
                      }
                    }
                """,
            api = """
                    // Signature format: 5.0
                """,
            stubPaths = emptyArray(),
        )
    }
}
