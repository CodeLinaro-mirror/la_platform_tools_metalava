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

val SIGNATURE_FILE_OPTIONS_HELP =
    """
Signature File Output:

  Options controlling the signature file output. The format of the generated file is determined by the options in the
  `Signature Format Output` section.

  --api <file>                               Output file into which the API signature will be generated. If this is not
                                             specified then no API signature file will be created.
  --removed-api <file>                       Output file into which the API signatures for removed APIs will be
                                             generated. If this is not specified then no removed API signature file will
                                             be created.
  --delete-empty-removed-signatures          Deletes removed signature files if they are empty.
    """
        .trimIndent()

class SignatureFileOptionsTest :
    BaseOptionGroupTest<SignatureFileOptions>(SIGNATURE_FILE_OPTIONS_HELP) {
    override fun createOptions() = SignatureFileOptions()
}
