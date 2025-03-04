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
) : CombinableConfig<Config> {

    /** Combine this [Config] with another returning a [Config] object that combines them both. */
    override fun combineWith(other: Config): Config =
        Config(
            apiSurfaces = combine(apiSurfaces, other.apiSurfaces),
        )

    /**
     * Combined two possibly nullable objects, if either are null then return the other, otherwise
     * invoke [CombinableConfig.combineWith].
     */
    internal fun <T : CombinableConfig<T>> combine(t1: T?, t2: T?): T? {
        return if (t1 == null) t2 else if (t2 == null) t1 else t1.combineWith(t2)
    }

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    internal fun validate() {
        apiSurfaces?.validate()
    }
}

/** Implemented by config objects that can be combined when loaded in separate files. */
interface CombinableConfig<T : CombinableConfig<T>> {
    /** Combine this with [other] returning a new instance. */
    fun combineWith(other: T): T
}
