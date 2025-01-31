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

package com.android.tools.metalava.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/** The top level configuration object. */
@JacksonXmlRootElement(localName = "config", namespace = CONFIG_NAMESPACE)
// Ignore the xsi:schemaLocation property if present on the root <config> element.
@JsonIgnoreProperties("schemaLocation")
data class Config(
    @field:JacksonXmlProperty(localName = "api-surfaces", namespace = CONFIG_NAMESPACE)
    val apiSurfaces: ApiSurfacesConfig? = null,
)

/** A set of [ApiSurfaceConfig]s. */
data class ApiSurfacesConfig(
    @field:JacksonXmlProperty(localName = "api-surface", namespace = CONFIG_NAMESPACE)
    val apiSurfaceList: List<ApiSurfaceConfig> = emptyList(),
)

/** An API surface that Metalava could generate. */
data class ApiSurfaceConfig(
    /** The name of the API surface, e.g. `public`, `restricted`, etc. */
    @field:JacksonXmlProperty(isAttribute = true) val name: String?,

    /** The optional name of the API surface that this surface extends, e.g. `public`. */
    @field:JacksonXmlProperty(isAttribute = true) val extends: String? = null,
)
