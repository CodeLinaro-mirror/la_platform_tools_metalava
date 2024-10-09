/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.io.File
import java.util.function.Predicate

/**
 * Checks to see if a package name matches a set of configured rules.
 *
 * This supports a number of rule styles:
 * - exact match (foo)
 * - prefix match (foo*, probably not intentional)
 * - package and subpackage match (foo.*)
 * - explicit addition (+foo.*)
 * - subtraction (+*:-foo.*)
 *
 * Real examples: args: "-stubpackages com.android.test.power ", args: "-stubpackages android.car*
 * ", args: "-stubpackages com.android.ahat:com.android.ahat.*", args:
 * "-force-convert-to-warning-nullability-annotations +*:-android.*:+android.icu.*:-dalvik.*
 */
class PackageFilter {
    private val components: MutableList<PackageFilterComponent> = mutableListOf()

    fun matches(qualifiedName: String): Boolean {
        for (component in components.reversed()) {
            if (component.filter.test(qualifiedName)) {
                return component.treatAsPositiveMatch
            }
        }
        return false
    }

    internal fun addPackages(path: String) {
        for (arg in path.split(File.pathSeparatorChar)) {
            val treatAsPositiveMatch = !arg.startsWith("-")
            val pkg = arg.removePrefix("-").removePrefix("+")
            val index = pkg.indexOf('*')
            if (index != -1) {
                if (index < pkg.length - 1) {
                    throw IllegalStateException(
                        "Wildcards in stub packages must be at the end of the package: $pkg"
                    )
                }
                val prefix = pkg.removeSuffix("*")
                if (prefix.endsWith(".")) {
                    // In doclava, "foo.*" does not match "foo", but we want to do that.
                    val exact = prefix.substring(0, prefix.length - 1)
                    add(StringEqualsPredicate(exact), treatAsPositiveMatch)
                }
                add(StringPrefixPredicate(prefix), treatAsPositiveMatch)
            } else {
                add(StringEqualsPredicate(pkg), treatAsPositiveMatch)
            }
        }
    }

    private fun add(predicate: Predicate<String>, treatAsPositiveMatch: Boolean) {
        components.add(PackageFilterComponent(predicate, treatAsPositiveMatch))
    }

    fun matches(packageItem: PackageItem): Boolean {
        return matches(packageItem.qualifiedName())
    }

    companion object {
        fun parse(path: String): PackageFilter {
            val filter = PackageFilter()
            filter.addPackages(path)
            return filter
        }
    }
}

internal class StringPrefixPredicate(private val acceptedPrefix: String) : Predicate<String> {
    override fun test(candidatePackage: String): Boolean {
        return candidatePackage.startsWith(acceptedPrefix)
    }
}

internal class StringEqualsPredicate(private val acceptedPackage: String) : Predicate<String> {
    override fun test(candidatePackage: String): Boolean {
        return candidatePackage == acceptedPackage
    }
}

/**
 * One element of a PackageFilter. Detects packages and either includes or excludes them from the
 * filter
 */
internal class PackageFilterComponent(
    val filter: Predicate<String>,
    val treatAsPositiveMatch: Boolean,
)
