/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.TypeParameterList
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import kotlin.math.min

// Copy of ApiInfo in doclava1 (converted to Kotlin + some cleanup to make it work with metalava's
// data structures.
// (Converted to Kotlin such that I can inherit behavior via interfaces, in particular Codebase.)
class TextCodebase(
    location: File,
    annotationManager: AnnotationManager,
) : DefaultCodebase(location, "Codebase", true, annotationManager) {
    internal val mPackages = HashMap<String, TextPackageItem>(300)
    internal val mAllClasses = HashMap<String, TextClassItem>(30000)

    val externalClasses = HashMap<String, ClassItem>()

    override fun trustedApi(): Boolean = true

    override fun getPackages(): PackageList {
        val list = ArrayList<PackageItem>(mPackages.values)
        list.sortWith(PackageItem.comparator)
        return PackageList(this, list)
    }

    override fun size(): Int {
        return mPackages.size
    }

    override fun findClass(className: String): TextClassItem? {
        return mAllClasses[className]
    }

    override fun supportsDocumentation(): Boolean = false

    fun addPackage(pInfo: TextPackageItem) {
        // track the set of organized packages in the API
        mPackages[pInfo.name()] = pInfo

        // accumulate a direct map of all the classes in the API
        for (cl in pInfo.allClasses()) {
            mAllClasses[cl.qualifiedName()] = cl as TextClassItem
        }
    }

    fun registerClass(cls: TextClassItem) {
        mAllClasses[cls.qualifiedName] = cls
    }

    /**
     * Tries to find [name] in [mAllClasses]. If not found, then if a [classResolver] is provided it
     * will invoke that and return the [ClassItem] it returns if any. Otherwise, it will create an
     * empty stub class (or interface, if [isInterface] is true).
     *
     * Initializes outer classes and packages for the created class as needed.
     */
    fun getOrCreateClass(
        name: String,
        isInterface: Boolean = false,
        classResolver: ClassResolver? = null,
    ): ClassItem {
        val erased = TextTypeItem.eraseTypeArguments(name)
        val cls = mAllClasses[erased] ?: externalClasses[erased]
        if (cls != null) {
            return cls
        }

        if (classResolver != null) {
            val classItem = classResolver.resolveClass(erased)
            if (classItem != null) {
                // Save the class item, so it can be retrieved the next time this is loaded. This is
                // needed because otherwise TextTypeItem.asClass would not work properly.
                externalClasses[erased] = classItem
                return classItem
            }
        }

        val stubClass = TextClassItem.createStubClass(this, name, isInterface)
        mAllClasses[erased] = stubClass
        stubClass.emit = false

        val fullName = stubClass.fullName()
        if (fullName.contains('.')) {
            // We created a new inner class stub. We need to fully initialize it with outer classes,
            // themselves possibly stubs
            val outerName = erased.substring(0, erased.lastIndexOf('.'))
            // Pass classResolver = null, so it only looks in this codebase for the outer class.
            val outerClass = getOrCreateClass(outerName, isInterface = false, classResolver = null)

            // It makes no sense for a Foo to come from one codebase and Foo.Bar to come from
            // another.
            if (outerClass.codebase != stubClass.codebase) {
                throw IllegalStateException(
                    "Outer class $outerClass is from ${outerClass.codebase} but" +
                        " inner class $stubClass is from ${stubClass.codebase}"
                )
            }

            stubClass.containingClass = outerClass
            outerClass.addInnerClass(stubClass)
        } else {
            // Add to package
            val endIndex = erased.lastIndexOf('.')
            val pkgPath = if (endIndex != -1) erased.substring(0, endIndex) else ""
            val pkg =
                findPackage(pkgPath)
                    ?: run {
                        val newPkg =
                            TextPackageItem(
                                this,
                                pkgPath,
                                DefaultModifierList(this, DefaultModifierList.PUBLIC),
                                SourcePositionInfo.UNKNOWN
                            )
                        addPackage(newPkg)
                        newPkg.emit = false
                        newPkg
                    }
            stubClass.setContainingPackage(pkg)
            pkg.addClass(stubClass)
        }
        return stubClass
    }

    override fun findPackage(pkgName: String): TextPackageItem? {
        return mPackages[pkgName]
    }

    override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem {
        return DefaultAnnotationItem.create(this, source)
    }

    override fun toString(): String {
        return description
    }

    override fun unsupported(desc: String?): Nothing {
        error(desc ?: "Not supported for a signature-file based codebase")
    }

    fun obtainTypeFromString(
        type: String,
        cl: TextClassItem,
        methodTypeParameterList: TypeParameterList
    ): TextTypeItem {
        if (TextTypeItem.isLikelyTypeParameter(type)) {
            val length = type.length
            var nameEnd = length
            for (i in 0 until length) {
                val c = type[i]
                if (c == '<' || c == '[' || c == '!' || c == '?') {
                    nameEnd = i
                    break
                }
            }
            val name =
                if (nameEnd == length) {
                    type
                } else {
                    type.substring(0, nameEnd)
                }

            val isMethodTypeVar = methodTypeParameterList.typeParameterNames().contains(name)
            val isClassTypeVar = cl.typeParameterList().typeParameterNames().contains(name)

            if (isMethodTypeVar || isClassTypeVar) {
                // Confirm that it's a type variable
                // If so, create type variable WITHOUT placing it into the
                // cache, since we can't cache these; they can have different
                // inherited bounds etc
                return TextTypeItem(this, type)
            }
        }

        return obtainTypeFromString(type)
    }

    // Copied from Converter:

    fun obtainTypeFromString(type: String): TextTypeItem {
        return mTypesFromString.obtain(type) as TextTypeItem
    }

    private val mTypesFromString =
        object : Cache(this) {
            override fun make(o: Any): Any {
                val name = o as String

                // Reverse effect of TypeItem.shortenTypes(...)
                if (implicitJavaLangType(name)) {
                    return TextTypeItem(codebase, "java.lang.$name")
                }

                return TextTypeItem(codebase, name)
            }

            private fun implicitJavaLangType(s: String): Boolean {
                if (s.length <= 1) {
                    return false // Usually a type variable
                }
                if (s[1] == '[') {
                    return false // Type variable plus array
                }

                val dotIndex = s.indexOf('.')
                val array = s.indexOf('[')
                val generics = s.indexOf('<')
                if (array == -1 && generics == -1) {
                    return dotIndex == -1 && !TextTypeItem.isPrimitive(s)
                }
                val typeEnd =
                    if (array != -1) {
                        if (generics != -1) {
                            min(array, generics)
                        } else {
                            array
                        }
                    } else {
                        generics
                    }

                // Allow dotted type in generic parameter, e.g. "Iterable<java.io.File>" -> return
                // true
                return (dotIndex == -1 || dotIndex > typeEnd) &&
                    !TextTypeItem.isPrimitive(s.substring(0, typeEnd).trim())
            }
        }

    private abstract class Cache(val codebase: TextCodebase) {

        protected var mCache = HashMap<Any, Any>()

        internal fun obtain(o: Any?): Any? {
            if (o == null) {
                return null
            }
            var r: Any? = mCache[o]
            if (r == null) {
                r = make(o)
                mCache[o] = r
            }
            return r
        }

        protected abstract fun make(o: Any): Any
    }
}
