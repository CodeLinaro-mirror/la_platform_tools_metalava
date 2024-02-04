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

import com.android.tools.metalava.model.TypeItem.Companion.equals
import java.util.Objects
import java.util.function.Predicate

/**
 * Whether metalava supports type use annotations. Note that you can't just turn this flag back on;
 * you have to also add TYPE_USE back to the handful of nullness annotations in
 * stub-annotations/src/main/java/.
 */
const val SUPPORT_TYPE_USE_ANNOTATIONS = false

/**
 * Represents a {@link https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Type.html Type}
 */
@MetalavaApi
interface TypeItem {
    /** Modifiers for the type. Contains type-use annotation information. */
    val modifiers: TypeModifiers

    fun accept(visitor: TypeVisitor)

    /**
     * Whether this type is equal to [other], not considering modifiers.
     *
     * This is implemented on each sub-interface of [TypeItem] instead of [equals] because
     * interfaces are not allowed to implement [equals]. An [equals] implementation is provided by
     * [DefaultTypeItem].
     */
    fun equalToType(other: TypeItem?): Boolean

    /**
     * Hashcode for the type.
     *
     * This is implemented on each sub-interface of [TypeItem] instead of [hashCode] because
     * interfaces are not allowed to implement [hashCode]. A [hashCode] implementation is provided
     * by [DefaultTypeItem].
     */
    fun hashCodeForType(): Int

    /**
     * Generates a string for this type.
     *
     * @param annotations For a type like this: @Nullable java.util.List<@NonNull java.lang.String>,
     *   [annotations] controls whether the annotations like @Nullable and @NonNull are included.
     * @param kotlinStyleNulls Controls whether it should return "@Nullable List<String>" as
     *   "List<String!>?".
     * @param filter Specifies a filter to apply to the type annotations, if any.
     * @param spaceBetweenParameters Controls whether there should be a space between class type
     *   parameters, e.g. "java.util.Map<java.lang.Integer, java.lang.Number>" or
     *   "java.util.Map<java.lang.Integer,java.lang.Number>".
     */
    fun toTypeString(
        annotations: Boolean = false,
        kotlinStyleNulls: Boolean = false,
        filter: Predicate<Item>? = null,
        spaceBetweenParameters: Boolean = false
    ): String

    /** Legacy alias for [toErasedTypeString]`()`. */
    @Deprecated(
        "the context item is no longer used",
        replaceWith = ReplaceWith("toErasedTypeString()")
    )
    @MetalavaApi
    fun toErasedTypeString(context: Item?): String = toErasedTypeString()

    /**
     * Get a string representation of the erased type.
     *
     * Implements the behavior described
     * [here](https://docs.oracle.com/javase/tutorial/java/generics/genTypes.html).
     *
     * One point to note is that vararg parameters are represented using standard array syntax, i.e.
     * `[]`, not the special source `...` syntax. The reason for that is that the erased type is
     * mainly used at runtime which treats a vararg parameter as a standard array type.
     */
    @MetalavaApi fun toErasedTypeString(): String

    /** Array dimensions of this type; for example, for String it's 0 and for String[][] it's 2. */
    @MetalavaApi fun arrayDimensions(): Int = 0

    /** Returns the internal name of the type, as seen in bytecode. */
    fun internalName(): String

    fun asClass(): ClassItem?

    fun toSimpleType(): String {
        return stripJavaLangPrefix(toTypeString())
    }

    /**
     * Helper methods to compare types, especially types from signature files with types from
     * parsing, which may have slightly different formats, e.g. varargs ("...") versus arrays
     * ("[]"), java.lang. prefixes removed in wildcard signatures, etc.
     */
    fun toCanonicalType(): String {
        var s = toTypeString()
        while (s.contains(JAVA_LANG_PREFIX)) {
            s = s.replace(JAVA_LANG_PREFIX, "")
        }
        if (s.contains("...")) {
            s = s.replace("...", "[]")
        }

        return s
    }

