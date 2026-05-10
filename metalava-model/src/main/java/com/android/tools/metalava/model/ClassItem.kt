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

import com.android.tools.metalava.model.annotation.AnnotationClass
import com.android.tools.metalava.model.scope.QualifiedNameScope

/**
 * Represents a {@link https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html Class}
 *
 * If you need to model array dimensions or resolved type parameters, see {@link
 * com.android.tools.metalava.model.TypeItem} instead
 */
@MetalavaApi
interface ClassItem :
    ClassContentItem,
    SelectableItem,
    TypeParameterListOwner,
    ReferencableItem,
    // This is implemented because constructors cannot usually be referenced directly, e.g. in
    // imports of `new` expressions. Instead, the class is referenced giving access to its
    // constructors. Having ClassItem extend ReferencableCallableItem models that behavior.
    ReferencableCallableItem,
    QualifiedNameScope {
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
    @MetalavaApi fun superClass() = superClassType()?.resolveClass(codebase)

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

    /**
     * A class is effectively sealed if it is either explicitly marked sealed, is a non-public
     * interface, or an abstract class with no publicly accessible constructors. For more
     * information, see b/220960090
     */
    fun isEffectivelySealed(): Boolean {
        return modifiers.isSealed() ||
            (isInterface() && !isPublic) ||
            ((isClass() && modifiers.isAbstract()) &&
                (constructors().none { (it.isPublic || it.isProtected) && !it.hidden }))
    }

    /**
     * This stores only the direct classes that inherit from this class, and not any indirect
     * classes. This stores subclasses for only sealed classes and interfaces (for interfaces, it
     * stores all the classes that implement that interface)
     */
    fun sealedClassDirectSubclasses(): List<ClassItem>

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
            val cls = it.resolveClass(codebase)
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
    val isFileFacade: Boolean

    /**
     * Whether this class is a multi-file facade class, generated from Kotlin files annotated with
     * [JvmMultifileClass]. This can only be true when [isFileFacade] is true.
     */
    val isMultiFileClass: Boolean

    /**
     * The [RecordComponents] for this [ClassItem].
     *
     * Is null unless [classKind] is [ClassKind.RECORD], in which case this is set to the, possibly
     * empty, [RecordComponents].
     */
    val recordComponents: RecordComponents?

    override fun describe(capitalize: Boolean): String {
        val descriptor =
            if (classKind == ClassKind.TYPEALIAS) {
                if (capitalize) {
                    "Typealias"
                } else {
                    "typealias"
                }
            } else {
                if (capitalize) {
                    "Class"
                } else {
                    "class"
                }
            }
        return "$descriptor ${qualifiedName()}"
    }

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
        // Same as doclava1 (modulo the new handling when class names match)
        val comparator: Comparator<in ClassItem> = Comparator { o1, o2 ->
            val delta = o1.fullName().compareTo(o2.fullName())
            if (delta == 0) {
                o1.qualifiedName().compareTo(o2.qualifiedName())
            } else {
                delta
            }
        }

        /** A total ordering over [ClassItem] comparing [ClassItem.qualifiedName]. */
        private val qualifiedComparator: Comparator<ClassItem> =
            Comparator.comparing { it.qualifiedName() }

        fun classNameSorter(): Comparator<in ClassItem> = qualifiedComparator

        /**
         * The name used for a fake class which is a container for top level functions and
         * properties in a non-JVM API surface.
         */
        const val TOP_LEVEL_DECLARATION_FACADE_NAME = "\$TopLevelDeclarations"
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
                val cls = itf.resolveClass(codebase) ?: continue
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
                val cls = itf.resolveClass(codebase) ?: continue
                cls.findField(fieldName, includeSuperClasses, true)?.let {
                    return it
                }
            }
        }
        return null
    }

    /**
     * Searches for a property with the [template]'s name and receiver in the class, including
     * searching super classes and interfaces if specified.
     */
    fun findProperty(
        template: PropertyItem,
        includeSuperClasses: Boolean = false,
        includeInterfaces: Boolean = false,
    ): PropertyItem? {
        properties()
            .firstOrNull {
                it.name() == template.name() &&
                    PropertyItem.equalReceivers(template.receiver, it.receiver)
            }
            ?.let {
                return it
            }

        if (includeSuperClasses) {
            superClass()?.findProperty(template, true, includeInterfaces)?.let {
                return it
            }
        }

        if (includeInterfaces) {
            for (itf in interfaceTypes()) {
                val cls = itf.resolveClass(codebase) ?: continue
                cls.findProperty(template, includeSuperClasses, true)?.let {
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
     * Out of those matching items it will select the first [MethodItem] which has bytecode as a
     * target language whose parameters match the supplied parameters string. Parameters are matched
     * against a candidate [MethodItem] as follows:
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
    fun findBytecodeMethod(methodName: String, parameters: String) =
        methods().firstOrNull {
            it.name() == methodName &&
                TargetLanguage.BYTECODE in it.targetLanguages &&
                parametersMatch(it, parameters)
        }

    /**
     * Find the [ConstructorItem] in this.
     *
     * Out of those matching items it will select the first [ConstructorItem] which has bytecode as
     * a target language whose parameters match the supplied parameters string. Parameters are
     * matched against a candidate [ConstructorItem] as follows:
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
    fun findBytecodeConstructor(parameters: String) =
        constructors().firstOrNull {
            TargetLanguage.BYTECODE in it.targetLanguages && parametersMatch(it, parameters)
        }

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

    /**
     * Get the [AnnotationClass] for this class.
     *
     * This must only be called when [ClassItem.classKind] is [ClassKind.ANNOTATION_TYPE].
     */
    val annotationClass: AnnotationClass

    /**
     * For a typealias, the underlying type for which this typealias is an alternative name. Null if
     * this is not a typealias.
     */
    val optionalAliasedType: TypeItem?

    /**
     * For a typealias, the underlying type for which this typealias is an alternative name
     *
     * This must only be called when [classKind] is [ClassKind.TYPEALIAS], and will throw an error
     * otherwise.
     */
    val aliasedType: TypeItem

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
            val superClass = superClassType.resolveClass(codebase) ?: return null
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

    fun filteredInterfaceTypes(predicate: FilterPredicate) =
        filteredInterfaceTypes(
            predicate,
            LinkedHashSet(),
            includeSelf = false,
            includeParents = false,
            target = this
        )

    fun allInterfaceTypes(predicate: FilterPredicate) =
        filteredInterfaceTypes(
            predicate,
            LinkedHashSet(),
            includeSelf = false,
            includeParents = true,
            target = this
        )

    private fun filteredInterfaceTypes(
        predicate: FilterPredicate,
        types: LinkedHashSet<ClassTypeItem>,
        includeSelf: Boolean,
        includeParents: Boolean,
        target: ClassItem
    ): Set<ClassTypeItem> {
        val superClassType = superClassType()
        if (superClassType != null) {
            val superClass = superClassType.resolveClass(codebase)
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
            val cls = type.resolveClass(codebase) ?: continue
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
        // Optimize trying to map variables from a class to its self.
        if (this === target) return emptyMap()

        // Gather the supertypes to check for [target]. It is only possible for [target] to be found
        // in the class hierarchy through this class's interfaces if [target] is an interface.
        val candidates =
            if (target.isInterface()) {
                interfaceTypes() + superClassType()
            } else {
                listOf(superClassType())
            }

        for (superClassType in candidates.filterNotNull()) {
            // Get the class from the class type so that its type parameters can be accessed.
            val declaringClass = superClassType.resolveClass(codebase) ?: continue

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
            }
        return declaringClass.typeParameterList.zip(classTypeArguments).toMap()
    }

    /**
     * Creates an implicit default constructor in this class.
     *
     * Default constructors that are added by Java have the same visibility as their class which is
     * the default behavior of this method if no [visibility] is provided. However, this is also
     * used to create default constructors in order for stub classes to compile and as they do not
     * appear in the API they need to be marked as package private so this method allows the
     * [visibility] to be explicitly specified by the caller.
     *
     * @param visibility the visibility of the constructor, defaults to the same as this class.
     */
    fun createImplicitDefaultConstructor(
        visibility: VisibilityLevel = modifiers.getVisibilityLevel()
    ): ConstructorItem

    /** Add a method to this class. */
    fun addMethod(method: MethodItem)

    /**
     * Return true if a [ClassItem] could be subclassed, i.e. is not final or sealed and has at
     * least one accessible constructor.
     */
    fun isExtensible() =
        !modifiers.isFinal() &&
            !modifiers.isSealed() &&
            // There are no constructors for an interface but that doesn't mean that it's not
            // extensible. If the interface is public or protected it is extensible.
            (isInterface() && (isPublic || isProtected) ||
                constructors().any { it.isPublic || it.isProtected })
}
