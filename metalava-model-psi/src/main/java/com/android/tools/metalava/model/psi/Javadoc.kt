/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

/*
 * Various utilities for handling javadoc, such as
 * merging comments into existing javadoc sections,
 * rewriting javadocs into fully qualified references, etc.
 *
 * TODO: Handle KDoc
 */

fun containsLinkTags(documentation: String): Boolean {
    var index = 0
    while (true) {
        index = documentation.indexOf('@', index)
        if (index == -1) {
            return false
        }
        if (
            !documentation.startsWith("@code", index) &&
                !documentation.startsWith("@literal", index) &&
                !documentation.startsWith("@param", index) &&
                !documentation.startsWith("@deprecated", index) &&
                !documentation.startsWith("@inheritDoc", index) &&
                !documentation.startsWith("@return", index)
        ) {
            return true
        }

        index++
    }
}