    /**
     * Makes substitutions to the type based on the [typeParameterBindings]. For instance, if the
     * [typeParameterBindings] contains `{T -> String}`, calling this method on `T` would return
     * `String`, and calling it on `List<T>` would return `List<String>` (in both cases the
     * modifiers on the `String` will be independently mutable from the `String` in the
     * [typeParameterBindings]). Calling it on an unrelated type like `int` would return a duplicate
     * of that type.
     *
     * This method is intended to be used in conjunction with [ClassItem.mapTypeVariables],
     */
    fun convertType(typeParameterBindings: TypeParameterBindings): TypeItem

    fun convertType(from: ClassItem, to: ClassItem): TypeItem {
        val map = from.mapTypeVariables(to)
        if (map.isNotEmpty()) {
            return convertType(map)
        }

        return this
    }

    fun isJavaLangObject(): Boolean = false

    fun isString(): Boolean = false

    fun defaultValue(): Any? = null

    fun defaultValueString(): String = "null"

    /**
     * Check to see whether this type has any type arguments.
     *
     * It only checks this [TypeItem], and does not recurse down into any others, so it will return
     * `true` for say `List<T>`, but `false` for `List<T>[]` and `T`.
     */
    fun hasTypeArguments(): Boolean = false

    /** Creates an identical type, with a copy of this type's modifiers so they can be mutated. */
    fun duplicate(): TypeItem

