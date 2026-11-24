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

import java.io.File
import org.xml.sax.InputSource

const val CONFIG_NAMESPACE = "http://www.google.com/tools/metalava/config"

/** Parser for XML configuration files. */
interface ConfigParser {
    /** Parse a list of configuration files in order, returning a single [Config] object. */
    fun parse(files: List<File>): Config {
        return parseInputSources(files.map { InputSource(it.path) })
    }

    /**
     * Parse a list of configuration [InputSource]s in order, returning a single [Config] object.
     */
    fun parseInputSources(inputSources: List<InputSource>): Config

    companion object : ConfigParser by StaxConfigParser
}
