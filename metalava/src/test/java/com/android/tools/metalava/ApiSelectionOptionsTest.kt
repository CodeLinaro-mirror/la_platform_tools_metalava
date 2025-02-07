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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val API_SELECTION_OPTIONS_HELP =
    """
Api Selection:

  Options that select which parts of the source files will be part of the generated API.

  --api-surface <surface>                    The API surface currently being generated.

                                             Currently, only used for testing purposes.
  --show-annotation <annotation-filter>      Unhide any hidden elements that are also annotated with the given
                                             annotation.
  --show-single-annotation <annotation-filter>
                                             Like --show-annotation, but does not apply to members; these must also be
                                             explicitly annotated.
  --show-for-stub-purposes-annotation <annotation-filter>
                                             Like --show-annotation, but elements annotated with it are assumed to be
                                             "implicitly" included in the API surface, and they'll be included in
                                             certain kinds of output such as stubs, but not in others, such as the
                                             signature file and API lint.
    """
        .trimIndent()

class ApiSelectionOptionsTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {
    override fun createOptions() = ApiSelectionOptions()
}
