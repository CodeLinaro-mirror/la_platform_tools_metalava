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

// Contains support for handling a `ThrowableType`, which is a representation of a type that can be
// used in a `throws` list. Initially this will be a set of extension functions/properties for
// accessing `ThrowableType` related information but it will eventually be migrated to separate
// classes.

/**
 * The optional [ClassItem] that is a subclass of [java.lang.Throwable].
 *
 * When `this` is a [TypeParameterItem] this will just return the erased type class, if available,
 * or `null` otherwise. When `this` is not a [TypeParameterItem] then this will just return `this`.
 */
val ClassItem.throwableClass: ClassItem?
    get() =
        if (this is TypeParameterItem) typeBounds().firstNotNullOfOrNull { it.asClass() } else this

/** A description of the `ThrowableType`, suitable for use in reports. */
fun ClassItem.description() =
    if (this is TypeParameterItem)
        "${simpleName()} (extends ${throwableClass?.qualifiedName() ?: "unknown throwable"})}"
    else qualifiedName()
