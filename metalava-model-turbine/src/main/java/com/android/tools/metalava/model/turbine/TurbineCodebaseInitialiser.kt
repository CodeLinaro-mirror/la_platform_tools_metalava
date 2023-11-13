/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.turbine

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass.FieldInfo
import com.google.turbine.binder.bytecode.BytecodeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.diag.TurbineLog
import com.google.turbine.tree.Tree.CompUnit
import java.io.File
import java.util.Optional
import javax.lang.model.SourceVersion

/**
 * This initializer acts as adaptor between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
open class TurbineCodebaseInitialiser(
    val units: List<CompUnit>,
    val codebase: TurbineBasedCodebase,
    val classpath: List<File>,
) {
    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /** Map between ClassSymbols and TurbineClass for classes present in source */
    private lateinit var sourceClassMap: ImmutableMap<ClassSymbol, SourceTypeBoundClass>

    /** Map between ClassSymbols and TurbineClass for classes present in classPath */
    private lateinit var envClassMap: CompoundEnv<ClassSymbol, BytecodeBoundClass>

    /**
     * Binds the units with the help of Turbine's binder.
     *
     * Then creates the packages, classes and their members, as well as sets up various class
     * hierarchies using the binder's output
     */
    fun initialize() {
        codebase.initialize()

        // Bind the units
        try {
            val procInfo =
                ProcessorInfo.create(
                    ImmutableList.of(),
                    null,
                    ImmutableMap.of(),
                    SourceVersion.latest()
                )

            // Any non-fatal error (like unresolved symbols) will be captured in this log and will
            // be ignored.
            val log = TurbineLog()

            bindingResult =
                Binder.bind(
                    log,
                    ImmutableList.copyOf(units),
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    procInfo,
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
            sourceClassMap = bindingResult.units()
            envClassMap = bindingResult.classPathEnv()
        } catch (e: Throwable) {
            throw e
        }

        createAllPackages()
        createAllClasses()
    }

    private fun createAllPackages() {
        // Root package
        findOrCreatePackage("")

        for (unit in units) {
            val optPkg = unit.pkg()
            val pkg = if (optPkg.isPresent()) optPkg.get() else null
            var pkgName = ""
            if (pkg != null) {
                val pkgNameList = pkg.name().map { it.value() }
                pkgName = pkgNameList.joinToString(separator = ".")
            }
            findOrCreatePackage(pkgName)
        }
    }

    /**
     * Searches for the package with supplied name in the codebase's package map and if not found
     * creates the corresponding TurbinePackageItem and adds it to the package map.
     */
    private fun findOrCreatePackage(name: String): TurbinePackageItem {
        val pkgItem = codebase.findPackage(name)
        if (pkgItem != null) {
            return pkgItem as TurbinePackageItem
        } else {
            val modifers = TurbineModifierItem.create(codebase, 0)
            val turbinePkgItem = TurbinePackageItem.create(codebase, name, modifers)
            codebase.addPackage(turbinePkgItem)
            return turbinePkgItem
        }
    }

    private fun createAllClasses() {
        val classes = sourceClassMap.keys
        for (cls in classes) {

            // Turbine considers package-info as class and creates one for empty packages which is
            // not consistent with Psi
            if (cls.simpleName() == "package-info") {
                continue
            }

            findOrCreateClass(cls)
        }
    }

    /** Creates a class if not already present in codebase's classmap */
    private fun findOrCreateClass(sym: ClassSymbol): TurbineClassItem {
        val className = sym.binaryName().replace('/', '.').replace('$', '.')
        var classItem = codebase.findClass(className)

        if (classItem == null) {
            classItem = createClass(sym)
        }

        return classItem
    }

    private fun createClass(sym: ClassSymbol): TurbineClassItem {

        var cls: TypeBoundClass? = sourceClassMap[sym]
        cls = if (cls != null) cls else envClassMap.get(sym)!!

        // Get the package item
        val pkgName = sym.packageName().replace('/', '.')
        val pkgItem = findOrCreatePackage(pkgName)

        // Create class
        val qualifiedName = sym.binaryName().replace('/', '.').replace('$', '.')
        val simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        val fullName = sym.simpleName().replace('$', '.')
        val modifierItem = TurbineModifierItem.create(codebase, cls.access())
        val classItem =
            TurbineClassItem(
                codebase,
                simpleName,
                fullName,
                qualifiedName,
                modifierItem,
                TurbineClassType.getClassType(cls.kind()),
            )

        // Setup the SuperClass
        val superClassItem = cls.superclass()?.let { superClass -> findOrCreateClass(superClass) }
        classItem.setSuperClass(superClassItem, null)

        // Setup InnerClasses
        val t = cls.children()
        setInnerClasses(classItem, t.values.asList())

        // Set direct interfaces
        classItem.directInterfaces = cls.interfaces().map { itf -> findOrCreateClass(itf) }

        // Create fields
        createFields(classItem, cls.fields())

        // Add to the codebase
        val isTopClass = cls.owner() == null
        codebase.addClass(classItem, isTopClass)

        // Add the class to corresponding PackageItem
        if (isTopClass) {
            classItem.containingPackage = pkgItem
            pkgItem.addTopClass(classItem)
        }

        return classItem
    }

    /** This method sets up inner class hierarchy. */
    private fun setInnerClasses(
        classItem: TurbineClassItem,
        innerClasses: ImmutableList<ClassSymbol>
    ) {
        classItem.innerClasses =
            innerClasses.map { cls ->
                val innerClassItem = findOrCreateClass(cls)
                innerClassItem.containingClass = classItem
                innerClassItem
            }
    }

    /** This methods creates and sets the fields of a class */
    private fun createFields(classItem: TurbineClassItem, fields: ImmutableList<FieldInfo>) {
        classItem.fields =
            fields.map { field ->
                val fieldModifierItem = TurbineModifierItem.create(codebase, field.access())
                TurbineFieldItem(
                    codebase,
                    field.name(),
                    classItem,
                    fieldModifierItem,
                )
            }
    }
}
