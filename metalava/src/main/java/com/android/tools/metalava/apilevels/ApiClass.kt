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

import com.google.common.collect.Iterables
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a class or an interface and its methods/fields. This is used to write the simplified
 * XML file containing all the public API.
 */
class ApiClass(name: String) : ApiElement(name) {

    private val mSuperClasses = mutableMapOf<String, ApiElement>()
    private val mInterfaces = mutableMapOf<String, ApiElement>()

    /** If `true`, never seen as public. */
    var alwaysHidden = false // Package private class?
    private val mFields = ConcurrentHashMap<String, ApiElement>()
    private val mMethods = ConcurrentHashMap<String, ApiElement>()

    /**
     * Updates the [ApiElement] for field with [name], creating and adding one if necessary.
     *
     * @param name the name of the field.
     * @param updater the [ApiElement.Updater] that will update the element with information about
     *   the version to which it belongs.
     * @param deprecated the deprecated status.
     */
    fun updateField(
        name: String,
        updater: Updater,
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
     * @param updater the [ApiElement.Updater] that will update the element with information about
     *   the version to which it belongs.
     * @param deprecated the deprecated status.
     */
    fun updateMethod(
        signature: String,
        updater: Updater,
        deprecated: Boolean,
    ): ApiElement {
        // Correct historical mistake in android.jar files
        var correctedName = signature
        if (correctedName.endsWith(")Ljava/lang/AbstractStringBuilder;")) {
            correctedName =
                correctedName.substring(
                    0,
                    correctedName.length - ")Ljava/lang/AbstractStringBuilder;".length
                ) + ")L" + this.name + ";"
        }
        return updateElementInMap(mMethods, correctedName, updater, deprecated)
    }

    val methods: Collection<ApiElement>
        get() = mMethods.values

    /**
     * Updates an element for [superClassType], creating and adding one if necessary.
     *
     * @param superClassType the name of the super class type.
     * @param updater the [ApiElement.Updater] that will update the element with information about
     *   the version to which it belongs.
     */
    fun updateSuperClass(superClassType: String, updater: Updater) =
        updateElementInMap(
            mSuperClasses,
            superClassType,
            updater,
            // References to super classes can never be deprecated.
            false,
        )

    val superClasses: Collection<ApiElement>
        get() = mSuperClasses.values

    fun updateHidden(hidden: Boolean) {
        alwaysHidden = hidden
    }

    /**
     * Updates an element for [interfaceType], creating and adding one if necessary.
     *
     * @param interfaceType the interface type.
     * @param updater the [ApiElement.Updater] that will update the element with information about
     *   the version to which it belongs.
     */
    fun updateInterface(interfaceType: String, updater: Updater) =
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
        updater: Updater,
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
        if (mInterfaces.isEmpty() || mSuperClasses.isEmpty()) {
            return
        }
        val iterator = mInterfaces.values.iterator()
        while (iterator.hasNext()) {
            val interfaceElement = iterator.next()
            for (superClass in superClasses) {
                if (superClass.introducedNotLaterThan(interfaceElement)) {
                    val cls = allClasses[superClass.name]
                    if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                        iterator.remove()
                        break
                    }
                }
            }
        }
    }

    private fun implementsInterface(
        interfaceElement: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        for (localInterface in interfaces) {
            if (localInterface.introducedNotLaterThan(interfaceElement)) {
                if (interfaceElement.name == localInterface.name) {
                    return true
                }
                // Check parent interface.
                val cls = allClasses[localInterface.name]
                if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                    return true
                }
            }
        }
        for (superClass in superClasses) {
            if (superClass.introducedNotLaterThan(interfaceElement)) {
                val cls = allClasses[superClass.name]
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
            if (!method.name.startsWith("<init>(") && isOverrideOfInherited(method, allClasses)) {
                it.remove()
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
        return if (localMethod != null && localMethod.introducedNotLaterThan(method)) {
            // This class has the method, and it was introduced in at the same api level
            // as the child method, or before.
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
        for (parent in Iterables.concat(superClasses, interfaces)) {
            // Only check the parent if it was a parent class at the introduction of the method.
            if (parent!!.introducedNotLaterThan(method)) {
                val cls = allClasses[parent.name]
                if (cls != null && cls.isOverride(method, allClasses)) {
                    return true
                }
            }
        }
        return false
    }

    private var haveInlined = false

    fun inlineFromHiddenSuperClasses(hidden: Map<String, ApiClass>) {
        if (haveInlined) {
            return
        }
        haveInlined = true
        for (superClass in superClasses) {
            val hiddenSuper = hidden[superClass.name]
            if (hiddenSuper != null) {
                hiddenSuper.inlineFromHiddenSuperClasses(hidden)
                val myMethods = mMethods
                val myFields = mFields
                for ((name, value) in hiddenSuper.mMethods) {
                    if (!myMethods.containsKey(name)) {
                        myMethods[name] = value
                    }
                }
                for ((name, value) in hiddenSuper.mFields) {
                    if (!myFields.containsKey(name)) {
                        myFields[name] = value
                    }
                }
            }
        }
    }

    fun removeHiddenSuperClasses(api: Map<String, ApiClass>) {
        // If we've included a package private class in the super class map (from the older
        // android.jar files)
        // remove these here and replace with the filtered super classes, updating API levels in the
        // process
        val iterator = mSuperClasses.values.iterator()
        var min = ApiVersion.HIGHEST
        while (iterator.hasNext()) {
            val next = iterator.next()
            min = minOf(min, next.since)
            val extendsClass = api[next.name]
            if (extendsClass != null && extendsClass.alwaysHidden) {
                val since = extendsClass.since
                iterator.remove()
                for (other in superClasses) {
                    if (other.since >= since) {
                        other.update(min)
                    }
                }
                break
            }
        }
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
