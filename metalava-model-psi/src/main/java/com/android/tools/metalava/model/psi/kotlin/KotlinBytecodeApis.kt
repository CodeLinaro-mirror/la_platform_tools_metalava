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

package com.android.tools.metalava.model.psi.kotlin

import com.android.SdkConstants
import com.android.tools.lint.helpers.readAllBytes
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.KOTLIN_DEPRECATED
import com.android.tools.metalava.model.KOTLIN_METADATA
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.psi.PsiAnnotationItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiCallableItem
import com.android.tools.metalava.model.psi.PsiConstructorItem
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.PsiTypeItemFactory
import com.android.tools.metalava.model.psi.psiParameters
import com.android.tools.metalava.model.value.IntValue
import com.android.tools.metalava.model.value.StringValue
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.metadata.KmClass
import kotlin.metadata.KmConstructor
import kotlin.metadata.KmDeclarationContainer
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmPropertyAccessorAttributes
import kotlin.metadata.Visibility
import kotlin.metadata.hasAnnotations
import kotlin.metadata.isReified
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.jvm.syntheticMethodForAnnotations
import kotlin.metadata.visibility
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Functionality for loading APIs from jar files compiled from Kotlin source code.
 *
 * First, the jar file needs to be processed by [rewriteJar] to remove the `ACC_SYNTHETIC` modifier
 * from methods to allow them to be read by psi, and to track all the qualified names of classes
 * present in the jar. Then, [loadPsiFromProject] will search for the class names from the jar in a
 * psi project to add APIs to the [codebase].
 */
internal class KotlinBytecodeApis(val codebase: PsiBasedCodebase) {
    /** Class names to process. Populated by [rewriteJar] and used by [loadPsiFromProject]. */
    private val qualifiedClassNames = mutableListOf<String>()

    /**
     * A map from the fully qualified name of a multi-file class facade to the paths of the class
     * files that make it up. Each class part corresponds to a source file, and metadata for the
     * entries can only be found in the class part, not the multi-file class facade.
     */
    private val multiFileClassParts: MutableMap<String, List<String>> = mutableMapOf()

