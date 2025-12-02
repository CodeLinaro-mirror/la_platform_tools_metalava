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

package com.android.tools.metalava

import com.android.tools.metalava.model.ANDROIDX_INT_DEF
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.source.doc.containsWord
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.permission.getRequiresPermissionInfo
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import java.util.regex.Pattern

/**
 * Misc API suggestions.
 *
 * Currently, all the checks in here require [SelectableItem.documentation] to be non-null in order
 * for them to do anything. So, this whole check is disabled when
 * [Codebase.Config.allowReadingComments] is `false`.
 */
class AndroidApiChecks(val reporter: Reporter) {
    fun check(codebase: Codebase) {
        for (packageItem in codebase.getPackages().packages) {
            // Get the package name with a trailing `.` to simplify prefix checking below. Without
            // it the checks would have to check for `android` and `android.` separately.
            val name = packageItem.qualifiedName() + "."

            // Limit the checks to the android.* namespace (except for ICU)
            if (!name.startsWith("android.") || name.startsWith("android.icu.")) continue

            checkPackage(packageItem)
        }
    }

    private fun checkPackage(packageItem: PackageItem) {
        packageItem.accept(
            object :
                ApiVisitor(
                    apiPredicateConfig = @Suppress("DEPRECATION") options.apiPredicateConfig,
                ) {

                override fun visitSelectableItem(item: SelectableItem) {
                    // TODOs are only checked on [Item]s with documentation and [ParameterItem]s
                    // do not have any. Documentation for parameters is stored within the containing
                    // callable in @param sections.
                    checkTodos(item)
                }

                override fun visitCallable(callable: CallableItem) {
                    checkRequiresPermission(callable)
                }

                override fun visitMethod(method: MethodItem) {
                    val documentation = method.documentation ?: return
                    val content = documentation.blockTagDescription("return") ?: return
                    checkVariable(
                        method,
                        content,
                        "Return value of '" + method.name() + "'",
                        method.returnType()
                    )
                }

                override fun visitField(field: FieldItem) {
                    val documentation = field.documentation ?: return
                    val content = documentation.mainDescription ?: return
                    if (field.name().contains("ACTION")) {
                        checkIntentAction(field, documentation)
                    }
                    checkVariable(field, content, "Field '" + field.name() + "'", field.type())
                }

                override fun visitParameter(parameter: ParameterItem) {
                    val content = parameter.description ?: return
                    checkVariable(
                        parameter,
                        content,
                        "Parameter '" +
                            parameter.name() +
                            "' of '" +
                            parameter.containingCallable().name() +
                            "'",
                        parameter.type()
                    )
                }
            }
        )
    }

    private fun checkTodos(item: SelectableItem) {
        val documentation = item.documentation ?: return
        if (documentation.check(CONTAINS_TODO_PREDICATE)) {
            reporter.report(Issues.TODO, item, "Documentation mentions 'TODO'")
        }
    }

    private fun checkRequiresPermission(callable: CallableItem) {
        val documentation = callable.documentation ?: return

        val annotation = callable.modifiers.findAnnotation("androidx.annotation.RequiresPermission")
        val requiresPermissionInfo = annotation?.getRequiresPermissionInfo()
        if (requiresPermissionInfo != null) {
            val conditional = requiresPermissionInfo.conditional
            val permissions = requiresPermissionInfo.permissionValues.mapNotNull { it.asString() }
            for (item in permissions) {
                val perm = item.substringAfterLast('.')
                // Search for the permission name as a whole word.
                val mentioned = documentation.containsWord(perm)
                if (mentioned && !conditional) {
                    reporter.report(
                        Issues.REQUIRES_PERMISSION,
                        callable,
                        "Method '${callable.name()}' documentation duplicates auto-generated documentation by @RequiresPermission. If the permissions are only required under certain circumstances use conditional=true to suppress the auto-documentation",
                        // TODO(b/414336151): Temporarily downgrade severity to error-when-new as
                        //   there are a few issues in Android that were not being reported
                        //   correctly before switching to the new Value model.
                        maximumSeverity = Severity.WARNING_ERROR_WHEN_NEW,
                    )
                } else if (!mentioned && conditional) {
                    reporter.report(
                        Issues.CONDITIONAL_REQUIRES_PERMISSION_NOT_EXPLAINED,
                        callable,
                        "Method '${callable.name()}' documentation does not explain when the conditional permission '$perm' is required."
                    )
                }
            }
        } else if (documentation.check(CONTAINS_PERMISSION_NAME_OR_FIELD_PREDICATE)) {
            reporter.report(
                Issues.REQUIRES_PERMISSION,
                callable,
                "Method '" +
                    callable.name() +
                    "' documentation mentions permissions without declaring @RequiresPermission"
            )
        }
    }

