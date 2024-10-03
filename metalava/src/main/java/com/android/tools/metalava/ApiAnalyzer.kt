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

package com.android.tools.metalava

import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.ANDROIDX_REQUIRES_PERMISSION
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROID_DEPRECATED_FOR_SDK
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.util.Locale
import java.util.function.Predicate

/**
 * The [ApiAnalyzer] is responsible for walking over the various classes and members and compute
 * visibility etc. of the APIs
 */
class ApiAnalyzer(
    private val sourceParser: SourceParser,
    /** The code to analyze */
    private val codebase: Codebase,
    private val reporter: Reporter,
    private val config: Config = Config(),
) {

    data class Config(
        val manifest: Manifest = emptyManifest,

        /** Packages to exclude/hide */
        val hidePackages: List<String> = emptyList(),

        /**
         * Packages that we should skip generating even if not hidden; typically only used by tests
         */
        val skipEmitPackages: List<String> = emptyList(),

        /**
         * External annotation files that contain non-inclusion annotations which will appear in the
         * generated API.
         *
         * These will be merged into the codebase.
         */
        val mergeQualifierAnnotations: List<File> = emptyList(),

        /**
         * External annotation files that contain annotations which affect inclusion of items in the
         * API.
         *
         * These will be merged into the codebase.
         */
        val mergeInclusionAnnotations: List<File> = emptyList(),

        /** The filter for all the show annotations. */
        val allShowAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),

        /** Configuration for any [ApiPredicate] instances this needs to create. */
        val apiPredicateConfig: ApiPredicate.Config = ApiPredicate.Config()
    )

    /** All packages in the API */
    private val packages: PackageList = codebase.getPackages()

    fun computeApi() {
        if (codebase.trustedApi()) {
            // The codebase is already an API; no consistency checks to be performed
            return
        }

        skipEmitPackages()
        // Suppress kotlin file facade classes with no public api
        hideEmptyKotlinFileFacadeClasses()

        // Propagate visibility down into individual elements -- if a class is hidden,
        // then the methods and fields are hidden etc
        propagateHiddenRemovedAndDocOnly()
    }

    // TODO: Annotation test: @ParameterName, if present, must be supplied on *all* the arguments!
    // Warn about @DefaultValue("null"); they probably meant @DefaultNull
    // Supplying default parameter in override is not allowed!

    fun generateInheritedStubs(filterEmit: Predicate<Item>, filterReference: Predicate<Item>) {
        // When analyzing libraries we may discover some new classes during traversal; these aren't
        // part of the API but may be super classes or interfaces; these will then be added into the
        // package class lists, which could trigger a concurrent modification, so create a snapshot
        // of the class list and iterate over it:
        val allClasses = packages.allClasses().toList()
        allClasses.forEach {
            if (filterEmit.test(it)) {
                generateInheritedStubs(it, filterEmit, filterReference)
            }
        }
    }

    private fun generateInheritedStubs(
        cls: ClassItem,
        filterEmit: Predicate<Item>,
        filterReference: Predicate<Item>
    ) {
        if (!cls.isClass()) return
        if (cls.superClass() == null) return
        val allSuperClasses = cls.allSuperClasses()
        val hiddenSuperClasses =
            allSuperClasses.filter { !filterReference.test(it) && !it.isJavaLangObject() }

        if (hiddenSuperClasses.none()) { // not missing any implementation methods
            return
        }

        addInheritedStubsFrom(cls, hiddenSuperClasses, allSuperClasses, filterEmit, filterReference)
        addInheritedInterfacesFrom(cls, hiddenSuperClasses, filterReference)
    }

    private fun addInheritedInterfacesFrom(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
        filterReference: Predicate<Item>
    ) {
        var interfaceTypes: MutableList<ClassTypeItem>? = null
        var interfaceTypeClasses: MutableList<ClassItem>? = null
        for (hiddenSuperClass in hiddenSuperClasses) {
            for (hiddenInterface in hiddenSuperClass.interfaceTypes()) {
                val hiddenInterfaceClass = hiddenInterface.asClass()
                if (filterReference.test(hiddenInterfaceClass ?: continue)) {
                    if (interfaceTypes == null) {
                        interfaceTypes = cls.interfaceTypes().toMutableList()
                        interfaceTypeClasses =
                            interfaceTypes.mapNotNull { it.asClass() }.toMutableList()
                        if (cls.isInterface()) {
                            cls.superClass()?.let { interfaceTypeClasses.add(it) }
                        }
                        cls.setInterfaceTypes(interfaceTypes)
                    }
                    if (interfaceTypeClasses!!.any { it == hiddenInterfaceClass }) {
                        continue
                    }

                    interfaceTypeClasses.add(hiddenInterfaceClass)

                    if (hiddenInterfaceClass.hasTypeVariables()) {
                        val mapping = cls.mapTypeVariables(hiddenSuperClass)
                        if (mapping.isNotEmpty()) {
                            val mappedType = hiddenInterface.convertType(mapping)
                            interfaceTypes.add(mappedType)
                            continue
                        }
                    }

                    interfaceTypes.add(hiddenInterface)
                }
            }
        }
    }

    private fun addInheritedStubsFrom(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
        superClasses: Sequence<ClassItem>,
        filterEmit: Predicate<Item>,
        filterReference: Predicate<Item>
    ) {
        // Also generate stubs for any methods we would have inherited from abstract parents
        // All methods from super classes that (1) aren't overridden in this class already, and
        // (2) are overriding some method that is in a public interface accessible from this class.
        val interfaces: Set<TypeItem> = cls.allInterfaceTypes(filterReference).toSet()

        // Note that we can't just call method.superMethods() to and see whether any of their
        // containing classes are among our target APIs because it's possible that the super class
        // doesn't actually implement the interface, but still provides a matching signature for the
        // interface. Instead, we'll look through all of our interface methods and look for
        // potential overrides.
        val inheritableMethods = MethodItemSet()
        for (interfaceType in interfaces) {
            val interfaceClass = interfaceType.asClass() ?: continue
            for (method in interfaceClass.methods()) {
                inheritableMethods.add(method)
            }
        }

        // Also add in any abstract methods from public super classes
        val publicSuperClasses =
            superClasses.filter { filterEmit.test(it) && !it.isJavaLangObject() }
        for (superClass in publicSuperClasses) {
            for (method in superClass.methods()) {
                if (!method.modifiers.isAbstract() || !method.modifiers.isPublicOrProtected()) {
                    continue
                }
                inheritableMethods.add(method)
            }
        }

        // Also add in any concrete public methods from hidden super classes
        for (superClass in hiddenSuperClasses) {
            // Determine if there is a non-hidden class between the superClass and this class.
            // If non-hidden classes are found, don't include the methods for this hiddenSuperClass,
            // as it will already have been included in a previous super class
            val includeHiddenSuperClassMethods =
                !cls.allSuperClasses()
                    // Search from this class up to, but not including the superClass.
                    .takeWhile { currentClass -> currentClass != superClass }
                    // Find any class that is not hidden.
                    .any { currentClass -> !hiddenSuperClasses.contains(currentClass) }

            if (!includeHiddenSuperClassMethods) {
                continue
            }

            for (method in superClass.methods()) {
                if (method.modifiers.isAbstract() || !method.modifiers.isPublic()) {
                    continue
                }

                if (method.hasHiddenType(filterReference)) {
                    continue
                }

                inheritableMethods.add(method)
            }
        }

        // Find all methods that are inherited from these classes into our class (making sure that
        // we don't have duplicates, e.g. a method defined by one inherited class and then
        // overridden by another closer one). map from method name to super methods overriding our
        // interfaces
        val inheritedMethods = MethodItemSet()

        for (superClass in hiddenSuperClasses) {
            for (method in superClass.methods()) {
                val modifiers = method.modifiers
                if (!modifiers.isPrivate() && !modifiers.isAbstract()) {
                    if (inheritableMethods.containsMatchingMethod(method)) {
                        inheritedMethods.add(method)
                    }
                }
            }
        }

        // Remove any methods that are overriding any of our existing methods
        for (method in cls.methods()) {
            inheritedMethods.removeMatchingMethods(method)
        }

        // Next remove any overrides among the remaining super methods (e.g. one method from a
        // hidden parent is overriding another method from a more distant hidden parent).
        inheritedMethods.values.forEach { methods ->
            if (methods.size >= 2) {
                for (candidate in ArrayList(methods)) {
                    for (superMethod in candidate.allSuperMethods()) {
                        methods.remove(superMethod)
                    }
                }
            }
        }

        // Add all the existing methods in the class to the set of existing methods.
        val existingMethods = MethodItemSet()
        for (method in cls.methods()) {
            existingMethods.add(method)
        }

        // We're now left with concrete methods in hidden parents that are implementing methods in
        // public interfaces that are listed in this class. Create stubs for them:
        inheritedMethods.values.flatten().forEach {
            // Copy the method from the hidden class that is not part of the API into the class that
            // is part of the API.
            val method = it.duplicate(cls)
            /* Insert comment marker: This is useful for debugging purposes but doesn't
               belong in the stub
            method.documentation = "// Inlined stub from hidden parent class ${it.containingClass().qualifiedName()}\n" +
                    method.documentation
             */

            // If we already have an override of this method, do not add it to the methods list
            if (existingMethods.containsMatchingMethod(method)) {
                return@forEach
            }

            cls.addMethod(method)
        }
    }

    /** Apply package filters listed in [Options.skipEmitPackages] */
    private fun skipEmitPackages() {
        for (pkgName in config.skipEmitPackages) {
            val pkg = codebase.findPackage(pkgName) ?: continue
            pkg.emit = false
        }
    }

    /** If a file facade class has no public members, don't add it to the api */
    private fun hideEmptyKotlinFileFacadeClasses() {
        codebase.getPackages().allClasses().forEach { cls ->
            if (
                cls.isFileFacade() &&
                    // a facade class needs to be emitted if it has any top-level fun/prop to emit
                    cls.members().none { member ->
                        // a member needs to be emitted if
                        //  1) it doesn't have a hide annotation;
                        //  2) it is either public or has a show annotation;
                        //  3) it is not `expect`
                        !member.hasHideAnnotation() &&
                            (member.isPublic || member.hasShowAnnotation()) &&
                            !member.modifiers.isExpect()
                    }
            ) {
                cls.emit = false
            }
        }
    }

    /**
     * Merge in external qualifier annotations (i.e. ones intended to be included in the API written
     * from all configured sources).
     */
    fun mergeExternalQualifierAnnotations() {
        val mergeQualifierAnnotations = config.mergeQualifierAnnotations
        if (mergeQualifierAnnotations.isNotEmpty()) {
            AnnotationsMerger(sourceParser, codebase, reporter)
                .mergeQualifierAnnotationsFromFiles(mergeQualifierAnnotations)
        }
    }

    /** Merge in external show/hide annotations from all configured sources */
    fun mergeExternalInclusionAnnotations() {
        val mergeInclusionAnnotations = config.mergeInclusionAnnotations
        if (mergeInclusionAnnotations.isNotEmpty()) {
            AnnotationsMerger(sourceParser, codebase, reporter)
                .mergeInclusionAnnotationsFromFiles(mergeInclusionAnnotations)
        }
    }

    /**
     * Propagate the hidden flag down into individual elements -- if a class is hidden, then the
     * methods and fields are hidden etc
     */
    private fun propagateHiddenRemovedAndDocOnly() {
        // Create a visitor to propagate hidden and docOnly from the containing package onto the top
        // level classes and then propagate them, and removed status, down onto the nested classes
        // and members.
        val visitor =
            object : BaseItemVisitor(preserveClassNesting = true) {

                override fun visitPackage(pkg: PackageItem) {
                    pkg.variantSelectors.inheritInto()
                }

                override fun visitClass(cls: ClassItem) {
                    cls.variantSelectors.inheritInto()
                }

                override fun visitCallable(callable: CallableItem) {
                    callable.variantSelectors.inheritInto()
                }

                override fun visitField(field: FieldItem) {
                    field.variantSelectors.inheritInto()
                }
            }

        codebase.accept(visitor)
    }

    private fun checkSystemPermissions(method: MethodItem) {
        val annotation = method.modifiers.findAnnotation(ANDROIDX_REQUIRES_PERMISSION)
        var hasAnnotation = false

        if (annotation != null) {
            hasAnnotation = true
            for (attribute in annotation.attributes) {
                var values: List<AnnotationAttributeValue>? = null
                var any = false
                when (attribute.name) {
                    "value",
                    "allOf" -> {
                        values = attribute.leafValues()
                    }
                    "anyOf" -> {
                        any = true
                        values = attribute.leafValues()
                    }
                }

                values ?: continue

                val system = ArrayList<String>()
                val nonSystem = ArrayList<String>()
                val missing = ArrayList<String>()
                for (value in values) {
                    val perm = (value.value() ?: value.toSource()).toString()
                    val level = config.manifest.getPermissionLevel(perm)
                    if (level == null) {
                        if (any) {
                            missing.add(perm)
                            continue
                        }

                        reporter.report(
                            Issues.REQUIRES_PERMISSION,
                            method,
                            "Permission '$perm' is not defined by manifest ${config.manifest}."
                        )
                        continue
                    }
                    if (
                        level.contains("normal") ||
                            level.contains("dangerous") ||
                            level.contains("ephemeral")
                    ) {
                        nonSystem.add(perm)
                    } else {
                        system.add(perm)
                    }
                }
                if (any && missing.size == values.size) {
                    reporter.report(
                        Issues.REQUIRES_PERMISSION,
                        method,
                        "None of the permissions ${missing.joinToString()} are defined by manifest " +
                            "${config.manifest}."
                    )
                }

                if (system.isEmpty() && nonSystem.isEmpty()) {
                    hasAnnotation = false
                } else if (any && nonSystem.isNotEmpty() || !any && system.isEmpty()) {
                    reporter.report(
                        Issues.REQUIRES_PERMISSION,
                        method,
                        "Method '" +
                            method.name() +
                            "' must be protected with a system permission; it currently" +
                            " allows non-system callers holding " +
                            nonSystem.toString()
                    )
                }
            }
        }

        if (!hasAnnotation) {
            reporter.report(
                Issues.REQUIRES_PERMISSION,
                method,
                "Method '" + method.name() + "' must be protected with a system permission."
            )
        }
    }

    fun performChecks() {
        if (codebase.trustedApi()) {
            // The codebase is already an API; no consistency checks to be performed
            return
        }

        val checkSystemApi =
            !reporter.isSuppressed(Issues.REQUIRES_PERMISSION) &&
                config.allShowAnnotations.matches(ANDROID_SYSTEM_API) &&
                !config.manifest.isEmpty()
        val checkHiddenShowAnnotations =
            !reporter.isSuppressed(Issues.UNHIDDEN_SYSTEM_API) &&
                config.allShowAnnotations.isNotEmpty()

        codebase.accept(
            object :
                ApiVisitor(
                    config = @Suppress("DEPRECATION") options.apiVisitorConfig,
                ) {
                override fun visitParameter(parameter: ParameterItem) {
                    checkTypeReferencesHidden(parameter, parameter.type())
                }

                override fun visitItem(item: Item) {
                    // None of the checks in this apply to [ParameterItem]. The deprecation checks
                    // do not apply as there is no way to provide an `@deprecation` tag in Javadoc
                    // for parameters. The unhidden showability annotation check
                    // ('UnhiddemSystemApi`) does not apply as you cannot annotation a
                    // [ParameterItem] with a showability annotation.
                    if (item is ParameterItem) return

                    if (
                        item.originallyDeprecated &&
                            !item.documentationContainsDeprecated() &&
                            // Don't warn about this in Kotlin; the Kotlin deprecation annotation
                            // includes deprecation
                            // messages (unlike java.lang.Deprecated which has no attributes).
                            // Instead, these
                            // are added to the documentation by the [DocAnalyzer].
                            !item.isKotlin() &&
                            // @DeprecatedForSdk will show up as an alias for @Deprecated, but it's
                            // correct
                            // and expected to *not* combine this with @deprecated in the text;
                            // here,
                            // the text comes from an annotation attribute.
                            item.modifiers.isAnnotatedWith(JAVA_LANG_DEPRECATED)
                    ) {
                        reporter.report(
                            Issues.DEPRECATION_MISMATCH,
                            item,
                            "${item.toString().capitalize()}: @Deprecated annotation (present) and @deprecated doc tag (not present) do not match"
                        )
                        // TODO: Check opposite (doc tag but no annotation)
                    } else {
                        val deprecatedForSdk =
                            item.modifiers.findAnnotation(ANDROID_DEPRECATED_FOR_SDK)
                        if (deprecatedForSdk != null) {
                            if (item.documentation.contains("@deprecated")) {
                                reporter.report(
                                    Issues.DEPRECATION_MISMATCH,
                                    item,
                                    "${item.toString().capitalize()}: Documentation contains `@deprecated` which implies this API is fully deprecated, not just @DeprecatedForSdk"
                                )
                            } else {
                                val value = deprecatedForSdk.findAttribute(ANNOTATION_ATTR_VALUE)
                                val message = value?.value?.value()?.toString() ?: ""
                                item.appendDocumentation(message, "@deprecated")
                            }
                        }
                    }

                    if (
                        checkHiddenShowAnnotations &&
                            item.hasShowAnnotation() &&
                            !item.originallyHidden &&
                            !item.showability.showNonRecursive()
                    ) {
                        item.modifiers
                            .annotations()
                            // Find the first show annotation. Just because item.hasShowAnnotation()
                            // is true does not mean that there must be one show annotation as a
                            // revert annotation could be treated as a show annotation on one item
                            // and a hide annotation on another but is neither a show or hide
                            // annotation.
                            .firstOrNull(AnnotationItem::isShowAnnotation)
                            // All show annotations must have a non-null string otherwise they
                            // would not have been matched.
                            ?.qualifiedName
                            ?.removePrefix(ANDROID_ANNOTATION_PREFIX)
                            ?.let { annotationName ->
                                reporter.report(
                                    Issues.UNHIDDEN_SYSTEM_API,
                                    item,
                                    "@$annotationName APIs must also be marked @hide: ${item.describe()}"
                                )
                            }
                    }
                }

                override fun visitClass(cls: ClassItem) {
                    if (checkSystemApi) {
                        // Look for Android @SystemApi exposed outside the normal SDK; we require
                        // that they're protected with a system permission.
                        // Also flag @SystemApi apis not annotated with @hide.

                        // This class is a system service if it's annotated with @SystemService,
                        // or if it's android.content.pm.PackageManager
                        if (
                            cls.modifiers.isAnnotatedWith("android.annotation.SystemService") ||
                                cls.qualifiedName() == "android.content.pm.PackageManager"
                        ) {
                            // Check permissions on system services
                            for (method in cls.filteredMethods(filterEmit)) {
                                checkSystemPermissions(method)
                            }
                        }
                    }
                }

                override fun visitField(field: FieldItem) {
                    checkTypeReferencesHidden(field, field.type())
                }

                override fun visitProperty(property: PropertyItem) {
                    checkTypeReferencesHidden(property, property.type())
                }

                override fun visitMethod(method: MethodItem) {
                    checkTypeReferencesHidden(
                        method,
                        method.returnType()
                    ) // returnType is nullable only for constructors
                }

                /** Check that the type doesn't refer to any hidden classes. */
                private fun checkTypeReferencesHidden(item: Item, type: TypeItem) {
                    type.accept(
                        object : BaseTypeVisitor() {
                            override fun visitClassType(classType: ClassTypeItem) {
                                val cls = classType.asClass() ?: return
                                if (
                                    !filterReference.test(cls) &&
                                        cls.origin != ClassOrigin.CLASS_PATH
                                ) {
                                    reporter.report(
                                        Issues.HIDDEN_TYPE_PARAMETER,
                                        item,
                                        "${item.toString().capitalize()} references hidden type $classType."
                                    )
                                }
                            }
                        }
                    )
                }
            }
        )
    }

    // TODO: Switch to visitor iteration
    fun handleStripping() {
        val notStrippable = HashSet<ClassItem>(5000)

        val filter = ApiPredicate(config = config.apiPredicateConfig.copy(ignoreShown = true))

        // If a class is public or protected, not hidden, not imported and marked as included,
        // then we can't strip it
        val allTopLevelClasses = codebase.getPackages().allTopLevelClasses().toList()
        allTopLevelClasses
            .filter { it.isApiCandidate() && it.emit && !it.hidden() }
            .forEach { cantStripThis(it, filter, notStrippable, it, "self") }

        // complain about anything that looks includeable but is not supposed to
        // be written, e.g. hidden things
        for (cl in notStrippable) {
            if (!cl.isHiddenOrRemoved()) {
                val publiclyConstructable =
                    !cl.modifiers.isSealed() && cl.constructors().any { it.isApiCandidate() }
                for (m in cl.methods()) {
                    if (!m.isApiCandidate()) {
                        if (publiclyConstructable && m.modifiers.isAbstract()) {
                            reporter.report(
                                Issues.HIDDEN_ABSTRACT_METHOD,
                                m,
                                "${m.name()} cannot be hidden and abstract when " +
                                    "${cl.simpleName()} has a visible constructor, in case a " +
                                    "third-party attempts to subclass it."
                            )
                        }
                        continue
                    }
                    if (m.isHiddenOrRemoved()) {
                        reporter.report(
                            Issues.UNAVAILABLE_SYMBOL,
                            m,
                            "Reference to unavailable method " + m.name()
                        )
                    } else if (m.originallyDeprecated) {
                        // don't bother reporting deprecated methods unless they are public and
                        // explicitly marked as deprecated.
                        reporter.report(
                            Issues.DEPRECATED,
                            m,
                            "Method " + cl.qualifiedName() + "." + m.name() + " is deprecated"
                        )
                    }

                    checkTypeReferencesHiddenOrDeprecated(m.returnType(), m, cl, "Return type")
                    for (p in m.parameters()) {
                        checkTypeReferencesHiddenOrDeprecated(p.type(), m, cl, "Parameter")
                    }
                }

                if (!cl.effectivelyDeprecated) {
                    val s = cl.superClass()
                    if (s?.effectivelyDeprecated == true) {
                        reporter.report(
                            Issues.EXTENDS_DEPRECATED,
                            cl,
                            "Extending deprecated super class $s from ${cl.qualifiedName()}: this class should also be deprecated"
                        )
                    }

                    for (t in cl.interfaceTypes()) {
                        if (t.asClass()?.effectivelyDeprecated == true) {
                            reporter.report(
                                Issues.EXTENDS_DEPRECATED,
                                cl,
                                "Implementing interface of deprecated type $t in ${cl.qualifiedName()}: this class should also be deprecated"
                            )
                        }
                    }
                }
            } else if (cl.originallyDeprecated) {
                // not hidden, but deprecated
                reporter.report(Issues.DEPRECATED, cl, "Class ${cl.qualifiedName()} is deprecated")
            }
        }
    }

    private fun cantStripThis(
        cl: ClassItem,
        filter: Predicate<Item>,
        notStrippable: MutableSet<ClassItem>,
        from: Item,
        usage: String
    ) {
        if (cl.origin == ClassOrigin.CLASS_PATH) {
            return
        }

        if (cl.isHiddenOrRemoved() || cl.isPackagePrivate && !cl.isApiCandidate()) {
            reporter.report(
                Issues.REFERENCES_HIDDEN,
                from,
                "Class ${cl.qualifiedName()} is ${if (cl.isHiddenOrRemoved()) "hidden" else "not public"} but was referenced ($usage) from public ${from.describe(
                    false
                )}"
            )
        }

        if (!notStrippable.add(cl)) {
            // slight optimization: if it already contains cl, it already contains
            // all of cl's parents
            return
        }

        // cant strip any public fields or their generics
        for (field in cl.fields()) {
            if (!filter.test(field)) {
                continue
            }
            cantStripThis(field.type(), field, filter, notStrippable, "in field type")
        }
        // cant strip any of the type's generics
        cantStripThis(cl.typeParameterList, filter, notStrippable, cl)
        // cant strip any of the annotation elements
        // cantStripThis(cl.annotationElements(), notStrippable);
        // take care of methods
        cantStripThis(cl.methods(), filter, notStrippable)
        cantStripThis(cl.constructors(), filter, notStrippable)
        // blow the outer class open if this is an inner class
        val containingClass = cl.containingClass()
        if (containingClass != null) {
            cantStripThis(containingClass, filter, notStrippable, cl, "as containing class")
        }
        // all visible inner classes will be included in stubs
        cl.nestedClasses()
            .filter { it.isApiCandidate() }
            .forEach { cantStripThis(it, filter, notStrippable, cl, "as nested class") }
        // blow open super class and interfaces
        // TODO: Consider using val superClass = cl.filteredSuperclass(filter)
        val superItems = cl.allInterfaces().toMutableSet()
        cl.superClass()?.let { superClass -> superItems.add(superClass) }

        for (superItem in superItems) {
            // allInterfaces includes cl itself if cl is an interface
            if (superItem.isHiddenOrRemoved() && superItem != cl) {
                // cl is a public class declared as extending a hidden superclass.
                // this is not a desired practice, but it's happened, so we deal
                // with it by finding the first super class which passes checkLevel for purposes of
                // generating the doc & stub information, and proceeding normally.
                if (superItem.origin != ClassOrigin.CLASS_PATH) {
                    reporter.report(
                        Issues.HIDDEN_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " stripped of unavailable superclass " +
                            superItem.qualifiedName()
                    )
                }
            } else {
                // doclava would also mark the package private super classes as unhidden, but that's
                // not
                // right (this was just done for its stub handling)
                //   cantStripThis(superClass, filter, notStrippable, stubImportPackages, cl, "as
                // super class")

                if (superItem.isPrivate && superItem.origin != ClassOrigin.CLASS_PATH) {
                    reporter.report(
                        Issues.PRIVATE_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " extends private class " +
                            superItem.qualifiedName()
                    )
                }
            }
        }
    }

    private fun cantStripThis(
        callables: List<CallableItem>,
        filter: Predicate<Item>,
        notStrippable: MutableSet<ClassItem>,
    ) {
        // for each callable, blow open the parameters, throws and return types. also blow open
        // their generics
        for (callable in callables) {
            if (!filter.test(callable)) {
                continue
            }
            cantStripThis(callable.typeParameterList, filter, notStrippable, callable)
            for (parameter in callable.parameters()) {
                cantStripThis(
                    parameter.type(),
                    parameter,
                    filter,
                    notStrippable,
                    "in parameter type"
                )
            }
            for (thrown in callable.throwsTypes()) {
                if (thrown is VariableTypeItem) continue
                val classItem = thrown.erasedClass ?: continue
                cantStripThis(classItem, filter, notStrippable, callable, "as exception")
            }
            cantStripThis(callable.returnType(), callable, filter, notStrippable, "in return type")
        }
    }

    private fun cantStripThis(
        typeParameterList: TypeParameterList,
        filter: Predicate<Item>,
        notStrippable: MutableSet<ClassItem>,
        context: Item
    ) {
        for (typeParameter in typeParameterList) {
            for (bound in typeParameter.typeBounds()) {
                cantStripThis(bound, context, filter, notStrippable, "as type parameter")
            }
        }
    }

    private fun cantStripThis(
        type: TypeItem,
        context: Item,
        filter: Predicate<Item>,
        notStrippable: MutableSet<ClassItem>,
        usage: String,
    ) {
        type.accept(
            object : BaseTypeVisitor() {
                override fun visitClassType(classType: ClassTypeItem) {
                    val asClass = classType.asClass() ?: return
                    cantStripThis(asClass, filter, notStrippable, context, usage)
                }
            }
        )
    }

    /**
     * Checks if the type (method parameter or return type) references a hidden or deprecated class.
     */
    private fun checkTypeReferencesHiddenOrDeprecated(
        type: TypeItem,
        containingMethod: MethodItem,
        containingClass: ClassItem,
        usage: String
    ) {
        if (!containingMethod.effectivelyDeprecated) {
            type.accept(
                object : BaseTypeVisitor() {
                    override fun visitClassType(classType: ClassTypeItem) {
                        if (classType.asClass()?.effectivelyDeprecated == true) {
                            reporter.report(
                                Issues.REFERENCES_DEPRECATED,
                                containingMethod,
                                "$usage references deprecated type $classType in ${containingClass.qualifiedName()}.${containingMethod.name()}(): this method should also be deprecated"
                            )
                        }
                    }
                }
            )
        }

        val hiddenClasses = findHiddenClasses(type)
        val typeClassName = (type as? ClassTypeItem)?.qualifiedName
        for (hiddenClass in hiddenClasses) {
            if (hiddenClass.origin == ClassOrigin.CLASS_PATH) continue
            if (hiddenClass.qualifiedName() == typeClassName) {
                // The type itself is hidden
                reporter.report(
                    Issues.UNAVAILABLE_SYMBOL,
                    containingMethod,
                    "$usage of unavailable type $type in ${containingClass.qualifiedName()}.${containingMethod.name()}()"
                )
            } else {
                // The type contains a hidden type
                reporter.report(
                    Issues.HIDDEN_TYPE_PARAMETER,
                    containingMethod,
                    "$usage uses type parameter of unavailable type $type in ${containingClass.qualifiedName()}.${containingMethod.name()}()"
                )
            }
        }
    }

    /**
     * Find references to hidden classes.
     *
     * This finds hidden classes that are used by public parts of the API in order to ensure the API
     * is self-consistent and does not reference classes that are not included in the stubs. Any
     * such references cause an error to be reported.
     *
     * A reference to an imported class is not treated as an error, even though imported classes are
     * hidden from the stub generation. That is because imported classes are, by definition,
     * excluded from the set of classes for which stubs are required.
     *
     * @param ti the type information to examine for references to hidden classes.
     * @return all references to hidden classes referenced by the type
     */
    private fun findHiddenClasses(ti: TypeItem): Set<ClassItem> {
        val hiddenClasses = mutableSetOf<ClassItem>()
        ti.accept(
            object : BaseTypeVisitor() {
                override fun visitClassType(classType: ClassTypeItem) {
                    val asClass = classType.asClass() ?: return
                    if (asClass.isHiddenOrRemoved()) {
                        hiddenClasses.add(asClass)
                    }
                }
            }
        )
        return hiddenClasses
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }
}

