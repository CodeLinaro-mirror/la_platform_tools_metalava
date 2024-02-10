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
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isRetention
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SyntheticElement
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getParentOfType

open class PsiClassItem
internal constructor(
    codebase: PsiBasedCodebase,
    val psiClass: PsiClass,
    private val name: String,
    private val fullName: String,
    private val qualifiedName: String,
    private val hasImplicitDefaultConstructor: Boolean,
    override val classKind: ClassKind,
    private val typeParameterList: TypeParameterList,
    modifiers: PsiModifierItem,
    documentation: String,
    /** True if this class is from the class path (dependencies). Exposed in [isFromClassPath]. */
    private val fromClassPath: Boolean
) :
    PsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiClass
    ),
    ClassItem {

    override var emit: Boolean = !modifiers.isExpect()

    lateinit var containingPackage: PsiPackageItem

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage

    override fun simpleName(): String = name

    override fun fullName(): String = fullName

    override fun qualifiedName(): String = qualifiedName

    override fun isDefined(): Boolean = codebase.unsupported()

    override fun psi() = psiClass

    override fun isFromClassPath(): Boolean = fromClassPath

    override fun hasImplicitDefaultConstructor(): Boolean = hasImplicitDefaultConstructor

    private var superClass: ClassItem? = null
    private var superClassType: ClassTypeItem? = null

    override fun superClass(): ClassItem? = superClass

    override fun superClassType(): ClassTypeItem? = superClassType

    override var stubConstructor: ConstructorItem? = null
    override var artifact: String? = null

    private var containingClass: PsiClassItem? = null

    override fun containingClass(): PsiClassItem? = containingClass

    override var hasPrivateConstructor: Boolean = false

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        this.interfaceTypes = interfaceTypes
    }

    private var allInterfaces: List<ClassItem>? = null

    override fun allInterfaces(): Sequence<ClassItem> {
        if (allInterfaces == null) {
            val classes = mutableSetOf<PsiClass>()
            var curr: PsiClass? = psiClass
            while (curr != null) {
                if (curr.isInterface && !classes.contains(curr)) {
                    classes.add(curr)
                }
                addInterfaces(classes, curr.interfaces)
                curr = curr.superClass
            }
            val result = mutableListOf<ClassItem>()
            for (cls in classes) {
                val item = codebase.findOrCreateClass(cls)
                result.add(item)
            }

            allInterfaces = result
        }

        return allInterfaces!!.asSequence()
    }

    private fun addInterfaces(result: MutableSet<PsiClass>, interfaces: Array<out PsiClass>) {
        for (itf in interfaces) {
            if (itf.isInterface && !result.contains(itf)) {
                result.add(itf)
                addInterfaces(result, itf.interfaces)
                val superClass = itf.superClass
                if (superClass != null) {
                    addInterfaces(result, arrayOf(superClass))
                }
            }
        }
    }

    private lateinit var innerClasses: List<PsiClassItem>
    private lateinit var interfaceTypes: List<ClassTypeItem>
    private lateinit var constructors: List<PsiConstructorItem>
    private lateinit var methods: MutableList<PsiMethodItem>
    private lateinit var properties: List<PsiPropertyItem>
    private lateinit var fields: List<FieldItem>

    /**
     * If this item was created by filtering down a different codebase, this temporarily points to
     * the original item during construction. This is used to let us initialize for example throws
     * lists later, when all classes in the codebase have been initialized.
     */
    internal var source: PsiClassItem? = null

    override fun innerClasses(): List<PsiClassItem> = innerClasses

    override fun constructors(): List<ConstructorItem> = constructors

    override fun methods(): List<PsiMethodItem> = methods

    override fun properties(): List<PropertyItem> = properties

    override fun fields(): List<FieldItem> = fields

    final override var primaryConstructor: PsiConstructorItem? = null
        private set

    override fun type(): ClassTypeItem =
        codebase.getType(codebase.getClassType(psiClass)) as ClassTypeItem

    override fun hasTypeVariables(): Boolean = psiClass.hasTypeParameters()

    override fun typeParameterList() = typeParameterList

    override fun getSourceFile(): SourceFile? {
        if (isInnerClass()) {
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

    override fun finishInitialization() {
        super.finishInitialization()

        for (method in methods) {
            method.finishInitialization()
        }
        for (method in constructors) {
            method.finishInitialization()
        }
        for (field in fields) {
            // There may be non-Psi fields here later (thanks to addField) but not during
            // construction
            (field as PsiFieldItem).finishInitialization()
        }
        for (inner in innerClasses) {
            inner.finishInitialization()
        }

        // Delay initializing super classes and implemented interfaces for all inner classes: they
        // may refer
        // to *other* inner classes in this class, which would lead to an attempt to construct
        // recursively. Instead, we wait until all the inner classes have been constructed, and at
        // the very end, initialize super classes and interfaces recursively.
        if (psiClass.containingClass == null) {
            initializeSuperClasses()
        }
    }

    private fun initializeSuperClasses() {
        val isInterface = isInterface()

        // Get the interfaces from the appropriate list.
        val interfaces =
            if (isInterface || isAnnotationType()) {
                // An interface uses "extends <interfaces>", either explicitly for normal interfaces
                // or implicitly for annotations.
                psiClass.extendsListTypes
            } else {
                // A class uses "extends <interfaces>".
                psiClass.implementsListTypes
            }

        // Map them to PsiTypeItems.
        val interfaceTypes =
            interfaces.map {
                codebase.getSuperType(it).also { type ->
                    // ensure that we initialize classes eagerly too, so that they're registered etc
                    type.asClass()
                }
            }
        setInterfaceTypes(interfaceTypes)

        if (!isInterface) {
            // Set the super class type for classes
            val superClassPsiType = psiClass.superClassType as? PsiType
            superClassPsiType?.let { superType ->
                this.superClassType = codebase.getSuperType(superType)
                this.superClass = this.superClassType?.asClass()
            }
        }

        for (inner in innerClasses) {
            inner.initializeSuperClasses()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is ClassItem && qualifiedName == other.qualifiedName()
    }

    /** Creates a constructor in this class */
    override fun createDefaultConstructor(): ConstructorItem {
        return PsiConstructorItem.createDefaultConstructor(codebase, this, psiClass)
    }

    override fun inheritMethodFromNonApiAncestor(template: MethodItem): MethodItem {
        val method = template as PsiMethodItem
        val newMethod = PsiMethodItem.create(codebase, this, method)

        newMethod.setThrowsTypes(method.throwsTypes())
        newMethod.finishInitialization()

        // Remember which class this method was copied from.
        newMethod.inheritedFrom = template.containingClass()

        return newMethod
    }

    override fun addMethod(method: MethodItem) {
        methods.add(method as PsiMethodItem)
    }

    private var retention: AnnotationRetention? = null

    override fun getRetention(): AnnotationRetention {
        retention?.let {
            return it
        }

        if (!isAnnotationType()) {
            error("getRetention() should only be called on annotation classes")
        }

        retention = ClassItem.findRetention(this)
        return retention!!
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    override fun toString(): String = "class ${qualifiedName()}"

    companion object {
        private fun hasExplicitRetention(
            modifiers: PsiModifierItem,
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

        fun create(
            codebase: PsiBasedCodebase,
            psiClass: PsiClass,
            fromClassPath: Boolean
        ): PsiClassItem {
            if (psiClass is PsiTypeParameter) {
                error(
                    "Must not be called with PsiTypeParameter; use PsiTypeParameterItem.create(...) instead"
                )
            }
            val simpleName = psiClass.name!!
            val fullName = computeFullClassName(psiClass)
            val qualifiedName = psiClass.qualifiedName ?: simpleName
            val hasImplicitDefaultConstructor = hasImplicitDefaultConstructor(psiClass)
            val classKind = getClassKind(psiClass)

            val commentText = javadoc(psiClass)
            val modifiers = PsiModifierItem.create(codebase, psiClass, commentText)

            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val typeParameterList = PsiTypeParameterList.create(codebase, psiClass)

            val item =
                PsiClassItem(
                    codebase = codebase,
                    psiClass = psiClass,
                    name = simpleName,
                    fullName = fullName,
                    qualifiedName = qualifiedName,
                    classKind = classKind,
                    typeParameterList = typeParameterList,
                    hasImplicitDefaultConstructor = hasImplicitDefaultConstructor,
                    documentation = commentText,
                    modifiers = modifiers,
                    fromClassPath = fromClassPath
                )
            item.modifiers.setOwner(item)

            // Register this class now so it's present when calling Codebase.findOrCreateClass for
            // inner classes below
            codebase.registerClass(item)

            // Construct the children
            val psiMethods = psiClass.methods
            val methods: MutableList<PsiMethodItem> = ArrayList(psiMethods.size)
            val isKotlin = isKotlin(psiClass)

            if (
                classKind == ClassKind.ANNOTATION_TYPE &&
                    !hasExplicitRetention(modifiers, psiClass, isKotlin)
            ) {
                // By policy, include explicit retention policy annotation if missing
                val defaultRetentionPolicy = AnnotationRetention.getDefault(isKotlin)
                modifiers.addAnnotation(
                    codebase.createAnnotation(
                        buildString {
                            append('@')
                            append(java.lang.annotation.Retention::class.qualifiedName)
                            append('(')
                            append(java.lang.annotation.RetentionPolicy::class.qualifiedName)
                            append('.')
                            append(defaultRetentionPolicy.name)
                            append(')')
                        },
                        item,
                    )
                )
            }

            // create methods
            val constructors: MutableList<PsiConstructorItem> = ArrayList(5)
            var hasConstructorWithOnlyOptionalArgs = false
            var noArgConstructor: PsiConstructorItem? = null
            for (psiMethod in psiMethods) {
                if (psiMethod.isConstructor) {
                    val constructor = PsiConstructorItem.create(codebase, item, psiMethod)
                    // After KT-13495, "all constructors of `sealed` classes now have `protected`
                    // visibility by default," and (S|U)LC follows that (hence the same in UAST).
                    // However, that change was made to allow more flexible class hierarchy and
                    // nesting. If they're compiled to JVM bytecode, sealed class's ctor is still
                    // technically `private` to block instantiation from outside class hierarchy.
                    // Another synthetic constructor, along with an internal ctor marker, is added
                    // for subclasses of a sealed class. Therefore, from Metalava's perspective,
                    // it is not necessary to track such semantically protected ctor. Here we force
                    // set the visibility to `private` back to ignore it during signature writing.
                    if (item.modifiers.isSealed()) {
                        constructor.modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                    }
                    if (constructor.areAllParametersOptional()) {
                        if (constructor.parameters().isNotEmpty()) {
                            constructors.add(constructor)
                            // uast reported a constructor having only optional arguments, so if we
                            // later find an explicit no-arg constructor, we can skip it because
                            // its existence is implied
                            hasConstructorWithOnlyOptionalArgs = true
                        } else {
                            noArgConstructor = constructor
                        }
                    } else {
                        constructors.add(constructor)
                    }
                } else if (classKind == ClassKind.ENUM && psiMethod is SyntheticElement) {
                    // skip
                } else {
                    val method = PsiMethodItem.create(codebase, item, psiMethod)
                    methods.add(method)
                }
            }

            // Add the no-arg constructor back in if no constructors have only optional arguments
            // or if an all-optional constructor created it as part of @JvmOverloads
            if (
                noArgConstructor != null &&
                    (!hasConstructorWithOnlyOptionalArgs ||
                        noArgConstructor.modifiers.isAnnotatedWith("kotlin.jvm.JvmOverloads"))
            ) {
                constructors.add(noArgConstructor)
            }

            // Note that this is dependent on the constructor filtering above. UAST sometimes
            // reports duplicate primary constructors, e.g.: the implicit no-arg constructor
            constructors.singleOrNull { it.isPrimary }?.let { item.primaryConstructor = it }

            if (hasImplicitDefaultConstructor) {
                assert(constructors.isEmpty())
                constructors.add(
                    PsiConstructorItem.createDefaultConstructor(codebase, item, psiClass)
                )
            }

            val fields: MutableList<PsiFieldItem> = mutableListOf()
            val psiFields = psiClass.fields
            if (psiFields.isNotEmpty()) {
                psiFields.asSequence().mapTo(fields) { PsiFieldItem.create(codebase, item, it) }
            }

            if (classKind == ClassKind.INTERFACE) {
                // All members are implicitly public, fields are implicitly static, non-static
                // methods are abstract
                // (except in Java 1.9, where they can be private
                for (method in methods) {
                    if (!method.isPrivate) {
                        method.mutableModifiers().setVisibilityLevel(VisibilityLevel.PUBLIC)
                    }
                }
                for (method in fields) {
                    val m = method.mutableModifiers()
                    m.setVisibilityLevel(VisibilityLevel.PUBLIC)
                    m.setStatic(true)
                }
            }

            item.constructors = constructors
            item.methods = methods
            item.fields = fields

            item.properties = emptyList()

            if (isKotlin && methods.isNotEmpty()) {
                val getters = mutableMapOf<String, PsiMethodItem>()
                val setters = mutableMapOf<String, PsiMethodItem>()
                val backingFields = fields.associateBy { it.name() }
                val constructorParameters =
                    item.primaryConstructor
                        ?.parameters()
                        ?.filter { (it.sourcePsi as? KtParameter)?.isPropertyParameter() ?: false }
                        ?.associateBy { it.name() }
                        .orEmpty()

                for (method in methods) {
                    if (method.isKotlinProperty()) {
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

                val properties = mutableListOf<PsiPropertyItem>()
                for ((name, getter) in getters) {
                    val type = getter.returnType() as? PsiTypeItem ?: continue
                    properties +=
                        PsiPropertyItem.create(
                            codebase = codebase,
                            containingClass = item,
                            name = name,
                            type = type,
                            getter = getter,
                            setter = setters[name],
                            constructorParameter = constructorParameters[name],
                            backingField = backingFields[name]
                        )
                }
                item.properties = properties
            }

            val psiInnerClasses = psiClass.innerClasses
            item.innerClasses =
                if (psiInnerClasses.isEmpty()) {
                    emptyList()
                } else {
                    val result =
                        psiInnerClasses
                            .asSequence()
                            .map {
                                val inner = codebase.findOrCreateClass(it)
                                inner.containingClass = item
                                inner
                            }
                            .toMutableList()
                    result
                }

            return item
        }

        internal fun getClassKind(psiClass: PsiClass): ClassKind {
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
         * but for an inner class it includes all the outer classes
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
            if (
                constructors.isEmpty() &&
                    !psiClass.isInterface &&
                    !psiClass.isAnnotationType &&
                    !psiClass.isEnum
            ) {
                if (PsiUtil.hasDefaultConstructor(psiClass)) {
                    return true
                }

                // The above method isn't always right; for example, for the
                // ContactsContract.Presence class
                // in the framework, which looks like this:
                //    @Deprecated
                //    public static final class Presence extends StatusUpdates {
                //    }
                // javac makes a default constructor:
                //    public final class android.provider.ContactsContract$Presence extends
                // android.provider.ContactsContract$StatusUpdates {
                //        public android.provider.ContactsContract$Presence();
                //    }
                // but the above method returns false. So add some of our own heuristics:
                if (
                    psiClass.hasModifierProperty(PsiModifier.FINAL) &&
                        !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        psiClass.hasModifierProperty(PsiModifier.PUBLIC)
                ) {
                    return true
                }
            }

            return false
        }
    }
}
