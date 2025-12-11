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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

val STUB_GENERATION_OPTIONS_HELP =
    """
Stub Generation:

  Options controlling the generation of stub files.

  --stubs <dir>                              Base directory to output the generated stub source files for the API, if
                                             specified.

                                             At most one of this and --doc-stubs can be provided.
  --doc-stubs <dir>                          Generate documentation stub source files for the API. Documentation stub
                                             files are similar to regular stub files, but there are some differences.
                                             For example, in the stub files, we'll use special annotations like
                                             @RecentlyNonNull instead of @NonNull to indicate that an element is
                                             recently marked as non null, whereas in the documentation stubs we'll just
                                             list this as @NonNull. Another difference is that @doconly elements are
                                             included in documentation stubs, but not regular stubs, etc.

                                             At most one of this and --stubs can be provided.
  --include-annotations / --exclude-all-annotations
                                             Include/exclude annotations such as @Nullable in/from the stub files.
                                             (default: exclude)
  --exclude-documentation-from-stubs         Exclude element documentation (javadoc and kdoc) from the generated stubs.
                                             Copyright notices are not affected by this, they are always included.
                                             Documentation stubs (--doc-stubs) are not affected either. (default:
                                             exclude)
  --enhance-documentation                    Enhance documentation in various ways, for example auto-generating
                                             documentation based on source annotations present in the code. This is
                                             implied by --doc-stubs. (default: do not enhance unless --doc-stubs is
                                             specified)
  --migrate-nullness <api-file>              Compare nullness information with the previous stable API and mark newly
                                             annotated APIs as recently added. That replaces the annotations with a
                                             special form of annotation that will cause the Kotlin compiler to treat
                                             nullability issues as warnings not errors. The intent is that this will
                                             make it possible to fix existing app code incrementally after a release
                                             rather than having to fix it all at once.
  --force-convert-to-warning-nullability-annotations <package1:-package2:...>
                                             On every API declared in a class referenced by the given filter, makes
                                             nullability issues appear to callers as warnings rather than errors by
                                             replacing @Nullable/@NonNull in these APIs with
                                             @RecentlyNullable/@RecentlyNonNull.

                                             See `metalava help package-filters` for more information.
  --apply-api-levels <api-versions.xml>      Reads an XML file containing API level descriptions and merges the
                                             information into the documentation.
    """
        .trimIndent()

class StubGenerationOptionsTest :
    BaseOptionGroupTest<StubGenerationOptions>(
        STUB_GENERATION_OPTIONS_HELP,
    ) {
    override fun createOptions(): StubGenerationOptions = StubGenerationOptions()

    @Test
    fun `Test --stubs and --doc-stubs are mutually exclusive`() {
        val nonExistentDir = temporaryFolder.root.resolve("stubs-dir")
        runTest(ARG_STUBS, nonExistentDir.path, ARG_DOC_STUBS, nonExistentDir.path) {
            val exception =
                assertThrows(MetalavaCliException::class.java) { options.generatorConfig() }

            assertEquals(
                "Cannot use --stubs and --doc-stubs, they are mutually exclusive",
                exception.message
            )
        }
    }

    @Test
    fun `Test --apply-api-levels for non-existent file works correctly`() {
        val nonExistentFile = temporaryFolder.root.resolve("non-existent/api-versions.xml")
        runTest(ARG_APPLY_API_LEVELS, nonExistentFile.path) {
            // Make sure that no errors are reported when parsing the options.
            assertEquals("", stdout)
            assertEquals("", stderr)
            val exception =
                assertThrows(MetalavaCliException::class.java) { options.generatorConfig() }

            assertEquals(
                "--apply-api-levels file 'TESTROOT/non-existent/api-versions.xml' does not exist or is not readable",
                cleanupString(exception.message!!)
            )
        }
    }
}
