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

package com.android.tools.metalava

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.EmittedOnlyPredicate
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.testOrTrue
import com.android.tools.metalava.model.visitors.ApiSurfaceVisitor

/**
 * Visitor which visits all items in two matching codebases and matches up the items and invokes
 * [compareItems] on each pair, or [addedItem] or [removedItem] when items are not matched
 */
open class ComparisonVisitor {
    open fun compareItems(old: Item, new: Item) {}

    open fun addedItem(new: SelectableItem) {}

    open fun removedItem(old: SelectableItem, from: SelectableItem?) {}

    open fun compareSelectableItems(old: SelectableItem, new: SelectableItem) {}

    open fun comparePackageItems(old: PackageItem, new: PackageItem) {}

    open fun compareClassItems(old: ClassItem, new: ClassItem) {}

    open fun compareCallableItems(old: CallableItem, new: CallableItem) {}

    open fun compareConstructorItems(old: ConstructorItem, new: ConstructorItem) {}

    open fun compareMethodItems(old: MethodItem, new: MethodItem) {}

    open fun compareFieldItems(old: FieldItem, new: FieldItem) {}

    open fun comparePropertyItems(old: PropertyItem, new: PropertyItem) {}

    open fun compareParameterItems(old: ParameterItem, new: ParameterItem) {}

    open fun addedPackageItem(new: PackageItem) {}

    open fun addedClassItem(new: ClassItem) {}

    open fun addedCallableItem(new: CallableItem) {}

    open fun addedConstructorItem(new: ConstructorItem) {}

    open fun addedMethodItem(new: MethodItem) {}

    open fun addedFieldItem(new: FieldItem) {}

    open fun addedPropertyItem(new: PropertyItem) {}

    open fun removedPackageItem(old: PackageItem, from: PackageItem?) {}

    open fun removedClassItem(old: ClassItem, from: SelectableItem) {}

    open fun removedCallableItem(old: CallableItem, from: ClassItem) {}

    open fun removedConstructorItem(old: ConstructorItem, from: ClassItem) {}

    open fun removedMethodItem(old: MethodItem, from: ClassItem) {}

    open fun removedFieldItem(old: FieldItem, from: ClassItem) {}

    open fun removedPropertyItem(old: PropertyItem, from: ClassItem) {}

    open fun addedCodebase(new: Codebase) {}

    open fun removedCodebase(old: Codebase) {}
}

/** Simple stack type built on top of an [ArrayList]. */
private typealias Stack<E> = ArrayList<E>

private fun <E> Stack<E>.push(e: E) {
    add(e)
}

private fun <E> Stack<E>.pop(): E = removeAt(lastIndex)

private fun <E> Stack<E>.peek(): E = last()

object CodebaseComparator {
    /**
     * Visits [old] and [new] codebases, comparing items and dispatching callbacks to [visitor] for
     * added, removed, and matching items.
     *
     * @param visitor the [ComparisonVisitor] receiving callbacks as differences and matches are
     *   found.
     * @param old the baseline or previous codebase.
     * @param new the current or newer codebase.
     * @param surfaceFilter filter matching items belonging to the specific API surface being
     *   compared (e.g. the delta surface). Only items matching this filter are considered for
     *   addition or removal callbacks. If null, all items are considered.
     * @param referenceFilter filter matching items belonging to the full reference API surface
     *   hierarchy (e.g. the target surface and all base surfaces it extends). Used to construct the
     *   item trees and to verify whether an item absent from [surfaceFilter] was moved into or
     *   inherited from a base surface (e.g. inherited methods, fields, properties, interfaces, and
     *   thrown types) rather than removed.
     */
    fun compare(
        visitor: ComparisonVisitor,
        old: Codebase,
        new: Codebase,
        surfaceFilter: FilterPredicate? = null,
        referenceFilter: FilterPredicate? = null,
    ) {
        // Algorithm: build up two trees (by nesting level); then visit the two trees.

        // Use referenceFilter so that the trees contain all items in
        // the reference API surface hierarchy (e.g. including base surfaces), allowing items moved
        // between surfaces or inherited from superclasses in base surfaces to be matched.
        val oldTree = createTree(old, referenceFilter)
        val newTree = createTree(new, referenceFilter)

        /* Debugging:
        println("Old:\n${ItemTree.prettyPrint(oldTree)}")
        println("New:\n${ItemTree.prettyPrint(newTree)}")
        */

        compare(visitor, oldTree, newTree, null, null, surfaceFilter, referenceFilter)
    }

