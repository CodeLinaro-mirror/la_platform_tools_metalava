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

import com.android.tools.metalava.model.type.InternalTypeItemFactory
import com.android.tools.metalava.model.utils.extractSimpleName
import java.util.Objects

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

    fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>)

    /**
     * Whether this type is equal to [other]. If [includeNullability] is false, does not consider
     * modifiers. If [includeNullability] is true, nullability is considered but not annotations.
     *
     * This is implemented on each sub-interface of [TypeItem] instead of [equals] because
     * interfaces are not allowed to implement [equals]. An [equals] implementation is provided by
     * [DefaultTypeItem].
     */
    fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean

    /**
     * Hashcode for the type.
     *
     * This is implemented on each sub-interface of [TypeItem] instead of [hashCode] because
     * interfaces are not allowed to implement [hashCode]. A [hashCode] implementation is provided
     * by [DefaultTypeItem].
     */
    fun hashCodeForType(): Int

    /**
     * Provide a helpful description of the type, for use in error messages.
     *
     * This is not suitable for use in signature or stubs as while it defaults to [toTypeString] for
     * most types it is overridden by others to provide additional information.
     */
    fun description(): String = toTypeString()

    /**
     * Generates a string for this type.
     *
     * @see [TypeStringConfiguration] for information on the parameters.
     */
    fun toTypeString(
        configuration: TypeStringConfiguration = TypeStringConfiguration.DEFAULT
    ): String

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

    /** Returns the internal name of the type, as seen in bytecode. */
    fun internalName(): String

    fun toSimpleTypeString() = toTypeString(SIMPLE_TYPE_CONFIGURATION)

    /**
     * Provide a canonical string representation of this type.
     *
     * Helper methods to compare types, especially types from signature files with types from
     * parsing, which may have slightly different formats, e.g. varargs (`...`) versus arrays
     * (`[]`), java.lang. prefixes removed in wildcard signatures, etc.
     */
    fun toCanonicalTypeString() = toTypeString(CANONICAL_TYPE_CONFIGURATION)

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

    /**
     * Return an erased form of this [TypeItem].
     *
     * No annotations, no type arguments, no variables.
     */
    fun asErasedType(): TypeItem

    fun isJavaLangObject(): Boolean = false

    fun isString(): Boolean = false

    fun defaultValue(): Any? = null

    fun defaultValueString(): String = "null"

    /**
     * Duplicates this type substituting in the provided [modifiers] in place of this instance's
     * [modifiers].
     */
    fun substitute(modifiers: TypeModifiers): TypeItem

    /**
     * Return a [TypeItem] instance identical to this on except its [modifiers]'s
     * [TypeModifiers.nullability] property is the same as the [nullability] parameter.
     *
     * If the parameter is the same as this instance's [modifiers]'s property then it will just
     * return this instance, otherwise it will return a new instance with a new [TypeModifiers].
     */
    fun substitute(nullability: TypeNullability) =
        if (modifiers.nullability == nullability) this
        else substitute(modifiers.substitute(nullability))

    /**
     * Return a [TypeItem] instance of the same type as this one that was produced by the [TypeItem]
     * appropriate [TypeTransformer.transform] method.
     */
    fun transform(transformer: TypeTransformer): TypeItem

    /** Whether this type was originally a value class type. Defaults to false if not overridden. */
    val isValueClassType
        get() = false

    /**
     * Returns whether this type is SAM convertible or a Kotlin lambda.
     *
     * If a final parameter uses a SAM convertible or lambda type, it also means that it could be
     * called in Kotlin using the trailing lambda syntax.
     *
     * Specifically this will attempt to handle the follow cases:
     * - Java SAM interface = true
     * - Kotlin SAM interface = false // Kotlin (non-fun) interfaces are not SAM convertible
     * - Kotlin fun interface = true
     * - Kotlin lambda = true
     * - Variable type with Kotlin lambda bound = true
     * - Any other type = false
     */
    fun isSamCompatibleOrKotlinLambda(classResolver: ClassResolver): Boolean {
        // Overrides are present on ClassTypeItem, LambdaTypeItem, and VariableTypeItem
        return false
    }

    companion object : InternalTypeItemFactory {
        /** [TypeStringConfiguration] for [toSimpleTypeString] to pass to [toTypeString]. */
        private val SIMPLE_TYPE_CONFIGURATION =
            TypeStringConfiguration(stripJavaLangPrefix = StripJavaLangPrefix.LEGACY)

        /** [TypeStringConfiguration] for [toCanonicalTypeString] to pass to [toTypeString]. */
        private val CANONICAL_TYPE_CONFIGURATION =
            TypeStringConfiguration(
                stripJavaLangPrefix = StripJavaLangPrefix.ALWAYS,
                treatVarargsAsArray = true,
            )

        /** Shortens types, if configured */
        fun shortenTypes(type: String): String {
            var cleaned = type
            if (cleaned.contains("@androidx.annotation.")) {
                cleaned = cleaned.replace("@androidx.annotation.", "@")
            }
            return cleaned
        }

        /**
         * Returns the base [ClassTypeItem], if available, `null` otherwise.
         *
         * The base [ClassTypeItem] is computed as follows:
         * * For [ArrayTypeItem] it is the base [ClassTypeItem] of its
         *   [ArrayTypeItem.componentType].
         * * For [ClassTypeItem] (and [LambdaTypeItem]) it is the [ClassTypeItem].
         * * For [VariableTypeItem] is the [VariableTypeItem.asErasedType].
         * * For all other types it is `null`.
         */
        private fun TypeItem.baseClassType(): ClassTypeItem? =
            when (this) {
                is ArrayTypeItem -> innermostComponentType().baseClassType()
                is ClassTypeItem -> this
                is VariableTypeItem -> asErasedType()
                else -> null
            }

        /**
         * Create a [Comparator] that when given two [TypeItem] will try and extract from them a
         * [ClassTypeItem] (using [TypeItem.baseClassType] and if successful will compare them using
         * [classTypeComparator]. If unsuccessful then it will use the [fallbackComparator] to
         * compare the [TypeItem]s directly and if that is `null` then will treat them as equal.
         *
         * This only defines a partial ordering over [TypeItem]. It is the responsibility of the
         * caller to combine it with other [Comparator]s if a total ordering is required.
         */
        private fun typeItemAsClassComparator(
            classTypeComparator: Comparator<ClassTypeItem>,
            fallbackComparator: Comparator<TypeItem>? = null,
        ): Comparator<TypeItem> = Comparator { type1, type2 ->
            val classType1 = type1.baseClassType()
            val classType2 = type2.baseClassType()
            if (classType1 != null && classType2 != null) {
                classTypeComparator.compare(classType1, classType2)
            } else {
                fallbackComparator?.compare(type1, type2) ?: 0
            }
        }

        /** A partial ordering over [ClassTypeItem] comparing [ClassTypeItem.fullName]. */
        private val fullNameComparator: Comparator<ClassTypeItem> =
            Comparator.comparing { @Suppress("DEPRECATION") it.fullName() }

        /** A total ordering over [ClassTypeItem] comparing [ClassTypeItem.qualifiedName]. */
        private val qualifiedComparator: Comparator<ClassTypeItem> =
            Comparator.comparing { it.qualifiedName }

        /**
         * A total ordering over [ClassTypeItem] comparing [ClassTypeItem.fullName] first and then
         * [ClassTypeItem.qualifiedName].
         */
        private val fullNameThenQualifierComparator =
            fullNameComparator.thenComparing(qualifiedComparator)

        /** A total ordering over [TypeItem] comparing [TypeItem.toTypeString]. */
        private val typeStringComparator =
            Comparator.comparing<TypeItem, String> { it.toTypeString() }

        /**
         * A total ordering over [TypeItem] comparing [ClassTypeItem]s using
         * [TypeItem.fullNameThenQualifierComparator] and then comparing [TypeItem.toTypeString].
         */
        val totalComparator: Comparator<TypeItem> =
            typeItemAsClassComparator(fullNameThenQualifierComparator)
                .thenComparing(typeStringComparator)

        /**
         * A partial ordering over [TypeItem] using [fullNameComparator] to compare the result of
         * calling [TypeItem.baseClassType] and if that returned `null` for either type then it will
         * use [typeStringComparator] on the [TypeItem] directly.
         */
        @Deprecated(
            "" +
                "this should not be used as it only defines a partial ordering which means that the " +
                "source order will affect the result"
        )
        val partialComparator: Comparator<TypeItem> =
            typeItemAsClassComparator(fullNameComparator, typeStringComparator)
    }
}

