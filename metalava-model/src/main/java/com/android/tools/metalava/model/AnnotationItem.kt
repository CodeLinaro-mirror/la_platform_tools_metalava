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

import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.model.api.flags.ApiFlag
import com.android.tools.metalava.model.api.flags.ApiFlags
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.ArrayValue
import com.android.tools.metalava.model.value.LegacyValueFormatter.Companion.ANNOTATION_SOURCE_FORMATTER
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueLanguage
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.model.value.ValueStringConfiguration
import com.android.tools.metalava.reporter.FileLocation
import java.lang.StringBuilder
import kotlin.reflect.KClass

fun isNullnessAnnotation(qualifiedName: String): Boolean =
    isNullableAnnotation(qualifiedName) || isNonNullAnnotation(qualifiedName)

fun isNullableAnnotation(qualifiedName: String): Boolean {
    return qualifiedName == "Nullable" ||
        qualifiedName.endsWith(".RecentlyNullable") ||
        qualifiedName.endsWith(".Nullable") ||
        qualifiedName.endsWith(".NullableType")
}

fun isNonNullAnnotation(qualifiedName: String): Boolean {
    return qualifiedName == "NonNull" ||
        qualifiedName.endsWith(".RecentlyNonNull") ||
        qualifiedName.endsWith(".NonNull") ||
        qualifiedName.endsWith(".NotNull") ||
        qualifiedName.endsWith(".Nonnull")
}

fun isJvmSyntheticAnnotation(qualifiedName: String): Boolean {
    return qualifiedName == "kotlin.jvm.JvmSynthetic"
}

sealed interface AnnotationItem {
    val annotationContext: AnnotationContext

    /**
     * The location of this annotation with the source file.
     *
     * Will be [FileLocation.UNKNOWN] if the location cannot be determined, e.g. because it is from
     * a `.class` file.
     */
    val fileLocation: FileLocation

    /** Fully qualified name of the annotation */
    val qualifiedName: String

    /**
     * Determines the effect that this will have on whether an item annotated with this annotation
     * will be shown as part of the API or not.
     */
    val showability: Showability

    /**
     * The [ApiFlag] referenced by this [AnnotationItem].
     *
     * This will be `null` if no [ApiFlags] have been provided or this [AnnotationItem]'s type is
     * not [ANDROID_FLAGGED_API]. Otherwise, it will be one of the instances of [ApiFlag], e.g.
     * [ApiFlag.REVERT_FLAGGED_API].
     */
    val apiFlag: ApiFlag?

    /**
     * Append the string representation of this annotation to the [builder] according to
     * [configuration] and [annotationIsValue].
     *
     * If [annotationIsValue] is `true` then this is being written out as a value, i.e. either
     * nested within another [AnnotationItem] or as [MethodItem.defaultValue]. In that case
     * [ValueStringConfiguration.valueLanguage] affects the representation of the annotation as
     * follows:
     * * Kotlin does not use a leading `@` for annotation values but Java does.
     * * Parentheses are optional everywhere for an annotation with an empty attributes list except
     *   when used as a Kotlin annotation value where they are required.
     *
     * Otherwise, if [annotationIsValue] is `false` then this uses the [ValueLanguage.JAVA]
     * representation as that is the same as Kotlin.
     */
    fun appendAnnotationStringTo(
        builder: StringBuilder,
        configuration: ValueStringConfiguration,
        annotationIsValue: Boolean,
    ) {
        // While top level annotations use the Java syntax for Kotlin and Java, nested annotations
        // use different syntax for each one.
        val language = if (annotationIsValue) configuration.valueLanguage else ValueLanguage.JAVA
        builder.append(language.annotationClassPrefix)

        // Get the annotation class name.
        val formatName = configuration.annotationQualifiedNameGetter(this)
        builder.append(formatName)

        if (language.annotationAttributesListRequiresParentheses || attributes.isNotEmpty()) {
            builder.append("(")

            val nameValueSeparator = configuration.annotationAttributeNameValueSeparator.text

            val singleAttribute = attributes.singleOrNull()
            if (singleAttribute == null) {
                var separator = ""

                // Get the attributes in the correct order.
                val orderedAttributes =
                    if (configuration.sortAnnotationAttributes) attributes.sortedBy { it.name }
                    else attributes

                for (attribute in orderedAttributes) {
                    builder.append(separator)
                    builder.append(attribute.name).append(nameValueSeparator)
                    configuration.appendNestedValueTo(builder, attribute.value)
                    separator = ", "
                }
            } else {
                // A single attribute whose attribute name is "value" can just use the value.
                val name = singleAttribute.name
                if (name != ANNOTATION_ATTR_VALUE) {
                    builder.append(name).append(nameValueSeparator)
                }
                configuration.appendNestedValueTo(builder, singleAttribute.value)
            }

            builder.append(")")
        }
    }

