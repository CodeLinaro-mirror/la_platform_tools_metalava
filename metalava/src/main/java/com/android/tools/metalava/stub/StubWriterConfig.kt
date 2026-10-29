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

package com.android.tools.metalava.stub

/**
 * Contains configuration for [StubWriter] that can, or at least could, come from command line
 * options.
 */
internal data class StubWriterConfig(
    /** If true then include documentation in the generated stubs. */
    val includeDocumentationInStubs: Boolean = false,

    /**
     * If true then include Java record class related information in the generated stubs. Otherwise,
     * treat record classes as normal classes as much as possible.
     */
    val javaRecordClasses: Boolean = false,

    /**
     * If true then include Java sealed class related information in the generated stubs.
     *
     * TODO(b/482391240): Decide what to do with sealed classes when this is false.
     */
    val javaSealedClasses: Boolean = false,
)