/** Different ways of handling `java.lang.` prefix stripping in [TypeItem.toTypeString]. */
enum class StripJavaLangPrefix {
    /** Never strip java.lang. prefixes when */
    NEVER,

    /**
     * Only strip java.lang. prefixes from the start of the type as long as they are not a generic
     * varargs parameter.
     *
     * This is legacy behavior from when types were treated as strings.
     */
    LEGACY,

    /**
     * A special value that is only used internally within [TypeItem.toTypeString].
     *
     * If [LEGACY] was provided for a varargs type then [LEGACY] will be replaced by this when
     * processing the [ArrayTypeItem] to indicate to the nested [ClassTypeItem] that it is a varargs
     * parameter and to only strip the `java.lang.` prefix if it is at the start and is a generic
     * type.
     */
    VARARGS,

    /** Always strip java.lang. prefixes from the type. */
    ALWAYS,
}

/** Lambda for converting one [TypeItem] into a different [TypeItem]. */
typealias TypeItemConverter = (TypeItem) -> TypeItem

/**
 * A mapping from type parameters to types which should be substituted for these type parameters.
 *
 * The primary use case for the is to map from one class's type parameters to the types provided for
 * those type parameters in a possibly indirect subclass. It can also be used for a mapping from a
 * typealias's type parameters to the types provided for those type parameters in a usage of that
 * typealias.
 *
 * e.g. Given `Map<K, V>` and a subinterface `StringToIntMap extends Map<String, Integer>` then this
 * would contain a mapping from `K -> String` and `V -> Integer`.
 */
typealias TypeParameterBindings = Map<TypeParameterItem, TypeArgumentTypeItem>