    /**
     * Compares two [MergedCodebase] instances, dispatching callbacks to [visitor] for added,
     * removed, and matching items.
     *
     * @param visitor the [ComparisonVisitor] receiving callbacks as differences and matches are
     *   found.
     * @param old the baseline or previous merged codebase.
     * @param new the current or newer merged codebase.
     * @param surfaceFilter filter matching items belonging to the specific API surface being
     *   compared (e.g. the delta surface). Only items matching this filter are considered for
     *   addition or removal callbacks. If null, all items are considered.
     * @param referenceFilter filter matching items belonging to the full reference API surface
     *   hierarchy (e.g. the target surface and all base surfaces it extends). Used to construct the
     *   item trees and to verify whether an item absent from [surfaceFilter] was moved into or
     *   inherited from a base surface (e.g. inherited methods, fields, properties, interfaces, and
     *   thrown types) rather than removed.
     */
    fun compare(
        visitor: ComparisonVisitor,
        old: MergedCodebase,
        new: MergedCodebase,
        surfaceFilter: FilterPredicate? = null,
        referenceFilter: FilterPredicate? = null,
    ) {
        // Algorithm: build up two trees (by nesting level); then visit the two trees.

        // Use referenceFilter so that the trees contain all items in
        // the reference API surface hierarchy (e.g. including base surfaces), allowing items moved
        // between surfaces or inherited from superclasses in base surfaces to be matched.
        val oldTree = createTree(old, referenceFilter)
        val newTree = createTree(new, referenceFilter)

        /* Debugging:
        println("Old:\n${ItemTree.prettyPrint(oldTree)}")
        println("New:\n${ItemTree.prettyPrint(newTree)}")
        */

        compare(visitor, oldTree, newTree, null, null, surfaceFilter, referenceFilter)
    }

    /**
     * Compares the [old] and [new] [MultiplatformCodebase].
     *
     * If a source set [Codebase] is only present in [old], dispatches to
     * [ComparisonVisitor.removedCodebase] on [visitor].
     *
     * If a source set [Codebase] is only present in [new], dispatches to
     * [ComparisonVisitor.addedCodebase] on [visitor].
     *
     * If a source set [Codebase] is present in both [old] and [new], uses [compare] to compare all
     * elements of the [Codebase]s.
     *
     * @param visitor the [ComparisonVisitor] receiving callbacks as differences and matches are
     *   found.
     * @param old the baseline or previous multiplatform codebase.
     * @param new the current or newer multiplatform codebase.
     * @param surfaceFilter filter matching items belonging to the specific API surface being
     *   compared (e.g. the delta surface). Only items matching this filter are considered for
     *   addition or removal callbacks. If null, all items are considered.
     * @param referenceFilter filter matching items belonging to the full reference API surface
     *   hierarchy (e.g. the target surface and all base surfaces it extends). Used to construct the
     *   item trees and to verify whether an item absent from [surfaceFilter] was moved into or
     *   inherited from a base surface rather than removed.
     */
    fun compareMultiplatform(
        visitor: ComparisonVisitor,
        old: MultiplatformCodebase,
        new: MultiplatformCodebase,
        surfaceFilter: FilterPredicate? = null,
        referenceFilter: FilterPredicate? = null,
    ) {
        val allSourceSetNames = old.sourceSets + new.sourceSets
        for (sourceSetName in allSourceSetNames) {
            val oldSourceSet = old.sourceSetToCodebase[sourceSetName]
            val newSourceSet = new.sourceSetToCodebase[sourceSetName]
            when {
                oldSourceSet == null ->
                    visitor.addedCodebase(
                        requireNotNull(newSourceSet) {
                            "$sourceSetName does not exist in the old or new codebase"
                        }
                    )
                newSourceSet == null -> visitor.removedCodebase(oldSourceSet)
                else -> compare(visitor, oldSourceSet, newSourceSet, surfaceFilter, referenceFilter)
            }
        }
    }

