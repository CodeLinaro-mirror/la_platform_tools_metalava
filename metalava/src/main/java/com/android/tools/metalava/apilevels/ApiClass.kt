/*
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 * Represents a class or an interface and its methods/fields. This is used to write the simplified
 * XML file containing all the public API.
 */
class ApiClass(name: String, private val isEnum: Boolean) : ApiElement(name) {

    private val mSuperClasses = mutableMapOf<String, ApiElement>()
    private val mInterfaces = mutableMapOf<String, ApiElement>()

    private val mFields = mutableMapOf<String, ApiElement>()
    private val mMethods = mutableMapOf<String, ApiElement>()

    /**
     * Updates the [ApiElement] for field with [name], creating and adding one if necessary.
     *
     * @param name the name of the field.
     * @param updater the [ApiHistoryUpdater] that will update the element with information about
     *   the version to which it belongs.
     * @param deprecated the deprecated status.
     */
    fun updateField(
        name: String,
        updater: ApiHistoryUpdater,
        deprecated: Boolean,
    ): ApiElement {
        return updateElementInMap(mFields, name, updater, deprecated)
    }

    val fields: Collection<ApiElement>
        get() = mFields.values

    /**
     * Updates the [ApiElement] for method with [signature], creating and adding one if necessary.
     *
     * @param signature the signature of the method, which includes the name and parameter/return
     *   types
     * @param updater the [ApiHistoryUpdater] that will update the element with information about
     *   the version to which it belongs.
     * @param deprecated the deprecated status.
     */
    fun updateMethod(
        signature: String,
        updater: ApiHistoryUpdater,
        deprecated: Boolean,
    ): ApiElement {
        return updateElementInMap(mMethods, signature, updater, deprecated)
    }

    val methods: Collection<ApiElement>
        get() = mMethods.values

    /** The names of the compiler generated enum methods. */
    private fun enumMethodNames(): List<String> {
        return if (name.contains("/")) {
            listOf("valueOf(Ljava/lang/String;)L$name;", "values()[L$name;")
        } else {
            listOf("valueOf(java.lang.String)", "values()")
        }
    }

    /**
     * Adds class dependent members, if needed.
     *
     * These are members whose history is identical to the containing class, e.g. compiler generated
     * enum methods.
     */
    fun addClassDependentMembersIfNeeded() {
        if (!isEnum) return

        for (methodName in enumMethodNames()) {
            mMethods[methodName] = ApiElement(methodName, this)
        }
    }

    /**
     * Updates an element for [superClassType], creating and adding one if necessary.
     *
     * @param superClassType the name of the super class type.
     * @param updater the [ApiHistoryUpdater] that will update the element with information about
     *   the version to which it belongs.
     */
    fun updateSuperClass(superClassType: String, updater: ApiHistoryUpdater) =
        updateElementInMap(
            mSuperClasses,
            superClassType,
            updater,
            // References to super classes can never be deprecated.
            false,
        )

    val superClasses: Collection<ApiElement>
        get() = mSuperClasses.values

    /**
     * Updates an element for [interfaceType], creating and adding one if necessary.
     *
     * @param interfaceType the interface type.
     * @param updater the [ApiHistoryUpdater] that will update the element with information about
     *   the version to which it belongs.
     */
    fun updateInterface(interfaceType: String, updater: ApiHistoryUpdater) =
        updateElementInMap(
            mInterfaces,
            interfaceType,
            updater,
            // References to interfaces can never be deprecated.
            false,
        )

    val interfaces: Collection<ApiElement>
        get() = mInterfaces.values

    private fun updateElementInMap(
        elements: MutableMap<String, ApiElement>,
        name: String,
        updater: ApiHistoryUpdater,
        deprecated: Boolean,
    ): ApiElement {
        val existing = elements[name]
        val element = existing ?: ApiElement(name).apply { elements[name] = this }
        updater.update(element, deprecated)
        return element
    }

    /**
     * Removes all interfaces that are also implemented by superclasses or extended by interfaces
     * this class implements.
     *
     * @param allClasses all classes keyed by their names.
     */
    fun removeImplicitInterfaces(allClasses: Map<String, ApiClass>) {
        if (mInterfaces.isEmpty()) {
            return
        }
        val iterator = mInterfaces.values.iterator()
        while (iterator.hasNext()) {
            val interfaceElement = iterator.next()
            if (implementsImplicitInterface(interfaceElement, allClasses)) {
                iterator.remove()
            }
        }
    }

    private fun implementsInterface(
        interfaceElement: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        val localInterface = mInterfaces[interfaceElement.name]
        return if (
            localInterface != null && localInterface.introducedNotLaterThan(interfaceElement)
        ) {
            true
        } else {
            implementsImplicitInterface(interfaceElement, allClasses)
        }
    }

