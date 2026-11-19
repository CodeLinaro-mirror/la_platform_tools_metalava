/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.intRangeAnnotationSource
import com.android.tools.metalava.testing.KnownSourceFiles.floatRangeAnnotationSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [DocAnalyzer] which check the handling of ranges in the docs */
class DocAnalyzerRangeTest : DriverTest() {
    @Test
    fun `Document ranges`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.Manifest;
                    import android.annotation.IntRange;

                    public class RangeTest {
                        @IntRange(from = 10)
                        public int test1(@IntRange(from = 20) int range2) { return 15; }

                        @IntRange(from = 10, to = 20)
                        public int test2() { return 15; }

                        @IntRange(to = 100)
                        public int test3() { return 50; }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            docStubs = true,
            checkCompilation = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * @param range2 Value is 20 or greater
                     * @return Value is 10 or greater
                     */
                    public int test1(int range2) { throw new RuntimeException("Stub!"); }
                    /** @return Value is between 10 and 20 inclusive */
                    public int test2() { throw new RuntimeException("Stub!"); }
                    /** @return Value is 100 or less */
                    public int test3() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter when no doc exists`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /** @param parameter2 Value is 10 or greater */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter when doc exists but no param doc`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     *
                     * @param parameter2 Value is 10 or greater
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new parameter, sorted correctly between existing ones`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @param parameter1 docs for parameter1
                        * @param parameter3 docs for parameter2
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     *
                     * @param parameter1 docs for parameter1
                     * @param parameter2 Value is 10 or greater
                     * @param parameter3 docs for parameter2
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing parameter`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @param parameter1 docs for parameter1
                        * @param parameter2 docs for parameter2
                        * @param parameter3 docs for parameter2
                        * @return return value documented here
                        */
                        public int test1(int parameter1, @IntRange(from = 10) int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     *
                     * @param parameter1 docs for parameter1
                     * @param parameter2 docs for parameter2.
                     * <br>
                     * Value is 10 or greater
                     * @param parameter3 docs for parameter2
                     * @return return value documented here
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add new return value`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        @IntRange(from = 10)
                        public int test1(int parameter1, int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /** @return Value is 10 or greater */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Add to existing return value - ensuring it appears last`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    import android.annotation.IntRange;
                    public class RangeTest {
                        /**
                        * This is the existing documentation.
                        * @return return value documented here
                        */
                        @IntRange(from = 10)
                        public int test1(int parameter1, int parameter2, int parameter3) { }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class RangeTest {
                    public RangeTest() { throw new RuntimeException("Stub!"); }
                    /**
                     * This is the existing documentation.
                     *
                     * @return return value documented here.
                     * <br>
                     * Value is 10 or greater
                     */
                    public int test1(int parameter1, int parameter2, int parameter3) { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Merge API levels`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;

                    public class Toolbar {
                        /**
                        * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                        * @return blah blah blah
                        */
                        public int getCurrentContentInsetEnd() {
                            return 0;
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
                            <method name="&lt;init>(Landroid/content/Context;)V"/>
                            <method name="collapseActionView()V"/>
                            <method name="getContentInsetStartWithNavigation()I" since="24"/>
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                            <method name="getCurrentContentInsetLeft()I" since="24"/>
                            <method name="getCurrentContentInsetRight()I" since="24"/>
                            <method name="getCurrentContentInsetStart()I" since="24"/>
                        </class>
                    </api>
                    """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;
                    /** @apiSince 21 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Toolbar {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    /**
                     * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                     *
                     * @return blah blah blah
                     * @apiSince 24
                     */
                    public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Trailing comment close`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;

                    public class Toolbar {
                        /**
                        * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here. */
                        public int getCurrentContentInsetEnd() {
                            return 0;
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            applyApiLevelsXml =
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <api version="2">
                        <class name="android/widget/Toolbar" since="21">
                            <method name="getCurrentContentInsetEnd()I" since="24"/>
                        </class>
                    </api>
                    """,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                    package android.widget;
                    /** @apiSince 21 */
                    @SuppressWarnings({"unchecked", "deprecation", "all"})
                    public class Toolbar {
                    public Toolbar() { throw new RuntimeException("Stub!"); }
                    /**
                     * Existing documentation for {@linkplain #getCurrentContentInsetEnd()} here.
                     * @apiSince 24
                     */
                    public int getCurrentContentInsetEnd() { throw new RuntimeException("Stub!"); }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test different values`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import android.annotation.FloatRange;
                            import android.annotation.IntRange;
                            public class RangeTest {
                                /**
                                 * Blah.
                                 */
                                public int test1(
                                    @IntRange(from = Integer.MIN_VALUE) int i1,
                                    @IntRange(from = Integer.MAX_VALUE - 1) int i2,
                                    @IntRange(from = 1L << 40) int i3,
                                    @FloatRange(from = 10.5f) int f1,
                                    @FloatRange(from = 10.0E112) int f2) { }
                            }
                        """
                    ),
                    floatRangeAnnotationSource,
                    intRangeAnnotationSource,
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class RangeTest {
                            public RangeTest() { throw new RuntimeException("Stub!"); }
                            /**
                             * Blah.
                             *
                             * @param i1 Value is {@link java.lang.Integer#MIN_VALUE} or greater
                             * @param i2 Value is 2147483646 or greater
                             * @param i3 Value is 1099511627776L or greater
                             * @param f1 Value is 10.5f or greater
                             * @param f2 Value is 1.0E113 or greater
                             */
                            public int test1(int i1, int i2, int i3, int f1, int f2) { throw new RuntimeException("Stub!"); }
                            }
                        """
                    ),
                ),
        )
    }
}
