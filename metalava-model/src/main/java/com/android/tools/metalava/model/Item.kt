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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.reporter.BaselineKey
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Reportable

/**
 * Represents a code element such as a package, a class, a method, a field, a parameter.
 *
 * This extra abstraction on top of PSI allows us to more model the API (and customize visibility,
 * which cannot always be done by looking at a particular piece of code and examining visibility
 * and @hide/@removed annotations: sometimes package private APIs are unhidden by being used in
 * public APIs for example.
 *
 * The abstraction also lets us back the model by an alternative implementation read from signature
 * files, to do compatibility checks.
 */
interface Item : Reportable {
    val codebase: Codebase

    /** Return the modifiers of this class */
    @MetalavaApi val modifiers: ModifierList

    fun parent(): SelectableItem?

    /**
     * Recursive check to see if compatibility checks should be suppressed for this item or any of
     * its parents (containing class, containing package).
     */
    fun isCompatibilitySuppressed(): Boolean {
        return hasSuppressCompatibilityMetaAnnotation() ||
            parent()?.isCompatibilitySuppressed() ?: false
    }

    /** True if this item has been marked deprecated. */
    val originallyDeprecated: Boolean

    /**
     * True if this item has been marked as deprecated or is a descendant of a non-package item that
     * has been marked as deprecated.
     */
    val effectivelyDeprecated: Boolean

    /** Visits this element using the given [visitor] */
    fun accept(visitor: ItemVisitor)

    /**
     * Mutate the [modifiers] list.
     *
     * Provides a [MutableModifierList] of the [modifiers] that can be modified by [mutator]. Once
     * the mutator exits the [modifiers] will be updated. The [MutableModifierList] must not be
     * accessed from outside [mutator].
     */
    fun mutateModifiers(mutator: MutableModifierList.() -> Unit)

    /**
     * The, possibly empty, description of the [Item].
     *
     * For [SelectableItem]s this is the main description in [SelectableItem.documentation]. For
     * [ParameterItem]s this is the description of the corresponding `@param` tag in the
     * [ParameterItem.containingCallable]'s [CallableItem.documentation],
     */
    val description: DocContent?

    /**
     * The owner of this [Item]'s [description] that provides support for modifying [description].
     */
    val descriptionOwner: DocContentOwner

    /**
     * A rank used for sorting. This allows signature files etc to sort similar items by a natural
     * order, if non-zero. (Even though in signature files the elements are normally sorted first
     * logically (constructors, then methods, then fields) and then alphabetically, this lets us
     * preserve the source ordering for example for overloaded methods of the same name, where it's
     * not clear that an alphabetical order (of each parameter?) would be preferable.)
     */
    val sortingRank: Int

    val isPublic: Boolean
    val isProtected: Boolean
    val isInternal: Boolean
    val isPackagePrivate: Boolean
    val isPrivate: Boolean

    /** Calls [equalsToItem]. */
    override fun equals(other: Any?): Boolean

    /** Calls [hashCodeForItem]. */
    override fun hashCode(): Int

    /** Calls [toStringForItem]. */
    override fun toString(): String

    /**
     * Whether this [Item] is equal to [other].
     *
     * This is implemented instead of [equals] because interfaces are not allowed to implement
     * [equals]. Implementations of this will implement [equals] by calling this.
     */
    fun equalsToItem(other: Any?): Boolean

    /**
     * Hashcode for this [Item].
     *
     * This is implemented instead of [hashCode] because interfaces are not allowed to implement
     * [hashCode]. Implementations of this will implement [hashCode] by calling this.
     */
    fun hashCodeForItem(): Int

    /** Provides a string representation of the item, suitable for use while debugging. */
    fun toStringForItem(): String = describe()

    /**
     * The language in which this was written, or [SourceLanguage.UNKNOWN] if not known, e.g. when
     * created from a signature file.
     */
    val sourceLanguage: SourceLanguage

    /**
     * Is this element declared in Java (rather than Kotlin) ?
     *
     * See [sourceLanguage].
     */
    fun isJava() = sourceLanguage.isJava()

    /**
     * Is this element declared in Kotlin (rather than Java) ?
     *
     * See [sourceLanguage].
     */
    fun isKotlin() = sourceLanguage.isKotlin()