    /**
     * Generates source code for this annotation (using fully qualified names).
     *
     * @param target the [AnnotationTarget] for which this is being generated.
     */
    fun toSource(
        target: AnnotationTarget = AnnotationTarget.SIGNATURE_FILE,
        context: Item? = null,
    ): String

    /** The applicable targets for this annotation */
    val targets: Set<AnnotationTarget>

    /** Attributes of the annotation; may be empty. */
    val attributes: List<AnnotationAttribute>

    /**
     * The [TypeNullability] associated with this or `null` if this is not a nullability annotation.
     */
    val typeNullability: TypeNullability?

    /** True if this annotation represents @Nullable or @NonNull (or some synonymous annotation) */
    fun isNullnessAnnotation(): Boolean

    /** True if this annotation represents @Nullable (or some synonymous annotation) */
    fun isNullable(): Boolean

    /** True if this annotation represents @NonNull (or some synonymous annotation) */
    fun isNonNull(): Boolean

    /** True if this annotation represents @Retention (either the Java or Kotlin version) */
    fun isRetention(): Boolean = isRetention(qualifiedName)

    /** True if this annotation represents @JvmSynthetic */
    fun isJvmSynthetic(): Boolean {
        return isJvmSyntheticAnnotation(qualifiedName)
    }

    /** True if this annotation represents @IntDef, @LongDef or @StringDef */
    fun isTypeDefAnnotation(): Boolean {
        val name = qualifiedName
        if (!(name.endsWith("Def"))) {
            return false
        }
        return (ANDROIDX_INT_DEF == name ||
            ANDROIDX_STRING_DEF == name ||
            ANDROIDX_LONG_DEF == name ||
            ANDROID_INT_DEF == name ||
            ANDROID_STRING_DEF == name ||
            ANDROID_LONG_DEF == name)
    }

    /** Returns the given named attribute if specified */
    fun findAttribute(name: String) = attributes.firstOrNull { it.name == name }

    /** Find the class declaration for the given annotation */
    fun resolve(): ClassItem?

    /** If this annotation has a typedef annotation associated with it, return it */
    fun findTypedefAnnotation(): AnnotationItem?

    /**
     * Returns true iff the annotation is a show annotation.
     *
     * If `true` then an item annotated with this annotation (and any contents) will be added to the
     * API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isShowAnnotation(): Boolean

    /**
     * Returns true iff this annotation is a show for stubs purposes annotation.
     *
     * If `true` then an item annotated with this annotation (and any contents) which are not
     * annotated with another [isShowAnnotation] will be added to the stubs but not the API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isShowForStubPurposes(): Boolean

    /**
     * Returns true iff this annotation is a hide annotation.
     *
     * Hide annotations can either be explicitly specified when creating the [Codebase] or they can
     * be any annotation that is annotated with a hide meta-annotation (see [isHideMetaAnnotation]).
     *
     * If `true` then an item annotated with this annotation (and any contents) will be excluded
     * from the API.
     *
     * e.g. if a class is annotated with this then it will also apply (unless overridden by a closer
     * annotation) to all its contents like nested classes, methods, fields, constructors,
     * properties, etc.
     */
    fun isHideAnnotation(): Boolean

    fun isSuppressCompatibilityAnnotation(): Boolean

    /**
     * Returns true iff this annotation is a showability annotation, i.e. one that will affect
     * [showability].
     */
    fun isShowabilityAnnotation(): Boolean

    /** Returns the retention of this annotation */
    val retention: AnnotationRetention
        get() {
            val cls = resolve()
            if (cls != null) {
                if (cls.isAnnotationType()) {
                    return cls.annotationClass.retention
                }
            }

            return AnnotationRetention.getDefault()
        }

    /** Take a snapshot of this [AnnotationItem] suitable for use in [Codebase]. */
    fun snapshot(targetCodebase: Codebase): AnnotationItem

