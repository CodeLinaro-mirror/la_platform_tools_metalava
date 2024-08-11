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

import com.android.tools.metalava.reporter.FileLocation

/** Represents the body of a [CallableItem]. */
interface CallableBody {

    /**
     * Finds uncaught exceptions actually thrown inside this body (as opposed to ones declared in
     * the signature)
     */
    fun findThrownExceptions(): Set<ClassItem>

    /**
     * Finds the locations within this where a `synchronized` statement may be visible because it
     * locks either the instance on which the method is called or its class. e.g. `synchronized
     * (this) {...}` or `synchronized (Class.class)`.
     */
    fun findVisiblySynchronizedLocations(): List<FileLocation>

    companion object {
        /** Indicates that the model does not provide [CallableBody] instances. */
        val UNAVAILABLE =
            object : CallableBody {
                override fun findThrownExceptions() = error("method body is unavailable")

                /** Return an empty list as the method body is unavailable. */
                override fun findVisiblySynchronizedLocations() = emptyList<FileLocation>()
            }
    }
}
