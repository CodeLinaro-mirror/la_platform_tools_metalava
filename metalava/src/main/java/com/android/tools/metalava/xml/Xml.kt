/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.xml

import java.io.PrintWriter
import java.io.Reader
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

const val AMP_ENTITY = "&amp;"
const val APOS_ENTITY = "&apos;"
const val GT_ENTITY = "&gt;"
const val LT_ENTITY = "&lt;"
const val QUOT_ENTITY = "&quot;"
private const val NEWLINE_ENTITY = "&#xA;"

// XML parser features
private const val LOAD_EXTERNAL_DTD =
    "http://apache.org/xml/features/nonvalidating/load-external-dtd"
private const val EXTERNAL_PARAMETER_ENTITIES =
    "http://xml.org/sax/features/external-parameter-entities"
private const val EXTERNAL_GENERAL_ENTITIES =
    "http://xml.org/sax/features/external-general-entities"

/**
 * Parses the given XML string as a DOM document, using the JDK parser. The parser does not
 * validate, and is optionally namespace aware.
 *
 * @param xml the XML content to be parsed (must be well-formed)
 * @param namespaceAware whether the parser is namespace aware
 * @param errorWriter the writer to write errors to
 * @return the DOM document
 */
fun parseDocument(
    xml: String,
    namespaceAware: Boolean,
    errorWriter: PrintWriter = PrintWriter(System.out)
): Document {
    return parseDocument(StringReader(stripBom(xml)), namespaceAware, errorWriter)
}

/** Strips a leading UTF-8 BOM (`\uFEFF`) if present. */
private fun stripBom(xml: String): String =
    if (xml.isNotEmpty() && xml[0] == '\uFEFF') xml.substring(1) else xml

/**
 * Parses the given [Reader] as a DOM document, using the JDK parser. The parser does not validate,
 * and is optionally namespace aware.
 *
 * @param xml a reader for the XML content to be parsed (must be well-formed)
 * @param namespaceAware whether the parser is namespace aware
 * @param errorWriter the writer to write errors to
 * @return the DOM document
 */
fun parseDocument(
    xml: Reader,
    namespaceAware: Boolean,
    errorWriter: PrintWriter = PrintWriter(System.out)
): Document {
    val inputStream = InputSource(xml)
    val builder = createDocumentBuilder(namespaceAware)
    builder.setErrorHandler(
        object : ErrorHandler {
            override fun warning(exception: SAXParseException) {
                errorWriter.println("${exception.lineNumber}: Warning: ${exception.message}")
            }

            override fun error(exception: SAXParseException) {
                errorWriter.println("${exception.lineNumber}: Error: ${exception.message}")
            }

            override fun fatalError(exception: SAXParseException) {
                error(exception)
            }
        }
    )
    return builder.parse(inputStream)
}

/** Creates a preconfigured document builder. */
private fun createDocumentBuilder(namespaceAware: Boolean): DocumentBuilder {
    try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = namespaceAware
        factory.isValidating = false
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false)
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false)
        factory.setFeature(LOAD_EXTERNAL_DTD, false)
        return factory.newDocumentBuilder()
    } catch (e: ParserConfigurationException) {
        throw Error(e) // Impossible in the current context.
    }
}

/** Returns the list of child [Element] nodes of [node]. */
fun getChildren(node: Node): List<Element> {
    val childNodes = node.childNodes
    val children = ArrayList<Element>(childNodes.length)
    for (i in 0 until childNodes.length) {
        val child = childNodes.item(i)
        if (child.nodeType == Node.ELEMENT_NODE) {
            children.add(child as Element)
        }
    }
    return children
}

/** Returns the first child [Element] of [parent] with the given tag [name], or `null`. */
fun getFirstSubTagByName(parent: Node?, name: String): Element? =
    findTagByName(parent?.firstChild, name)

/** Returns the next sibling [Element] after [node] with the given tag [name], or `null`. */
fun getNextTagByName(node: Node?, name: String): Element? = findTagByName(node?.nextSibling, name)

private fun findTagByName(start: Node?, name: String): Element? {
    var curr = start
    while (curr != null) {
        if (curr.nodeType == Node.ELEMENT_NODE) {
            val currName = curr.localName ?: curr.nodeName
            if (currName == name) {
                return curr as Element
            }
        }
        curr = curr.nextSibling
    }
    return null
}

/** Escapes an attribute value for inclusion inside a double-quoted XML attribute. */
fun toXmlAttributeValue(attrValue: String): String {
    var prev = 0.toChar()
    for (i in attrValue.indices) {
        val c = attrValue[i]
        if (
            c == '"' || c == '\'' || c == '<' || c == '&' || c == '\n' || (c == '>' && prev == ']')
        ) {
            return buildString(attrValue.length * 2) {
                append(attrValue, 0, i)
                prev = if (i > 0) attrValue[i - 1] else 0.toChar()
                for (j in i until attrValue.length) {
                    val ch = attrValue[j]
                    when {
                        ch == '"' -> append(QUOT_ENTITY)
                        ch == '<' -> append(LT_ENTITY)
                        ch == '\'' -> append(APOS_ENTITY)
                        ch == '&' -> append(AMP_ENTITY)
                        ch == '\n' -> append(NEWLINE_ENTITY)
                        ch == '>' && prev == ']' -> append(GT_ENTITY)
                        else -> append(ch)
                    }
                    prev = ch
                }
            }
        }
        prev = c
    }
    return attrValue
}
