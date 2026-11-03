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

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/** Unit tests for [ApiXmlParser]. */
class ApiXmlParserTest {

    /**
     * Parses [xml] using [ApiXmlParser], prints the resulting [Api] using [ApiXmlPrinter], and
     * verifies that the printed XML matches [expectedXml] (defaulting to [xml]).
     */
    private fun checkRoundTrip(
        xml: String,
        expectedXml: String = xml,
    ) {
        val trimmedInput = xml.trimIndent()
        val api = ApiXmlParser.parse(trimmedInput)
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { writer -> ApiXmlPrinter().print(api, writer) }
        val printed = stringWriter.toString().replace("\t", "    ").trimEnd()
        assertEquals(expectedXml.trimIndent(), printed)
    }

    @Test
    fun `Basic classes, superclasses, interfaces, methods, and fields`() {
        checkRoundTrip(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3">
                    <class name="java/lang/Object" since="1">
                        <method name="&lt;init>()V"/>
                    </class>
                    <class name="test/pkg/Base" since="1" deprecated="3" removed="4">
                        <extends name="java/lang/Object"/>
                        <implements name="test/pkg/Interface" since="2"/>
                        <method name="&lt;init>()V"/>
                        <method name="foo(Ljava/lang/String;)I" since="2" deprecated="2" removed="3"/>
                        <field name="CONST" since="2" deprecated="4"/>
                    </class>
                    <class name="test/pkg/Interface" since="2">
                        <extends name="java/lang/Object"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `SDK extensions, modules, and custom min version`() {
        checkRoundTrip(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="3" min="21">
                    <sdk id="30" shortname="R-ext" name="R Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}R"/>
                    <sdk id="31" shortname="S-ext" name="S Extensions" reference="android/os/Build${'$'}VERSION_CODES${'$'}S"/>
                    <sdk id="1000000" shortname="adservices-ext" name="AdServices Extensions" reference="android/adservices/AdServicesVersion"/>
                    <class name="android/pkg/Test" module="framework-sdkextensions" since="30" sdks="30:2,31:2,0:30">
                        <extends name="java/lang/Object"/>
                        <method name="&lt;init>()V"/>
                        <method name="bar()V" since="31" sdks="31:5,0:31"/>
                        <field name="FIELD" since="33" sdks="1000000:4,0:33"/>
                    </class>
                </api>
            """
        )
    }

    @Test
    fun `Minor versions use file version 4`() {
        checkRoundTrip(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <api version="4">
                    <class name="test/pkg/Foo" since="36.0">
                        <extends name="java/lang/Object"/>
                        <method name="bar()V" since="36.1" deprecated="37.2" removed="38.0"/>
                    </class>
                </api>
            """
        )
    }
}
