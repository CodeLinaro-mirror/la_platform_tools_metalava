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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.VisibilityLevel
import java.util.function.Predicate

class StubConstructorManager(codebase: Codebase) {

    private val packages: PackageList = codebase.getPackages()

    /** Map from [ClassItem] to [StubConstructors]. */
    private val classToStubConstructors = mutableMapOf<ClassItem, StubConstructors>()

    /**
     * Contains information about constructors needed when generating stubs for a specific class.
     */
    private class StubConstructors(
        /**
         * The default constructor to invoke on the class from subclasses.
         *
         * Note that in some cases [stubConstructor] may not be in [ClassItem.constructors], e.g.
         * when we need to create a constructor to match a public parent class with a non-default
         * constructor and the one in the code is not a match, e.g. is marked `@hide`.
         *
         * Is `null` if the class has a default constructor that is accessible.
         */
        val stubConstructor: ConstructorItem?,

        /**
         * The constructor that constructors in a stub class must delegate to in their `super` call.
         *
         * Is `null` if the super class has a default constructor.
         */
        val superConstructor: ConstructorItem?,
    ) {
        companion object {
            val EMPTY = StubConstructors(null, null)
        }
    }

    fun addConstructors(filter: Predicate<Item>) {
        // Let's say we have
        //  class GrandParent { public GrandParent(int) {} }
        //  class Parent {  Parent(int) {} }
        //  class Child { public Child(int) {} }
        //
        // Here Parent's constructor is not public. For normal stub generation we'd end up with
        // this:
        //  class GrandParent { public GrandParent(int) {} }
        //  class Parent { }
        //  class Child { public Child(int) {} }
        //
        // This doesn't compile - Parent can't have a default constructor since there isn't
        // one for it to invoke on GrandParent.
        //
        // we can generate a fake constructor instead, such as
        //   Parent() { super(0); }
        //
        // But it's hard to do this lazily; what if we're generating the Child class first?
        // Therefore, we'll instead walk over the hierarchy and insert these constructors into the
        // Item hierarchy such that code generation can find them.
        //
        // We also need to handle the throws list, so we can't just unconditionally insert package
        // private constructors

        // Add constructors to the classes by walking up the super hierarchy and recursively add
        // constructors; we'll do it recursively to make sure that the superclass has had its
        // constructors initialized first (such that we can match the parameter lists and throws
        // signatures), and we use the tag fields to avoid looking at all the internal classes more
        // than once.
        packages.allClasses().filter { filter.test(it) }.forEach { addConstructors(it, filter) }
    }

