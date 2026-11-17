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

data class DocumentationConfig(
    val externalPackages: List<ExternalPackageConfig> = emptyList(),
) : CombinableConfig<DocumentationConfig> {
    /** Combine with another [DocumentationConfig] by concatenating the [externalPackages]s. */
    override fun combineWith(other: DocumentationConfig) =
        DocumentationConfig(externalPackages + other.externalPackages)

    /** Extract the set of external package prefixes. */
    fun toPackagePrefixes(): Set<String> = externalPackages.mapTo(mutableSetOf()) { it.prefix }

    /** Validate this object, i.e. check to make sure that the contained objects are consistent. */
    fun validate() {}

    /** Write this [DocumentationConfig] to [writer]. */
    internal fun writeTo(writer: ConfigWriter) {
        writer.element("documentation") {
            for (externalPackage in externalPackages) {
                externalPackage.writeTo(this)
            }
        }
    }
}

data class ExternalPackageConfig(
    val prefix: String,
) {
    /** Write this [ExternalPackageConfig] to [writer]. */
    internal fun writeTo(writer: ConfigWriter) {
        writer.element(
            "external-package",
            attributes = { attribute("prefix", prefix) },
        )
    }
}
