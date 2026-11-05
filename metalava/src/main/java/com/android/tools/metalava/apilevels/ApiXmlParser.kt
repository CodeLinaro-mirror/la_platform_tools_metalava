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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.SdkExtension
import com.android.tools.metalava.apilevels.ApiXmlPrinter.Companion.DEFAULT_MIN_VERSION
import java.io.File
import java.io.Reader
import java.io.StringReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/**
 * StAX-based parser that reads an `api-versions.xml` file and populates an [Api] instance.
 *
 * This is the inverse of [ApiXmlPrinter]: parsing an `api-versions.xml` file with [ApiXmlParser]
 * and printing the resulting [Api] with [ApiXmlPrinter] produces identical XML output.
 */
object ApiXmlParser {

    /**
     * Parses an `api-versions.xml` [file] into an [Api] instance.
     *
     * @param file the `api-versions.xml` file to parse.
     * @param useInternalNames whether class and method names in [file] use JVM internal format.
     * @return the populated [Api] instance.
     */
    fun parse(file: File, useInternalNames: Boolean = true): Api {
        return file.bufferedReader().use { reader ->
            parse(reader, systemId = file.path, useInternalNames = useInternalNames)
        }
    }

    /**
     * Parses `api-versions.xml` content from [xml] into an [Api] instance.
     *
     * @param xml the XML string to parse.
     * @param useInternalNames whether class and method names in [xml] use JVM internal format.
     * @return the populated [Api] instance.
     */
    fun parse(xml: String, useInternalNames: Boolean = true): Api {
        return StringReader(xml).use { reader ->
            parse(reader, useInternalNames = useInternalNames)
        }
    }

    /**
     * Parses `api-versions.xml` content from [reader] into an [Api] instance.
     *
     * @param reader the character stream to read XML from.
     * @param systemId optional system identifier for error reporting.
     * @param useInternalNames whether class and method names in [reader] use JVM internal format.
     * @return the populated [Api] instance.
     */
    fun parse(
        reader: Reader,
        systemId: String = "",
        useInternalNames: Boolean = true,
    ): Api {
        val xmlInputFactory =
            XMLInputFactory.newDefaultFactory().apply {
                setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
                setProperty(XMLInputFactory.SUPPORT_DTD, false)
                setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            }
        val streamReader = xmlInputFactory.createXMLStreamReader(systemId, reader)
        try {
            return parseStream(streamReader, useInternalNames)
        } finally {
            streamReader.close()
        }
    }

