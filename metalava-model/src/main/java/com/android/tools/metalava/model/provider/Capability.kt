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

package com.android.tools.metalava.model.provider

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase

/** The set of different capabilities that a codebase creator can provide. */
enum class Capability {
    /** Can parse java files. */
    JAVA,

    /** Can parse kotlin files. */
    KOTLIN,

    /** Can parse signature files. */
    SIGNATURE,

    /** Has access to the method body. */
    METHOD_BODY,

    /** Has access to documentation. */
    DOCUMENTATION,

    /** Can load additional APIs from a jar file. */
    LOAD_JAR,

    /** Can create a [ClassPathResolver]. */
    CLASS_PATH_RESOLVER,

    /** Can load additional APIs from a jar file when creating an API from source files. */
    JAR_WITH_SOURCES,

    /** Can continue parsing if it hits an error. */
    LAX_PARSER,

    /** Has access to the imports from the source file. */
    IMPORTS,

    /** Has access to `package.html` files. */
    PACKAGE_HTML_FILES,

    /** Has access to hidden items. */
    HIDDEN_ITEMS,

    /** Has access to [ApiVariantSelectors]. */
    API_VARIANT_SELECTORS,

    /** Can create [MultiplatformCodebase]s */
    MULTIPLATFORM,
}
