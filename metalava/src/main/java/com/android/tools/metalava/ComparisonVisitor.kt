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
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.visitors.ApiFilters
import com.android.tools.metalava.model.visitors.ApiVisitor

/**
 * Visitor which visits all items in two matching codebases and matches up the items and invokes
 * [compareItems] on each pair, or [addedItem] or [removedItem] when items are not matched
 */
open class ComparisonVisitor {
    open fun compareItems(old: Item, new: Item) {}

    open fun addedItem(new: SelectableItem) {}

    open fun removedItem(old: SelectableItem, from: SelectableItem?) {}

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
}

/** Simple stack type built on top of an [ArrayList]. */
private typealias Stack<E> = ArrayList<E>

private fun <E> Stack<E>.push(e: E) {
    add(e)
}

private fun <E> Stack<E>.pop(): E = removeAt(lastIndex)

private fun <E> Stack<E>.peek(): E = last()

class CodebaseComparator {
    /**
     * Visits this codebase and compares it with another codebase, informing the visitors about the
     * correlations and differences that it finds
     */
    fun compare(
        visitor: ComparisonVisitor,
        old: Codebase,
        new: Codebase,
        filter: FilterPredicate? = null
    ) {
        // Algorithm: build up two trees (by nesting level); then visit the
        // two trees
        val oldTree = createTree(old, filter)
        val newTree = createTree(new, filter)

        /* Debugging:
        println("Old:\n${ItemTree.prettyPrint(oldTree)}")
        println("New:\n${ItemTree.prettyPrint(newTree)}")
        */

        compare(visitor, oldTree, newTree, null, null, filter)
    }

    fun compare(
        visitor: ComparisonVisitor,
        old: MergedCodebase,
        new: MergedCodebase,
        filter: FilterPredicate? = null
    ) {
        // Algorithm: build up two trees (by nesting level); then visit the
        // two trees
        val oldTree = createTree(old, filter)
        val newTree = createTree(new, filter)

        /* Debugging:
        println("Old:\n${ItemTree.prettyPrint(oldTree)}")
        println("New:\n${ItemTree.prettyPrint(newTree)}")
        */

        compare(visitor, oldTree, newTree, null, null, filter)
    }

