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

/** The top level configuration object. */
data class Config(
    val apiFlags: ApiFlagsConfig? = null,
    val apiSurfaces: ApiSurfacesConfig? = null,
    val buildProperties: BuildPropertiesConfig? = null,
    val issues: IssuesConfig? = null,
    val annotationClasses: AnnotationClassesConfig? = null,
    val documentation: DocumentationConfig? = null,
) : CombinableConfig<Config>, ConfigXmlWritable {

    /** Combine this [Config] with another returning a [Config] object that combines them both. */
    override fun combineWith(other: Config): Config =
        Config(
            apiFlags = combine(apiFlags, other.apiFlags),
            apiSurfaces = combine(apiSurfaces, other.apiSurfaces),
            buildProperties = combine(buildProperties, other.buildProperties),
            issues = combine(issues, other.issues),
            annotationClasses = combine(annotationClasses, other.annotationClasses),
            documentation = combine(documentation, other.documentation),
        )

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    internal fun validate() {
        apiFlags?.validate()
        apiSurfaces?.validate()
        buildProperties?.validate()
        annotationClasses?.validate()
        documentation?.validate()
    }

    /** Write this [Config] to [writer]. */
    override fun writeTo(writer: ConfigWriter) {
        writer.element("config") {
            apiFlags?.writeTo(this)
            apiSurfaces?.writeTo(this)
            buildProperties?.writeTo(this)
            issues?.writeTo(this)
            annotationClasses?.writeTo(this)
            documentation?.writeTo(this)
        }
    }
}

/** Implemented by config objects that can be combined when loaded in separate files. */
interface CombinableConfig<T : CombinableConfig<T>> {
    /** Combine this with [other] returning a new instance. */
    fun combineWith(other: T): T
}

/**
 * Combined two possibly nullable objects, if either are null then return the other, otherwise
 * invoke [CombinableConfig.combineWith].
 */
internal fun <T : CombinableConfig<T>> combine(t1: T?, t2: T?): T? {
    return if (t1 == null) t2 else if (t2 == null) t1 else t1.combineWith(t2)
}
