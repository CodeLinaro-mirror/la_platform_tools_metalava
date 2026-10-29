/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.reporter.Issues
import java.io.Writer

class ModifierListWriter(
    private val writer: Writer,
    config: Config,
) {
    data class Config(
        /**
         * Can be one of [AnnotationTarget.SIGNATURE_FILE], [AnnotationTarget.SDK_STUBS_FILE] or
         * [AnnotationTarget.DOC_STUBS_FILE].
         */
        val target: AnnotationTarget,

        /** The [AnnotationFormatter] that is used for formatting any [AnnotationItem]s. */
        val annotationFormatter: AnnotationFormatter,

        /**
         * If `true` then only annotations with [AnnotationRetention.RUNTIME] will be written out,
         * otherwise the retention is ignored.
         */
        val runtimeAnnotationsOnly: Boolean,

        /** If `true` then nullness annotations will not be written out, otherwise they will. */
        val skipNullnessAnnotations: Boolean,

        /**
         * If `true` then the `final` modifier on a method in a `final` class will not be written
         * out, otherwise it will.
         */
        val normalizeFinal: Boolean = true,

        /**
         * If `true` then any unnecessary `abstract` modifiers, e.g. on an annotation or enum class
         * will not be written out, otherwise `abstract` modifier will always be written out except
         * on interface methods.
         */
        val normalizeAbstract: Boolean = true,

        /** Determines whether `@FlaggedApi` annotations are inherited and if so how. */
        val flaggedApiInheritance: FlaggedApiInheritance = FlaggedApiInheritance.NONE,

        /**
         * If `true` then a [ClassKind.RECORD] class will be represented as a `record` class and the
         * `final` modifier will not be written out as `record` classes are implicitly `final`.
         * Otherwise, a [ClassKind.RECORD] class will be written out as an explicitly `final` normal
         * class.
         */
        val javaRecordClasses: Boolean = false,

        /**
         * If `true` then `sealed`, `non-sealed`, `exhaustive` and `nonexhaustive` modifiers will be
         * written out for java classes, otherwise they will not.
         */
        val javaSealedClasses: Boolean = false,
    )

    private val target = config.target
    private val annotationFormatter = config.annotationFormatter
    private val runtimeAnnotationsOnly = config.runtimeAnnotationsOnly
    private val skipNullnessAnnotations = config.skipNullnessAnnotations
    private val normalizeFinal = config.normalizeFinal
    private val normalizeAbstract = config.normalizeAbstract
    private val flaggedApiInheritance = config.flaggedApiInheritance
    private val javaRecordClasses: Boolean = config.javaRecordClasses
    private val javaSealedClasses = config.javaSealedClasses

    /**
     * Whether the `abstract` modifier should only be disallowed on interface methods.
     *
     * Signature files only disallow `abstract` on interfaces when not normalizing abstract. All
     * other files disallow `abstract` on methods iff it is disallowed on the method's containing
     * class.
     */
    private val onlyDisallowAbstractOnInterfaceMethods =
        target == AnnotationTarget.SIGNATURE_FILE && !normalizeAbstract

    companion object {
        /**
         * Checks whether the method requires a body to be generated in the stubs.
         * * Methods that are annotations are implicitly `abstract` but the body is provided by the
         *   runtime, so they never need bodies.
         * * Native methods never need bodies.
         * * Abstract methods do not need bodies unless they are enums in which case see
         *   [MethodItem.allowAbstract] for an explanation as to why they need bodies.
         */
        fun requiresMethodBodyInStubs(methodItem: MethodItem): Boolean {
            val modifiers = methodItem.modifiers
            val containingClass = methodItem.containingClass()

            val isEnum = containingClass.isEnum()
            val isAnnotation = containingClass.isAnnotationType()

            return (!modifiers.isAbstract() || isEnum) && !isAnnotation && !modifiers.isNative()
        }
    }

    /** Write the modifier list (possibly including annotations) to the supplied [writer]. */
    fun write(item: Item) {
        writeAnnotations(item)
        writeKeywords(item)
    }

    /** Write the modifier keywords. */
    fun writeKeywords(item: Item) {
        if (
            item is PackageItem ||
                (target != AnnotationTarget.SIGNATURE_FILE &&
                    item is FieldItem &&
                    item.isEnumConstant())
        ) {
            // Packages and enum constants (in a stubs file) use a modifier list, but only
            // annotations apply.
            return
        }

        // Kotlin order:
        //   https://kotlinlang.org/docs/reference/coding-conventions.html#modifiers

        val classItem = item as? ClassItem
        val classKind =
            classItem?.classKind?.let { kind ->
                if (kind == ClassKind.RECORD && !javaRecordClasses) ClassKind.CLASS else kind
            }
        val methodItem = item as? MethodItem

        val list = item.modifiers
        val visibilityLevel = list.getVisibilityLevel()
        val modifier = visibilityLevel.javaSourceCodeModifier
        if (modifier.isNotEmpty()) {
            writer.write("$modifier ")
        }

        // Abstract: should appear in interfaces if in compat mode
        val isAbstract = list.isAbstract()
        if (isAbstract && allowAbstract(classKind, methodItem)) {
            writer.write("abstract ")
        }

        if (list.isDefault() && item !is ParameterItem) {
            writer.write("default ")
        }

        if (list.isStatic() && classKind?.implicitlyStatic != true) {
            writer.write("static ")
        }

        if (
            list.isFinal() &&
                // Don't show final on parameters: that's an implementation detail
                item !is ParameterItem &&
                // Don't add final on enum or enum members as they are implicitly final.
                classKind?.implicitlyFinal != true &&
                // If normalizing and the current item is a method and its containing class is final
                // then do not write out the final keyword.
                (!normalizeFinal || methodItem?.containingClass()?.modifiers?.isFinal() != true)
        ) {
            writer.write("final ")
        }

        // Only write sealed keywords if they do not come from java or java sealed classes are
        // specifically supported. When [item] is read from a signature file it will be set to
        // [SourceLanguage.UNKNOWN] whether it was originally from Kotlin or Java. This check
        // ensures that reading a [Codebase] from a signature file, and then writing it out (as is
        // done by many of the signature related commands) does not drop the sealed keywords.
        val allowSealedKeywords = item.sourceLanguage != SourceLanguage.JAVA || javaSealedClasses
        if (allowSealedKeywords) {
            if (list.isSealed()) {
                writer.write("sealed ")

                // Only write exhaustive to signature files.
                if (target == AnnotationTarget.SIGNATURE_FILE) {
                    if (list.isExhaustive()) {
                        writer.write("exhaustive ")
                    } else if (javaSealedClasses) {
                        writer.write("non-exhaustive ")
                    } else {
                        writer.write("nonexhaustive ")
                    }
                }
            }

            if (list.isNonSealed()) {
                writer.write("non-sealed ")
            }
        } else if (list.isSealed()) {
            item.codebase.reporter.report(
                Issues.ADDED_SEALED,
                item,
                "`sealed` is not currently supported, see b/482391240 for more details.",
            )
        }

        if (list.isSuspend()) {
            writer.write("suspend ")
        }

        if (list.isInline()) {
            writer.write("inline ")
        }

        if (list.isValue()) {
            writer.write("value ")
        }

        if (list.isInfix()) {
            writer.write("infix ")
        }

        if (list.isOperator()) {
            writer.write("operator ")
        }

        if (list.isTransient()) {
            writer.write("transient ")
        }

        if (list.isVolatile()) {
            writer.write("volatile ")
        }

        if (list.isSynchronized() && target.isStubsFile()) {
            writer.write("synchronized ")
        }

        if (list.isNative() && (target.isStubsFile() || isSignaturePolymorphic(item))) {
            writer.write("native ")
        }

        if (list.isFunctional()) {
            writer.write("fun ")
        }
    }

    /** Determine whether the `abstract` modifier is required on [classKind] or [methodItem]. */
    private fun allowAbstract(classKind: ClassKind?, methodItem: MethodItem?) =
        classKind?.allowAbstract ?: methodItem?.allowAbstract() ?: true

    /**
     * Determine whether the `abstract` modifier is allowed on this [MethodItem].
     *
     * In signature files, only interface methods disallow `abstract` modifier. Annotation and enum
     * methods could also disallow them but are inconsistent.
     *
     * In all other files, including but not limited to stubs, neither interfaces, annotation types,
     * nor enums allow `abstract` modifier. In fact only normal class kinds allow them.
     *
     * Interface and annotation types do not allow the `abstract` modifier on methods because while
     * they are usable on their methods they are unnecessary.
     *
     * Methods in an enum can also be `abstract` but that requires them to be implemented in each
     * Enum constant. However, the stubs do not generate overrides of those methods for the enum
     * constants so they must always to be concrete otherwise the stubs for the enum class will not
     * compile.
     */
    private fun MethodItem.allowAbstract(): Boolean {
        val containingClassKind = containingClass().classKind
        return if (onlyDisallowAbstractOnInterfaceMethods) {
            containingClassKind != ClassKind.INTERFACE
        } else {
            // Otherwise, disallow it on the methods iff it is disallowed on the method's containing
            // class.
            containingClassKind.allowAbstract
        }
    }

    /** Find the [ANDROID_FLAGGED_API] in this list of [AnnotationItem]s, if there is one. */
    private fun List<AnnotationItem>.findFlaggedApiAnnotation() = find {
        it.qualifiedName == ANDROID_FLAGGED_API
    }

    /** Get the [ANDROID_FLAGGED_API] [AnnotationItem] to add to [item]'s [annotations], if any. */
    private fun flaggedApiAnnotationToInherit(
        item: Item,
        annotations: List<AnnotationItem>,
    ): AnnotationItem? {
        // If they are not required to be inherited onto nested classes then return null.
        if (flaggedApiInheritance != FlaggedApiInheritance.NESTED_CLASSES) return null

        // If the item is not a nested class then return null.
        if (item !is ClassItem) return null
        var containingClassItem: ClassItem? = item.containingClass() ?: return null

        // If this already has an [ANDROID_FLAGGED_API] annotation
        if (annotations.findFlaggedApiAnnotation() != null) return null

        // Find the closest enclosing [ANDROID_FLAGGED_API] annotation in [item]'s containing
        // [ClassItem], if there is one.
        while (containingClassItem != null) {
            containingClassItem.modifiers.annotations().findFlaggedApiAnnotation()?.let {
                return it
            }
            containingClassItem = containingClassItem.containingClass()
        }

        // Otherwise, no flagged api annotation needs adding.
        return null
    }

    fun writeAnnotations(item: Item) {
        // Generate annotations on separate lines in stub files for packages, classes and
        // methods and also for enum constants.
        val separateLines =
            target != AnnotationTarget.SIGNATURE_FILE &&
                when (item) {
                    is CallableItem,
                    is ClassItem,
                    is PackageItem -> true
                    is FieldItem -> item.isEnumConstant()
                    else -> false
                }

        // Do not write deprecate annotations on a package.
        if (item !is PackageItem) {
            val writeDeprecated =
                when {
                    // Do not write @Deprecated for a parameter unless it was explicitly marked
                    // as deprecated.
                    item is ParameterItem -> item.originallyDeprecated
                    else -> item.effectivelyDeprecated
                }
            if (writeDeprecated) {
                writer.write("@Deprecated")
                writer.write(if (separateLines) "\n" else " ")
            }
        }

        val modifierList = item.modifiers
        var annotations = modifierList.annotations()

        // Check to see if a FlaggedApi annotation needs to be inherited onto this item and if it
        // does add it to this list. It will be sorted into the correct position below.
        flaggedApiAnnotationToInherit(item, annotations)?.let { flaggedApiAnnotation ->
            annotations = annotations + flaggedApiAnnotation
        }

        // Modifiers may be written differently for stubs to remove superfluous information that the
        // consumers of the stubs (e.g. linters) don't need
        if (target.isStubsFile()) {
            // @FlaggedApi annotations are always converted to @RequiresFlag in stub files.
            // Developers are not allowed to add @RequiresFlag to flagged APIs to avoid duplicate
            // @RequiresFlag compilation errors in stubs
            if (
                annotations.any { it.qualifiedName == ANDROID_FLAGGED_API } &&
                    annotations.any { it.qualifiedName == ANDROID_REQUIRES_FLAG }
            ) {
                item.codebase.reporter.report(
                    Issues.MULTIPLE_FLAGGING,
                    item,
                    "@RequiresFlag can not be placed on APIs that are already flagged.",
                )

                annotations = annotations.filter { it.qualifiedName != ANDROID_REQUIRES_FLAG }
            }

            // Inherit @FlaggedApi from enclosing classes if not present (for public APIs),
            // and remove @FlaggedApi if the item was part of a previously released API.
            if (item is SelectableItem && modifierList.isPublic() or modifierList.isProtected()) {
                if (annotations.findFlaggedApiAnnotation() == null) {
                    var containingClassItem: ClassItem? = item.containingClass()
                    while (containingClassItem != null) {
                        val flaggedApiAnnotation =
                            containingClassItem.modifiers.annotations().findFlaggedApiAnnotation()
                        if (flaggedApiAnnotation != null) {
                            annotations = annotations + flaggedApiAnnotation
                            break
                        }
                        containingClassItem = containingClassItem.containingClass()
                    }
                }

                val previousItem =
                    item.codebase.config.annotationManager.findPreviouslyReleasedItem(item)
                if (previousItem != null) {
                    annotations = annotations.filter { it.qualifiedName != ANDROID_FLAGGED_API }
                }
            }
        }

        if (annotations.isEmpty()) {
            return
        }

        if (annotations.any { it.isSuppressCompatibilityAnnotation() }) {
            writer.write("@$SUPPRESS_COMPATIBILITY_ANNOTATION")
            writer.write(if (separateLines) "\n" else " ")
        }

        // Remove @SuppressCompatibility if it exists (it will for text codebases) because it was
        // already written out above.
        annotations =
            annotations.filter { it.qualifiedName != SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED }
        // Ensure stable signature file order
        annotations = annotations.sortedBy { it.qualifiedName }

        var index = -1
        for (annotation in annotations) {
            index++

            if (runtimeAnnotationsOnly && annotation.retention != AnnotationRetention.RUNTIME) {
                continue
            }

            var printAnnotation = annotation
            if (!annotation.targets.contains(target)) {
                continue
            } else if (annotation.isNullnessAnnotation()) {
                // skip Nullness annotations if requested, otherwise fall through the if-statements
                // like any other annotation
                if (skipNullnessAnnotations) {
                    continue
                }
            } else if (annotation.qualifiedName == "java.lang.Deprecated") {
                // Special cased in stubs and signature files: emitted first
                continue
            } else {
                val typedefMode = item.codebase.annotationManager.typedefMode
                if (typedefMode == TypedefMode.INLINE) {
                    val typedef = annotation.findTypedefAnnotation()
                    if (typedef != null) {
                        printAnnotation = typedef
                    }
                } else if (
                    typedefMode == TypedefMode.REFERENCE &&
                        annotation.targets === ANNOTATION_SIGNATURE_ONLY &&
                        annotation.findTypedefAnnotation() != null
                ) {
                    // For annotation references, only include the simple name
                    writer.write("@")
                    writer.write(annotation.resolve()?.simpleName() ?: annotation.qualifiedName)
                    if (separateLines) {
                        writer.write("\n")
                    } else {
                        writer.write(" ")
                    }
                    continue
                }
            }

            val source =
                annotationFormatter.formatAnnotation(printAnnotation, AnnotationPurpose.ITEM, item)
            writer.write(source)

            if (separateLines) {
                writer.write("\n")
            } else {
                writer.write(" ")
            }
        }
    }

    /** The set of classes that may contain polymorphic methods. */
    private val polymorphicHandleTypes =
        setOf(
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.VarHandle",
        )

    /**
     * Check to see whether a native item is actually a method with a polymorphic signature.
     *
     * The java compiler treats methods with polymorphic signatures specially. It identifies a
     * method as being polymorphic according to the rules defined in JLS 15.12.3. See
     * https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12.3 for the latest (at
     * time of writing rules). They state:
     *
     * A method is signature polymorphic if all of the following are true:
     * * It is declared in the [java.lang.invoke.MethodHandle] class or the
     *   [java.lang.invoke.VarHandle] class.
     * * It has a single variable arity parameter (§8.4.1) whose declared type is Object[].
     * * It is native.
     *
     * The latter point means that the `native` modifier is an important part of a polymorphic
     * method's signature even though Metalava generally views the `native` modifier as an
     * implementation detail that should not be part of the API. So, if this method returns `true`
     * then the `native` modifier will be output to API signatures.
     */
    private fun isSignaturePolymorphic(item: Item): Boolean {
        return item is MethodItem &&
            item.containingClass().qualifiedName() in polymorphicHandleTypes &&
            item.parameters().let { parameters ->
                parameters.size == 1 &&
                    parameters[0].let { parameter ->
                        parameter.isVarArgs() &&
                            // Check type is java.lang.Object[]
                            parameter.type().let { type ->
                                type is ArrayTypeItem &&
                                    type.componentType.let { componentType ->
                                        componentType is ClassTypeItem &&
                                            componentType.qualifiedName == "java.lang.Object"
                                    }
                            }
                    }
            }
    }
}

/**
 * Synthetic annotation used to mark an API as suppressed for compatibility checks.
 *
 * This is added automatically when an API has a meta-annotation that suppresses compatibility but
 * is defined outside the source set and may not always be available on the classpath.
 *
 * Because this is used in API files, it needs to maintain compatibility.
 */
const val SUPPRESS_COMPATIBILITY_ANNOTATION = "SuppressCompatibility"

/**
 * Fully-qualified version of [SUPPRESS_COMPATIBILITY_ANNOTATION].
 *
 * This is only used at run-time for matching against [AnnotationItem.qualifiedName], so it doesn't
 * need to maintain compatibility.
 */
val SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED =
    AnnotationItem.unshortenAnnotation(SUPPRESS_COMPATIBILITY_ANNOTATION)

/** Determines how [ModifierListWriter] handles `@FlaggedApi` inheritance. */
enum class FlaggedApiInheritance {
    /** @FlaggedApi annotations are not inherited. */
    NONE,

    /**
     * @FlaggedApi annotations are inherited onto nested classes that do not have their own
     *   annotation.
     */
    NESTED_CLASSES,
}
