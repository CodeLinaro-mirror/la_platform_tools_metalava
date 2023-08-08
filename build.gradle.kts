import com.android.tools.metalava.CREATE_ARCHIVE_TASK
import com.android.tools.metalava.CREATE_BUILD_INFO_TASK
import com.android.tools.metalava.configureBuildInfoTask
import com.android.tools.metalava.configureKtfmt
import com.android.tools.metalava.configurePublishingArchive
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Take a copy of the original [buildDir] for use by [testPrebuiltsSdkDir] */
val originalBuildDir = buildDir

buildDir = getBuildDirectory()

defaultTasks =
    mutableListOf(
        "installDist",
        "test",
        CREATE_ARCHIVE_TASK,
        CREATE_BUILD_INFO_TASK,
        "lint",
        "ktfmtCheck",
    )

repositories {
    google()
    mavenCentral()
    val lintRepo = project.findProperty("lintRepo") as String?
    if (lintRepo != null) {
        logger.warn("Building using custom $lintRepo maven repository")
        maven { url = uri(lintRepo) }
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    id("com.android.lint") version "8.2.0-alpha08"
    id("application")
    id("java")
    id("maven-publish")
}

group = "com.android.tools.metalava"

version = getMetalavaVersion()

application {
    mainClass.set("com.android.tools.metalava.Driver")
    applicationDefaultJvmArgs = listOf("-ea", "-Xms2g", "-Xmx4g")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(KotlinCompile::class.java) {
    kotlinOptions {
        jvmTarget = "17"
        apiVersion = "1.7"
        languageVersion = "1.7"
        allWarningsAsErrors = true
    }
}

val customLintVersion = findProperty("lintVersion") as String?
val studioVersion: String =
    if (customLintVersion != null) {
        logger.warn("Building using custom $customLintVersion version of Android Lint")
        customLintVersion
    } else {
        "31.2.0-alpha08"
    }

dependencies {
    implementation("com.android.tools.external.org-jetbrains:uast:$studioVersion")
    implementation("com.android.tools.external.com-intellij:kotlin-compiler:$studioVersion")
    implementation("com.android.tools.external.com-intellij:intellij-core:$studioVersion")
    implementation("com.android.tools.lint:lint-api:$studioVersion")
    implementation("com.android.tools.lint:lint-checks:$studioVersion")
    implementation("com.android.tools.lint:lint-gradle:$studioVersion")
    implementation("com.android.tools.lint:lint:$studioVersion")
    implementation("com.android.tools:common:$studioVersion")
    implementation("com.android.tools:sdk-common:$studioVersion")
    implementation("com.android.tools:sdklib:$studioVersion")
    implementation("com.github.ajalt.clikt:clikt-jvm:3.5.3")
    implementation(libs.kotlinStdlib)
    implementation(libs.kotlinReflect)
    implementation("org.ow2.asm:asm:8.0")
    implementation("org.ow2.asm:asm-tree:8.0")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("com.google.code.gson:gson:2.8.9")
    testImplementation("com.android.tools.lint:lint-tests:$studioVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation(libs.kotlinTest)
}

val zipTask: TaskProvider<Zip> =
    project.tasks.register("zipTestResults", Zip::class.java) {
        destinationDirectory.set(File(getDistributionDirectory(), "host-test-reports"))
        archiveFileName.set("metalava-tests.zip")
    }

/**
 * The location into which a fake representation of the prebuilts/sdk directory will be written.
 *
 * This uses [originalBuildDir] rather than [buildDir] as the latter depends on the `OUT_DIR`
 * environment variable. As this value is passed in to the tests this would make the tests dependent
 * on the `OUT_DIR` which as it varies from build to build would reduce the benefit of remote
 * caching.
 */
val testPrebuiltsSdkDir = originalBuildDir.resolve("prebuilts/sdk")

/**
 * Register tasks to emulate parts of the prebuilts/sdk repository using source from this directory.
 *
 * [sourceDir] is the path to the root directory of code that is compiled into a jar that
 * corresponds to the jar specified by [destJar].
 *
 * [destJar] is the path to a jar within prebuilts/sdk. The fake jar created from [sourceDir] is
 * copied to getBuildDirectory()/prebuilts/sdk/$destJar.
 *
 * The jars created by this can be accessed by tests via the `METALAVA_TEST_PREBUILTS_SDK_ROOT`
 * environment variable which is set to point to getBuildDirectory()/prebuilts/sdk; see [testTask].
 */
fun registerTestPrebuiltsSdkTasks(sourceDir: String, destJar: String): TaskProvider<Jar> {
    val basename = sourceDir.replace("/", "-")
    val javaCompileTaskName = "$basename.classes"
    val jarTaskName = "$basename.jar"

    val compileTask =
        project.tasks.register(javaCompileTaskName, JavaCompile::class) {
            options.compilerArgs = listOf("--patch-module", "java.base=" + file(sourceDir))
            source = fileTree(sourceDir)
            classpath = project.files()
            destinationDirectory.set(originalBuildDir.resolve(javaCompileTaskName))
        }

    val destJarFile = File(destJar)
    val dir = destJarFile.parent
    val filename = destJarFile.name
    if (dir == ".") {
        throw IllegalArgumentException("bad destJar argument '$destJar'")
    }

    val jarTask =
        project.tasks.register(jarTaskName, Jar::class) {
            from(compileTask.flatMap { it.destinationDirectory })
            archiveFileName.set(filename)
            destinationDirectory.set(testPrebuiltsSdkDir.resolve(dir))
        }

    return jarTask
}

val testPrebuiltsSdkApi30 =
    registerTestPrebuiltsSdkTasks("src/testdata/prebuilts-sdk-test/30", "30/public/android.jar")
val testPrebuiltsSdkApi31 =
    registerTestPrebuiltsSdkTasks("src/testdata/prebuilts-sdk-test/31", "31/public/android.jar")
val testPrebuiltsSdkExt1 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/1",
        "extensions/1/public/framework-ext.jar"
    )
val testPrebuiltsSdkExt2 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/2",
        "extensions/2/public/framework-ext.jar"
    )
val testPrebuiltsSdkExt3 =
    registerTestPrebuiltsSdkTasks(
        "src/testdata/prebuilts-sdk-test/extensions/3",
        "extensions/3/public/framework-ext.jar"
    )

project.tasks.register("test-sdk-extensions-info.xml", Copy::class) {
    from("src/testdata/prebuilts-sdk-test/sdk-extensions-info.xml")
    into(testPrebuiltsSdkDir)
}

project.tasks.register("test-prebuilts-sdk") {
    dependsOn(testPrebuiltsSdkApi30)
    dependsOn(testPrebuiltsSdkApi31)
    dependsOn(testPrebuiltsSdkExt1)
    dependsOn(testPrebuiltsSdkExt2)
    dependsOn(testPrebuiltsSdkExt3)
    dependsOn("test-sdk-extensions-info.xml")
}

val testTask = tasks.named("test", Test::class.java)

testTask.configure {
    dependsOn("test-prebuilts-sdk")
    setEnvironment("METALAVA_TEST_PREBUILTS_SDK_ROOT" to testPrebuiltsSdkDir)
    jvmArgs = listOf("--add-opens=java.base/java.lang=ALL-UNNAMED")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    testLogging.events =
        hashSetOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT,
            TestLogEvent.STANDARD_ERROR
        )
    if (isBuildingOnServer()) ignoreFailures = true
    finalizedBy(zipTask)
}