    companion object {
        /**
         * The simple name of an annotation, which is the annotation name (not qualified name)
         * prefixed by @
         */
        fun simpleName(item: AnnotationItem): String {
            return item.qualifiedName.let { "@${it.substringAfterLast('.')}" }
        }

        /**
         * Given a "full" annotation name, shortens it by removing redundant package names. This is
         * intended to be used to reduce clutter in signature files.
         *
         * For example, this method will convert `@androidx.annotation.Nullable` to just
         * `@Nullable`, and `@androidx.annotation.IntRange(from=20)` to `IntRange(from=20)`.
         */
        fun shortenAnnotation(source: String): String {
            return when {
                source == "@java.lang.Deprecated" -> "@Deprecated"
                source.startsWith(ANDROID_ANNOTATION_PREFIX, 1) -> {
                    "@" + source.substring(ANDROID_ANNOTATION_PREFIX.length + 1)
                }
                source.startsWith(ANDROIDX_ANNOTATION_PREFIX, 1) -> {
                    "@" + source.substring(ANDROIDX_ANNOTATION_PREFIX.length + 1)
                }
                else -> source
            }
        }

        /**
         * Reverses the [shortenAnnotation] method. Intended for use when reading in signature files
         * that contain shortened type references.
         */
        fun unshortenAnnotation(source: String): String {
            return when {
                source == "@Deprecated" -> "@java.lang.Deprecated"
                // The first 4 annotations are in the android.annotation. package, not
                // androidx.annotation
                // Nullability annotations are written as @NonNull and @Nullable in API text files,
                // and these should be linked no android.annotation package when generating stubs.
                source.startsWith("@SystemService") ||
                    source.startsWith("@TargetApi") ||
                    source.startsWith("@SuppressLint") ||
                    source.startsWith("@FlaggedApi") ||
                    source.startsWith("@Nullable") ||
                    source.startsWith("@NonNull") -> "@android.annotation." + source.substring(1)
                // If the first character of the name (after "@") is lower-case, then
                // assume it's a package name, so no need to shorten it.
                source.startsWith("@") && source[1].isLowerCase() -> source
                else -> {
                    "@androidx.annotation." + source.substring(1)
                }
            }
        }
    }
}

/** Get the [TypeNullability] from a list of [AnnotationItem]s. */
val List<AnnotationItem>.typeNullability
    get() = mapNotNull { it.typeNullability }.firstOrNull()

/**
 * Get the value of the named attribute as an object of the specified type or null if the attribute
 * could not be found.
 *
 * This can only be called for attributes which have a single value, it will throw an exception if
 * called for an attribute whose value is any array type. See [getAttributeValues] instead.
 *
 * This supports the following types for [T]:
 * * [String] - the attribute must be of type [String] or [Class].
 * * [AnnotationItem] - the attribute must be of an annotation type.
 * * [Boolean] - the attribute must be of type [Boolean].
 * * [Byte] - the attribute must be of type [Byte].
 * * [Char] - the attribute must be of type [Char].
 * * [Double] - the attribute must be of type [Double].
 * * [Float] - the attribute must be of type [Float].
 * * [Int] - the attribute must be of type [Int].
 * * [Long] - the attribute must be of type [Long].
 * * [Short] - the attribute must be of type [Short].
 *
 * Any other types will result in a [ClassCastException].
 */
inline fun <reified T : Any> AnnotationItem.getAttributeValue(name: String): T? {
    val value = nonInlineGetAttributeValue(T::class, name) ?: return null
    return value as T
}

/**
 * Non-inline portion of functionality needed by [getAttributeValue]; separated to reduce the cost
 * of inlining [getAttributeValue].
 */
@PublishedApi
internal fun AnnotationItem.nonInlineGetAttributeValue(kClass: KClass<*>, name: String): Any? {
    val attributeValue = findAttribute(name)?.value ?: return null
    val value =
        when (attributeValue) {
            is ArrayValue -> throw IllegalStateException("Annotation attribute is of type array")
            else -> attributeValue.asLiteralValue()?.underlyingValue ?: attributeValue
        }

    return convertValue(kClass, value)
}

/**
 * Get the values of the named attribute as a list of objects of the specified type or null if the
 * attribute could not be found.
 *
 * This can be used to get the value of an attribute that is either one of the types in
 * [getAttributeValue] (in which case this returns a list containing a single item), or an array of
 * one of the types in [getAttributeValue] (in which case this returns a list containing all the
 * items in the array).
 */
inline fun <reified T : Any> AnnotationItem.getAttributeValues(name: String): List<T>? {
    return nonInlineGetAttributeValues(T::class, name) { it as T }
}

/**
 * Non-inline portion of functionality needed by [getAttributeValues]; separated to reduce the cost
 * of inlining [getAttributeValues].
 */
@PublishedApi
internal fun <T : Any> AnnotationItem.nonInlineGetAttributeValues(
    kClass: KClass<*>,
    name: String,
    caster: (Any) -> T
): List<T>? {
    val attributeValue = findAttribute(name)?.value ?: return null
    val values = attributeValue.asFlatList().map { it.asLiteralValue()?.underlyingValue ?: it }

    return values.map { convertValue(kClass, it) }.map { caster(it) }
}

