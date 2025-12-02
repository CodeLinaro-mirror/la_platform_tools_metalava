/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.multiplatform

import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeStringConfiguration
import java.util.Objects

/**
 * A value which differs between source sets of a multiplatform project. This is a mapping from the
 * name of a source set to the value in that source set.
 */
typealias SourceSetDependent<V> = Map<String, V>

fun <V, T> SourceSetDependent<V>.transformValues(transform: (V) -> T): SourceSetDependent<T> {
    return mapValues { (_, value) -> transform(value) }
}

/**
 * Models a Kotlin multiplatform project (see https://kotlinlang.org/docs/multiplatform.html).
 *
 * There is a [Codebase] for each source set of the multiplatform project.
 */
class MultiplatformCodebase(sourceSetToCodebase: SourceSetDependent<Codebase>) :
    MultiplatformElement<Codebase>(sourceSetToCodebase) {
    /** A list of all the packages which exist in any source set of the codebase. */
    val packages: List<MultiplatformPackageItem> =
        aggregateChildren(
            childAccessor = { getPackages().packages },
            childIdentifier = { qualifiedName() },
            multiplatformChildCreator = { qualifiedName, sourceSetToPackage ->
                MultiplatformPackageItem(qualifiedName, sourceSetToPackage)
            },
        )

    /**
     * Searches for the package with [qualifiedName]. If the package exists in any source set,
     * returns a [MultiplatformPackageItem]. If it does not exist in any source sets, returns null.
     */
    fun findPackage(qualifiedName: String): MultiplatformPackageItem? {
        return packages.singleOrNull { it.qualifiedName == qualifiedName }
    }

    /**
     * Searches for the class with [qualifiedName]. If the class exists in any source set, returns a
     * [MultiplatformClassItem]. If it does not exist in any source sets, returns null.
     */
    fun findClass(qualifiedName: String): MultiplatformClassItem? {
        val sourceSetToClass =
            sourceSetToElement.mapValues { (_, codebase) -> codebase?.findClass(qualifiedName) }
        return if (sourceSetToElement.values.any { it != null }) {
            MultiplatformClassItem(
                qualifiedName,
                sourceSetToClass,
            )
        } else {
            null
        }
    }
}

/**
 * A wrapper for a [SourceSetDependent] map of some element. Provides common functionality for
 * different parts of a multiplatform model.
 *
 * The key of [sourceSetToElement] is nullable. If the value is null for some source set, that means
 * the element does not exist in that source set.
 */
sealed class MultiplatformElement<E>(protected val sourceSetToElement: SourceSetDependent<E?>) {
    /** The source sets which this element exists in. */
    val sourceSets: Set<String> =
        sourceSetToElement.keys.filter { sourceSetToElement[it] != null }.toSet()

    /**
     * For each source set, computes some value with [valueAccessor] based on the element for that
     * source set. Returns a mapping from source set to value, with only the source sets where the
     * element exists.
     */
    protected fun <V> sourceSetDependentValue(valueAccessor: (E) -> V): SourceSetDependent<V> {
        // Only map from source sets where the element exists.
        return sourceSets.associateWith { valueAccessor(sourceSetToElement[it]!!) }
    }

    /**
     * Computes a list of the [MultiplatformElement] children with type [C] of this element. For
     * instance, this can be used to list all packages in a codebase, all classes in a package, etc.
     *
     * @param childAccessor Lists the children for the element of one source set.
     * @param childIdentifier Returns an identifier for a child of type [C] in order to collect
     *   children with the same signature from different source sets into a [MultiplatformElement].
     * @param multiplatformChildCreator Creates a [MultiplatformElement] for a child from an
     *   identifier and a mapping of source set to value of the child in that source set.
     */
    protected fun <C, M : MultiplatformElement<C>, I> aggregateChildren(
        childAccessor: E.() -> List<C>,
        childIdentifier: C.() -> I,
        multiplatformChildCreator: (I, SourceSetDependent<C?>) -> M,
    ): List<M> {
        // Create a mapping from source set to the children that exist in that source set.
        val sourceSetToChildren =
            sourceSetToElement.mapValues { (_, parent) -> parent?.childAccessor() ?: emptyList() }
        val allChildIdentifiers =
            sourceSetToChildren.values
                .flatMap { childList -> childList.map { child -> childIdentifier(child) } }
                .toSet()
        // For each child identifier, find the value of the child in all source sets, and create a
        // MultiplatformElement for it.
        return allChildIdentifiers.map { childIdentifier ->
            val sourceSetToChild =
                sourceSetToChildren.mapValues { (_, children) ->
                    children.singleOrNull { child -> childIdentifier(child) == childIdentifier }
                }
            multiplatformChildCreator(childIdentifier, sourceSetToChild)
        }
    }
}

/** Wrapper for common functionality of [MultiplatformElement] with [Item] element types. */
sealed class MultiplatformItem<I : Item>(sourceSetToItem: SourceSetDependent<I?>) :
    MultiplatformElement<I>(sourceSetToItem) {
    /**
     * A mapping from source set where the [Item] to the modifiers of the [Item] in that source set.
     */
    val modifiers: SourceSetDependent<BaseModifierList> = sourceSetDependentValue { it.modifiers }
}

