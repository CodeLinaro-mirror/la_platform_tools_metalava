/*
 * Copyright (C) 2018 The Android Open Source Project
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

plugins {
    id("application")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
    id("metalava-build-plugin")
}

application {
    mainClass.set("com.android.tools.metalava.Driver")
    applicationDefaultJvmArgs = listOf("-ea", "-Xms2g", "-Xmx4g")
}

configurations {
    compileClasspath {
        // Prevent metalava from using sdklib classes pulled in by indirect dependencies.
        exclude(group = "com.android.tools", module = "sdklib")
        // Prevent metalava from using Guava classes pulled in by indirect dependencies.
        exclude(group = "com.google.guava")
        // Prevent metalava from using ASM classes pulled in by indirect dependencies.
        exclude(group = "org.ow2.asm")
    }
}

dependencies {
    implementation(project(":metalava-model"))
    implementation(project(":metalava-model-source"))
    implementation(project(":metalava-model-text"))
    implementation(project(":metalava-reporter"))
    implementation(libs.androidLintApi)
    implementation(libs.androidLintChecks)
    implementation(libs.androidLint)
    implementation(libs.androidToolsCommon)
    implementation(libs.clikt)
    implementation(libs.gson)
    implementation(libs.tracingWire)

    runtimeOnly(project(":metalava-model-psi"))

    testImplementation(project(":metalava-testing"))
    testImplementation(testFixtures(project(":metalava-model")))
    testImplementation(testFixtures(project(":metalava-model-text")))
    testImplementation(libs.androidLintTests)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinTestJUnit)

    testRuntimeOnly(project(":metalava-model-turbine"))
}

val testDataDir = layout.projectDirectory.dir("src/testdata")

tasks.named<Test>("test").configure {
    // Add some additional environment variables to pass in some directories needed by the test.
    environment.putAll(
        mapOf(
            "METALAVA_TESTDATA_DIR" to testDataDir.asFile.absolutePath,
        )
    )
}

// Copy the version.properties from the root project into its own directory.
val copyVersion =
    tasks.register("copyVersion", Copy::class.java) {
        from("${rootProject.projectDir}/version.properties")
        into(project.layout.buildDirectory.dir("version"))
    }

// Add the version directory to the directories from which the jar is built.
sourceSets.getByName("main").output.dir(copyVersion)