    /**
     * Processes the [originalJarFile] to remove the `ACC_SYNTHETIC` modifier from methods. This is
     * done because psi does not process synthetic members, but they can be important for API
     * tracking (e.g. methods annotated with [DeprecationLevel.HIDDEN]).
     *
     * Also saves the qualified names of all classes in the jar.
     */
    fun rewriteJar(originalJarFile: File): File {
        val newJarFile = kotlin.io.path.createTempFile(suffix = ".jar").toFile()
        val outputStream = ZipOutputStream(newJarFile.outputStream())
        ZipFile(originalJarFile).use { jar ->
            for (entry in jar.entries().iterator()) {
                val fileName = entry.name
                if (
                    !fileName.endsWith(SdkConstants.DOT_CLASS) ||
                        fileName.endsWith("package-info.class")
                ) {
                    // for entries that are not .class files, just write them to the new jar
                    outputStream.putNextEntry(entry)
                    outputStream.write(jar.readAllBytes(entry))
                    continue
                }

                val qualifiedName =
                    fileName
                        .removeSuffix(SdkConstants.DOT_CLASS)
                        .replace('/', '.')
                        .replace('$', '.')
                qualifiedClassNames.add(qualifiedName)

                // Create a reader for the old jar, and a writer for the new.
                val classReader = ClassReader(jar.getInputStream(entry))
                val classWriter = ClassWriter(/* flags= */ 0)
                // Process the class with a visitor that defers to the writer in all cases except
                // for methods.
                classReader.accept(
                    object : ClassVisitor(Opcodes.ASM9, classWriter) {
                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String?,
                            signature: String?,
                            exceptions: Array<String>?
                        ): MethodVisitor {
                            // Update the access flags of the method
                            val newAccess =
                                if (access and Opcodes.ACC_BRIDGE != 0) {
                                    // If this is a bridge method, leave the accessors as-is, since
                                    // we don't need to track these (these are generated by the java
                                    // compiler to handle type erasure).
                                    access
                                } else {
                                    // Otherwise, unset the synthetic flag so this method can be
                                    // processed by psi
                                    access and Opcodes.ACC_SYNTHETIC.inv()
                                }
                            // Visit the method with the class writer, using the new access flags
                            return super.visitMethod(
                                newAccess,
                                name,
                                descriptor,
                                signature,
                                exceptions
                            )
                        }
                    },
                    ClassReader.SKIP_CODE
                )
                outputStream.putNextEntry(ZipEntry(fileName))
                outputStream.write(classWriter.toByteArray())
            }
        }
        outputStream.flush()
        outputStream.close()
        return newJarFile
    }

    /**
     * Uses the [project] to load the psi for all classes previously found by [rewriteJar], and adds
     * members to the [codebase].
     *
     * This will not add any classes to the [codebase], but for any existing classes, it will add
     * any callables which are not already present in the class item in the codebase.
     */
    fun loadPsiFromProject(project: Project) {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        for (qualifiedName in qualifiedClassNames) {
            val psiClass = facade.findClass(qualifiedName, scope) ?: continue
            val classItem = codebase.findClass(qualifiedName) as? DefaultClassItem ?: continue
            // Find associated Kotlin metadata for the class. If there isn't any, this wasn't a
            // Kotlin source class and can be skipped.
            val metadataContainer = psiClass.getMetadataContainer() ?: continue
            addMethodsToClass(psiClass, classItem, metadataContainer)
        }

        // Process all multi-file classes. Each multi-file class is made up of parts from classes
        // generated from each file of the multi-file class. The class parts have the kotlin
        // metadata for the class members, while the multi-file class does not.
        for ((qualifiedName, classParts) in multiFileClassParts) {
            // Find the multi-file class itself in the codebase.
            val multiFileClassItem =
                codebase.findClass(qualifiedName) as? DefaultClassItem ?: continue
            for (classPartPath in classParts) {
                // Find the psi and metadata corresponding to this part of the multi-file class.
                val psiClassPart =
                    facade.findClass(classPartPath.replace("/", "."), scope) ?: continue
                val metadataContainer = psiClassPart.getMetadataContainer() ?: continue
                // Use the class part and metadata to add entries to the multi-file class item.
                addMethodsToClass(psiClassPart, multiFileClassItem, metadataContainer)
            }
        }
    }

    /** Adds to the [classItem] the methods from the [psiClass] which are not already present. */
    private fun addMethodsToClass(
        psiClass: PsiClass,
        classItem: DefaultClassItem,
        metadataContainer: KmDeclarationContainer,
    ) {
        val classTypeItemFactory = codebase.globalTypeItemFactory.from(classItem)
        // Kotlin source constructors get a constructor generated in the bytecode with
        // `kotlin.jvm.internal.DefaultConstructorMarker` as the final parameter. It only needs to
        // be tracked when there isn't already a matching constructor not including the
        // DefaultConstructorMarker parameter (e.g. if a constructor uses a value class type, the
        // constructor with DefaultConstructorMarker is the only version that will be tracked for
        // binary compatibility), so the other methods should be added first.
        val (withDefaultConstructorMarker, remainingMethods) =
            psiClass.methods.partition {
                it.isConstructor &&
                    it.psiParameters.lastOrNull()?.type?.canonicalText ==
                        "kotlin.jvm.internal.DefaultConstructorMarker"
            }

        for (psiMethod in remainingMethods) {
            addMethodToClass(
                psiMethod,
                psiClass,
                classItem,
                metadataContainer,
                classTypeItemFactory,
                hasDefaultConstructorMarker = false,
            )
        }
        for (psiMethod in withDefaultConstructorMarker) {
            // Skip DefaultConstructorMarker items without other parameters, as these are only not
            // already tracked through the source version for classes like companion objects where
            // we don't need to track a constructor.
            if (psiMethod.psiParameters.size == 1) continue
            addMethodToClass(
                psiMethod,
                psiClass,
                classItem,
                metadataContainer,
                classTypeItemFactory,
                hasDefaultConstructorMarker = true,
            )
        }
    }

    /**
     * If a method matching the [psiMethod] is not already present, adds a new [MethodItem]
     * generated from it to the [classItem].
     */
    private fun addMethodToClass(
        psiMethod: PsiMethod,
        psiClass: PsiClass,
        classItem: DefaultClassItem,
        metadataContainer: KmDeclarationContainer,
        classTypeItemFactory: PsiTypeItemFactory,
        hasDefaultConstructorMarker: Boolean,
    ) {
        // Skip processing certain methods based on name.
        if (skipTracking(psiMethod.name)) return
        // Only process visible APIs. Internal APIs will have the public modifier, which can
        // be corrected later using the Kotlin metadata.
        if (
            !psiMethod.modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                !psiMethod.modifierList.hasModifierProperty(PsiModifier.PROTECTED)
        )
            return

        // Don't re-add methods which are already present: find the items which might have
        // the same signature of this one, to compare by erased signature.
        val potentialMatches =
            potentialMatchingCallables(
                psiMethod,
                classItem,
                hasDefaultConstructorMarker,
            )
        // Right now, it would be complicated to get the real erased signature of the item
        // because that involves replacing variable types with their bounds. Get an
        // approximation by just dropping type arguments, to enable exiting early before
        // creating a codebase item if there's a definite match.
        // It would be nice to use the ClassUtil.getAsmMethodSignature helper here, but it
        // drops type variables completely.
        // For DefaultConstructorMarker, check for matches that don't include the extra parameter.
        val psiParametersForErasedSignature =
            if (hasDefaultConstructorMarker) {
                psiMethod.psiParameters.dropLast(1)
            } else {
                psiMethod.psiParameters
            }
        val semiErasedSignature =
            psiParametersForErasedSignature.joinToString {
                it.type.canonicalText.dropTypeArguments()
            }
        // Check if there's a signature match (technically, it would be possible to find a
        // false match here if a type variable that is in semiErasedSignature had the same
        // name as a primitive type used in one of the potential matches, but that shouldn't
        // be allowed).
        if (checkForSignatureMatch(semiErasedSignature, potentialMatches)) return

        // Create the item.
        val callableItem =
            if (psiMethod.isConstructor) {
                PsiConstructorItem.create(
                        codebase,
                        classItem,
                        psiMethod,
                        classTypeItemFactory,
                        targetLanguages = TargetLanguageSet.BYTECODE_ONLY,
                    )
                    .takeUnless {
                        // if a source constructor has an optional parameter, there are two
                        // DefaultConstructorMarker constructors generated in the bytecode: one with
                        // a DefaultConstructorMarker parameter added, and one with both an int and
                        // DefaultConstructorMarker parameter added. We don't need to track the
                        // version with the extra int parameter. However, it is also possible that
                        // the penultimate parameter of a DefaultConstructorMarker constructor is
                        // int just because the last parameter of a source constructor was int, so
                        // check if there is a constructor in the metadata matching the signature,
                        // if there isn't, this is an extra copy because the source version had an
                        // optional parameter.
                        hasDefaultConstructorMarker &&
                            (it.parameters()[it.parameters().size - 2].type() as? PrimitiveTypeItem)
                                ?.kind == PrimitiveTypeItem.Primitive.INT &&
                            it.findMatchingConstructor(metadataContainer) == null
                    }
            } else {
                PsiMethodItem.create(
                        codebase,
                        classItem,
                        psiMethod,
                        classTypeItemFactory,
                        targetLanguages = TargetLanguageSet.BYTECODE_ONLY,
                    )
                    .takeUnless {
                        // Skip enum synthetic methods since we don't track those.
                        it.isEnumSyntheticMethod()
                    }
            } ?: return

        // Double check that there isn't already a callable with the same signature. The
        // previous check didn't replace variable types with their bounds, so now that it is
        // easy to do that, make sure there isn't a matching signature.
        // For DefaultConstructorMarker, check for matches that don't include the extra parameter.
        if (potentialMatches.isNotEmpty()) {
            val parameterItemsForErasedSignature =
                if (hasDefaultConstructorMarker) {
                    callableItem.parameters().dropLast(1)
                } else {
                    callableItem.parameters()
                }
            val erasedSignature =
                parameterItemsForErasedSignature.joinToString { it.type().toErasedTypeString() }
            if (
                erasedSignature != semiErasedSignature &&
                    checkForSignatureMatch(erasedSignature, potentialMatches)
            )
                return
        }

        val metadataEntry = callableItem.findMetadataEntry(metadataContainer)
        // Reified inline functions can't be called from java, and for kotlin clients their usages
        // are all inlined in the binary (directly calling the binary version of the function will
        // be an error). So, it does not make sense to track the bytecode version for compatibility
        // since it will never be used.
        if (metadataEntry?.isReified == true) return
        // Propagate special property annotations to accessors.
        if (metadataEntry is MetadataEntry.AccessorMetadataEntry) {
            callableItem.propagateAnnotationsAsNeeded(metadataEntry.kmProperty, psiClass)
        }
        // Update the visibility of the item based on metadata, if needed.
        if (metadataEntry?.visibility == Visibility.INTERNAL) {
            callableItem.mutateModifiers { setVisibilityLevel(VisibilityLevel.INTERNAL) }
        }

        // Add the constructed callable.
        when (callableItem) {
            is ConstructorItem -> classItem.addConstructor(callableItem)
            is MethodItem -> classItem.addMethod(callableItem)
        }
    }

    /**
     * Whether an item with the given [methodName] should not be included in API tracking
     *
     * Value classes have equals, toString, and hashCode `-impl` methods which we don't track
     * because they are common to all value classes.
     *
     * The `$` is used for mangled names of internal elements (which are not @PublishedApi), and
     * delegate and lambda generated elements used by the class itself but not external callers, so
     * they don't need to be tracked.
     */
    private fun skipTracking(methodName: String) =
        methodName == "equals-impl" ||
            methodName == "equals-impl0" ||
            methodName == "toString-impl" ||
            methodName == "hashCode-impl" ||
            methodName.contains('$')

    /** Removes type arguments (anything between "<" and ">") from the type string. */
    private fun String.dropTypeArguments(): String =
        substringBefore("<") + substringAfterLast(">", "")

    /**
     * Finds callables of the [classItem] that might have the same signature as the [psiMethod]
     * (those that have the same name and parameter count), and returns them along with their erased
     * signatures.
     *
     * If [hasDefaultConstructorMarker] is true, the parameter count of the potential matches will
     * be one less than the parameter count of the [psiMethod].
     */
    private fun potentialMatchingCallables(
        psiMethod: PsiMethod,
        classItem: ClassItem,
        hasDefaultConstructorMarker: Boolean,
    ): List<Pair<CallableItem, String>> {
        val callables =
            if (psiMethod.isConstructor) {
                classItem.constructors()
            } else {
                classItem.methods()
            }
        val parameterCount =
            if (hasDefaultConstructorMarker) {
                // Account for the extra DefaultConstructorMarker parameter.
                psiMethod.psiParameters.size - 1
            } else {
                psiMethod.psiParameters.size
            }
        return callables
            .filter { callable ->
                callable.name() == psiMethod.name && callable.parameters().size == parameterCount
            }
            .map { callable ->
                callable to callable.parameters().joinToString { it.type().toErasedTypeString() }
            }
    }

    /**
     * Checks the [potentialMatches] (pairs of [CallableItem]s and their erased signatures) to see
     * if one of the signatures is the same as [erasedSignature].
     *
     * If it is, and the matching item was created as Kotlin-only and not reified, updates it to
     * include bytecode as a target language as well.
     *
     * Returns whether a match was found.
     */
    private fun checkForSignatureMatch(
        erasedSignature: String,
        potentialMatches: List<Pair<CallableItem, String>>,
    ): Boolean {
        val (callableItem, _) =
            potentialMatches.firstOrNull { (_, signature) -> signature == erasedSignature }
                ?: return false
        // If the item was created as Kotlin only but does exist in bytecode, update the target
        // language set. Exclude reified inline functions because even though these are present in
        // bytecode, there's an error if they're actually used.
        if (
            callableItem.targetLanguages == TargetLanguageSet.KOTLIN_ONLY &&
                callableItem.typeParameterList.none { it.isReified() }
        ) {
            callableItem.targetLanguages = TargetLanguageSet.NOT_JAVA
        }
        return true
    }

    /**
     * Loads the Kotlin metadata for the class and returns the [KmDeclarationContainer] where
     * information about the class members is stored.
     */
    private fun PsiClass.getMetadataContainer(): KmDeclarationContainer? {
        // Find a @Metadata annotation on the class, and convert to Kotlin metadata
        val metadataAnnotation =
            annotations.singleOrNull { it.qualifiedName == KOTLIN_METADATA } ?: return null
        val annotationItem = PsiAnnotationItem.create(codebase, metadataAnnotation) ?: return null
        val metadata = annotationItem.toMetadata()

        // Return the relevant metadata container. Uses `readLenient` instead of `readStrict` as the
        // only metadata needed is signatures and visibility, which according to the docs should be
        // safe to do on metadata generated by different compiler versions (`readStrict` errors if
        // the metadata was generated by a different compiler version).
        return when (val classMetadata = KotlinClassMetadata.readLenient(metadata)) {
            is KotlinClassMetadata.Class -> classMetadata.kmClass
            is KotlinClassMetadata.FileFacade -> classMetadata.kmPackage
            is KotlinClassMetadata.MultiFileClassPart -> classMetadata.kmPackage
            is KotlinClassMetadata.MultiFileClassFacade -> {
                // A multi-file class facade does not have the metadata for the class members. Each
                // class part corresponding to a source file contains the metadata for the members
                // from that file. Track what the parts of this multi-file class are, so they can be
                // processed later.
                qualifiedName?.let { multiFileClassParts[it] = classMetadata.partClassNames }
                null
            }
            is KotlinClassMetadata.SyntheticClass,
            is KotlinClassMetadata.Unknown -> null
        }
    }

    /** Converts the annotation (assumed to be @kotlin.Metadata) to [Metadata]. */
    private fun AnnotationItem.toMetadata(): Metadata {
        // Utilities for getting the necessary attribute values.
        fun getIntAttribute(name: String): Int? =
            (findAttribute(name)?.value as? IntValue)?.underlyingValue

        fun getStringAttribute(name: String): String? =
            (findAttribute(name)?.value as? StringValue)?.underlyingValue

        fun getIntArrayAttribute(name: String): IntArray? =
            findAttribute(name)
                ?.value
                ?.asFlatList()
                ?.mapNotNull { (it as? IntValue)?.underlyingValue }
                ?.toIntArray()

        fun AnnotationItem.getStringArrayAttribute(name: String): Array<String>? =
            findAttribute(name)
                ?.value
                ?.asFlatList()
                ?.mapNotNull { (it as? StringValue)?.underlyingValue }
                ?.toTypedArray()

        // Find all annotation values.
        val kind = getIntAttribute("k")
        val metadataVersion = getIntArrayAttribute("mv")
        val data1 = getStringArrayAttribute("d1")
        val data2 = getStringArrayAttribute("d2")
        val extraString = getStringAttribute("xs")
        val packageName = getStringAttribute("pn")
        val extraInt = getIntAttribute("xi")

        return Metadata(kind, metadataVersion, data1, data2, extraString, packageName, extraInt)
    }

    /**
     * Searches for a constructor in the metadata with the same signature as the [PsiCallableItem].
     *
     * If [hasDefaultConstructorMarker] is true, the DefaultConstructorMarker parameter is dropped
     * from the signature to find a match.
     */
    private fun PsiCallableItem.findMatchingConstructor(
        container: KmDeclarationContainer?,
    ): MetadataEntry.ConstructorMetadataEntry? {
        val internalDescriptor = internalDesc(voidConstructorTypes = true)
        return (container as? KmClass)
            ?.constructors
            ?.firstOrNull { it.signature?.descriptor == internalDescriptor }
            ?.let { MetadataEntry.ConstructorMetadataEntry(it) }
    }

    /**
     * Finds the metadata for the callable in the [container]. The metadata might be from a
     * constructor, function, or property accessor.
     */
    private fun PsiCallableItem.findMetadataEntry(
        container: KmDeclarationContainer?,
    ): MetadataEntry? {
        if (container == null) return null

        // For constructors and functions generated from constructor definitions, check if there
        // is a constructor with the right signature.
        return if (isConstructor() || name() == "constructor-impl") {
            findMatchingConstructor(container)
        } else {
            val expectedDescriptor = internalDesc(voidConstructorTypes = true)
            // Cut off the mangled part of the name, if there is one.
            // val simpleName = name().substringBefore('-')
            // Check for a function with the right signature.
            container.functions
                .firstOrNull { it.signature.matches(name(), expectedDescriptor) }
                ?.let { MetadataEntry.FunctionMetadataEntry(it) }
                // No matching function, check if this is a property accessor.
                ?: container.properties.firstNotNullOfOrNull {
                    if (it.getterSignature.matches(name(), expectedDescriptor)) {
                        MetadataEntry.AccessorMetadataEntry(it.getter, it)
                    } else if (it.setterSignature.matches(name(), expectedDescriptor)) {
                        MetadataEntry.AccessorMetadataEntry(it.setter!!, it)
                    } else {
                        null
                    }
                }
        }
    }

    /** Whether the signature exists and has the [expectedName] and [expectedDescriptor]. */
    private fun JvmMethodSignature?.matches(
        expectedName: String,
        expectedDescriptor: String,
    ): Boolean {
        return this != null && expectedName == name && descriptor == expectedDescriptor
    }

    /**
     * If the [kmProperty] was annotated in source, propagates some special annotations to the
     * callable item.
     *
     * This includes [PublishedApi], annotations meta-annotated with [RequiresOptIn], and
     * deprecation status.
     */
    private fun PsiCallableItem.propagateAnnotationsAsNeeded(
        kmProperty: KmProperty,
        psiClass: PsiClass,
    ) {
        if (!kmProperty.hasAnnotations) return

        // The annotations on a property in source end up in bytecode on a synthetic method
        // generated to track the annotations. Find that method in the psi class.
        val annotationMethodSignature = kmProperty.syntheticMethodForAnnotations ?: return
        // For an interface, the annotation method will be in a nested DefaultImpls class.
        val classForAnnotationMethod =
            if (psiClass.isInterface) {
                psiClass.innerClasses.singleOrNull { it.name == "DefaultImpls" }
            } else {
                psiClass
            } ?: return
        val annotationMethod =
            classForAnnotationMethod.methods.singleOrNull {
                it.name == annotationMethodSignature.name
            } ?: return

        if (kmProperty.visibility == Visibility.INTERNAL) {
            // Check if the method is @PublishedApi, propagate it to the accessor method if so.
            annotationMethod.annotations
                .firstOrNull { it.qualifiedName == "kotlin.PublishedApi" }
                ?.let { publishedAnnotation ->
                    val annotationItem = PsiAnnotationItem.create(codebase, publishedAnnotation)
                    mutateModifiers { addAnnotation(annotationItem) }
                }
        }

        // Propagate deprecation from properties to accessors.
        if (
            !modifiers.isDeprecated() &&
                annotationMethod.annotations.any { it.hasQualifiedName(KOTLIN_DEPRECATED) }
        ) {
            mutateModifiers { setDeprecated(true) }
        }

        for (annotationEntry in annotationMethod.annotations) {
            val annotationClass = annotationEntry.resolveAnnotationType() ?: continue
            // Special case for RequiresOptIn-annotated annotations: when these are applied
            // to a property, they are implicitly propagated to the getter and setter
            // (if present) for Kotlin clients. Match Kotlin compiler behavior by propagating.
            if (annotationClass.hasAnnotation("kotlin.RequiresOptIn")) {
                val annotationItem = PsiAnnotationItem.create(codebase, annotationEntry)
                mutateModifiers { addAnnotation(annotationItem) }
            }
        }
    }

    /**
     * Wrapper for function, constructor, or property kotlin metadata, because the Km type do not
     * have a shared parent class.
     */
    private sealed interface MetadataEntry {
        /** Source visibility of the declaration. */
        val visibility: Visibility

        /** Whether the definition has a reified type parameter. */
        val isReified: Boolean

        /** Wrapper for aa [KmFunction]. */
        class FunctionMetadataEntry(private val kmFunction: KmFunction) : MetadataEntry {
            override val visibility: Visibility
                get() = kmFunction.visibility

            override val isReified: Boolean
                get() = kmFunction.typeParameters.any { it.isReified }
        }

        /** Wrapper for aa [KmConstructor]. */
        class ConstructorMetadataEntry(private val kmConstructor: KmConstructor) : MetadataEntry {
            override val visibility: Visibility
                get() = kmConstructor.visibility

            override val isReified = false
        }

        /** Wrapper for a [KmPropertyAccessorAttributes] from a [KmProperty]. */
        class AccessorMetadataEntry(
            private val kmAccessor: KmPropertyAccessorAttributes,
            val kmProperty: KmProperty
        ) : MetadataEntry {
            override val visibility: Visibility
                get() = kmAccessor.visibility

            override val isReified: Boolean
                get() = kmProperty.typeParameters.any { it.isReified }
        }
    }
}
