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

package com.android.tools.metalava.lint

import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.multiplatform.BaseMultiplatformItemVisitor
import com.android.tools.metalava.model.multiplatform.MultiplatformClassItem
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformItem
import com.android.tools.metalava.model.multiplatform.MultiplatformMethodItem
import com.android.tools.metalava.model.multiplatform.MultiplatformPackageItem
import com.android.tools.metalava.model.multiplatform.MultiplatformPropertyItem
import com.android.tools.metalava.model.multiplatform.MultiplatformTypeParameterListOwner
import com.android.tools.metalava.model.multiplatform.SourceSetDependent
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Issues.KMP_DEPRECATION_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_EXPERIMENTAL_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_HIDE_SHOW_ANNOTATION_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_MODIFIER_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_ORIGIN_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_REIFIED_MISMATCH
import com.android.tools.metalava.reporter.Issues.KMP_VISIBILITY_MISMATCH
import com.android.tools.metalava.reporter.Reporter

class MultiplatformLint(val reporter: Reporter) : BaseMultiplatformItemVisitor() {
    fun check(codebase: MultiplatformCodebase) {
        codebase.accept(this)
    }

    override fun skip(item: MultiplatformItem<*>): Boolean {
        if (item is MultiplatformClassItem) {
            val originToSourceSet = item.origin.valueToSourceSet { it }
            // It seems unlikely a class would be defined in sources in one source set and on the
            // classpath in another, but if it happens, report it and run lint checks.
            if (originToSourceSet.size > 1) {
                val originDescriptions =
                    originToSourceSet.entries.joinToString { (origin, sourceSets) ->
                        "$origin in $sourceSets"
                    }
                reporter.report(
                    KMP_ORIGIN_MISMATCH,
                    item,
                    "$item has different origins in different source sets: $originDescriptions"
                )
                return false
            }
            // Skip processing any items that were not defined in source.
            return originToSourceSet.keys.single() != ClassOrigin.COMMAND_LINE
        }

        // For anything other than classes, always check the item. This includes items which are not
        // part of the API surface, because several of the checks ensure that whether an element is
        // part of the API surface is the same between source sets.
        return false
    }

    /**
     * Checks if a boolean value is true in some source sets and false in others, reporting an issue
     * if so.
     *
     * @param bySourceSet a map from boolean value to a list of source sets with that value
     * @param item the [MultiplatformItem] which this check is for
     * @param valueDescription a description of what it means for the [item] to have a true value in
     *   a source set -- for instance, "deprecated"
     * @param issue the issue to report if the value is true in some source sets and false in others
     * @param itemDescription a description of the item this issue is for
     */
    private fun checkTrueFalseMismatch(
        bySourceSet: Map<Boolean, List<String>>,
        item: MultiplatformItem<*>,
        valueDescription: String,
        issue: Issue,
        itemDescription: String = item.toString(),
    ) {
        if (true in bySourceSet && false in bySourceSet) {
            reporter.report(
                issue,
                item,
                "$itemDescription is $valueDescription in source sets ${bySourceSet[true]} " +
                    "but not $valueDescription in source sets ${bySourceSet[false]}"
            )
        }
    }

    /**
     * Uses [transformValue] to transform each value in the mapping, and returns a map from the
     * transformed values to the source sets which have that value.
     */
    private fun <V, T> SourceSetDependent<V>.valueToSourceSet(
        transformValue: (V) -> T
    ): Map<T, List<String>> {
        return entries.groupBy({ transformValue(it.value) }) { it.key }
    }

    override fun visitSelectableItem(item: MultiplatformItem<*>) {
        // These checks don't make sense for packages.
        if (item is MultiplatformPackageItem) return

        checkTrueFalseMismatch(
            item.modifiers.valueToSourceSet { it.isDeprecated() },
            item,
            "deprecated",
            KMP_DEPRECATION_MISMATCH,
        )

        val visibilityByPlatform = item.modifiers.valueToSourceSet { it.getVisibilityLevel() }
        if (visibilityByPlatform.size > 1) {
            val visibilityDescriptions =
                visibilityByPlatform.entries.joinToString { (visibility, sourceSets) ->
                    "${visibility.userVisibleDescription} in $sourceSets"
                }
            reporter.report(
                KMP_VISIBILITY_MISMATCH,
                item,
                "Multiplatform $item has different visibilities in different source sets: $visibilityDescriptions"
            )
        }

        checkTrueFalseMismatch(
            item.modifiers.valueToSourceSet {
                it.annotations().any { annotationItem -> annotationItem.isHideAnnotation() }
            },
            item,
            "hidden with an annotation",
            KMP_HIDE_SHOW_ANNOTATION_MISMATCH,
        )

        checkTrueFalseMismatch(
            item.modifiers.valueToSourceSet {
                it.annotations().any { annotationItem -> annotationItem.isShowAnnotation() }
            },
            item,
            "shown with an annotation",
            KMP_HIDE_SHOW_ANNOTATION_MISMATCH,
        )

        checkTrueFalseMismatch(
            item.modifiers.valueToSourceSet {
                it.annotations().any { annotationItem ->
                    annotationItem.isSuppressCompatibilityAnnotation()
                }
            },
            item,
            "experimental",
            KMP_EXPERIMENTAL_MISMATCH,
        )
    }

    override fun visitClassItem(classItem: MultiplatformClassItem) {
        checkTrueFalseMismatch(
            classItem.modifiers.valueToSourceSet { it.isFinal() },
            classItem,
            "final",
            KMP_MODIFIER_MISMATCH,
        )
    }

    override fun visitMethodItem(methodItem: MultiplatformMethodItem) {
        checkTrueFalseMismatch(
            methodItem.modifiers.valueToSourceSet { it.isOperator() },
            methodItem,
            "operator",
            KMP_MODIFIER_MISMATCH,
        )

        checkTrueFalseMismatch(
            methodItem.modifiers.valueToSourceSet { it.isInfix() },
            methodItem,
            "infix",
            KMP_MODIFIER_MISMATCH,
        )

        checkTypeParameterReified(methodItem)
    }

    override fun visitPropertyItem(propertyItem: MultiplatformPropertyItem) {
        checkTypeParameterReified(propertyItem)
    }

    fun checkTypeParameterReified(owner: MultiplatformTypeParameterListOwner<*>) {
        for (typeParameter in owner.typeParameterList) {
            checkTrueFalseMismatch(
                typeParameter.isReified.valueToSourceSet { it },
                owner,
                "reified",
                KMP_REIFIED_MISMATCH,
                typeParameter.toString(),
            )
        }
    }
}
