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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.DriverTest
import org.junit.Test

class MultipleCompatibilityFilesTest : DriverTest() {

    private val previouslyReleasedPublicApi =
        """
            // Signature format: 2.0
            package test.pkg {
              public class Bar extends IllegalStateException {
                field public int field;
              }
              public class Foo {
                method public void foo() throws test.pkg.Bar;
              }
            }
        """

    private val previouslyReleasedSystemApiDelta =
        """
            // Signature format: 2.0
            package test.pkg {
              public class Baz extends test.pkg.Bar {
              }
              public class Foo {
                method public void foo() throws test.pkg.Baz;
              }
            }
        """

    /**
     * The current and complete public api which will be tested for compatibility against the public
     * API.
     */
    private val currentCompletePublicApi =
        """
            // Signature format: 2.0
            package test.pkg {
              public class Bar extends IllegalStateException {
                field public volatile int field;
              }
              public class Foo {
                method public void foo() throws test.pkg.Bar;
              }
            }
        """

    /**
     * The current and complete system api which will be tested for compatibility against some
     * combination of the above previously released APIs.
     */
    private val currentCompleteSystemApi =
        """
            package test.pkg {
              public class Bar extends IllegalStateException {
                field public volatile int field;
              }
              public class Baz extends test.pkg.Bar {
              }
              public class Foo {
                method public void foo() throws test.pkg.Baz;
              }
            }
        """

    @Test
    fun `Test current public vs released public only`() {
        check(
            checkCompatibilityApiReleasedList = listOf(previouslyReleasedPublicApi),
            signatureSource = currentCompletePublicApi,
            // This reports a real issue that exists in the public API.
            expectedIssues =
                """
                    load-api.txt:4: error: Field test.pkg.Bar.field has changed 'volatile' qualifier [ChangedVolatile]
                """,
        )
    }

    @Test
    fun `Test current system vs released system only`() {
        check(
            checkCompatibilityApiReleasedList = listOf(previouslyReleasedSystemApiDelta),
            signatureSource = currentCompleteSystemApi,
            // This does not report the `ChangedVolatile` issue with test.pkg.Bar.field because it
            // is not given `previouslyReleasedPublicApi` and so does not know that the field was
            // previously declared without the `volatile` keyword.
        )
    }

    @Test
    fun `Test current system vs multiple released compatibility files`() {
        check(
            checkCompatibilityApiReleasedList =
                listOf(previouslyReleasedPublicApi, previouslyReleasedSystemApiDelta),
            signatureSource = currentCompleteSystemApi,
            // Although there is an issue in the public API that is not reported here because it
            // only reports issues found when comparing against `previouslyReleasedSystemApiDelta`.
            // That is to avoid reporting an issue in one API surface against any API surface that
            // extends it.
        )
    }

    @Test
    fun `Test current system vs multiple released compatibility files (invalid first)`() {
        check(
            checkCompatibilityApiReleasedList =
                listOf("Invalid Signature File", previouslyReleasedSystemApiDelta),
            signatureSource = currentCompleteSystemApi,
            expectedFail =
                """
                    Aborting: Unable to parse signature file: TESTROOT/project/released-api.txt:2: expected package got Invalid
                """
        )
    }

    @Test
    fun `Test current public vs multiple removed compatibility files (invalid first)`() {
        check(
            checkCompatibilityRemovedApiReleasedList =
                listOf("Invalid Signature File", previouslyReleasedPublicApi),
            signatureSource = currentCompletePublicApi,
            // This reports a real issue that exists in the public API.
            expectedFail =
                """
                    Aborting: Unable to parse signature file: TESTROOT/project/removed-released-api.txt:2: expected package got Invalid
                """,
        )
    }
}
