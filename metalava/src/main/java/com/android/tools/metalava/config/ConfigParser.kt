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
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.io.File
import java.net.URI
import java.nio.file.Paths
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.SchemaFactory
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

const val CONFIG_NAMESPACE = "http://www.google.com/tools/metalava/config"

/** Parser for XML configuration files. */
class ConfigParser private constructor(private val reporter: Reporter) : DefaultHandler() {
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
            val xmlMapper = configXmlMapper()
            return files
                .map { file ->

                    // Parse the configuration file to validate against the schema first.
                    saxParser.parse(file, configParser)

                    // Read the configuration file into a Config object.
                    xmlMapper.readValue(file, Config::class.java)
                }
                // Merge the config objects together.
                .reduceOrNull { configLeft, configRight -> merge(configLeft, configRight) }
            // If no configuration files were created then return an empty Config.
            ?: Config()
        }

        /**
         * Get an [XmlMapper] that can be used to serialize and deserialize [Config] objects.
         *
         * While serializing a [Config] object is not something that is used by Metalava it is
         * helpful to be able to do that for debugging and also for development. e.g. it is easy to
         * work out what the [XmlMapper] can read by simply seeing what it writes out as it
         * generally supports reading what it writes. Tweaking it to match what is defined in the
         * schema just requires adding the correct annotations to the object.
         */
        internal fun configXmlMapper(): XmlMapper {
            return XmlMapper.builder()
                // Do not add extra wrapper elements around collections.
                .defaultUseWrapper(false)
                // Pretty print, indenting each level by 2 spaces.
                .enable(SerializationFeature.INDENT_OUTPUT)
                // Exclude any `null` values from being serialized.
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // Add support for using Kotlin data classes.
                .addModule(kotlinModule())
                .build()
        }

        /** Merge two Config objects together returning an object that combines them both. */
        internal fun merge(configLeft: Config, configRight: Config): Config {
            val apiSurfaces = merge(configLeft.apiSurfaces, configRight.apiSurfaces)
            return Config(apiSurfaces)
        }

        internal fun merge(apiSurfaces1: ApiSurfacesConfig?, apiSurfaces2: ApiSurfacesConfig?) =
            if (apiSurfaces1 == null) apiSurfaces2
            else if (apiSurfaces2 == null) apiSurfaces1
            else ApiSurfacesConfig(apiSurfaces1.apiSurfaceList + apiSurfaces2.apiSurfaceList)
    }
}