/** Returns true if this item is public or protected and so a candidate for inclusion in an API. */
private fun SelectableItem.isApiCandidate(): Boolean {
    return !isHiddenOrRemoved() && (modifiers.isPublic() || modifiers.isProtected())
}

/**
 * Whether documentation for the [Item] has the `@deprecated` tag -- for inherited methods, this
 * also looks at any inherited documentation.
 */
private fun Item.documentationContainsDeprecated(): Boolean {
    val text = documentation.text
    if (text.contains("@deprecated")) return true
    if (this is MethodItem && (text == "" || text.contains("@inheritDoc"))) {
        return superMethods().any { it.documentationContainsDeprecated() }
    }
    return false
}

/**
 * A set of [MethodItem]s.
 *
 * This is implemented as a [MutableMap] from the [MethodItem.name] to the list of [MethodItem]s
 * with that name.
 */
private typealias MethodItemSet = HashMap<String, MutableList<MethodItem>>

/**
 * Add a method to the set.
 *
 * This does not check to see if the [MethodItem] exists already so it is possible that it will
 * contain duplicate methods.
 */
private fun MethodItemSet.add(method: MethodItem) {
    val name = method.name()
    val list = computeIfAbsent(name) { mutableListOf() }
    list.add(method)
}

/**
 * Check to see whether the set contains a method that matches [method] as determined by
 * [MethodItem.matches].
 */
private fun MethodItemSet.containsMatchingMethod(method: MethodItem): Boolean {
    val name = method.name()
    val list = this[name] ?: return false
    for (existing in list) {
        if (method.matches(existing)) {
            return true
        }
    }
    return false
}

/** Remove any method that matches [method] as determined by [MethodItem.matches]. */
private fun MethodItemSet.removeMatchingMethods(method: MethodItem) {
    val name = method.name()
    val list = this[name] ?: return
    val iterator = list.listIterator()
    while (iterator.hasNext()) {
        val existing = iterator.next()
        if (method.matches(existing)) {
            iterator.remove()
        }
    }
}
