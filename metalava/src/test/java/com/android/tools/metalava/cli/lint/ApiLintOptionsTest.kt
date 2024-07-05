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

package com.android.tools.metalava.cli.lint

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.testing.signature
import com.google.common.truth.Truth.assertThat
import org.junit.Test

val API_LINT_OPTIONS_HELP =
    """
Api Lint:

  Options controlling API linting.

  --api-lint                                 Check API for Android API best practices.
  --api-lint-previous-api <file>             An API signature file that defines, albeit maybe only partially, a
                                             previously released API.

                                             If the API surface extends another API surface then this must include all
                                             the corresponding signature files in order from the outermost API surface
                                             that does not extend any API surface to the innermost one that represents
                                             the API surface being generated.

                                             API Lint issues found in the previously released API will be ignored.
  --error-message:api-lint <message>         If set, this is output when errors are detected in --api-lint.
  --baseline:api-lint <file>                 An optional baseline file that contains a list of known API lint issues
                                             which should be ignored. If this does not exist and
                                             --update-baseline:api-lint is not specified then it will be created and
                                             populated with all the known API lint issues.
  --update-baseline:api-lint <file>          An optional file into which a list of the latest API lint issues found will
                                             be written. If --baseline:api-lint is specified then any issues listed in
                                             there will be copied into this file; that minimizes the amount of churn in
                                             the baseline file when updating by not removing legacy issues that have
                                             been fixed. If --delete-empty-baselines is specified and this baseline is
                                             empty then the file will be deleted.
    """
        .trimIndent()

class ApiLintOptionsTest :
    BaseOptionGroupTest<ApiLintOptions>(
        API_LINT_OPTIONS_HELP,
    ) {

    override fun createOptions(): ApiLintOptions = ApiLintOptions()

    @Test
    fun `api lint previous api`() {
        val file =
            signature("released.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(ARG_API_LINT_PREVIOUS_API, file.path) {
            assertThat(options.apiLintPreviousApis).isEqualTo(listOf(file))
        }
    }
}
