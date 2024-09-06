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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.VisibilityLevel
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Predicate

class StubConstructorManager(private val codebase: Codebase) {

    private val packages: PackageList = codebase.getPackages()

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

        // Keep track of all the ClassItems that have been visited so classes are only visited once.
        val visited = Collections.newSetFromMap(IdentityHashMap<ClassItem, Boolean>())

        // Add constructors to the classes by walking up the super hierarchy and recursively add
        // constructors; we'll do it recursively to make sure that the superclass has had its
        // constructors initialized first (such that we can match the parameter lists and throws
        // signatures), and we use the tag fields to avoid looking at all the internal classes more
        // than once.
        packages
            .allClasses()
            .filter { filter.test(it) }
            .forEach { addConstructors(it, filter, visited) }
    }

    /**
     * Handle computing constructor hierarchy.
     *
     * We'll be setting several attributes: [ClassItem.stubConstructor] : The default constructor to
     * invoke in this class from subclasses. **NOTE**: This constructor may not be part of the
     * [ClassItem.constructors] list, e.g. for package private default constructors we've inserted
     * (because there were no public constructors or constructors not using hidden parameter types.)
     *
     * [ConstructorItem.superConstructor] : The default constructor to invoke.
     *
     * @param visited contains the [ClassItem]s that have already been visited; this method adds
     *   [cls] to it so [cls] will not be visited again.
     */
    private fun addConstructors(
        cls: ClassItem,
        filter: Predicate<Item>,
        visited: MutableSet<ClassItem>
    ) {
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

        if (cls in visited) {
            return
        }

        // Don't add constructors to interfaces, enums, annotations, etc
        if (!cls.isClass()) {
            return
        }

        // Remember that we have visited this class so that it is not visited again. This does not
        // strictly need to be done before visiting the super classes as there should not be cycles
        // in the class hierarchy. However, if due to some invalid input there is then doing this
        // here will prevent those cycles from causing a stack overflow.
        visited.add(cls)

        // First handle its super class hierarchy to make sure that we've already constructed super
        // classes.
        val superClass = cls.filteredSuperclass(filter)
        superClass?.let { addConstructors(it, filter, visited) }

        val superDefaultConstructor = superClass?.stubConstructor
        if (superDefaultConstructor != null) {
            cls.constructors().forEach { constructor ->
                constructor.superConstructor = superDefaultConstructor
            }
        }

        // Find default constructor, if one doesn't exist
        val filteredConstructors = cls.filteredConstructors(filter).toList()
        cls.stubConstructor =
            if (filteredConstructors.isNotEmpty()) {
                // Try to pick the constructor, select first by fewest throwables,
                // then fewest parameters, then based on order in listFilter.test(cls)
                filteredConstructors.reduce { first, second -> pickBest(first, second) }
            } else if (
                cls.constructors().isNotEmpty() ||
                    // For text based codebase, stub constructor needs to be generated even if
                    // cls.constructors() is empty, so that public default constructor is not
                    // created.
                    cls.codebase.preFiltered
            ) {

                // No accessible constructors are available so a package private constructor is
                // created. Technically, the stub now has a constructor that isn't available at
                // runtime, but apps creating subclasses inside the android.* package is not
                // supported.
                cls.createDefaultConstructor(VisibilityLevel.PACKAGE_PRIVATE).also {
                    it.superConstructor = superDefaultConstructor
                }
            } else {
                null
            }
    }

    private fun pickBest(current: ConstructorItem, next: ConstructorItem): ConstructorItem {
        val currentThrowsCount = current.throwsTypes().size
        val nextThrowsCount = next.throwsTypes().size

        return if (currentThrowsCount < nextThrowsCount) {
            current
        } else if (currentThrowsCount > nextThrowsCount) {
            next
        } else {
            val currentParameterCount = current.parameters().size
            val nextParameterCount = next.parameters().size
            if (currentParameterCount <= nextParameterCount) {
                current
            } else next
        }
    }
}
