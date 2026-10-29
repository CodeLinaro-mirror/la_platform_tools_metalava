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

package com.android.tools.metalava.doc

import com.android.tools.lint.LintCliClient
import com.android.tools.lint.checks.ApiLookup
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.SdkExtension
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.ANDROID_PLATFORM_SDK_ID
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.getCallableParameterDescriptorUsingDots
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * Implementation of [ApiHistoryProvider] backed by Lint's [ApiLookup] and a SAX-parsed SDK
 * extension map ([createSymbolToSdkExtSinceMap]).
 *
 * @param xmlFile the `api-versions.xml` file to load.
 * @param cacheDir optional directory in which [ApiLookup] will store its binary cache file.
 * @param underTest `true` if running in a unit test so that [ApiLookup] uses a unique temporary
 *   cache directory when [cacheDir] is `null`.
 */
class LintApiHistoryProvider(
    xmlFile: File,
    cacheDir: File? = null,
    underTest: Boolean = false,
) : ApiHistoryProvider {
    /** The Lint [ApiLookup] database loaded from `api-versions.xml`. */
    private val apiLookup: ApiLookup = getApiLookup(xmlFile, cacheDir, underTest = underTest)

    /**
     * Map from symbol key (`"pkg.Class"` or `"pkg.Class#member"`) to the [SdkAndVersion] in which
     * the symbol was first introduced.
     */
    private val elementToSdkExtSinceMap: Map<String, SdkAndVersion> =
        createSymbolToSdkExtSinceMap(xmlFile)

    override fun getApiHistory(cls: ClassItem): ItemApiHistory? =
        apiLookup.getApiHistory(cls, elementToSdkExtSinceMap)

    override fun getApiHistory(callable: CallableItem): ItemApiHistory? =
        apiLookup.getApiHistory(callable, elementToSdkExtSinceMap)

    override fun getApiHistory(field: FieldItem): ItemApiHistory? =
        apiLookup.getApiHistory(field, elementToSdkExtSinceMap)

    companion object {
        /** [ApiHistoryProvider.Loader] that creates a [LintApiHistoryProvider]. */
        val loader: ApiHistoryProvider.Loader =
            object : ApiHistoryProvider.Loader {
                override fun load(xmlFile: File) = LintApiHistoryProvider(xmlFile, underTest = true)

                override fun toString() = "lint"
            }
    }
}

/** A constraint that will only match for Android Platform SDKs. */
private val androidSdkConstraint = ApiConstraint.get(1)

/**
 * Get the min [ApiVersion], i.e. the lowest version of the Android Platform SDK.
 *
 * TODO(b/282932318): Replace with call to ApiConstraint.min() when bug is fixed.
 */
private fun ApiConstraint.minApiVersion(): ApiVersion? {
    return getConstraints()
        .filter { it != ApiConstraint.UNKNOWN }
        // Remove any constraints that are not for the Android Platform SDK.
        .filter { it.isAtLeast(androidSdkConstraint) }
        // Get the minimum of all the lowest ApiVersions, or null if there are no ApiVersions in the
        // constraints.
        .minOfOrNull {
            val major = it.fromInclusive()
            val minor = it.fromInclusiveMinor()
            ApiVersion.fromMajorMinor(
                major,
                if (minor == 0) null else minor,
            )
        }
}

/**
 * Construct an [ItemApiHistory] for [cls] from this [ApiLookup] and [elementToSdkExtSinceMap], or
 * return `null` if [cls] is not present in the API history.
 */
private fun ApiLookup.getApiHistory(
    cls: ClassItem,
    elementToSdkExtSinceMap: Map<String, SdkAndVersion>,
): ItemApiHistory? {
    return ItemApiHistory.create(
        since = getClassVersion(cls),
        deprecatedIn = getClassDeprecatedIn(cls),
        sdkExtSince = elementToSdkExtSinceMap[cls.qualifiedName()],
    )
}

/**
 * Construct an [ItemApiHistory] for [callable] from this [ApiLookup] and [elementToSdkExtSinceMap],
 * or return `null` if [callable] is not present in the API history.
 */
private fun ApiLookup.getApiHistory(
    callable: CallableItem,
    elementToSdkExtSinceMap: Map<String, SdkAndVersion>,
): ItemApiHistory? {
    val desc = callable.getCallableParameterDescriptorUsingDots() ?: "()"
    val key = "${callable.containingClass().qualifiedName()}#${callable.name()}$desc"
    return ItemApiHistory.create(
        since = getCallableVersion(callable),
        deprecatedIn = getCallableDeprecatedIn(callable),
        sdkExtSince = elementToSdkExtSinceMap[key],
    )
}

/**
 * Construct an [ItemApiHistory] for [field] from this [ApiLookup] and [elementToSdkExtSinceMap], or
 * return `null` if [field] is not present in the API history.
 */
private fun ApiLookup.getApiHistory(
    field: FieldItem,
    elementToSdkExtSinceMap: Map<String, SdkAndVersion>,
): ItemApiHistory? {
    val key = "${field.containingClass().qualifiedName()}#${field.name()}"
    return ItemApiHistory.create(
        since = getFieldVersion(field),
        deprecatedIn = getFieldDeprecatedIn(field),
        sdkExtSince = elementToSdkExtSinceMap[key],
    )
}

