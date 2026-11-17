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

import java.io.File
import javax.xml.XMLConstants
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import org.xml.sax.InputSource

/** StAX-based parser for XML configuration files. */
internal class StaxConfigParser private constructor(private val systemId: String) {
    /** Errors that were reported while parsing a configuration file. */
    private val errors = StringBuilder()

    /** Record a validation or parsing error at [lineNumber] with [message]. */
    private fun recordError(lineNumber: Int, message: String) {
        errors.apply {
            append("    ")
            val normalizedPath =
                if (systemId.startsWith("file:")) {
                    systemId.replace("file://", "file:")
                } else {
                    "file:$systemId"
                }
            append(normalizedPath)
            if (lineNumber > 0) {
                append(":")
                append(lineNumber)
            }
            append(": ")
            append(message)
            append("\n")
        }
    }

    /** Record an error from a StAX [XMLStreamException]. */
    private fun recordStreamException(exception: XMLStreamException) {
        val lineNumber = exception.location?.lineNumber ?: -1
        val message = exception.message?.substringAfter("\nMessage: ") ?: "Unknown error"
        recordError(lineNumber, message)
    }

    /** Parse a single XML configuration stream from [reader] into a [Config] object. */
    private fun parseFile(reader: XMLStreamReader): Config {
        var config: Config? = null
        while (reader.hasNext()) {
            val event = reader.next()
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (reader.namespaceURI == CONFIG_NAMESPACE && reader.localName == "config") {
                    config = parseConfig(reader)
                } else {
                    val qName = formatQName(reader.prefix, reader.localName)
                    recordError(
                        reader.location.lineNumber,
                        "cvc-elt.1.a: Cannot find the declaration of element '$qName'.",
                    )
                    skipElement(reader)
                }
            }
        }
        return config ?: Config()
    }

    /** Parse the root `<config>` element from [reader] into a [Config] object. */
    private fun parseConfig(reader: XMLStreamReader): Config {
        checkAttributes(
            reader,
            "config",
            allowedAttributes = emptySet(),
            allowSchemaLocation = true
        )

        var apiSurfaces: ApiSurfacesConfig? = null
        val apiSurfaceNames = mutableSetOf<String>()
        val apiSurfaceExtends = mutableListOf<String>()
        var apiFlags: ApiFlagsConfig? = null
        val apiFlagKeys = mutableSetOf<String>()
        var buildProperties: BuildPropertiesConfig? = null
        val buildPropertyNames = mutableSetOf<String>()
        var issues: IssuesConfig? = null
        var annotationClasses: AnnotationClassesConfig? = null
        val annotationClassNames = mutableSetOf<String>()
        var documentation: DocumentationConfig? = null
        val externalPackagePrefixes = mutableSetOf<String>()

        while (reader.hasNext()) {
            when (nextElementEvent(reader, "config")) {
                XMLStreamConstants.START_ELEMENT -> {
                    val localName = reader.localName
                    if (reader.namespaceURI != CONFIG_NAMESPACE) {
                        recordUnexpectedElement(reader, "config")
                        skipElement(reader)
                        continue
                    }
                    when (localName) {
                        "api-surfaces" -> {
                            val parsed =
                                parseApiSurfaces(reader, apiSurfaceNames, apiSurfaceExtends)
                            apiSurfaces = combine(apiSurfaces, parsed)
                        }
                        "api-flags" -> {
                            val parsed = parseApiFlags(reader, apiFlagKeys)
                            apiFlags = combine(apiFlags, parsed)
                        }
                        "build-properties" -> {
                            val parsed = parseBuildProperties(reader, buildPropertyNames)
                            buildProperties = combine(buildProperties, parsed)
                        }
                        "issues" -> {
                            val parsed = parseIssues(reader)
                            issues = combine(issues, parsed)
                        }
                        "annotation-classes" -> {
                            val parsed = parseAnnotationClasses(reader, annotationClassNames)
                            annotationClasses = combine(annotationClasses, parsed)
                        }
                        "documentation" -> {
                            val parsed = parseDocumentation(reader, externalPackagePrefixes)
                            documentation = combine(documentation, parsed)
                        }
                        else -> {
                            recordUnexpectedElement(reader, "config")
                            skipElement(reader)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    val endLine = reader.location.lineNumber
                    for (ext in apiSurfaceExtends) {
                        if (ext !in apiSurfaceNames) {
                            recordError(
                                endLine,
                                "cvc-identity-constraint.4.3: Key 'ApiSurfaceExtendsKeyRef' with value '$ext' not found for identity constraint of element 'config'.",
                            )
                        }
                    }
                    return Config(
                        apiSurfaces = apiSurfaces,
                        apiFlags = apiFlags,
                        buildProperties = buildProperties,
                        issues = issues,
                        annotationClasses = annotationClasses,
                        documentation = documentation,
                    )
                }
            }
        }
        return Config(
            apiSurfaces = apiSurfaces,
            apiFlags = apiFlags,
            buildProperties = buildProperties,
            issues = issues,
            annotationClasses = annotationClasses,
            documentation = documentation,
        )
    }

    /** Parse an `<api-surfaces>` element from [reader] into an [ApiSurfacesConfig]. */
    private fun parseApiSurfaces(
        reader: XMLStreamReader,
        apiSurfaceNames: MutableSet<String>,
        apiSurfaceExtends: MutableList<String>,
    ): ApiSurfacesConfig {
        checkAttributes(reader, "api-surfaces", allowedAttributes = emptySet())
        val apiSurfaceList = mutableListOf<ApiSurfaceConfig>()
        var docOnly: ApiVariantTypeRuleConfig? = null
        var removed: ApiVariantTypeRuleConfig? = null
        var stage = 0 // 0: api-surface, 1: doc-only, 2: removed

        while (reader.hasNext()) {
            when (nextElementEvent(reader, "api-surfaces")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI != CONFIG_NAMESPACE) {
                        recordUnexpectedElement(reader, "api-surfaces")
                        skipElement(reader)
                        continue
                    }
                    when (reader.localName) {
                        "api-surface" -> {
                            if (stage > 0) {
                                recordUnexpectedElement(reader, "api-surfaces")
                                skipElement(reader)
                            } else {
                                parseApiSurface(reader, apiSurfaceNames, apiSurfaceExtends)?.let {
                                    apiSurfaceList.add(it)
                                }
                            }
                        }
                        "doc-only" -> {
                            if (stage > 1) {
                                recordUnexpectedElement(reader, "api-surfaces")
                                skipElement(reader)
                            } else {
                                stage = 1
                                val parsed = parseApiVariantTypeRule(reader, "doc-only")
                                docOnly = combine(docOnly, parsed)
                            }
                        }
                        "removed" -> {
                            stage = 2
                            val parsed = parseApiVariantTypeRule(reader, "removed")
                            removed = combine(removed, parsed)
                        }
                        else -> {
                            recordUnexpectedElement(reader, "api-surfaces")
                            skipElement(reader)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT ->
                    return ApiSurfacesConfig(
                        apiSurfaceList = apiSurfaceList,
                        docOnly = docOnly,
                        removed = removed,
                    )
            }
        }
        return ApiSurfacesConfig(
            apiSurfaceList = apiSurfaceList,
            docOnly = docOnly,
            removed = removed,
        )
    }

    /** Parse an `<api-surface>` element from [reader] into an [ApiSurfaceConfig]. */
    private fun parseApiSurface(
        reader: XMLStreamReader,
        apiSurfaceNames: MutableSet<String>,
        apiSurfaceExtends: MutableList<String>,
    ): ApiSurfaceConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "api-surface", API_SURFACE_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "api-surface", "name")
        val extends = reader.getAttributeValue(null, "extends")
        val contentsStr = reader.getAttributeValue(null, "contents")

        if (name != null) {
            validatePattern(
                lineNumber,
                "api-surface",
                "name",
                name,
                API_SURFACE_NAME_REGEX,
                "ApiSurfaceNameType",
            )
            checkUniqueKey(lineNumber, apiSurfaceNames, name, "ApiSurfaceByName")
        }
        if (extends != null) {
            validatePattern(
                lineNumber,
                "api-surface",
                "extends",
                extends,
                API_SURFACE_NAME_REGEX,
                "ApiSurfaceNameType",
            )
            apiSurfaceExtends.add(extends)
        }
        val contents =
            if (contentsStr != null) {
                validateEnum(
                    lineNumber,
                    "api-surface",
                    "contents",
                    contentsStr,
                    CONTENTS_MAP,
                    "ContentsConfig",
                )
            } else {
                null
            }

        var selectionCriteria: SelectionCriteriaConfig? = null
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "api-surface")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "selection-criteria" &&
                            selectionCriteria == null
                    ) {
                        selectionCriteria = parseSelectionCriteria(reader)
                    } else {
                        recordUnexpectedElement(reader, "api-surface")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (selectionCriteria == null) {
                        recordIncompleteElement(
                            reader.location.lineNumber,
                            "api-surface",
                            "selection-criteria",
                        )
                    }
                    if (name == null || (contentsStr != null && contents == null)) return null
                    return ApiSurfaceConfig(
                        name = name,
                        extends = extends,
                        contents = contents,
                        selectionCriteria = selectionCriteria ?: SelectionCriteriaConfig(),
                    )
                }
            }
        }
        if (name == null || (contentsStr != null && contents == null)) return null
        return ApiSurfaceConfig(
            name = name,
            extends = extends,
            contents = contents,
            selectionCriteria = selectionCriteria ?: SelectionCriteriaConfig(),
        )
    }

    /** Parse a `<selection-criteria>` element from [reader] into a [SelectionCriteriaConfig]. */
    private fun parseSelectionCriteria(reader: XMLStreamReader): SelectionCriteriaConfig {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "selection-criteria", SELECTION_CRITERIA_ATTRIBUTES)
        val unannotatedStr = reader.getAttributeValue(null, "unannotated")
        val unannotated =
            if (unannotatedStr != null) {
                validateEnum(
                    lineNumber,
                    "selection-criteria",
                    "unannotated",
                    unannotatedStr,
                    EFFECT_MAP,
                    "SelectionCriteriaEffectType",
                )
            } else {
                null
            }

        val annotationRules = mutableListOf<AnnotationRuleConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "selection-criteria")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "annotation-rule"
                    ) {
                        parseAnnotationRule(reader)?.let { annotationRules.add(it) }
                    } else {
                        recordUnexpectedElement(reader, "selection-criteria")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT ->
                    return SelectionCriteriaConfig(
                        unannotated = unannotated,
                        annotationRules = annotationRules,
                    )
            }
        }
        return SelectionCriteriaConfig(
            unannotated = unannotated,
            annotationRules = annotationRules,
        )
    }

    /** Parse an `<annotation-rule>` element from [reader] into an [AnnotationRuleConfig]. */
    private fun parseAnnotationRule(reader: XMLStreamReader): AnnotationRuleConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "annotation-rule", ANNOTATION_RULE_ATTRIBUTES)
        val pattern = requiredAttribute(reader, lineNumber, "annotation-rule", "pattern")
        val effectStr = reader.getAttributeValue(null, "effect")
        val recursive =
            optionalBooleanAttribute(reader, lineNumber, "annotation-rule", "recursive", true)
        expectEmptyElement(reader, "annotation-rule")

        if (pattern == null || recursive == null) return null
        val effect =
            if (effectStr != null) {
                validateEnum(
                    lineNumber,
                    "annotation-rule",
                    "effect",
                    effectStr,
                    EFFECT_MAP,
                    "SelectionCriteriaEffectType",
                ) ?: return null
            } else {
                EffectConfig.SHOW
            }
        return AnnotationRuleConfig(
            pattern = pattern,
            effect = effect,
            recursive = recursive,
        )
    }

    /**
     * Parse an API variant type rule element (`<doc-only>` or `<removed>`) named [elementName] from
     * [reader] into an [ApiVariantTypeRuleConfig].
     */
    private fun parseApiVariantTypeRule(
        reader: XMLStreamReader,
        elementName: String,
    ): ApiVariantTypeRuleConfig {
        checkAttributes(reader, elementName, allowedAttributes = emptySet())
        val annotationRules = mutableListOf<AnnotationPatternRuleConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, elementName)) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "annotation-rule"
                    ) {
                        parseAnnotationPatternRule(reader)?.let { annotationRules.add(it) }
                    } else {
                        recordUnexpectedElement(reader, elementName)
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return ApiVariantTypeRuleConfig(annotationRules)
            }
        }
        return ApiVariantTypeRuleConfig(annotationRules)
    }

    /**
     * Parse an `<annotation-rule>` pattern element inside `<doc-only>` or `<removed>` from [reader]
     * into an [AnnotationPatternRuleConfig].
     */
    private fun parseAnnotationPatternRule(reader: XMLStreamReader): AnnotationPatternRuleConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "annotation-rule", ANNOTATION_PATTERN_RULE_ATTRIBUTES)
        val pattern = requiredAttribute(reader, lineNumber, "annotation-rule", "pattern")
        expectEmptyElement(reader, "annotation-rule")

        if (pattern == null) return null
        return AnnotationPatternRuleConfig(pattern = pattern)
    }

    /** Parse an `<api-flags>` element from [reader] into an [ApiFlagsConfig]. */
    private fun parseApiFlags(
        reader: XMLStreamReader,
        apiFlagKeys: MutableSet<String>,
    ): ApiFlagsConfig {
        checkAttributes(reader, "api-flags", allowedAttributes = emptySet())
        var unknownFlags: UnknownApiFlagsConfig? = null
        val flags = mutableListOf<ApiFlagConfig>()
        var seenApiFlag = false
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "api-flags")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI != CONFIG_NAMESPACE) {
                        recordUnexpectedElement(reader, "api-flags")
                        skipElement(reader)
                        continue
                    }
                    when (reader.localName) {
                        "unknown-flags" -> {
                            if (unknownFlags != null || seenApiFlag) {
                                recordUnexpectedElement(reader, "api-flags")
                                skipElement(reader)
                            } else {
                                unknownFlags = parseUnknownFlags(reader)
                            }
                        }
                        "api-flag" -> {
                            seenApiFlag = true
                            parseApiFlag(reader, apiFlagKeys)?.let { flags.add(it) }
                        }
                        else -> {
                            recordUnexpectedElement(reader, "api-flags")
                            skipElement(reader)
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT ->
                    return ApiFlagsConfig(unknownFlags = unknownFlags, flags = flags)
            }
        }
        return ApiFlagsConfig(unknownFlags = unknownFlags, flags = flags)
    }

    /** Parse an `<unknown-flags>` element from [reader] into an [UnknownApiFlagsConfig]. */
    private fun parseUnknownFlags(reader: XMLStreamReader): UnknownApiFlagsConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "unknown-flags", UNKNOWN_FLAGS_ATTRIBUTES)
        val mutabilityStr = requiredAttribute(reader, lineNumber, "unknown-flags", "mutability")
        val statusStr = requiredAttribute(reader, lineNumber, "unknown-flags", "status")
        expectEmptyElement(reader, "unknown-flags")

        if (mutabilityStr == null || statusStr == null) return null
        val mutability =
            validateEnum(
                lineNumber,
                "unknown-flags",
                "mutability",
                mutabilityStr,
                MUTABILITY_MAP,
                "Mutability",
            )
        val status =
            validateEnum(lineNumber, "unknown-flags", "status", statusStr, STATUS_MAP, "Status")
        if (mutability == null || status == null) return null
        return UnknownApiFlagsConfig(mutability = mutability, status = status)
    }

    /** Parse an `<api-flag>` element from [reader] into an [ApiFlagConfig]. */
    private fun parseApiFlag(
        reader: XMLStreamReader,
        apiFlagKeys: MutableSet<String>,
    ): ApiFlagConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "api-flag", API_FLAG_ATTRIBUTES)
        val pkg = requiredAttribute(reader, lineNumber, "api-flag", "package")
        val name = requiredAttribute(reader, lineNumber, "api-flag", "name")
        val mutabilityStr = requiredAttribute(reader, lineNumber, "api-flag", "mutability")
        val statusStr = requiredAttribute(reader, lineNumber, "api-flag", "status")
        val isExported =
            optionalBooleanAttribute(reader, lineNumber, "api-flag", "is-exported", false)
        expectEmptyElement(reader, "api-flag")

        if (pkg == null || name == null || mutabilityStr == null || statusStr == null) return null
        validatePattern(
            lineNumber,
            "api-flag",
            "package",
            pkg,
            API_FLAG_PACKAGE_REGEX,
            "ApiFlagPackageNameType",
        )
        validatePattern(
            lineNumber,
            "api-flag",
            "name",
            name,
            API_FLAG_NAME_REGEX,
            "ApiFlagNameType"
        )
        val mutability =
            validateEnum(
                lineNumber,
                "api-flag",
                "mutability",
                mutabilityStr,
                MUTABILITY_MAP,
                "Mutability",
            )
        val status = validateEnum(lineNumber, "api-flag", "status", statusStr, STATUS_MAP, "Status")
        checkUniqueKey(lineNumber, apiFlagKeys, "$pkg,$name", "ApiFlagByQualifiedName")
        if (mutability == null || status == null || isExported == null) return null
        return ApiFlagConfig(
            pkg = pkg,
            name = name,
            mutability = mutability,
            status = status,
            isExported = isExported,
        )
    }

    /** Parse a `<build-properties>` element from [reader] into a [BuildPropertiesConfig]. */
    private fun parseBuildProperties(
        reader: XMLStreamReader,
        buildPropertyNames: MutableSet<String>,
    ): BuildPropertiesConfig {
        checkAttributes(reader, "build-properties", allowedAttributes = emptySet())
        val properties = mutableListOf<BuildPropertyConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "build-properties")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "build-property"
                    ) {
                        parseBuildProperty(reader, buildPropertyNames)?.let { properties.add(it) }
                    } else {
                        recordUnexpectedElement(reader, "build-properties")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (properties.isEmpty()) {
                        recordIncompleteElement(
                            reader.location.lineNumber,
                            "build-properties",
                            "build-property",
                        )
                    }
                    return BuildPropertiesConfig(properties)
                }
            }
        }
        return BuildPropertiesConfig(properties)
    }

    /** Parse a `<build-property>` element from [reader] into a [BuildPropertyConfig]. */
    private fun parseBuildProperty(
        reader: XMLStreamReader,
        buildPropertyNames: MutableSet<String>,
    ): BuildPropertyConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "build-property", BUILD_PROPERTY_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "build-property", "name")
        val value = requiredAttribute(reader, lineNumber, "build-property", "value")
        expectEmptyElement(reader, "build-property")

        if (name == null || value == null) return null
        validatePattern(
            lineNumber,
            "build-property",
            "name",
            name,
            BUILD_PROPERTY_NAME_REGEX,
            "BuildPropertyNameType",
        )
        checkUniqueKey(lineNumber, buildPropertyNames, name, "BuildPropertyName")
        return BuildPropertyConfig(name = name, value = value)
    }

    /** Parse an `<issues>` element from [reader] into an [IssuesConfig]. */
    private fun parseIssues(reader: XMLStreamReader): IssuesConfig {
        checkAttributes(reader, "issues", allowedAttributes = emptySet())
        val issues = mutableListOf<IssueConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "issues")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (reader.namespaceURI == CONFIG_NAMESPACE && reader.localName == "issue") {
                        parseIssue(reader)?.let { issues.add(it) }
                    } else {
                        recordUnexpectedElement(reader, "issues")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return IssuesConfig(issues)
            }
        }
        return IssuesConfig(issues)
    }

    /** Parse an `<issue>` element from [reader] into an [IssueConfig]. */
    private fun parseIssue(reader: XMLStreamReader): IssueConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "issue", ISSUE_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "issue", "name")
        val severityStr = requiredAttribute(reader, lineNumber, "issue", "severity")
        expectEmptyElement(reader, "issue")

        if (name == null || severityStr == null) return null
        validatePattern(lineNumber, "issue", "name", name, ISSUE_NAME_REGEX, "IssueNameType")
        val severity =
            validateEnum(
                lineNumber,
                "issue",
                "severity",
                severityStr,
                SEVERITY_MAP,
                "IssueSeverityType",
            ) ?: return null
        return IssueConfig(name = name, severity = severity)
    }

    /** Parse an `<annotation-classes>` element from [reader] into an [AnnotationClassesConfig]. */
    private fun parseAnnotationClasses(
        reader: XMLStreamReader,
        annotationClassNames: MutableSet<String>,
    ): AnnotationClassesConfig {
        checkAttributes(reader, "annotation-classes", allowedAttributes = emptySet())
        val annotationClasses = mutableListOf<AnnotationClassConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "annotation-classes")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "annotation-class"
                    ) {
                        parseAnnotationClass(reader, annotationClassNames)?.let {
                            annotationClasses.add(it)
                        }
                    } else {
                        recordUnexpectedElement(reader, "annotation-classes")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return AnnotationClassesConfig(annotationClasses)
            }
        }
        return AnnotationClassesConfig(annotationClasses)
    }

    /** Parse an `<annotation-class>` element from [reader] into an [AnnotationClassConfig]. */
    private fun parseAnnotationClass(
        reader: XMLStreamReader,
        annotationClassNames: MutableSet<String>,
    ): AnnotationClassConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "annotation-class", ANNOTATION_CLASS_ATTRIBUTES)
        val name = requiredAttribute(reader, lineNumber, "annotation-class", "name")
        val targetsStr = requiredAttribute(reader, lineNumber, "annotation-class", "targets")
        expectEmptyElement(reader, "annotation-class")

        if (name == null || targetsStr == null) return null
        val targets =
            validateEnum(
                lineNumber,
                "annotation-class",
                "targets",
                targetsStr,
                TARGETS_MAP,
                "AnnotationClassTargetsType",
            ) ?: return null
        checkUniqueKey(lineNumber, annotationClassNames, name, "AnnotationClassName")
        return AnnotationClassConfig(name = name, targets = targets)
    }

    /** Parse a `<documentation>` element from [reader] into a [DocumentationConfig]. */
    private fun parseDocumentation(
        reader: XMLStreamReader,
        externalPackagePrefixes: MutableSet<String>,
    ): DocumentationConfig {
        checkAttributes(reader, "documentation", allowedAttributes = emptySet())
        val externalPackages = mutableListOf<ExternalPackageConfig>()
        while (reader.hasNext()) {
            when (nextElementEvent(reader, "documentation")) {
                XMLStreamConstants.START_ELEMENT -> {
                    if (
                        reader.namespaceURI == CONFIG_NAMESPACE &&
                            reader.localName == "external-package"
                    ) {
                        parseExternalPackage(reader, externalPackagePrefixes)?.let {
                            externalPackages.add(it)
                        }
                    } else {
                        recordUnexpectedElement(reader, "documentation")
                        skipElement(reader)
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return DocumentationConfig(externalPackages)
            }
        }
        return DocumentationConfig(externalPackages)
    }

    /** Parse an `<external-package>` element from [reader] into an [ExternalPackageConfig]. */
    private fun parseExternalPackage(
        reader: XMLStreamReader,
        externalPackagePrefixes: MutableSet<String>,
    ): ExternalPackageConfig? {
        val lineNumber = reader.location.lineNumber
        checkAttributes(reader, "external-package", EXTERNAL_PACKAGE_ATTRIBUTES)
        val prefix = requiredAttribute(reader, lineNumber, "external-package", "prefix")
        expectEmptyElement(reader, "external-package")

        if (prefix == null) return null
        validatePattern(
            lineNumber,
            "external-package",
            "prefix",
            prefix,
            PACKAGE_PREFIX_REGEX,
            "PackagePrefixType",
        )
        checkUniqueKey(lineNumber, externalPackagePrefixes, prefix, "ExternalPackagePrefix")
        return ExternalPackageConfig(prefix = prefix)
    }

    /** Retrieve a required attribute, recording an error if it is missing. */
    private fun requiredAttribute(
        reader: XMLStreamReader,
        lineNumber: Int,
        elementName: String,
        attrName: String,
    ): String? {
        val value = reader.getAttributeValue(null, attrName)
        if (value == null) {
            recordError(
                lineNumber,
                "cvc-complex-type.4: Attribute '$attrName' must appear on element '$elementName'.",
            )
        }
        return value
    }

    /** Retrieve an optional boolean attribute, returning [defaultValue] if omitted. */
    private fun optionalBooleanAttribute(
        reader: XMLStreamReader,
        lineNumber: Int,
        elementName: String,
        attrName: String,
        defaultValue: Boolean,
    ): Boolean? {
        val value = reader.getAttributeValue(null, attrName) ?: return defaultValue
        return when (value) {
            "true",
            "1" -> true
            "false",
            "0" -> false
            else -> {
                recordError(
                    lineNumber,
                    "cvc-datatype-valid.1.2.1: '$value' is not a valid value for 'boolean'.",
                )
                recordError(
                    lineNumber,
                    "cvc-attribute.3: The value '$value' of attribute '$attrName' on element '$elementName' is not valid with respect to its type, 'boolean'.",
                )
                null
            }
        }
    }

    /**
     * Validate an attribute [value] against [regex], recording XSD-compatible errors on failure.
     */
    private fun validatePattern(
        lineNumber: Int,
        elementName: String,
        attrName: String,
        value: String,
        regex: Regex,
        typeName: String,
    ): Boolean {
        if (!regex.matches(value)) {
            recordError(
                lineNumber,
                "cvc-pattern-valid: Value '$value' is not facet-valid with respect to pattern '${regex.pattern}' for type '$typeName'.",
            )
            recordError(
                lineNumber,
                "cvc-attribute.3: The value '$value' of attribute '$attrName' on element '$elementName' is not valid with respect to its type, '$typeName'.",
            )
            return false
        }
        return true
    }

    /** Validate an attribute [value] against an enum [valuesMap]. */
    private fun <E> validateEnum(
        lineNumber: Int,
        elementName: String,
        attrName: String,
        value: String,
        valuesMap: Map<String, E>,
        typeName: String,
    ): E? {
        val enumValue = valuesMap[value]
        if (enumValue == null) {
            recordError(
                lineNumber,
                "cvc-enumeration-valid: Value '$value' is not facet-valid with respect to enumeration '${valuesMap.keys}'. It must be a value from the enumeration.",
            )
            recordError(
                lineNumber,
                "cvc-attribute.3: The value '$value' of attribute '$attrName' on element '$elementName' is not valid with respect to its type, '$typeName'.",
            )
        }
        return enumValue
    }

    /** Check that [key] has not already been seen in [seenKeys] for [constraintName]. */
    private fun checkUniqueKey(
        lineNumber: Int,
        seenKeys: MutableSet<String>,
        key: String,
        constraintName: String,
    ) {
        if (!seenKeys.add(key)) {
            recordError(
                lineNumber,
                "cvc-identity-constraint.4.2.2: Duplicate key value [$key] declared for identity constraint \"$constraintName\" of element \"config\".",
            )
        }
    }

    /** Record an error when [elementName] is missing a required [expectedChild] element. */
    private fun recordIncompleteElement(
        lineNumber: Int,
        elementName: String,
        expectedChild: String,
    ) {
        recordError(
            lineNumber,
            "cvc-complex-type.2.4.b: The content of element '$elementName' is not complete. One of '{\"$CONFIG_NAMESPACE\":$expectedChild}' is expected.",
        )
    }

    /**
     * Advance [reader] to the next [XMLStreamConstants.START_ELEMENT] or
     * [XMLStreamConstants.END_ELEMENT] inside [elementName], recording an error if any
     * non-whitespace character content is encountered.
     */
    private fun nextElementEvent(reader: XMLStreamReader, elementName: String): Int {
        while (reader.hasNext()) {
            when (val event = reader.next()) {
                XMLStreamConstants.START_ELEMENT,
                XMLStreamConstants.END_ELEMENT -> return event
                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.CDATA -> {
                    if (!reader.isWhiteSpace && reader.text.isNotBlank()) {
                        recordError(
                            reader.location.lineNumber,
                            "cvc-complex-type.2.3: Element '$elementName' cannot have character [children], because the type's content type is element-only.",
                        )
                    }
                }
            }
        }
        return XMLStreamConstants.END_DOCUMENT
    }

    /** Consume an element that must not have any child elements or character content. */
    private fun expectEmptyElement(reader: XMLStreamReader, elementName: String) {
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    recordUnexpectedElement(reader, elementName)
                    skipElement(reader)
                }
                XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.CDATA -> {
                    if (!reader.isWhiteSpace && reader.text.isNotBlank()) {
                        recordError(
                            reader.location.lineNumber,
                            "cvc-complex-type.2.1: Element '$elementName' must have no character or element information item [children], because the type's content type is empty.",
                        )
                    }
                }
                XMLStreamConstants.END_ELEMENT -> return
            }
        }
    }

    /** Check that all attributes on the current element are in [allowedAttributes]. */
    private fun checkAttributes(
        reader: XMLStreamReader,
        elementName: String,
        allowedAttributes: Set<String>,
        allowSchemaLocation: Boolean = false,
    ) {
        val lineNumber = reader.location.lineNumber
        for (i in 0 until reader.attributeCount) {
            val attrNs = reader.getAttributeNamespace(i)
            val attrLocal = reader.getAttributeLocalName(i)
            if (
                allowSchemaLocation &&
                    attrNs == XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI &&
                    attrLocal == "schemaLocation"
            ) {
                continue
            }
            if (!attrNs.isNullOrEmpty() || attrLocal !in allowedAttributes) {
                val attrName = formatQName(reader.getAttributePrefix(i), attrLocal)
                recordError(
                    lineNumber,
                    "cvc-complex-type.3.2.2: Attribute '$attrName' is not allowed to appear in element '$elementName'.",
                )
            }
        }
    }

    /** Record an error for an unexpected child element inside [parentElement]. */
    private fun recordUnexpectedElement(reader: XMLStreamReader, parentElement: String) {
        val qName = formatQName(reader.prefix, reader.localName)
        recordError(
            reader.location.lineNumber,
            "cvc-complex-type.2.4.a: Invalid content was found starting with element '$qName' in '$parentElement'.",
        )
    }

    /** Skip the current element and all of its descendants. */
    private fun skipElement(reader: XMLStreamReader) {
        var depth = 1
        while (depth > 0 && reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> depth++
                XMLStreamConstants.END_ELEMENT -> depth--
            }
        }
    }

    /** Format an XML qualified name from an optional [prefix] and [localName]. */
    private fun formatQName(prefix: String?, localName: String): String =
        if (prefix.isNullOrEmpty()) localName else "$prefix:$localName"

    companion object : ConfigParser {
        private val API_SURFACE_ATTRIBUTES = setOf("name", "extends", "contents")
        private val API_SURFACE_NAME_REGEX = Regex("[a-z-]+")
        private val CONTENTS_MAP = ContentsConfig.entries.associateBy { it.configFileValue }
        private val SELECTION_CRITERIA_ATTRIBUTES = setOf("unannotated")
        private val ANNOTATION_RULE_ATTRIBUTES = setOf("pattern", "effect", "recursive")
        private val ANNOTATION_PATTERN_RULE_ATTRIBUTES = setOf("pattern")
        private val EFFECT_MAP = EffectConfig.entries.associateBy { it.configFileValue }
        private val UNKNOWN_FLAGS_ATTRIBUTES = setOf("mutability", "status")
        private val API_FLAG_ATTRIBUTES =
            setOf("package", "name", "mutability", "status", "is-exported")
        private val API_FLAG_PACKAGE_REGEX = Regex("[a-z0-9_.]+")
        private val API_FLAG_NAME_REGEX = Regex("[a-z0-9_]+")
        private val MUTABILITY_MAP =
            ApiFlagActionConfig.Mutability.entries.associateBy { it.configFileValue }
        private val STATUS_MAP =
            ApiFlagActionConfig.Status.entries.associateBy { it.configFileValue }
        private val BUILD_PROPERTY_ATTRIBUTES = setOf("name", "value")
        private val BUILD_PROPERTY_NAME_REGEX = Regex("[a-zA-Z0-9_]+")
        private val ISSUE_ATTRIBUTES = setOf("name", "severity")
        private val ISSUE_NAME_REGEX = Regex("([A-Z][a-z0-9]*)+")
        private val SEVERITY_MAP =
            IssueConfig.SeverityConfig.entries.associateBy { it.configFileValue }
        private val ANNOTATION_CLASS_ATTRIBUTES = setOf("name", "targets")
        private val TARGETS_MAP =
            AnnotationClassConfig.TargetsConfig.entries.associateBy { it.configFileValue }
        private val EXTERNAL_PACKAGE_ATTRIBUTES = setOf("prefix")
        private val PACKAGE_PREFIX_REGEX =
            Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*")

        /** Parse a list of configuration files in order, returning a single [Config] object. */
        override fun parse(files: List<File>): Config {
            if (files.isEmpty()) return Config()
            return parseInputSources(files.map { InputSource(it.path) })
        }

        /**
         * Parse a list of configuration [InputSource]s in order, returning a single [Config]
         * object.
         */
        override fun parseInputSources(inputSources: List<InputSource>): Config {
            if (inputSources.isEmpty()) return Config()

            val xmlInputFactory =
                XMLInputFactory.newDefaultFactory().apply {
                    setProperty(XMLInputFactory.SUPPORT_DTD, false)
                    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
                }
            val allErrors = StringBuilder()
            val configs = mutableListOf<Config>()

            for (inputSource in inputSources) {
                val systemId = inputSource.systemId ?: ""
                val parser = StaxConfigParser(systemId)
                try {
                    val charStream = inputSource.characterStream
                    val byteStream = inputSource.byteStream
                    when {
                        charStream != null -> {
                            val reader = xmlInputFactory.createXMLStreamReader(systemId, charStream)
                            try {
                                configs.add(parser.parseFile(reader))
                            } finally {
                                reader.close()
                            }
                        }
                        byteStream != null -> {
                            val reader = xmlInputFactory.createXMLStreamReader(systemId, byteStream)
                            try {
                                configs.add(parser.parseFile(reader))
                            } finally {
                                reader.close()
                            }
                        }
                        else -> {
                            File(systemId).bufferedReader().use { fileReader ->
                                val reader =
                                    xmlInputFactory.createXMLStreamReader(systemId, fileReader)
                                try {
                                    configs.add(parser.parseFile(reader))
                                } finally {
                                    reader.close()
                                }
                            }
                        }
                    }
                } catch (e: XMLStreamException) {
                    parser.recordStreamException(e)
                } catch (e: Exception) {
                    parser.recordError(-1, e.message ?: "")
                }
                allErrors.append(parser.errors)
            }

            if (allErrors.isNotEmpty()) {
                error("Errors found while parsing configuration file(s):\n$allErrors")
            }

            return configs.reduceOrNull(Config::combineWith)?.apply { validate() } ?: Config()
        }

        override fun toString(): String = "stax"
    }
}
