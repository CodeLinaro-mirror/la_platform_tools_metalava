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

/** Implemented by configuration objects that can be serialized to XML. */
sealed interface ConfigXmlWritable {
    /** Write this configuration object to [writer]. */
    fun writeTo(writer: ConfigWriter)
}

/** Format [this] as XML in the same format as [ConfigParser] reads. */
fun ConfigXmlWritable.toConfigXml(indent: String = ""): String {
    val writer = ConfigWriter(indent)
    writeTo(writer)
    return writer.toString().trimEnd()
}

/** Helper for writing [ConfigXmlWritable] objects to indented XML. */
class ConfigWriter internal constructor(private var currentIndent: String = "") {
    private val buffer = StringBuilder()
    private var depth = 0
    private var openTagPendingClose = false

    /** Write an XML element with [name], optional [attributes], and optional child [body]. */
    internal fun element(
        name: String,
        attributes: ConfigWriter.() -> Unit = {},
        body: (ConfigWriter.() -> Unit)? = null,
    ) {
        if (openTagPendingClose) {
            buffer.append(">\n")
            openTagPendingClose = false
        }

        val elementIndent = currentIndent
        buffer.append(elementIndent).append('<').append(name)
        if (depth == 0) {
            attribute("xmlns", CONFIG_NAMESPACE)
        }
        attributes()

        if (body == null) {
            buffer.append("/>\n")
        } else {
            openTagPendingClose = true
            depth++
            currentIndent = "$elementIndent  "
            body()
            depth--
            currentIndent = elementIndent

            if (openTagPendingClose) {
                buffer.append("/>\n")
                openTagPendingClose = false
            } else {
                buffer.append(elementIndent).append("</").append(name).append(">\n")
            }
        }
    }

    /** Write an XML attribute with [name] and [value] if [value] is non-null. */
    internal fun attribute(name: String, value: Any?) {
        if (value == null) return
        buffer.append(' ').append(name).append("=\"")
        val text = value.toString()
        for (i in 0 until text.length) {
            when (val c = text[i]) {
                '&' -> buffer.append("&amp;")
                '<' -> buffer.append("&lt;")
                '>' -> buffer.append("&gt;")
                '"' -> buffer.append("&quot;")
                else -> buffer.append(c)
            }
        }
        buffer.append('"')
    }

    /** Return the formatted XML string. */
    override fun toString(): String = buffer.toString()
}
