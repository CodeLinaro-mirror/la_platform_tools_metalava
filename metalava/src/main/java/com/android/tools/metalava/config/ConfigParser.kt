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

import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.SchemaFactory
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

/** Parser for XML configuration files. */
class ConfigParser(private val reporter: Reporter) : DefaultHandler() {

    private fun reportParseException(exception: SAXParseException, severity: Severity) {
        val systemIdAsURI = URI.create(exception.systemId)
        val location = FileLocation.createLocation(Paths.get(systemIdAsURI), exception.lineNumber)
        reporter.report(
            Issues.CONFIG_FILE_PROBLEM,
            null,
            "Problem parsing configuration file: ${exception.message}",
            location,
            // The issue has a severity of ERROR, this limits it to whatever is required by the
            // caller.
            maximumSeverity = severity,
        )
    }

    override fun warning(exception: SAXParseException) {
        reportParseException(exception, Severity.WARNING_ERROR_WHEN_NEW)
    }

    override fun error(exception: SAXParseException) {
        reportParseException(exception, Severity.ERROR)
    }

    override fun fatalError(exception: SAXParseException) {
        reportParseException(exception, Severity.ERROR)
    }

    companion object {
        /** Parse a list of configuration files in order, returning a single [Config] object. */
        fun parse(reporter: Reporter, files: List<File>): Config {
            val schemaUrl = ConfigParser::class.java.getResource("/schemas/config.xsd")
            val schemafactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            val schema = schemafactory.newSchema(schemaUrl)

            val saxParserFactory = SAXParserFactory.newNSInstance()
            saxParserFactory.schema = schema
            val saxParser = saxParserFactory.newSAXParser()
            val configParser = ConfigParser(reporter)
            for (file in files) {
                saxParser.parse(file, configParser)
            }
            return Config()
        }
    }
}
