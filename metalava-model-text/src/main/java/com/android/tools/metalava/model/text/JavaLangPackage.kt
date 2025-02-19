/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.JAVA_LANG_PACKAGE
import com.android.tools.metalava.model.JAVA_LANG_PREFIX

/**
 * Encapsulates information about the contents of the `java.lang` package obtained from a number of
 * different sources, e.g. a [ClassResolver] or Metalava's own classpath, depending on what is
 * available.
 */
abstract class JavaLangPackage {
    /** Cache for the result of [check]. */
    private val nameToBoolean = mutableMapOf<String, Boolean>()

    /**
     * Check to see if [name] exists.
     *
     * @param name the qualified name of a class in `java.lang` package and not its sub-packages.
     */
    fun containsQualified(name: String): Boolean {
        require(name.startsWith(JAVA_LANG_PREFIX)) { "'$name' must start with '$JAVA_LANG_PREFIX'" }
        require(name.lastIndexOf('.') == JAVA_LANG_PACKAGE.length) {
            "'$name' must not be in a sub-package of '$JAVA_LANG_PACKAGE'"
        }
        return nameToBoolean.computeIfAbsent(name, ::check)
    }

    protected abstract fun check(name: String): Boolean

    companion object {
        /**
         * A [JavaLangPackage] implementation that uses reflection to check the running process's
         * `java.lang` package.
         *
         * This may not be exactly the same as the `java.lang` package that is usable from the API
         * being read, but it is better than just assuming every unqualified type is part of
         * `java.lang`.
         */
        val DEFAULT: JavaLangPackage = JavaLangPackageViaReflection()
    }

    private class JavaLangPackageViaReflection : JavaLangPackage() {
        private val platformClassLoader = ClassLoader.getPlatformClassLoader()

        override fun check(name: String) =
            try {
                // Try and load the class.
                platformClassLoader.loadClass(name)
                // The class was loaded so it
                true
            } catch (_: ClassNotFoundException) {
                // The class could not be found so it does not exist.
                false
            }
    }
}
