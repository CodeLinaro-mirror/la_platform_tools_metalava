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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.SdkExtension
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Encapsulates information read from the `sdk-extension-info.xml` file. */
class SdkExtensionInfo(
    /** Information retrieved from `<sdk>` elements. */
    val availableSdkExtensions: AvailableSdkExtensions,

    /** Information retrieved from `<symbol>` elements, organized by jar. */
    private val extensionsByJar: Map<String, ApiToExtensionsMap>,
) {
    /**
     * An empty [ApiToExtensionsMap], returned from [extensionsMapForJarOrEmpty] if a jar specific
     * map could not be found.
     *
     * This is given [availableSdkExtensions] as it will be used to check the validity of sdk names
     * even if there is no information about the symbols.
     */
    private val empty = ApiToExtensionsMap(availableSdkExtensions, Node("<empty>"))

    /**
     * Get the [ApiToExtensionsMap] for [jar], returning an empty map if no specific map for [jar]
     * could be found.
     */
    fun extensionsMapForJarOrEmpty(jar: String) = extensionsByJar[jar] ?: empty

    companion object {
        /**
         * Create an ApiToExtensionsMap from a list of text based rules.
         *
         * The input is XML:
         *
         *     <?xml version="1.0" encoding="utf-8"?>
         *     <sdk-extensions-info version="1">
         *         <sdk name="<name>" shortname="<short-name>" id="<int>" reference="<constant>" />
         *         <symbol jar="<jar>" pattern="<pattern>" sdks="<sdks>" />
         *     </sdk-extensions-info>
         *
         * The <sdk> and <symbol> tags may be repeated.
         * - <name> is a long name for the SDK, e.g. "R Extensions".
         * - <short-name> is a short name for the SDK, e.g. "R-ext".
         * - <id> is the numerical identifier for the SDK, e.g. 30. It is an error to use the
         *   Android SDK ID (0).
         * - <jar> is the jar file symbol belongs to, named after the jar file in
         *   prebuilts/sdk/extensions/<int>/public, e.g. "framework-sdkextensions".
         * - <constant> is a Java symbol that can be passed to `SdkExtensions.getExtensionVersion`
         *   to look up the version of the corresponding SDK, e.g.
         *   "android/os/Build$VERSION_CODES$R"
         * - <pattern> is either '*', which matches everything, or a 'com.foo.Bar$Inner#member'
         *   string (or prefix thereof terminated before . or $), which matches anything with that
         *   prefix. Note that arguments and return values of methods are omitted (and there is no
         *   way to distinguish overloaded methods).
         * - <sdks> is a comma separated list of SDKs in which the symbol defined by <jar> and
         *   <pattern> appears; the list items are <name> attributes of SDKs defined in the XML.
         *
         * It is an error to specify the same <jar> and <pattern> pair twice.
         *
         * A more specific <symbol> rule has higher precedence than a less specific rule.
         *
         * @param filterByJar jar file to limit lookups to: ignore symbols not present in this jar
         *   file
         * @param xml XML as described above
         * @throws IllegalArgumentException if the XML is malformed
         */
        fun fromXml(xml: String): SdkExtensionInfo {
            val sdkExtensions = mutableSetOf<SdkExtension>()
            val allSeenExtensions = mutableSetOf<String>()

            // Map from jar name to the root node.
            val jarToRoot = mutableMapOf<String, Node>()

            val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
            try {
                parser.parse(
                    xml.byteInputStream(),
                    object : DefaultHandler() {
                        override fun startElement(
                            uri: String,
                            localName: String,
                            qualifiedName: String,
                            attributes: Attributes
                        ) {
                            when (qualifiedName) {
                                "sdk" -> {
                                    val id = attributes.getIntOrThrow(qualifiedName, "id")
                                    val shortname =
                                        attributes.getStringOrThrow(qualifiedName, "shortname")
                                    val name = attributes.getStringOrThrow(qualifiedName, "name")
                                    val reference =
                                        attributes.getStringOrThrow(qualifiedName, "reference")
                                    sdkExtensions.add(
                                        SdkExtension.fromXmlAttributes(
                                            id,
                                            shortname,
                                            name,
                                            reference,
                                        )
                                    )
                                }
                                "symbol" -> {
                                    val jar = attributes.getStringOrThrow(qualifiedName, "jar")
                                    // Get the root node for the jar, creating one if needed.
                                    val rootForJar =
                                        jarToRoot.computeIfAbsent(jar) { Node("<jar $jar>") }

                                    val sdks =
                                        attributes
                                            .getStringOrThrow(qualifiedName, "sdks")
                                            .split(',')
                                    if (sdks != sdks.distinct()) {
                                        throw IllegalArgumentException(
                                            "symbol lists the same SDK multiple times: '$sdks'"
                                        )
                                    }
                                    allSeenExtensions.addAll(sdks)
                                    val pattern =
                                        attributes.getStringOrThrow(qualifiedName, "pattern")
                                    if (pattern == "*") {
                                        rootForJar.extensions = sdks
                                        return
                                    }
                                    // pattern is com.example.Foo, add nodes:
                                    //     "com" -> "example" -> "Foo"
                                    val parts = pattern.splitIntoBreadcrumbs()
                                    var node = rootForJar
                                    for (name in parts) {
                                        node = node.children.addNode(name)
                                    }
                                    if (node.extensions.isNotEmpty()) {
                                        throw IllegalArgumentException(
                                            "duplicate pattern: $pattern"
                                        )
                                    }
                                    node.extensions = sdks
                                }
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                throw IllegalArgumentException("failed to parse xml", e)
            }

            val availableSdkExtensions = AvailableSdkExtensions(sdkExtensions)

            // verify: all rules refer to declared SDKs
            for (ext in allSeenExtensions) {
                if (!availableSdkExtensions.containsSdkExtension(ext)) {
                    throw IllegalArgumentException("bad SDK definitions: undefined SDK $ext")
                }
            }

            // Transform the map from jar to root node into a map from jar to ApiToExtensionsMap.
            val extensionsByJar =
                jarToRoot.entries.associate { (jar, root) ->
                    jar to ApiToExtensionsMap(availableSdkExtensions, root)
                }
            return SdkExtensionInfo(availableSdkExtensions, extensionsByJar)
        }
    }
}

private fun MutableSet<Node>.addNode(name: String): Node {
    findNode(name)?.let {
        return it
    }
    val node = Node(name)
    add(node)
    return node
}

private fun Attributes.getStringOrThrow(tag: String, attr: String): String =
    getValue(attr) ?: throw IllegalArgumentException("<$tag>: missing attribute: $attr")

private fun Attributes.getIntOrThrow(tag: String, attr: String): Int =
    getStringOrThrow(tag, attr).toIntOrNull()
        ?: throw IllegalArgumentException("<$tag>: attribute $attr: not an integer")