    /**
     * Handle computing constructor hierarchy.
     *
     * We'll be setting several attributes: [StubConstructors.stubConstructor] : The default
     * constructor to invoke in this class from subclasses. **NOTE**: This constructor may not be
     * part of the [ClassItem.constructors] list, e.g. for package private default constructors
     * we've inserted (because there were no public constructors or constructors not using hidden
     * parameter types.)
     *
     * [StubConstructors.superConstructor] : The super constructor to invoke.
     */
    private fun addConstructors(
        cls: ClassItem,
        filter: Predicate<Item>,
    ): StubConstructors {

        // Don't add constructors to interfaces, enums, annotations, etc
        if (!cls.isClass()) {
            return StubConstructors.EMPTY
        }

        // What happens if we have
        //  package foo:
        //     public class A { public A(int) }
        //  package bar
        //     public class B extends A { public B(int) }
        // If we just try inserting package private constructors here things will NOT work:
        //  package foo:
        //     public class A { public A(int); A() {} }
        //  package bar
        //     public class B extends A { public B(int); B() }
        // because A <() is not accessible from B() -- it's outside the same package.
        //
        // So, we'll need to model the real constructors for all the scenarios where that works.
        //
        // The remaining challenge is that there will be some gaps: when we don't have a default
        // constructor, subclass constructors will have to have an explicit super(args) call to pick
        // the parent constructor to use. And which one? It generally doesn't matter; just pick one,
        // but unfortunately, the super constructor can throw exceptions, and in that case the
        // subclass constructor must also throw all those exceptions (you can't surround a super
        // call with try/catch.)
        //
        // Luckily, this does not seem to be an actual problem with any of the source code that
        // metalava currently processes. If it did become a problem then the solution would be to
        // pick super constructors with a compatible set of throws.

        // If this class has already been visited then return the StubConstructors that was created.
        classToStubConstructors[cls]?.let {
            return it
        }

        // Remember that we have visited this class so that it is not visited again. This does not
        // strictly need to be done before visiting the super classes as there should not be cycles
        // in the class hierarchy. However, if due to some invalid input there is then doing this
        // here will prevent those cycles from causing a stack overflow. This will be overridden
        // with the actual constructors below.
        classToStubConstructors[cls] = StubConstructors.EMPTY

        // First handle its super class hierarchy to make sure that we've already constructed super
        // classes.
        val superClass = cls.filteredSuperclass(filter)
        val superClassConstructors = superClass?.let { addConstructors(it, filter) }

        val superDefaultConstructor = superClassConstructors?.stubConstructor

        // Find constructor subclasses should delegate to, creating one if necessary. If the stub
        // will contain a no-args constructor then that is represented as `null` to allow it to be
        // optimized below.
        val filteredConstructors = cls.filteredConstructors(filter).toList()
        val stubConstructor =
            if (filteredConstructors.isNotEmpty()) {
                // Pick the best constructor. If that is a no-args constructor then represent that
                // as `null`.
                pickBest(filteredConstructors).takeUnless { it.parameters().isEmpty() }
            } else {
                // No accessible constructors are available (not even a default implicit
                // constructor) so a package private constructor is needed. Technically, this will
                // result in the stub class having a constructor that isn't available at runtime,
                // but creating subclasses in API packages is not supported.
                cls.createDefaultConstructor(VisibilityLevel.PACKAGE_PRIVATE)
            }

        // If neither the constructors in this class nor its subclasses need to add a `super(...)`
        // call then use a shared object.
        if (stubConstructor == null && superDefaultConstructor == null) {
            return StubConstructors.EMPTY
        }

        return StubConstructors(
                stubConstructor = stubConstructor,
                superConstructor = superDefaultConstructor,
            )
            .also {
                // Save it away for retrieval by subclasses.
                classToStubConstructors[cls] = it
            }
    }

    companion object {
        /**
         * Comparator to pick the best [ConstructorItem] to which derived stub classes will
         * delegate.
         *
         * Uses the following rules:
         * 1. Fewest throwables as they have to be propagated down to constructors that delegate to
         *    it.
         * 2. Fewest parameters to reduce the size of the `super(...)` call.
         * 3. Shortest erased parameter types as that should reduce the size of the `super(...)`
         *    call.
         * 4. Total ordering defined by [CallableItem.comparator] to ensure consistent behavior.
         *
         * Returns less than zero if the first [ConstructorItem] passed to `compare(c1, c2)` is the
         * best option, more if the second [ConstructorItem] is the best option and zero if they are
         * the same.
         */
        private val bestStubConstructorComparator: Comparator<ConstructorItem> =
            Comparator.comparingInt<ConstructorItem?>({ it.throwsTypes().size })
                .thenComparingInt({ it.parameters().size })
                .thenComparingInt({
                    it.parameters().sumOf { it.type().toErasedTypeString().length }
                })
                .thenComparing(CallableItem.comparator)
    }

    /**
     * Pick the best [ConstructorItem] to which derived stub classes will delegate.
     *
     * Selects the first [ConstructorItem] in [constructors] which compares less to or equal to all
     * the other [ConstructorItem]s in the list when compared using [bestStubConstructorComparator].
     * That defines a total order so the result is independent of the order of [constructors].
     */
    private fun pickBest(constructors: List<ConstructorItem>): ConstructorItem {
        // Try to pick the best constructor to which derived stub classes can delegate.
        return constructors.reduce { first, second ->
            val result = bestStubConstructorComparator.compare(first, second)
            if (result <= 0) first else second
        }
    }

    /**
     * Get the optional synthetic constructor, if created, for [classItem].
     *
     * If a [ClassItem] does not have an accessible constructor then one will be synthesized for use
     * by subclasses. This method returns that constructor, or `null` if there was no synthetic
     * constructor.
     */
    fun optionalSyntheticConstructor(classItem: ClassItem): ConstructorItem? {
        val stubConstructor = classToStubConstructors[classItem]?.stubConstructor ?: return null
        if (stubConstructor in classItem.constructors()) return null
        return stubConstructor
    }

    /** Get the optional super constructor, if needed, for [classItem]. */
    fun optionalSuperConstructor(classItem: ClassItem): ConstructorItem? {
        return classToStubConstructors[classItem]?.superConstructor
    }
}
