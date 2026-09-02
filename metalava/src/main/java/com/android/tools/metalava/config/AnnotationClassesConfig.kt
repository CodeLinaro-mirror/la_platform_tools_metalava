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

package com.android.tools.metalava.config

import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class AnnotationClassesConfig(
    @field:JacksonXmlProperty(localName = "annotation-class", namespace = CONFIG_NAMESPACE)
    val annotationClasses: List<AnnotationClassConfig> = emptyList(),
) : CombinableConfig<AnnotationClassesConfig> {
    /** Combine with another [AnnotationClassesConfig] by concatenating the [annotationClasses]s. */
    override fun combineWith(other: AnnotationClassesConfig) =
        AnnotationClassesConfig(annotationClasses + other.annotationClasses)

    /** Convert to a map of annotation class qualified name to its targets. */
    fun toAnnotationClassTargets(): Map<String, Set<AnnotationTarget>> =
        annotationClasses.associate { it.name to it.targets.annotationTargets }

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {}
}

data class AnnotationClassConfig(
    @field:JacksonXmlProperty(isAttribute = true) val name: String,
    @field:JacksonXmlProperty(isAttribute = true) val targets: TargetsConfig,
) {
    enum class TargetsConfig(
        private val configFileValue: String,
        /** The set of [AnnotationTarget]s where matching annotations should be included. */
        val annotationTargets: Set<AnnotationTarget>,
    ) {
        NONE("none", NO_ANNOTATION_TARGETS),
        ;

        /** Name to use when serializing and deserializing this [TargetsConfig] instance. */
        @JsonValue fun forJackson() = configFileValue
    }
}