    private fun checkIntentAction(field: FieldItem, documentation: ItemDocumentation) {
        // Intent rules don't apply to support library
        if (field.containingClass().qualifiedName().startsWith("android.support.")) {
            return
        }

        val hasBehavior =
            field.modifiers.findAnnotation("android.annotation.BroadcastBehavior") != null
        val hasSdkConstant =
            field.modifiers.findAnnotation("android.annotation.SdkConstant") != null

        if (documentation.check(CONTAINS_BROADCAST_ACTION_OR_SYSTEM_PREDICATE)) {
            if (!hasBehavior) {
                reporter.report(
                    Issues.BROADCAST_BEHAVIOR,
                    field,
                    "Field '" + field.name() + "' is missing @BroadcastBehavior"
                )
            }
            if (!hasSdkConstant) {
                reporter.report(
                    Issues.SDK_CONSTANT,
                    field,
                    "Field '" +
                        field.name() +
                        "' is missing @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)"
                )
            }
        }

        if (documentation.check(CONTAINS_ACTIVITY_ACTION_PREDICATE)) {
            if (!hasSdkConstant) {
                reporter.report(
                    Issues.SDK_CONSTANT,
                    field,
                    "Field '" +
                        field.name() +
                        "' is missing @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)"
                )
            }
        }
    }

    /**
     * Check to see if this [TypeItem] is a [PrimitiveTypeItem] of [PrimitiveTypeItem.Primitive.INT]
     * kind.
     */
    private fun TypeItem.isIntType() =
        this is PrimitiveTypeItem && kind == PrimitiveTypeItem.Primitive.INT

    /**
     * Checks to make sure that the documentation and type are consistent with respect to use of
     * `null` annotations and `@IntDef` annotations.
     */
    private fun checkVariable(item: Item, content: DocContent, ident: String, type: TypeItem) {
        // Check to see if it mentions a constant name that could/should be an IntDef.
        if (type.isIntType() && content.check(CONTAINS_CONSTANT_NAME_PREDICATE)) {
            var foundTypeDef = false
            for (annotation in item.modifiers.annotations()) {
                val cls = annotation.resolve() ?: continue
                val modifiers = cls.modifiers
                if (modifiers.findAnnotation(ANDROIDX_INT_DEF) != null) {
                    // TODO: Check that all the constants listed in the documentation are included
                    // in the
                    // annotation?
                    foundTypeDef = true
                    break
                }
            }

            if (!foundTypeDef) {
                reporter.report(
                    Issues.INT_DEF,
                    item,
                    // TODO: Include source code you can paste right into the code?
                    "$ident documentation mentions constants without declaring an @IntDef"
                )
            }
        }

        // Check to make sure that if the documentation mentions `null` that it also uses the
        // correct nullability annotations.
        if (type.modifiers.isPlatformNullability == true && content.containsNullWord()) {
            reporter.report(
                Issues.NULLABLE,
                item,
                "$ident documentation mentions 'null' without declaring @NonNull or @Nullable"
            )
        }
    }

    companion object {
        /** Pattern that looks for constants of the form `BAR_FOO` or wildcards like `BAR_*`. */
        private val constantPattern = Pattern.compile("[A-Z]{3,}_([A-Z]{3,}|\\*)")

        /**
         * A [DocContentPredicate] that will check for the presence of [constantPattern] in the
         * documentation.
         */
        private val CONTAINS_CONSTANT_NAME_PREDICATE =
            DocContentPredicates.textContainsAny { text ->
                // Check to make sure the text is long enough before trying to apply the pattern.
                // Applying a pattern has a small overhead so it is worth avoiding that on short
                // strings that could never match.
                text.length >= 5 && constantPattern.matcher(text).find()
            }

        /**
         * A [DocContentPredicate] that will check for the presence of `TO-DO`s in the
         * documentation.
         */
        private val CONTAINS_TODO_PREDICATE =
            DocContentPredicates.textContainsAny { text ->
                text.contains("TODO:") || text.contains("TODO(")
            }

        /**
         * A [DocContentPredicate] that will check for the presence of `Broadcast Action:` or
         * `protected intent` and `system` in the documentation.
         */
        private val CONTAINS_BROADCAST_ACTION_OR_SYSTEM_PREDICATE =
            DocContentPredicates.textContainsAny { text ->
                text.contains("Broadcast Action:") ||
                    (text.contains("protected intent") && text.contains("system"))
            }

        /**
         * A [DocContentPredicate] that will check for the presence of `Activity Action:` in the
         * documentation.
         */
        private val CONTAINS_ACTIVITY_ACTION_PREDICATE =
            DocContentPredicates.textContainsAny { text -> text.contains("Activity Action:") }

        /**
         * A [DocContentPredicate] that will check for the presence of permission names or fields in
         * the documentation.
         */
        private val CONTAINS_PERMISSION_NAME_OR_FIELD_PREDICATE =
            DocContentPredicates.textContainsAny { text ->
                text.contains("android.Manifest.permission") || text.contains("android.permission.")
            }
    }
}
