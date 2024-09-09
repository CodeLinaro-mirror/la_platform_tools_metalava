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

import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.FileLocation

/**
 * A lamda that given a [CallableItem] will create a [CallableBody] for it.
 *
 * This is called from within the constructor of the [CallableItem] and should not access any
 * properties of [CallableItem] as they may not have been initialized. This should just store a
 * reference for later use.
 */
typealias CallableBodyFactory = (CallableItem) -> CallableBody

/** Represents the body of a [CallableItem]. */
interface CallableBody {

    /**
     * Return a duplicate of this instance to use by [callableItem] which will be in the same type
     * of [Codebase] as this.
     */
    fun duplicate(callableItem: CallableItem): CallableBody

    /**
     * Take a snapshot of this suitable for use by [callableItem] which will be in a
     * [DefaultCodebase].
     *
     * At the moment this simply delegates to [duplicate] as there is no easy way to take a snapshot
     * of the state.
     */
    fun snapshot(callableItem: CallableItem) = duplicate(callableItem)

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

    /**
     * Called on a method whose return value is annotated with [typeDefAnnotation] of class
     * [typeDefClass].
     *
     * This scans the body of the method, finds `return` statements and checks to make sure that if
     * they use a constant that it is one of the constants in the type def, reporting any which are
     * not.
     */
    fun verifyReturnedConstants(typeDefAnnotation: AnnotationItem, typeDefClass: ClassItem)

    companion object {
        /** Indicates that the model does not provide [CallableBody] instances. */
        val UNAVAILABLE =
            object : CallableBody {
                override fun duplicate(callableItem: CallableItem) = this

                override fun findThrownExceptions() = error("method body is unavailable")

                /** Return an empty list as the method body is unavailable. */
                override fun findVisiblySynchronizedLocations() = emptyList<FileLocation>()

                /** Do nothing. */
                override fun verifyReturnedConstants(
                    typeDefAnnotation: AnnotationItem,
                    typeDefClass: ClassItem
                ) {}
            }

        /**
         * A special [CallableBodyFactory] that returns [UNAVAILABLE].
         *
         * Used where there is no available body, e.g. text/turbine model, implicit default
         * constructor, etc..
         */
        val UNAVAILABLE_FACTORY: CallableBodyFactory = { UNAVAILABLE }
    }
}
