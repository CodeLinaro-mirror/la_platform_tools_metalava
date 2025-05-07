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

package com.android.tools.metalava.model.annotation

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.Value
import java.util.TreeMap

interface AnnotationFilter {
    // tells whether an annotation is included by the filter
    fun matches(annotation: AnnotationItem): Boolean

    // Returns a sorted set of fully qualified annotation names that may be included by this filter.
    // Note that this filter might incorporate parameters but this function strips them.
    fun getIncludedAnnotationNames(): Set<String>

    // Returns true if [getIncludedAnnotationNames] includes the given qualified name
    fun matchesAnnotationName(qualifiedName: String): Boolean

    // Returns true if nothing is matched by this filter
    fun isEmpty(): Boolean

    // Returns true if some annotation is matched by this filter
    fun isNotEmpty(): Boolean

    companion object {
        private val empty = AnnotationFilterBuilder().build()

        fun emptyFilter(): AnnotationFilter = empty

        /**
         * Create an [AnnotationFilter] from a list of [filterExpressions] each of which is an
         * annotation filter expression that can include or exclude an annotation based on its
         * qualified name and/or attribute values.
         */
        fun create(filterExpressions: List<String>): AnnotationFilter {
            val builder = AnnotationFilterBuilder()
            filterExpressions.forEach(builder::add)
            return builder.build()
        }
    }
}

/** Builder for [AnnotationFilter]s. */
class AnnotationFilterBuilder {
    private val inclusionExpressions = mutableListOf<AnnotationFilterEntry>()

    // Adds the given option as a fully qualified annotation name to match with this filter
    // Can be "androidx.annotation.RestrictTo"
    // Can be "androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)"
    // Note that the order of calls to this method could affect the return from
    // {@link #firstQualifiedName} .
    fun add(option: String) {
        inclusionExpressions.add(AnnotationFilterEntry.fromOption(option))
    }

    /** Build the [AnnotationFilter]. */
    fun build(): AnnotationFilter {
        val map = inclusionExpressions.groupByTo(TreeMap()) { it.qualifiedName }
        return ImmutableAnnotationFilter(map)
    }
}

// Immutable implementation of AnnotationFilter
private class ImmutableAnnotationFilter(
    private val qualifiedNameToEntries: Map<String, List<AnnotationFilterEntry>>
) : AnnotationFilter {

    override fun matches(annotation: AnnotationItem): Boolean {
        val qualifiedName = annotation.qualifiedName
        // If the annotation name is not in the map of annotation names that can be matched then
        // this can never match so return immediately rather than generating a entry for the
        // annotation.
        if (qualifiedName !in qualifiedNameToEntries) {
            return false
        }
        val wrapper = AnnotationFilterEntry.fromAnnotationItem(annotation)
        return matches(wrapper)
    }

    private fun matches(annotation: AnnotationFilterEntry): Boolean {
        val entries = qualifiedNameToEntries[annotation.qualifiedName] ?: return false
        return entries.any { entry -> annotationsMatch(entry, annotation) }
    }

    override fun getIncludedAnnotationNames(): Set<String> = qualifiedNameToEntries.keys

    override fun matchesAnnotationName(qualifiedName: String): Boolean {
        return qualifiedNameToEntries.contains(qualifiedName)
    }

    override fun isEmpty(): Boolean {
        return qualifiedNameToEntries.isEmpty()
    }

    override fun isNotEmpty(): Boolean {
        return !isEmpty()
    }

    private fun annotationsMatch(
        filter: AnnotationFilterEntry,
        existingAnnotation: AnnotationFilterEntry
    ): Boolean {
        // The annotation must have an attribute for each attribute in the filter.
        if (filter.attributes.size > existingAnnotation.attributes.size) {
            return false
        }

        // The annotation must have the same value as every filter attribute.
        val annotationAttributes = existingAnnotation.attributes
        return filter.attributes.all { (attributeName, filterValue) ->
            filterValue == annotationAttributes[attributeName]
        }
    }
}

/**
 * An [AnnotationFilterEntry] filters for annotations having a certain [qualifiedName] and possibly
 * certain [attributes].
 *
 * An [AnnotationFilterEntry] does not have a Codebase like an [AnnotationItem] does.
 */
private class AnnotationFilterEntry
private constructor(
    val qualifiedName: String,
    val attributes: Map<String, Value>,
) {
    companion object {
        /** Normalize this [Value] to simplify comparison. */
        private fun Value.normalizeValue(): Value =
            when (this) {
                is ArrayValue -> {
                    val size = elements.size
                    when (size) {
                        0 -> this
                        // Replace an array containing a single value with the normalized value.
                        1 -> elements[0]
                        // Normalize the elements of the array.
                        else -> Value.createArrayValue(elements)
                    }
                }
                is ArrayElementValue -> this
            }

        fun fromOption(text: String): AnnotationFilterEntry {
            val annotationItem =
                DefaultAnnotationItem.createFromSource(
                    // Use the NoOpAnnotationManager whose `normalizeInputName(...)` method will not
                    // reject any annotations so createFromSource(...) will never return null.
                    AnnotationContext.DEFAULT_RESOLVE_NULL,
                    "@$text"
                ) ?: error("Could not construct annotation from `$text`")

            return fromAnnotationItem(annotationItem)
        }

        fun fromAnnotationItem(annotationItem: AnnotationItem): AnnotationFilterEntry {
            val qualifiedName = annotationItem.qualifiedName

            // Create a map from attribute name to normalized value.
            val attributes =
                annotationItem.attributes.associateBy({ it.name }) { it.value.normalizeValue() }

            // Merge in any default values.
            val withDefaults =
                annotationItem.annotationContext
                    .defaultsForAnnotationClass(annotationItem.qualifiedName)
                    .apply(attributes)

            return AnnotationFilterEntry(qualifiedName, withDefaults)
        }
    }
}