    companion object {
        /** Shortens types, if configured */
        fun shortenTypes(type: String): String {
            var cleaned = type
            if (cleaned.contains("@androidx.annotation.")) {
                cleaned = cleaned.replace("@androidx.annotation.", "@")
            }
            return stripJavaLangPrefix(cleaned)
        }

        /**
         * Removes java.lang. prefixes from types, unless it's in a subpackage such as
         * java.lang.reflect. For simplicity we may also leave inner classes in the java.lang
         * package untouched.
         *
         * NOTE: We only remove this from the front of the type; e.g. we'll replace
         * java.lang.Class<java.lang.String> with Class<java.lang.String>. This is because the
         * signature parsing of types is not 100% accurate and we don't want to run into trouble
         * with more complicated generic type signatures where we end up not mapping the simplified
         * types back to the real fully qualified type names.
         */
        fun stripJavaLangPrefix(type: String): String {
            if (type.startsWith(JAVA_LANG_PREFIX)) {
                // Replacing java.lang is harder, since we don't want to operate in sub packages,
                // e.g. java.lang.String -> String, but java.lang.reflect.Method -> unchanged
                val start = JAVA_LANG_PREFIX.length
                val end = type.length
                for (index in start until end) {
                    if (type[index] == '<') {
                        return type.substring(start)
                    } else if (type[index] == '.') {
                        return type
                    }
                }

                return type.substring(start)
            }

            return type
        }

        /**
         * Create a [Comparator] that when given two [TypeItem] will treat them as equal if either
         * returns `null` from [TypeItem.asClass] and will otherwise compare the two [ClassItem]s
         * using [comparator].
         *
         * This only defines a partial ordering over [TypeItem].
         */
        private fun typeItemAsClassComparator(
            comparator: Comparator<ClassItem>
        ): Comparator<TypeItem> {
            return Comparator { type1, type2 ->
                val cls1 = type1.asClass()
                val cls2 = type2.asClass()
                if (cls1 != null && cls2 != null) {
                    comparator.compare(cls1, cls2)
                } else {
                    0
                }
            }
        }

        /** A total ordering over [TypeItem] comparing [TypeItem.toTypeString]. */
        private val typeStringComparator =
            Comparator.comparing<TypeItem, String> { it.toTypeString() }

        /**
         * A total ordering over [TypeItem] comparing [TypeItem.asClass] using
         * [ClassItem.fullNameThenQualifierComparator] and then comparing [TypeItem.toTypeString].
         */
        val totalComparator: Comparator<TypeItem> =
            typeItemAsClassComparator(ClassItem.fullNameThenQualifierComparator)
                .thenComparing(typeStringComparator)

        @Deprecated(
            "" +
                "this should not be used as it only defines a partial ordering which means that the " +
                "source order will affect the result"
        )
        val partialComparator: Comparator<TypeItem> = Comparator { type1, type2 ->
            val cls1 = type1.asClass()
            val cls2 = type2.asClass()
            if (cls1 != null && cls2 != null) {
                ClassItem.fullNameComparator.compare(cls1, cls2)
            } else {
                type1.toTypeString().compareTo(type2.toTypeString())
            }
        }

        /**
         * Convert a type string containing to its lambda representation or return the original.
         *
         * E.g.: `"kotlin.jvm.functions.Function1<Integer, String>"` to `"(Integer) -> String"`.
         */
        fun toLambdaFormat(typeName: String): String {
            // Bail if this isn't a Kotlin function type
            if (!typeName.startsWith(KOTLIN_FUNCTION_PREFIX)) {
                return typeName
            }

            // Find the first character after the first opening angle bracket. This will either be
            // the first character of the paramTypes of the lambda if it has parameters.
            val paramTypesStart =
                typeName.indexOf('<', startIndex = KOTLIN_FUNCTION_PREFIX.length) + 1

            // The last type param is always the return type. We find and set these boundaries with
            // the push down loop below.
            var paramTypesEnd = -1
            var returnTypeStart = -1

            // Get the exclusive end of the return type parameter by finding the last closing
            // angle bracket.
            val returnTypeEnd = typeName.lastIndexOf('>')

            // Bail if an an unexpected format broke the indexOf's above.
            if (paramTypesStart <= 0 || paramTypesStart >= returnTypeEnd) {
                return typeName
            }

            // This loop looks for the last comma that is not inside the type parameters of a type
            // parameter. It's a simple push down state machine that stores its depth as a counter
            // instead of a stack. It runs backwards from the last character of the type parameters
            // just before the last closing angle bracket to the beginning just before the first
            // opening angle bracket.
            var depth = 0
            for (i in returnTypeEnd - 1 downTo paramTypesStart) {
                val c = typeName[i]

                // Increase or decrease stack depth on angle brackets
                when (c) {
                    '>' -> depth++
                    '<' -> depth--
                }

                when {
                    depth == 0 ->
                        when { // At the top level
                            c == ',' -> {
                                // When top level comma is found, mark it as the exclusive end of
                                // the
                                // parameter types and end the loop
                                paramTypesEnd = i
                                break
                            }
                            !c.isWhitespace() -> {
                                // Keep moving the start of the return type back until whitespace
                                returnTypeStart = i
                            }
                        }
                    depth < 0 -> return typeName // Bail, unbalanced nesting
                }
            }

            // Bail if some sort of unbalanced nesting occurred or the indices around the comma
            // appear grossly incorrect.
            if (depth > 0 || returnTypeStart < 0 || returnTypeStart <= paramTypesEnd) {
                return typeName
            }

            return buildString(typeName.length) {
                append("(")

                // Slice param types, if any, and append them between the parenthesis
                if (paramTypesEnd > 0) {
                    append(typeName, paramTypesStart, paramTypesEnd)
                }

                append(") -> ")

                // Slice out the return type param and append it after the arrow
                append(typeName, returnTypeStart, returnTypeEnd)
            }
        }

        /** Prefix of Kotlin JVM function types, used for lambdas. */
        private const val KOTLIN_FUNCTION_PREFIX = "kotlin.jvm.functions.Function"
    }
}

/**
 * A mapping from one class's type parameters to the types provided for those type parameters in a
 * possibly indirect subclass.
 *
 * e.g. Given `Map<K, V>` and a subinterface `StringToIntMap extends Map<String, Integer>` then this
 * would contain a mapping from `K -> String` and `V -> Integer`.
 */
typealias TypeParameterBindings = Map<TypeParameterItem, TypeItem>

abstract class DefaultTypeItem(private val codebase: Codebase) : TypeItem {

    private lateinit var cachedDefaultType: String
    private lateinit var cachedErasedType: String

    override fun toString(): String = toTypeString()

    override fun toTypeString(
        annotations: Boolean,
        kotlinStyleNulls: Boolean,
        filter: Predicate<Item>?,
        spaceBetweenParameters: Boolean
    ): String {
        return toTypeString(
            TypeStringConfiguration(
                codebase,
                annotations,
                kotlinStyleNulls,
                filter,
                spaceBetweenParameters
            )
        )
    }