    /**
     * Checks if the given [interfaceElement] is implemented by a superclass or extended by an
     * interface this class implements.
     */
    private fun implementsImplicitInterface(
        interfaceElement: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        for (parent in interfaces.asSequence() + superClasses.asSequence()) {
            if (parent.introducedNotLaterThan(interfaceElement)) {
                val cls = allClasses[parent.name]
                if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Removes all methods that override method declared by superclasses and interfaces of this
     * class.
     *
     * @param allClasses all classes keyed by their names.
     */
    fun removeOverridingMethods(allClasses: Map<String, ApiClass>) {
        val it: MutableIterator<Map.Entry<String, ApiElement>> = mMethods.entries.iterator()
        while (it.hasNext()) {
            val (_, method) = it.next()
            if (method.name.startsWith("<init>(")) {
                continue
            }
            if (isOverrideOfInherited(method, allClasses)) {
                it.remove()
            } else {
                val superMethod = findMethodInAncestors(method, allClasses)
                if (superMethod != null) {
                    method.updateLastPresentIn(minOf(lastPresentIn, superMethod.lastPresentIn))
                    deprecatedIn?.let { method.updateDeprecatedIn(it) }
                    superMethod.deprecatedIn?.let { method.updateDeprecatedIn(it) }
                }
            }
        }
    }

    /**
     * Checks if the given method overrides one of the methods defined by this class or its
     * superclasses or interfaces.
     *
     * @param method the method to check
     * @param allClasses the map containing all API classes
     * @return true if the method is an override
     */
    private fun isOverride(method: ApiElement, allClasses: Map<String, ApiClass>): Boolean {
        val name = method.name
        val localMethod = mMethods[name]
        return if (
            localMethod != null &&
                localMethod.introducedNotLaterThan(method) &&
                localMethod.lastPresentIn >= method.lastPresentIn
        ) {
            // This class has the method, and it was introduced in at the same api level
            // as the child method, or before, and was not removed before the child method.
            true
        } else {
            isOverrideOfInherited(method, allClasses)
        }
    }

    /**
     * Checks if the given method overrides one of the methods declared by ancestors of this class.
     */
    private fun isOverrideOfInherited(
        method: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        // Check this class' parents.
        for (parent in superClasses.asSequence() + interfaces.asSequence()) {
            // Only check the parent if it was a parent class at the introduction of the method.
            if (parent.introducedNotLaterThan(method)) {
                val cls = allClasses[parent.name]
                if (cls != null && cls.isOverride(method, allClasses)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Finds the matching method declared by ancestors of this class with the latest
     * [lastPresentIn], if any.
     */
    private fun findMethodInAncestors(
        method: ApiElement,
        allClasses: Map<String, ApiClass>,
        visited: MutableSet<ApiClass> = mutableSetOf()
    ): ApiElement? {
        if (!visited.add(this)) {
            return null
        }
        var bestMatch: ApiElement? = null
        for (parent in superClasses.asSequence() + interfaces.asSequence()) {
            val cls = allClasses[parent.name] ?: continue
            val localMethod = cls.mMethods[method.name]
            if (localMethod != null) {
                if (bestMatch == null || bestMatch.lastPresentIn < localMethod.lastPresentIn) {
                    bestMatch = localMethod
                }
            }
            val ancestorMethod = cls.findMethodInAncestors(method, allClasses, visited)
            if (ancestorMethod != null) {
                if (bestMatch == null || bestMatch.lastPresentIn < ancestorMethod.lastPresentIn) {
                    bestMatch = ancestorMethod
                }
            }
        }
        return bestMatch
    }

    // Ensure this class doesn't extend/implement any other classes/interfaces that are
    // not in the provided api. This can happen when a class in an android.jar file
    // encodes the inheritance, but the class that is inherited is not present in any
    // android.jar file. The class would instead be present in an apex's stub jar file.
    // An example of this is the QosSessionAttributes interface being provided by the
    // Connectivity apex, but being implemented by NrQosSessionAttributes from
    // frameworks/base/telephony.
    fun removeMissingClasses(api: Map<String, ApiClass>) {
        val superClassIter = mSuperClasses.values.iterator()
        while (superClassIter.hasNext()) {
            val superClass = superClassIter.next()
            if (!api.containsKey(superClass.name)) {
                superClassIter.remove()
            }
        }
        val interfacesIter = mInterfaces.values.iterator()
        while (interfacesIter.hasNext()) {
            val intf = interfacesIter.next()
            if (!api.containsKey(intf.name)) {
                interfacesIter.remove()
            }
        }
    }

    // Returns the set of superclasses or interfaces are not present in the provided api map
    fun findMissingClasses(api: Map<String, ApiClass>): Set<ApiElement> {
        val result: MutableSet<ApiElement> = HashSet()
        for (superClass in superClasses) {
            if (!api.containsKey(superClass.name)) {
                result.add(superClass)
            }
        }
        for (intf in interfaces) {
            if (!api.containsKey(intf.name)) {
                result.add(intf)
            }
        }
        return result
    }
}
