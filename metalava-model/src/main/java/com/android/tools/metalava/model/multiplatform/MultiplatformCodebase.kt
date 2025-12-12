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
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterListOwner
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
     * Uses [getClassFromCodebase] to search for the class with [qualifiedName] in each [Codebase]
     * of [sourceSetToElement]. If the class is found in any source sets, returns a
     * [MultiplatformClassItem]. If it is not found in any, returns null.
     */
    private fun getClass(
        qualifiedName: String,
        getClassFromCodebase: Codebase.() -> ClassItem?
    ): MultiplatformClassItem? {
        val sourceSetToClass =
            sourceSetToElement.transformValues { codebase -> codebase?.getClassFromCodebase() }
        return if (sourceSetToClass.values.any { it != null }) {
            MultiplatformClassItem(
                qualifiedName,
                sourceSetToClass,
            )
        } else {
            null
        }
    }

    /**
     * Searches for the class with [qualifiedName]. If the class exists in any source set, returns a
     * [MultiplatformClassItem]. If it does not exist in any source sets, returns null.
     */
    fun findClass(qualifiedName: String): MultiplatformClassItem? {
        return getClass(qualifiedName) { findClass(qualifiedName) }
    }

    /**
     * Searches for the class with [qualifiedName], including searching the classpath. If the class
     * exists in any source set, returns a [MultiplatformClassItem]. If it does not exist in any
     * source sets, returns null.
     */
    fun resolveClass(qualifiedName: String): MultiplatformClassItem? {
        return getClass(qualifiedName) { resolveClass(qualifiedName) }
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

    /**
     * Computes a list of the [MultiplatformElement] children with type [C] of this element, where
     * the children are grouped based on their index. For instance, this can be used to list all
     * value parameters of a callable or type parameters of a type parameter owner.
     *
     * @param childAccessor Lists the children for the element of one source set.
     * @param multiplatformChildCreator Creates a [MultiplatformElement] for a child from the index
     *   of the child and a mapping of source set to value of the child in that source set.
     */
    protected fun <C, M : MultiplatformElement<C>> aggregateIndexedChildren(
        childAccessor: E.() -> List<C>,
        multiplatformChildCreator: (Int, SourceSetDependent<C?>) -> M,
    ): List<M> {
        // Create a mapping from source set to the children that exist in that source set.
        val sourceSetToChildren =
            sourceSetToElement.transformValues { parent -> parent?.childAccessor() ?: emptyList() }
        val numberOfChildren = sourceSetToChildren.values.maxOf { it.size }
        // For each child index, find the child at that index in all source sets, and create a
        // MultiplatformElement for it.
        return (0..<numberOfChildren).map { childIndex ->
            val sourceSetToChild =
                sourceSetToChildren.transformValues { children -> children.getOrNull(childIndex) }
            multiplatformChildCreator(childIndex, sourceSetToChild)
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
            // Do not include file facade classes. Their members will be listed in
            // [topLevelFunctions] and [topLevelProperties].
            childAccessor = { topLevelClasses().filter { !it.isFileFacade } },
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

    /**
     * All the top-level functions defined in this package in any source set.
     *
     * In the underlying [Codebase] model, these functions are contained in file facade classes, but
     * those are not included here because they are not real classes in the Kotlin API surface.
     */
    val topLevelFunctions: List<MultiplatformMethodItem> =
        aggregateChildren(
            childAccessor = {
                topLevelClasses().filter { it.isFileFacade }.flatMap { it.methods() }
            },
            childIdentifier = { MultiplatformCallableItem.Identifier(this) },
            multiplatformChildCreator = { identifier, sourceSetToMethodItem ->
                MultiplatformMethodItem(this, identifier, sourceSetToMethodItem)
            }
        )

    /**
     * All the top-level properties defined in this package in any source set.
     *
     * In the underlying [Codebase] model, these properties are contained in file facade classes,
     * but those are not included here because they are not real classes in the Kotlin API surface.
     */
    val topLevelProperties: List<MultiplatformPropertyItem> =
        aggregateChildren(
            childAccessor = {
                topLevelClasses().filter { it.isFileFacade }.flatMap { it.properties() }
            },
            childIdentifier = { MultiplatformPropertyItem.Identifier(name(), receiver) },
            multiplatformChildCreator = { identifier, sourceSetToPropertyItem ->
                MultiplatformPropertyItem(this, identifier, sourceSetToPropertyItem)
            }
        )

    override fun toString(): String {
        return "multiplatform package $qualifiedName"
    }
}

/** A [MultiplatformItem] based on an [Item] that is a [TypeParameterListOwner]. */
sealed class MultiplatformTypeParameterListOwner<E>(sourceSetToElement: SourceSetDependent<E?>) :
    MultiplatformItem<E>(sourceSetToElement) where E : TypeParameterListOwner, E : Item {
    /**
     * The type parameters of this item, listed in order.
     *
     * For expect/actual items or items defined in a single source set, the source sets of the type
     * parameters will be the same as the owning item. However, if two items with the same signature
     * are defined in unrelated source sets, they could have different type parameters.
     */
    val typeParameterList: List<MultiplatformTypeParameterItem> =
        aggregateIndexedChildren(
            childAccessor = { typeParameterList },
            multiplatformChildCreator = { typeParameterIndex, sourceSetToTypeParameter ->
                MultiplatformTypeParameterItem(this, typeParameterIndex, sourceSetToTypeParameter)
            }
        )
}

/** A class named [qualifiedName] in a [MultiplatformCodebase]. */
class MultiplatformClassItem(
    val qualifiedName: String,
    sourceSetToItem: SourceSetDependent<ClassItem?>
) : MultiplatformTypeParameterListOwner<ClassItem>(sourceSetToItem) {
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

    /** The methods of this class which exist in any source set. */
    val methods: List<MultiplatformMethodItem> =
        aggregateChildren(
            childAccessor = { methods() },
            childIdentifier = { MultiplatformCallableItem.Identifier(this) },
            multiplatformChildCreator = { identifier, sourceSetToMethodItem ->
                MultiplatformMethodItem(this, identifier, sourceSetToMethodItem)
            }
        )

    /** The constructors of this class which exist in any source set. */
    val constructors: List<MultiplatformConstructorItem> =
        aggregateChildren(
            childAccessor = { constructors() },
            childIdentifier = { MultiplatformCallableItem.Identifier(this) },
            multiplatformChildCreator = { identifier, sourceSetToConstructorItem ->
                MultiplatformConstructorItem(this, identifier, sourceSetToConstructorItem)
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

/** A property of the [containingItem], identified by [name] and optional [receiver] type. */
class MultiplatformPropertyItem
private constructor(
    /**
     * The item which contains this property, either a class or a package (for a top level
     * property).
     */
    val containingItem: MultiplatformItem<*>,
    private val containingItemQualifiedName: String,
    private val identifier: Identifier,
    sourceSetToItem: SourceSetDependent<PropertyItem?>,
) : MultiplatformTypeParameterListOwner<PropertyItem>(sourceSetToItem) {
    constructor(
        containingClass: MultiplatformClassItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<PropertyItem?>
    ) : this(containingClass, containingClass.qualifiedName, identifier, sourceSetToItem)

    constructor(
        containingPackage: MultiplatformPackageItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<PropertyItem?>
    ) : this(containingPackage, containingPackage.qualifiedName, identifier, sourceSetToItem)

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
        return "multiplatform property $containingItemQualifiedName#$receiverString$name"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiplatformPropertyItem) return false
        return other.containingItem == containingItem && other.identifier == identifier
    }

    override fun hashCode(): Int {
        return Objects.hash(containingItem, identifier)
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

/**
 * A callable (method or constructor) of the [containingItem], identified by [parameterTypes] and
 * name for methods.
 */
sealed class MultiplatformCallableItem<C : CallableItem>
protected constructor(
    /**
     * The item which contains this property, either a class or a package (for a top level
     * function).
     */
    open val containingItem: MultiplatformItem<*>,
    protected val containingItemQualifiedName: String,
    protected val identifier: Identifier,
    sourceSetToItem: SourceSetDependent<C?>
) : MultiplatformTypeParameterListOwner<C>(sourceSetToItem) {
    constructor(
        containingClass: MultiplatformClassItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<C?>
    ) : this(containingClass, containingClass.qualifiedName, identifier, sourceSetToItem)

    constructor(
        containingPackage: MultiplatformPackageItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<C?>
    ) : this(containingPackage, containingPackage.qualifiedName, identifier, sourceSetToItem)

    /**
     * The parameter types of the callable.
     *
     * The nullability of these type is significant, as it is possible to define two callables in
     * Kotlin that differ only by parameter nullability. However, other modifiers (annotations) on
     * the types are not significant and may differ by source set.
     */
    val parameterTypes: List<TypeItem>
        get() = identifier.parameterTypes

    /**
     * The parameters of the callable, listed in order. All parameters will exist in the same set of
     * source sets as the containing callable.
     */
    val parameters: List<MultiplatformParameterItem> =
        aggregateIndexedChildren(
            childAccessor = { parameters() },
            multiplatformChildCreator = { parameterIndex, sourceSetToParameter ->
                MultiplatformParameterItem(this, parameterIndex, sourceSetToParameter)
            }
        )

    /**
     * A mapping from source set where the [CallableItem] exists to the list of throws types for the
     * callable in that source set.
     */
    val throwsTypes: SourceSetDependent<List<ExceptionTypeItem>> = sourceSetDependentValue {
        it.throwsTypes()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiplatformCallableItem<C>) return false
        return other.containingItem == containingItem && other.identifier == identifier
    }

    override fun hashCode(): Int {
        return Objects.hash(containingItem, identifier)
    }

    /**
     * The combination of [name] and [parameterTypes] that uniquely identifies the [CallableItem]
     * within a class. The nullability of the [parameterTypes] are significant but other modifiers
     * (annotations) are not.
     */
    class Identifier(val name: String, val parameterTypes: List<TypeItem>) {
        constructor(
            callableItem: CallableItem
        ) : this(callableItem.name(), callableItem.parameters().map { it.type() })

        fun parameterDescription(): String {
            return "(" +
                parameterTypes.joinToString {
                    it.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS)
                } +
                ")"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Identifier) return false
            return name == other.name &&
                parameterTypes.size == other.parameterTypes.size &&
                parameterTypes.zip(other.parameterTypes).all { (t1, t2) ->
                    t1.equalToType(t2, includeNullability = true)
                }
        }

        override fun hashCode(): Int {
            return Objects.hash(name, parameterTypes)
        }
    }
}

/** A method of the [containingItem], identified by [parameterTypes] and [name]. */
class MultiplatformMethodItem
private constructor(
    containingItem: MultiplatformItem<*>,
    containingItemQualifiedName: String,
    identifier: Identifier,
    sourceSetToItem: SourceSetDependent<MethodItem?>
) :
    MultiplatformCallableItem<MethodItem>(
        containingItem,
        containingItemQualifiedName,
        identifier,
        sourceSetToItem
    ) {
    constructor(
        containingClass: MultiplatformClassItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<MethodItem?>
    ) : this(containingClass, containingClass.qualifiedName, identifier, sourceSetToItem)

    constructor(
        containingPackage: MultiplatformPackageItem,
        identifier: Identifier,
        sourceSetToItem: SourceSetDependent<MethodItem?>
    ) : this(containingPackage, containingPackage.qualifiedName, identifier, sourceSetToItem)

    /** The name of the method. */
    val name: String
        get() = identifier.name

    override fun toString(): String {
        return "multiplatform method $containingItemQualifiedName#${identifier.name}" +
            identifier.parameterDescription()
    }
}

/**
 * A constructor of the [containingItem] (which must be a class), identified by [parameterTypes].
 */
class MultiplatformConstructorItem(
    override val containingItem: MultiplatformClassItem,
    identifier: Identifier,
    sourceSetToItem: SourceSetDependent<ConstructorItem?>
) : MultiplatformCallableItem<ConstructorItem>(containingItem, identifier, sourceSetToItem) {
    override fun toString(): String {
        return "multiplatform constructor $containingItemQualifiedName" +
            identifier.parameterDescription()
    }
}

/** A parameter of the [containingCallable], identified by [parameterIndex]. */
class MultiplatformParameterItem(
    val containingCallable: MultiplatformCallableItem<*>,
    val parameterIndex: Int,
    sourceSetToItem: SourceSetDependent<ParameterItem?>
) : MultiplatformItem<ParameterItem>(sourceSetToItem) {
    /**
     * A mapping from source set where the [ParameterItem] exists to the public name of the
     * parameter in that source set.
     *
     * Expect/actuals must have the same parameter names, but if callables are defined with the same
     * signature in unrelated source sets, they could have different parameter names.
     */
    val publicName: SourceSetDependent<String?> = sourceSetDependentValue { it.publicName() }

    /**
     * A mapping from source set where the [ParameterItem] exists to whether the parameter has a
     * default value in that source set.
     *
     * Actual parameters inherit default values from expects, but if callables are defined with the
     * same signature in unrelated source sets, they could have different parameter default values.
     */
    val hasDefaultValue: SourceSetDependent<Boolean> = sourceSetDependentValue {
        it.hasDefaultValue()
    }

    override fun toString(): String {
        return "multiplatform parameter #$parameterIndex of $containingCallable"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiplatformParameterItem) return false
        return containingCallable == other.containingCallable &&
            parameterIndex == other.parameterIndex
    }

    override fun hashCode(): Int {
        return Objects.hash(containingCallable, parameterIndex)
    }
}

/** A type parameter of [owner], identified by [typeParameterIndex]. */
class MultiplatformTypeParameterItem(
    val owner: MultiplatformTypeParameterListOwner<*>,
    val typeParameterIndex: Int,
    sourceSetToItem: SourceSetDependent<TypeParameterItem?>
) : MultiplatformElement<TypeParameterItem>(sourceSetToItem) {
    /**
     * A mapping from source set where this type parameter exists to the modifiers of the type
     * parameter in that source set.
     */
    val modifiers: SourceSetDependent<BaseModifierList> = sourceSetDependentValue { it.modifiers }

    /**
     * A mapping from source set where this type parameter exists to the name of the type parameter
     * in that source set.
     *
     * The compiler allows an expect/actual type parameter to have different names between the
     * expect and actual.
     */
    val name: SourceSetDependent<String> = sourceSetDependentValue { it.name() }

    /**
     * A mapping from source set where this type parameter exists to whether the type parameter is
     * reified in that source set.
     */
    val isReified: SourceSetDependent<Boolean> = sourceSetDependentValue { it.isReified() }

    override fun toString(): String {
        return "multiplatform type parameter #$typeParameterIndex of $owner"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiplatformTypeParameterItem) return false
        return owner == other.owner && typeParameterIndex == other.typeParameterIndex
    }

    override fun hashCode(): Int {
        return Objects.hash(owner, typeParameterIndex)
    }
}