/** A package named [qualifiedName] in a [MultiplatformCodebase]. */
class MultiplatformPackageItem(
    val qualifiedName: String,
    sourceSetToItem: SourceSetDependent<PackageItem?>,
) : MultiplatformItem<PackageItem>(sourceSetToItem) {
    /** All the top-level (not nested) classes defined in this package in any source set. */
    fun topLevelClasses(): List<MultiplatformClassItem> {
        return aggregateChildren(
            childAccessor = { topLevelClasses() },
            childIdentifier = { qualifiedName() },
            multiplatformChildCreator = { qualifiedName, sourceSetToClassItem ->
                MultiplatformClassItem(qualifiedName, sourceSetToClassItem)
            }
        )
    }

    /** All the classes (including nested classes) defined in this package in any source set. */
    fun allClasses(): Sequence<MultiplatformClassItem> {
        return topLevelClasses().asSequence().flatMap { it.allClasses() }
    }

    override fun toString(): String {
        return "multiplatform package $qualifiedName"
    }
}

/** A class named [qualifiedName] in a [MultiplatformCodebase]. */
class MultiplatformClassItem(
    val qualifiedName: String,
    sourceSetToItem: SourceSetDependent<ClassItem?>
) : MultiplatformItem<ClassItem>(sourceSetToItem) {
    /**
     * A mapping from source set where the [ClassItem] exists to the super class of the [ClassItem]
     * for that source set.
     */
    val superClassType: SourceSetDependent<ClassTypeItem?> = sourceSetDependentValue {
        it.superClassType()
    }

    /**
     * A mapping from source set where the [ClassItem] exists to the interfaces of the [ClassItem]
     * for that source set.
     */
    val interfaceTypes: SourceSetDependent<List<ClassTypeItem>> = sourceSetDependentValue {
        it.interfaceTypes()
    }

    /**
     * A mapping from source set where the [ClassItem] exists to the kind of the [ClassItem] for
     * that source set.
     */
    val classKind: SourceSetDependent<ClassKind> = sourceSetDependentValue { it.classKind }

    /**
     * The nested classes of this class which exist in any source set.
     *
     * This only includes directly nested classes, not the nested classes of nested classes.
     */
    val nestedClasses: List<MultiplatformClassItem> =
        aggregateChildren(
            childAccessor = { nestedClasses() },
            childIdentifier = { qualifiedName() },
            multiplatformChildCreator = { qualifiedName, sourceSetToClassItem ->
                MultiplatformClassItem(qualifiedName, sourceSetToClassItem)
            }
        )

    /**
     * A mapping from source set where the [ClassItem] exists to the optional aliased type of the
     * class in that source set.
     *
     * If the [ClassItem] is a typealias in a source set, the value for that source set is the
     * aliased type. If the class is not a typealias in a source set, the value for that source set
     * is null.
     *
     * It is possible for a class to be defined as an `expect class` and an `actual typealias`.
     */
    val optionalAliasedType: SourceSetDependent<TypeItem?>
        get() = sourceSetDependentValue { it.optionalAliasedType }

    /**
     * A sequence with this [MultiplatformClassItem], its [nestedClasses], and all the recursively
     * nested classes of those nested classes.
     */
    fun allClasses(): Sequence<MultiplatformClassItem> {
        return sequenceOf(this).plus(nestedClasses.asSequence().flatMap { it.allClasses() })
    }

    /** The properties of this class which exist in any source set. */
    val properties: List<MultiplatformPropertyItem> =
        aggregateChildren(
            childAccessor = { properties() },
            childIdentifier = { MultiplatformPropertyItem.Identifier(name(), receiver) },
            multiplatformChildCreator = { identifier, sourceSetToPropertyItem ->
                MultiplatformPropertyItem(this, identifier, sourceSetToPropertyItem)
            }
        )

    override fun toString(): String {
        return "multiplatform class $qualifiedName"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other as? MultiplatformClassItem)?.qualifiedName == qualifiedName
    }

    override fun hashCode(): Int {
        return qualifiedName.hashCode()
    }
}

/** A property of the [containingClass], identified by [name] and optional [receiver] type. */
class MultiplatformPropertyItem(
    val containingClass: MultiplatformClassItem,
    private val identifier: Identifier,
    sourceSetToItem: SourceSetDependent<PropertyItem?>,
) : MultiplatformItem<PropertyItem>(sourceSetToItem) {
    /** The name of the property. */
    val name: String
        get() = identifier.name

    /**
     * The receiver type of the property, or null if it has no receiver.
     *
     * The nullability of this type is significant, as it is possible to define two properties in
     * Kotlin that differ only by receiver nullability. However, other modifiers (annotations) on
     * the type are not significant and may differ by source set.
     */
    val receiver: TypeItem?
        get() = identifier.receiver

    override fun toString(): String {
        val receiverString =
            receiver?.let { it.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) + "." }
                ?: ""
        return "multiplatform property ${containingClass.qualifiedName}#$receiverString$name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiplatformPropertyItem) return false
        return other.containingClass == containingClass && other.identifier == identifier
    }

    override fun hashCode(): Int {
        return Objects.hash(containingClass, identifier)
    }

    /**
     * The combination of [name] and [receiver] that uniquely identifies the [PropertyItem] within a
     * class. The nullability of the [receiver] is significant but other modifiers (annotations) are
     * not.
     */
    class Identifier(val name: String, val receiver: TypeItem?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Identifier) return false
            return name == other.name &&
                ((receiver == null && other.receiver == null) ||
                    (receiver != null &&
                        receiver.equalToType(other.receiver, includeNullability = true)))
        }

        override fun hashCode(): Int {
            return Objects.hash(name, receiver)
        }
    }
}
