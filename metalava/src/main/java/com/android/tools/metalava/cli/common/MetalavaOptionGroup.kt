/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.cli.common

import com.github.ajalt.clikt.parameters.groups.OptionGroup

/** Base class for [OptionGroup]s used by metalava. */
abstract class MetalavaOptionGroup(name: String? = null, help: String? = null) :
    OptionGroup(name, help) {
    /**
     * Error `lazy` implementation with the same signature as [kotlin.lazy].
     *
     * This is to prevent [MetalavaOptionGroup]s from using [kotlin.lazy] as a property delegate
     * because an option group can be reused between two invocations of a command. When this happens
     * and a lazy property is used, the second invocation will use the value computed based on the
     * options used for the first invocation instead of the options used for the second.
     */
    @Deprecated(MESSAGE, level = DeprecationLevel.ERROR)
    fun <T> lazy(mode: LazyThreadSafetyMode, initializer: () -> T): Lazy<T> = error(MESSAGE)

    companion object {
        const val MESSAGE = "MetalavaOptionGroup cannot use `by lazy` for delegate properties"
    }
}