    /**
     * Recursively compares two lists of [ItemTree]s at the same nesting level.
     *
     * @param surfaceFilter determines if an item is considered for addition or removal callbacks.
     * @param referenceFilter determines if an item is considered present in the reference API
     *   hierarchy (to distinguish items moved into base surfaces or inherited from base surfaces
     *   from removals).
     */
    private fun compare(
        visitor: ComparisonVisitor,
        oldList: List<ItemTree>,
        newList: List<ItemTree>,
        newParent: SelectableItem?,
        oldParent: SelectableItem?,
        surfaceFilter: FilterPredicate?,
        referenceFilter: FilterPredicate?,
    ) {
        // Debugging tip: You can print out a tree like this: ItemTree.prettyPrint(list)
        var index1 = 0
        var index2 = 0
        val length1 = oldList.size
        val length2 = newList.size

        while (true) {
            if (index1 < length1) {
                if (index2 < length2) {
                    // Compare the items
                    val oldTree = oldList[index1]
                    val newTree = newList[index2]
                    val old = oldTree.item()
                    val new = newTree.item()

                    val compare = compare(old, new)
                    val newMatches = surfaceFilter.testOrTrue(new)
                    val oldMatches = surfaceFilter.testOrTrue(old)
                    when {
                        compare > 0 -> {
                            index2++
                            if (newMatches) {
                                dispatchToAddedOrCompareIfItemWasMoved(
                                    new,
                                    oldParent,
                                    visitor,
                                )
                            }
                        }
                        compare < 0 -> {
                            index1++
                            if (oldMatches) {
                                dispatchToRemovedOrCompareIfItemWasMoved(
                                    old,
                                    visitor,
                                    newParent,
                                    referenceFilter,
                                )
                            }
                        }
                        else -> {
                            if (newMatches) {
                                if (oldMatches) {
                                    dispatchToCompare(visitor, old, new)
                                } else {
                                    dispatchToAddedOrCompareIfItemWasMoved(
                                        new,
                                        oldParent,
                                        visitor,
                                    )
                                }
                            } else {
                                if (oldMatches) {
                                    // If new is in the reference API, it was moved to a base
                                    // surface rather than removed from the API.
                                    val newInApi = referenceFilter.testOrTrue(new)
                                    if (newInApi) {
                                        dispatchToCompare(visitor, old, new)
                                    } else {
                                        dispatchToRemovedOrCompareIfItemWasMoved(
                                            old,
                                            visitor,
                                            newParent,
                                            referenceFilter,
                                        )
                                    }
                                }
                            }

                            // Compare the children (recurse)
                            compare(
                                visitor,
                                oldTree.children,
                                newTree.children,
                                newTree.item(),
                                oldTree.item(),
                                surfaceFilter,
                                referenceFilter,
                            )

                            index1++
                            index2++
                        }
                    }
                } else {
                    // All the remaining items in oldList have been deleted
                    while (index1 < length1) {
                        val oldTree = oldList[index1++]
                        val old = oldTree.item()
                        if (surfaceFilter.testOrTrue(old)) {
                            dispatchToRemovedOrCompareIfItemWasMoved(
                                old,
                                visitor,
                                newParent,
                                referenceFilter,
                            )
                        }
                    }
                }
            } else if (index2 < length2) {
                // All the remaining items in newList have been added
                while (index2 < length2) {
                    val newTree = newList[index2++]
                    val new = newTree.item()

                    if (surfaceFilter.testOrTrue(new)) {
                        dispatchToAddedOrCompareIfItemWasMoved(new, oldParent, visitor)
                    }
                }
            } else {
                break
            }
        }
    }

    /**
     * Dispatch calls to [ComparisonVisitor.compareParameterItems] for each pair of [ParameterItem]s
     * in [oldParameters] and [newParameters].
     *
     * The [oldParameters] and [newParameters] are guaranteed to have the same number of parameters
     * as they come from two [MethodItem]s that compare equal according to [comparator].
     */
    private fun dispatchCompareParameters(
        visitor: ComparisonVisitor,
        oldParameters: List<ParameterItem>,
        newParameters: List<ParameterItem>,
    ) {
        require(oldParameters.size == newParameters.size)
        for ((oldParameter, newParameter) in oldParameters.zip(newParameters)) {
            visitor.compareItems(oldParameter, newParameter)
            visitor.compareParameterItems(oldParameter, newParameter)
        }
    }

