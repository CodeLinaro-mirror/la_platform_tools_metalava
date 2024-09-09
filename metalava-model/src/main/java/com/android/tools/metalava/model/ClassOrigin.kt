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

package com.android.tools.metalava.model

/** Identifies the origin of a specific class definition. */
enum class ClassOrigin {
    /**
     * The class was defined in a file that was specified on the command line.
     *
     * That can include signature files, jar files as well as source files.
     */
    COMMAND_LINE,

    /** The class was found while searching for a class on the source path. */
    SOURCE_PATH,

    /** The class was found while searching for a class on the class path. */
    CLASS_PATH,
}
