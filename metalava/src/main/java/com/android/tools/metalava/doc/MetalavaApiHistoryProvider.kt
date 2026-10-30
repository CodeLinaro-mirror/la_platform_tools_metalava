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

import com.android.tools.metalava.apilevels.Api
import com.android.tools.metalava.apilevels.ApiClass
import com.android.tools.metalava.apilevels.ApiElement
import com.android.tools.metalava.apilevels.ApiToExtensionsMap.Companion.ANDROID_PLATFORM_SDK_ID
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.ApiXmlParser
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import java.io.File

/**
 * Implementation of [ApiHistoryProvider] backed by Metalava's [Api] model and [ApiXmlParser].
 *
 * @param xmlFile the `api-versions.xml` file to load.
 */
class MetalavaApiHistoryProvider(xmlFile: File) : ApiHistoryProvider {

    /** The [Api] model parsed from [xmlFile]. */
    private val api: Api = ApiXmlParser.parse(xmlFile)

    /**
     * Per-class index mapping normalized method keys (`name(paramDescriptors)` without return type,
     * with `/` and `$` replaced by `.`) to the matching [ApiElement]s in that class.
     *
     * Keying by `name(paramDescriptors)` without the return type allows covariant return overrides
     * in subclasses to match inherited methods from supertypes. Replacing `$` with `.` normalizes
     * inner class descriptors across `api-versions.xml` entries generated from bytecode vs.
     * source/signature models.
     */
    private val classMethodIndex: Map<String, Map<String, List<ApiElement>>> =
        api.classes.associate { apiClass ->
            apiClass.name to apiClass.methods.groupBy { it.methodKey() }
        }

    /**
     * Returns the [ItemApiHistory] for [cls] from the parsed [Api], or `null` if [cls] is not
     * present in `api-versions.xml`.
     */
    override fun getApiHistory(cls: ClassItem): ItemApiHistory? {
        val apiClass = api.findClass(cls.internalName()) ?: return null
        return ItemApiHistory.create(
            since = apiClass.platformSince(),
            deprecatedIn = apiClass.deprecatedIn?.normalizeMinorZero(),
            sdkExtSince = null,
        )
    }

    /**
     * Returns the [ItemApiHistory] for [callable] from the parsed [Api], or `null` if [callable]'s
     * containing class is not present in `api-versions.xml` or [callable] has no recorded history.
     */
    override fun getApiHistory(callable: CallableItem): ItemApiHistory? {
        val containingClass = callable.containingClass()
        val apiClass = api.findClass(containingClass.internalName()) ?: return null

        // In bytecode-derived api-versions.xml files, constructors of non-static inner classes
        // include the synthetic enclosing class instance as their first descriptor parameter,
        // whereas in source/signature-derived XML files they may omit it.
        val isNonStaticInnerConstructor =
            callable.isConstructor() &&
                containingClass.isNestedClass() &&
                !containingClass.modifiers.isStatic()

        // Try looking up with the synthetic enclosing class parameter first, falling back to the
        // key without it for non-static inner class constructors.
        val primaryKey = callable.methodKey(includeEnclosingClassParam = true)
        val fallbackKey =
            if (isNonStaticInnerConstructor) {
                callable.methodKey(includeEnclosingClassParam = false)
            } else {
                null
            }

        // Look up the method element declared directly on apiClass for deprecation and SDK
        // extension history, which are not inherited from supertypes.
        val directMethod =
            findDirectMethod(apiClass, callable, primaryKey)
                ?: fallbackKey?.let { findDirectMethod(apiClass, callable, it) }

        // Look up the effective platform introduction version. Constructors are never inherited
        // from superclasses; if a non-static inner class constructor is not explicitly listed, fall
        // back to the containing class's introduction version.
        val since =
            if (callable.isConstructor()) {
                directMethod?.platformSince()
                    ?: if (isNonStaticInnerConstructor) apiClass.platformSince() else null
            } else {
                findMethodSince(apiClass, primaryKey, directMethod = directMethod)
            }

        if (since == null) return null

        // If the method is not directly present on apiClass (e.g. an overriding method omitted by
        // Api.clean()) or has no explicit deprecated attribute, fall back to the containing class's
        // deprecation version, clamped to be no earlier than the member's introduction version.
        val deprecatedIn =
            (directMethod?.deprecatedIn ?: apiClass.deprecatedIn)?.normalizeMinorZero()?.let {
                if (it < since) since else it
            }

        return ItemApiHistory.create(
            since = since,
            deprecatedIn = deprecatedIn,
            sdkExtSince = null,
        )
    }