zipTask.configure { from(testTask.map { it.reports.junitXml.outputLocation.get() }) }

fun getMetalavaVersion(): Any {
    val versionPropertyFile = File(projectDir, "src/main/resources/version.properties")
    if (versionPropertyFile.canRead()) {
        val versionProps = Properties()
        versionProps.load(FileInputStream(versionPropertyFile))
        return versionProps["metalavaVersion"]
            ?: throw IllegalStateException(
                "metalava version was not set in ${versionPropertyFile.absolutePath}"
            )
    } else {
        throw FileNotFoundException("Could not read ${versionPropertyFile.absolutePath}")
    }
}

fun getBuildDirectory(): File {
    return if (System.getenv("OUT_DIR") != null) {
        File(System.getenv("OUT_DIR"), "metalava")
    } else {
        File(projectDir, "../../out/metalava")
    }
}

/**
 * The build server will copy the contents of the distribution directory and make it available for
 * download.
 */
fun getDistributionDirectory(): File {
    return if (System.getenv("DIST_DIR") != null) {
        File(System.getenv("DIST_DIR"))
    } else {
        File(projectDir, "../../out/dist")
    }
}

fun isBuildingOnServer(): Boolean {
    return System.getenv("OUT_DIR") != null && System.getenv("DIST_DIR") != null
}

/**
 * @return build id string for current build
 *
 * The build server does not pass the build id so we infer it from the last folder of the
 * distribution directory name.
 */
fun getBuildId(): String {
    return if (System.getenv("DIST_DIR") != null) File(System.getenv("DIST_DIR")).name else "0"
}

val publicationName = "Metalava"
val repositoryName = "Dist"

publishing {
    publications {
        create<MavenPublication>(publicationName) {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers { developer { name.set("The Android Open Source Project") } }
                scm {
                    connection.set(
                        "scm:git:https://android.googlesource.com/platform/tools/metalava"
                    )
                    url.set("https://android.googlesource.com/platform/tools/metalava/")
                }
            }
        }
    }

    repositories {
        maven {
            name = repositoryName
            url = uri("file://${getDistributionDirectory().canonicalPath}/repo/m2repository")
        }
    }
}

lint {
    fatal.add("UastImplementation")
    disable.add("UseTomlInstead") // not useful for this project
    disable.add("GradleDependency") // not useful for this project
    abortOnError = true
    baseline = File("lint-baseline.xml")
}

configureKtfmt()

// Add a buildId into Gradle Metadata file so we can tell which build it is from.
tasks.withType(GenerateModuleMetadata::class.java).configureEach {
    val outDirProvider = project.providers.environmentVariable("DIST_DIR")
    inputs.property("buildOutputDirectory", outDirProvider).optional(true)
    doLast {
        val metadata = outputFile.asFile.get()
        val text = metadata.readText()
        val buildId = outDirProvider.orNull?.let { File(it).name } ?: "0"
        metadata.writeText(
            text.replace(
                """"createdBy": {
    "gradle": {""",
                """"createdBy": {
    "gradle": {
      "buildId:": "$buildId",""",
            )
        )
    }
}

val archiveTaskProvider =
    configurePublishingArchive(
        project,
        publicationName,
        repositoryName,
        getBuildId(),
        getDistributionDirectory()
    )

configureBuildInfoTask(
    project,
    isBuildingOnServer(),
    getDistributionDirectory(),
    archiveTaskProvider
)