    /** Parses the XML stream from [reader] into an [Api] instance. */
    private fun parseStream(reader: XMLStreamReader, useInternalNames: Boolean): Api {
        val api = Api(useInternalNames)
        val sdkExtensions = mutableSetOf<SdkExtension>()
        var minVersion: ApiVersion = DEFAULT_MIN_VERSION
        var currentClass: ApiClass? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    when (val tagName = reader.localName) {
                        "api" -> {
                            val minAttr = reader.getAttributeValue(null, "min")
                            minVersion =
                                if (minAttr != null) {
                                    parseVersion(api, minAttr)
                                } else {
                                    DEFAULT_MIN_VERSION.also { api.recordVersion(it) }
                                }
                        }
                        "sdk" -> {
                            val id =
                                reader.getAttributeValue(null, "id")?.toInt()
                                    ?: error("Missing id attribute on <sdk>")
                            val shortname = reader.getAttributeValue(null, "shortname") ?: ""
                            val name = reader.getAttributeValue(null, "name") ?: ""
                            val reference = reader.getAttributeValue(null, "reference") ?: ""
                            sdkExtensions.add(
                                SdkExtension.fromXmlAttributes(id, shortname, name, reference)
                            )
                        }
                        "class" -> {
                            val name =
                                reader.getAttributeValue(null, "name")
                                    ?: error("Missing name attribute on <class>")
                            val module = reader.getAttributeValue(null, "module")
                            val since =
                                reader.getAttributeValue(null, "since")?.let {
                                    parseVersion(api, it)
                                } ?: minVersion
                            val sdks = reader.getAttributeValue(null, "sdks")
                            val deprecatedIn =
                                reader.getAttributeValue(null, "deprecated")?.let {
                                    parseVersion(api, it)
                                }
                            val removedIn =
                                reader.getAttributeValue(null, "removed")?.let {
                                    parseVersion(api, it)
                                }
                            val apiClass =
                                ApiClass(name, isEnum = false).apply {
                                    initFromXml(
                                        since = since,
                                        sdks = sdks,
                                        deprecatedIn = deprecatedIn,
                                        removedIn = removedIn,
                                        mainlineModule = module,
                                    )
                                }
                            api.addClass(apiClass)
                            currentClass = apiClass
                        }
                        "extends",
                        "implements" -> {
                            val cls = currentClass ?: error("<$tagName> outside of <class> element")
                            val element =
                                parseMemberElement(
                                    api = api,
                                    reader = reader,
                                    tagName = tagName,
                                    defaultSince = cls.since,
                                    defaultSdks = cls.sdks,
                                    // References to superclasses and interfaces are never
                                    // deprecated, so they do not inherit deprecatedIn from the
                                    // enclosing class.
                                    defaultDeprecatedIn = null,
                                    defaultRemovedIn = cls.removedIn,
                                )
                            if (tagName == "extends") {
                                cls.addSuperClass(element)
                            } else {
                                cls.addInterface(element)
                            }
                        }
                        "method",
                        "field" -> {
                            val cls = currentClass ?: error("<$tagName> outside of <class> element")
                            val element =
                                parseMemberElement(
                                    api = api,
                                    reader = reader,
                                    tagName = tagName,
                                    defaultSince = cls.since,
                                    defaultSdks = cls.sdks,
                                    defaultDeprecatedIn = cls.deprecatedIn,
                                    defaultRemovedIn = cls.removedIn,
                                )
                            if (tagName == "method") {
                                cls.addMethod(element)
                            } else {
                                cls.addField(element)
                            }
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "class") {
                        currentClass = null
                    }
                }
            }
        }

        if (sdkExtensions.isNotEmpty()) {
            api.availableSdkExtensions = AvailableSdkExtensions(sdkExtensions)
        }

        return api
    }

    /**
     * Parses a child element of `<class>` (`<extends>`, `<implements>`, `<method>`, or `<field>`),
     * inheriting omitted attributes from the given defaults.
     */
    private fun parseMemberElement(
        api: Api,
        reader: XMLStreamReader,
        tagName: String,
        defaultSince: ApiVersion,
        defaultSdks: String?,
        defaultDeprecatedIn: ApiVersion?,
        defaultRemovedIn: ApiVersion?,
    ): ApiElement {
        val name =
            reader.getAttributeValue(null, "name") ?: error("Missing name attribute on <$tagName>")
        val since =
            reader.getAttributeValue(null, "since")?.let { parseVersion(api, it) } ?: defaultSince
        val sdks = reader.getAttributeValue(null, "sdks") ?: defaultSdks
        val deprecatedIn =
            reader.getAttributeValue(null, "deprecated")?.let { parseVersion(api, it) }
                ?: defaultDeprecatedIn
        val removedIn =
            reader.getAttributeValue(null, "removed")?.let { parseVersion(api, it) }
                ?: defaultRemovedIn
        return ApiElement(name).apply {
            initFromXml(
                since = since,
                sdks = sdks,
                deprecatedIn = deprecatedIn,
                removedIn = removedIn,
            )
        }
    }

    /** Parses an [ApiVersion] from [value] and records it on [api]. */
    private fun parseVersion(api: Api, value: String): ApiVersion {
        return ApiVersion.fromString(value).also { api.recordVersion(it) }
    }
}
