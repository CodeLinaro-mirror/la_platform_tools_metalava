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

package com.android.tools.metalava.config

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class BuildPropertiesConfig(
    @field:JacksonXmlProperty(localName = "build-property", namespace = CONFIG_NAMESPACE)
    val properties: List<BuildPropertyConfig> = emptyList(),
) : CombinableConfig<BuildPropertiesConfig> {

    /** Combine with another [BuildPropertyConfig] by concatenating the [properties]s. */
    override fun combineWith(other: BuildPropertiesConfig) =
        BuildPropertiesConfig(properties + other.properties)

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {}
}

data class BuildPropertyConfig(
    /** The build property name */
    @field:JacksonXmlProperty(isAttribute = true) val name: String,
    /** The build property value */
    @field:JacksonXmlProperty(isAttribute = true) val value: String,
) {}
