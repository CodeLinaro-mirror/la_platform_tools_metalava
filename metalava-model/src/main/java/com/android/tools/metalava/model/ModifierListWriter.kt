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

import java.io.Writer

class ModifierListWriter
private constructor(
    private val writer: Writer,
    /**
     * Can be one of [AnnotationTarget.SIGNATURE_FILE], [AnnotationTarget.SDK_STUBS_FILE] or
     * [AnnotationTarget.DOC_STUBS_FILE].
     */
    private val target: AnnotationTarget,
    private val runtimeAnnotationsOnly: Boolean = false,
    private val skipNullnessAnnotations: Boolean = false,
    private val language: Language = Language.JAVA,
) {
    companion object {
        fun forSignature(
            writer: Writer,
            skipNullnessAnnotations: Boolean,
        ) =
            ModifierListWriter(
                writer = writer,
                target = AnnotationTarget.SIGNATURE_FILE,
                skipNullnessAnnotations = skipNullnessAnnotations,
            )

        fun forStubs(
            writer: Writer,
            target: AnnotationTarget,
            runtimeAnnotationsOnly: Boolean = false,
            language: Language = Language.JAVA,
        ) =
            ModifierListWriter(
                writer = writer,
                target = target,
                runtimeAnnotationsOnly = runtimeAnnotationsOnly,
                skipNullnessAnnotations = language == Language.KOTLIN,
                language = language,
            )
    }

    /**
     * Write the modifier list (possibly including annotations) to the supplied [writer].
     *
     * @return true if generating stubs and [Item] is a [MethodItem] and requires a body in order
     *   for the stub to compile.
     */
    fun write(item: Item): Boolean {
        writeAnnotations(item)

        if (
            item is PackageItem ||
                (target != AnnotationTarget.SIGNATURE_FILE &&
                    item is FieldItem &&
                    item.isEnumConstant())
        ) {
            // Packages and enum constants (in a stubs file) use a modifier list, but only
            // annotations apply.
            return false
        }

        // Kotlin order:
        //   https://kotlinlang.org/docs/reference/coding-conventions.html#modifiers

        // Abstract: should appear in interfaces if in compat mode
        val classItem = item as? ClassItem
        val methodItem = item as? MethodItem

        val list = item.modifiers
        val visibilityLevel = list.getVisibilityLevel()
        val modifier =
            if (language == Language.JAVA) {
                visibilityLevel.javaSourceCodeModifier
            } else {
                visibilityLevel.kotlinSourceCodeModifier
            }
        if (modifier.isNotEmpty()) {
            writer.write("$modifier ")
        }

        val isInterface =
            classItem?.isInterface() == true ||
                (methodItem?.containingClass()?.isInterface() == true &&
                    !list.isDefault() &&
                    !list.isStatic())

        val isAbstract = list.isAbstract()
        val removeAbstract =
            isAbstract &&
                target != AnnotationTarget.SIGNATURE_FILE &&
                methodItem?.let {
                    val containingClass = methodItem.containingClass()

                    // Need to filter out abstract from the modifiers list and turn it into
                    // a concrete method to make the stub compile
                    containingClass.isEnum() || containingClass.isAnnotationType()
                }
                    ?: false

        if (
            isAbstract &&
                !removeAbstract &&
                classItem?.isEnum() != true &&
                classItem?.isAnnotationType() != true &&
                !isInterface
        ) {
            writer.write("abstract ")
        }

        if (list.isDefault() && item !is ParameterItem) {
            writer.write("default ")
        }

        if (list.isStatic() && (classItem == null || !classItem.isEnum())) {
            writer.write("static ")
        }

        if (
            list.isFinal() &&
                language == Language.JAVA &&
                // Don't show final on parameters: that's an implementation side detail
                item !is ParameterItem &&
                classItem?.isEnum() != true
        ) {
            writer.write("final ")
        } else if (!list.isFinal() && language == Language.KOTLIN) {
            writer.write("open ")
        }

        if (list.isSealed()) {
            writer.write("sealed ")
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

        if (language == Language.KOTLIN) {
            if (list.isData()) {
                writer.write("data ")
            }
        }

        // Compute whether a method body is required.
        return if (target == AnnotationTarget.SIGNATURE_FILE || methodItem == null) {
            false
        } else {
            val containingClass = methodItem.containingClass()

            val isEnum = containingClass.isEnum()
            val isAnnotation = containingClass.isAnnotationType()

            (!list.isAbstract() || removeAbstract || isEnum) && !isAnnotation && !list.isNative()
        }
    }

    private fun writeAnnotations(item: Item) {
        // Generate annotations on separate lines in stub files for packages, classes and
        // methods and also for enum constants.
        val separateLines =
            target != AnnotationTarget.SIGNATURE_FILE &&
                when (item) {
                    is MethodItem,
                    is ClassItem,
                    is PackageItem -> true
                    is FieldItem -> item.isEnumConstant()
                    else -> false
                }

        // Do not write deprecate or suppress compatibility annotations on a package.
        if (item !is PackageItem) {
            if (item.deprecated) {
                // Do not write @Deprecated for a parameter unless it was explicitly marked as
                // deprecated.
                if (item !is ParameterItem || item.originallyDeprecated) {
                    writer.write("@Deprecated")
                    writer.write(if (separateLines) "\n" else " ")
                }
            }

            if (item.hasSuppressCompatibilityMetaAnnotation()) {
                writer.write("@$SUPPRESS_COMPATIBILITY_ANNOTATION")
                writer.write(if (separateLines) "\n" else " ")
            }
        }

        val list = item.modifiers
        var annotations = list.annotations()

        // Ensure stable signature file order
        if (annotations.size > 1) {
            annotations = annotations.sortedBy { it.qualifiedName }
        }

        if (annotations.isNotEmpty()) {
            // Omit common packages in signature files.
            val omitCommonPackages = target == AnnotationTarget.SIGNATURE_FILE
            var index = -1
            for (annotation in annotations) {
                index++

                if (runtimeAnnotationsOnly && annotation.retention != AnnotationRetention.RUNTIME) {
                    continue
                }

                var printAnnotation = annotation
                if (!annotation.targets.contains(target)) {
                    continue
                } else if ((annotation.isNullnessAnnotation())) {
                    if (skipNullnessAnnotations) {
                        continue
                    }
                } else if (annotation.qualifiedName == "java.lang.Deprecated") {
                    // Special cased in stubs and signature files: emitted first
                    continue
                } else {
                    val typedefMode = list.codebase.annotationManager.typedefMode
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
                        writer.write(
                            annotation.resolve()?.simpleName() ?: annotation.qualifiedName!!
                        )
                        if (separateLines) {
                            writer.write("\n")
                        } else {
                            writer.write(" ")
                        }
                        continue
                    }
                }

                val source = printAnnotation.toSource(target, showDefaultAttrs = false)

                if (omitCommonPackages) {
                    writer.write(AnnotationItem.shortenAnnotation(source))
                } else {
                    writer.write(source)
                }
                if (separateLines) {
                    writer.write("\n")
                } else {
                    writer.write(" ")
                }
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
