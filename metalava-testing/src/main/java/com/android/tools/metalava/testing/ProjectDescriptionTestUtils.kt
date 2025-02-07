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

package com.android.tools.metalava.testing

import com.android.tools.lint.checks.infrastructure.TestFile

private val standardClasspath = getKotlinStdlibPaths() + getAndroidJar()
val standardProjectXmlClasspath =
    standardClasspath.joinToString("\n") { "<classpath file=\"$it\"/>" }

/** The XML string for one module of a project (using the [standardProjectXmlClasspath]). */
fun createModuleDescription(
    moduleName: String,
    android: Boolean,
    sourceFiles: Array<TestFile>,
    dependsOn: List<String> = listOf("commonMain"),
): String {
    val sourceLines = sourceFiles.joinToString("\n") { "<src file=\"${it.targetRelativePath}\" />" }
    val dependsOnLines = dependsOn.joinToString("\n") { "<dep module=\"$it\" kind=\"dependsOn\"/>" }
    return """
        <module name="$moduleName" android="$android">
          $dependsOnLines
          $sourceLines
          $standardProjectXmlClasspath
        </module>
        """
}

/** The XML string for the common module of a project. */
fun createCommonModuleDescription(sourceFiles: Array<TestFile>): String {
    return createModuleDescription("commonMain", false, sourceFiles, emptyList())
}

/** The XML string for the android module of a project. */
fun createAndroidModuleDescription(
    sourceFiles: Array<TestFile>,
    dependsOn: List<String> = listOf("commonMain"),
): String {
    return createModuleDescription("androidMain", true, sourceFiles, dependsOn)
}

/** The XML string for a project, with no root dir set (so the file location is the root dir). */
fun createProjectDescription(vararg modules: String): TestFile {
    return xml(
        "project.xml",
        """
        <project>
          ${modules.joinToString("\n")}
        </project>
        """
    )
}
