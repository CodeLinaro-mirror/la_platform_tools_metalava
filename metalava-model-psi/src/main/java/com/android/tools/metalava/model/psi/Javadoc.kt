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

/**
 * Whether we should report unresolved symbols. This is typically a bug in the documentation. It
 * looks like there are a LOT of mistakes right now, so I'm worried about turning this on since
 * doclava didn't seem to abort on this.
 *
 * Here are some examples I've spot checked: (1) "Unresolved SQLExceptionif": In
 * java.sql.CallableStatement the getBigDecimal method contains this, presumably missing a space
 * before the if suffix: "@exception SQLExceptionif parameterName does not..." (2) In
 * android.nfc.tech.IsoDep there is "@throws TagLostException if ..." but TagLostException is not
 * imported anywhere and is not in the same package (it's in the parent package).
 */
const val REPORT_UNRESOLVED_SYMBOLS = false

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
