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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.addDefaultRetentionPolicyAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isRetention
import com.android.tools.metalava.model.item.DefaultClassItem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getParentOfType

internal class PsiClassItem
internal constructor(
    override val codebase: PsiBasedCodebase,
    val psiClass: PsiClass,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    classKind: ClassKind,
    containingClass: ClassItem?,
    containingPackage: PackageItem,
    qualifiedName: String,
    simpleName: String,
    fullName: String,
    typeParameterList: TypeParameterList,
    /** True if this class is from the class path (dependencies). Exposed in [isFromClassPath]. */
    isFromClassPath: Boolean,
    superClassType: ClassTypeItem?,
    interfaceTypes: List<ClassTypeItem>
) :
    DefaultClassItem(
        codebase = codebase,
        fileLocation = PsiFileLocation.fromPsiElement(psiClass),
        itemLanguage = psiClass.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        source = null,
        classKind = classKind,
        containingClass = containingClass,
        containingPackage = containingPackage,
        qualifiedName = qualifiedName,
        simpleName = simpleName,
        fullName = fullName,
        typeParameterList = typeParameterList,
        isFromClassPath = isFromClassPath,
        superClassType = superClassType,
        interfaceTypes = interfaceTypes,
    ),
    ClassItem,
    PsiItem {

    override fun psi() = psiClass

    override var primaryConstructor: ConstructorItem? = null
        private set

    override fun createClassTypeItemForThis() =
        codebase.globalTypeItemFactory.getClassTypeForClass(this)

    override fun getSourceFile(): SourceFile? {
        if (isNestedClass()) {
            return null
        }

        val containingFile = psiClass.containingFile ?: return null
        if (containingFile is PsiCompiledFile) {
            return null
        }

        val uFile =
            if (psiClass is UClass) {
                psiClass.getParentOfType(UFile::class.java)
            } else {
                null
            }

        return PsiSourceFile(codebase, containingFile, uFile)
    }

    /** Creates a constructor in this class */
    override fun createDefaultConstructor(visibility: VisibilityLevel): PsiConstructorItem {
        return PsiConstructorItem.createDefaultConstructor(codebase, this, psiClass, visibility)
    }

    override fun isFileFacade(): Boolean {
        return psiClass.isKotlin() &&
            psiClass is UClass &&
            psiClass.javaPsi is KtLightClassForFacade
    }

    companion object {
        private fun hasExplicitRetention(
            modifiers: BaseModifierList,
            psiClass: PsiClass,
            isKotlin: Boolean
        ): Boolean {
            if (modifiers.hasAnnotation(AnnotationItem::isRetention)) {
                return true
            }
            if (isKotlin && psiClass is UClass) {
                // In Kotlin some annotations show up on the Java facade only; for example,
                // a @DslMarker annotation will imply a runtime annotation which is present
                // in the java facade, not in the source list of annotations
                val modifierList = psiClass.modifierList
                if (
                    modifierList != null &&
                        modifierList.annotations.any { isRetention(it.qualifiedName) }
                ) {
                    return true
                }
            }
            return false
        }

        internal fun create(
            codebase: PsiBasedCodebase,
            psiClass: PsiClass,
            containingClassItem: PsiClassItem?,
            containingPackage: PackageItem,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
            fromClassPath: Boolean,
        ): PsiClassItem {
            if (psiClass is PsiTypeParameter) {
                error(
                    "Must not be called with PsiTypeParameter; use PsiTypeParameterItem.create(...) instead"
                )
            }
            val simpleName = psiClass.name!!
            val fullName = computeFullClassName(psiClass)
            val qualifiedName = psiClass.qualifiedName ?: simpleName
            val classKind = getClassKind(psiClass)

            val modifiers = PsiModifierItem.create(codebase, psiClass)

            val isKotlin = psiClass.isKotlin()

            if (
                classKind == ClassKind.ANNOTATION_TYPE &&
                    !hasExplicitRetention(modifiers, psiClass, isKotlin)
            ) {
                modifiers.addDefaultRetentionPolicyAnnotation(codebase, isKotlin)
            }

            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, classTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "class $qualifiedName",
                    psiClass
                )

            val (superClassType, interfaceTypes) =
                computeSuperTypes(psiClass, classKind, classTypeItemFactory)

            val classItem =
                PsiClassItem(
                    codebase = codebase,
                    psiClass = psiClass,
                    modifiers = modifiers,
                    documentationFactory = PsiItemDocumentation.factory(psiClass, codebase),
                    classKind = classKind,
                    containingClass = containingClassItem,
                    containingPackage = containingPackage,
                    qualifiedName = qualifiedName,
                    simpleName = simpleName,
                    fullName = fullName,
                    typeParameterList = typeParameterList,
                    isFromClassPath = fromClassPath,
                    superClassType = superClassType,
                    interfaceTypes = interfaceTypes,
                )

            // Construct the children
            val psiMethods = psiClass.methods

            // create methods
            for (psiMethod in psiMethods) {
                if (psiMethod.isConstructor) {
                    val constructor =
                        PsiConstructorItem.create(
                            codebase,
                            classItem,
                            psiMethod,
                            classTypeItemFactory,
                        )
                    classItem.addConstructor(constructor)
                } else {
                    val method =
                        PsiMethodItem.create(codebase, classItem, psiMethod, classTypeItemFactory)
                    if (!method.isEnumSyntheticMethod()) {
                        classItem.addMethod(method)
                    }
                }
            }

            // Note that this is dependent on the constructor filtering above. UAST sometimes
            // reports duplicate primary constructors, e.g.: the implicit no-arg constructor
            val constructors = classItem.constructors()
            constructors.singleOrNull { it.isPrimary }?.let { classItem.primaryConstructor = it }

            val hasImplicitDefaultConstructor = hasImplicitDefaultConstructor(psiClass)
            if (hasImplicitDefaultConstructor) {
                assert(constructors.isEmpty())
                classItem.addConstructor(classItem.createDefaultConstructor())
            }

            val psiFields = psiClass.fields
            if (psiFields.isNotEmpty()) {
                for (psiField in psiFields) {
                    val fieldItem =
                        PsiFieldItem.create(codebase, classItem, psiField, classTypeItemFactory)
                    classItem.addField(fieldItem)
                }
            }

            val methods = classItem.methods()
            if (isKotlin && methods.isNotEmpty()) {
                val getters = mutableMapOf<String, PsiMethodItem>()
                val setters = mutableMapOf<String, PsiMethodItem>()
                val backingFields =
                    classItem.fields().associateBy({ it.name() }) { it as PsiFieldItem }
                val constructorParameters =
                    classItem.primaryConstructor
                        ?.parameters()
                        ?.map { it as PsiParameterItem }
                        ?.filter { (it.sourcePsi as? KtParameter)?.isPropertyParameter() ?: false }
                        ?.associateBy { it.name() }
                        .orEmpty()

                for (method in methods) {
                    if (method.isKotlinProperty()) {
                        method as PsiMethodItem
                        val name =
                            when (val sourcePsi = method.sourcePsi) {
                                is KtProperty -> sourcePsi.name
                                is KtPropertyAccessor -> sourcePsi.property.name
                                is KtParameter -> sourcePsi.name
                                else -> null
                            }
                                ?: continue

                        if (method.parameters().isEmpty()) {
                            if (!method.name().startsWith("component")) {
                                getters[name] = method
                            }
                        } else {
                            setters[name] = method
                        }
                    }
                }

                for ((name, getter) in getters) {
                    val type = getter.returnType() as? PsiTypeItem ?: continue
                    val propertyItem =
                        PsiPropertyItem.create(
                            codebase = codebase,
                            containingClass = classItem,
                            name = name,
                            type = type,
                            getter = getter,
                            setter = setters[name],
                            constructorParameter = constructorParameters[name],
                            backingField = backingFields[name]
                        )
                    classItem.addProperty(propertyItem)
                }
            }

            // This actually gets all nested classes not just inner, i.e. non-static nested,
            // classes.
            val psiNestedClasses = psiClass.innerClasses
            for (psiNestedClass in psiNestedClasses) {
                codebase.createClass(
                    psiClass = psiNestedClass,
                    containingClassItem = classItem,
                    enclosingClassTypeItemFactory = classTypeItemFactory,
                )
            }

            return classItem
        }

        /**
         * Compute the super types for the class.
         *
         * Returns a pair of the optional super class type and the possibly empty list of interface
         * types.
         */
        private fun computeSuperTypes(
            psiClass: PsiClass,
            classKind: ClassKind,
            classTypeItemFactory: PsiTypeItemFactory
        ): Pair<ClassTypeItem?, List<ClassTypeItem>> {

            // A map from the qualified type name to the corresponding [KtTypeReference]. This is
            // empty for non-Kotlin code, otherwise it maps from the qualified type name of a
            // super type to the associated [KtTypeReference]. The qualified name is used to map
            // between them because Kotlin does not differentiate between `implements` and `extends`
            // lists and just has one super type list. The qualified name is safe because a class
            // cannot implement/extend the same generic type multiple times with different type
            // arguments so the qualified name should be unique among the super type list.
            // The [KtTypeReference] is needed to access the type nullability of the generic type
            // arguments.
            val qualifiedNameToKt =
                if (psiClass is UClass) {
                    psiClass.uastSuperTypes.associateBy({ it.getQualifiedName() }) {
                        it.sourcePsi as KtTypeReference
                    }
                } else emptyMap()

            // Get the [KtTypeReference], if any, associated with ths [PsiType] which must be a
            // [PsiClassType] as that is the only type allowed in an extends/implements list.
            fun PsiType.ktTypeReference(): KtTypeReference? {
                val qualifiedName = (this as PsiClassType).computeQualifiedName()
                return qualifiedNameToKt[qualifiedName]
            }

            // Construct the super class type if needed and available.
            val superClassType =
                if (classKind != ClassKind.INTERFACE) {
                    val superClassPsiType = psiClass.superClassType as? PsiType
                    superClassPsiType?.let { superClassType ->
                        val ktTypeRef = superClassType.ktTypeReference()
                        classTypeItemFactory.getSuperClassType(
                            PsiTypeInfo(superClassType, ktTypeRef)
                        )
                    }
                } else null

            // Get the interfaces from the appropriate list.
            val interfaces =
                if (classKind == ClassKind.INTERFACE || classKind == ClassKind.ANNOTATION_TYPE) {
                    // An interface uses "extends <interfaces>", either explicitly for normal
                    // interfaces or implicitly for annotations.
                    psiClass.extendsListTypes
                } else {
                    // A class uses "extends <interfaces>".
                    psiClass.implementsListTypes
                }

            // Map them to PsiTypeItems.
            val interfaceTypes =
                interfaces.map { interfaceType ->
                    val ktTypeRef = interfaceType.ktTypeReference()
                    classTypeItemFactory.getInterfaceType(PsiTypeInfo(interfaceType, ktTypeRef))
                }
            return Pair(superClassType, interfaceTypes)
        }

        private fun getClassKind(psiClass: PsiClass): ClassKind {
            return when {
                psiClass.isAnnotationType -> ClassKind.ANNOTATION_TYPE
                psiClass.isInterface -> ClassKind.INTERFACE
                psiClass.isEnum -> ClassKind.ENUM
                psiClass is PsiTypeParameter ->
                    error("Must not call this with a PsiTypeParameter - $psiClass")
                else -> ClassKind.CLASS
            }
        }

        /**
         * Computes the "full" class name; this is not the qualified class name (e.g. with package)
         * but for a nested class it includes all the outer classes
         */
        fun computeFullClassName(cls: PsiClass): String {
            if (cls.containingClass == null) {
                val name = cls.name
                return name!!
            } else {
                val list = mutableListOf<String>()
                var curr: PsiClass? = cls
                while (curr != null) {
                    val name = curr.name
                    curr =
                        if (name != null) {
                            list.add(name)
                            curr.containingClass
                        } else {
                            break
                        }
                }
                return list.asReversed().joinToString(separator = ".") { it }
            }
        }

        private fun hasImplicitDefaultConstructor(psiClass: PsiClass): Boolean {
            if (psiClass.name?.startsWith("-") == true) {
                // Deliberately hidden; see examples like
                //     @file:JvmName("-ViewModelExtensions") // Hide from Java sources in the IDE.
                return false
            }

            if (psiClass is UClass && psiClass.sourcePsi == null) {
                // Top level kt classes (FooKt for Foo.kt) do not have implicit default constructor
                return false
            }

            val constructors = psiClass.constructors
            return constructors.isEmpty() &&
                !psiClass.isInterface &&
                !psiClass.isAnnotationType &&
                !psiClass.isEnum
        }
    }
}
