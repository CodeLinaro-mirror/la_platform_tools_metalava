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

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationArrayAttributeValue
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationSingleAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationItem
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
        // this can never match so return immediately rather than generating the source
        // representation of the annotation.
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
        if (filter.attributes.count() > existingAnnotation.attributes.count()) {
            return false
        }
        for (attribute in filter.attributes) {
            val existingValue = existingAnnotation.findAttribute(attribute.name)?.legacyValue
            val existingValueSource = existingValue?.toSource()
            val attributeValueSource = attribute.legacyValue.toSource()
            if (attribute.name == "value") {
                // Special-case where varargs value annotation attribute can be specified with
                // either @Foo(BAR) or @Foo({BAR}) and they are equivalent.
                when {
                    attribute.legacyValue is AnnotationSingleAttributeValue &&
                        existingValue is AnnotationArrayAttributeValue -> {
                        if (existingValueSource != "{$attributeValueSource}") return false
                    }
                    attribute.legacyValue is AnnotationArrayAttributeValue &&
                        existingValue is AnnotationSingleAttributeValue -> {
                        if ("{$existingValueSource}" != attributeValueSource) return false
                    }
                    else -> {
                        if (existingValueSource != attributeValueSource) return false
                    }
                }
            } else {
                if (existingValueSource != attributeValueSource) {
                    return false
                }
            }
        }
        return true
    }
}

// An AnnotationFilterEntry filters for annotations having a certain qualifiedName and
// possibly certain attributes.
// An AnnotationFilterEntry doesn't necessarily have a Codebase like an AnnotationItem does
private class AnnotationFilterEntry(
    val qualifiedName: String,
    val attributes: List<AnnotationAttribute>
) {
    fun findAttribute(name: String?): AnnotationAttribute? {
        val actualName = name ?: ANNOTATION_ATTR_VALUE
        return attributes.firstOrNull { it.name == actualName }
    }

    companion object {
        fun fromSource(source: String): AnnotationFilterEntry {
            val text = source.replace("@", "")
            return fromOption(text)
        }

        fun fromOption(text: String): AnnotationFilterEntry {
            val annotationItem =
                DefaultAnnotationItem.createFromSource(
                    // Use the NoOpAnnotationManager whose `normalizeInputName(...)` method will not
                    // reject any annotations so createFromSource(...) will never return null.
                    AnnotationContext.DEFAULT_RESOLVE_NULL,
                    "@$text"
                ) ?: error("Could not construct annotation from `$text`")

            val qualifiedName = annotationItem.qualifiedName
            val attributes = annotationItem.attributes
            return AnnotationFilterEntry(qualifiedName, attributes)
        }

        fun fromAnnotationItem(annotationItem: AnnotationItem): AnnotationFilterEntry {
            // Have to call toSource to resolve attribute values into fully qualified class names.
            // For example: resolving RestrictTo(LIBRARY_GROUP) into
            // RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)
            // In addition, toSource (with the default argument showDefaultAttrs=true) retrieves
            // default attributes from the definition of the annotation. For example,
            // @SystemApi actually is converted into @android.annotation.SystemApi(\
            // client=android.annotation.SystemApi.Client.PRIVILEGED_APPS,\
            // process=android.annotation.SystemApi.Process.ALL)
            return fromSource(annotationItem.toSource(showDefaultAttrs = true))
        }
    }
}