abstract class DefaultTypeItem(
    final override val modifiers: TypeModifiers,
    override val isValueClassType: Boolean,
) : TypeItem {

    private lateinit var cachedDefaultType: String
    private lateinit var cachedErasedType: String

    override fun toString(): String = toTypeString()

    override fun toTypeString(configuration: TypeStringConfiguration): String {
        // Cache the default type string. Other configurations are less likely to be reused.
        return if (configuration.isDefault) {
            if (!::cachedDefaultType.isInitialized) {
                cachedDefaultType = generateTypeString(configuration)
            }
            cachedDefaultType
        } else {
            generateTypeString(configuration)
        }
    }

    /**
     * Generate a string representation of this type based on [configuration].
     *
     * The returned value will be cached if the [configuration] is the default.
     */
    private fun generateTypeString(configuration: TypeStringConfiguration) = buildString {
        appendTypeString(this@DefaultTypeItem, configuration)
    }

    override fun toErasedTypeString(): String {
        if (!::cachedErasedType.isInitialized) {
            cachedErasedType = toTypeString(ERASED_TYPE_STRING_CONFIGURATION)
        }
        return cachedErasedType
    }

    override fun internalName(): String {
        // Default implementation; PSI subclass is more accurate
        return toSlashFormat(toErasedTypeString())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeItem) return false
        return equalToType(other, includeNullability = false)
    }

    override fun hashCode(): Int = hashCodeForType()

    companion object {
        private val ERASED_TYPE_STRING_CONFIGURATION =
            TypeStringConfiguration(
                eraseGenerics = true,
                treatVarargsAsArray = true,
            )

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
                    // Get the nested configuration. This replaces StripJavaLangPrefix.LEGACY
                    // with StripJavaLangPrefix.VARARGS for a varargs type to maintain the legacy
                    // behavior of NOT stripping java.lang. prefix from varargs parameters unless it
                    // has type arguments.
                    val nestedConfiguration =
                        if (
                            type.isVarargs &&
                                configuration.stripJavaLangPrefix == StripJavaLangPrefix.LEGACY
                        ) {
                            configuration.copy(stripJavaLangPrefix = StripJavaLangPrefix.VARARGS)
                        } else configuration

                    // Compute the outermost array suffix as that can differ if it is varargs. If
                    // this is a varargs then it must be the outermost, otherwise the outermost is
                    // not a varargs so they will all use the same suffix.
                    val outermostArraySuffix =
                        if (type.isVarargs && !configuration.treatVarargsAsArray) "..." else "[]"

                    // The ordering of array annotations means this can't just use a recursive
                    // approach for annotated multidimensional arrays, but it can if annotations
                    // aren't included.
                    if (configuration.annotations) {
                        var deepComponentType = type.componentType
                        val arrayModifiers = mutableListOf(type.modifiers)
                        while (deepComponentType is ArrayTypeItem) {
                            arrayModifiers.add(deepComponentType.modifiers)
                            deepComponentType = deepComponentType.componentType
                        }
                        val suffixes = arrayModifiers.map { it.nullability.suffix }.reversed()

                        // Print the innermost component type.
                        appendTypeString(deepComponentType, nestedConfiguration)

                        // Print modifiers from the outermost array type in, and the array suffixes.
                        arrayModifiers.zip(suffixes).forEachIndexed { index, (modifiers, suffix) ->
                            appendAnnotations(modifiers, configuration, leadingSpace = true)
                            // The array suffix can be different on the outermost array type. The
                            // outermost is the last in the list.
                            if (index == arrayModifiers.lastIndex) {
                                append(outermostArraySuffix)
                            } else {
                                // Only the outermost array can be varargs.
                                append("[]")
                            }
                            if (configuration.kotlinStyleNulls) {
                                append(suffix)
                            }
                        }
                    } else {
                        // Non-annotated case: just recur to the component
                        appendTypeString(type.componentType, nestedConfiguration)
                        append(outermostArraySuffix)
                        if (configuration.kotlinStyleNulls) {
                            append(type.modifiers.nullability.suffix)
                        }
                    }
                }
                is ClassTypeItem -> {
                    if (type.outerClassType != null) {
                        // Legacy behavior for stripping java.lang. prefixes is to not strip them
                        // from nested classes. This replicates that by replacing LEGACY with
                        // NEVER in the configuration used to append the type string of the
                        // outermost class which is responsible for stripping the prefix.
                        val nestedConfiguration =
                            if (configuration.stripJavaLangPrefix == StripJavaLangPrefix.LEGACY)
                                configuration.copy(stripJavaLangPrefix = StripJavaLangPrefix.NEVER)
                            else configuration
                        appendTypeString(type.outerClassType!!, nestedConfiguration)
                        append(configuration.nestedClassSeparator)
                        if (configuration.annotations) {
                            appendAnnotations(type.modifiers, configuration)
                        }
                        append(type.className)
                    } else {
                        // Check to see whether a java.lang. prefix should be stripped from the type
                        // name.
                        val stripJavaLangPrefix =
                            when (configuration.stripJavaLangPrefix) {
                                StripJavaLangPrefix.ALWAYS -> true
                                StripJavaLangPrefix.LEGACY ->
                                    // This should only strip if this is at the start.
                                    isEmpty()
                                StripJavaLangPrefix.VARARGS ->
                                    // This should only strip if this is at the start and is a
                                    // generic type.
                                    isEmpty() && type.hasTypeArguments()
                                else -> false
                            }

                        // Get the class name prefix, i.e. the part before the class's simple name
                        // where annotations can be placed. e.g. for java.lang.String the simple
                        // name is `String` and the prefix is `java.lang.`.
                        val classNamePrefix = type.classNamePrefix

                        // Append the class name prefix unless it is `java.lang.` and `java.lang.`
                        // prefixes should be stripped.
                        if (!(stripJavaLangPrefix && classNamePrefix == JAVA_LANG_PREFIX)) {
                            append(classNamePrefix)
                        }
                        if (configuration.annotations) {
                            appendAnnotations(type.modifiers, configuration)
                        }
                        append(type.className)
                    }

                    if (!configuration.eraseGenerics && type.arguments.isNotEmpty()) {
                        append("<")
                        type.arguments.forEachIndexed { index, parameter ->
                            appendTypeString(parameter, configuration)
                            if (index != type.arguments.size - 1) {
                                append(",")
                                if (configuration.spaceBetweenTypeArguments) {
                                    append(" ")
                                }
                            }
                        }
                        append(">")
                    }
                    if (configuration.kotlinStyleNulls) {
                        append(type.modifiers.nullability.suffix)
                    }
                }
                is VariableTypeItem -> {
                    if (configuration.annotations) {
                        appendAnnotations(type.modifiers, configuration)
                    }
                    if (configuration.eraseGenerics) {
                        // Replace the type variable with the bounds of the type parameter.
                        val typeParameter = type.asTypeParameter
                        appendTypeString(typeParameter.asErasedType(), configuration)
                    } else {
                        append(type.name)
                    }
                    if (configuration.kotlinStyleNulls) {
                        append(type.modifiers.nullability.suffix)
                    }
                }
                is WildcardTypeItem -> {
                    if (configuration.annotations) {
                        appendAnnotations(type.modifiers, configuration)
                    }
                    append("?")

                    type.superBound?.let {
                        append(" super ")
                        appendTypeString(it, configuration)
                        // If there's a super bound, don't also print an object extends bound.
                        return
                    }

                    type.extendsBound?.let {
                        if (shouldIncludeExtendsBound(it, configuration)) {
                            append(" extends ")
                            appendTypeString(it, configuration)
                        }
                    }

                    // It doesn't make sense to have a nullness suffix on a wildcard, this should be
                    // handled by the bound.
                }
            }
        }

        /**
         * Returns whether the [extendsBound] should be included in the type string based on the
         * [configuration].
         */
        private fun shouldIncludeExtendsBound(
            extendsBound: ReferenceTypeItem,
            configuration: TypeStringConfiguration
        ): Boolean {
            // Non-object bounds should always be included.
            if (!extendsBound.isJavaLangObject()) return true

            // If the bound is Object, it should only be included when the nullability isn't implied
            // by the configuration. If both kotlinStyleNulls and annotations are false, no
            // nullability information is included anyway.
            if (!configuration.kotlinStyleNulls && !configuration.annotations) return false

            // When nullability information is included, excluded bounds imply non-null when
            // kotlinStyleNulls is true and platform when it is false.
            val nullability = extendsBound.modifiers.nullability
            if (configuration.kotlinStyleNulls && nullability == TypeNullability.NONNULL)
                return false
            if (!configuration.kotlinStyleNulls && nullability == TypeNullability.PLATFORM)
                return false
            return true
        }

        private fun StringBuilder.appendAnnotations(
            modifiers: TypeModifiers,
            configuration: TypeStringConfiguration,
            leadingSpace: Boolean = false,
            trailingSpace: Boolean = true
        ) {
            val annotations =
                modifiers.annotations.filter { annotation ->
                    // If Kotlin-style nulls are printed, nullness annotations shouldn't be.
                    !(configuration.kotlinStyleNulls && annotation.isNullnessAnnotation())
                }
            if (annotations.isEmpty()) return

            if (leadingSpace) {
                append(' ')
            }
            val annotationFormatter = configuration.annotationFormatter
            annotations.forEachIndexed { index, annotation ->
                annotationFormatter.appendFormatAnnotation(this, annotation, AnnotationPurpose.TYPE)
                if (index != annotations.size - 1) {
                    append(' ')
                }
            }
            if (trailingSpace) {
                append(' ')
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
            val base =
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

            // If class name contains $, it's not an ambiguous nested class name.
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

/**
 * Configuration options for how to represent a type as a string.
 *
 * @param annotations Whether to include annotations on the type.
 * @param annotationFormatter Responsible for formatting type annotations.
 * @param eraseGenerics If `true` then type parameters are ignored and type variables are replaced
 *   with the upper bound of the type parameter.
 * @param kotlinStyleNulls Whether to represent nullability with Kotlin-style suffixes: `?` for
 *   nullable, no suffix for non-null, and `!` for platform nullability. For example, the Java type
 *   `@Nullable List<String>` would be represented as `List<String!>?`.
 * @param nestedClassSeparator The character that is used to separate a nested class from its
 *   containing class.
 * @param spaceBetweenTypeArguments Whether to include a space between type arguments of a generic
 *   type.
 * @param stripJavaLangPrefix Controls how `java.lang.` prefixes are removed from the types.
 * @param treatVarargsAsArray If `false` then a varargs type will use `...` to indicate that it is a
 *   varargs type, otherwise it will use `[]` like a normal array.
 */
data class TypeStringConfiguration(
    val annotations: Boolean = false,
    val annotationFormatter: AnnotationFormatter = DEFAULT_ANNOTATION_FORMATTER,
    val eraseGenerics: Boolean = false,
    val kotlinStyleNulls: Boolean = false,
    val nestedClassSeparator: Char = '.',
    val spaceBetweenTypeArguments: Boolean = false,
    val stripJavaLangPrefix: StripJavaLangPrefix = StripJavaLangPrefix.NEVER,
    val treatVarargsAsArray: Boolean = false,
) {
    /**
     * Check to see if this matches [DEFAULT].
     *
     * This is computed lazily to avoid the comparison against [DEFAULT] being done while creating
     * the instance to assign to [DEFAULT] at which point [DEFAULT] would be `null`.
     */
    val isDefault by lazy(LazyThreadSafetyMode.NONE) { this == DEFAULT }

    companion object {
        /**
         * The default [AnnotationFormatter] used by [TypeStringConfiguration].
         *
         * Must be initialized before [DEFAULT] to avoid a [NullPointerException].
         */
        private val DEFAULT_ANNOTATION_FORMATTER = AnnotationFormatter.legacyAnnotationFormatter()

        /** The default [TypeStringConfiguration]. */
        val DEFAULT: TypeStringConfiguration = TypeStringConfiguration()

        /** A [TypeStringConfiguration] like [DEFAULT], but with Kotlin-style null suffixes. */
        val DEFAULT_KOTLIN_NULLS = TypeStringConfiguration(kotlinStyleNulls = true)
    }
}

/**
 * The type for [ClassTypeItem.arguments].
 *
 * See https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-TypeArgument.
 */
sealed interface TypeArgumentTypeItem : TypeItem {
    /** Override to specialize the return type. */
    override fun convertType(typeParameterBindings: TypeParameterBindings): TypeArgumentTypeItem

    /** Override to specialize the return type. */
    override fun substitute(modifiers: TypeModifiers): TypeArgumentTypeItem

    /** Override to specialize the return type. */
    override fun transform(transformer: TypeTransformer): TypeArgumentTypeItem
}

/**
 * The type for a reference.
 *
 * See https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-ReferenceType.
 */
sealed interface ReferenceTypeItem : TypeItem, TypeArgumentTypeItem {
    /** Override to specialize the return type. */
    override fun substitute(modifiers: TypeModifiers): ReferenceTypeItem

    /** Override to specialize the return type. */
    override fun transform(transformer: TypeTransformer): ReferenceTypeItem
}

/**
 * The "union" of [ClassTypeItem] and [VariableTypeItem].
 *
 * Provided as this is convenient for some code to handle these together.
 */
sealed interface ClassOrVariableTypeItem : TypeItem, ReferenceTypeItem {
    /**
     * Override to specialize the return type.
     *
     * Use [asErasedClass] instead of calling [ClassTypeItem.resolveClass] on the result of this as
     * [asErasedClass] is more efficient.
     */
    override fun asErasedType(): ClassTypeItem

    /** Override to specialize the return type. */
    override fun transform(transformer: TypeTransformer): ExceptionTypeItem

    /**
     * Get the erased [ClassItem], if any.
     *
     * The erased [ClassItem] is the one which would be used by Java at runtime after the generic
     * types have been erased.
     */
    fun asErasedClass(classResolver: ClassResolver): ClassItem?

    /**
     * The best guess of the full name, i.e. the qualified class name without the package but
     * including the outer class names.
     *
     * This is not something that can be accurately determined solely by examining the reference or
     * even the import as there is no distinction made between a package name and a class name. Java
     * naming conventions do say that package names should start with a lower case letter and class
     * names should start with an upper case letter, but they are not enforced so cannot be fully
     * relied upon.
     *
     * It is possible that in some contexts a model could provide a better full name than guessing
     * from the fully qualified name, e.g. a reference within the same package, however that is not
     * something that will be supported by all models and so attempting to use that could lead to
     * subtle model differences that could break users of the models.
     *
     * The only way to fully determine the full name is to resolve the class and extract it from
     * there but this avoids resolving a class as it can be expensive. Instead, this just makes the
     * best guess assuming normal Java conventions.
     */
    @Deprecated(
        "Do not use as full name is only ever a best guess based on naming conventions; use the full type string instead",
        ReplaceWith("toTypeString()")
    )
    fun fullName(): String = bestGuessAtFullName(toTypeString())

    companion object {
        /**
         * A partial ordering over [ClassOrVariableTypeItem] comparing [ClassOrVariableTypeItem]
         * full names.
         */
        val fullNameComparator: Comparator<ClassOrVariableTypeItem> =
            Comparator.comparing { @Suppress("DEPRECATION") it.fullName() }
    }
}

/**
 * The "union" type of [TypeParameterItem]'s type bounds.
 *
 * See https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-TypeBound
 *
 * At the moment this is identical to [ClassOrVariableTypeItem] but it is kept as that may not
 * always be the case.
 */
sealed interface BoundsTypeItem : ClassOrVariableTypeItem

/**
 * The "union" type of [MethodItem.throwsTypes]'s.
 *
 * See https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-ExceptionType.
 *
 * At the moment this is identical to [ClassOrVariableTypeItem] but it is kept as that may not
 * always be the case.
 */
sealed interface ExceptionTypeItem : ClassOrVariableTypeItem

/** Represents a primitive type, like int or boolean. */
interface PrimitiveTypeItem : TypeItem {
    /** The kind of [Primitive] this type is. */
    val kind: Primitive

    /** The possible kinds of primitives. */
    enum class Primitive(
        val primitiveName: String,
        val kotlinName: String,
        val defaultValue: Any?,
        val defaultValueString: String,
        val wrapperClass: Class<*>,
    ) {
        BOOLEAN(
            primitiveName = "boolean",
            kotlinName = "Boolean",
            defaultValue = false,
            defaultValueString = "false",
            wrapperClass =
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Boolean::class.java,
        ),
        BYTE(
            primitiveName = "byte",
            kotlinName = "Byte",
            defaultValue = 0.toByte(),
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Byte::class.java,
        ),
        CHAR(
            primitiveName = "char",
            kotlinName = "Char",
            defaultValue = 0.toChar(),
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") Character::class.java,
        ),
        DOUBLE(
            primitiveName = "double",
            kotlinName = "Double",
            defaultValue = 0.0,
            defaultValueString = "0",
            wrapperClass =
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Double::class.java,
        ),
        FLOAT(
            primitiveName = "float",
            kotlinName = "Float",
            defaultValue = 0F,
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Float::class.java,
        ),
        INT(
            primitiveName = "int",
            kotlinName = "Int",
            defaultValue = 0,
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") Integer::class.java,
        ),
        LONG(
            primitiveName = "long",
            kotlinName = "Long",
            defaultValue = 0L,
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Long::class.java,
        ),
        SHORT(
            primitiveName = "short",
            kotlinName = "Short",
            defaultValue = 0.toShort(),
            defaultValueString = "0",
            wrapperClass = @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") java.lang.Short::class.java,
        ),
        VOID(
            primitiveName = "void",
            // Unit is not exactly the same as void, but it is what is used in Kotlin when a method
            // has no return, like void in Java.
            kotlinName = "Unit",
            defaultValue = null,
            defaultValueString = "null",
            wrapperClass = Void::class.java,
        ),
        ;

        /**
         * The name of the Kotlin function that will convert a [Number] to an instance of this type.
         *
         * This is `null` for non-numeric [Primitive]s.
         */
        val kotlinNumericConversionFunction =
            if (Number::class.java.isAssignableFrom(wrapperClass)) "to$kotlinName" else null

        companion object {
            /** Map from [Primitive.wrapperClass]'s name to [Primitive]. */
            private val wrapperClassNameToKind =
                Primitive.entries.associateBy { it.wrapperClass.name }

            /**
             * Get the [Primitive] associated with [wrapperClassName], returning `null`, if it could
             * not be found.
             */
            fun forWrapperClassName(wrapperClassName: String) =
                wrapperClassNameToKind[wrapperClassName]

            /** Map from [Primitive.kotlinNumericConversionFunction]'s name to [Primitive]. */
            private val kotlinNumericConversionFunctionNameToKind =
                Primitive.entries
                    .filter { it.kotlinNumericConversionFunction != null }
                    .associateBy { it.kotlinNumericConversionFunction }

            /**
             * Get the [Primitive] associated with the Kotlin numeric conversion function called
             * [name], returning `null`, if it could not be found.
             */
            fun forKotlinNumericConversionFunctionName(name: String) =
                kotlinNumericConversionFunctionNameToKind[name]
        }
    }

    override fun defaultValue(): Any? = kind.defaultValue

    override fun defaultValueString(): String = kind.defaultValueString

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>) {
        visitor.visit(this, other)
    }

    /** Erasing a [PrimitiveTypeItem] requires removing annotations. */
    override fun asErasedType() = substitute(modifiers.withoutAnnotations())

    override fun substitute(modifiers: TypeModifiers): PrimitiveTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): PrimitiveTypeItem {
        // Primitive type is never affected by a type mapping so always return this.
        return this
    }

    override fun transform(transformer: TypeTransformer): PrimitiveTypeItem {
        return transformer.transform(this)
    }

    override fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean {
        return (other as? PrimitiveTypeItem)?.kind == kind &&
            (!includeNullability || modifiers.nullability == other.modifiers.nullability)
    }

    override fun hashCodeForType(): Int = kind.hashCode()
}