/**
 * Perform some conversions to try and make [value] to be an instance of [kClass].
 *
 * This fixes up some known issues with [value] not corresponding to the expected type but otherwise
 * simply returns the value it is given. It is the caller's responsibility to actually cast the
 * returned value to the correct type.
 */
private fun convertValue(kClass: KClass<*>, value: Any): Any {
    // The value stored for number types is not always the same as the type of the annotation
    // attributes. This is for a number of reasons, e.g.
    // * In a .class file annotation values are stored in the constant pool and some number types do
    //   not have their own constant form (or their own array constant form) so are stored as
    //   instances of a wider type. They need to be converted to the correct type.
    // * In signature files annotation values are not always stored as the narrowest type, may not
    //   have a suffix and type information may not always be available when parsing.
    if (Number::class.java.isAssignableFrom(kClass.java)) {
        value as Number
        return when (kClass) {
            // Byte does have its own constant form but when stored in an array it is stored as an
            // int.
            Byte::class -> value.toByte()
            // DefaultAnnotationValue.create() always reads integers as longs.
            Int::class -> value.toInt()
            // DefaultAnnotationValue.create() always reads floating point as doubles.
            Float::class -> value.toFloat()
            // Short does not have its own constant form.
            Short::class -> value.toShort()
            else -> value
        }
    }

    if (kClass == AnnotationItem::class) {
        return (value as AnnotationValue).annotationItem
    }

    return value
}

/** Provides contextual information needed by [AnnotationItem]s. */
interface AnnotationContext : ClassResolver {
    /** The manager of annotations within this context. */
    val annotationManager: AnnotationManager

    /**
     * Get the defaults for the annotation class called [qualifiedName].
     *
     * While the default implementation is in terms of [resolveClass] this is separate to allow
     * subclasses to provide defaults without resolving a [ClassItem] as that can have side effects
     * which cause problems if done during [Codebase] construction.
     */
    fun defaultsForAnnotationClass(qualifiedName: String) =
        resolveClass(qualifiedName)?.annotationClass?.defaults ?: AnnotationDefaults.EMPTY

    companion object {
        /**
         * Instance that can be used in contexts where [resolveClass] always returns null, e.g.
         * testing or when parsing annotations provides on the command line.
         */
        val DEFAULT_RESOLVE_NULL: AnnotationContext =
            object : AnnotationContext, ClassResolver by ClassResolver.RETURN_NULL {
                /**
                 * Return [noOpAnnotationManager] rather than just throwing an exception as most
                 * uses of [AnnotationItem]s will make at least one call to [annotationManager] and
                 * having it return a valid, but basic implementation makes this more useful.
                 */
                override val annotationManager
                    get() = noOpAnnotationManager
            }
    }
}