    private fun compare(
        visitor: ComparisonVisitor,
        oldList: List<ItemTree>,
        newList: List<ItemTree>,
        newParent: SelectableItem?,
        oldParent: SelectableItem?,
        filter: FilterPredicate?
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
                    when {
                        compare > 0 -> {
                            index2++
                            if (new.emit) {
                                dispatchToAddedOrCompareIfItemWasMoved(
                                    new,
                                    oldParent,
                                    visitor,
                                )
                            }
                        }
                        compare < 0 -> {
                            index1++
                            if (old.emit) {
                                dispatchToRemovedOrCompareIfItemWasMoved(
                                    old,
                                    visitor,
                                    newParent,
                                    filter,
                                )
                            }
                        }
                        else -> {
                            if (new.emit) {
                                if (old.emit) {
                                    dispatchToCompare(visitor, old, new)
                                } else {
                                    dispatchToAddedOrCompareIfItemWasMoved(
                                        new,
                                        oldParent,
                                        visitor,
                                    )
                                }
                            } else {
                                if (old.emit) {
                                    dispatchToRemovedOrCompareIfItemWasMoved(
                                        old,
                                        visitor,
                                        newParent,
                                        filter,
                                    )
                                }
                            }

                            // Compare the children (recurse)
                            compare(
                                visitor,
                                oldTree.children,
                                newTree.children,
                                newTree.item(),
                                oldTree.item(),
                                filter
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
                        dispatchToRemovedOrCompareIfItemWasMoved(
                            old,
                            visitor,
                            newParent,
                            filter,
                        )
                    }
                }
            } else if (index2 < length2) {
                // All the remaining items in newList have been added
                while (index2 < length2) {
                    val newTree = newList[index2++]
                    val new = newTree.item()

                    dispatchToAddedOrCompareIfItemWasMoved(new, oldParent, visitor)
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
        // If it's a method, we may not have added a new method,
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
        filter: FilterPredicate?
    ) {
        // If it's a method, we may not have removed the method, we may have simply
        // removed an override and are now inheriting the method from a superclass.
        // Alternatively, it may have always truly been an inherited method, but if the base
        // class was hidden then the signature file may have listed the method as being
        // declared on the subclass
        val inheritedMethod =
            if (old is MethodItem && newParent is ClassItem) {
                val superMethod = newParent.findPredicateMethodWithSuper(old, filter)

                if (superMethod != null && (filter == null || filter.test(superMethod))) {
                    superMethod.duplicate(newParent)
                } else {
                    null
                }
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

                if (superField != null && (filter == null || filter.test(superField))) {
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

    companion object {
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

        val comparator: Comparator<SelectableItem> = Comparator { item1, item2 ->
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
                            // Try to incrementally match aspects of the method until you can
                            // conclude
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
                                    // If the parameter count is the same, compare the parameter
                                    // types
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
                                            // wildcard
                                            //      signatures since older signature files may have
                                            // removed
                                            //      those
                                            val simpleType1 = parameter1.type().toCanonicalType()
                                            val simpleType2 = parameter2.type().toCanonicalType()
                                            delta = simpleType1.compareTo(simpleType2)
                                            if (delta != 0) {
                                                // If still not the same, check the special case for
                                                // Kotlin coroutines: It's possible one has
                                                // "experimental"
                                                // when fully qualified while the other does not.
                                                // We treat these the same, so strip the prefix and
                                                // strip
                                                // "experimental", then compare.
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
                                }
                            }
                            // The method names are different, return the result of the compareTo
                            delta
                        }
                        is FieldItem -> {
                            item1.name().compareTo((item2 as FieldItem).name())
                        }
                        is PropertyItem -> {
                            item1.name().compareTo((item2 as PropertyItem).name())
                        }
                        else -> error("Unexpected item $item1 of ${item1.javaClass}")
                    }
            }
        }

        val treeComparator: Comparator<ItemTree> = Comparator { item1, item2 ->
            comparator.compare(item1.item, item2.item())
        }
    }

    private fun ensureSorted(item: ItemTree) {
        item.children.sortWith(treeComparator)
        for (child in item.children) {
            ensureSorted(child)
        }
    }

    /**
     * Sorts and removes duplicate items. The kept item will be an unhidden item if possible. Ties
     * are broken in favor of keeping children having lower indices
     */
    private fun removeDuplicates(item: ItemTree) {
        item.children.sortWith(treeComparator)
        val children = item.children
        var i = children.count() - 2
        while (i >= 0) {
            val child = children[i]
            val prev = children[i + 1]
            if (comparator.compare(child.item, prev.item) == 0) {
                if (prev.item!!.emit && !child.item!!.emit) {
                    // merge child into prev because prev is emitted
                    val prevChildren = prev.children.toList()
                    prev.children.clear()
                    prev.children += child.children
                    prev.children += prevChildren
                    children.removeAt(i)
                } else {
                    // merge prev into child because child was specified first
                    child.children += prev.children
                    children.removeAt(i + 1)
                }
            }
            i--
        }
        for (child in children) {
            removeDuplicates(child)
        }
    }

    private fun createTree(
        codebase: MergedCodebase,
        filter: FilterPredicate? = null
    ): List<ItemTree> {
        return createTree(codebase.children, filter)
    }

    private fun createTree(codebase: Codebase, filter: FilterPredicate? = null): List<ItemTree> {
        return createTree(listOf(codebase), filter)
    }

    private fun createTree(
        codebases: List<Codebase>,
        filter: FilterPredicate? = null
    ): List<ItemTree> {
        val stack = Stack<ItemTree>()
        val root = ItemTree(null)
        stack.push(root)

        for (codebase in codebases) {
            val acceptAll = codebase.preFiltered || filter == null
            val predicate = if (acceptAll) FilterPredicate { true } else filter!!
            val apiFilters = ApiFilters(emit = predicate, reference = predicate)
            codebase.accept(
                object :
                    ApiVisitor(
                        preserveClassNesting = true,
                        // Do not visit [ParameterItem]s, as they will be compared in
                        // [dispatchToCompare].
                        visitParameterItems = false,
                        inlineInheritedFields = true,
                        apiFilters = apiFilters,
                        // Whenever a caller passes arguments of "--show-annotation 'SomeAnnotation'
                        // --check-compatibility:api:released $oldApi",
                        // really what they mean is:
                        // 1. Definitions:
                        //  1.1 Define the SomeAnnotation API as the set of APIs that are either
                        // public or are annotated with @SomeAnnotation
                        //  1.2 $oldApi was previously the difference between the SomeAnnotation api
                        // and the public api
                        // 2. The caller would like Metalava to verify that all APIs that are known
                        // to have previously been part of the SomeAnnotation api remain part of the
                        // SomeAnnotation api
                        // So, when doing compatibility checking we want to consider public APIs
                        // even if the caller didn't explicitly pass --show-unannotated
                        showUnannotated = true,
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

                    override fun include(cls: ClassItem): Boolean =
                        if (acceptAll) true else super.include(cls)

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