/** Represents an array type, including vararg types. */
interface ArrayTypeItem : TypeItem, ReferenceTypeItem {
    /** The array's inner type (which for multidimensional arrays is another array type). */
    val componentType: TypeItem

    /** Whether this array type represents a varargs parameter. */
    val isVarargs: Boolean

    /** Get the innermost component type of this [ArrayTypeItem]. */
    fun innermostComponentType(): TypeItem {
        var type = componentType
        while (type is ArrayTypeItem) {
            type = type.componentType
        }
        return type
    }

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>) {
        visitor.visit(this, other)
    }

    /**
     * Erasing an [ArrayTypeItem] requires removing annotations, erasing its component type and
     * dropping the [isVarargs] if set.
     */
    override fun asErasedType() =
        substitute(modifiers.withoutAnnotations(), componentType.asErasedType(), isVarargs = false)

    override fun substitute(modifiers: TypeModifiers): ArrayTypeItem =
        substitute(modifiers, componentType)

    /**
     * Return an [ArrayTypeItem] instance identical to this one except its [TypeItem.modifiers] and
     * [ArrayTypeItem.componentType] properties are the same as the [modifiers] and [componentType]
     * parameters respectively.
     *
     * If the parameters are the same as this instance's properties then it will just return this
     * instance, otherwise it will return a new instance.
     */
    fun substitute(
        modifiers: TypeModifiers = this.modifiers,
        componentType: TypeItem = this.componentType,
        isVarargs: Boolean = this.isVarargs,
    ): ArrayTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): ArrayTypeItem {
        return substitute(
            componentType = componentType.convertType(typeParameterBindings),
        )
    }

    override fun transform(transformer: TypeTransformer): ArrayTypeItem {
        return transformer.transform(this)
    }

    override fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean {
        if (other !is ArrayTypeItem) return false
        return isVarargs == other.isVarargs &&
            (!includeNullability || modifiers.nullability == other.modifiers.nullability) &&
            componentType.equalToType(other.componentType, includeNullability)
    }

    override fun hashCodeForType(): Int = Objects.hash(isVarargs, componentType)
}