/** Default implementation of an annotation item */
open class DefaultAnnotationItem
/** The primary constructor is private to force subclasses to use the secondary constructor. */
protected constructor(
    override val annotationContext: AnnotationContext,
    override val fileLocation: FileLocation,

    /** Fully qualified name of the annotation (prior to name mapping) */
    protected val originalName: String,

    /** Fully qualified name of the annotation (after name mapping) */
    final override val qualifiedName: String,

    /** Possibly empty list of attributes. */
    attributesGetter: () -> List<AnnotationAttribute>,
) : AnnotationItem {

    override val targets
        get() = info.targets

    final override val attributes: List<AnnotationAttribute> by
        lazy(LazyThreadSafetyMode.NONE, attributesGetter)

    /** Information that metalava has gathered about this annotation item. */
    internal val info: AnnotationInfo by lazy {
        annotationContext.annotationManager.getAnnotationInfo(this)
    }

    override val typeNullability: TypeNullability?
        get() = info.typeNullability

    override fun isNullnessAnnotation(): Boolean {
        return info.typeNullability != null
    }

    override fun isNullable(): Boolean {
        return info.typeNullability == TypeNullability.NULLABLE
    }

    override fun isNonNull(): Boolean {
        return info.typeNullability == TypeNullability.NONNULL
    }

    override val showability: Showability
        get() = info.showability

    override val apiFlag: ApiFlag?
        get() = info.apiFlag

    override fun resolve(): ClassItem? {
        return annotationContext.resolveClass(originalName)
    }

    /** If this annotation has a typedef annotation associated with it, return it */
    override fun findTypedefAnnotation(): AnnotationItem? {
        return resolve()?.modifiers?.findAnnotation(AnnotationItem::isTypeDefAnnotation)
    }

    override fun isShowAnnotation(): Boolean = info.showability.show()

    override fun isShowForStubPurposes(): Boolean = info.showability.showForStubsOnly()

    override fun isHideAnnotation(): Boolean = info.showability.hide()

    override fun isSuppressCompatibilityAnnotation(): Boolean = info.suppressCompatibility

    override fun isShowabilityAnnotation(): Boolean = info.showability != Showability.NO_EFFECT

    override fun snapshot(targetCodebase: Codebase): AnnotationItem {
        // Force the info property to be initialized which will cause the AnnotationInfo for
        // annotations of the same class as this to be created based off this AnnotationItem and
        // not the snapshot AnnotationItem. That is important because the AnnotationInfo
        // properties depends on accessing information like the ApiVariantSelectors which is
        // discarded when creating the snapshot. The snapshot AnnotationItem will retrieve the
        // cached version of the AnnotationInfo from the AnnotationManager.
        info

        return DefaultAnnotationItem(
            targetCodebase,
            fileLocation,
            originalName,
            qualifiedName,
        ) {
            attributes.map { attributeToSnapshot ->
                // Defer retrieval of the value until it is needed as it could throw an exception.
                // This makes it easier to incrementally expand the Value model without breaking
                // existing snapshot tests.
                // TODO(b/354633349): Stop deferring retrieval.
                val valueProvider =
                    object : ValueProvider {
                        override val value: Value
                            get() = attributeToSnapshot.value.snapshot(targetCodebase)
                    }

                DefaultAnnotationAttribute(
                    attributeToSnapshot.name,
                    valueProvider,
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationItem) return false
        return qualifiedName == other.qualifiedName && attributes == other.attributes
    }

    override fun hashCode(): Int {
        var result = qualifiedName.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }

    override fun toSource(target: AnnotationTarget, context: Item?): String {
        return ANNOTATION_SOURCE_FORMATTER.annotationItemToSource(this, target, context)
    }

    final override fun toString() = buildString {
        appendAnnotationStringTo(
            this,
            ValueStringConfiguration.DEFAULT,
            // This method is never used for values.
            annotationIsValue = false,
        )
    }

    companion object {
        /** Create an annotation from [source]. */
        fun createFromSource(
            annotationContext: AnnotationContext,
            source: String,
        ): AnnotationItem? {
            val valueParser =
                ValueParser(
                    annotationContext,
                    TypeItemParser.forValueParser(annotationContext),
                )
            return valueParser.parseAnnotationItem(source)
        }

        /**
         * Create a [DefaultAnnotationItem] deferring the creation of the attributes until needed.
         *
         * Maps the [originalName] to a [qualifiedName] by using the [annotationContext]'s
         * [AnnotationManager.normalizeInputName].
         */
        fun createAttributesLazily(
            annotationContext: AnnotationContext,
            fileLocation: FileLocation,
            originalName: String,
            attributesGetter: () -> List<AnnotationAttribute>,
        ): AnnotationItem? {
            val qualifiedName =
                annotationContext.annotationManager.normalizeInputName(originalName) ?: return null
            return DefaultAnnotationItem(
                annotationContext = annotationContext,
                fileLocation = fileLocation,
                originalName = originalName,
                qualifiedName = qualifiedName,
                attributesGetter = attributesGetter,
            )
        }

        /**
         * Create a [DefaultAnnotationItem] with [attributes].
         *
         * Maps the [originalName] to a [qualifiedName] by using the [annotationContext]'s
         * [AnnotationManager.normalizeInputName].
         */
        fun createWithAttributes(
            annotationContext: AnnotationContext,
            fileLocation: FileLocation,
            originalName: String,
            attributes: List<AnnotationAttribute>,
        ): AnnotationItem? {
            return createAttributesLazily(annotationContext, fileLocation, originalName) {
                attributes
            }
        }
    }
}

/** The default annotation attribute name when no name is provided. */
const val ANNOTATION_ATTR_VALUE = "value"

/** An attribute of an annotation, such as "value" */
sealed interface AnnotationAttribute {
    /** The name of the annotation */
    val name: String

    /**
     * The value of this attribute.
     *
     * The [Value] will be suitable for use as an annotation attribute value as specified by JLS
     * 9.6.1 (what this model calls "attributes", the JSL calls "elements"). That includes constant
     * fields.
     */
    val value: Value
}

const val ANNOTATION_VALUE_TRUE = "true"

class DefaultAnnotationAttribute(
    override val name: String,
    private val valueProvider: ValueProvider,
) : AnnotationAttribute {

    override val value: Value
        get() = valueProvider.value

    override fun toString(): String {
        return "$name=${value.toValueString()}"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationAttribute) return false
        return name == other.name && value == other.value
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}
