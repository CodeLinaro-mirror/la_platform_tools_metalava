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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.value.ArrayElementValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.Value
import java.util.TreeMap
import kotlin.collections.map

/**
 * Matches an annotation against a set of [Rule]s.
 *
 * Each [Rule] contains a pattern for matching and the result to return if the pattern matches.
 */
internal class AnnotationMatcher<R : Any>
private constructor(
    private val qualifiedNameToEntries: Map<String, List<Entry<R>>>,
) {
    /**
     * A rule that this will try and match.
     *
     * @param annotationPattern the annotation pattern that it will match. This is the source
     *   representation of an annotation without the leading `@`.
     * @param result the value that will be returned from [matchResult].
     */
    data class Rule<R : Any>(val annotationPattern: String, val result: R)

    /**
     * Returns a sorted set of fully qualified annotation names that may be matched by this matcher.
     * Note that this matcher might incorporate parameters but this function strips them.
     */
    val annotationNames: Set<String>
        get() = qualifiedNameToEntries.keys

    /**
     * Checks whether an annotation is matched by a [Rule] and if was then return the [Rule.result],
     * otherwise return `null`.
     */
    fun matchResult(annotation: AnnotationItem): R? {
        val qualifiedName = annotation.qualifiedName
        // If the annotation name is not in the map of annotation names that can be matched then
        // this can never match so return immediately rather than generating an entry for the
        // annotation.
        if (qualifiedName !in qualifiedNameToEntries) {
            return null
        }

        // If there are no entries for the annotation's class then return immediately.
        val entries = qualifiedNameToEntries[annotation.qualifiedName] ?: return null

        // Get the attributes from the annotation and normalize them.
        val attributes = normalizeAttributes(annotation)
        return entries.find { entry -> entry.attributesMatch(attributes) }?.result
    }

    override fun toString() = buildString {
        append("AnnotationMatcher(\n")
        for ((qualifiedName, entries) in qualifiedNameToEntries) {
            append("    ")
            append(qualifiedName)
            append(" -> {\n")
            for (entry in entries) {
                append("        Entry(\n")
                for ((name, value) in entry.attributes) {
                    append("            ")
                    append(name)
                    append("=")
                    append(value.toValueString())
                    append("\n")
                }
                append("            result: ")
                append(entry.result)
                append("\n        )\n")
            }
            append("    }\n")
        }
        append(")")
    }

    /**
     * Matches [attributes] against those retrieved from an [AnnotationItem].
     *
     * This does not match [AnnotationItem.qualifiedName] as that has already been used to retrieve
     * the possibly matching [Entry] list from [qualifiedNameToEntries].
     */
    private class Entry<R : Any>(
        val attributes: Map<String, Value>,
        val result: R,
    ) {
        fun attributesMatch(
            annotationAttributes: Map<String, Value>,
        ): Boolean {
            // The annotation must have an attribute for each attribute in the matcher.
            if (attributes.size > annotationAttributes.size) {
                return false
            }

            // The annotation must have the same value as every matcher attribute.
            return attributes.all { (attributeName, matcherValue) ->
                matcherValue == annotationAttributes[attributeName]
            }
        }
    }

    companion object {
        /**
         * Create an [AnnotationMatcher] from a list of [rules] each of which maps an annotation
         * pattern to an associated [Rule.result] that will be returned by
         * [AnnotationMatcher.matchResult] if the supplied annotation matches the pattern.
         */
        fun <R : Any> createFromRules(rules: List<Rule<R>>): AnnotationMatcher<R> {
            val map =
                rules
                    .map { fromRule(it) }
                    .groupByTo(TreeMap(), { it.qualifiedName }) { Entry(it.attributes, it.result) }
            return AnnotationMatcher(map)
        }

        /**
         * Encapsulates information extracted from an [AnnotationItem] needed during construction of
         * an [AnnotationMatcher].
         */
        private class IntermediateData<R : Any>(
            val qualifiedName: String,
            val attributes: Map<String, Value>,
            val result: R,
        )

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

        private fun <R : Any> fromRule(rule: Rule<R>): IntermediateData<R> {
            val pattern = rule.annotationPattern
            val annotationItem =
                AnnotationItem.createFromSource(
                    // Use the NoOpAnnotationManager whose `normalizeInputName(...)` method will not
                    // reject any annotations so createFromSource(...) will never return null.
                    AnnotationContext.DEFAULT_RESOLVE_NULL,
                    "@$pattern"
                ) ?: error("Could not construct annotation from `$pattern`")

            val attributes = normalizeAttributes(annotationItem)
            return IntermediateData(annotationItem.qualifiedName, attributes, rule.result)
        }

        private fun normalizeAttributes(annotationItem: AnnotationItem): Map<String, Value> {
            val attributes = annotationItem.attributes
            val attributeDefaults =
                annotationItem.annotationContext.defaultsForAnnotationClass(
                    annotationItem.qualifiedName
                )

            // Create a map from attribute name to normalized value.
            val normalizedAttributes =
                attributes.associateBy({ it.name }) { it.value.normalizeValue() }

            // Merge in any default values.
            val withDefaults = attributeDefaults.apply(normalizedAttributes)

            return withDefaults
        }
    }
}
