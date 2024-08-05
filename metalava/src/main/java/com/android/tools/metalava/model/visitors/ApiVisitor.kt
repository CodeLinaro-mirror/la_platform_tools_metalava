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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.PackageFilter
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import java.util.function.Predicate

open class ApiVisitor(
    /**
     * Whether constructors should be visited as part of a [#visitMethod] call instead of just a
     * [#visitConstructor] call. Helps simplify visitors that don't care to distinguish between the
     * two cases. Defaults to true.
     */
    visitConstructorsAsMethods: Boolean = true,
    /**
     * Whether inner classes should be visited "inside" a class; when this property is true, inner
     * classes are visited before the [#afterVisitClass] method is called; when false, it's done
     * afterwards. Defaults to false.
     */
    nestInnerClasses: Boolean = false,

    /** Whether to include inherited fields too */
    val inlineInheritedFields: Boolean = true,

    /** Comparator to sort methods with. */
    val methodComparator: Comparator<MethodItem> = MethodItem.comparator,

    /** The filter to use to determine if we should emit an item */
    val filterEmit: Predicate<Item>,

    /** The filter to use to determine if we should emit a reference to an item */
    val filterReference: Predicate<Item>,

    /**
     * Whether this visitor should visit elements that have not been annotated with one of the
     * annotations passed in using the --show-annotation flag. This is normally true, but signature
     * files sometimes sets this to false so the signature file only contains the "diff" of the
     * annotated API relative to the base API.
     */
    val showUnannotated: Boolean = true,

    /** Configuration that may come from the command line. */
    config: Config,
) : BaseItemVisitor(visitConstructorsAsMethods, nestInnerClasses) {

    private val packageFilter: PackageFilter? = config.packageFilter

    /**
     * Contains configuration for [ApiVisitor] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        val packageFilter: PackageFilter? = null,

        /** Configuration for any [ApiPredicate] instances this needs to create. */
        val apiPredicateConfig: ApiPredicate.Config = ApiPredicate.Config()
    )

    constructor(
        /**
         * Whether constructors should be visited as part of a [#visitMethod] call instead of just a
         * [#visitConstructor] call. Helps simplify visitors that don't care to distinguish between
         * the two cases. Defaults to true.
         */
        visitConstructorsAsMethods: Boolean = true,
        /**
         * Whether inner classes should be visited "inside" a class; when this property is true,
         * inner classes are visited before the [#afterVisitClass] method is called; when false,
         * it's done afterwards. Defaults to false.
         */
        nestInnerClasses: Boolean = false,

        /** Whether to ignore APIs with annotations in the --show-annotations list */
        ignoreShown: Boolean = true,

        /** Whether to match APIs marked for removal instead of the normal API */
        remove: Boolean = false,

        /** Comparator to sort methods with. */
        methodComparator: Comparator<MethodItem> = MethodItem.comparator,

        /**
         * The filter to use to determine if we should emit an item. If null, the default value is
         * an [ApiPredicate] based on the values of [remove], [includeApisForStubPurposes],
         * [config], and [ignoreShown].
         */
        filterEmit: Predicate<Item>? = null,

        /**
         * The filter to use to determine if we should emit a reference to an item. If null, the
         * default value is an [ApiPredicate] based on the values of [remove] and [config].
         */
        filterReference: Predicate<Item>? = null,

        /**
         * Whether to include "for stub purposes" APIs.
         *
         * See [ApiPredicate.includeOnlyForStubPurposes]
         */
        includeApisForStubPurposes: Boolean = true,

        /** Configuration that may come from the command line. */
        config: Config,
    ) : this(
        visitConstructorsAsMethods = visitConstructorsAsMethods,
        nestInnerClasses = nestInnerClasses,
        inlineInheritedFields = true,
        methodComparator = methodComparator,
        filterEmit = filterEmit
                ?: ApiPredicate(
                    matchRemoved = remove,
                    includeApisForStubPurposes = includeApisForStubPurposes,
                    config = config.apiPredicateConfig.copy(ignoreShown = ignoreShown),
                ),
        filterReference = filterReference
                ?: ApiPredicate(
                    ignoreRemoved = remove,
                    config = config.apiPredicateConfig.copy(ignoreShown = true),
                ),
        config = config,
    )

    /**
     * Visit a [List] of [VisitCandidate]s after sorting it into order using
     * [ClassItem.classNameSorter] on [VisitCandidate.cls].
     */
    private fun visitClassList(classes: List<VisitCandidate>) {
        classes.sortedWith(Comparator.comparing({ it.cls }, ClassItem.classNameSorter())).forEach {
            vc ->
            vc.accept()
        }
    }

    override fun visit(cls: ClassItem) {
        // Get a VisitCandidate and visit it, if needed.
        getVisitCandidateIfNeeded(cls)?.accept()
    }

    /** Recursively flatten the class nesting. */
    private fun flattenClassNesting(cls: ClassItem): Sequence<ClassItem> =
        sequenceOf(cls) + cls.innerClasses().asSequence().flatMap { flattenClassNesting(it) }

    override fun visit(pkg: PackageItem) {
        if (!pkg.emit) {
            return
        }

        // Get the list of classes to visit directly. If nested classes are to appear as nested
        // then just visit the top level classes directly and then the inner classes will be visited
        // by their containing classes. Otherwise, flatten the nested classes and treat them all as
        // top level classes.
        val classesToVisitDirectly =
            pkg.topLevelClasses()
                .let { topLevelClasses ->
                    if (nestInnerClasses) topLevelClasses
                    else topLevelClasses.flatMap { flattenClassNesting(it) }
                }
                .mapNotNull { getVisitCandidateIfNeeded(it) }
                .toList()

        // If none of the classes in this package will be visited them ignore the package entirely.
        if (classesToVisitDirectly.isEmpty()) return

        visitItem(pkg)
        visitPackage(pkg)

        visitClassList(classesToVisitDirectly)

        afterVisitPackage(pkg)
        afterVisitItem(pkg)
    }

    /** @return Whether this class is generally one that we want to recurse into */
    open fun include(cls: ClassItem): Boolean {
        if (skip(cls)) {
            return false
        }
        if (packageFilter != null && !packageFilter.matches(cls.containingPackage())) {
            return false
        }

        return cls.emit
    }

    /**
     * Returns a [VisitCandidate] if the [cls] needs to be visited, otherwise return `null`.
     *
     * The [cls] needs to be visited if it passes the various checks that determine whether it
     * should be emitted as part of an API surface as determined by [filterEmit] and
     * [filterReference].
     */
    private fun getVisitCandidateIfNeeded(cls: ClassItem): VisitCandidate? {
        if (!include(cls)) return null

        // Check to see whether this class should be emitted in its entirety. If not then it may
        // still be emitted if it contains emittable members.
        val emit = filterEmit.test(cls)

        // If the class is emitted then create a VisitCandidate immediately.
        if (emit) return VisitCandidate(cls)

        // Check to see if the class could be emitted if it contains emittable members. If not then
        // return `null` to ignore this class. This will happen for a hidden class, e.g. package
        // private, that implements/overrides methods from the API.
        if (!filterReference.test(cls)) return null

        // Create a VisitCandidate to encapsulate the emittable members, if any.
        val vc = VisitCandidate(cls)

        // Check to see if the class has any emittable members, if not return `null` to ignore this
        // class.
        if (vc.containsNoEmittableMembers()) return null

        // The class is emittable so return it.
        return vc
    }

    /**
     * Encapsulates a [ClassItem] that is being visited and its members, filtered by [filterEmit],
     * and sorted by various members specific comparators.
     *
     * The purpose of this is to store the lists of filtered and sorted members that were created
     * during filtering of the classes in the [PackageItem] visit method. They need to be stored as
     * they can take a long time to generate and will be needed again when visiting the class
     * contents.
     */
    inner class VisitCandidate(val cls: ClassItem) {

        /**
         * If the list this is called upon is empty then just return [emptyList], else apply the
         * [transform] to the list and return that.
         */
        private inline fun <T> List<T>.mapIfNotEmpty(transform: List<T>.() -> List<T>) =
            if (isEmpty()) emptyList() else transform(this)

        /**
         * Sort the sequence into a [List].
         *
         * The standard [Sequence.sortedWith] will sort it into a list and then return a sequence
         * wrapper which would then have to be converted back into a list. Instead, this just sorts
         * it into a [List] and returns that.
         */
        private fun <T> Sequence<T>.sortToList(comparator: Comparator<in T>) =
            if (none()) emptyList()
            else
                toMutableList().let {
                    // Sort the list in place.
                    it.sortWith(comparator)
                    // Return the sorter list.
                    it
                }

        private val constructors =
            cls.constructors().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(methodComparator)
            }

        private val methods =
            cls.methods().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(methodComparator)
            }

        private val fields by
            lazy(LazyThreadSafetyMode.NONE) {
                val fieldSequence =
                    if (inlineInheritedFields) {
                        cls.filteredFields(filterEmit, showUnannotated).asSequence()
                    } else {
                        cls.fields().asSequence().filter { filterEmit.test(it) }
                    }

                // Sort the fields so that enum constants come first.
                fieldSequence.sortToList(FieldItem.comparatorEnumConstantFirst)
            }

        private val properties =
            cls.properties().mapIfNotEmpty {
                asSequence().filter { filterEmit.test(it) }.sortToList(PropertyItem.comparator)
            }

        /** Whether the class body contains any emmittable [MemberItem]s. */
        fun containsNoEmittableMembers() =
            constructors.isEmpty() && methods.isEmpty() && fields.isEmpty() && properties.isEmpty()

        fun accept() {
            visitItem(cls)
            visitClass(cls)

            for (constructor in constructors) {
                constructor.accept(this@ApiVisitor)
            }

            for (method in methods) {
                method.accept(this@ApiVisitor)
            }

            for (property in properties) {
                property.accept(this@ApiVisitor)
            }
            for (field in fields) {
                field.accept(this@ApiVisitor)
            }

            if (nestInnerClasses) { // otherwise done in visit(PackageItem)
                visitClassList(cls.innerClasses().mapNotNull { getVisitCandidateIfNeeded(it) })
            }

            afterVisitClass(cls)
            afterVisitItem(cls)
        }
    }
}