    /**
     * Returns the [ItemApiHistory] for [field] from the parsed [Api], or `null` if [field]'s
     * containing class is not present in `api-versions.xml` or [field] has no recorded history.
     */
    override fun getApiHistory(field: FieldItem): ItemApiHistory? {
        val apiClass = api.findClass(field.containingClass().internalName()) ?: return null
        val fieldName = field.internalName()
        // Look up the field element declared directly on apiClass for deprecation and SDK
        // extension history, while computing `since` across apiClass and its supertypes.
        val directField = apiClass.getField(fieldName)
        val since = findFieldSince(apiClass, fieldName)
        if (since == null) return null

        val deprecatedIn =
            (directField?.deprecatedIn ?: apiClass.deprecatedIn)?.normalizeMinorZero()?.let {
                if (it < since) since else it
            }
        return ItemApiHistory.create(
            since = since,
            deprecatedIn = deprecatedIn,
            sdkExtSince = null,
        )
    }

    /**
     * Finds the direct [ApiElement] for [callable] in [apiClass] matching [methodKey], preferring
     * an exact JVM descriptor match (including return type) if multiple overloads in the class
     * share the same parameter erasure (e.g. bridge or covariant return methods in bytecode).
     */
    private fun findDirectMethod(
        apiClass: ApiClass,
        callable: CallableItem,
        methodKey: String,
    ): ApiElement? {
        val candidates = classMethodIndex[apiClass.name]?.get(methodKey) ?: return null
        if (candidates.size == 1) return candidates[0]
        // Disambiguate multiple candidates with the same parameter types by matching the full JVM
        // signature including return type.
        val fullName = callable.internalName() + callable.internalDesc(voidConstructorTypes = true)
        return candidates.firstOrNull { it.name == fullName } ?: candidates.first()
    }

    /**
     * Computes the minimum effective platform `since` [ApiVersion] for [methodKey] in [apiClass]
     * and its supertypes ([ApiClass.superClasses] and [ApiClass.interfaces]), or `null` if not
     * found.
     *
     * Because `Api.clean()` removes overriding method entries from subclasses when a supertype
     * already defines the method, a method's `since` version may only be recorded on a superclass
     * or superinterface. When inheriting a method from a supertype edge `<extends>` or
     * `<implements>`, the effective introduction version via that supertype is `max(edge.since,
     * supertypeMethod.since)`, and the overall `since` version is the minimum across the direct
     * declaration (if any) and all supertype paths.
     */
    private fun findMethodSince(
        apiClass: ApiClass,
        methodKey: String,
        directMethod: ApiElement? = null,
        memo: MutableMap<String, ApiVersion?> = mutableMapOf(),
    ): ApiVersion? {
        // Memoize per-class results in diamond inheritance hierarchies while guarding against
        // cycles.
        if (memo.containsKey(apiClass.name)) return memo[apiClass.name]
        memo[apiClass.name] = null

        // Start with the platform version of the disambiguated direct method (if provided on the
        // starting class), or the earliest platform version among matching methods declared
        // directly on apiClass.
        var minSince: ApiVersion? =
            if (directMethod != null) {
                directMethod.platformSince()
            } else {
                classMethodIndex[apiClass.name]
                    ?.get(methodKey)
                    ?.mapNotNull { it.platformSince() }
                    ?.minOrNull()
            }

        // Traverse implemented interfaces and superclasses to find inherited declarations.
        for (parentEdge in apiClass.interfaces.asSequence() + apiClass.superClasses.asSequence()) {
            val parentClass = api.findClass(parentEdge.name) ?: continue
            val parentMethodSince = findMethodSince(parentClass, methodKey, memo = memo) ?: continue
            // The method could not have been inherited via this supertype before apiClass started
            // extending/implementing it (recorded on parentEdge).
            val edgeSince = parentEdge.platformSince()
            val effectiveSince =
                if (edgeSince != null) maxOf(edgeSince, parentMethodSince) else parentMethodSince
            if (minSince == null || effectiveSince < minSince) {
                minSince = effectiveSince
            }
        }

        memo[apiClass.name] = minSince
        return minSince
    }

