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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.testing.testTypeString
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

interface Assertions {

    /**
     * Get the class from the [Codebase], failing if it does not exist.
     *
     * Checks to make sure that returned [ClassItem]'s [ClassItem.emit] property matches
     * [expectedEmit]. That defaults to `true` as this is usually used to retrieve a class that is
     * present in the source which have `emit = true` by default.
     */
    fun Codebase.assertClass(qualifiedName: String, expectedEmit: Boolean = true): ClassItem {
        val classItem = findClass(qualifiedName)
        assertNotNull(classItem, message = "Expected $qualifiedName to be defined")
        assertEquals(
            expectedEmit,
            classItem.emit,
            message = "Expected $qualifiedName to have emit=$expectedEmit"
        )
        return classItem
    }

    /**
     * Resolve the class from the [Codebase], failing if it does not exist.
     *
     * Checks to make sure that returned [ClassItem]'s [ClassItem.emit] property matches
     * [expectedEmit]. That defaults to `true` as this is usually used to retrieve a class that is
     * present in the source which have `emit = true` by default.
     */
    fun Codebase.assertResolvedClass(
        qualifiedName: String,
        expectedEmit: Boolean = false
    ): ClassItem {
        // Resolve the class which should make it available to assertClass(...) if it could be
        // found.
        resolveClass(qualifiedName)
        // Assert that the class exists and has correct setting of `emit`.
        return assertClass(qualifiedName, expectedEmit)
    }

    /** Get the package from the [Codebase], failing if it does not exist. */
    fun Codebase.assertPackage(pkgName: String): PackageItem {
        val packageItem = findPackage(pkgName)
        assertNotNull(packageItem, message = "Expected $pkgName to be defined")
        return packageItem
    }

    /** Get the type alias from the [Codebase], failing if it does not exist. */
    fun Codebase.assertTypeAlias(qualifiedName: String): TypeAliasItem {
        val typeAliasItem = findTypeAlias(qualifiedName)
        assertNotNull(typeAliasItem, message = "Expected $qualifiedName to be a defined type alias")
        return typeAliasItem
    }

    /**
     * Return a dump of the state of [SelectableItem.selectedApiVariants] across this [Codebase].
     */
    private fun Codebase.dumpSelectedApiVariants() = buildString {
        accept(
            object :
                BaseItemVisitor(
                    preserveClassNesting = true,
                ) {
                private var indent = ""

                override fun visitSelectableItem(item: SelectableItem) {
                    append("$indent${item.describe()} - ${item.selectedApiVariants}\n")
                    indent += "  "
                }

                override fun afterVisitSelectableItem(item: SelectableItem) {
                    indent = indent.substring(2)
                }
            }
        )
    }

    /** Assert that the [dumpSelectedApiVariants] matches [expected]. */
    fun Codebase.assertSelectedApiVariants(expected: String, message: String? = null) {
        val actual = dumpSelectedApiVariants()
        assertEquals(expected.trimIndent(), actual.trimEnd(), message)
    }

    /** Get the field from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertField(fieldName: String): FieldItem {
        val fieldItem = findField(fieldName)
        assertNotNull(fieldItem, message = "Expected $fieldName to be defined")
        return fieldItem
    }

    /** Get the method from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertMethod(methodName: String, parameters: String): MethodItem {
        val methodItem = findMethod(methodName, parameters)
        assertNotNull(methodItem, message = "Expected $methodName($parameters) to be defined")
        return methodItem
    }

    /** Get the constructor from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertConstructor(parameters: String): ConstructorItem {
        val constructorItem = findConstructor(parameters)
        assertNotNull(
            constructorItem,
            message = "Expected ${simpleName()}($parameters) to be defined"
        )
        return assertIs(constructorItem)
    }

    /** Get the property from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertProperty(propertyName: String): PropertyItem {
        val propertyItem = properties().firstOrNull { it.name() == propertyName }
        assertNotNull(propertyItem, message = "Expected $propertyName to be defined")
        return propertyItem
    }

    /** Get the annotation from the [Item], failing if it does not exist. */
    fun Item.assertAnnotation(qualifiedName: String): AnnotationItem {
        val annoItem = modifiers.findAnnotation(qualifiedName)
        assertNotNull(annoItem, message = "Expected item to be annotated with ($qualifiedName)")
        return assertIs(annoItem)
    }

