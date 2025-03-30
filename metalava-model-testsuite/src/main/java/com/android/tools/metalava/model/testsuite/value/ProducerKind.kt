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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.Codebase

/**
 * The kinds of [Codebase] producers.
 *
 * They are enumerated here because they can affect how values are represented. e.g. an annotation
 * attribute that is specified by a constant field in the source will be represented by the value of
 * the constant field in a jar.
 */
enum class ProducerKind {
    JAR,
    SOURCE,
}