/** Get the Android platform [ApiVersion] in which [cls] was introduced, or `null` if unknown. */
private fun ApiLookup.getClassVersion(cls: ClassItem): ApiVersion? {
    val owner = cls.qualifiedName()
    return getClassVersions(owner).minApiVersion()
}

/** Get the Android platform [ApiVersion] in which [method] was introduced, or `null` if unknown. */
private fun ApiLookup.getCallableVersion(method: CallableItem): ApiVersion? {
    val containingClass = method.containingClass()
    val owner = containingClass.qualifiedName()
    val desc = method.getCallableParameterDescriptorUsingDots()
    // Metalava uses the class name as the name of the constructor but the ApiLookup uses <init>.
    val name = if (method.isConstructor()) "<init>" else method.name()
    return getMethodVersions(owner, name, desc).minApiVersion()
}

/** Get the Android platform [ApiVersion] in which [field] was introduced, or `null` if unknown. */
private fun ApiLookup.getFieldVersion(field: FieldItem): ApiVersion? {
    val containingClass = field.containingClass()
    val owner = containingClass.qualifiedName()
    return getFieldVersions(owner, field.name()).minApiVersion()
}

/**
 * Get the Android platform [ApiVersion] in which [cls] was deprecated, or `null` if not deprecated.
 */
private fun ApiLookup.getClassDeprecatedIn(cls: ClassItem): ApiVersion? {
    val owner = cls.qualifiedName()
    return getClassDeprecatedInVersions(owner).minApiVersion()
}

/**
 * Get the Android platform [ApiVersion] in which [callable] was deprecated, or `null` if not
 * deprecated.
 */
private fun ApiLookup.getCallableDeprecatedIn(callable: CallableItem): ApiVersion? {
    val containingClass = callable.containingClass()
    val owner = containingClass.qualifiedName()
    val desc = callable.getCallableParameterDescriptorUsingDots() ?: return null
    val name = if (callable.isConstructor()) "<init>" else callable.name()
    return getMethodDeprecatedInVersions(owner, name, desc).minApiVersion()
}

/**
 * Get the Android platform [ApiVersion] in which [field] was deprecated, or `null` if not
 * deprecated.
 */
private fun ApiLookup.getFieldDeprecatedIn(field: FieldItem): ApiVersion? {
    val containingClass = field.containingClass()
    val owner = containingClass.qualifiedName()
    return getFieldDeprecatedInVersions(owner, field.name()).minApiVersion()
}

/**
 * Create an [ApiLookup] instance initialized from [xmlFile].
 *
 * @param xmlFile the `api-versions.xml` file to load.
 * @param cacheDir optional directory in which [ApiLookup] will store its binary cache file.
 * @param underTest `true` if running in a unit test so that a unique temporary cache directory is
 *   created when [cacheDir] is `null`.
 */
fun getApiLookup(
    xmlFile: File,
    cacheDir: File? = null,
    underTest: Boolean = true,
): ApiLookup {
    val client =
        object : LintCliClient(PROGRAM_NAME) {
            override fun getCacheDir(name: String?, create: Boolean): File? {
                if (cacheDir != null) {
                    return cacheDir
                }

                if (create && underTest) {
                    // Pick unique directory during unit tests
                    return Files.createTempDirectory(PROGRAM_NAME).toFile()
                }

                val sb = StringBuilder(PROGRAM_NAME)
                if (name != null) {
                    sb.append(File.separator)
                    sb.append(name)
                }
                val relative = sb.toString()

                val tmp = System.getenv("TMPDIR")
                if (tmp != null) {
                    // Android Build environment: Make sure we're really creating a unique
                    // temp directory each time since builds could be running in
                    // parallel here.
                    val dir = File(tmp, relative)
                    if (!dir.isDirectory) {
                        dir.mkdirs()
                    }

                    return Files.createTempDirectory(dir.toPath(), null).toFile()
                }

                val dir = File(System.getProperty("java.io.tmpdir"), relative)
                if (create && !dir.isDirectory) {
                    dir.mkdirs()
                }
                return dir
            }
        }

    val xmlPathProperty = "LINT_API_DATABASE"
    val prev = System.getProperty(xmlPathProperty)
    try {
        System.setProperty(xmlPathProperty, xmlFile.path)
        @Suppress("DEPRECATION") return ApiLookup.get(client) ?: error("ApiLookup creation failed")
    } finally {
        if (prev != null) {
            System.setProperty(xmlPathProperty, xmlFile.path)
        } else {
            System.clearProperty(xmlPathProperty)
        }
    }
}

/**
 * Generate a map of symbol -> (list of SDKs and corresponding versions the symbol first appeared)
 * in by parsing an api-versions.xml file. This will be used when injecting @sdkExtSince
 * annotations, which convey the same information, in a format documentation tools can consume.
 *
 * A symbol is either of a class, method or field.
 *
 * The symbols are Strings on the format "com.pkg.Foo#MethodOrField", with no method signature.
 */
