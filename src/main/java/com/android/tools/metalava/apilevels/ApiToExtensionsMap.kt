/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.metalava.apilevels

import java.lang.IllegalArgumentException

/**
 * A filter of classes, fields and methods that are allowed in and extension SDK, and for each item,
 * what extension SDK it first appeared in.
 *
 * Internally, the filers are represented as a tree, where each node in the tree matches a part of a
 * package, class or member name. For example, given the patterns
 *
 *   com.example.Foo             -> [A]
 *   com.example.Foo#someMethod  -> [B]
 *   com.example.Bar             -> [A, C]
 *
 * (anything prefixed with com.example.Foo is allowed and part of the A extension, except for
 * com.example.Foo#someMethod which is part of B; anything prefixed with com.example.Bar is part
 * of both A and C), the internal tree looks like
 *
 *   root -> null
 *     com -> null
 *       example -> null
 *         Foo -> [A]
 *           someMethod -> [B]
 *         Bar -> [A, C]
 */
class ApiToExtensionsMap private constructor(private val root: Node) {
    fun getExtensions(clazz: ApiClass): Set<String> = getExtensions(clazz.name.toDotNotation())

    fun getExtensions(clazz: ApiClass, member: ApiElement): Set<String> =
        getExtensions(clazz.name.toDotNotation() + "#" + member.name.toDotNotation())

    fun getExtensions(what: String): Set<String> {
        val parts = what.split(REGEX_DELIMITERS)

        var lastSeenExtensions = root.extensions
        var node = root.children.findNode(parts[0]) ?: return lastSeenExtensions
        if (node.extensions.isNotEmpty()) {
            lastSeenExtensions = node.extensions
        }

        for (part in parts.stream().skip(1)) {
            node = node.children.findNode(part) ?: break
            if (node.extensions.isNotEmpty()) {
                lastSeenExtensions = node.extensions
            }
        }
        return lastSeenExtensions
    }

    companion object {
        private val REGEX_DELIMITERS = Regex("[.#$]")
        private val REGEX_WHITESPACE = Regex("\\s+")

        /*
         * Create an ApiToExtensionsMap from a list of text based rules.
         *
         * The input is a multi-line string, where each rules is written on the format
         *
         *     <jar-file> <pattern> <ext> [<ext> [...]]
         *
         * <pattern> is either '*', which matches everything, or a 'com.foo.Bar$Inner#member' string
         * (or prefix thereof terminated before . or $), which matches anything with that prefix.
         * Note that arguments and return values of methods are omitted (and there is no way to
         * distinguish overloaded methods).
         *
         * <ext> is the name of an extension SDK (e.g. T).
         *
         * All fields are separated by whitespace (spaces or tabs).
         *
         * Lines beginning with # are comments and are ignored. Blank lines are ignored.
         *
         * It is an error to specify the same <pattern> twice.
         *
         * A more specific rule has higher precedence than a less specific rule.
         *
         * @param jar jar file to limit lookups to: ignore lines belonging to other jar files
         * @param rules multi-line string of filter patterns as described above
         * @throws IllegalArgumentException if the input is malformed
         */
        fun fromString(jar: String, rules: String): ApiToExtensionsMap {
            val root = Node("<root>")
            val lines = rules.lines().filter {
                it.isNotBlank() && !it.startsWith('#')
            }
            for (line in lines) {
                val all = line.split(REGEX_WHITESPACE, 3)
                if (all.size != 3) {
                    throw IllegalArgumentException("bad input: $line")
                }
                if (jar != all[0]) {
                    // this is not the jar file you're looking for
                    continue
                }
                val pattern = all[1]
                val extensions = all[2].split(REGEX_WHITESPACE).toSet()

                if (pattern == "*") {
                    root.extensions = extensions
                    continue
                }

                // add each part of the pattern as separate nodes, e.g. if pattern is
                // com.example.Foo, add nodes, "com" -> "example" -> "Foo"
                val parts = pattern.split(REGEX_DELIMITERS)
                var node = root.children.addNode(parts[0])
                for (name in parts.stream().skip(1)) {
                    node = node.children.addNode(name)
                }
                if (node.extensions.isNotEmpty()) {
                    throw IllegalArgumentException("duplicate pattern: $line")
                }
                node.extensions = extensions
            }

            return ApiToExtensionsMap(root)
        }
    }
}

private fun MutableSet<Node>.addNode(name: String): Node {
    findNode(name)?.let {
        return it
    }
    val node = Node(name)
    add(node)
    return node
}

private fun Set<Node>.findNode(breadcrumb: String): Node? = find { it.breadcrumb == breadcrumb }

private fun String.toDotNotation(): String = split('(')[0].replace('/', '.')

private class Node(val breadcrumb: String) {
    var extensions: Set<String> = emptySet()
    val children: MutableSet<Node> = mutableSetOf()
}
