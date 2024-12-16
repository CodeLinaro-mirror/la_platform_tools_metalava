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

package com.android.tools.metalava.cli.compatibility

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.SignatureBasedApi
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.visitors.ApiType
import com.android.tools.metalava.testing.signature
import com.android.tools.metalava.testing.source
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

val COMPATIBILITY_CHECK_OPTIONS_HELP =
    """
Compatibility Checks:

  Options controlling which, if any, compatibility checks are performed against a previously released API.

  --check-compatibility:api:released <file>  Check compatibility of the previously released API.

                                             When multiple files are provided any files that are a delta on another file
                                             must come after the other file, e.g. if `system` is a delta on `public`
                                             then `public` must come first, then `system`. Or, in other words, they must
                                             be provided in order from the narrowest API to the widest API.
  --check-compatibility:removed:released <file>
                                             Check compatibility of the previously released but since removed APIs.

                                             When multiple files are provided any files that are a delta on another file
                                             must come after the other file, e.g. if `system` is a delta on `public`
                                             then `public` must come first, then `system`. Or, in other words, they must
                                             be provided in order from the narrowest API to the widest API.
  --api-compat-annotation <annotation>       Specify an annotation important for API compatibility.

                                             Adding/removing this annotation will be considered an incompatible change.
                                             The fully qualified name of the annotation should be passed.
  --error-message:compatibility:released <message>
                                             If set, this is output when errors are detected in
                                             --check-compatibility:api:released or
                                             --check-compatibility:removed:released.
  --baseline:compatibility:released <file>   An optional baseline file that contains a list of known compatibility
                                             issues which should be ignored. If this does not exist and
                                             --update-baseline:compatibility:released is not specified then it will be
                                             created and populated with all the known compatibility issues.
  --update-baseline:compatibility:released <file>
                                             An optional file into which a list of the latest compatibility issues found
                                             will be written. If --baseline:compatibility:released is specified then any
                                             issues listed in there will be copied into this file; that minimizes the
                                             amount of churn in the baseline file when updating by not removing legacy
                                             issues that have been fixed. If --delete-empty-baselines is specified and
                                             this baseline is empty then the file will be deleted.
    """
        .trimIndent()

class CompatibilityCheckOptionsTest :
    BaseOptionGroupTest<CompatibilityCheckOptions>(
        COMPATIBILITY_CHECK_OPTIONS_HELP,
    ) {

    override fun createOptions(): CompatibilityCheckOptions = CompatibilityCheckOptions()

    @Test
    fun `check compatibility api released`() {
        val file =
            signature("released.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(ARG_CHECK_COMPATIBILITY_API_RELEASED, file.path) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            previouslyReleasedApi = SignatureBasedApi.fromFiles(listOf(file)),
                            apiType = ApiType.PUBLIC_API,
                        ),
                    )
                )
        }
    }

    @Test
    fun `check compatibility api released multiple files`() {
        val file1 =
            signature("released1.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)
        val file2 =
            signature("released2.txt", "// Signature format: 2.0\n")
                .createFile(temporaryFolder.root)
        runTest(
            ARG_CHECK_COMPATIBILITY_API_RELEASED,
            file1.path,
            ARG_CHECK_COMPATIBILITY_API_RELEASED,
            file2.path,
        ) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            previouslyReleasedApi =
                                SignatureBasedApi.fromFiles(listOf(file1, file2)),
                            apiType = ApiType.PUBLIC_API,
                        ),
                    )
                )
        }
    }

    @Test
    fun `check compatibility removed api released`() {
        val file =
            signature("removed.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)
        runTest(ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED, file.path) {
            assertThat(options.compatibilityChecks)
                .isEqualTo(
                    listOf(
                        CompatibilityCheckOptions.CheckRequest(
                            previouslyReleasedApi =
                                SignatureBasedApi.fromFiles(
                                    listOf(file),
                                    apiVariantType = ApiVariantType.REMOVED,
                                ),
                            apiType = ApiType.REMOVED,
                        ),
                    )
                )
        }
    }

    /**
     * Create a fake jar file. It is ok that it is not actually a jar file as its contents are not
     * read.
     */
    private fun fakeJar() = source("some.jar", "PK...").createFile(temporaryFolder.root)

    @Test
    fun `check compatibility api released from jar`() {
        val jarFile = fakeJar()
        val exception =
            assertThrows(IllegalStateException::class.java) {
                runTest(ARG_CHECK_COMPATIBILITY_API_RELEASED, jarFile.path) {
                    options.compatibilityChecks
                }
            }

        assertThat(exception.message)
            .isEqualTo(
                "$ARG_CHECK_COMPATIBILITY_API_RELEASED: Can no longer check compatibility against jar files like $jarFile please use equivalent signature files"
            )
    }

    @Test
    fun `check compatibility api released mixture of signature and jar`() {
        val jarFile = fakeJar()
        val signatureFile =
            signature("removed.txt", "// Signature format: 2.0\n").createFile(temporaryFolder.root)

        val exception =
            assertThrows(IllegalStateException::class.java) {
                runTest(
                    ARG_CHECK_COMPATIBILITY_API_RELEASED,
                    jarFile.path,
                    ARG_CHECK_COMPATIBILITY_API_RELEASED,
                    signatureFile.path,
                ) {
                    options.compatibilityChecks
                }
            }

        assertThat(exception.message)
            .isEqualTo(
                "$ARG_CHECK_COMPATIBILITY_API_RELEASED: Can no longer check compatibility against jar files like $jarFile please use equivalent signature files"
            )
    }

    @Test
    fun `check compatibility api removed does not support jar file`() {
        val jarFile = fakeJar()

        val exception =
            assertThrows(IllegalStateException::class.java) {
                runTest(ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED, jarFile.path) {
                    options.compatibilityChecks
                }
            }
        assertThat(exception.message)
            .isEqualTo(
                "$ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED: Can no longer check compatibility against jar files like $jarFile please use equivalent signature files"
            )
    }

    @Test
    fun `api compat annotations multiple values`() {
        runTest(
            ARG_API_COMPAT_ANNOTATION,
            "com.example.MyAnnotation",
            ARG_API_COMPAT_ANNOTATION,
            "com.example.MyOtherAnnotation",
        ) {
            assertThat(options.apiCompatAnnotations)
                .containsExactly("com.example.MyAnnotation", "com.example.MyOtherAnnotation")
        }
    }
}
