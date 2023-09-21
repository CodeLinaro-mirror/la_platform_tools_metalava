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
import com.google.turbine.tree.Tree.CompUnit
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

    fun initialize() {
        codebase.initialize()
        for (unit in units) {
            extractData(unit)
        }
    }

    /** Extracts data from the compilation units. A unit corresponds to one parsed source file. */
    fun extractData(unit: CompUnit) {
        val optPkg = unit.pkg()
        val pkg = if (optPkg.isPresent()) optPkg.get() else null
        var pkgName = ""
        if (pkg != null) {
            val pkgNameList = pkg.name().map { it.value() }
            pkgName = pkgNameList.joinToString(separator = ".")
        }
        val typeDecls = unit.decls()
        for (typeDecl in typeDecls) {
            populateClass(typeDecl, pkgName)
        }
    }

    /** Creates a TurbineClassItem and adds the classitem to the various maps in codebase */
    fun populateClass(typeDecl: TyDecl, pkgName: String) {
        val className = typeDecl.name().value()
        val qualifiedName = pkgName + "." + className
        val modifers = DefaultModifierList(codebase)
        val classItem = TurbineClassItem(codebase, className, qualifiedName, modifers)
        codebase.addTopClass(classItem)
    }
}
