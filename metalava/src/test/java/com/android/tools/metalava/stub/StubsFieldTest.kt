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

class StubsFieldTest : AbstractStubsTest() {
    /**
     * Test class that is used to test the behavior when a static final field is initialized with an
     * unknown value.
     */
    private val hiddenClass =
        java(
            """
                package test.pkg;
                class Hidden {
                    static boolean booleanMethod() { return true; }
                    static byte byteMethod() { return 45; }
                    static char charMethod() { return 'A'; }
                    static float floatMethod() { return 0; }
                    static String stringMethod() { return "unknown"; }
                    static Runnable runnableMethod() { return () -> {}; }
                }
            """
        )

    @Test
    fun `Test field with unknown value in annotation`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    hiddenClass,
                    java(
                        """
                            package test.pkg;
                            public @interface Foo {
                                boolean BOOLEAN_FIELD = Hidden.booleanMethod();
                                byte BYTE_FIELD = Hidden.byteMethod();
                                char CHAR_FIELD = Hidden.charMethod();
                                double DOUBLE_FIELD = Hidden.floatMethod();
                                float FLOAT_FIELD = Hidden.floatMethod();
                                int INT_FIELD = Hidden.byteMethod();
                                long LONG_FIELD = Hidden.byteMethod();
                                Runnable RUNNABLE_FIELD = Hidden.runnableMethod();
                                short SHORT_FIELD = Hidden.byteMethod();
                                String STRING_FIELD = Hidden.stringMethod();
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
                            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
                            public @interface Foo {
                            public static final boolean BOOLEAN_FIELD = java.lang.Boolean.parseBoolean("false"); // Not compile-time constant
                            public static final byte BYTE_FIELD = java.lang.Byte.parseByte("0"); // Not compile-time constant
                            public static final char CHAR_FIELD = "A".charAt(0); // Not compile-time constant
                            public static final double DOUBLE_FIELD = java.lang.Double.parseDouble("0"); // Not compile-time constant
                            public static final float FLOAT_FIELD = java.lang.Float.parseFloat("0"); // Not compile-time constant
                            public static final int INT_FIELD = java.lang.Integer.parseInt("0"); // Not compile-time constant
                            public static final long LONG_FIELD = java.lang.Long.parseLong("0"); // Not compile-time constant
                            public static final java.lang.Runnable RUNNABLE_FIELD = null; // Not compile-time constant
                            public static final short SHORT_FIELD = java.lang.Short.parseShort("0"); // Not compile-time constant
                            public static final java.lang.String STRING_FIELD = java.lang.String.valueOf(0); // Not compile-time constant
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test field with unknown value in class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    hiddenClass,
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public static final boolean BOOLEAN_FIELD = Hidden.booleanMethod();
                                public static final byte BYTE_FIELD = Hidden.byteMethod();
                                public static final char CHAR_FIELD = Hidden.charMethod();
                                public static final double DOUBLE_FIELD = Hidden.floatMethod();
                                public static final float FLOAT_FIELD = Hidden.floatMethod();
                                public static final int INT_FIELD = Hidden.byteMethod();
                                public static final long LONG_FIELD = Hidden.byteMethod();
                                public static final Runnable RUNNABLE_FIELD = Hidden.runnableMethod();
                                public static final short SHORT_FIELD = Hidden.byteMethod();
                                public static final String STRING_FIELD = Hidden.stringMethod();
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
                            public static final boolean BOOLEAN_FIELD;
                            static { BOOLEAN_FIELD = false; }
                            public static final byte BYTE_FIELD;
                            static { BYTE_FIELD = 0; }
                            public static final char CHAR_FIELD;
                            static { CHAR_FIELD = 0; }
                            public static final double DOUBLE_FIELD;
                            static { DOUBLE_FIELD = 0; }
                            public static final float FLOAT_FIELD;
                            static { FLOAT_FIELD = 0; }
                            public static final int INT_FIELD;
                            static { INT_FIELD = 0; }
                            public static final long LONG_FIELD;
                            static { LONG_FIELD = 0; }
                            public static final java.lang.Runnable RUNNABLE_FIELD;
                            static { RUNNABLE_FIELD = null; }
                            public static final short SHORT_FIELD;
                            static { SHORT_FIELD = 0; }
                            public static final java.lang.String STRING_FIELD;
                            static { STRING_FIELD = null; }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test field with unknown value in enum`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    hiddenClass,
                    java(
                        """
                            package test.pkg;
                            public enum Foo {
                                ;
                                public static final boolean BOOLEAN_FIELD = Hidden.booleanMethod();
                                public static final byte BYTE_FIELD = Hidden.byteMethod();
                                public static final char CHAR_FIELD = Hidden.charMethod();
                                public static final double DOUBLE_FIELD = Hidden.floatMethod();
                                public static final float FLOAT_FIELD = Hidden.floatMethod();
                                public static final int INT_FIELD = Hidden.byteMethod();
                                public static final long LONG_FIELD = Hidden.byteMethod();
                                public static final Runnable RUNNABLE_FIELD = Hidden.runnableMethod();
                                public static final short SHORT_FIELD = Hidden.byteMethod();
                                public static final String STRING_FIELD = Hidden.stringMethod();
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
                            public enum Foo {
                            ;
                            public static final boolean BOOLEAN_FIELD;
                            static { BOOLEAN_FIELD = false; }
                            public static final byte BYTE_FIELD;
                            static { BYTE_FIELD = 0; }
                            public static final char CHAR_FIELD;
                            static { CHAR_FIELD = 0; }
                            public static final double DOUBLE_FIELD;
                            static { DOUBLE_FIELD = 0; }
                            public static final float FLOAT_FIELD;
                            static { FLOAT_FIELD = 0; }
                            public static final int INT_FIELD;
                            static { INT_FIELD = 0; }
                            public static final long LONG_FIELD;
                            static { LONG_FIELD = 0; }
                            public static final java.lang.Runnable RUNNABLE_FIELD;
                            static { RUNNABLE_FIELD = null; }
                            public static final short SHORT_FIELD;
                            static { SHORT_FIELD = 0; }
                            public static final java.lang.String STRING_FIELD;
                            static { STRING_FIELD = null; }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test field with unknown value in interface`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    hiddenClass,
                    java(
                        """
                            package test.pkg;
                            public interface Foo {
                                boolean BOOLEAN_FIELD = Hidden.booleanMethod();
                                byte BYTE_FIELD = Hidden.byteMethod();
                                char CHAR_FIELD = Hidden.charMethod();
                                double DOUBLE_FIELD = Hidden.floatMethod();
                                float FLOAT_FIELD = Hidden.floatMethod();
                                int INT_FIELD = Hidden.byteMethod();
                                long LONG_FIELD = Hidden.byteMethod();
                                Runnable RUNNABLE_FIELD = Hidden.runnableMethod();
                                short SHORT_FIELD = Hidden.byteMethod();
                                String STRING_FIELD = Hidden.stringMethod();
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
                            public interface Foo {
                            public static final boolean BOOLEAN_FIELD = java.lang.Boolean.parseBoolean("false"); // Not compile-time constant
                            public static final byte BYTE_FIELD = java.lang.Byte.parseByte("0"); // Not compile-time constant
                            public static final char CHAR_FIELD = "A".charAt(0); // Not compile-time constant
                            public static final double DOUBLE_FIELD = java.lang.Double.parseDouble("0"); // Not compile-time constant
                            public static final float FLOAT_FIELD = java.lang.Float.parseFloat("0"); // Not compile-time constant
                            public static final int INT_FIELD = java.lang.Integer.parseInt("0"); // Not compile-time constant
                            public static final long LONG_FIELD = java.lang.Long.parseLong("0"); // Not compile-time constant
                            public static final java.lang.Runnable RUNNABLE_FIELD = null; // Not compile-time constant
                            public static final short SHORT_FIELD = java.lang.Short.parseShort("0"); // Not compile-time constant
                            public static final java.lang.String STRING_FIELD = java.lang.String.valueOf(0); // Not compile-time constant
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test field with non-constant value in class`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public class Foo {
                                public static final Class<?> FIELD = Integer.class;
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
                            public static final java.lang.Class<?> FIELD;
                            static { FIELD = null; }
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test field with non-constant value in interface`() {
        checkStubs(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            public interface Foo {
                                Class<?> FIELD = Integer.class;
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
                            public interface Foo {
                            public static final java.lang.Class<?> FIELD = null; // Not compile-time constant
                            }
                        """
                    ),
                ),
        )
    }
}