    private fun toTypeString(configuration: TypeStringConfiguration): String {
        // Cache the default type string. Other configurations are less likely to be reused.
        return if (configuration.isDefault) {
            if (!::cachedDefaultType.isInitialized) {
                cachedDefaultType = buildString {
                    appendTypeString(this@DefaultTypeItem, configuration)
                }
            }
            cachedDefaultType
        } else {
            buildString { appendTypeString(this@DefaultTypeItem, configuration) }
        }
    }

    override fun toErasedTypeString(): String {
        if (!::cachedErasedType.isInitialized) {
            cachedErasedType = buildString { appendErasedTypeString(this@DefaultTypeItem) }
        }
        return cachedErasedType
    }

    override fun internalName(): String {
        // Default implementation; PSI subclass is more accurate
        return toSlashFormat(toErasedTypeString())
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TypeItem) return false
        return equalToType(other)
    }

    override fun hashCode(): Int = hashCodeForType()

    companion object {
        /**
         * Configuration options for how to represent a type as a string.
         *
         * @param codebase The codebase the type is in.
         * @param annotations Whether to include annotations on the type.
         * @param kotlinStyleNulls Whether to represent nullability with Kotlin-style suffixes: `?`
         *   for nullable, no suffix for non-null, and `!` for platform nullability. For example,
         *   the Java type `@Nullable List<String>` would be represented as `List<String!>?`.
         * @param filter A filter to apply to the type annotations, if any.
         * @param spaceBetweenParameters Whether to include a space between class type params.
         */
        private data class TypeStringConfiguration(
            val codebase: Codebase,
            val annotations: Boolean = false,
            val kotlinStyleNulls: Boolean = false,
            val filter: Predicate<Item>? = null,
            val spaceBetweenParameters: Boolean = false,
        ) {
            val isDefault =
                !annotations && !kotlinStyleNulls && filter == null && !spaceBetweenParameters
        }

        private fun StringBuilder.appendTypeString(
            type: TypeItem,
            configuration: TypeStringConfiguration
        ) {
            when (type) {
                is PrimitiveTypeItem -> {
                    if (configuration.annotations) {
                        appendAnnotations(type.modifiers, configuration)
                    }
                    append(type.kind.primitiveName)
                    // Primitives must be non-null.
                }
                is ArrayTypeItem -> {
                    // The ordering of array annotations means this can't just use a recursive
                    // approach for annotated multi-dimensional arrays, but it can if annotations
                    // aren't included.
                    if (configuration.annotations) {
                        var deepComponentType = type.componentType
                        val arrayModifiers = mutableListOf(type.modifiers)
                        while (deepComponentType is ArrayTypeItem) {
                            arrayModifiers.add(deepComponentType.modifiers)
                            deepComponentType = deepComponentType.componentType
                        }
                        val suffixes = arrayModifiers.map { it.nullability().suffix }.reversed()

                        // Print the innermost component type.
                        appendTypeString(deepComponentType, configuration)

                        // Print modifiers from the outermost array type in, and the array suffixes.
                        arrayModifiers.zip(suffixes).forEachIndexed { index, (modifiers, suffix) ->
                            appendAnnotations(modifiers, configuration, leadingSpace = true)
                            // Only the outermost array can be varargs.
                            if (index < arrayModifiers.size - 1 || !type.isVarargs) {
                                append("[]")
                            } else {
                                append("...")
                            }
                            if (configuration.kotlinStyleNulls) {
                                append(suffix)
                            }
                        }
                    } else {
                        // Non-annotated case: just recur to the component
                        appendTypeString(type.componentType, configuration)
                        if (type.isVarargs) {
                            append("...")
                        } else {
                            append("[]")
                        }
                        if (configuration.kotlinStyleNulls) {
                            append(type.modifiers.nullability().suffix)
                        }
                    }
                }
                is ClassTypeItem -> {
                    if (type.outerClassType != null) {
                        appendTypeString(type.outerClassType!!, configuration)
                        append('.')
                        if (configuration.annotations) {
                            appendAnnotations(type.modifiers, configuration)
                        }
                        append(type.className)
                    } else {
                        if (configuration.annotations) {
                            append(type.qualifiedName.substringBeforeLast(type.className))
                            appendAnnotations(type.modifiers, configuration)
                            append(type.className)
                        } else {
                            append(type.qualifiedName)
                        }
                    }

                    if (type.arguments.isNotEmpty()) {
                        append("<")
                        type.arguments.forEachIndexed { index, parameter ->
                            appendTypeString(parameter, configuration)
                            if (index != type.arguments.size - 1) {
                                append(",")
                                if (configuration.spaceBetweenParameters) {
                                    append(" ")
                                }
                            }
                        }
                        append(">")
                    }
                    if (configuration.kotlinStyleNulls) {
                        append(type.modifiers.nullability().suffix)
                    }
                }
                is VariableTypeItem -> {
                    if (configuration.annotations) {
                        appendAnnotations(type.modifiers, configuration)
                    }
                    append(type.name)
                    if (configuration.kotlinStyleNulls) {
                        append(type.modifiers.nullability().suffix)
                    }
                }
                is WildcardTypeItem -> {
                    if (configuration.annotations) {
                        appendAnnotations(type.modifiers, configuration)
                    }
                    append("?")
                    type.extendsBound?.let {
                        // Leave out object bounds, because they're implied
                        if (!it.isJavaLangObject()) {
                            append(" extends ")
                            appendTypeString(it, configuration)
                        }
                    }
                    type.superBound?.let {
                        append(" super ")
                        appendTypeString(it, configuration)
                    }
                    // It doesn't make sense to have a nullness suffix on a wildcard, this should be
                    // handled by the bound.
                }
            }
        }

        private fun StringBuilder.appendAnnotations(
            modifiers: TypeModifiers,
            configuration: TypeStringConfiguration,
            leadingSpace: Boolean = false,
            trailingSpace: Boolean = true
        ) {
            val annotations =
                modifiers.annotations().filter { annotation ->
                    // If Kotlin-style nulls are printed, nullness annotations shouldn't be.
                    if (configuration.kotlinStyleNulls && annotation.isNullnessAnnotation()) {
                        return@filter false
                    }

                    val filter = configuration.filter ?: return@filter true
                    val qualifiedName = annotation.qualifiedName ?: return@filter true
                    val annotationClass =
                        configuration.codebase.findClass(qualifiedName) ?: return@filter true
                    filter.test(annotationClass)
                }
            if (annotations.isEmpty()) return

            if (leadingSpace) {
                append(' ')
            }
            annotations.forEachIndexed { index, annotation ->
                append(annotation.toSource())
                if (index != annotations.size - 1) {
                    append(' ')
                }
            }
            if (trailingSpace) {
                append(' ')
            }
        }

        private fun StringBuilder.appendErasedTypeString(type: TypeItem) {
            when (type) {
                is PrimitiveTypeItem -> append(type.kind.primitiveName)
                is ArrayTypeItem -> {
                    appendErasedTypeString(type.componentType)
                    append("[]")
                }
                is ClassTypeItem -> append(type.qualifiedName)
                is VariableTypeItem ->
                    type.asTypeParameter.typeBounds().firstOrNull()?.let {
                        appendErasedTypeString(it)
                    }
                        ?: append(JAVA_LANG_OBJECT)
                else ->
                    throw IllegalStateException(
                        "should never visit $type of type ${type.javaClass} while generating erased type string"
                    )
            }
        }

        // Copied from doclava1
        private fun toSlashFormat(typeName: String): String {
            var name = typeName
            var dimension = ""
            while (name.endsWith("[]")) {
                dimension += "["
                name = name.substring(0, name.length - 2)
            }

            val base: String
            base =
                when (name) {
                    "void" -> "V"
                    "byte" -> "B"
                    "boolean" -> "Z"
                    "char" -> "C"
                    "short" -> "S"
                    "int" -> "I"
                    "long" -> "J"
                    "float" -> "F"
                    "double" -> "D"
                    else -> "L" + getInternalName(name) + ";"
                }

            return dimension + base
        }

        /**
         * Computes the internal class name of the given fully qualified class name. For example, it
         * converts foo.bar.Foo.Bar into foo/bar/Foo$Bar
         *
         * @param qualifiedName the fully qualified class name
         * @return the internal class name
         */
        private fun getInternalName(qualifiedName: String): String {
            if (qualifiedName.indexOf('.') == -1) {
                return qualifiedName
            }

            // If class name contains $, it's not an ambiguous inner class name.
            if (qualifiedName.indexOf('$') != -1) {
                return qualifiedName.replace('.', '/')
            }
            // Let's assume that components that start with Caps are class names.
            return buildString {
                var prev: String? = null
                for (part in qualifiedName.split(".")) {
                    if (!prev.isNullOrEmpty()) {
                        if (Character.isUpperCase(prev[0])) {
                            append('$')
                        } else {
                            append('/')
                        }
                    }
                    append(part)
                    prev = part
                }
            }
        }
    }
}

