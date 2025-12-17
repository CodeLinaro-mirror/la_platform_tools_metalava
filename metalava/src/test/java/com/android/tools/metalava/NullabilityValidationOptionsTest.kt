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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import org.junit.Assert.*

val NULLABILITY_VALIDATION_HELP =
    """
Nullability Validation:

  Options control nullability validation.

  --nullability-errors-non-fatal             Specifies that errors encountered during validation of nullability
                                             annotations should not be treated as errors. They will be written out to
                                             the file specified in --nullability-warnings-txt instead.
  --nullability-warnings-txt <file>          Specifies where to write warnings encountered during validation of
                                             nullability annotations. (Does not trigger validation by itself.)
  --validate-nullability-from-merged-stubs   Triggers validation of nullability annotations for any class where
                                             --merge-qualifier-annotations includes a Java stub file.
  --validate-nullability-from-list <file>    Triggers validation of nullability annotations for any class listed in the
                                             named file (one top-level class per line, # prefix for comment line).
    """
        .trimIndent()

class NullabilityValidationOptionsTest :
    BaseOptionGroupTest<NullabilityValidationOptions>(NULLABILITY_VALIDATION_HELP) {
    override fun createOptions() = NullabilityValidationOptions()
}
