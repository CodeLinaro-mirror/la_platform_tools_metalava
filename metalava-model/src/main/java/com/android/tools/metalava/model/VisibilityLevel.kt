/*
 * Copyright (C) 2019 The Android Open Source Project
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

/**
 * The visibility levels for classes and class members.
 *
 * <p>The order is significant. It is from least visible to most visible, where visibility is in
 * terms of whether it is viewable in the final generated API.
 */
enum class VisibilityLevel(
    /** String representation in source code. */
    val javaSourceCodeModifier: String,

    /** String representation in user visible messages. */
    val userVisibleDescription: String,

    /** Representation in the internal flags. */
    internal val visibilityFlagValue: Int
) {
    PACKAGE_PRIVATE(
        javaSourceCodeModifier = "",
        userVisibleDescription = "package private",
        visibilityFlagValue = ModifierFlags.PACKAGE_PRIVATE,
    ),
    PRIVATE(
        javaSourceCodeModifier = "private",
        userVisibleDescription = "private",
        visibilityFlagValue = ModifierFlags.PRIVATE,
    ),
    INTERNAL(
        javaSourceCodeModifier = "internal",
        userVisibleDescription = "internal",
        visibilityFlagValue = ModifierFlags.INTERNAL,
    ),
    PROTECTED(
        javaSourceCodeModifier = "protected",
        userVisibleDescription = "protected",
        visibilityFlagValue = ModifierFlags.PROTECTED,
    ),
    PUBLIC(
        javaSourceCodeModifier = "public",
        userVisibleDescription = "public",
        visibilityFlagValue = ModifierFlags.PUBLIC,
    )
}
