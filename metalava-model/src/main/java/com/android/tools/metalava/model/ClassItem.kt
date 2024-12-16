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

/**
 * Represents a {@link https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html Class}
 *
 * If you need to model array dimensions or resolved type parameters, see {@link
 * com.android.tools.metalava.model.TypeItem} instead
 */
@MetalavaApi
interface ClassItem : ClassContentItem, SelectableItem, TypeParameterListOwner {
    /**
     * The qualified name of a class. In class foo.bar.Outer.Inner, the qualified name is the whole
     * thing.
     */
    @MetalavaApi fun qualifiedName(): String

    /** The simple name of a class. In class foo.bar.Outer.Inner, the simple name is "Inner" */
    fun simpleName(): String

    /** The full name of a class. In class foo.bar.Outer.Inner, the full name is "Outer.Inner" */
    fun fullName(): String

    /** Is this a nested class? */
    @MetalavaApi fun isNestedClass() = containingClass() != null

    /** Is this a top level class? */
    fun isTopLevelClass(): Boolean = containingClass() == null

    /** The origin of this class. */
    override val origin: ClassOrigin

    /** This [ClassItem] and all of its nested classes, recursively */
    fun allClasses(): Sequence<ClassItem> {
        return sequenceOf(this).plus(nestedClasses().asSequence().flatMap { it.allClasses() })
    }

    override fun parent(): SelectableItem? = containingClass() ?: containingPackage()

    override val effectivelyDeprecated: Boolean
        get() = originallyDeprecated || containingClass()?.effectivelyDeprecated == true

    /** Returns the internal name of the class, as seen in bytecode */
    fun internalName(): String {
        var curr: ClassItem? = this
        while (curr?.containingClass() != null) {
            curr = curr.containingClass()
        }

        if (curr == null) {
            return fullName().replace('.', '$')
        }

        return curr.containingPackage().qualifiedName().replace('.', '/') +
            "/" +
            fullName().replace('.', '$')
    }

    /**
     * The super class of this class, if any.
     *
     * Interfaces always return `null` for this.
     */
    @MetalavaApi fun superClass() = superClassType()?.asClass()

    /** All super classes, if any */
    fun allSuperClasses(): Sequence<ClassItem> {
        return generateSequence(superClass()) { it.superClass() }
    }

    /**
     * The super class type of this class, if any. The difference between this and [superClass] is
     * that the type reference can include type arguments; e.g. in "class MyList extends
     * List<String>" the super class is java.util.List and the super class type is
     * java.util.List<java.lang.String>.
     */
    fun superClassType(): ClassTypeItem?

    /** Returns true if this class extends the given class (includes self) */
    fun extends(qualifiedName: String): Boolean {
        if (qualifiedName() == qualifiedName) {
            return true
        }

        val superClass = superClass()
        return superClass?.extends(qualifiedName)
            ?: when {
                isEnum() -> qualifiedName == JAVA_LANG_ENUM
                isAnnotationType() -> qualifiedName == JAVA_LANG_ANNOTATION
                else -> qualifiedName == JAVA_LANG_OBJECT
            }
    }

    /** Returns true if this class implements the given interface (includes self) */
    fun implements(qualifiedName: String): Boolean {
        if (qualifiedName() == qualifiedName) {
            return true
        }

        interfaceTypes().forEach {
            val cls = it.asClass()
            if (cls != null && cls.implements(qualifiedName)) {
                return true
            }
        }

        // Might be implementing via superclass
        if (superClass()?.implements(qualifiedName) == true) {
            return true
        }

        return false
    }

    /** Returns true if this class extends or implements the given class or interface */
    fun extendsOrImplements(qualifiedName: String): Boolean =
        extends(qualifiedName) || implements(qualifiedName)

    /** Any interfaces implemented by this class */
    @MetalavaApi fun interfaceTypes(): List<ClassTypeItem>

    /**
     * All classes and interfaces implemented (by this class and its super classes and the
     * interfaces themselves)
     */
    fun allInterfaces(): Sequence<ClassItem>

    /**
     * Any classes nested in this class, that includes inner classes which are just non-static
     * nested classes.
     */
    fun nestedClasses(): List<ClassItem>