    /**
     * Checks to see whether [new] has actually been added or if it was just moved from elsewhere
     * and dispatch to the appropriate method.
     */
    private fun dispatchToAddedOrCompareIfItemWasMoved(
        new: SelectableItem,
        oldParent: SelectableItem?,
        visitor: ComparisonVisitor,
    ) {
        // If it's a method/property, we may not have added a new method/property,
        // we may simply have inherited it previously and overriding
        // it now (or in the case of signature files, identical overrides
        // are not explicitly listed and therefore not added to the model)
        val inherited =
            if (new is MethodItem && oldParent is ClassItem) {
                oldParent
                    .findMethod(
                        template = new,
                        includeSuperClasses = true,
                        includeInterfaces = true
                    )
                    ?.duplicate(oldParent)
            } else if (new is PropertyItem && oldParent is ClassItem) {
                oldParent
                    .findProperty(new, includeSuperClasses = true, includeInterfaces = true)
                    ?.duplicate(oldParent)
            } else {
                null
            }

        if (inherited != null) {
            dispatchToCompare(visitor, inherited, new)
        } else {
            dispatchToAdded(visitor, new)
        }
    }

    /** Dispatch to the [Item] specific `added(...)` method. */
    private fun dispatchToAdded(visitor: ComparisonVisitor, item: SelectableItem) {
        visitor.addedItem(item)

        if (item is CallableItem) {
            visitor.addedCallableItem(item)
        }

        when (item) {
            is PackageItem -> visitor.addedPackageItem(item)
            is ClassItem -> visitor.addedClassItem(item)
            is ConstructorItem -> visitor.addedConstructorItem(item)
            is MethodItem -> visitor.addedMethodItem(item)
            is FieldItem -> visitor.addedFieldItem(item)
            is PropertyItem -> visitor.addedPropertyItem(item)
            else -> error("unexpected addition of $item")
        }
    }

    /**
     * Checks to see whether [old] has actually been removed or if it was just moved from elsewhere
     * and dispatch to the appropriate method.
     */
    private fun dispatchToRemovedOrCompareIfItemWasMoved(
        old: SelectableItem,
        visitor: ComparisonVisitor,
        newParent: SelectableItem?,
        referenceFilter: FilterPredicate?,
    ) {
        // If it's a method, we may not have removed the method, we may have simply
        // removed an override and are now inheriting the method from a superclass.
        // Alternatively, it may have always truly been an inherited method, but if the base
        // class was hidden then the signature file may have listed the method as being
        // declared on the subclass
        val inheritedMethod =
            if (old is MethodItem && newParent is ClassItem) {
                // Use an updated filter when searching for a matching method: if an [Item] is an
                // unstable API that will be reverted then it will not be treated as if it was
                // removed. That is because reverting it will replace it with the old item against
                // which it is being compared in this compatibility check. So, while this specific
                // item will not appear in the API the old item will and so it has not been removed.
                val methodFilter = referenceFilter?.or(RevertedPredicate)

                // Find an element which matches the methodFilter
                val superMethod = newParent.findPredicateMethodWithSuper(old, methodFilter)
                superMethod?.duplicate(newParent)
            } else {
                null
            }

        if (inheritedMethod != null) {
            dispatchToCompare(visitor, old, inheritedMethod)
            return
        }

        // fields may also be moved to superclasses like methods may
        val inheritedField =
            if (old is FieldItem && newParent is ClassItem) {
                val superField =
                    newParent.findField(
                        fieldName = old.name(),
                        includeSuperClasses = true,
                        includeInterfaces = true
                    )

                if (superField != null && referenceFilter.testOrTrue(superField)) {
                    superField.duplicate(newParent)
                } else {
                    null
                }
            } else {
                null
            }

        if (inheritedField != null) {
            dispatchToCompare(visitor, old, inheritedField)
            return
        }

        // A property may have been moved to a superclass.
        if (old is PropertyItem && newParent is ClassItem) {
            val superProperty =
                newParent.findProperty(old, includeSuperClasses = true, includeInterfaces = true)
            if (superProperty != null && referenceFilter.testOrTrue(superProperty)) {
                dispatchToCompare(visitor, old, superProperty.duplicate(newParent))
                return
            }
        }

        dispatchToRemoved(visitor, old, newParent)
    }

