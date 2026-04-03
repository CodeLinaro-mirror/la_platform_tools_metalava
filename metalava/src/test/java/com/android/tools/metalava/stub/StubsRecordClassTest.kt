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

package com.android.tools.metalava.stub

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.text.FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.FORMAT_V6_WITH_JAVA_RECORD_CLASSES
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class StubsRecordClassTest : AbstractStubsTest() {
    private fun checkSimpleRecordStubs(
        format: FileFormat,
        checkCompilation: Boolean,
        expectedStubs: TestFile,
    ) {
        checkStubs(
            signatureSources =
                arrayOf(
                    """
                        // Signature format: 6.0
                        // - style=java
                        package test.pkg {
                          public record Test {
                            record_component #0 a: int;
                            record_component #1 b: String;
                            ctor public Test(int, String);
                            method public int a();
                            method public String b();
                          }
                        }
                    """,
                ),
            format = format,
            // TODO(b/482390286): Will not compile as it does not declare its components correctly.
            checkCompilation = checkCompilation,
            stubFiles = arrayOf(expectedStubs)
        )
    }

    @Test
    fun `Test simple record, java-record-classes=yes`() {
        checkSimpleRecordStubs(
            format = FORMAT_V6_WITH_JAVA_RECORD_CLASSES,
            // TODO(b/482390286): Will not compile as it does not declare its components correctly.
            checkCompilation = false,
            expectedStubs =
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public record Test(int a, java.lang.String b) {
                        public int a() { throw new RuntimeException("Stub!"); }
                        public java.lang.String b() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
        )
    }

    @Test
    fun `Test simple record, java-record-classes=no`() {
        checkSimpleRecordStubs(
            format = FORMAT_V6_WITHOUT_JAVA_RECORD_CLASSES,
            checkCompilation = true,
            expectedStubs =
                java(
                    """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class Test {
                        public Test(int arg1, java.lang.String arg2) { throw new RuntimeException("Stub!"); }
                        public int a() { throw new RuntimeException("Stub!"); }
                        public java.lang.String b() { throw new RuntimeException("Stub!"); }
                        }
                    """
                ),
        )
    }
}
