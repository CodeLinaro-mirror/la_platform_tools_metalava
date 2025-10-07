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

import com.android.build.gradle.internal.tasks.factory.dependsOn

// Subproject containing code that is common to all models that are produced from source code.

plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("metalava-build-plugin")
    id("maven-publish")

    antlr
}

tasks.generateGrammarSource {
    outputDirectory =
        layout.buildDirectory
            .dir("generated-src/antlr/main/com/android/tools/metalava/model/source/javadoc")
            .get()
            .asFile
    arguments =
        listOf(
            "-visitor",
            "-Xexact-output-dir",
        )
}

tasks.compileKotlin.dependsOn(tasks.generateGrammarSource)

tasks.compileTestKotlin.dependsOn(tasks.generateTestGrammarSource)

// Add dependency from `generateJvmTestLintModel` and `lintAnalyzeJvmTest` onto
// `generateTestGrammarSource` to avoid configuration error. The resolving of the lint tasks is
// deferred as they have not yet been created.
tasks
    .named { it == "generateJvmTestLintModel" || it == "lintAnalyzeJvmTest" }
    .configureEach { dependsOn(tasks.generateTestGrammarSource) }

tasks.compileTestFixturesKotlin.dependsOn(tasks.generateTestFixturesGrammarSource)

dependencies {
    antlr(libs.antlr4)

    implementation(project(":metalava-reporter"))
    implementation(project(":metalava-model"))
    implementation(project(":metalava-reporter"))
    implementation(libs.antlr4)

    testFixturesImplementation(project(":metalava-model"))
    testFixturesImplementation(testFixtures(project(":metalava-model")))
    testFixturesImplementation(project(":metalava-model-testsuite"))
    testFixturesImplementation(project(":metalava-reporter"))
    testFixturesImplementation(libs.androidLintTests)
    testFixturesImplementation(project(":metalava-testing"))

    testImplementation(libs.androidLintTests)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinTest)
}
