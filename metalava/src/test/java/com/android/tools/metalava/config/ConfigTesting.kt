/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.testing.xml
import java.io.File
import java.io.StringReader
import org.xml.sax.InputSource

/** Write [this] to [file] in the same format as [ConfigParser] reads. */
fun Config.writeTo(file: File) {
    val xmlMapper = ConfigParser.configXmlMapper()
    xmlMapper.writeValue(file, this)
}

/**
 * Create a [TestFile] at [targetPath] containing [this] in the same format as [ConfigParser] reads.
 */
fun Config.toTestFile(targetPath: String = "config.xml"): TestFile {
    val xmlMapper = ConfigParser.configXmlMapper()
    return xml(targetPath, xmlMapper.writeValueAsString(this))
}

/** Get an [InputSource] to access the contents of this [TestFile]. */
fun TestFile.toInputSource(): InputSource = StringInputSource(targetRelativePath, rawContents)

/** An [InputSource] wrapper around a [path] and [String] [contents]. */
private class StringInputSource(path: String, private val contents: String) : InputSource(path) {
    override fun getCharacterStream() = StringReader(contents)
}
