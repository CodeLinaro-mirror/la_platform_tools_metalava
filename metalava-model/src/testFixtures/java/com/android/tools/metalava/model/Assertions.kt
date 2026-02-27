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

import com.android.tools.metalava.model.multiplatform.MultiplatformClassItem
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.multiplatform.MultiplatformConstructorItem
import com.android.tools.metalava.model.multiplatform.MultiplatformElement
import com.android.tools.metalava.model.multiplatform.MultiplatformMethodItem
import com.android.tools.metalava.model.multiplatform.MultiplatformPackageItem
import com.android.tools.metalava.model.multiplatform.MultiplatformPropertyItem
import com.android.tools.metalava.model.multiplatform.SourceSetDependent
import com.android.tools.metalava.model.testing.testTypeString
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
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
        return checkClass(classItem, qualifiedName, expectedEmit)
    }

    /**
     * Checks to make sure that [classItem] is non-null and that its [ClassItem.emit] property
     * matches [expectedEmit].
     */
    private fun checkClass(
        classItem: ClassItem?,
        qualifiedName: String,
        expectedEmit: Boolean,
    ): ClassItem {
        assertNotNull(classItem, message = "Expected $qualifiedName to be defined")
        assertEquals(
            expectedEmit,
            classItem.emit,
            message = "Expected $qualifiedName to have emit=$expectedEmit"
        )
        return classItem
    }

    /**
     * Resolve the class from the [ClassResolver], failing if it does not exist.
     *
     * Checks to make sure that returned [ClassItem]'s [ClassItem.emit] property matches
     * [expectedEmit]. That defaults to `true` as this is usually used to retrieve a class that is
     * present in the source which have `emit = true` by default.
     */
    fun ClassResolver.assertResolvedClass(
        qualifiedName: String,
        expectedEmit: Boolean = false
    ): ClassItem {
        // Resolve the class which should make it available to assertClass(...) if it could be
        // found.
        val resolved = resolveClass(qualifiedName)
        // Assert that the class exists and has correct setting of `emit`.
        return checkClass(resolved, qualifiedName, expectedEmit)
    }

    /** Get the package from the [Codebase], failing if it does not exist. */
    fun Codebase.assertPackage(pkgName: String): PackageItem {
        val packageItem = findPackage(pkgName)
        assertNotNull(packageItem, message = "Expected $pkgName to be defined")
        return packageItem
    }

    /** Resolve the package from the [ClassPathResolver], failing if it does not exist. */
    fun ClassPathResolver.assertResolvedPackage(pkgName: String): PackageItem {
        val packageItem = resolvePackage(pkgName)
        assertNotNull(packageItem, message = "Expected $pkgName to be defined")
        return packageItem
    }

    /** Get the type alias from the [Codebase], failing if it does not exist. */
    fun Codebase.assertTypeAlias(qualifiedName: String): ClassItem {
        val typeAliasItem = assertClass(qualifiedName)
        assertEquals(
            typeAliasItem.classKind,
            ClassKind.TYPEALIAS,
            message =
                "Expected $qualifiedName to be a defined type alias but was ${typeAliasItem.classKind}"
        )
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

    /** Finds the callable in the list, failing if it does not exist. */
    private fun <T : CallableItem> List<T>.assertCallable(
        callableName: String,
        parameters: List<String>,
        requiredTargetLanguage: TargetLanguage? = null,
    ): T {
        val callableItem = singleOrNull {
            it.name() == callableName &&
                it.parameters().size == parameters.size &&
                it.parameters().zip(parameters).all { (parameterItem, expectedTypeString) ->
                    parameterItem.type().toTypeString() == expectedTypeString
                } &&
                (requiredTargetLanguage == null || requiredTargetLanguage in it.targetLanguages)
        }
        assertNotNull(
            callableItem,
            message =
                "Expected $callableName($parameters) to be defined" +
                    if (requiredTargetLanguage != null) {
                        " with target language $requiredTargetLanguage"
                    } else {
                        ""
                    }
        )
        return callableItem
    }

    /**
     * Get the method from the [ClassItem], failing if it does not exist. The [parameters] are
     * expected to be type strings formatted according to [TypeStringConfiguration.DEFAULT].
     *
     * If a [requiredTargetLanguage] is provided, the return [MethodItem] will have it as one of its
     * target languages. If no [requiredTargetLanguage] is provided and there are multiple
     * [MethodItem]s with the same name and parameters but different target languages, the assertion
     * will fail.
     */
    fun ClassItem.assertMethod(
        methodName: String,
        parameters: List<String>,
        requiredTargetLanguage: TargetLanguage? = null,
    ): MethodItem {
        return methods().assertCallable(methodName, parameters, requiredTargetLanguage)
    }

    /**
     * Get the constructor from the [ClassItem], failing if it does not exist. The [parameters] are
     * expected to be type strings formatted according to [TypeStringConfiguration.DEFAULT].
     *
     * If a [requiredTargetLanguage] is provided, the return [ConstructorItem] will have it as one
     * of its target languages. If no [requiredTargetLanguage] is provided and there are multiple
     * [ConstructorItem]s with the same parameters but different target languages, the assertion
     * will fail.
     */
    fun ClassItem.assertConstructor(
        parameters: List<String>,
        requiredTargetLanguage: TargetLanguage? = null,
    ): ConstructorItem {
        return constructors().assertCallable(simpleName(), parameters, requiredTargetLanguage)
    }

    /**
     * Get the property from the [ClassItem], failing if it does not exist.
     *
     * [receiverTypeString] is expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun ClassItem.assertProperty(
        propertyName: String,
        receiverTypeString: String? = null,
    ): PropertyItem {
        val propertyItem =
            properties().firstOrNull {
                it.name() == propertyName &&
                    it.receiver?.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) ==
                        receiverTypeString
            }
        assertNotNull(
            propertyItem,
            message =
                "Expected ${receiverTypeString?.let { "$it." } ?: "" }$propertyName to be defined"
        )
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

    /** Make sure that [this] contains a [TypeParameterItem] called [name], returning it. */
    fun TypeParameterListOwner.assertTypeParameter(name: String): TypeParameterItem {
        val found = typeParameterList.find { it.name() == name }
        assertNotNull(
            found,
            message =
                "Expected $this to have type parameter $name but had ${typeParameterList.joinToString()}"
        )
        return found
    }

    /** Make sure when the documentation for [this] is printed that it matches [expectedOutput]. */
    fun SelectableItem.assertPrintedDocumentation(expectedOutput: String, message: String? = null) {
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { documentation?.print(it) }
        val actualOutput = stringWriter.toString().trimEnd()
        assertEquals(expectedOutput.trimIndent(), actualOutput, message)
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

    /** Get the [AnnotationAttribute] from the [AnnotationItem], failing if it does not exist. */
    fun AnnotationItem.assertAttribute(name: String): AnnotationAttribute {
        val attribute = findAttribute(name)
        assertNotNull(
            attribute,
            message =
                "Expected ${this.qualifiedName} to contain attribute $name but found ${attributes.joinToString { it.name }}"
        )
        return attribute
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

    /** Checks that the element exists in exactly the source sets of [expectedSourceSets]. */
    fun MultiplatformElement<*>.assertSourceSets(vararg expectedSourceSets: String) {
        assertThat(sourceSets).containsExactly(*expectedSourceSets)
    }

    /** Finds the package in the [MultiplatformCodebase], failing if it does not exist. */
    fun MultiplatformCodebase.assertPackage(qualifiedName: String): MultiplatformPackageItem {
        val packageItem = findPackage(qualifiedName)
        assertNotNull(packageItem, "Expected package $qualifiedName to be defined")
        return packageItem
    }

    /** Finds the class in the [MultiplatformCodebase], failing if it does not exist. */
    fun MultiplatformCodebase.assertClass(qualifiedName: String): MultiplatformClassItem {
        val classItem = findClass(qualifiedName)
        assertNotNull(classItem, "Expected class $qualifiedName to be defined")
        return classItem
    }

    /** Assert that the source set to value mapping contains exactly the expected pairs. */
    fun <V> SourceSetDependent<V>.assertSourceSetValues(vararg expectedValues: Pair<String, V>) {
        assertThat(this).isEqualTo(expectedValues.toMap())
    }

    /**
     * Finds the property by [name] and [receiverType] in the [MultiplatformClassItem], failing if
     * it does not exist.
     *
     * [receiverType] is expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun MultiplatformClassItem.assertProperty(
        name: String,
        receiverType: String? = null
    ): MultiplatformPropertyItem {
        val propertyItem =
            properties.singleOrNull { property ->
                property.name == name &&
                    property.receiver?.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) ==
                        receiverType
            }
        assertNotNull(
            propertyItem,
            "Expected property ${receiverType?.let { "$it." } ?: "" }$name to be defined in $this"
        )
        return propertyItem
    }

    /**
     * Finds the property by [name] and [receiverType] in the [MultiplatformClassItem], failing if
     * it does not exist.
     *
     * [receiverType] is expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun MultiplatformPackageItem.assertProperty(
        name: String,
        receiverType: String? = null
    ): MultiplatformPropertyItem {
        val propertyItem =
            topLevelProperties.singleOrNull { property ->
                property.name == name &&
                    property.receiver?.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS) ==
                        receiverType
            }
        assertNotNull(
            propertyItem,
            "Expected property ${receiverType?.let { "$it." } ?: "" }$name to be defined in $this"
        )
        return propertyItem
    }

    /**
     * Finds the constructor by [parameterTypes] in the [MultiplatformClassItem], failing if it does
     * not exist.
     *
     * The [parameterTypes] are expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun MultiplatformClassItem.assertConstructor(
        parameterTypes: List<String>
    ): MultiplatformConstructorItem {
        val constructorItem =
            constructors.singleOrNull { ctor ->
                ctor.parameterTypes.map { type ->
                    type.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS)
                } == parameterTypes
            }
        assertNotNull(
            constructorItem,
            "Expected constructor $qualifiedName(${parameterTypes.joinToString()}) to be defined in $this"
        )
        return constructorItem
    }

    /**
     * Finds the method by [name] and [parameterTypes] in the [MultiplatformClassItem], failing if
     * it does not exist.
     *
     * The [parameterTypes] are expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun MultiplatformClassItem.assertMethod(
        name: String,
        parameterTypes: List<String>
    ): MultiplatformMethodItem {
        val methodItem =
            methods.singleOrNull { method ->
                method.name == name &&
                    method.parameterTypes.map { type ->
                        type.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS)
                    } == parameterTypes
            }
        assertNotNull(
            methodItem,
            "Expected method $name(${parameterTypes.joinToString()}) to be defined in $this"
        )
        return methodItem
    }

    /**
     * Finds the method by [name] and [parameterTypes] in the [MultiplatformPackageItem], failing if
     * it does not exist.
     *
     * The [parameterTypes] are expected to be formatted according to
     * [TypeStringConfiguration.DEFAULT_KOTLIN_NULLS].
     */
    fun MultiplatformPackageItem.assertMethod(
        name: String,
        parameterTypes: List<String>,
    ): MultiplatformMethodItem {
        val methodItem =
            topLevelFunctions.singleOrNull { method ->
                method.name == name &&
                    method.parameterTypes.map { type ->
                        type.toTypeString(TypeStringConfiguration.DEFAULT_KOTLIN_NULLS)
                    } == parameterTypes
            }
        assertNotNull(
            methodItem,
            "Expected method $name(${parameterTypes.joinToString()}) to be defined in $this"
        )
        return methodItem
    }

    companion object : Assertions {}
}

private inline fun <reified T> Any?.assertIsInstanceOf(body: (T).() -> Unit) {
    assertThat(this).isInstanceOf(T::class.java)
    (this as T).body()
}
