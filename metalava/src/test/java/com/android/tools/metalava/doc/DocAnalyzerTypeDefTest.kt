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
import com.android.tools.metalava.intDefAnnotationSource
import com.android.tools.metalava.intRangeAnnotationSource
import com.android.tools.metalava.testing.java
import org.junit.Test

/** Tests for the [DocAnalyzer] which check the handling of ranges in the docs */
class DocAnalyzerTypeDefTest : DriverTest() {
    @Test
    fun `Test IntDef flag`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.IntDef;

                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;

                            @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                            public class TypedefTest {
                                public static final int STYLE_NORMAL = 0;
                                public static final int STYLE_NO_TITLE = 1;
                                public static final int STYLE_NO_FRAME = 2;
                                public static final int STYLE_NO_INPUT = 3;
                                public static final int STYLE_UNRELATED = 3;

                                @IntDef(value = {STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT, 2, 3 + 1},
                                flag=true)
                                @Retention(RetentionPolicy.SOURCE)
                                private @interface DialogFlags {}

                                public void setFlags(Object first, @DialogFlags int flags) {
                                }
                            }
                        """
                    ),
                    intDefAnnotationSource
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class TypedefTest {
                            public TypedefTest() { throw new RuntimeException("Stub!"); }
                            /**
                             * @param flags Value is either <code>0</code> or a combination of the following:
                             * <ul>
                             *   <li>{@link #STYLE_NORMAL}</li>
                             *   <li>{@link #STYLE_NO_TITLE}</li>
                             *   <li>{@link #STYLE_NO_FRAME}</li>
                             *   <li>{@link #STYLE_NO_INPUT}</li>
                             *   <li>2</li>
                             *   <li>4</li>
                             * <ul>
                             */
                            public void setFlags(java.lang.Object first, int flags) { throw new RuntimeException("Stub!"); }
                            public static final int STYLE_NORMAL = 0;
                            public static final int STYLE_NO_FRAME = 2;
                            public static final int STYLE_NO_INPUT = 3;
                            public static final int STYLE_NO_TITLE = 1;
                            public static final int STYLE_UNRELATED = 3;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test IntDef enum`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            import android.annotation.IntDef;

                            import java.lang.annotation.Retention;
                            import java.lang.annotation.RetentionPolicy;

                            @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                            public class TypedefTest {
                                public static final int STYLE_NORMAL = 0;
                                public static final int STYLE_NO_TITLE = 1;
                                public static final int STYLE_NO_FRAME = 2;
                                public static final int STYLE_NO_INPUT = 3;
                                public static final int STYLE_UNRELATED = 3;

                                @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                                @Retention(RetentionPolicy.SOURCE)
                                private @interface DialogStyle {}

                                public void setStyle(@DialogStyle int style, int theme) {
                                }
                            }
                        """
                    ),
                    intDefAnnotationSource,
                ),
            checkCompilation = true,
            docStubs = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class TypedefTest {
                            public TypedefTest() { throw new RuntimeException("Stub!"); }
                            /**
                             * @param style Value is one of the following:
                             * <ul>
                             *   <li>{@link #STYLE_NORMAL}</li>
                             *   <li>{@link #STYLE_NO_TITLE}</li>
                             *   <li>{@link #STYLE_NO_FRAME}</li>
                             *   <li>{@link #STYLE_NO_INPUT}</li>
                             * <ul>
                             */
                            public void setStyle(int style, int theme) { throw new RuntimeException("Stub!"); }
                            public static final int STYLE_NORMAL = 0;
                            public static final int STYLE_NO_FRAME = 2;
                            public static final int STYLE_NO_INPUT = 3;
                            public static final int STYLE_NO_TITLE = 1;
                            public static final int STYLE_UNRELATED = 3;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test IntDef hidden constant`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.annotation.IntDef;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                    public class TypedefTest {
                        @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME, STYLE_NO_INPUT})
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface DialogStyle {}

                        public static final int STYLE_NORMAL = 0;
                        public static final int STYLE_NO_TITLE = 1;
                        public static final int STYLE_NO_FRAME = 2;
                        /** @hide */
                        public static final int STYLE_NO_INPUT = 3;

                        public void setStyle(@DialogStyle int style, int theme) {
                        }
                    }
                    """
                    ),
                    intDefAnnotationSource
                ),
            docStubs = true,
            checkCompilation = true,
            expectedIssues =
                """
                    src/test/pkg/TypedefTest.java:20: error: Typedef references constant which isn't part of the API, skipping in documentation: test.pkg.TypedefTest#STYLE_NO_INPUT [HiddenTypedefConstant]
                """,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class TypedefTest {
                            public TypedefTest() { throw new RuntimeException("Stub!"); }
                            /**
                             * @param style Value is one of the following:
                             * <ul>
                             *   <li>{@link #STYLE_NORMAL}</li>
                             *   <li>{@link #STYLE_NO_TITLE}</li>
                             *   <li>{@link #STYLE_NO_FRAME}</li>
                             * <ul>
                             */
                            public void setStyle(int style, int theme) { throw new RuntimeException("Stub!"); }
                            public static final int STYLE_NORMAL = 0;
                            public static final int STYLE_NO_FRAME = 2;
                            public static final int STYLE_NO_TITLE = 1;
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test IntDef with IntRange`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;

                    import android.annotation.IntDef;
                    import android.annotation.IntRange;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
                    public class TypedefTest {
                        @IntDef({STYLE_NORMAL, STYLE_NO_TITLE, STYLE_NO_FRAME})
                        @IntRange(from = 20)
                        @Retention(RetentionPolicy.SOURCE)
                        private @interface DialogStyle {}

                        public static final int STYLE_NORMAL = 0;
                        public static final int STYLE_NO_TITLE = 1;
                        public static final int STYLE_NO_FRAME = 2;

                        public void setStyle(@DialogStyle int style, int theme) {
                        }
                    }
                    """
                    ),
                    intRangeAnnotationSource,
                    intDefAnnotationSource
                ),
            docStubs = true,
            checkCompilation = true,
            stubFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            @SuppressWarnings({"unchecked", "deprecation", "all"})
                            public class TypedefTest {
                            public TypedefTest() { throw new RuntimeException("Stub!"); }
                            /**
                             * @param style Value is one of the following:
                             * <ul>
                             *   <li>{@link #STYLE_NORMAL}</li>
                             *   <li>{@link #STYLE_NO_TITLE}</li>
                             *   <li>{@link #STYLE_NO_FRAME}</li>
                             * <ul>.
                             * <br>
                             * Value is 20 or greater
                             */
                            public void setStyle(int style, int theme) { throw new RuntimeException("Stub!"); }
                            public static final int STYLE_NORMAL = 0;
                            public static final int STYLE_NO_FRAME = 2;
                            public static final int STYLE_NO_TITLE = 1;
                            }
                        """
                    ),
                ),
        )
    }
}
