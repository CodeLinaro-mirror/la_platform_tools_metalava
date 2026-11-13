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

plugins {
    `java-library`
}

// Turbine requires Java 21 (matching external/turbine/pom.xml).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Metalava subprojects target JVM 17 (via MetalavaBuildPlugin), so their compileClasspath and
// testRuntimeClasspath request org.gradle.jvm.version = 17. Override that attribute on
// :turbine's outgoing variants so JVM 17 projects (like :metalava-model-turbine, :metalava, and
// :metalava-model-snapshot-testing) can resolve :turbine when running on a Java 21+ JVM.
for (configName in listOf("apiElements", "runtimeElements")) {
    configurations.named(configName) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("../../../external/turbine/java"))
        // Exclude CLI entry point and dependency proto generation which are not needed by
        // metalava-model-turbine and would otherwise require running protoc.
        java.exclude(
            "com/google/turbine/deps/**",
            "com/google/turbine/main/**",
        )
    }
}

// Compiler flags required by Turbine (matching external/turbine/pom.xml).
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-XDaddTypeAnnotationsToSymbol=true",
        )
    )
}

dependencies {
    api(libs.guava)
    api(libs.jspecify)
    compileOnly(libs.errorProneAnnotations)
    compileOnly(libs.autoValueAnnotations)
    annotationProcessor(libs.autoValue)
}
