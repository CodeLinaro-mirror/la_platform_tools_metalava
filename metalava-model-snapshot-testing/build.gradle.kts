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

plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("metalava-build-plugin")

    // This project does not actually provide an implementation of the metalava-model, but it does
    // run the test suite over other models, on a snapshot of the codebase they create.
    id("metalava-model-provider-plugin")
}

dependencies {
    testImplementation(project(":metalava-model"))
    testImplementation(testFixtures(project(":metalava-model")))

    // Include the text model's test runner so that the test suite will run its signature based
    // tests on a snapshot of the codebase it produces.
    testImplementation(testFixtures(project(":metalava-model-text")))

    // Pick up the SourceModelSuiteRunner service to run the `metalava-model-testsuite` against a
    // SourceModelProvider.
    testImplementation(testFixtures(project(":metalava-model-source")))

    // Include the turbine model's SourceModelProvider so that the test suite will run its Java
    // based tests on a snapshot of the codebase it produces.
    testImplementation(project(":metalava-model-turbine"))

    // Include the psi model's SourceModelProvider so that the test suite will run its Java based
    // tests on a snapshot of the codebase it produces.
    testImplementation(project(":metalava-model-psi"))
}
