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

package com.android.tools.metalava.doc.annotationhandlers

import java.io.File
import java.io.Reader
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** A change made to a policy in a specific release or behind a flag. */
sealed interface PolicyChange {
    /** An allowed scope was added to the policy. */
    data class AllowedScope(val scope: PolicyScope) : PolicyChange

    /** An allowed DPC type was added to the policy. */
    data class AllowedDpc(val dpcType: AllowedDpcType) : PolicyChange

    /** The required cross-user permission changed. */
    data class CrossUserPermission(val previous: String?, val newValue: String?) : PolicyChange

    /** An allowed enum value was added to the policy. */
    data class EnumValue(val value: Int) : PolicyChange
}

/** Changes made to a policy in a release or behind a feature flag. */
sealed interface PolicyDelta {
    val changes: List<PolicyChange>

    data class Flagged(val flag: String, override val changes: List<PolicyChange>) : PolicyDelta

    data class Released(val since: String, override val changes: List<PolicyChange>) : PolicyDelta
}

/** Historical changes for a single policy. */
data class PolicyHistory(
    val id: String,
    val flaggedDelta: PolicyDelta.Flagged?,
    val releasedDeltas: List<PolicyDelta.Released>,
)

/** Parsed history for all device policies from policy-history.xml. */
class DevicePolicyHistory(private val policiesById: Map<String, PolicyHistory>) {
    fun getPolicyHistory(id: String): PolicyHistory? = policiesById[id]

    companion object {
        val EMPTY = DevicePolicyHistory(emptyMap())

        fun fromFile(file: File): DevicePolicyHistory {
            if (!file.exists() || file.length() == 0L) {
                return EMPTY
            }
            return file.reader().use { fromReader(it) }
        }

        fun fromString(xml: String): DevicePolicyHistory {
            if (xml.isBlank()) {
                return EMPTY
            }
            return StringReader(xml).use { fromReader(it) }
        }

        /**
         * Parses policy history XML from [reader] using a StAX [XMLStreamReader].
         *
         * Expects a `<policy-changes>` document conforming to `/schemas/policy-history.xsd` (in
         * `metalava/src/main/resources/schemas/policy-history.xsd`), containing `<policy id="...">`
         * entries with optional `<flagged flag="...">` and `<released since="...">` blocks of
         * `<change field="..." previous="...">...</change>` elements.
         *
         * @throws XMLStreamException if the XML stream is malformed.
         * @throws IllegalStateException if a `<change>` specifies an unexpected `field` name.
         */
        fun fromReader(reader: Reader): DevicePolicyHistory {
            val xmlInputFactory =
                XMLInputFactory.newDefaultFactory().apply {
                    setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
                    setProperty(XMLInputFactory.SUPPORT_DTD, false)
                    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
                }
            val streamReader = xmlInputFactory.createXMLStreamReader(reader)
            return parseStream(streamReader).also { streamReader.close() }
        }

        private fun parseStream(reader: XMLStreamReader): DevicePolicyHistory {
            val policies = mutableMapOf<String, PolicyHistory>()
            while (reader.hasNext()) {
                if (
                    reader.next() == XMLStreamConstants.START_ELEMENT &&
                        reader.localName == "policy"
                ) {
                    val policy = parsePolicy(reader)
                    policies[policy.id] = policy
                }
            }
            return DevicePolicyHistory(policies)
        }

        private fun parsePolicy(reader: XMLStreamReader): PolicyHistory {
            val id = reader.getAttributeValue(null, "id") ?: ""
            var flaggedDelta: PolicyDelta.Flagged? = null
            val releasedDeltas = mutableListOf<PolicyDelta.Released>()

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        when (reader.localName) {
                            "flagged" -> {
                                val flag = reader.getAttributeValue(null, "flag") ?: ""
                                flaggedDelta =
                                    PolicyDelta.Flagged(flag, parseChanges(reader, "flagged"))
                            }
                            "released" -> {
                                val since = reader.getAttributeValue(null, "since") ?: ""
                                releasedDeltas.add(
                                    PolicyDelta.Released(since, parseChanges(reader, "released"))
                                )
                            }
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "policy") {
                            break
                        }
                    }
                }
            }
            return PolicyHistory(id, flaggedDelta, releasedDeltas)
        }

        private fun parseChanges(reader: XMLStreamReader, parentTag: String): List<PolicyChange> {
            val changes = mutableListOf<PolicyChange>()
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        if (reader.localName == "change") {
                            changes.add(parseChange(reader))
                        }
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == parentTag) {
                            return changes
                        }
                    }
                }
            }
            return changes
        }

        private fun parseChange(reader: XMLStreamReader): PolicyChange {
            val field = reader.getAttributeValue(null, "field") ?: ""
            val previous = reader.getAttributeValue(null, "previous")?.ifEmpty { null }
            val newValue = reader.elementText.trim()
            return when (field) {
                "allowedScope" -> PolicyChange.AllowedScope(PolicyScope.valueOf(newValue))
                "allowedDpcType" -> PolicyChange.AllowedDpc(AllowedDpcType.valueOf(newValue))
                "requiredCrossUserPermission" ->
                    PolicyChange.CrossUserPermission(
                        previous = previous,
                        newValue = newValue.ifEmpty { null },
                    )
                "enumValue" -> PolicyChange.EnumValue(newValue.toInt())
                else -> error("Unexpected field $field")
            }
        }
    }
}
