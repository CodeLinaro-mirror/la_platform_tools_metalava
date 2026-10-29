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

import com.android.tools.metalava.config.ApiFlagActionConfig.Status
import com.android.tools.metalava.reporter.Severity
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class IssuesConfig(
    @field:JacksonXmlProperty(localName = "issue", namespace = CONFIG_NAMESPACE)
    val issues: List<IssueConfig> = emptyList(),
) : CombinableConfig<IssuesConfig> {
    /** Combine with another [IssuesConfig] by concatenating the [issues]s. */
    override fun combineWith(other: IssuesConfig) = IssuesConfig(issues + other.issues)
}

data class IssueConfig(
    @field:JacksonXmlProperty(isAttribute = true) val name: String,
    @field:JacksonXmlProperty(isAttribute = true) val severity: SeverityConfig,
) {
    enum class SeverityConfig(private val configFileValue: String, val issueSeverity: Severity) {
        HIDDEN("hidden", Severity.HIDDEN),
        INFO("info", Severity.INFO),
        WARNING("warning", Severity.WARNING),
        WARNING_ERROR_WHEN_NEW("error-when-new", Severity.WARNING_ERROR_WHEN_NEW),
        ERROR("error", Severity.ERROR),
        ;

        /** Name to use when serializing and deserializing this [Status] instance. */
        @JsonValue fun forJackson() = configFileValue
    }
}
