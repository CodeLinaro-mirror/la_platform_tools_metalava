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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Import
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TraversingVisitor
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import java.util.TreeSet

/**
 * Compute set of import statements that are actually referenced from the documentation (we do
 * inexact matching here; we don't need to have an exact set of imports since it's okay to have some
 * extras). This isn't a big problem since our code style forbids/discourages wildcards, so it shows
 * up in fewer places, but we need to handle it when it does -- such as in ojluni.
 */
fun filterImports(
    imports: TreeSet<Import>,
    classes: Sequence<ClassItem>,
    predicate: FilterPredicate
): TreeSet<Import> {
    // Create a map from the short name for the import to a list of the items imported. A
    // list is needed because classes and members could be imported with the same short
    // name.
    val remainingImports = mutableMapOf<String, MutableList<Import>>()
    imports.groupByTo(remainingImports) { it.name }

    val result = TreeSet<Import>(compareBy { it.pattern })

    // We keep the wildcard imports since we don't know which ones of those are relevant
    imports.filter { it.name == "*" }.forEach { result.add(it) }

    for (cls in classes.filter { predicate.test(it) }) {
        cls.accept(
            object : TraversingVisitor() {
                override fun visitItem(item: Item): TraversalAction {
                    if (item !is SelectableItem) return TraversalAction.SKIP_CHILDREN

                    // Do not let documentation on hidden items affect the imports.
                    if (!predicate.test(item)) {
                        // Just because an item like a class is hidden does not mean
                        // that its child items are so make sure to visit them.
                        return TraversalAction.CONTINUE
                    }
                    val doc = item.documentation
                    if (doc != null) {
                        // Scan the documentation text to see if it contains any of the
                        // short names imported. It does not check whether the names
                        // are actually used as part of a link, so they could just be in
                        // as text but having extra imports should not be an issue.
                        var found: MutableList<String>? = null
                        for (name in remainingImports.keys) {
                            val typeReferencePrefix =
                                DocContentPredicates.containsPossiblyImportedTypeReference(name)
                            if (doc.check(typeReferencePrefix)) {
                                if (found == null) {
                                    found = mutableListOf()
                                }
                                found.add(name)
                            }
                        }

                        // For every imported name add all the matching imports and then
                        // remove them from the available imports as there is no need to
                        // check them again.
                        found?.let {
                            for (name in found) {
                                val all = remainingImports.remove(name) ?: continue
                                result.addAll(all)
                            }

                            if (remainingImports.isEmpty()) {
                                // There is nothing to do if the map of imports to add
                                // is empty.
                                return TraversalAction.SKIP_TRAVERSAL
                            }
                        }
                    }

                    return TraversalAction.CONTINUE
                }
            }
        )
    }
    return result
}
