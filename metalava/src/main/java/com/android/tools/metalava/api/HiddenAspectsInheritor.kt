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

package com.android.tools.metalava.api

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.reporter.Issues
import java.util.IdentityHashMap

/**
 * Inherit aspects of the API that would otherwise be hidden.
 *
 * Includes:
 * * Concrete methods from a public class' hidden super classes that are implementing methods in
 *   public interfaces.
 * * Public interfaces that are implemented by hidden super classes of public classes.
 *
 * @param codebase the [Codebase] on which this will operate.
 * @param filterEmit determines which [SelectableItem] are part of the target API surface.
 * @param filterReference determines which [SelectableItem]s are part of the target API surface or
 *   any API surface it extends.
 */
class HiddenAspectsInheritor(
    private val codebase: Codebase,
    private val filterEmit: FilterPredicate,
    private val filterReference: FilterPredicate,
) {
    private val reporter = codebase.reporter

    private val packages = codebase.getPackages()

    /** Inherit hidden aspects of the API. */
    fun inheritHiddenAspects() {
        // When analyzing libraries we may discover some new classes during traversal; these aren't
        // part of the API but may be super classes or interfaces; these will then be added into the
        // package class lists, which could trigger a concurrent modification, so create a snapshot
        // of the class list and iterate over it:
        val allClasses = packages.allClasses().toList()

        inheritHiddenInterfacesAndConcreteClasses(allClasses)

        inheritHiddenConstants(allClasses)
    }

    /**
     * For each [ClassItem] in [allClasses], inherit hidden interfaces and concrete methods that
     * implement public interface methods from any of their hidden super types.
     */
    private fun inheritHiddenInterfacesAndConcreteClasses(allClasses: List<ClassItem>) {
        // Visit all the concrete classes checking to see whether it should inherit any hidden
        // methods or interfaces.
        val visited = UniqueClassItemSet()
        for (classItem in allClasses) {
            // If it is not a class, i.e. an interface, etc., then ignore it.
            if (!classItem.isClass()) continue

            inheritHiddenInterfacesAndConcreteMethods(classItem, visited)
        }
    }

    /**
     * For [ClassItem], inherit hidden interfaces and concrete methods that implement public
     * interface methods from any of its hidden super types.
     */
    private fun inheritHiddenInterfacesAndConcreteMethods(
        cls: ClassItem,
        visited: UniqueClassItemSet,
    ) {
        // If already visited this class then ignore it. Otherwise, remember that this was visited.
        if (cls in visited) return
        visited.add(cls)

        // If it has no super class then ignore it.
        val superClass = cls.superClass() ?: return

        // If the class is not going to be emitted then do not inherit any methods into it.
        if (!filterEmit.test(cls)) return

        // Make sure that the super class has inherited the methods and interfaces.
        inheritHiddenInterfacesAndConcreteMethods(superClass, visited)

        val allSuperClasses = cls.allSuperClasses()
        val hiddenSuperClasses =
            allSuperClasses.filter { !filterReference.test(it) && !it.isJavaLangObject() }

        if (hiddenSuperClasses.none()) { // not missing any implementation methods
            return
        }

        inheritConcreteMethodsFromHiddenClasses(
            cls,
            hiddenSuperClasses,
            allSuperClasses,
        )
        inheritInterfacesFromHiddenSuperClasses(cls, hiddenSuperClasses)
    }

    /**
     * Add any interfaces in the API (as determined by [filterReference]) from [hiddenSuperClasses]
     * to [cls].
     *
     * e.g. if `PublicClass` class extends `HiddenClass` and `HiddenClass` implements
     * `PublicInterface` then it will make `PublicClass` implement `PublicInterface`.
     */
    private fun inheritInterfacesFromHiddenSuperClasses(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
    ) {
        // Keep track of the interface types. If any new interfaces are added then this will be
        // stored in [cls].
        var interfaceTypes: MutableSet<ClassTypeItem>? = null

        // Iterate over all the hidden super classes.
        for (hiddenSuperClass in hiddenSuperClasses) {
            // For each hidden super class iterate over its interfaces.
            for (interfaceType in hiddenSuperClass.interfaceTypes()) {
                // For each interface type resolve it, ignoring it if it cannot be resolved.
                val interfaceClass = interfaceType.resolveClass(codebase) ?: continue

                // Ignore interfaces that are not part of the API.
                if (!filterReference.test(interfaceClass)) continue

                // Initialize the collections of interface types.
                if (interfaceTypes == null) {
                    interfaceTypes = cls.interfaceTypes().toMutableSet()
                }

                // If necessary rewrite a generic interface type to use the correct type parameters.
                // e.g. given:
                //     public class StringContainer extends HiddenContainer<String> { ... }
                //     @Hide public interface HiddenContainer<T> implements Container<T> { ... }
                //     public interface Container<T> { ... }
                //
                // Then this will result in:
                //     public class StringContainer
                //         extends HiddenContainer<String>
                //         implements Container>String> { ... }
                //
                if (interfaceClass.hasTypeVariables()) {
                    val mapping = cls.mapTypeVariables(hiddenSuperClass)
                    if (mapping.isNotEmpty()) {
                        val mappedType = interfaceType.convertType(mapping)
                        interfaceTypes.add(mappedType)
                        continue
                    }
                }

                // Add the interface type to the set owned by the class.
                interfaceTypes.add(interfaceType)
            }
        }

        if (interfaceTypes != null) {
            // Store the mutable list of interface types in the class.
            cls.setInterfaceTypes(interfaceTypes.toList())
        }
    }

    /**
     * Inherit concrete method implementations of public interface methods that are implemented in
     * hidden classes and so will not otherwise be included in the API.
     */
    private fun inheritConcreteMethodsFromHiddenClasses(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
        superClasses: Sequence<ClassItem>,
    ) {
        // Also generate stubs for any methods we would have inherited from abstract parents
        // All methods from super classes that (1) aren't overridden in this class already, and
        // (2) are overriding some method that is in a public interface accessible from this class.
        val interfaces = cls.allInterfaceTypes(filterReference).toSet()

        // Note that we can't just call method.superMethods() to and see whether any of their
        // containing classes are among our target APIs because it's possible that the super class
        // doesn't actually implement the interface, but still provides a matching signature for the
        // interface. Instead, we'll look through all of our interface methods and look for
        // potential overrides.
        val inheritableMethods = MethodItemSet()
        for (interfaceType in interfaces) {
            val interfaceClass = interfaceType.resolveClass(codebase) ?: continue
            for (method in interfaceClass.methods()) {
                inheritableMethods.add(method)
            }
        }

        // Also add in any abstract methods from public super classes
        val publicSuperClasses =
            superClasses.filter { filterEmit.test(it) && !it.isJavaLangObject() }
        for (superClass in publicSuperClasses) {
            for (method in superClass.methods()) {
                if (!method.modifiers.isAbstract() || !method.modifiers.isPublicOrProtected()) {
                    continue
                }
                inheritableMethods.add(method)
            }
        }

        // Also add in any concrete public methods from hidden super classes
        for (superClass in hiddenSuperClasses) {
            // Determine if there is a non-hidden class between the superClass and this class.
            // If non-hidden classes are found, don't include the methods for this hiddenSuperClass,
            // as it will already have been included in a previous super class
            val includeHiddenSuperClassMethods =
                !cls.allSuperClasses()
                    // Search from this class up to, but not including the superClass.
                    .takeWhile { currentClass -> currentClass != superClass }
                    // Find any class that is not hidden.
                    .any { currentClass -> !hiddenSuperClasses.contains(currentClass) }

            if (!includeHiddenSuperClassMethods) {
                continue
            }

            for (method in superClass.methods()) {
                if (method.modifiers.isAbstract() || !method.modifiers.isPublic()) {
                    continue
                }

                if (method.hasHiddenType(filterReference)) {
                    continue
                }

                inheritableMethods.add(method)
            }
        }

        // Find all methods that are inherited from these classes into our class (making sure that
        // we don't have duplicates, e.g. a method defined by one inherited class and then
        // overridden by another closer one). map from method name to super methods overriding our
        // interfaces
        val inheritedMethods = MethodItemSet()

        for (superClass in hiddenSuperClasses) {
            for (method in superClass.methods()) {
                val modifiers = method.modifiers
                if (!modifiers.isPrivate() && !modifiers.isAbstract()) {
                    if (inheritableMethods.containsMatchingMethod(method)) {
                        inheritedMethods.add(method)
                    }
                }
            }
        }

        // Remove any methods that are overriding any of our existing methods
        for (method in cls.methods()) {
            inheritedMethods.removeMatchingMethods(method)
        }

        // Next remove any overrides among the remaining super methods (e.g. one method from a
        // hidden parent is overriding another method from a more distant hidden parent).
        inheritedMethods.values.forEach { methods ->
            if (methods.size >= 2) {
                for (candidate in ArrayList(methods)) {
                    for (superMethod in candidate.allSuperMethods()) {
                        methods.remove(superMethod)
                    }
                }
            }
        }

        // Add all the existing methods in the class to the set of existing methods.
        val existingMethods = MethodItemSet()
        for (method in cls.methods()) {
            existingMethods.add(method)
        }

        // We're now left with concrete methods in hidden parents that are implementing methods in
        // public interfaces that are listed in this class. Create stubs for them:
        inheritedMethods.values.flatten().forEach {
            // Copy the method from the hidden class that is not part of the API into the class that
            // is part of the API.
            val method = it.duplicate(cls)
            /* Insert comment marker: This is useful for debugging purposes but doesn't
               belong in the stub
            method.documentation = "// Inlined stub from hidden parent class ${it.containingClass().qualifiedName()}\n" +
                    method.documentation
             */

            // If we already have an override of this method, do not add it to the methods list
            if (existingMethods.containsMatchingMethod(method)) {
                return@forEach
            }

            val runtimeDesc = it.internalDesc()
            val stubDesc = method.internalDesc()
            if (filterEmit.test(method) && runtimeDesc != stubDesc) {
                // This is problematic primarily for the platform where we use stubs, and the
                // generated method in the android.jar won't actually exist at runtime.
                // While we don't use stubs in AndroidX, this can still cause compat issues because
                // the current.txt (which will show the equivalent of stubDesc) won't actually match
                // the ABI of the library (because call sites will reference runtimeDesc).
                reporter.report(
                    Issues.INHERIT_CHANGES_SIGNATURE,
                    it,
                    "Explicitly override $it in $cls, or hide it in ${it.containingClass()};" +
                        " it cannot be implicitly inherited as API from the hidden super class" +
                        " because that would change its erased signature from $runtimeDesc to" +
                        " $stubDesc, and cause failures at runtime.",
                )
            }

            cls.addMethod(method)

            // Make sure that the same method is not added from multiple super classes.
            existingMethods.add(method)
        }
    }

    /**
     * For each [ClassItem] in [allClasses], inherit hidden interfaces and concrete methods that
     * implement public interface methods from any of their hidden super types.
     */
    private fun inheritHiddenConstants(allClasses: List<ClassItem>) {
        // Visit all the concrete classes checking to see whether it should inherit any hidden
        // methods or interfaces.
        for (classItem in allClasses) {
            // If it is not a class, i.e. an interface, etc., then ignore it.
            if (!classItem.isClass()) continue

            inheritHiddenConstants(classItem)
        }
    }

    /** Return true if this is a public constant. */
    private fun FieldItem.isPublicConstant() =
        modifiers.isStatic() && modifiers.isFinal() && modifiers.isPublic()

    /** Add inherited hidden constants, if any, from [superTypeClass] to [targetClassItem]. */
    private fun FieldItemSet.addInheritedHiddenConstantsFromSuperType(
        targetClassItem: ClassItem,
        superTypeClass: ClassItem,
    ) {
        // Do not inherit fields from classes that are in the API.
        if (filterReference.test(superTypeClass)) return

        for (fieldItem in superTypeClass.fields()) {
            // If the field is a public constant then try and inherit it into this class.
            if (fieldItem.isPublicConstant()) {
                // Create a duplicate of the field in this class.
                val duplicate = fieldItem.duplicate(targetClassItem)

                // Only add it if it is going to be part of the API. This will prevent any fields
                // which are explicitly hidden from being included in the API.
                if (filterReference.test(duplicate)) {
                    add(duplicate)
                }
            }
        }
    }

    /** Inherit hidden constants into [cls], if necessary. */
    private fun inheritHiddenConstants(cls: ClassItem) {
        // Do not inherit fields into classes that are not in the API.
        if (!filterReference.test(cls)) return

        // Find the first super class of this class which is not hidden. This class should not
        // add any fields that will be added to that class. If there is no such super class then
        // there is nothing to do.
        val closestNonHiddenAncestor =
            generateSequence(cls.superClass()) { it.superClass() }
                .firstOrNull { filterReference.test(it) } ?: return

        // Compute the set of fields to be inherited.
        val inheritedFields =
            FieldItemSet()
                .apply {
                    // Compute the set of interfaces from which this class could inherit constants.
                    // That is all the interfaces this class implements (directly, or indirectly)
                    // minus those implemented (directly or indirectly) by its closest, non-hidden
                    // ancestor.
                    val extraInterfaces =
                        cls.allInterfaces().toSet() -
                            closestNonHiddenAncestor.allInterfaces().toSet()

                    // Add fields from the interfaces first so they can be overridden by those from
                    // the super class if necessary.
                    for (interfaceClass in extraInterfaces) {
                        addInheritedHiddenConstantsFromSuperType(cls, interfaceClass)
                    }

                    // Super class fields override interface fields.
                    cls.superClass()?.let { superClass ->
                        addInheritedHiddenConstantsFromSuperType(cls, superClass)
                    }

                    // Remove any fields that conflict with the current class fields.
                    for (fieldItem in cls.fields()) {
                        remove(fieldItem)
                    }
                }
                .values

        // Add the inherited fields to this class.
        for (fieldItem in inheritedFields) {
            (cls as SkeletonClassItem).addField(fieldItem)
        }
    }
}

