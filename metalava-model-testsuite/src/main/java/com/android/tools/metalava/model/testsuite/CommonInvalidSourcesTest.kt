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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import org.junit.Test

/**
 * Tests for some invalid sources.
 *
 * This exercises the error handling and reporting of the different models which is always going to
 * be slightly different.
 */
class CommonInvalidSourcesTest : BaseModelTest() {
    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test unexpected interface`() {
        runSourceCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Foo extends Interface {}
                    """
                ),
                java(
                    """
                        package test.pkg;
                        public interface Interface {}
                    """
                ),
            ),
        ) {
            val expectedIssues =
                when (codebaseCreatorConfig.providerName) {
                    "turbine" ->
                        "MAIN_SRC/src/test/pkg/Foo.java:2:26: error: unexpected interface type: test.pkg.Interface [InvalidSyntax]"
                    else -> ""
                }
            assertAndRemoveReportedIssues(expectedIssues)
        }
    }
}
