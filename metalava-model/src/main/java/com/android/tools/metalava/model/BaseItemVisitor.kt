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

package com.android.tools.metalava.model

open class BaseItemVisitor(
    /**
     * Whether nested classes should be visited "inside" a class; when this property is true, nested
     * classes are visited before the [#afterVisitClass] method is called; when false, it's done
     * afterwards. Defaults to false.
     */
    val preserveClassNesting: Boolean = false,

    /**
     * Determines whether this will visit [ParameterItem]s or not.
     *
     * If this is `true` then [ParameterItem]s will be visited, and passed to [visitItem],
     * [visitParameter] and [afterVisitItem] in that order. Otherwise, they will not be visited.
     *
     * Defaults to `true` as that is the safest option which avoids inadvertently ignoring them.
     */
    protected val visitParameterItems: Boolean = true,
) : ItemVisitor {
    /** Calls [visitItem] before invoking [body] after which it calls [afterVisitItem]. */
    protected inline fun <T : Item> wrapBodyWithCallsToVisitMethodsForItem(
        item: T,
        body: () -> Unit
    ) {
        visitItem(item)
        body()
        afterVisitItem(item)
    }

    /**
     * Calls [visitItem], then [visitSelectableItem] before invoking [body] after which it calls
     * [afterVisitSelectableItem] and finally [afterVisitItem].
     */
    protected inline fun <T : SelectableItem> wrapBodyWithCallsToVisitMethodsForSelectableItem(
        item: T,
        body: () -> Unit
    ) {
        wrapBodyWithCallsToVisitMethodsForItem(item) {
            visitSelectableItem(item)
            body()
            afterVisitSelectableItem(item)
        }
    }

    override fun visit(cls: ClassItem) {
        if (skip(cls)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(cls) {
            visitClass(cls)

            for (constructor in cls.constructors()) {
                constructor.accept(this)
            }

            for (method in cls.methods()) {
                method.accept(this)
            }

            for (property in cls.properties()) {
                property.accept(this)
            }

            if (cls.isEnum()) {
                // In enums, visit the enum constants first, then the fields
                for (field in cls.fields()) {
                    if (field.isEnumConstant()) {
                        field.accept(this)
                    }
                }
                for (field in cls.fields()) {
                    if (!field.isEnumConstant()) {
                        field.accept(this)
                    }
                }
            } else {
                for (field in cls.fields()) {
                    field.accept(this)
                }
            }

            if (preserveClassNesting) {
                for (nestedCls in cls.nestedClasses()) {
                    nestedCls.accept(this)
                }
            } // otherwise done in visit(PackageItem)

            afterVisitClass(cls)
        }
    }

    override fun visit(field: FieldItem) {
        if (skip(field)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(field) { visitField(field) }
    }

    override fun visit(constructor: ConstructorItem) {
        visitMethodOrConstructor(constructor) { visitConstructor(it) }
    }

    override fun visit(method: MethodItem) {
        visitMethodOrConstructor(method) { visitMethod(it) }
    }

    private inline fun <T : CallableItem> visitMethodOrConstructor(
        callable: T,
        dispatch: (T) -> Unit
    ) {
        if (skip(callable)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(callable) {
            visitCallable(callable)

            // Call the specific visitX method for the CallableItem subclass.
            dispatch(callable)

            if (visitParameterItems) {
                for (parameter in callable.parameters()) {
                    parameter.accept(this)
                }
            }
        }
    }

    /**
     * Get the package's classes to visit directly.
     *
     * If nested classes are to appear as nested within their containing classes then this will just
     * return the package's top level classes. It will then be the responsibility of
     * `visit(ClassItem)` to visit the nested classes. Otherwise, this will return a flattened
     * sequence of each class followed by its nested classes.
     */
    protected fun packageClassesAsSequence(pkg: PackageItem) =
        if (preserveClassNesting) pkg.topLevelClasses().asSequence() else pkg.allClasses()

    override fun visit(codebase: Codebase) {
        visitCodebase(codebase)
        codebase.getPackages().packages.forEach { it.accept(this) }
        afterVisitCodebase(codebase)
    }

    override fun visit(pkg: PackageItem) {
        if (skipPackage(pkg)) {
            return
        }

        if (skip(pkg)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(pkg) {
            visitPackage(pkg)

            for (cls in packageClassesAsSequence(pkg)) {
                cls.accept(this)
            }

            for (typeAlias in pkg.typeAliases()) {
                typeAlias.accept(this)
            }

            afterVisitPackage(pkg)
        }
    }

    /**
     * Ignore any packages whose `emit` property is `false`. That is basically any package that does
     * not contain at least one class that could be emitted as part of the API.
     */
    open fun skipPackage(pkg: PackageItem) = !pkg.emit

    override fun visit(parameter: ParameterItem) {
        if (skip(parameter)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForItem(parameter) { visitParameter(parameter) }
    }

    override fun visit(property: PropertyItem) {
        if (skip(property)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(property) { visitProperty(property) }
    }

    override fun visit(typeAlias: TypeAliasItem) {
        if (skip(typeAlias)) {
            return
        }

        wrapBodyWithCallsToVisitMethodsForSelectableItem(typeAlias) { visitTypeAlias(typeAlias) }
    }

    open fun skip(item: Item): Boolean = false

    /**
     * Visits any [Item].
     *
     * This is always called BEFORE other more specialized visit methods, such as [visitClass].
     */
    open fun visitItem(item: Item) {}

    /**
     * Visits any [SelectableItem], i.e. everything for which [visitItem] is called except
     * [ParameterItem]s.
     *
     * This is always called BEFORE other more specialized visit methods, such as [visitClass].
     */
    open fun visitSelectableItem(item: SelectableItem) {}

    open fun visitCodebase(codebase: Codebase) {}

    open fun visitPackage(pkg: PackageItem) {}

    open fun visitClass(cls: ClassItem) {}

    open fun visitCallable(callable: CallableItem) {}

    open fun visitConstructor(constructor: ConstructorItem) {}

    open fun visitMethod(method: MethodItem) {}

    open fun visitField(field: FieldItem) {}

    /** Visits a [ParameterItem]. */
    open fun visitParameter(parameter: ParameterItem) {}

    open fun visitProperty(property: PropertyItem) {}

    open fun visitTypeAlias(typeAlias: TypeAliasItem) {}

    /**
     * Visits any [SelectableItem], i.e. everything for which [afterVisitItem] is called except
     * [ParameterItem]s.
     *
     * This is always called AFTER other more specialized visit methods, such as [afterVisitClass].
     */
    open fun afterVisitSelectableItem(item: SelectableItem) {}

    /**
     * Visits any [Item], except for [TypeParameterItem].
     *
     * This is always called AFTER other more specialized visit methods, such as [afterVisitClass].
     */
    open fun afterVisitItem(item: Item) {}

    open fun afterVisitCodebase(codebase: Codebase) {}

    open fun afterVisitPackage(pkg: PackageItem) {}

    open fun afterVisitClass(cls: ClassItem) {}
}
