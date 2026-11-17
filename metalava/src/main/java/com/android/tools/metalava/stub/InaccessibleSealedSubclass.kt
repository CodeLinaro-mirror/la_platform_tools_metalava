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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import java.io.PrintWriter

/**
 * Represents an inaccessible sealed class subclass that is needed to ensure that the sealed class
 * stubs is non-exhaustive.
 */
internal class InaccessibleSealedSubclass(
    /**
     * The optional [ClassItem] within which the subclass will be nested. If this is `null` then the
     * subclass will be added as a top-level, package-private class.
     */
    private val containingClass: ClassItem?,

    /** The `sealed` [ClassItem] that the subclass will extend. */
    private val sealedClassItem: ClassItem,

    /**
     * The [ClassTypeItem] of the subclass that is used in the [sealedClassItem]'s `permits` list.
     */
    private val subclassType: ClassTypeItem,
) {
    /**
     * Write an inaccessible subclass of [sealedClassItem] to make [sealedClassItem] non-exhaustive.
     */
    fun write(writer: PrintWriter, stubConstructorManager: StubConstructorManager) {
        val simpleName = subclassType.className
        if (containingClass == null) {
            writeSuppressWarnings(writer)
        } else {
            // Sealed classes cannot access private nested classes within the sealed class due to
            // https://bugs.openjdk.org/browse/JDK-8338981. However, they can access private nested
            // classes in a containing class so this adds private in that case.
            // TODO(b/507474428): Should always be private but needs javac 25 as it contains a fix
            //  for https://bugs.openjdk.org/browse/JDK-8338981.
            if (containingClass != sealedClassItem) {
                writer.write("private ")
            }
            writer.write("static ")
        }

        // The subclass is abstract to avoid having to implement all the methods. It is non-sealed
        // because it has to be either `final`, `non-sealed` or `sealed` and `non-sealed` is the
        // only one that does not require extra work, i.e. implementing all abstract methods for
        // `final` or adding a non-empty permits for `sealed`.
        writer.write("abstract non-sealed class ")
        writer.write(simpleName)
        if (sealedClassItem.isInterface()) {
            writer.write(" implements ")
        } else {
            writer.write(" extends ")
        }
        writer.write(sealedClassItem.type().asErasedType().toTypeString())
        writer.write(" {\n")

        // Write the subclass's constructor.
        writer.write("private ")
        writer.write(simpleName)
        writer.write("() { ")

        // Delegate to the sealed class constructor. This will only be needed when the sealed class
        // is concrete, as abstract sealed class stubs only have a package level empty constructor.
        stubConstructorManager.optionalStubConstructor(sealedClassItem)?.let { superConstructor ->
            if (superConstructor.parameters().isNotEmpty()) {
                writeConstructorDelegate(writer, delegatingConstructor = null, superConstructor)
            }
        }

        // Write the remains of the constructor body.
        writer.write("throw new RuntimeException(\"Stub!\"); }\n")

        // Close the subclass.
        writer.write("}\n")
    }
}

/**
 * Manages [InaccessibleSealedSubclass] instances needed within [JavaStubWriter].
 *
 * The `sealed` subclass that makes it `non-exhaustive` must be inaccessible outside the API.
 * Ideally, that means it should be a `private` nested class. Unfortunately, that cannot always be
 * done as interfaces cannot contain nested private classes. All classes/interfaces nested inside an
 * interface are public.
 *
 * There is also a bug in javac versions < 24 where a `sealed` class cannot list private subclasses
 * that are nested within itself in the `permits` list. See
 * [JDK-8338981](https://bugs.openjdk.org/browse/JDK-8338981).
 */
internal class InaccessibleSealedSubclassManager(
    private val stubConstructorManager: StubConstructorManager,
) {
    /**
     * Map from the containing [ClassItem] to the list of [InaccessibleSealedSubclass]es that will
     * be nested within it.
     *
     * A containing [ClassItem] of `null` maps to the list of [InaccessibleSealedSubclass]es that
     * will need to be added as a top-level, package-private class in the same file as the `sealed`
     * class.
     */
    private val map = mutableMapOf<ClassItem?, MutableList<InaccessibleSealedSubclass>>()

    /**
     * Create an [InaccessibleSealedSubclass] for [sealedClassItem] and add it to the correct
     * location within the file containing [sealedClassItem].
     *
     * That location will be the closest enclosing class (not interface) of [sealedClassItem], which
     * can also be [sealedClassItem] itself.
     *
     * Returns the [ClassTypeItem] for the [InaccessibleSealedSubclass] that will be used in the
     * permits list.
     */
    fun addSubclass(sealedClassItem: ClassItem): ClassTypeItem {
        // Find the containing class, if any, to which the subclass should be added.
        // Interfaces cannot have private nested classes.
        var containingClass: ClassItem? = sealedClassItem

        // Search for the closest enclosing class, constructing a simple name that incorporates the
        // names of all containing interfaces.
        val simpleName = buildString {
            append(NON_EXHAUSTIVE_SUBCLASS_NAME)

            while (containingClass?.isInterface() == true) {
                // Prepend an "_" to separate the interface name from the current name.
                insert(0, "_")
                // Prepend the interface name before the "_".
                insert(0, containingClass.simpleName())

                // Check the next outermost containing class.
                containingClass = containingClass.containingClass()
            }
        }

        // Construct a [ClassTypeItem] for the subclass type that will be used in the permits list.
        val subclassType =
            if (containingClass == null) {
                val qualifiedPackageName = sealedClassItem.containingPackage().qualifiedName()
                TypeItem.createClassType(
                    TypeModifiers.emptyNonNullModifiers,
                    qualifiedName = "$qualifiedPackageName.$simpleName",
                    arguments = emptyList(),
                    outerClassType = null,
                )
            } else {
                TypeItem.createClassType(
                    TypeModifiers.emptyNonNullModifiers,
                    qualifiedName = "${containingClass.qualifiedName()}.$simpleName",
                    arguments = emptyList(),
                    outerClassType = containingClass.type().asErasedType(),
                )
            }

        // Create the subclass instance.
        val subclass =
            InaccessibleSealedSubclass(
                containingClass,
                sealedClassItem,
                subclassType,
            )

        // Add it to the list of subclasses that will be nested within the containingClass.
        val subclasses = map.computeIfAbsent(containingClass) { mutableListOf() }
        subclasses.add(subclass)

        // Return the type for use in a permits list.
        return subclassType
    }

    /**
     * Write [InaccessibleSealedSubclass] that are nested within [containingClass] to [writer].
     *
     * If [containingClass] is `null` then this will write the top-level, package-private
     * subclasses.
     *
     * Any [InaccessibleSealedSubclass]es associated with [containingClass] will be removed.
     */
    fun writeSubclasses(writer: PrintWriter, containingClass: ClassItem?) {
        val subclasses = map.remove(containingClass)
        if (subclasses.isNullOrEmpty()) return
        for (subclass in subclasses) {
            subclass.write(writer, stubConstructorManager)
        }
    }

    companion object {
        /**
         * Simple name for the nested subclass of a sealed class which is needed to make the sealed
         * class be treated as `non-exhaustive`.
         */
        private const val NON_EXHAUSTIVE_SUBCLASS_NAME = "_Private_"
    }
}
