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

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class AnnotationClassesConfig(
    @field:JacksonXmlProperty(localName = "annotation-class", namespace = CONFIG_NAMESPACE)
    val annotationClasses: List<AnnotationClassConfig> = emptyList(),
) : CombinableConfig<AnnotationClassesConfig> {
    /** Combine with another [AnnotationClassesConfig] by concatenating the [annotationClasses]s. */
    override fun combineWith(other: AnnotationClassesConfig) =
        AnnotationClassesConfig(annotationClasses + other.annotationClasses)

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {}
}

data class AnnotationClassConfig(
    @field:JacksonXmlProperty(isAttribute = true) val name: String,
    @field:JacksonXmlProperty(isAttribute = true) val targets: TargetsConfig,
) {
    enum class TargetsConfig(private val configFileValue: String) {
        NONE("none"),
        ;

        /** Name to use when serializing and deserializing this [TargetsConfig] instance. */
        @JsonValue fun forJackson() = configFileValue
    }
}