/** Represents a primitive type, like int or boolean. */
interface PrimitiveTypeItem : TypeItem {
    /** The kind of [Primitive] this type is. */
    val kind: Primitive

    /** The possible kinds of primitives. */
    enum class Primitive(
        val primitiveName: String,
        val defaultValue: Any?,
        val defaultValueString: String
    ) {
        BOOLEAN("boolean", false, "false"),
        BYTE("byte", 0.toByte(), "0"),
        CHAR("char", 0.toChar(), "0"),
        DOUBLE("double", 0.0, "0"),
        FLOAT("float", 0F, "0"),
        INT("int", 0, "0"),
        LONG("long", 0L, "0"),
        SHORT("short", 0.toShort(), "0"),
        VOID("void", null, "null")
    }

    override fun defaultValue(): Any? = kind.defaultValue

    override fun defaultValueString(): String = kind.defaultValueString

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun duplicate(): PrimitiveTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): PrimitiveTypeItem {
        return duplicate()
    }

    override fun equalToType(other: TypeItem?): Boolean {
        return (other as? PrimitiveTypeItem)?.kind == kind
    }

    override fun hashCodeForType(): Int = kind.hashCode()

    override fun asClass(): ClassItem? = null
}

/** Represents an array type, including vararg types. */
interface ArrayTypeItem : TypeItem {
    /** The array's inner type (which for multidimensional arrays is another array type). */
    val componentType: TypeItem

