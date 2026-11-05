/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.api.SelectedApi
import com.android.tools.metalava.model.createImmutableModifiers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ClassMemberInitializerTest {

    private fun createTestCodebaseAndFactory(): Pair<DefaultCodebase, DefaultItemFactory> {
        val assembler =
            object : DefaultCodebaseAssembler() {
                override val codebase: DefaultCodebase =
                    DefaultCodebase(
                        location = File("test"),
                        description = "Test codebase",
                        preFiltered = false,
                        config = Codebase.Config.NOOP,
                        trustedApi = false,
                        supportsDocumentation = false,
                        assembler = this,
                        selectedApiFactory = SelectedApi.MUTABLE_FACTORY,
                    )
                override val itemFactory: DefaultItemFactory =
                    DefaultItemFactory(codebase, SourceLanguage.JAVA)

                override fun createClassFromUnderlyingModel(qualifiedName: String) = null

                override fun getPackageInfoFromUnderlyingModel(packageName: String) =
                    PackageInfo.NO_COMMENT
            }
        return assembler.codebase to assembler.itemFactory
    }

    @Test
    fun `Test ClassMemberMask operations`() {
        val mask = ClassMemberMask.METHODS or ClassMemberMask.FIELDS
        assertFalse(mask.isEmpty)
        assertTrue(ClassMemberMask.METHODS in mask)
        assertTrue(ClassMemberMask.FIELDS in mask)
        assertFalse(ClassMemberMask.CONSTRUCTORS in mask)

        val remaining = mask - ClassMemberMask.METHODS
        assertEquals(ClassMemberMask.FIELDS, remaining)
        assertTrue((remaining - ClassMemberMask.FIELDS).isEmpty)
    }

    @Test
    fun `Test separate member initialization and re-entrancy`() {
        val (codebase, itemFactory) = createTestCodebaseAndFactory()
        val pkg = codebase.findOrCreatePackage("test.pkg")
        val initializedMasks = mutableListOf<ClassMemberMask>()

        val initializer =
            object :
                ClassMemberInitializer(
                    ClassMemberMask.CONSTRUCTORS or
                        ClassMemberMask.METHODS or
                        ClassMemberMask.FIELDS
                ) {
                override fun initializeMembers(
                    classItem: SkeletonClassItem,
                    mask: ClassMemberMask
                ) {
                    initializedMasks += mask
                    if (ClassMemberMask.CONSTRUCTORS in mask) {
                        classItem.addConstructor(
                            classItem.createImplicitDefaultConstructor(VisibilityLevel.PUBLIC)
                        )
                        // Re-entrant read of constructors() during CONSTRUCTORS initialization
                        // must not re-enter initializeMembers.
                        assertEquals(1, classItem.constructors().size)
                    }
                    if (ClassMemberMask.METHODS in mask) {
                        // Calling methods() re-entrantly must not recurse.
                        assertEquals(0, classItem.methods().size)
                    }
                }
            }

        val classItem =
            itemFactory.createClassItem(
                modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC),
                classKind = ClassKind.CLASS,
                containingClass = null,
                containingPackage = pkg,
                qualifiedName = "test.pkg.Foo",
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.CLASS_PATH,
                superClassType = null,
                interfaceTypes = emptyList(),
                memberInitializer = initializer,
            ) as DefaultClassItem

        val initialMask =
            ClassMemberMask.CONSTRUCTORS or ClassMemberMask.METHODS or ClassMemberMask.FIELDS
        // Nothing initialized before access.
        assertEquals(emptyList(), initializedMasks)
        assertEquals(initialMask, classItem.uninitializedMembers)

        // Accessing fields() initializes only FIELDS.
        assertEquals(0, classItem.fields().size)
        assertEquals(listOf(ClassMemberMask.FIELDS), initializedMasks)
        assertEquals(
            ClassMemberMask.CONSTRUCTORS or ClassMemberMask.METHODS,
            classItem.uninitializedMembers,
        )

        // Accessing fields() again does not call initializeMembers again.
        assertEquals(0, classItem.fields().size)
        assertEquals(listOf(ClassMemberMask.FIELDS), initializedMasks)

        // Freezing the class before remaining members are initialized still allows lazy
        // initialization of constructors() and methods(), while rejecting external mutations.
        classItem.freeze()
        assertEquals(1, classItem.constructors().size)
        assertEquals(
            listOf(ClassMemberMask.FIELDS, ClassMemberMask.CONSTRUCTORS),
            initializedMasks,
        )
        assertEquals(ClassMemberMask.METHODS, classItem.uninitializedMembers)

        assertThrows(IllegalStateException::class.java) {
            classItem.addConstructor(
                classItem.createImplicitDefaultConstructor(VisibilityLevel.PUBLIC)
            )
        }

        assertEquals(0, classItem.methods().size)
        assertEquals(
            listOf(
                ClassMemberMask.FIELDS,
                ClassMemberMask.CONSTRUCTORS,
                ClassMemberMask.METHODS,
            ),
            initializedMasks,
        )
        assertEquals(ClassMemberMask.NONE, classItem.uninitializedMembers)
    }

    @Test
    fun `Test cross-kind dependency during initialization`() {
        val (codebase, itemFactory) = createTestCodebaseAndFactory()
        val pkg = codebase.findOrCreatePackage("test.pkg")
        val initializedMasks = mutableListOf<ClassMemberMask>()

        val initializer =
            object : ClassMemberInitializer(ClassMemberMask.ALL) {
                override fun initializeMembers(
                    classItem: SkeletonClassItem,
                    mask: ClassMemberMask
                ) {
                    initializedMasks += mask
                    if (ClassMemberMask.PROPERTIES in mask) {
                        // Accessing methods() while initializing PROPERTIES should trigger
                        // METHODS initialization on demand.
                        classItem.methods()
                    }
                }
            }

        val classItem =
            itemFactory.createClassItem(
                modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC),
                classKind = ClassKind.CLASS,
                containingClass = null,
                containingPackage = pkg,
                qualifiedName = "test.pkg.Bar",
                typeParameterList = TypeParameterList.NONE,
                origin = ClassOrigin.CLASS_PATH,
                superClassType = null,
                interfaceTypes = emptyList(),
                memberInitializer = initializer,
            )

        // Calling properties() first triggers PROPERTIES, which in turn triggers METHODS.
        assertEquals(0, classItem.properties().size)
        assertEquals(
            listOf(ClassMemberMask.PROPERTIES, ClassMemberMask.METHODS),
            initializedMasks,
        )

        // Subsequent calls to methods() do not trigger anything.
        classItem.methods()
        assertEquals(
            listOf(ClassMemberMask.PROPERTIES, ClassMemberMask.METHODS),
            initializedMasks,
        )
    }
}