/** Represents a class type. */
interface ClassTypeItem : TypeItem, BoundsTypeItem, ReferenceTypeItem, ExceptionTypeItem {
    /** The qualified name of this class, e.g. "java.lang.String". */
    val qualifiedName: String

    /**
     * The class type's arguments, empty if it has none.
     *
     * i.e. The specific types that this class type assigns to each of the referenced [ClassItem]'s
     * type parameters.
     */
    val arguments: List<TypeArgumentTypeItem>

    /** The outer class type of this class, if it is a nested type. */
    val outerClassType: ClassTypeItem?

    /**
     * The name of the class, e.g. "String" for "java.lang.String" and "Inner" for
     * "test.pkg.Outer.Inner".
     */
    val className: String

    /**
     * Get the class name prefix, i.e. the part before [className] and after which type use
     * annotations, if any will appear.
     *
     * e.g. for `java.lang.String`, [className] is `String` and the prefix is `java.lang.`. For
     * `java.util.Map.Entry` [className] is `Entry` and the prefix is `java.util.Map.`.
     *
     * This is the value such that [classNamePrefix] + [className] == [qualifiedName].
     */
    val classNamePrefix: String
        get() {
            val classNamePrefixEnd = qualifiedName.length - className.length
            return qualifiedName.substring(0, classNamePrefixEnd)
        }