    /** Dispatch to the [Item] specific `removed(...)` method. */
    private fun dispatchToRemoved(
        visitor: ComparisonVisitor,
        item: SelectableItem,
        from: SelectableItem?
    ) {
        visitor.removedItem(item, from)

        if (item is CallableItem) {
            visitor.removedCallableItem(item, from as ClassItem)
        }

        when (item) {
            is PackageItem -> visitor.removedPackageItem(item, from as PackageItem?)
            is ClassItem -> visitor.removedClassItem(item, from as SelectableItem)
            is ConstructorItem -> visitor.removedConstructorItem(item, from as ClassItem)
            is MethodItem -> visitor.removedMethodItem(item, from as ClassItem)
            is FieldItem -> visitor.removedFieldItem(item, from as ClassItem)
            is PropertyItem -> visitor.removedPropertyItem(item, from as ClassItem)
            else -> error("unexpected removal of $item")
        }
    }

    /** Dispatch to the [Item] specific `compare(...)` method. */
    private fun dispatchToCompare(
        visitor: ComparisonVisitor,
        old: SelectableItem,
        new: SelectableItem
    ) {
        visitor.compareItems(old, new)
        visitor.compareSelectableItems(old, new)

        if (old is CallableItem) {
            visitor.compareCallableItems(old, new as CallableItem)
        }

        when (old) {
            is PackageItem -> visitor.comparePackageItems(old, new as PackageItem)
            is ClassItem -> visitor.compareClassItems(old, new as ClassItem)
            is ConstructorItem -> visitor.compareConstructorItems(old, new as ConstructorItem)
            is MethodItem -> visitor.compareMethodItems(old, new as MethodItem)
            is FieldItem -> visitor.compareFieldItems(old, new as FieldItem)
            is PropertyItem -> visitor.comparePropertyItems(old, new as PropertyItem)
            else -> error("unexpected comparison of $old and $new")
        }

        // If this is comparing two [CallableItem]s then compare their [ParameterItem]s too.
        if (old is CallableItem) {
            dispatchCompareParameters(visitor, old.parameters(), (new as CallableItem).parameters())
        }
    }

    private fun compare(item1: SelectableItem, item2: SelectableItem): Int =
        comparator.compare(item1, item2)

    /** Sorting rank for types */
    private fun typeRank(item: Item): Int {
        return when (item) {
            is PackageItem -> 0
            is ConstructorItem -> 1
            is MethodItem -> 2
            is FieldItem -> 3
            is ClassItem -> 4
            is PropertyItem -> 5
            else -> error("Unexpected item $item of ${item.javaClass}")
        }
    }