    /**
     * Returns true if this [Item]'s modifier list contains any suppress compatibility
     * meta-annotations.
     *
     * Metalava will suppress compatibility checks for APIs which are within the scope of a
     * "suppress compatibility" meta-annotation, but they may still be written to API files or stub
     * JARs.
     *
     * "Suppress compatibility" meta-annotations allow Metalava to handle concepts like Jetpack
     * experimental APIs, where developers can use the [RequiresOptIn] meta-annotation to mark
     * feature sets with unstable APIs.
     */
    fun hasSuppressCompatibilityMetaAnnotation(): Boolean =
        codebase.annotationManager.hasSuppressCompatibilityMetaAnnotations(modifiers)

    override val fileLocation: FileLocation
        get() = FileLocation.UNKNOWN

    /**
     * Produces a user visible description of this item, including a label such as "class" or
     * "field"
     */
    fun describe(capitalize: Boolean = false): String

    /** Returns the package that contains this item. */
    fun containingPackage(): PackageItem?

    /** Returns the class that contains this item. */
    fun containingClass(): ClassItem?

    /**
     * Returns the associated type, if any.
     *
     * i.e.
     * * For a field, property or parameter, this is the type of the variable.
     * * For a method, it's the return type.
     * * For classes it's the declared class type, i.e. a class type using the type parameter types
     *   as the type arguments.
     * * For type parameters it's a [VariableTypeItem] reference the type parameter.
     * * For packages and files, it's null.
     * * For type aliases it's the underlying type for which the alias is an alternative name.
     */
    fun type(): TypeItem?

    /**
     * Set the type of this.
     *
     * The [type] parameter must be of the same concrete type as returned by the [Item.type] method.
     */
    fun setType(type: TypeItem)

    /**
     * Find the [Item] in [codebase] that corresponds to this item, or `null` if there is no such
     * item.
     *
     * If [superMethods] is true and this is a [MethodItem] then the returned [MethodItem], if any,
     * could be in a [ClassItem] that does not correspond to the [MethodItem.containingClass], it
     * could be from a super class or super interface. e.g. if the [codebase] contains something
     * like:
     * ```
     *     public class Super {
     *         public void method() {...}
     *     }
     *     public class Foo extends Super {}
     * ```
     *
     * And this is called on `Foo.method()` then:
     * * if [superMethods] is false this will return `null`.
     * * if [superMethods] is true and [duplicate] is false, then this will return `Super.method()`.
     * * if both [superMethods] and [duplicate] are true then this will return a duplicate of
     *   `Super.method()` that has been added to `Foo` so it will be essentially `Foo.method()`.
     *
     * @param codebase the [Codebase] to search for a corresponding item.
     * @param superMethods if true and this is a [MethodItem] then this method will search for super
     *   methods. If this is a [ParameterItem] then the value of this parameter will be passed to
     *   the [findCorrespondingItemIn] call which is used to find the [MethodItem] corresponding to
     *   the [ParameterItem.containingCallable].
     * @param duplicate if true, and this is a [MemberItem] (or [ParameterItem]) then the returned
     *   [Item], if any, will be in the [ClassItem] that corresponds to the [Item.containingClass].
     *   This should be `true` if the returned [Item] is going to be compared to the original [Item]
     *   as the [Item.containingClass] can affect that comparison, e.g. the meaning of certain
     *   modifiers.
     */
    fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean = false,
        duplicate: Boolean = false,
    ): Item?

    /**
     * Get the set of suppressed issues for this [Item].
     *
     * These are the values supplied to any of the [SUPPRESS_ANNOTATIONS] on this item. It DOES not
     * include suppressed issues from the [parent].
     */
    override fun suppressedIssues(): Set<String>

    /** The [BaselineKey] for this. */
    override val baselineKey
        get() = BaselineKey.forElementId(baselineElementId())

    /**
     * Get the baseline element id from which [baselineKey] is constructed.
     *
     * See [BaselineKey.forElementId] for more details.
     */
    fun baselineElementId(): String

    /** The languages from which this [Item] can be used. */
    val targetLanguages: Set<TargetLanguage>
}