    /** Resolve this to a [ClassItem], if possible. */
    fun resolveClass(classResolver: ClassResolver) = classResolver.resolveClass(qualifiedName)

    override fun asErasedClass(classResolver: ClassResolver) = resolveClass(classResolver)

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>) {
        visitor.visit(this, other)
    }

    /**
     * Check to see whether this type has any type arguments.
     *
     * It will return `true` for say `List<T>`, but `false` for `String`.
     */
    fun hasTypeArguments() = arguments.isNotEmpty()

    override fun isString(): Boolean = qualifiedName == JAVA_LANG_STRING

    override fun isJavaLangObject(): Boolean = qualifiedName == JAVA_LANG_OBJECT

    /**
     * Erasing a [ClassTypeItem] requires removing annotations and argument types and erasing its
     * outer class type.
     */
    override fun asErasedType(): ClassTypeItem =
        substitute(modifiers.withoutAnnotations(), outerClassType?.asErasedType(), emptyList())

    override fun substitute(modifiers: TypeModifiers): ClassTypeItem =
        substitute(modifiers, outerClassType, arguments)

    /**
     * Return a [ClassTypeItem] instance identical to this one except its [TypeItem.modifiers],
     * [ClassTypeItem.outerClassType] and [ClassTypeItem.arguments] properties are the same as the
     * [modifiers], [outerClassType] and [arguments] parameters respectively.
     *
     * If the parameters are the same as this instance's properties then it will just return this
     * instance, otherwise it will return a new instance.
     */
    fun substitute(
        modifiers: TypeModifiers = this.modifiers,
        outerClassType: ClassTypeItem? = this.outerClassType,
        arguments: List<TypeArgumentTypeItem> = this.arguments,
    ): ClassTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): ClassTypeItem {
        return substitute(
            outerClassType = outerClassType?.convertType(typeParameterBindings),
            arguments = arguments.mapIfNotSame { it.convertType(typeParameterBindings) },
        )
    }

    override fun transform(transformer: TypeTransformer): ClassTypeItem {
        return transformer.transform(this)
    }

    override fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean {
        if (other !is ClassTypeItem) return false
        return qualifiedName == other.qualifiedName &&
            arguments.size == other.arguments.size &&
            (!includeNullability || modifiers.nullability == other.modifiers.nullability) &&
            arguments.zip(other.arguments).all { (p1, p2) ->
                p1.equalToType(p2, includeNullability)
            } &&
            ((outerClassType == null && other.outerClassType == null) ||
                outerClassType?.equalToType(other.outerClassType, includeNullability) == true)
    }

    override fun hashCodeForType(): Int = Objects.hash(qualifiedName, outerClassType, arguments)

    override fun isSamCompatibleOrKotlinLambda(classResolver: ClassResolver): Boolean {
        // Check if this is a lambda type that was not created as a LambdaTypeItem (e.g. from the
        // text model b/437086600)
        if (classNamePrefix == "kotlin.jvm.functions." && className.startsWith("Function"))
            return true

        // Check the type to see if it is defined in Kotlin or not.
        // Interfaces defined in Kotlin do not support SAM conversion, but `fun` interfaces do.
        // This is a best-effort check, since external dependencies (bytecode) won't appear to
        // be Kotlin for psi, and won't have a `fun` modifier visible. To resolve this, we could
        // parse the kotlin.metadata annotation on the bytecode declaration, but in reality the
        // amount of Java methods with a Kotlin interface with a single abstract method from an
        // external dependency should be minimal. When using signature files, it also won't be clear
        // whether a non-fun interface was defined in Java or Kotlin.
        val cls = resolveClass(classResolver) ?: return false
        if (!cls.isInterface()) return false
        // The functional modifier will only be present on Kotlin source interfaces
        if (cls.modifiers.isFunctional()) return true
        // For Java or unknown source language, check if there is a single abstract method
        return cls.sourceLanguage != SourceLanguage.KOTLIN &&
            cls.methods().singleOrNull { it.modifiers.isAbstract() } != null
    }

    companion object {
        /** Computes the simple name of a class from a qualified class name. */
        fun computeClassName(qualifiedName: String) = qualifiedName.extractSimpleName()
    }
}

