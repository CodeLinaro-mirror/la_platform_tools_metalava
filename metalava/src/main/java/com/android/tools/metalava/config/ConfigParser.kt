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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.SchemaFactory
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

const val CONFIG_NAMESPACE = "http://www.google.com/tools/metalava/config"

/** Parser for XML configuration files. */
class ConfigParser private constructor() : DefaultHandler() {
    /** Errors that were reported while parsing a configuration file. */
    private val errors = StringBuilder()

    private fun recordException(path: String, message: String) {
        errors.apply {
            append("    ")
            append(path)
            append(": ")
            append(message)
            append("\n")
        }
    }

    private fun recordParseException(exception: SAXParseException) {
        errors.apply {
            append("    ")
            append(exception.systemId)
            append(":")
            append(exception.lineNumber)
            append(": ")
            append(exception.message)
            append("\n")
        }
    }

    override fun warning(exception: SAXParseException) {
        recordParseException(exception)
    }

    override fun error(exception: SAXParseException) {
        recordParseException(exception)
    }

    companion object {
        /** Parse a list of configuration files in order, returning a single [Config] object. */
        fun parse(files: List<File>): Config {
            val schemaUrl = ConfigParser::class.java.getResource("/schemas/config.xsd")
            val schemafactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            val schema = schemafactory.newSchema(schemaUrl)

            val saxParserFactory = SAXParserFactory.newNSInstance()
            saxParserFactory.schema = schema
            val saxParser = saxParserFactory.newSAXParser()
            val configParser = ConfigParser()
            val xmlMapper = configXmlMapper()

            // Parse all the configuration files, validating against the schema, collating any
            // errors that are reported.
            for (file in files) {
                // Parse the configuration file to validate against the schema first.
                try {
                    saxParser.parse(file, configParser)
                } catch (e: SAXParseException) {
                    configParser.recordParseException(e)
                } catch (e: Exception) {
                    configParser.recordException(file.path, e.message ?: "")
                }
            }

            // If any errors were reported then fail as it is unlikely that reading or using the
            // configuration file will work.
            if (configParser.errors.isNotEmpty()) {
                error("Errors found while parsing configuration file(s):\n${configParser.errors}")
            }

            return files
                .map { file ->
                    // Read the configuration file into a Config object.
                    xmlMapper.readValue(file, Config::class.java)
                }
                // Merge the config objects together.
                .reduceOrNull(Config::combineWith)
                // Validate the config.
                ?.apply { validate() }
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
    }
}
