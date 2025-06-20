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
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueContext
import com.android.tools.metalava.model.value.ValueLanguage
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.model.value.ValueProvider
import com.android.tools.metalava.model.value.ValueStringConfiguration
import com.android.tools.metalava.model.value.provider
import com.android.tools.metalava.reporter.FileLocation
import java.lang.StringBuilder

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

    /** Take a snapshot of this [AnnotationItem] suitable for use in [targetContext]. */
    fun snapshot(targetContext: AnnotationContext): AnnotationItem

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
         * Create an [AnnotationItem] deferring the creation of the attributes until needed.
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
            return LazyAttributesAnnotationItem(
                annotationContext = annotationContext,
                fileLocation = fileLocation,
                originalName = originalName,
                qualifiedName = qualifiedName,
                attributesGetter = attributesGetter,
            )
        }

        /**
         * Create an [AnnotationItem] with [attributes].
         *
         * Maps the [originalName] to a [qualifiedName] by using the [annotationContext]'s
         * [AnnotationManager.normalizeInputName].
         */
        fun createWithAttributes(
            annotationContext: AnnotationContext,
            fileLocation: FileLocation = FileLocation.UNKNOWN,
            originalName: String,
            attributes: List<AnnotationAttribute> = emptyList(),
        ): AnnotationItem? {
            val qualifiedName =
                annotationContext.annotationManager.normalizeInputName(originalName) ?: return null
            return DefaultAnnotationItem(
                annotationContext,
                fileLocation,
                originalName,
                qualifiedName,
                attributes,
            )
        }

        /**
         * Create a marker [AnnotationItem], i.e. one without [attributes].
         *
         * Maps the [originalName] to a [qualifiedName] by using the [annotationContext]'s
         * [AnnotationManager.normalizeInputName].
         */
        fun createMarkerAnnotation(
            annotationContext: AnnotationContext,
            originalName: String,
            fileLocation: FileLocation = FileLocation.UNKNOWN,
        ) = createWithAttributes(annotationContext, fileLocation, originalName)

        /**
         * Create a single element [AnnotationItem], i.e. one with a single required attribute
         * called [ANNOTATION_ATTR_VALUE], i.e. `value`.
         *
         * Maps the [originalName] to a [qualifiedName] by using the [annotationContext]'s
         * [AnnotationManager.normalizeInputName].
         */
        fun createSingleElementAnnotation(
            annotationContext: AnnotationContext,
            originalName: String,
            value: Value,
            fileLocation: FileLocation = FileLocation.UNKNOWN,
        ) =
            createWithAttributes(
                annotationContext,
                fileLocation,
                originalName,
                listOf(AnnotationAttribute.createAttribute(ANNOTATION_ATTR_VALUE, value))
            )
    }
}

/** Get the [TypeNullability] from a list of [AnnotationItem]s. */
val List<AnnotationItem>.typeNullability
    get() = mapNotNull { it.typeNullability }.firstOrNull()

/** Provides contextual information needed by [AnnotationItem]s. */
interface AnnotationContext : ClassResolver, ValueContext {
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
internal abstract class BaseAnnotationItem(
    override val annotationContext: AnnotationContext,
    override val fileLocation: FileLocation,

    /** Fully qualified name of the annotation (prior to name mapping) */
    internal val originalName: String,

    /** Fully qualified name of the annotation (after name mapping) */
    override val qualifiedName: String,
) : AnnotationItem {

    override val targets
        get() = info.targets

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

    override fun snapshot(targetContext: AnnotationContext): AnnotationItem {
        // Force the info property to be initialized which will cause the AnnotationInfo for
        // annotations of the same class as this to be created based off this AnnotationItem and
        // not the snapshot AnnotationItem. That is important because the AnnotationInfo
        // properties depends on accessing information like the ApiVariantSelectors which is
        // discarded when creating the snapshot. The snapshot AnnotationItem will retrieve the
        // cached version of the AnnotationInfo from the AnnotationManager.
        info

        return createSnapshotFor(targetContext)
    }

    /** Create snapshot of this for [targetContext]. */
    abstract fun createSnapshotFor(targetContext: AnnotationContext): AnnotationItem

    override fun equals(other: Any?): Boolean {
        if (other !is AnnotationItem) return false
        return qualifiedName == other.qualifiedName && attributes == other.attributes
    }

    override fun hashCode(): Int {
        var result = qualifiedName.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }

    override fun toString() = buildString {
        appendAnnotationStringTo(
            this,
            ValueStringConfiguration.DEFAULT,
            // This method is never used for values.
            annotationIsValue = false,
        )
    }
}

internal class DefaultAnnotationItem(
    annotationContext: AnnotationContext,
    fileLocation: FileLocation,
    originalName: String,
    qualifiedName: String,
    override val attributes: List<AnnotationAttribute>,
) :
    BaseAnnotationItem(
        annotationContext,
        fileLocation,
        originalName,
        qualifiedName,
    ) {
    override fun createSnapshotFor(targetContext: AnnotationContext) =
        DefaultAnnotationItem(
            targetContext,
            fileLocation,
            originalName,
            qualifiedName,
            attributes.map { it.snapshot(targetContext) }
        )
}

internal class LazyAttributesAnnotationItem(
    annotationContext: AnnotationContext,
    fileLocation: FileLocation,
    originalName: String,
    qualifiedName: String,
    /** Possibly empty list of attributes. */
    attributesGetter: () -> List<AnnotationAttribute>,
) : BaseAnnotationItem(annotationContext, fileLocation, originalName, qualifiedName) {
    override val attributes: List<AnnotationAttribute> by
        lazy(LazyThreadSafetyMode.NONE, attributesGetter)

    override fun createSnapshotFor(targetContext: AnnotationContext) =
        LazyAttributesAnnotationItem(
            targetContext,
            fileLocation,
            originalName,
            qualifiedName,
        ) {
            attributes.map { it.snapshot(targetContext) }
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

    /** Take a snapshot of this [AnnotationAttribute] suitable for use in [targetContext]. */
    fun snapshot(targetContext: AnnotationContext): AnnotationAttribute

    companion object {
        /**
         * Create an [AnnotationAttribute] called [name] that will retrieve its [Value] from
         * [valueProvider] when requested.
         */
        fun createLazyAttribute(name: String, valueProvider: ValueProvider): AnnotationAttribute =
            DefaultAnnotationAttribute(name, valueProvider)

        /** Create an [AnnotationAttribute] called [name] with [value]. */
        fun createAttribute(name: String, value: Value): AnnotationAttribute =
            DefaultAnnotationAttribute(name, value.provider())
    }
}

internal class DefaultAnnotationAttribute(
    override val name: String,
    private val valueProvider: ValueProvider,
) : AnnotationAttribute {

    override val value: Value
        get() = valueProvider.value

    override fun snapshot(targetContext: AnnotationContext): DefaultAnnotationAttribute {
        // Defer retrieval of the value until it is needed as it could throw an exception.
        // This makes it easier to incrementally expand the Value model without breaking
        // existing snapshot tests.
        // TODO(b/354633349): Stop deferring retrieval.
        val valueProvider =
            object : ValueProvider {
                override val value: Value
                    get() = this@DefaultAnnotationAttribute.value.snapshot(targetContext)
            }

        return DefaultAnnotationAttribute(name, valueProvider)
    }

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
