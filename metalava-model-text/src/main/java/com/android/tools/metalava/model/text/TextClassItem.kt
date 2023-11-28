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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListOwner
import java.util.function.Predicate

open class TextClassItem(
    override val codebase: TextCodebase,
    position: SourcePositionInfo = SourcePositionInfo.UNKNOWN,
    modifiers: DefaultModifierList,
    private var isInterface: Boolean = false,
    private var isEnum: Boolean = false,
    internal var isAnnotation: Boolean = false,
    val qualifiedName: String = "",
    val qualifiedTypeName: String = qualifiedName,
    var name: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
    val annotations: List<String>? = null,
    val typeParameterList: TypeParameterList = TypeParameterList.NONE
) :
    TextItem(codebase = codebase, position = position, modifiers = modifiers),
    ClassItem,
    TypeParameterListOwner {

    init {
        @Suppress("LeakingThis") modifiers.setOwner(this)
        if (typeParameterList is TextTypeParameterList) {
            @Suppress("LeakingThis") typeParameterList.setOwner(this)
        }
    }

    override val isTypeParameter: Boolean = false

    override var artifact: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassItem) return false

        return qualifiedName == other.qualifiedName()
    }

    override fun hashCode(): Int {
        return qualifiedName.hashCode()
    }

    override fun interfaceTypes(): List<TypeItem> = interfaceTypes

    override fun allInterfaces(): Sequence<ClassItem> {
        return interfaceTypes.asSequence().map { it.asClass() }.filterNotNull()
    }

    private var innerClasses: MutableList<ClassItem> = mutableListOf()

    override var stubConstructor: ConstructorItem? = null

    override var hasPrivateConstructor: Boolean = false

    override fun innerClasses(): List<ClassItem> = innerClasses

    override fun hasImplicitDefaultConstructor(): Boolean {
        return false
    }

    override fun isInterface(): Boolean = isInterface

    override fun isAnnotationType(): Boolean = isAnnotation

    override fun isEnum(): Boolean = isEnum

    var containingClass: ClassItem? = null

    override fun containingClass(): ClassItem? = containingClass

    private var containingPackage: PackageItem? = null

    fun setContainingPackage(containingPackage: TextPackageItem) {
        this.containingPackage = containingPackage
    }

    fun setIsAnnotationType(isAnnotation: Boolean) {
        this.isAnnotation = isAnnotation
    }

    fun setIsEnum(isEnum: Boolean) {
        this.isEnum = isEnum
    }

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage ?: error(this)

    override fun hasTypeVariables(): Boolean = typeParameterList.typeParameterCount() > 0

    override fun typeParameterList(): TypeParameterList = typeParameterList

    override fun typeParameterListOwnerParent(): TypeParameterListOwner? {
        return containingClass as? TypeParameterListOwner
    }

    override fun resolveParameter(variable: String): TypeParameterItem? {
        if (hasTypeVariables()) {
            for (t in typeParameterList().typeParameters()) {
                if (t.simpleName() == variable) {
                    return t
                }
            }
        }

        return null
    }

    private var superClass: ClassItem? = null
    private var superClassType: TypeItem? = null

    override fun superClass(): ClassItem? = superClass

    override fun superClassType(): TypeItem? = superClassType

    override fun setSuperClass(superClass: ClassItem?, superClassType: TypeItem?) {
        this.superClass = superClass
        this.superClassType = superClassType
    }

    override fun setInterfaceTypes(interfaceTypes: List<TypeItem>) {
        this.interfaceTypes = interfaceTypes.toMutableList()
    }

    private var typeInfo: TextTypeItem? = null

    override fun toType(): TextTypeItem {
        if (typeInfo == null) {
            typeInfo = codebase.typeResolver.obtainTypeFromClass(this)
        }
        return typeInfo!!
    }

    private var interfaceTypes = mutableListOf<TypeItem>()
    private val constructors = mutableListOf<ConstructorItem>()
    private val methods = mutableListOf<MethodItem>()
    private val fields = mutableListOf<FieldItem>()
    private val properties = mutableListOf<PropertyItem>()

    override fun constructors(): List<ConstructorItem> = constructors

    override fun methods(): List<MethodItem> = methods

    override fun fields(): List<FieldItem> = fields

    override fun properties(): List<PropertyItem> = properties

    fun addInterface(itf: TypeItem) {
        interfaceTypes.add(itf)
    }

    fun addConstructor(constructor: TextConstructorItem) {
        constructors += constructor
    }

    fun addMethod(method: TextMethodItem) {
        methods += method
    }

    fun addField(field: TextFieldItem) {
        fields += field
    }

    fun addProperty(property: TextPropertyItem) {
        properties += property
    }

    fun addEnumConstant(field: TextFieldItem) {
        field.setEnumConstant(true)
        fields += field
    }

    override fun addInnerClass(cls: ClassItem) {
        innerClasses.add(cls)
    }

    override fun filteredSuperClassType(predicate: Predicate<Item>): TypeItem? {
        // No filtering in signature files: we assume signature APIs
        // have already been filtered and all items should match.
        // This lets us load signature files and rewrite them using updated
        // output formats etc.
        return superClassType
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

    private var fullName: String = name

    override fun simpleName(): String = name.substring(name.lastIndexOf('.') + 1)

    override fun fullName(): String = fullName

    override fun qualifiedName(): String = qualifiedName

    override fun isDefined(): Boolean {
        assert(emit == (position != SourcePositionInfo.UNKNOWN))
        return emit
    }

    override fun toString(): String = "class ${qualifiedName()}"

    override fun mapTypeVariables(target: ClassItem): Map<String, String> {
        return emptyMap()
    }

    override fun createDefaultConstructor(): ConstructorItem {
        return TextConstructorItem.createDefaultConstructor(codebase, this, position)
    }

    private fun getParentAndInterfaces(): List<TextClassItem> {
        val classes = interfaceTypes().map { it.asClass() as TextClassItem }.toMutableList()
        superClass()?.let { classes.add(0, it as TextClassItem) }
        return classes
    }

    private var allSuperClassesAndInterfaces: List<TextClassItem>? = null

    /**
     * Returns all super classes and interfaces in the class hierarchy the class item inherits. The
     * returned list is sorted by the proximity of the classes to the class item in the hierarchy
     * chain. If an interface appears multiple time in the hierarchy chain, it is ordered based on
     * the furthest distance to the class item.
     */
    fun getAllSuperClassesAndInterfaces(): List<TextClassItem> {
        allSuperClassesAndInterfaces?.let {
            return it
        }

        val classLevelMap = mutableMapOf<TextClassItem, Int>()

        // Stores the parent class and interfaces to be iterated.
        // Since a class can inherit multiple class and interfaces, queue is two-dimensional.
        // Each inner lists represents all super class and interfaces in the same hierarchy level.
        val queue = ArrayDeque<List<TextClassItem>>()
        queue.add(getParentAndInterfaces())

        // We need to visit the hierarchy starting from the greatest ancestor,
        // but we cannot naively reverse-iterate based on the order the hierarchy is discovered
        // because a class/interface can appear multiple times in the hierarchy graph
        // (i.e. a vertex can have multiple outgoing edges).
        // Thus, we keep track of the furthest distances from each hierarchy vertices to the
        // destination vertex (cl) and reverse iterate from the vertices that are
        // farthest from the destination.
        var hierarchyLevel = 1
        while (queue.isNotEmpty()) {
            val superClasses = queue.removeFirst()
            val parentClasses = ArrayList<TextClassItem>()
            for (superClass in superClasses) {
                // Every class extends java.lang.Object and thus not need to be
                // included in the hierarchy
                if (!superClass.isJavaLangObject()) {
                    classLevelMap[superClass] = hierarchyLevel
                    parentClasses.addAll(superClass.getParentAndInterfaces())
                }
            }
            if (parentClasses.isNotEmpty()) {
                queue.add(parentClasses)
            }
            hierarchyLevel += 1
        }

        allSuperClassesAndInterfaces =
            classLevelMap.toList().sortedWith(compareBy { it.second }).map { it.first }

        return allSuperClassesAndInterfaces!!
    }

    companion object {
        internal fun createStubClass(
            codebase: TextCodebase,
            name: String,
            isInterface: Boolean
        ): TextClassItem {
            val index = if (name.endsWith(">")) name.indexOf('<') else -1
            val qualifiedName = if (index == -1) name else name.substring(0, index)
            val typeParameterList =
                if (index == -1) {
                    TypeParameterList.NONE
                } else {
                    TextTypeParameterList.create(codebase, name.substring(index))
                }
            val fullName = getFullName(qualifiedName)
            val cls =
                TextClassItem(
                    codebase = codebase,
                    name = fullName,
                    qualifiedName = qualifiedName,
                    isInterface = isInterface,
                    modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
                    typeParameterList = typeParameterList
                )
            cls.emit = false // it's a stub

            return cls
        }

        private fun getFullName(qualifiedName: String): String {
            var end = -1
            val length = qualifiedName.length
            var prev = qualifiedName[length - 1]
            for (i in length - 2 downTo 0) {
                val c = qualifiedName[i]
                if (c == '.' && prev.isUpperCase()) {
                    end = i + 1
                }
                prev = c
            }
            if (end != -1) {
                return qualifiedName.substring(end)
            }

            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        }

        /**
         * Determines whether if [thisClassType] is covariant type with [otherClassType]. If
         * [thisClassType] does not belong to this class, return false.
         *
         * @param thisClass [ClassItem] that [thisClassType] belongs to
         * @param thisClassType [TypeItem] that belongs to this class
         * @param otherClassType [TypeItem] that belongs to other class
         * @return Boolean that indicates whether if the two types are covariant
         */
        private fun isCovariantType(
            thisClass: ClassItem,
            thisClassType: TypeItem,
            otherClassType: TypeItem
        ): Boolean {
            // TypeItem.asClass() returns null for primitive types.
            // Since primitive types are not covariant with anything, return false
            val otherClass = otherClassType.asClass() ?: return false

            val otherSuperClassNames =
                (otherClass as TextClassItem).getAllSuperClassesAndInterfaces().map {
                    it.qualifiedName()
                }

            val thisClassTypeErased = thisClassType.toErasedTypeString()
            val typeArgIndex =
                thisClass.toType().typeArguments(simplified = true).indexOf(thisClassTypeErased)

            // thisClassSuperType is the super type of thisClassType retrieved from the type
            // arguments.
            // e.g. when type arguments are <K, V extends some.arbitrary.Class>,
            // thisClassSuperType will be "some.arbitrary.Class" when the thisClassType is "V"
            // If thisClassType is not included in the type arguments or
            // if thisClassType does not have a super type specified in the type argument,
            // thisClassSuperType will be thisClassType.
            val thisClassSuperType =
                if (typeArgIndex == -1) thisClassTypeErased
                else
                    thisClass.toType().typeArguments()[typeArgIndex].substringAfterLast(" extends ")

            return thisClassSuperType in otherSuperClassNames
        }

        private fun hasEqualTypeVar(
            type1: TypeItem,
            class1: ClassItem,
            type2: TypeItem,
            class2: ClassItem
        ): Boolean {
            // Given a type and its containing class,
            // find the interface types that contains the type.
            // For instance, for a method that looks like:
            // class SomeClass implements InterfaceA<some.return.Type>, InterfaceB<some.return.Type>
            //     Type foo()
            // this function will return [InterfaceA, InterfaceB] when Type and SomeClass
            // are passed as inputs.
            val typeContainingInterfaces = { t: TypeItem, cl: ClassItem ->
                val interfaceTypes =
                    cl.interfaceTypes().plus(cl.toType()).plus(cl.superClassType()).filterNotNull()
                interfaceTypes.filter {
                    val typeArgs = it.typeArguments(simplified = true)
                    t.toString() in typeArgs ||
                        t.toElementType() in typeArgs ||
                        t.asClass()?.superClass()?.qualifiedName() in typeArgs
                }
            }

            val typeContainingInterfaces1 = typeContainingInterfaces(type1, class1)
            val typeContainingInterfaces2 = typeContainingInterfaces(type2, class2)

            if (typeContainingInterfaces1.isEmpty() || typeContainingInterfaces2.isEmpty()) {
                return false
            }

            val interfaceTypesAreCovariant = { t1: TypeItem, t2: TypeItem ->
                t1.toErasedTypeString() == t2.toErasedTypeString() ||
                    t1.asClass()?.superClass()?.qualifiedName() == t2.asClass()?.qualifiedName() ||
                    t2.asClass()?.superClass()?.qualifiedName() == t1.asClass()?.qualifiedName()
            }

            // Check if the return type containing interfaces of the two methods have an
            // intersection.
            return typeContainingInterfaces1.any { typeInterface1 ->
                typeContainingInterfaces2.any { typeInterface2 ->
                    interfaceTypesAreCovariant(typeInterface1, typeInterface2)
                }
            }
        }

        private fun hasEqualTypeBounds(method1: MethodItem, method2: MethodItem): Boolean {
            val typeInTypeParams = { t: TypeItem, m: MethodItem ->
                t in m.typeParameterList().typeParameters().map { it.toType() }
            }

            val getTypeBounds = { t: TypeItem, m: MethodItem ->
                m.typeParameterList()
                    .typeParameters()
                    .single { it.toType() == t }
                    .typeBounds()
                    .toSet()
            }

            val returnType1 = method1.returnType()
            val returnType2 = method2.returnType()

            // The following two methods are considered equal:
            // method public <A extends some.package.SomeClass> A foo (Class<A>);
            // method public <T extends some.package.SomeClass> T foo (Class<T>);
            // This can be verified by converting return types to bounds ([some.package.SomeClass])
            // and compare equivalence.
            return typeInTypeParams(returnType1, method1) &&
                typeInTypeParams(returnType2, method2) &&
                getTypeBounds(returnType1, method1) == getTypeBounds(returnType2, method2)
        }

        private fun hasCovariantTypes(
            type1: TypeItem,
            class1: ClassItem,
            type2: TypeItem,
            class2: ClassItem
        ): Boolean {
            val types = listOf(type1, type2)

            val type1Erased = type1.toErasedTypeString()
            val type2Erased = type2.toErasedTypeString()

            // The return type of the following two methods are considered equal:
            // when SomeReturnSubClass extends SomeReturnClass:
            // method SomeReturnClass foo() and method SomeReturnSubClass foo()
            // Likewise, the return type of the two methods are also considered equal:
            // method T foo() in SomeClass<T extends SomeReturnClass> and
            // method SomeReturnSubClass foo() in SomeOtherClass
            // This can be verified by checking if a method's return type exists in
            // another method return type's super classes
            // Since this method is only used to compare methods with same name and parameters count
            // within same hierarchy tree, it is unlikely that
            // two methods have same generic return type.
            // However, not comparing erased type strings may lead to false negatives
            // (e.g. comparing java.util.Iterator<E> and java.util.Iterator<T>)
            // Thus erased type strings equivalence must be evaluated.
            return type1Erased == type2Erased ||
                isCovariantType(class1, type1, type2) ||
                isCovariantType(class2, type2, type1) ||
                types.any { it.isJavaLangObject() }
        }

        /**
         * Compares two [MethodItem]s and determines if the two methods have equal return types. The
         * two methods' return types are considered equal even if the two are not identical, but are
         * compatible in compiler level. For instance, return types in a same hierarchy tree are
         * considered equal.
         *
         * @param method1 first [MethodItem] to compare the return type
         * @param method2 second [MethodItem] to compare the return type
         * @return a [Boolean] value representing if the two methods' return types are equal
         */
        fun hasEqualReturnType(method1: MethodItem, method2: MethodItem): Boolean {
            val returnType1 = method1.returnType()
            val returnType2 = method2.returnType()
            val class1 = method1.containingClass()
            val class2 = method2.containingClass()

            if (returnType1 == returnType2) return true

            if (hasEqualTypeVar(returnType1, class1, returnType2, class2)) return true

            if (hasEqualTypeBounds(method1, method2)) return true

            if (hasCovariantTypes(returnType1, class1, returnType2, class2)) return true

            return false
        }

        /**
         * Compares two [MethodItem] and determines if the two are considered equal based on the
         * context of the containing classes of the methods. To be specific, for the two methods in
         * which the coexistence in a class would lead to a method already defined compiler error,
         * this method returns true.
         *
         * @param method1 first [MethodItem] to compare
         * @param method2 second [MethodItem] to compare
         * @return a [Boolean] value representing if the two methods are equal or not with respect
         *   to the classes contexts.
         */
        fun equalMethodInClassContext(method1: MethodItem, method2: MethodItem): Boolean {
            if (method1 == method2) return true

            if (method1.name() != method2.name()) return false
            if (method1.parameters().size != method2.parameters().size) return false

            val hasEqualParams =
                method1.parameters().zip(method2.parameters()).all {
                    (param1, param2): Pair<ParameterItem, ParameterItem> ->
                    val type1 = param1.type()
                    val type2 = param2.type()
                    val class1 = method1.containingClass()
                    val class2 = method2.containingClass()

                    // At this point, two methods' return types equivalence would have been checked.
                    // i.e. If hasEqualReturnType(method1, method2) is true,
                    // we know that the two methods return types are equal.
                    // In other words, if the two compared param types are both method return types,
                    // we transitively know that the two param types are equal as well.
                    val bothAreMethodReturnType =
                        type1 == method1.returnType() && type2 == method2.returnType()

                    type1 == type2 ||
                        bothAreMethodReturnType ||
                        hasEqualTypeVar(type1, class1, type2, class2) ||
                        hasCovariantTypes(type1, class1, type2, class2)
                }

            return hasEqualReturnType(method1, method2) && hasEqualParams
        }
    }
}