    /** Whether this array type represents a varargs parameter. */
    val isVarargs: Boolean

    override fun arrayDimensions(): Int = 1 + componentType.arrayDimensions()

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun duplicate(): ArrayTypeItem = duplicate(componentType.duplicate())

    /**
     * Duplicates this type (including duplicating the modifiers so they can be independently
     * mutated), but substituting in the provided [componentType] in place of this type's component.
     */
    fun duplicate(componentType: TypeItem): ArrayTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): ArrayTypeItem {
        return duplicate(componentType.convertType(typeParameterBindings))
    }

    override fun equalToType(other: TypeItem?): Boolean {
        if (other !is ArrayTypeItem) return false
        return isVarargs == other.isVarargs && componentType.equalToType(other.componentType)
    }

    override fun hashCodeForType(): Int = Objects.hash(isVarargs, componentType)

    override fun asClass(): ClassItem? = componentType.asClass()
}

/** Represents a class type. */
interface ClassTypeItem : TypeItem {
    /** The qualified name of this class, e.g. "java.lang.String". */
    val qualifiedName: String

    /**
     * The class type's arguments, empty if it has none.
     *
     * i.e. The specific types that this class type assigns to each of the referenced [ClassItem]'s
     * type parameters.
     */
    val arguments: List<TypeItem>

    /** The outer class type of this class, if it is an inner type. */
    val outerClassType: ClassTypeItem?

    /**
     * The name of the class, e.g. "String" for "java.lang.String" and "Inner" for
     * "test.pkg.Outer.Inner".
     */
    val className: String

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun hasTypeArguments() = arguments.isNotEmpty()