    /**
     * Computes the minimum effective platform `since` [ApiVersion] for [fieldName] in [apiClass]
     * and its supertypes ([ApiClass.superClasses] and [ApiClass.interfaces]), or `null` if not
     * found.
     *
     * Because `Api.clean()` removes inherited field entries from subclasses when a supertype
     * already defines the field, a field inherited from a superclass or superinterface is available
     * on [apiClass] starting at `max(edge.since, supertypeField.since)`, and the overall `since`
     * version is the minimum across the direct declaration and all supertype paths.
     */
    private fun findFieldSince(
        apiClass: ApiClass,
        fieldName: String,
        memo: MutableMap<String, ApiVersion?> = mutableMapOf(),
    ): ApiVersion? {
        // Memoize per-class results in diamond inheritance hierarchies while guarding against
        // cycles.
        if (memo.containsKey(apiClass.name)) return memo[apiClass.name]
        memo[apiClass.name] = null

        var minSince: ApiVersion? = apiClass.getField(fieldName)?.platformSince()

        for (parentEdge in apiClass.interfaces.asSequence() + apiClass.superClasses.asSequence()) {
            val parentClass = api.findClass(parentEdge.name) ?: continue
            val parentFieldSince = findFieldSince(parentClass, fieldName, memo) ?: continue
            val edgeSince = parentEdge.platformSince()
            val effectiveSince =
                if (edgeSince != null) maxOf(edgeSince, parentFieldSince) else parentFieldSince
            if (minSince == null || effectiveSince < minSince) {
                minSince = effectiveSince
            }
        }

        memo[apiClass.name] = minSince
        return minSince
    }

    companion object {
        /** [ApiHistoryProvider.Loader] that creates a [MetalavaApiHistoryProvider]. */
        val loader: ApiHistoryProvider.Loader =
            object : ApiHistoryProvider.Loader {
                override fun load(xmlFile: File) = MetalavaApiHistoryProvider(xmlFile)

                override fun toString() = "metalava"
            }
    }
}

/**
 * Normalizes an [ApiVersion] whose [ApiVersion.minor] is `0` to a major-only [ApiVersion] so that
 * `@apiSince` and `@deprecatedSince` tags omit a trailing `.0` (e.g. `36` instead of `36.0`).
 */
private fun ApiVersion.normalizeMinorZero(): ApiVersion =
    if (minor == 0) ApiVersion.fromLevel(major) else this

/**
 * Computes the Android platform [ApiVersion] for this [ApiElement], or `null` if this element only
 * exists in SDK extensions and has not been finalized in an Android platform SDK.
 *
 * When `sdks` is absent or empty, `since` is always an Android platform SDK version. When `sdks` is
 * present, `since` may instead hold an extension SDK version if the API was not yet finalized in an
 * Android platform SDK; therefore, only `0:<version>` entries (matching [ANDROID_PLATFORM_SDK_ID])
 * in `sdks` count as Android platform SDK versions.
 */
private fun ApiElement.platformSince(): ApiVersion? {
    val sdksAttr = sdks
    if (sdksAttr.isNullOrEmpty()) {
        return since.normalizeMinorZero()
    }
    var minPlatformVersion: ApiVersion? = null
    for (entry in sdksAttr.split(',')) {
        val colonIndex = entry.indexOf(':')
        if (colonIndex == -1) continue
        val sdkId = entry.substring(0, colonIndex).toIntOrNull() ?: continue
        if (sdkId == ANDROID_PLATFORM_SDK_ID) {
            val version =
                ApiVersion.fromString(entry.substring(colonIndex + 1)).normalizeMinorZero()
            if (minPlatformVersion == null || version < minPlatformVersion) {
                minPlatformVersion = version
            }
        }
    }
    return minPlatformVersion
}

/**
 * Returns the normalized method lookup key (`name(paramDescriptors)` without return type, with `/`
 * and `$` replaced by `.`) for this [ApiElement].
 */
private fun ApiElement.methodKey(): String {
    // Strip the return type descriptor after ')' so methods with covariant return types share the
    // same key as the supertype methods they override.
    val closeParen = name.indexOf(')')
    val withoutReturn = if (closeParen != -1) name.substring(0, closeParen + 1) else name
    return withoutReturn.replace('/', '.').replace('$', '.')
}

/**
 * Returns the normalized method lookup key (`name(paramDescriptors)` without return type, with `/`
 * and `$` replaced by `.`) for this [CallableItem].
 *
 * @param includeEnclosingClassParam whether to prepend the enclosing class descriptor parameter for
 *   non-static inner class constructors.
 */
private fun CallableItem.methodKey(includeEnclosingClassParam: Boolean): String = buildString {
    // Constructors are named "<init>" in api-versions.xml, whereas CallableItem.name() returns the
    // simple class name.
    append(if (isConstructor()) "<init>" else name())
    append("(")
    // Prepend the synthetic outer class parameter for non-static inner class constructors when
    // requested so that the key matches bytecode-derived <method> entries.
    if (includeEnclosingClassParam && isConstructor() && !containingClass().modifiers.isStatic()) {
        containingClass().containingClass()?.let { outerClass ->
            append(outerClass.type().internalName().replace('/', '.').replace('$', '.'))
        }
    }
    for (parameter in parameters()) {
        append(parameter.type().internalName().replace('/', '.').replace('$', '.'))
    }
    append(")")
}
