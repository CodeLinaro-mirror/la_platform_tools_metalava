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

import com.android.tools.metalava.SdkIdentifier
import java.io.PrintWriter

/** Printer that will write an XML representation of an [Api] instance. */
class ApiXmlPrinter(
    private val sdkIdentifiers: Set<SdkIdentifier>,
    private val firstApiLevel: Int,
) : ApiPrinter {
    override fun print(api: Api, writer: PrintWriter) {
        writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        api.print(writer, sdkIdentifiers)
    }

    /**
     * Prints the whole API definition to a writer.
     *
     * @param writer the writer to which the XML elements will be written.
     */
    private fun Api.print(writer: PrintWriter, sdkIdentifiers: Set<SdkIdentifier>) {
        writer.print("<api version=\"3\"")
        if (firstApiLevel > 1) {
            writer.print(" min=\"$firstApiLevel\"")
        }
        writer.println(">")
        for ((id, shortname, name, reference) in sdkIdentifiers) {
            writer.println(
                String.format(
                    "\t<sdk id=\"%d\" shortname=\"%s\" name=\"%s\" reference=\"%s\"/>",
                    id,
                    shortname,
                    name,
                    reference
                )
            )
        }
        print(classes, "class", "\t", writer)
        printClosingTag("api", "", writer)
    }

    /**
     * Prints homogeneous XML elements to a writer. Each element is printed on a separate line.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param elements the elements to print
     * @param tag the tag of the XML elements
     * @param indent the whitespace prefix to insert before each XML element
     * @param writer the writer to which the XML elements will be written.
     */
    private fun ApiElement.print(
        elements: Collection<ApiElement>,
        tag: String?,
        indent: String,
        writer: PrintWriter
    ) {
        for (element in elements.sorted()) {
            element.print(tag, this, indent, writer)
        }
    }

    /**
     * Prints an XML representation of the element to a writer terminated by a line break.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param tag the tag of the XML element
     * @param parentElement the parent API element
     * @param indent the whitespace prefix to insert before the XML element
     * @param writer the writer to which the XML element will be written.
     */
    private fun ApiElement.print(
        tag: String?,
        parentElement: ApiElement,
        indent: String,
        writer: PrintWriter
    ) {
        if (this is ApiClass) printClass(tag, parentElement, indent, writer)
        else print(tag, true, parentElement, indent, writer)
    }

    private fun ApiClass.printClass(
        tag: String?,
        parentElement: ApiElement,
        indent: String,
        writer: PrintWriter
    ) {
        if (alwaysHidden) {
            return
        }
        print(tag, false, parentElement, indent, writer)
        val innerIndent = indent + '\t'
        print(superClasses, "extends", innerIndent, writer)
        print(interfaces, "implements", innerIndent, writer)
        print(methods, "method", innerIndent, writer)
        print(fields, "field", innerIndent, writer)
        printClosingTag(tag, indent, writer)
    }

    /**
     * Prints an XML representation of the element to a writer terminated by a line break.
     * Attributes with values matching the parent API element are omitted.
     *
     * @param tag the tag of the XML element
     * @param closeTag if true the XML element is terminated by "/>", otherwise the closing tag of
     *   the element is not printed
     * @param parentElement the parent API element
     * @param indent the whitespace prefix to insert before the XML element
     * @param writer the writer to which the XML element will be written.
     * @see printClosingTag
     */
    private fun ApiElement.print(
        tag: String?,
        closeTag: Boolean,
        parentElement: ApiElement,
        indent: String?,
        writer: PrintWriter
    ) {
        writer.print(indent)
        writer.print('<')
        writer.print(tag)
        writer.print(" name=\"")
        writer.print(encodeAttribute(name))
        if (!isEmpty(mainlineModule) && !isEmpty(sdks)) {
            writer.print("\" module=\"")
            writer.print(encodeAttribute(mainlineModule!!))
        }
        if (since > parentElement.since) {
            writer.print("\" since=\"")
            writer.print(since)
        }
        if (!isEmpty(sdks) && sdks != parentElement.sdks) {
            writer.print("\" sdks=\"")
            writer.print(sdks)
        }
        if (deprecatedIn != null && deprecatedIn != parentElement.deprecatedIn) {
            writer.print("\" deprecated=\"")
            writer.print(deprecatedIn)
        }
        if (lastPresentIn < parentElement.lastPresentIn) {
            writer.print("\" removed=\"")
            writer.print(lastPresentIn + 1)
        }
        writer.print('"')
        if (closeTag) {
            writer.print('/')
        }
        writer.println('>')
    }

    companion object {
        /**
         * Prints a closing tag of an XML element terminated by a line break.
         *
         * @param tag the tag of the element
         * @param indent the whitespace prefix to insert before the closing tag
         * @param writer the writer to which the XML element will be written.
         */
        private fun printClosingTag(tag: String?, indent: String?, writer: PrintWriter) {
            writer.print(indent)
            writer.print("</")
            writer.print(tag)
            writer.println('>')
        }

        private fun encodeAttribute(attribute: String): String {
            return buildString {
                val n = attribute.length
                // &, ", ' and < are illegal in attributes; see
                // http://www.w3.org/TR/REC-xml/#NT-AttValue
                // (' legal in a " string and " is legal in a ' string but here we'll stay on the
                // safe
                // side).
                for (i in 0 until n) {
                    when (val c = attribute[i]) {
                        '"' -> {
                            append("&quot;") // $NON-NLS-1$
                        }
                        '<' -> {
                            append("&lt;") // $NON-NLS-1$
                        }
                        '\'' -> {
                            append("&apos;") // $NON-NLS-1$
                        }
                        '&' -> {
                            append("&amp;") // $NON-NLS-1$
                        }
                        else -> {
                            append(c)
                        }
                    }
                }
            }
        }

        private fun isEmpty(s: String?): Boolean {
            return s.isNullOrEmpty()
        }
    }
}