/**
 * Represents a kotlin lambda type.
 *
 * This extends [ClassTypeItem] out of necessity because that is how lambdas have been represented
 * in Metalava up until this was created and so until such time as all the code that consumes this
 * has been updated to handle lambdas specifically it will need to remain a [ClassTypeItem].
 */
interface LambdaTypeItem : ClassTypeItem {
    /** True if the lambda is a suspend function, false otherwise. */
    val isSuspend: Boolean

    /** The type of the optional receiver. */
    val receiverType: TypeItem?

    /** The parameter types. */
    val parameterTypes: List<TypeItem>

    /** The return type. */
    val returnType: TypeItem

    override fun substitute(modifiers: TypeModifiers): LambdaTypeItem =
        substitute(modifiers, outerClassType, arguments)

    /** Override to specialize the return type. */
    override fun substitute(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): LambdaTypeItem

    override fun transform(transformer: TypeTransformer): LambdaTypeItem {
        return transformer.transform(this)
    }

    override fun isSamCompatibleOrKotlinLambda(classResolver: ClassResolver): Boolean {
        // This is a Kotlin lambda type
        return true
    }
}

/** Represents a type variable type. */
interface VariableTypeItem : TypeItem, BoundsTypeItem, ReferenceTypeItem, ExceptionTypeItem {
    /** The name of the type variable */
    val name: String

    /** The corresponding type parameter for this type variable. */
    val asTypeParameter: TypeParameterItem

    /** Erasing a [VariableTypeItem] requires using the [TypeParameterItem]'s first bound. */
    override fun asErasedType() = asTypeParameter.asErasedType()

    override fun asErasedClass(classResolver: ClassResolver) =
        asTypeParameter.asErasedType().asErasedClass(classResolver)

    override fun description() =
        "$name (extends ${this.asTypeParameter.asErasedType().description()})}"

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>) {
        visitor.visit(this, other)
    }

    override fun substitute(modifiers: TypeModifiers): VariableTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): TypeArgumentTypeItem {
        val nullability = modifiers.nullability
        return typeParameterBindings[asTypeParameter]?.let { replacement ->
            val replacementNullability =
                when {
                    // If this use of the type parameter has a known nullability then it overrides
                    // the nullability of the substituted type.
                    // e.g. the result of replacing `T` with `String?` in `T & Any` (which makes T
                    // non-null) is `String? & Any` which simplifies to `String`.
                    //
                    // The resulty of replacing `T` with `String in `T?` is `String?`.
                    nullability.known -> nullability

                    // Otherwise, use the replacement nullability.
                    else -> null
                }

            if (replacementNullability == null) {
                replacement
            } else {
                replacement.substitute(replacementNullability) as TypeArgumentTypeItem
            }
        }
            ?:
            // The type parameter binding does not contain a replacement for this variable so use
            // this as is.
            this
    }

    override fun transform(transformer: TypeTransformer): VariableTypeItem {
        return transformer.transform(this)
    }

    override fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean {
        return (other as? VariableTypeItem)?.name == name &&
            (!includeNullability || modifiers.nullability == other.modifiers.nullability)
    }

    override fun hashCodeForType(): Int = name.hashCode()

    override fun isSamCompatibleOrKotlinLambda(classResolver: ClassResolver): Boolean {
        // A variable type can be used with trailing lambda syntax if its bound is a Kotlin
        // functional type, but not if the bound is a different SAM compatible type.
        return asTypeParameter.asErasedType().let {
            it is LambdaTypeItem ||
                // Check if this is a lambda type that was not created as a LambdaTypeItem (e.g.
                // from the text model b/437086600)
                it.classNamePrefix == "kotlin.jvm.functions." && it.className.startsWith("Function")
        }
    }
}

/**
 * Represents a wildcard type, like `?`, `? extends String`, and `? super String` in Java, or `*`,
 * `out String`, and `in String` in Kotlin.
 */
interface WildcardTypeItem : TypeItem, TypeArgumentTypeItem {
    /** The type this wildcard must extend. If null, the extends bound is implicitly `Object`. */
    val extendsBound: ReferenceTypeItem?

    /** The type this wildcard must be a super class of. */
    val superBound: ReferenceTypeItem?

    override fun accept(visitor: TypeVisitor) {
        visitor.visit(this)
    }

    override fun accept(visitor: MultipleTypeVisitor, other: List<TypeItem>) {
        visitor.visit(this, other)
    }

