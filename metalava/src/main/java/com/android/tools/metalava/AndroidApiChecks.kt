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
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.regex.Pattern

/** Misc API suggestions */
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
                override fun visitItem(item: Item) {
                    checkTodos(item)
                }

                override fun visitCallable(callable: CallableItem) {
                    checkRequiresPermission(callable)
                }

                override fun visitMethod(method: MethodItem) {
                    checkVariable(
                        method,
                        "@return",
                        "Return value of '" + method.name() + "'",
                        method.returnType()
                    )
                }

                override fun visitField(field: FieldItem) {
                    if (field.name().contains("ACTION")) {
                        checkIntentAction(field)
                    }
                    checkVariable(field, null, "Field '" + field.name() + "'", field.type())
                }

                override fun visitParameter(parameter: ParameterItem) {
                    checkVariable(
                        parameter,
                        parameter.name(),
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

    private var cachedDocumentation: String = ""
    private var cachedDocumentationItem: Item? = null
    private var cachedDocumentationTag: String? = null

    // Cache around findDocumentation
    private fun getDocumentation(item: Item, tag: String?): String {
        return if (item === cachedDocumentationItem && cachedDocumentationTag == tag) {
            cachedDocumentation
        } else {
            cachedDocumentationItem = item
            cachedDocumentationTag = tag
            cachedDocumentation = findDocumentation(item, tag)
            cachedDocumentation
        }
    }

    private fun findDocumentation(item: Item, tag: String?): String {
        if (item is ParameterItem) {
            return findDocumentation(item.containingCallable(), item.name())
        }

        val doc = item.documentation.text
        if (doc.isBlank()) {
            return ""
        }

        if (tag == null) {
            return doc
        }

        var begin: Int
        if (tag == "@return") {
            // return tag
            begin = doc.indexOf("@return")
        } else {
            begin = 0
            while (true) {
                begin = doc.indexOf(tag, begin)
                if (begin == -1) {
                    return ""
                } else {
                    // See if it's prefixed by @param
                    // Scan backwards and allow whitespace and *
                    var ok = false
                    for (i in begin - 1 downTo 0) {
                        val c = doc[i]
                        if (c != '*' && !Character.isWhitespace(c)) {
                            if (c == 'm' && doc.startsWith("@param", i - 5, true)) {
                                begin = i - 5
                                ok = true
                            }
                            break
                        }
                    }
                    if (ok) {
                        // found beginning
                        break
                    }
                }
                begin += tag.length
            }
        }

        if (begin == -1) {
            return ""
        }

        // Find end
        // This is the first block tag on a new line
        var isLinePrefix = false
        var end = doc.length
        for (i in begin + 1 until doc.length) {
            val c = doc[i]

            if (
                c == '@' &&
                    (isLinePrefix ||
                        doc.startsWith("@param", i, true) ||
                        doc.startsWith("@return", i, true))
            ) {
                // Found it
                end = i
                break
            } else if (c == '\n') {
                isLinePrefix = true
            } else if (c != '*' && !Character.isWhitespace(c)) {
                isLinePrefix = false
            }
        }

        return doc.substring(begin, end)
    }

    private fun checkTodos(item: Item) {
        if (item.documentation.contains("TODO:") || item.documentation.contains("TODO(")) {
            reporter.report(Issues.TODO, item, "Documentation mentions 'TODO'")
        }
    }

    private fun checkRequiresPermission(callable: CallableItem) {
        val text = callable.documentation

        val annotation = callable.modifiers.findAnnotation("androidx.annotation.RequiresPermission")
        if (annotation != null) {
            var conditional = false
            val permissions = mutableListOf<String>()
            for (attribute in annotation.attributes) {
                when (attribute.name) {
                    "value",
                    "allOf",
                    "anyOf" -> {
                        attribute.leafValues().mapTo(permissions) { it.toSource() }
                    }
                    "conditional" -> {
                        conditional = attribute.value.value() == true
                    }
                }
            }
            for (item in permissions) {
                val perm = item.substringAfterLast('.')
                // Search for the permission name as a whole word.
                val regex = Regex("""\b\Q$perm\E\b""")
                val mentioned = text.contains(regex)
                if (mentioned && !conditional) {
                    reporter.report(
                        Issues.REQUIRES_PERMISSION,
                        callable,
                        "Method '${callable.name()}' documentation duplicates auto-generated documentation by @RequiresPermission. If the permissions are only required under certain circumstances use conditional=true to suppress the auto-documentation"
                    )
                } else if (!mentioned && conditional) {
                    reporter.report(
                        Issues.CONDITIONAL_REQUIRES_PERMISSION_NOT_EXPLAINED,
                        callable,
                        "Method '${callable.name()}' documentation does not explain when the conditional permission '$perm' is required."
                    )
                }
            }
        } else if (
            text.contains("android.Manifest.permission") || text.contains("android.permission.")
        ) {
            reporter.report(
                Issues.REQUIRES_PERMISSION,
                callable,
                "Method '" +
                    callable.name() +
                    "' documentation mentions permissions without declaring @RequiresPermission"
            )
        }
    }

    private fun checkIntentAction(field: FieldItem) {
        // Intent rules don't apply to support library
        if (field.containingClass().qualifiedName().startsWith("android.support.")) {
            return
        }

        val hasBehavior =
            field.modifiers.findAnnotation("android.annotation.BroadcastBehavior") != null
        val hasSdkConstant =
            field.modifiers.findAnnotation("android.annotation.SdkConstant") != null

        val text = field.documentation

        if (
            text.contains("Broadcast Action:") ||
                text.contains("protected intent") && text.contains("system")
        ) {
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

        if (text.contains("Activity Action:")) {
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

    private fun checkVariable(item: Item, tag: String?, ident: String, type: TypeItem?) {
        type ?: return
        if (
            type.toString() == "int" && constantPattern.matcher(getDocumentation(item, tag)).find()
        ) {
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

        if (
            nullPattern.matcher(getDocumentation(item, tag)).find() &&
                item.type()?.modifiers?.isPlatformNullability == true
        ) {
            reporter.report(
                Issues.NULLABLE,
                item,
                "$ident documentation mentions 'null' without declaring @NonNull or @Nullable"
            )
        }
    }

    companion object {
        val constantPattern: Pattern = Pattern.compile("[A-Z]{3,}_([A-Z]{3,}|\\*)")
        @Suppress("SpellCheckingInspection")
        val nullPattern: Pattern = Pattern.compile("\\bnull\\b")
    }
}