private fun createSymbolToSdkExtSinceMap(xmlFile: File): Map<String, SdkAndVersion> {
    data class OuterClass(val name: String, val idAndVersion: IdAndVersion?)

    val sdkExtensionsById = mutableMapOf<Int, SdkExtension>()
    lateinit var lastSeenClass: OuterClass
    val elementToIdAndVersionMap = mutableMapOf<String, IdAndVersion>()
    val memberTags = listOf("class", "method", "field")
    val parser = SAXParserFactory.newDefaultInstance().newSAXParser()
    parser.parse(
        xmlFile,
        object : DefaultHandler() {
            override fun startElement(
                uri: String,
                localName: String,
                qualifiedName: String,
                attributes: Attributes
            ) {
                if (qualifiedName == "sdk") {
                    val id: Int =
                        attributes.getValue("id")?.toIntOrNull()
                            ?: throw IllegalArgumentException(
                                "<sdk>: missing or non-integer id attribute"
                            )
                    val shortname: String =
                        attributes.getValue("shortname")
                            ?: throw IllegalArgumentException("<sdk>: missing shortname attribute")
                    val name: String =
                        attributes.getValue("name")
                            ?: throw IllegalArgumentException("<sdk>: missing name attribute")
                    val reference: String =
                        attributes.getValue("reference")
                            ?: throw IllegalArgumentException("<sdk>: missing reference attribute")
                    sdkExtensionsById[id] =
                        SdkExtension.fromXmlAttributes(
                            id,
                            shortname,
                            name,
                            reference,
                        )
                } else if (memberTags.contains(qualifiedName)) {
                    val name: String =
                        attributes.getValue("name")
                            ?: throw IllegalArgumentException(
                                "<$qualifiedName>: missing name attribute"
                            )
                    val sdksList = attributes.getValue("sdks")
                    val idAndVersion =
                        sdksList
                            ?.split(",")
                            // Get the first pair of sdk-id:version where sdk-id is not 0. If no
                            // such pair exists then use `null`.
                            ?.firstNotNullOfOrNull {
                                val (sdk, version) = it.split(":")
                                val id = sdk.toInt()
                                // Ignore any references to the Android Platform SDK as they are
                                // handled by ApiLookup.
                                if (id == ANDROID_PLATFORM_SDK_ID) null
                                else IdAndVersion(id, version.toInt())
                            }

                    // Populate elementToIdAndVersionMap. The keys constructed here are derived from
                    // api-versions.xml; when used elsewhere in DocAnalyzer, the keys will be
                    // derived from PsiItems. The two sources use slightly different nomenclature,
                    // so change "api-versions.xml nomenclature" to "PsiItems nomenclature" before
                    // inserting items in the map.
                    //
                    // Nomenclature differences:
                    //   - constructors are named "<init>()V" in api-versions.xml, but
                    //     "ClassName()V" in PsiItems
                    //   - nested classes are named "Outer#Inner" in api-versions.xml, but
                    //     "Outer.Inner" in PsiItems
                    when (qualifiedName) {
                        "class" -> {
                            lastSeenClass =
                                OuterClass(name.replace('/', '.').replace('$', '.'), idAndVersion)
                            if (idAndVersion != null) {
                                elementToIdAndVersionMap[lastSeenClass.name] = idAndVersion
                            }
                        }
                        "method",
                        "field" -> {
                            val shortName =
                                if (qualifiedName == "method") {
                                    // Keep parameter descriptor (without return type) and normalize
                                    // '/' and '$' to '.' so overloaded methods have distinct keys.
                                    val withoutReturn = name.substringBefore(')') + ")"
                                    val withClassName =
                                        if (withoutReturn.startsWith("<init>")) {
                                            // Constructors in api-versions.xml are named '<init>':
                                            // rename to simple name of class instead.
                                            lastSeenClass.name.substringAfterLast('.') +
                                                withoutReturn.removePrefix("<init>")
                                        } else {
                                            withoutReturn
                                        }
                                    withClassName.replace('/', '.').replace('$', '.')
                                } else {
                                    name
                                }
                            val element = "${lastSeenClass.name}#$shortName"
                            if (idAndVersion != null) {
                                elementToIdAndVersionMap[element] = idAndVersion
                            } else if (sdksList == null && lastSeenClass.idAndVersion != null) {
                                // The method/field does not have an `sdks` attribute so fall back
                                // to the idAndVersion from the containing class.
                                elementToIdAndVersionMap[element] = lastSeenClass.idAndVersion!!
                            }
                        }
                    }
                }
            }
        }
    )

    val elementToSdkExtSinceMap = mutableMapOf<String, SdkAndVersion>()
    for (entry in elementToIdAndVersionMap.entries) {
        elementToSdkExtSinceMap[entry.key] =
            entry.value.let {
                val name =
                    sdkExtensionsById[it.first]?.name
                        ?: throw IllegalArgumentException(
                            "SDK reference to unknown <sdk> with id ${it.first}"
                        )
                SdkAndVersion(name, it.second)
            }
    }
    return elementToSdkExtSinceMap
}

private typealias IdAndVersion = Pair<Int, Int>