    /** The constructors in this class */
    @MetalavaApi fun constructors(): List<ConstructorItem>

    /** Whether this class has an implicit default constructor */
    fun hasImplicitDefaultConstructor(): Boolean

    /** The non-constructor methods in this class */
    @MetalavaApi fun methods(): List<MethodItem>

    /** The properties in this class */
    fun properties(): List<PropertyItem>

    /** The fields in this class */
    @MetalavaApi fun fields(): List<FieldItem>

    /** The members in this class: constructors, methods, fields/enum constants */
    fun members(): Sequence<MemberItem> {
        return fields().asSequence().plus(constructors().asSequence()).plus(methods().asSequence())
    }

    val classKind: ClassKind

    /** Whether this class is an interface */
    fun isInterface() = classKind == ClassKind.INTERFACE

    /** Whether this class is an annotation type */
    fun isAnnotationType() = classKind == ClassKind.ANNOTATION_TYPE

    /** Whether this class is an enum */
    fun isEnum() = classKind == ClassKind.ENUM

    /** Whether this class is a regular class (not an interface, not an enum, etc) */
    fun isClass() = classKind == ClassKind.CLASS

    /**
     * Whether this class is a File Facade class, i.e. a `*Kt` class that contains declarations
     * which do not belong to a Kotlin class, e.g. top-level functions, properties, etc.
     */
    fun isFileFacade() = false

    /** The containing class, for nested classes */
    @MetalavaApi override fun containingClass(): ClassItem?

    /** The containing package */
    override fun containingPackage(): PackageItem

    /** Gets the type for this class */
    override fun type(): ClassTypeItem

    override fun setType(type: TypeItem) =
        error("Cannot call setType(TypeItem) on PackageItem: $this")

    /** True if [freeze] has been called on this, false otherwise. */
    val frozen: Boolean