    private val comparator: Comparator<SelectableItem> = Comparator { item1, item2 ->
        val typeSort = typeRank(item1) - typeRank(item2)
        when {
            typeSort != 0 -> typeSort
            item1 == item2 -> 0
            else ->
                when (item1) {
                    is PackageItem -> {
                        item1.qualifiedName().compareTo((item2 as PackageItem).qualifiedName())
                    }
                    is ClassItem -> {
                        item1.qualifiedName().compareTo((item2 as ClassItem).qualifiedName())
                    }
                    is CallableItem -> {
                        // Try to incrementally match aspects of the method until you can conclude
                        // whether they are the same or different.
                        // delta is 0 when the methods are the same, else not 0
                        // Start by comparing the names
                        var delta = item1.name().compareTo((item2 as CallableItem).name())
                        if (delta == 0) {
                            // If the names are the same then compare the number of parameters
                            val parameters1 = item1.parameters()
                            val parameters2 = item2.parameters()
                            val parameterCount1 = parameters1.size
                            val parameterCount2 = parameters2.size
                            delta = parameterCount1 - parameterCount2
                            if (delta == 0) {
                                // If the parameter count is the same, compare the parameter types
                                for (i in 0 until parameterCount1) {
                                    val parameter1 = parameters1[i]
                                    val parameter2 = parameters2[i]
                                    val type1 = parameter1.type().toTypeString()
                                    val type2 = parameter2.type().toTypeString()
                                    delta = type1.compareTo(type2)
                                    if (delta != 0) {
                                        // If the parameter types aren't the same, try a little
                                        // harder:
                                        //  (1) treat varargs and arrays the same, and
                                        //  (2) drop java.lang. prefixes from comparisons in
                                        // wildcard signatures since older signature files may have
                                        // removed those
                                        val simpleType1 = parameter1.type().toCanonicalTypeString()
                                        val simpleType2 = parameter2.type().toCanonicalTypeString()
                                        delta = simpleType1.compareTo(simpleType2)
                                        if (delta != 0) {
                                            // If still not the same, check the special case for
                                            // Kotlin coroutines: It's possible one has
                                            // "experimental" when fully qualified while the other
                                            // does not.
                                            // We treat these the same, so strip the prefix and
                                            // strip "experimental", then compare.
                                            if (
                                                simpleType1.startsWith("kotlin.coroutines.") &&
                                                    simpleType2.startsWith("kotlin.coroutines.")
                                            ) {
                                                val t1 =
                                                    simpleType1
                                                        .removePrefix("kotlin.coroutines.")
                                                        .removePrefix("experimental.")
                                                val t2 =
                                                    simpleType2
                                                        .removePrefix("kotlin.coroutines.")
                                                        .removePrefix("experimental.")
                                                delta = t1.compareTo(t2)
                                                if (delta != 0) {
                                                    // They're not the same
                                                    break
                                                }
                                            } else {
                                                // They're not the same
                                                break
                                            }
                                        }
                                    }
                                }
                                if (delta == 0) {
                                    // Also compare the target languages. As long as there is a
                                    // common target language, it makes sense to compare the items,
                                    // but if there are no common target language, treat these items
                                    // as not the same.
                                    if (
                                        item1.targetLanguages
                                            .intersect(item2.targetLanguages)
                                            .isEmpty()
                                    ) {
                                        // If there is no intersection between the target language
                                        // sets, exactly one of them must contain Kotlin (because
                                        // Java implies bytecode, and the possible non-overlapping
                                        // sets are bytecode|kotlin or bytecode,java|kotlin).
                                        delta =
                                            if (TargetLanguage.KOTLIN in item1.targetLanguages) {
                                                1
                                            } else {
                                                -1
                                            }
                                    }
                                }
                            }
                        }
                        // The method names are different, return the result of the compareTo
                        delta
                    }
                    is FieldItem -> {
                        item1.name().compareTo((item2 as FieldItem).name())
                    }
                    is PropertyItem -> {
                        var delta = item1.name().compareTo((item2 as PropertyItem).name())
                        if (delta == 0) {
                            // If the properties have the same name, additionally check the receiver
                            // types.
                            delta =
                                when (val receiver1 = item1.receiver) {
                                    null ->
                                        when (item2.receiver) {
                                            null -> 0
                                            else -> 1
                                        }
                                    else ->
                                        when (val receiver2 = item2.receiver) {
                                            null -> -1
                                            else ->
                                                receiver1
                                                    .toTypeString()
                                                    .compareTo(receiver2.toTypeString())
                                        }
                                }
                        }
                        if (delta == 0) {
                            delta = item1.contextParameters.size - item2.contextParameters.size
                            if (delta == 0) {
                                for ((p1, p2) in
                                    item1.contextParameters.zip(item2.contextParameters)) {
                                    delta =
                                        p1.type().toTypeString().compareTo(p2.type().toTypeString())
                                    if (delta != 0) break
                                }
                            }
                        }
                        delta
                    }
                    else -> error("Unexpected item $item1 of ${item1.javaClass}")
                }
        }
    }

    private val treeComparator: Comparator<ItemTree> = Comparator { item1, item2 ->
        comparator.compare(item1.item, item2.item())
    }

    private fun ensureSorted(item: ItemTree) {
        item.children.sortWith(treeComparator)
        for (child in item.children) {
            ensureSorted(child)
        }
    }