    /**
     * Check the [Item.originallyDeprecated] and [Item.effectivelyDeprecated] are
     * [explicitlyDeprecated] and [implicitlyDeprecated] respectively.
     */
    private fun Item.assertDeprecatedStatus(
        explicitlyDeprecated: Boolean,
        implicitlyDeprecated: Boolean = explicitlyDeprecated,
    ) {
        assertEquals(
            explicitlyDeprecated,
            originallyDeprecated,
            message = "$this: originallyDeprecated"
        )
        assertEquals(
            implicitlyDeprecated,
            effectivelyDeprecated,
            message = "$this: effectivelyDeprecated"
        )
    }

    /** Make sure that the item is not deprecated explicitly, or implicitly. */
    fun Item.assertNotDeprecated() {
        assertDeprecatedStatus(explicitlyDeprecated = false)
    }

    /** Make sure that the item is explicitly deprecated. */
    fun Item.assertExplicitlyDeprecated() {
        assertDeprecatedStatus(explicitlyDeprecated = true)
    }

    /**
     * Make sure that the item is implicitly deprecated, this will fail if the item is explicitly
     * deprecated.
     */
    fun Item.assertImplicitlyDeprecated() {
        assertDeprecatedStatus(
            explicitlyDeprecated = false,
            implicitlyDeprecated = true,
        )
    }

    /**
     * Create a Kotlin like method description. It uses Kotlin structure for a method and Kotlin
     * style nulls but not Kotlin types.
     */
    fun CallableItem.kotlinLikeDescription(): String = buildString {
        if (isConstructor()) {
            append("constructor ")
        } else {
            append("fun ")
        }
        append(name())
        append("(")
        parameters().joinTo(this) {
            "${it.name()}: ${it.type().testTypeString(kotlinStyleNulls = true)}"
        }
        append("): ")
        append(returnType().testTypeString(kotlinStyleNulls = true))
    }

    /** Get the list of fully qualified annotation names associated with the [TypeItem]. */
    fun TypeItem.annotationNames(): List<String?> {
        return modifiers.annotations.map { it.qualifiedName }
    }

    /** Get the list of fully qualified annotation names associated with the [Item]. */
    fun Item.annotationNames(): List<String?> {
        return modifiers.annotations().map { it.qualifiedName }
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [VariableTypeItem] whose
     * [VariableTypeItem.asTypeParameter] references the supplied [typeParameter] and then run the
     * optional lambda on the [VariableTypeItem].
     */
    fun TypeItem.assertReferencesTypeParameter(
        typeParameter: TypeParameterItem,
        body: (VariableTypeItem.() -> Unit)? = null
    ) {
        assertVariableTypeItem {
            assertThat(asTypeParameter).isSameInstanceAs(typeParameter)
            if (body != null) this.body()
        }
    }

    /**
     * Check to make sure that this nullable [TypeItem] is actually a [TypeItem] and then run the
     * optional lambda on the [TypeItem].
     */
    fun <T : TypeItem> T?.assertNotNullTypeItem(body: (T.() -> Unit)? = null) {
        assertThat(this).isNotNull()
        if (body != null) this?.body()
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [ArrayTypeItem] and then run the
     * optional lambda on the [ArrayTypeItem].
     */
    fun TypeItem?.assertArrayTypeItem(body: (ArrayTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [ClassTypeItem] and then run the
     * optional lambda on the [ClassTypeItem].
     */
    fun TypeItem?.assertClassTypeItem(body: (ClassTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [PrimitiveTypeItem] and then run the
     * optional lambda on the [PrimitiveTypeItem].
     */
    fun TypeItem?.assertPrimitiveTypeItem(body: (PrimitiveTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [LambdaTypeItem] and then run the
     * optional lambda on the [LambdaTypeItem].
     */
    fun TypeItem?.assertLambdaTypeItem(body: (LambdaTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [VariableTypeItem] and then run the
     * optional lambda on the [VariableTypeItem].
     */
    fun TypeItem?.assertVariableTypeItem(body: (VariableTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }

    /**
     * Check to make sure that this [TypeItem] is actually a [WildcardTypeItem] and then run the
     * optional lambda on the [WildcardTypeItem].
     */
    fun TypeItem?.assertWildcardItem(body: (WildcardTypeItem.() -> Unit)? = null) {
        assertIsInstanceOf(body ?: {})
    }
}

private inline fun <reified T> Any?.assertIsInstanceOf(body: (T).() -> Unit) {
    assertThat(this).isInstanceOf(T::class.java)
    (this as T).body()
}