    /**
     * Freeze this [ClassItem] so it cannot be mutated.
     *
     * A frozen [ClassItem] cannot have new members (including nested classes) added or its
     * modifiers mutated.
     *
     * Freezing a [ClassItem] will also freeze its super types.
     */
    fun freeze()

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) = codebase.findClass(qualifiedName())

    /** Returns true if this class has type parameters */
    fun hasTypeVariables(): Boolean

    fun isJavaLangObject(): Boolean {
        return qualifiedName() == JAVA_LANG_OBJECT
    }

    // Mutation APIs: Used to "fix up" the API hierarchy to only expose visible parts of the API.

    // This replaces the interface types implemented by this class
    fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>)

    /** The primary constructor for this class in Kotlin, if present. */
    val primaryConstructor: ConstructorItem?
        get() = constructors().singleOrNull { it.isPrimary }

    override fun baselineElementId() = qualifiedName()

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun equalsToItem(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassItem) return false

        return qualifiedName() == other.qualifiedName()
    }

    override fun hashCodeForItem(): Int {
        return qualifiedName().hashCode()
    }

    override fun toStringForItem() = "class ${qualifiedName()}"

    companion object {
        /** Looks up the retention policy for the given class */
        fun findRetention(cls: ClassItem): AnnotationRetention {
            val modifiers = cls.modifiers
            val annotation = modifiers.findAnnotation(AnnotationItem::isRetention)
            val value = annotation?.findAttribute(ANNOTATION_ATTR_VALUE)
            val source = value?.value?.toSource()
            return when {
                source == null -> AnnotationRetention.getDefault(cls)
                source.contains("CLASS") -> AnnotationRetention.CLASS
                source.contains("RUNTIME") -> AnnotationRetention.RUNTIME
                source.contains("SOURCE") -> AnnotationRetention.SOURCE
                source.contains("BINARY") -> AnnotationRetention.BINARY
                else -> AnnotationRetention.getDefault(cls)
            }
        }

        // Same as doclava1 (modulo the new handling when class names match)
        val comparator: Comparator<in ClassItem> = Comparator { o1, o2 ->
            val delta = o1.fullName().compareTo(o2.fullName())
            if (delta == 0) {
                o1.qualifiedName().compareTo(o2.qualifiedName())
            } else {
                delta
            }
        }

        /** A partial ordering over [ClassItem] comparing [ClassItem.fullName]. */
        val fullNameComparator: Comparator<ClassItem> = Comparator.comparing { it.fullName() }

        /** A total ordering over [ClassItem] comparing [ClassItem.qualifiedName]. */
        private val qualifiedComparator: Comparator<ClassItem> =
            Comparator.comparing { it.qualifiedName() }

        /**
         * A total ordering over [ClassItem] comparing [ClassItem.fullName] first and then
         * [ClassItem.qualifiedName].
         */
        val fullNameThenQualifierComparator: Comparator<ClassItem> =
            fullNameComparator.thenComparing(qualifiedComparator)

        fun classNameSorter(): Comparator<in ClassItem> = ClassItem.qualifiedComparator
    }

    fun findMethod(
        template: MethodItem,
        includeSuperClasses: Boolean = false,
        includeInterfaces: Boolean = false
    ): MethodItem? {
        methods()
            .asSequence()
            .filter { it.matches(template) }
            .forEach {
                return it
            }

        if (includeSuperClasses) {
            superClass()?.findMethod(template, true, includeInterfaces)?.let {
                return it
            }
        }

        if (includeInterfaces) {
            for (itf in interfaceTypes()) {
                val cls = itf.asClass() ?: continue
                cls.findMethod(template, includeSuperClasses, true)?.let {
                    return it
                }
            }
        }
        return null
    }

    /**
     * Finds a method matching the given method that satisfies the given predicate, considering all
     * methods defined on this class and its super classes
     */
    fun findPredicateMethodWithSuper(template: MethodItem, filter: FilterPredicate?): MethodItem? {
        val method = findMethod(template, true, true)
        if (method == null) {
            return null
        }
        if (filter == null || filter.test(method)) {
            return method
        }
        return method.findPredicateSuperMethod(filter)
    }

    fun findConstructor(template: ConstructorItem): ConstructorItem? {
        constructors()
            .asSequence()
            .filter { it.matches(template) }
            .forEach {
                return it
            }
        return null
    }

    fun findField(
        fieldName: String,
        includeSuperClasses: Boolean = false,
        includeInterfaces: Boolean = false
    ): FieldItem? {
        val field = fields().firstOrNull { it.name() == fieldName }
        if (field != null) {
            return field
        }

        if (includeSuperClasses) {
            superClass()?.findField(fieldName, true, includeInterfaces)?.let {
                return it
            }
        }

        if (includeInterfaces) {
            for (itf in interfaceTypes()) {
                val cls = itf.asClass() ?: continue
                cls.findField(fieldName, includeSuperClasses, true)?.let {
                    return it
                }
            }
        }
        return null
    }

    /**
     * Find the [MethodItem] in this.
     *
     * It will look for [MethodItem]s whose [MethodItem.name] is equal to [methodName].
     *
     * Out of those matching items it will select the first [MethodItem] whose parameters match the
     * supplied parameters string. Parameters are matched against a candidate [MethodItem] as
     * follows:
     * * The [parameters] string is split on `,` and trimmed and then each item in the list is
     *   matched with the corresponding [ParameterItem] in `candidate.parameters()` as follows:
     * * Everything after `<` is removed.
     * * The result is compared to the result of calling [TypeItem.toErasedTypeString]`(candidate)`
     *   on the [ParameterItem.type].
     *
     * If every parameter matches then the matched [MethodItem] is returned. If no `candidate`
     * matches then it returns 'null`.
     *
     * @param methodName the name of the method or [simpleName] if looking for constructors.
     * @param parameters the comma separated erased types of the parameters.
     */
    fun findMethod(methodName: String, parameters: String) =
        methods().firstOrNull { it.name() == methodName && parametersMatch(it, parameters) }

    /**
     * Find the [ConstructorItem] in this.
     *
     * Out of those matching items it will select the first [ConstructorItem] whose parameters match
     * the supplied parameters string. Parameters are matched against a candidate [ConstructorItem]
     * as follows:
     * * The [parameters] string is split on `,` and trimmed and then each item in the list is
     *   matched with the corresponding [ParameterItem] in `candidate.parameters()` as follows:
     * * Everything after `<` is removed.
     * * The result is compared to the result of calling [TypeItem.toErasedTypeString]`(candidate)`
     *   on the [ParameterItem.type].
     *
     * If every parameter matches then the matched [ConstructorItem] is returned. If no `candidate`
     * matches then it returns 'null`.
     *
     * @param parameters the comma separated erased types of the parameters.
     */
    fun findConstructor(parameters: String) =
        constructors().firstOrNull { parametersMatch(it, parameters) }

    /**
     * Find the [CallableItem] in this.
     *
     * If [name] is [simpleName] then call [findConstructor] else call [findMethod].
     */
    fun findCallable(name: String, parameters: String) =
        if (name == simpleName()) findConstructor(parameters) else findMethod(name, parameters)

    private fun parametersMatch(callable: CallableItem, description: String): Boolean {
        val parameterStrings =
            description.splitToSequence(",").map(String::trim).filter(String::isNotEmpty).toList()
        val parameters = callable.parameters()
        if (parameters.size != parameterStrings.size) {
            return false
        }
        for (i in parameters.indices) {
            var parameterString = parameterStrings[i]
            val index = parameterString.indexOf('<')
            if (index != -1) {
                parameterString = parameterString.substring(0, index)
            }
            val parameter = parameters[i].type().toErasedTypeString()
            if (parameter != parameterString) {
                return false
            }
        }

        return true
    }

    /** Returns the corresponding source file, if any */
    fun sourceFile(): SourceFile?

    /** If this class is an annotation type, returns the retention of this class */
    fun getRetention(): AnnotationRetention

    /**
     * Return superclass matching the given predicate. When a superclass doesn't match, we'll keep
     * crawling up the tree until we find someone who matches.
     */
    fun filteredSuperclass(predicate: FilterPredicate): ClassItem? {
        val superClass = superClass() ?: return null
        return if (predicate.test(superClass)) {
            superClass
        } else {
            superClass.filteredSuperclass(predicate)
        }
    }

    fun filteredSuperClassType(predicate: FilterPredicate): ClassTypeItem? {
        var superClassType: ClassTypeItem? = superClassType() ?: return null
        var prev: ClassItem? = null
        while (superClassType != null) {
            val superClass = superClassType.asClass() ?: return null
            if (predicate.test(superClass)) {
                if (prev == null || superClass == superClass()) {
                    // Direct reference; no need to map type variables
                    return superClassType
                }
                if (!superClassType.hasTypeArguments()) {
                    // No type variables - also no need for mapping
                    return superClassType
                }

                return superClassType.convertType(this, prev) as ClassTypeItem
            }

            prev = superClass
            superClassType = superClass.superClassType()
        }

        return null
    }

    /**
     * Return methods matching the given predicate. Forcibly includes local methods that override a
     * matching method in an ancestor class.
     */
    fun filteredMethods(
        predicate: FilterPredicate,
        includeSuperClassMethods: Boolean = false
    ): Collection<MethodItem> {
        val methods = LinkedHashSet<MethodItem>()
        for (method in methods()) {
            if (predicate.test(method) || method.findPredicateSuperMethod(predicate) != null) {
                // val duplicated = method.duplicate(this)
                // methods.add(duplicated)
                methods.remove(method)
                methods.add(method)
            }
        }
        if (includeSuperClassMethods) {
            superClass()?.filteredMethods(predicate, includeSuperClassMethods)?.let {
                methods += it
            }
        }
        return methods
    }

    /** Returns the constructors that match the given predicate */
    fun filteredConstructors(predicate: FilterPredicate): Sequence<ConstructorItem> {
        return constructors().asSequence().filter { predicate.test(it) }
    }

    /**
     * Return fields matching the given predicate. Also clones fields from ancestors that would
     * match had they been defined in this class.
     */
    fun filteredFields(predicate: FilterPredicate, showUnannotated: Boolean): List<FieldItem> {
        val fields = LinkedHashSet<FieldItem>()
        if (showUnannotated) {
            for (clazz in allInterfaces()) {
                // If this class is an interface then it will be included in allInterfaces(). If it
                // is a class then it will not be included. Either way, this class' fields will be
                // handled below so there is no point in processing the fields here.
                if (clazz == this) {
                    continue
                }
                if (!clazz.isInterface()) {
                    continue
                }
                for (field in clazz.fields()) {
                    if (!predicate.test(field)) {
                        val duplicated = field.duplicate(this)
                        if (predicate.test(duplicated)) {
                            fields.remove(duplicated)
                            fields.add(duplicated)
                        }
                    }
                }
            }

            val superClass = superClass()
            if (superClass != null && !predicate.test(superClass) && predicate.test(this)) {
                // Include constants from hidden super classes.
                for (field in superClass.fields()) {
                    val fieldModifiers = field.modifiers
                    if (
                        !fieldModifiers.isStatic() ||
                            !fieldModifiers.isFinal() ||
                            !fieldModifiers.isPublic()
                    ) {
                        continue
                    }
                    if (!field.originallyHidden) {
                        val duplicated = field.duplicate(this)
                        if (predicate.test(duplicated)) {
                            fields.remove(duplicated)
                            fields.add(duplicated)
                        }
                    }
                }
            }
        }
        for (field in fields()) {
            if (predicate.test(field)) {
                fields.remove(field)
                fields.add(field)
            }
        }
        if (fields.isEmpty()) {
            return emptyList()
        }
        val list = fields.toMutableList()
        list.sortWith(FieldItem.comparator)
        return list
    }

    fun filteredInterfaceTypes(predicate: FilterPredicate): Collection<ClassTypeItem> {
        val interfaceTypes =
            filteredInterfaceTypes(
                predicate,
                LinkedHashSet(),
                includeSelf = false,
                includeParents = false,
                target = this
            )

        return interfaceTypes
    }

    fun allInterfaceTypes(predicate: FilterPredicate): Collection<TypeItem> {
        val interfaceTypes =
            filteredInterfaceTypes(
                predicate,
                LinkedHashSet(),
                includeSelf = false,
                includeParents = true,
                target = this
            )
        if (interfaceTypes.isEmpty()) {
            return interfaceTypes
        }

        return interfaceTypes
    }

    private fun filteredInterfaceTypes(
        predicate: FilterPredicate,
        types: LinkedHashSet<ClassTypeItem>,
        includeSelf: Boolean,
        includeParents: Boolean,
        target: ClassItem
    ): LinkedHashSet<ClassTypeItem> {
        val superClassType = superClassType()
        if (superClassType != null) {
            val superClass = superClassType.asClass()
            if (superClass != null) {
                if (!predicate.test(superClass)) {
                    superClass.filteredInterfaceTypes(
                        predicate,
                        types,
                        true,
                        includeParents,
                        target
                    )
                } else if (includeSelf && superClass.isInterface()) {
                    types.add(superClassType)
                    if (includeParents) {
                        superClass.filteredInterfaceTypes(
                            predicate,
                            types,
                            true,
                            includeParents,
                            target
                        )
                    }
                }
            }
        }
        for (type in interfaceTypes()) {
            val cls = type.asClass() ?: continue
            if (predicate.test(cls)) {
                if (hasTypeVariables() && type.hasTypeArguments()) {
                    val replacementMap = target.mapTypeVariables(this)
                    if (replacementMap.isNotEmpty()) {
                        val mapped = type.convertType(replacementMap)
                        types.add(mapped)
                        continue
                    }
                }
                types.add(type)
                if (includeParents) {
                    cls.filteredInterfaceTypes(predicate, types, true, includeParents, target)
                }
            } else {
                cls.filteredInterfaceTypes(predicate, types, true, includeParents, target)
            }
        }
        return types
    }

    /**
     * Creates a map of type parameters of the target class to the type variables substituted for
     * those parameters by this class.
     *
     * If this class is declared as `class A<X,Y> extends B<X,Y>`, and target class `B` is declared
     * as `class B<M,N>`, this method returns the map `{M->X, N->Y}`.
     *
     * There could be multiple intermediate classes between this class and the target class, and in
     * some cases we could be substituting in a concrete class, e.g. if this class is declared as
     * `class MyClass extends Parent<String,Number>` and target class `Parent` is declared as `class
     * Parent<M,N>` would return the map `{M->java.lang.String, N>java.lang.Number}`.
     *
     * The target class can be an interface. If the interface can be found through multiple paths in
     * the class hierarchy, this method returns the mapping from the first path found in terms of
     * declaration order. For instance, given declarations `class C<X, Y> implements I1<X>, I2<Y>`,
     * `interface I1<T1> implements Root<T1>`, `interface I2<T2> implements Root<T2>`, and
     * `interface Root<T>`, this method will return `{T->X}` as the mapping from `C` to `Root`, not
     * `{T->Y}`.
     */
    fun mapTypeVariables(target: ClassItem): TypeParameterBindings {
        // Gather the supertypes to check for [target]. It is only possible for [target] to be found
        // in the class hierarchy through this class's interfaces if [target] is an interface.
        val candidates =
            if (target.isInterface()) {
                interfaceTypes() + superClassType()
            } else {
                listOf(superClassType())
            }

        for (superClassType in candidates.filterNotNull()) {
            superClassType as? ClassTypeItem ?: continue
            // Get the class from the class type so that its type parameters can be accessed.
            val declaringClass = superClassType.asClass() ?: continue

            if (declaringClass.qualifiedName() == target.qualifiedName()) {
                // The target has been found, return the map directly.
                return mapTypeVariables(declaringClass, superClassType)
            } else {
                // This superClassType isn't target, but maybe it has target as a superclass.
                val nextLevelMap = declaringClass.mapTypeVariables(target)
                if (nextLevelMap.isNotEmpty()) {
                    val thisLevelMap = mapTypeVariables(declaringClass, superClassType)
                    // Link the two maps by removing intermediate type variables.
                    return nextLevelMap.mapValues { (_, value) ->
                        (value as? VariableTypeItem?)?.let { thisLevelMap[it.asTypeParameter] }
                            ?: value
                    }
                }
            }
        }
        return emptyMap()
    }

    /**
     * Creates a map between the type parameters of [declaringClass] and the arguments of
     * [classTypeItem].
     */
    private fun mapTypeVariables(
        declaringClass: ClassItem,
        classTypeItem: ClassTypeItem
    ): TypeParameterBindings {
        // Don't include arguments of class types, for consistency with the old psi implementation.
        // i.e. if the mapping is from `T -> List<String>` then just use `T -> List`.
        // TODO (b/319300404): remove this section
        val classTypeArguments =
            classTypeItem.arguments.map {
                if (it is ClassTypeItem && it.arguments.isNotEmpty()) {
                    it.substitute(arguments = emptyList())
                } else {
                    it
                }
                // Although a `ClassTypeItem`'s arguments can be `WildcardTypeItem`s as well as
                // `ReferenceTypeItem`s, a `ClassTypeItem` used in an extends or implements list
                // cannot have a `WildcardTypeItem` as an argument so this cast is safe. See
                // https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-Superclass
                as ReferenceTypeItem
            }
        return declaringClass.typeParameterList.zip(classTypeArguments).toMap()
    }

    /**
     * Creates a default constructor in this class.
     *
     * Default constructors that are added by Java have the same visibility as their class which is
     * the default behavior of this method if no [visibility] is provided. However, this is also
     * used to create default constructors in order for stub classes to compile and as they do not
     * appear in the API they need to be marked as package private so this method allows the
     * [visibility] to be explicitly specified by the caller.
     *
     * @param visibility the visibility of the constructor, defaults to the same as this class.
     */
    fun createDefaultConstructor(
        visibility: VisibilityLevel = modifiers.getVisibilityLevel()
    ): ConstructorItem

    fun addMethod(method: MethodItem)

    /**
     * Return true if a [ClassItem] could be subclassed, i.e. is not final or sealed and has at
     * least one accessible constructor.
     */
    fun isExtensible() =
        !modifiers.isFinal() &&
            !modifiers.isSealed() &&
            constructors().any { it.isPublic || it.isProtected }
}
