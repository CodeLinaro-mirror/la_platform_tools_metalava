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

package com.android.tools.metalava.model.api.flags

import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.Showability

/**
 * The available set of configured [ApiFlag]s.
 *
 * @param byQualifiedName map from qualified flag name to [ApiFlag].
 */
class ApiFlags(val byQualifiedName: Map<String, ApiFlag>) {
    /**
     * Get the [ApiFlag] by qualified name.
     *
     * If no such [ApiFlag] exists then return [ApiFlag.REVERT_FLAGGED_API].
     */
    operator fun get(qualifiedName: String) =
        byQualifiedName[qualifiedName] ?: ApiFlag.REVERT_FLAGGED_API

    override fun toString(): String {
        return "ApiFlags(byQualifiedName=$byQualifiedName)"
    }
}

/** A representation of an [ApiFlag] that is associated with an `@FlaggedApi` annotation. */
class ApiFlag
private constructor(
    /**
     * The qualified name of the flag.
     *
     * Provided for debug purposes only and cannot be relied upon to be the name of an actual flag,
     * e.g. [REVERT_FLAGGED_API]'s [qualifiedName] is simply `<disabled>`.
     */
    val description: String,

    /**
     * The [Showability] of any [Item]s annotated with an `@FlaggedApi` annotation that references
     * this [ApiFlag].
     */
    val showability: Showability,

    /** Controls whether `@FlaggedApi` annotations for this [ApiFlag] are kept or discarded. */
    val annotationTargets: Set<AnnotationTarget>,
) {
    override fun toString(): String {
        return "ApiFlag(description='$description')"
    }

    companion object {
        /** Revert any associated [Item]s. */
        val REVERT_FLAGGED_API =
            ApiFlag(
                "<revert>",
                showability = Showability.REVERT_UNSTABLE_API,
                annotationTargets = NO_ANNOTATION_TARGETS
            )

        /** Keep any associated [Item]s and their `@FlaggedApi` annotation. */
        val KEEP_FLAGGED_API =
            ApiFlag(
                "<keep>",
                showability = Showability.NO_EFFECT,
                annotationTargets = ANNOTATION_IN_ALL_STUBS,
            )

        /**
         * Keep any associated [Item]s but remove their `@FlaggedApi` annotation as this is being
         * (or has been) finalized.
         */
        val FINALIZE_FLAGGED_API =
            ApiFlag(
                "<finalize>",
                showability = Showability.NO_EFFECT,
                annotationTargets = NO_ANNOTATION_TARGETS,
            )
    }
}
