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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.testing.java
import org.junit.Test

class InvalidParamOrReturnTest : DriverTest() {

    @Test
    fun `Test invalid param or return formatting`() {
        check(
            extraArguments = warningIssues(Issues.INVALID_PARAM_OR_RETURN),
            expectedIssues =
                """
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p1' of method 'methodBad' must not end with sentence-terminal punctuation: Upper case start. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p1' of method 'methodBad' must start with a lowercase letter: Upper case start. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p2' of method 'methodBad' must be a single sentence fragment: lowercase start. But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p2' of method 'methodBad' must not end with sentence-terminal punctuation: lowercase start. But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p3' of method 'methodBad' must not end with sentence-terminal punctuation: description ending with e.g. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p4' of method 'methodBad' must avoid parentheses and prefer clean rephrasings: description with plain-text parentheses (such as this) [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p5' of method 'methodBad' must be a single sentence fragment: lowercase start! But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p5' of method 'methodBad' must not end with sentence-terminal punctuation: lowercase start! But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p6' of method 'methodBad' must be a single sentence fragment: lowercase start? But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p6' of method 'methodBad' must not end with sentence-terminal punctuation: lowercase start? But multiple sentences. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p7' of method 'methodBad' must not end with sentence-terminal punctuation: ends with exclamation! [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p8' of method 'methodBad' must not end with sentence-terminal punctuation: ends with question? [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Parameter 'p9' of method 'methodBad' must start with a lowercase letter: 123 Uppercase start [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Return value of method 'methodBad' must be a single sentence fragment: Upper case start. And more. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Return value of method 'methodBad' must not end with sentence-terminal punctuation: Upper case start. And more. [InvalidParamOrReturn]
                src/android/pkg/JavadocTest.java:32: warning: Return value of method 'methodBad' must start with a lowercase letter: Upper case start. And more. [InvalidParamOrReturn]
                """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class JavadocTest {
                        /**
                         * Correct Javadoc.
                         *
                         * @param p1 lowercase start, e.g. a number
                         * @param p2 {@code true} on success, {@code false} otherwise
                         * @param p3 description ending with etc.
                         * @param p4 tag containing inline code parentheses like {@code bar()} or {@link #equals(Object)}
                         * @param p5 -
                         * @param p6 123 lowercase start
                         * @param p7
                         * @return {@code true} on success, {@code false} otherwise
                         */
                        public boolean methodOk(int p1, boolean p2, int p3, Object p4, String p5, int p6, int p7) { return true; }

                        /**
                         * Bad Javadoc.
                         *
                         * @param p1 Upper case start.
                         * @param p2 lowercase start. But multiple sentences.
                         * @param p3 description ending with e.g.
                         * @param p4 description with plain-text parentheses (such as this)
                         * @param p5 lowercase start! But multiple sentences.
                         * @param p6 lowercase start? But multiple sentences.
                         * @param p7 ends with exclamation!
                         * @param p8 ends with question?
                         * @param p9 123 Uppercase start
                         * @return Upper case start. And more.
                         */
                        public boolean methodBad(int p1, boolean p2, int p3, Object p4, int p5, int p6, int p7, int p8, int p9) { return true; }
                    }
                    """
                    )
                )
        )
    }

    @Test
    fun `Test invalid param or return formatting is hidden by default`() {
        check(
            expectedIssues = "",
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package android.pkg;

                    public class JavadocTest {
                        /**
                         * @param p1 Upper case start.
                         * @return Upper case start. And more.
                         */
                        public boolean methodBad(int p1) { return true; }
                    }
                    """
                    )
                )
        )
    }
}