    /**
     * Sorts and removes duplicate items. Ties are broken in favor of keeping children having lower
     * indices.
     */
    private fun removeDuplicates(item: ItemTree) {
        item.children.sortWith(treeComparator)
        val children = item.children
        var i = children.count() - 2
        while (i >= 0) {
            val child = children[i]
            val prev = children[i + 1]
            if (comparator.compare(child.item, prev.item) == 0) {
                // merge prev into child because child was specified first
                child.children += prev.children
                children.removeAt(i + 1)
            }
            i--
        }
        for (child in children) {
            removeDuplicates(child)
        }
    }

    private fun createTree(
        codebase: MergedCodebase,
        referenceFilter: FilterPredicate?,
    ): List<ItemTree> {
        return createTree(codebase.children, referenceFilter)
    }

    private fun createTree(
        codebase: Codebase,
        referenceFilter: FilterPredicate?,
    ): List<ItemTree> {
        return createTree(listOf(codebase), referenceFilter)
    }

    private fun createTree(
        codebases: List<Codebase>,
        referenceFilter: FilterPredicate?,
    ): List<ItemTree> {
        val stack = Stack<ItemTree>()
        val root = ItemTree(null)
        stack.push(root)

        for (codebase in codebases) {
            // For non-prefiltered codebases (e.g. source-based codebases), exclude external
            // classpath dependencies (which have emit == false) while filtering to the requested
            // API surface.
            val filterEmit =
                if (codebase.preFiltered) null
                else if (referenceFilter == null) EmittedOnlyPredicate
                else EmittedOnlyPredicate.and(referenceFilter)
            codebase.accept(
                object :
                    ApiSurfaceVisitor(
                        preserveClassNesting = true,
                        // Do not visit [ParameterItem]s, as they will be compared in
                        // [dispatchToCompare].
                        visitParameterItems = false,
                        filterEmit = filterEmit,
                    ) {

                    /**
                     * Construct an [ItemTree] for [item] and push it onto the stack.
                     *
                     * This will not be called for [ParameterItem]s as it is not a [SelectableItem]
                     * and they are compared when comparing callables in [dispatchToCompare].
                     */
                    override fun visitSelectableItem(item: SelectableItem) {
                        val node = ItemTree(item)
                        val parent = stack.peek()
                        parent.children += node

                        stack.push(node)
                    }

                    /**
                     * Pop the [ItemTree] for [item] constructed in [visitSelectableItem] off the
                     * stack.
                     *
                     * This will not be called for [ParameterItem]s as it is not a [SelectableItem]
                     * and they are compared when comparing callables in [dispatchToCompare].
                     */
                    override fun afterVisitSelectableItem(item: SelectableItem) {
                        stack.pop()
                    }
                }
            )
        }

        if (codebases.count() >= 2) {
            removeDuplicates(root)
            // removeDuplicates will also sort the items
        } else {
            ensureSorted(root)
        }

        return root.children
    }

    data class ItemTree(val item: SelectableItem?) : Comparable<ItemTree> {
        val children: MutableList<ItemTree> = mutableListOf()

        fun item(): SelectableItem =
            item!! // Only the root note can be null, and this method should never be called on it

        override fun compareTo(other: ItemTree): Int {
            return comparator.compare(item(), other.item())
        }

        override fun toString(): String {
            return item.toString()
        }

        @Suppress("unused") // Left for debugging
        fun prettyPrint(): String {
            val sb = StringBuilder(1000)
            prettyPrint(sb, 0)
            return sb.toString()
        }

        private fun prettyPrint(sb: StringBuilder, depth: Int) {
            for (i in 0 until depth) {
                sb.append("    ")
            }
            sb.append(toString())
            sb.append('\n')
            for (child in children) {
                child.prettyPrint(sb, depth + 1)
            }
        }

        companion object {
            @Suppress("unused") // Left for debugging
            fun prettyPrint(list: List<ItemTree>): String {
                val sb = StringBuilder(1000)
                for (child in list) {
                    child.prettyPrint(sb, 0)
                }
                return sb.toString()
            }
        }
    }
}

/**
 * [FilterPredicate] that matches items whose [SelectableItem.selectedApi] has [SelectedApi.revert]
 * set to true.
 */
private object RevertedPredicate : FilterPredicate() {
    override fun test(t: SelectableItem): Boolean = t.selectedApi.revert
}
