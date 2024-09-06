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

package com.android.tools.metalava.model.item

import java.io.File

/**
 * A resource file is one that is supplied on input to Metalava and copied through to the stubs
 * unchanged.
 *
 * e.g. This is used for copying the `overview.html` file when generate documentation stubs.
 */
class ResourceFile(private val file: File) {
    val content by lazy(LazyThreadSafetyMode.NONE) { file.readText() }
}
