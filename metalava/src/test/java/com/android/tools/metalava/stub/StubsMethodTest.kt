/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsMethodTest : AbstractStubsTest() {
    @Test
    fun `Test final and non-final methods inherited into final class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            class Hidden {
                                public final void finalMethod() {}
                                public void nonFinalMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public final class Foo extends Hidden {
                            }
                        """
                    ),
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public final class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public void finalMethod() { throw new RuntimeException("Stub!"); }
                            public void nonFinalMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test final and non-final methods inherited into non-final class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            class Hidden {
                                public final void finalMethod() {}
                                public void nonFinalMethod() {}
                            }
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Foo extends Hidden {
                            }
                        """
                    ),
                ),
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class Foo {
                            public Foo() { throw new RuntimeException("Stub!"); }
                            public final void finalMethod() { throw new RuntimeException("Stub!"); }
                            public void nonFinalMethod() { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }
}
