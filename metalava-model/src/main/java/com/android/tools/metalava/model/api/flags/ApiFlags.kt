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

import com.android.tools.metalava.model.ANDROID_FLAGGED_API
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.value.asString

/**
 * The action the api flag is accomplishing.
 *
 * The constants are ordered by increasing lifecycle permanence in the API surface (`REVERT < KEEP <
 * FINALIZE`):
 * 1. [REVERT] — Associated [Item]s are reverted (or hidden if newly added) and excluded from the
 *    API surface.
 * 2. [KEEP] — Associated [Item]s are included in the API surface, but remain guarded by their
 *    `@FlaggedApi` annotation as the flag is still mutable and may be disabled at runtime or
 *    reverted in a future release.
 * 3. [FINALIZE] — Associated [Item]s are permanently finalized in the API surface and their
 *    `@FlaggedApi` annotation is stripped.
 */
enum class ApiFlagAction(
    val revert: Boolean,

    /** Controls whether `@FlaggedApi` annotations for this [ApiFlag] are kept or discarded. */
    val annotationTargets: Set<AnnotationTarget>,
) {
    /** Revert any associated [Item]s. */
    REVERT(
        revert = true,
        annotationTargets = NO_ANNOTATION_TARGETS,
    ),

    /** Keep any associated [Item]s and their `@FlaggedApi` annotation. */
    KEEP(
        revert = false,
        annotationTargets = ANNOTATION_IN_ALL_STUBS,
    ),

    /**
     * Keep any associated [Item]s but remove their `@FlaggedApi` annotation as this is being (or
     * has been) finalized.
     */
    FINALIZE(
        revert = false,
        annotationTargets = NO_ANNOTATION_TARGETS,
    ),
}

/** The available set of configured [ApiFlag]s. */
class ApiFlags(
    flags: List<ApiFlag>,
    private val unknownFlagAction: ApiFlagAction = ApiFlagAction.REVERT,
) {
    /** Map from qualified flag name to [ApiFlag]. */
    private val byQualifiedName =
        mutableMapOf<String, ApiFlag>().also { flags.associateByTo(it) { it.qualifiedName } }

    /** All the [ApiFlag]s managed by this. */
    val allFlags: Collection<ApiFlag>
        get() = byQualifiedName.values

    /**
     * Get the [ApiFlag] by qualified name.
     *
     * If no such [ApiFlag] exists then return [ApiFlag] with [unknownFlagAction].
     */
    operator fun get(qualifiedName: String) =
        byQualifiedName.computeIfAbsent(qualifiedName) {
            ApiFlag(it, unknownFlagAction, isExported = true, isKnown = false)
        }

    override fun toString(): String {
        return "ApiFlags(byQualifiedName=$byQualifiedName)"
    }
}

/** A representation of an [ApiFlag] that is associated with an `@FlaggedApi` annotation. */
data class ApiFlag(
    /** The qualified name of the flag. */
    val qualifiedName: String,

    /** The action that this flag will perform. */
    val action: ApiFlagAction,

    /** Whether the flag is exported */
    val isExported: Boolean = true,

    /** Whether the flag is known, i.e. was supplied in the configuration. */
    val isKnown: Boolean = true,
) {
    val revert
        get() = action.revert

    /** Controls whether `@FlaggedApi` annotations for this [ApiFlag] are kept or discarded. */
    val annotationTargets
        get() = action.annotationTargets
}

/**
 * Get the optional flag name from this [AnnotationItem].
 *
 * Returns `null` if this is not [ANDROID_FLAGGED_API] and does not have a `value` attribute.
 * Otherwise, it returns the value attribute as a [String].
 *
 * If the value exists but is not resolvable this returns the name of the field to preserve previous
 * behavior.
 */
val AnnotationItem.optionalFlagName: String?
    get() {
        if (qualifiedName != ANDROID_FLAGGED_API) return null
        val valueAttribute = findAttribute(ANNOTATION_ATTR_VALUE) ?: return null
        return valueAttribute.value.let { value ->
            // Use the literal string value, if possible. It will not be possible if the value is
            // an unresolvable field reference.
            value.asString()
                // Fallback to using the string representation of the field reference.
                ?: value.toValueString()
        }
    }
