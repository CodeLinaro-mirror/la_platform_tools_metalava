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

import com.android.tools.metalava.model.DefaultModifierList
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Kind.TY_DECL
import com.google.turbine.tree.Tree.TyDecl

/**
 * This initializer acts as adaptor between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
open class TurbineCodebaseInitialiser(
    val units: List<CompUnit>,
    val codebase: TurbineBasedCodebase
) {

    /** Map items (classitems, methoditems, etc.) with their qualified name. */
    private var itemMap: MutableMap<Tree, String> = mutableMapOf<Tree, String>()

    /**
     * Initialize uses two passes thorugh units. One pass is for creating all the items and the
     * other pass to set up all the hierarchy.
     */
    fun initialize() {
        codebase.initialize()
        for (unit in units) {
            createItems(unit)
        }
        for (unit in units) {
            setHierarchy(unit)
        }
    }

    /** Extracts data from the compilation units. A unit corresponds to one parsed source file. */
    fun createItems(unit: CompUnit) {
        val optPkg = unit.pkg()
        val pkg = if (optPkg.isPresent()) optPkg.get() else null
        var pkgName = ""
        if (pkg != null) {
            val pkgNameList = pkg.name().map { it.value() }
            pkgName = pkgNameList.joinToString(separator = ".")
        }
        val typeDecls = unit.decls()
        for (typeDecl in typeDecls) {
            populateClass(typeDecl, pkgName, null, true)
        }
    }

    /** Creates a TurbineClassItem and adds the classitem to the various maps in codebase. */
    fun populateClass(
        typeDecl: TyDecl,
        pkgName: String,
        containingClass: TurbineClassItem?,
        isTopClass: Boolean,
    ) {
        val className = typeDecl.name().value()
        val fullName = if (isTopClass) className else containingClass?.fullName() + "." + className
        val qualifiedName = pkgName + "." + fullName
        val modifers = DefaultModifierList(codebase)
        val classItem =
            TurbineClassItem(
                codebase,
                className,
                fullName,
                qualifiedName,
                containingClass,
                modifers,
            )

        val members = typeDecl.members()
        for (member in members) {
            when (member.kind()) {
                // A class or an interface declaration
                TY_DECL -> {
                    populateClass(member as TyDecl, pkgName, classItem, false)
                }
                else -> {
                    // Do nothing for now
                }
            }
        }
        codebase.addClass(classItem, isTopClass)
        itemMap.put(typeDecl, qualifiedName)
    }

    /**
     * This method aims to set up the hierarchy of classes and methods. This includes setting
     * superclasses, implements , innerclasses, method overrides, etc.
     */
    fun setHierarchy(unit: CompUnit) {
        val typeDecls = unit.decls()
        for (typeDecl in typeDecls) {
            setClassHierarchy(typeDecl)
        }
    }

    fun setClassHierarchy(typeDecl: TyDecl) {
        val className = itemMap[typeDecl]!!
        val classItem = codebase.findClass(className)!!
        val innerClasses = mutableListOf<TurbineClassItem>()

        for (member in typeDecl.members()) {
            when (member.kind()) {
                // A class or an interface declaration
                TY_DECL -> {
                    val memberName = itemMap[member]!!
                    val memberItem = codebase.findClass(memberName)!!
                    innerClasses.add(memberItem)
                }
                else -> {
                    // Do nothing for now
                }
            }
        }
        classItem.innerClasses = innerClasses
    }
}