/**
 * A set of [MethodItem]s.
 *
 * This is implemented as a [MutableMap] from the [MethodItem.name] to the list of [MethodItem]s
 * with that name.
 */
private typealias MethodItemSet = HashMap<String, MutableList<MethodItem>>

/**
 * Add a method to the set.
 *
 * This does not check to see if the [MethodItem] exists already so it is possible that it will
 * contain duplicate methods.
 */
private fun MethodItemSet.add(method: MethodItem) {
    val name = method.name()
    val list = computeIfAbsent(name) { mutableListOf() }
    list.add(method)
}

/**
 * Check to see whether the set contains a method that matches [method] as determined by
 * [MethodItem.matches].
 */
private fun MethodItemSet.containsMatchingMethod(method: MethodItem): Boolean {
    val name = method.name()
    val list = this[name] ?: return false
    for (existing in list) {
        if (method.matches(existing)) {
            return true
        }
    }
    return false
}

/** Remove any method that matches [method] as determined by [MethodItem.matches]. */
private fun MethodItemSet.removeMatchingMethods(method: MethodItem) {
    val name = method.name()
    val list = this[name] ?: return
    val iterator = list.listIterator()
    while (iterator.hasNext()) {
        val existing = iterator.next()
        if (method.matches(existing)) {
            iterator.remove()
        }
    }
}

/**
 * A set of [FieldItem]s.
 *
 * This is implemented as a [MutableMap] from the [FieldItem.name] to the [FieldItem] with that
 * name.
 */
private typealias FieldItemSet = HashMap<String, FieldItem>

/**
 * Add a field to the set.
 *
 * This does not check to see if the [FieldItem] exists already so it is possible that it will
 * replace an existing field.
 */
private fun FieldItemSet.add(field: FieldItem) {
    this[field.name()] = field
}

/** Remove a field from the set. */
private fun FieldItemSet.remove(field: FieldItem) {
    remove(field.name())
}

/** A set of unique [ClassItem]s matched by their identity. */
private typealias UniqueClassItemSet = IdentityHashMap<ClassItem, Unit>

/** Check to see if this [UniqueClassItemSet] contains [element]. */
private operator fun UniqueClassItemSet.contains(element: ClassItem): Boolean = containsKey(element)

/** Add [element] to this [UniqueClassItemSet]. */
private fun UniqueClassItemSet.add(element: ClassItem) {
    put(element, Unit)
}