    /**
     * Erasing a [WildcardTypeItem] does not make much sense.
     *
     * These can only appear in a generic class' parameters and so will be removed when that class
     * is erased. It might be helpful to have this be erased to either [extendsBound] if present or
     * `java.lang.Object` but there is no way to create a valid one with a [ClassResolver] and that
     * is not available to implementations of this.
     */
    override fun asErasedType() = error("Erasing $this makes little sense")

    override fun substitute(modifiers: TypeModifiers): WildcardTypeItem =
        substitute(modifiers, extendsBound, superBound)

    /**
     * Return a [WildcardTypeItem] instance identical to this one except its [TypeItem.modifiers],
     * [WildcardTypeItem.extendsBound] and [WildcardTypeItem.superBound] properties are the same as
     * the [modifiers], [extendsBound] and [superBound] parameters respectively.
     *
     * If the parameters are the same as this instance's properties then it will just return this
     * instance, otherwise it will return a new instance.
     */
    fun substitute(
        modifiers: TypeModifiers = this.modifiers,
        extendsBound: ReferenceTypeItem? = this.extendsBound,
        superBound: ReferenceTypeItem? = this.superBound,
    ): WildcardTypeItem

    override fun convertType(typeParameterBindings: TypeParameterBindings): WildcardTypeItem {
        return substitute(
            modifiers,
            // The converted bounds should always end up as ReferenceTypeItems.
            // When convertType is used for superclasses, although a `ClassTypeItem`'s arguments can
            // be `WildcardTypeItem`s as well as `ReferenceTypeItem`s, a `ClassTypeItem` used in an
            // extends or implements list cannot have a `WildcardTypeItem` as an argument so this
            // cast will always succeed.
            // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-Superclass
            // When convertType is used for typealiases, it is possible for a `WildcardTypeItem` to
            // be used as an argument. However, that should never end up as the bounds for another
            // `WildcardTypeItem`.
            extendsBound?.convertType(typeParameterBindings) as? ReferenceTypeItem,
            superBound?.convertType(typeParameterBindings) as? ReferenceTypeItem,
        )
    }

    // Any [TypeArgumentTypeItem] can be used in any context where a [WildcardTypeItem] is valid.
    override fun transform(transformer: TypeTransformer): TypeArgumentTypeItem {
        return transformer.transform(this)
    }

    override fun equalToType(other: TypeItem?, includeNullability: Boolean): Boolean {
        if (other !is WildcardTypeItem) return false
        return (!includeNullability || modifiers.nullability == other.modifiers.nullability) &&
            extendsBound?.equalToType(other.extendsBound, includeNullability) != false &&
            superBound?.equalToType(other.superBound, includeNullability) != false
    }

    override fun hashCodeForType(): Int = Objects.hash(extendsBound, superBound)
}

/**
 * Create a [TypeTransformer] that will remove any type annotations for which [filter] returns false
 * when called against the [AnnotationItem]'s [ClassItem] return by [AnnotationItem.resolve]. If
 * that returns `null` then the [AnnotationItem] will be kept.
 */
fun typeUseAnnotationFilter(filter: FilterPredicate): TypeTransformer =
    object : BaseTypeTransformer() {
        override fun transform(modifiers: TypeModifiers): TypeModifiers {
            if (modifiers.annotations.isEmpty()) return modifiers
            return modifiers.substitute(
                annotations =
                    modifiers.annotations.filter { annotationItem ->
                        // If the annotation cannot be resolved then keep it.
                        val annotationClass = annotationItem.resolve() ?: return@filter true
                        filter.test(annotationClass)
                    }
            )
        }
    }

/**
 * A [TypeTransformer] which replaces [WildcardTypeItem]s with their bounds. If neither a super nor
 * extends bound is defined for a wildcard, it leaves the unbounded wildcard in place.
 */
private object WildcardFlatteningTransformer : BaseTypeTransformer() {
    override fun transform(typeItem: WildcardTypeItem): TypeArgumentTypeItem {
        val bound = typeItem.superBound ?: typeItem.extendsBound
        return bound?.transform(this) ?: typeItem
    }
}

/**
 * Checks if [type1] and [type2] are equal if any wildcards present in the type are replaced with
 * their bounds.
 *
 * This is meant for comparing Kotlin types generated through PSI and the Kotlin analysis API, which
 * often differ in whether wildcards are present, in cases where it does not make sense to simply
 * compared erased types.
 *
 * For instance, `List<String>` and `List<? extends String>` would be considered equal, as would
 * `List<? super String>`. These types are not equal, but considering them equal enables comparing
 * types generated from UAST and the analysis API.
 */
fun equalWithFlattenedWildcards(type1: TypeItem, type2: TypeItem): Boolean {
    val transformedType1 = type1.transform(WildcardFlatteningTransformer)
    val transformedType2 = type2.transform(WildcardFlatteningTransformer)
    return transformedType1 == transformedType2
}

/**
 * Map the items in this list to a new list if [transform] returns at least one item which is not
 * the same instance as its input, otherwise return this.
 */
fun <T> List<T>.mapIfNotSame(transform: (T) -> T): List<T> {
    if (isEmpty()) return this
    val newList = map(transform)
    val i1 = iterator()
    val i2 = newList.iterator()
    while (i1.hasNext() && i2.hasNext()) {
        val t1 = i1.next()
        val t2 = i2.next()
        if (t1 !== t2) return newList
    }
    return this
}

/**
 * Attempt to get the full name from the qualified name.
 *
 * The full name is the qualified name without the package including any outer class names.
 *
 * It relies on the convention that packages start with a lower case letter and classes start with
 * an upper case letter.
 */
fun bestGuessAtFullName(qualifiedName: String): String {
    val length = qualifiedName.length
    var prev: Char? = null
    var lastDotIndex = -1
    for (i in 0..<length) {
        val c = qualifiedName[i]
        if (prev == null || prev == '.') {
            if (c.isUpperCase()) {
                return qualifiedName.substring(i)
            }
        }
        if (c == '.') {
            lastDotIndex = i
        }
        prev = c
    }

    return if (lastDotIndex == -1) {
        qualifiedName
    } else {
        qualifiedName.substring(lastDotIndex + 1)
    }
}