    override fun isString(): Boolean = qualifiedName == JAVA_LANG_STRING

    override fun isJavaLangObject(): Boolean = qualifiedName == JAVA_LANG_OBJECT

    override fun duplicate(): ClassTypeItem =
        duplicate(outerClassType?.duplicate(), arguments.map { it.duplicate() })

    /**
     * Duplicates this type (including duplicating the modifiers, so they can be independently
     * mutated), but substituting in the provided [outerClass] and [arguments] in place of this
     * instance's [outerClass] and [arguments].
     */
    fun duplicate(outerClass: ClassTypeItem?, arguments: List<TypeItem>): ClassTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): ClassTypeItem {
        return duplicate(
            outerClassType?.convertType(typeParameterBindings),
            arguments.map { it.convertType(typeParameterBindings) }
        )
    }

    override fun equalToType(other: TypeItem?): Boolean {
        if (other !is ClassTypeItem) return false
        return qualifiedName == other.qualifiedName &&
            arguments.size == other.arguments.size &&
            arguments.zip(other.arguments).all { (p1, p2) -> p1.equalToType(p2) } &&
            ((outerClassType == null && other.outerClassType == null) ||
                outerClassType?.equalToType(other.outerClassType) == true)
    }

    override fun hashCodeForType(): Int = Objects.hash(qualifiedName, outerClassType, arguments)

    companion object {
        /** Computes the simple name of a class from a qualified class name. */
        fun computeClassName(qualifiedName: String): String {
            val lastDotIndex = qualifiedName.lastIndexOf('.')
            return if (lastDotIndex == -1) {
                qualifiedName
            } else {
                qualifiedName.substring(lastDotIndex + 1)
            }
        }
    }
}

/** Represents a type variable type. */
interface VariableTypeItem : TypeItem {
    /** The name of the type variable */
    val name: String

    /** The corresponding type parameter for this type variable. */
    val asTypeParameter: TypeParameterItem

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun convertType(typeParameterBindings: TypeParameterBindings): TypeItem {
        return (typeParameterBindings[asTypeParameter] ?: this).duplicate()
    }

    override fun duplicate(): VariableTypeItem

    override fun equalToType(other: TypeItem?): Boolean {
        return (other as? VariableTypeItem)?.name == name
    }

    override fun hashCodeForType(): Int = name.hashCode()
}

/**
 * Represents a wildcard type, like `?`, `? extends String`, and `? super String` in Java, or `*`,
 * `out String`, and `in String` in Kotlin.
 */
interface WildcardTypeItem : TypeItem {
    /** The type this wildcard must extend. If null, the extends bound is implicitly `Object`. */
    val extendsBound: TypeItem?

    /** The type this wildcard must be a super class of. */
    val superBound: TypeItem?

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun duplicate(): WildcardTypeItem =
        duplicate(extendsBound?.duplicate(), superBound?.duplicate())

    /**
     * Duplicates this type (including duplicating the modifiers so they can be independently
     * mutated), but substituting in the provided [extendsBound] and [superBound] in place of this
     * type's bounds.
     */
    fun duplicate(extendsBound: TypeItem?, superBound: TypeItem?): WildcardTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): WildcardTypeItem {
        return duplicate(
            extendsBound?.convertType(typeParameterBindings),
            superBound?.convertType(typeParameterBindings)
        )
    }

    override fun equalToType(other: TypeItem?): Boolean {
        if (other !is WildcardTypeItem) return false
        return extendsBound?.equalToType(other.extendsBound) != false &&
            superBound?.equalToType(other.superBound) != false
    }

    override fun hashCodeForType(): Int = Objects.hash(extendsBound, superBound)

    override fun asClass(): ClassItem? {
        TODO("Not yet implemented")
    }
}

/** Different uses to which a [TypeItem] might be used that might affect its construction. */
enum class TypeUse {
    /** General type use; no special behavior. */
    GENERAL,

    /**
     * Super type, e.g. in an `extends` or `implements` list; is always [TypeNullability.NONNULL].
     */
    SUPER_TYPE,
}
