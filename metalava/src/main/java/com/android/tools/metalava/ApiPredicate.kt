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

package com.android.tools.metalava

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import java.util.function.Predicate

/**
 * Predicate that decides if the given member should be considered part of an API surface area. To
 * make the most accurate decision, it searches for signals on the member, all containing classes,
 * and all containing packages.
 */
class ApiPredicate(
    /**
     * Set if the value of [MemberItem.removed] should be ignored. That is, this predicate will
     * assume that all encountered members match the "removed" requirement.
     *
     * This is typically useful when generating "removed.txt", when it's okay to reference both
     * current and removed APIs.
     */
    private val ignoreRemoved: Boolean = false,

    /**
     * Set what the value of [MemberItem.removed] must be equal to in order for a member to match.
     *
     * This is typically useful when generating "removed.txt", when you only want to match members
     * that have actually been removed.
     */
    private val matchRemoved: Boolean = false,

    /** Whether we should include doc-only items */
    private val includeDocOnly: Boolean = false,

    /** Whether to include "for stub purposes" APIs. See [AnnotationItem.isShowForStubPurposes] */
    private val includeApisForStubPurposes: Boolean = true,

    /** Configuration that may be provided by command line options. */
    private val config: Config = @Suppress("DEPRECATION") options.apiPredicateConfig,
) : Predicate<Item> {

    /**
     * Contains configuration for [ApiPredicate] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        /**
         * Set if the value of [MemberItem.hasShowAnnotation] should be ignored. That is, this
         * predicate will assume that all encountered members match the "shown" requirement.
         *
         * This is typically useful when generating "current.txt", when no
         * [Options.allShowAnnotations] have been defined.
         */
        val ignoreShown: Boolean = true,

        /** Whether we allow matching items loaded from jar files instead of sources */
        val allowClassesFromClasspath: Boolean = true,

        /**
         * Whether overriding methods essential for compiling the stubs should be considered as APIs
         * or not.
         */
        val addAdditionalOverrides: Boolean = false,

        /**
         * Set of qualified names of classes where all visible overriding methods are considered as
         * APIs.
         */
        val additionalNonessentialOverridesClasses: Set<String> = emptySet(),
    )

    override fun test(member: Item): Boolean {
        // Type Parameter references (e.g. T) aren't actual types, skip all visibility checks
        if (member is ClassItem && member.isTypeParameter) {
            return true
        }

        if (!config.allowClassesFromClasspath && member.isFromClassPath()) {
            return false
        }

        val isVisible = { method: MethodItem -> !method.hidden || method.hasShowAnnotation() }
        val visibleForAdditionalOverridePurpose =
            if (config.addAdditionalOverrides) {
                member is MethodItem &&
                    !member.isConstructor() &&
                    (member.isRequiredOverridingMethodForTextStub() ||
                        (member.containingClass().qualifiedName() in
                            config.additionalNonessentialOverridesClasses &&
                            isVisible(member) &&
                            member.superMethods().all { isVisible(it) }))
            } else {
                false
            }

        var visible =
            member.isPublic ||
                member.isProtected ||
                (member.isInternal &&
                    member.hasShowAnnotation()) // TODO: Should this use checkLevel instead?
        var hidden = member.hidden && !visibleForAdditionalOverridePurpose
        if (!visible || hidden) {
            return false
        }
        if (!includeApisForStubPurposes && includeOnlyForStubPurposes(member)) {
            return false
        }

        // If a class item's parent class is an api-only annotation marked class,
        // the item should be marked visible as well, in order to provide
        // information about the correct class hierarchy that was concealed for
        // less restricted APIs.
        // Only the class definition is marked visible, and class attributes are
        // not affected.
        if (
            member is ClassItem &&
                member.superClass()?.let {
                    it.hasShowAnnotation() && !includeOnlyForStubPurposes(it)
                } == true
        ) {
            return member.removed == matchRemoved
        }

        var hasShowAnnotation = config.ignoreShown || member.hasShowAnnotation()
        var docOnly = member.docOnly
        var removed = member.removed

        var clazz: ClassItem? =
            when (member) {
                is MemberItem -> member.containingClass()
                is ClassItem -> member
                else -> null
            }

        if (clazz != null) {
            var pkg: PackageItem? = clazz.containingPackage()
            while (pkg != null) {
                hidden = hidden or pkg.hidden
                docOnly = docOnly or pkg.docOnly
                removed = removed or pkg.removed
                pkg = pkg.containingPackage()
            }
        }
        while (clazz != null) {
            visible =
                visible and
                    (clazz.isPublic ||
                        clazz.isProtected ||
                        (clazz.isInternal && clazz.hasShowAnnotation()))
            hasShowAnnotation =
                hasShowAnnotation or (config.ignoreShown || clazz.hasShowAnnotation())
            hidden = hidden or clazz.hidden
            docOnly = docOnly or clazz.docOnly
            removed = removed or clazz.removed
            clazz = clazz.containingClass()
        }

        if (ignoreRemoved) {
            removed = matchRemoved
        }

        if (docOnly && includeDocOnly) {
            docOnly = false
        }

        return visible && hasShowAnnotation && !hidden && !docOnly && removed == matchRemoved
    }

    /**
     * Returns true, if an item should be included only for "stub" purposes; that is, the item does
     * have at least one [AnnotationItem.isShowAnnotation] annotation and all those annotations are
     * also an [AnnotationItem.isShowForStubPurposes] annotation.
     */
    private fun includeOnlyForStubPurposes(item: Item): Boolean {
        if (!item.codebase.annotationManager.hasAnyStubPurposesAnnotations()) {
            return false
        }

        // If the item has a "show" annotation, then return whether it *only* has a "for stubs"
        // show annotation or not.
        //
        // Note, If the item does not have a show annotation, then it can't have a "for stubs" one,
        // because the later must be a subset of the former, which we don't detect in *this*
        // run (unfortunately it's hard to do so due to how things work), but when metalava
        // is executed for the parent API, we'd detect it as
        // [Issues.SHOWING_MEMBER_IN_HIDDEN_CLASS].
        if (item.hasShowAnnotationInherited()) {
            return item.onlyShowForStubPurposesInherited()
        }
        // If this item has neither --show-annotation nor --parent-api-annotation,
        // Then defer to the "parent" item (i.e. the enclosing class or package).
        return item.parent()?.let { includeOnlyForStubPurposes(it) } ?: false
    }
}
