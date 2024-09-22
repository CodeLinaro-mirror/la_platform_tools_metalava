/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.actualItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_STRING
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import java.io.PrintWriter

internal class JavaStubWriter(
    private val writer: PrintWriter,
    private val modifierListWriter: ModifierListWriter,
    private val config: StubWriterConfig,
    private val stubConstructorManager: StubConstructorManager,
) : DelegatedVisitor {

    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val qualifiedName = cls.containingPackage().qualifiedName()
            if (qualifiedName.isNotBlank()) {
                writer.println("package $qualifiedName;")
                writer.println()
            }
            if (config.includeDocumentationInStubs) {
                // All the classes referenced in the stubs are fully qualified, so no imports are
                // needed. However, in some cases for javadoc, replacement with fully qualified name
                // fails, and thus we need to include imports for the stubs to compile.
                cls.getSourceFile()?.getImports()?.let {
                    for (item in it) {
                        if (item.isMember) {
                            writer.println("import static ${item.pattern};")
                        } else {
                            writer.println("import ${item.pattern};")
                        }
                    }
                    writer.println()
                }
            }
        }

        appendDocumentation(cls, writer, config)

        // "ALL" doesn't do it; compiler still warns unless you actually explicitly list "unchecked"
        writer.println("@SuppressWarnings({\"unchecked\", \"deprecation\", \"all\"})")

        appendModifiers(cls)

        when {
            cls.isAnnotationType() -> writer.print("@interface")
            cls.isInterface() -> writer.print("interface")
            cls.isEnum() -> writer.print("enum")
            else -> writer.print("class")
        }

        writer.print(" ")
        writer.print(cls.simpleName())

        generateTypeParameterList(typeList = cls.typeParameterList, addSpace = false)
        generateSuperClassDeclaration(cls)
        generateInterfaceList(cls)
        writer.print(" {\n")

        // Enum constants must be written out first.
        if (cls.isEnum()) {
            var first = true
            // While enum order is significant at runtime as it affects `Enum.ordinal` and its
            // comparable order it is not significant in the stubs so sort alphabetically. That
            // matches the order in the documentation and the signature files. It is theoretically
            // possible for an annotation processor to care about the order but any that did would
            // be poorly written and would break on stubs created from signature files.
            val enumConstants =
                cls.fields().filter { it.isEnumConstant() }.sortedWith(FieldItem.comparator)
            for (enumConstant in enumConstants) {
                if (first) {
                    first = false
                } else {
                    writer.write(",\n")
                }
                appendDocumentation(enumConstant, writer, config)

                // Append the modifier list even though the enum constant does not actually have
                // modifiers as that will write the annotations which it does have and ignore
                // the modifiers.
                appendModifiers(enumConstant)

                writer.write(enumConstant.name())
            }
            writer.println(";")
        }
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.print("}\n\n")
    }

    private fun appendModifiers(item: Item) {
        modifierListWriter.write(item.actualItem)
    }

    private fun generateSuperClassDeclaration(cls: ClassItem) {
        if (cls.isEnum() || cls.isAnnotationType() || cls.isInterface()) {
            // No extends statement for enums and annotations; it's implied by the "enum" and
            // "@interface" keywords. Normal interfaces do support an extends statement but it is
            // generated in [generateInterfaceList].
            return
        }

        val superClass = cls.superClassType()
        if (superClass != null && !superClass.isJavaLangObject()) {
            writer.print(" extends ")
            writer.print(superClass.toTypeString())
        }
    }

    private fun generateInterfaceList(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            // No extends statement for annotations; it's implied by the "@interface" keyword
            return
        }

        val interfaces = cls.interfaceTypes()
        if (interfaces.isNotEmpty()) {
            val label = if (cls.isInterface()) " extends" else " implements"
            writer.print(label)
            interfaces.sortedWith(TypeItem.totalComparator).forEachIndexed { index, type ->
                if (index > 0) {
                    writer.print(",")
                }
                writer.print(" ")
                writer.print(type.toTypeString())
            }
        }
    }

    private fun generateTypeParameterList(typeList: TypeParameterList, addSpace: Boolean) {
        val typeListString = typeList.toString()
        if (typeListString.isNotEmpty()) {
            writer.print(typeListString)

            if (addSpace) {
                writer.print(' ')
            }
        }
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        writer.println()
        appendDocumentation(constructor, writer, config)
        appendModifiers(constructor)
        generateTypeParameterList(typeList = constructor.typeParameterList, addSpace = true)
        writer.print(constructor.containingClass().simpleName())

        generateParameterList(constructor)
        generateThrowsList(constructor)

        writer.print(" { ")

        writeConstructorBody(constructor)
        writer.println(" }")
    }

    private fun writeConstructorBody(constructor: ConstructorItem) {
        val optionalSuperConstructor =
            stubConstructorManager.optionalSuperConstructor(constructor.containingClass())
        optionalSuperConstructor?.let { superConstructor ->
            val parameters = superConstructor.parameters()
            if (parameters.isNotEmpty()) {
                writer.print("super(")

                // Get the types to which this class binds the super class's type parameters, if
                // any.
                val typeParameterBindings =
                    constructor
                        .containingClass()
                        .mapTypeVariables(superConstructor.containingClass())

                for ((index, parameter) in parameters.withIndex()) {
                    if (index > 0) {
                        writer.write(", ")
                    }
                    // Always make sure to add appropriate casts to the parameters in the super call
                    // as without the casts the compiler will fail if there is more than one
                    // constructor that could match.
                    val defaultValueWithCast =
                        defaultValueWithCastForType(parameter.type(), typeParameterBindings)
                    writer.write(defaultValueWithCast)
                }
                writer.print("); ")
            }
        }

        writeThrowStub()
    }

    /**
     * Get the string representation of the default value for [type], it will include a cast if
     * necessary.
     *
     * If [type] is a [VariableTypeItem] then it will map it to the appropriate type given the
     * [typeParameterBindings]. See the comment in the body for more details.
     */
    private fun defaultValueWithCastForType(
        type: TypeItem,
        typeParameterBindings: TypeParameterBindings,
    ): String {
        // Handle special cases and non-reference types, drop through to handle the default
        // reference type.
        when (type) {
            is PrimitiveTypeItem -> {
                val kind = type.kind
                return when (kind) {
                    PrimitiveTypeItem.Primitive.BOOLEAN,
                    PrimitiveTypeItem.Primitive.INT,
                    PrimitiveTypeItem.Primitive.LONG -> kind.defaultValueString
                    else -> "(${kind.primitiveName})${kind.defaultValueString}"
                }
            }
            is ClassTypeItem -> {
                val qualifiedName = type.qualifiedName
                when (qualifiedName) {
                    JAVA_LANG_STRING -> return "\"\""
                }
            }
        }

        // Get the actual type that the super constructor expects, taking into account any type
        // parameter mappings.
        val mappedType =
            if (type is VariableTypeItem) {
                // The super constructor's parameter is a type variable: so see if it should be
                // mapped back to a type specified by this class. e.g.
                //
                // Given:
                //   class Bar<T extends Number> {
                //       public Bar(int i) {}
                //       public Bar(T t) {}
                //   }
                //   class Foo extends Bar<Integer> {
                //       public Foo(Integer i) { super(i); }
                //   }
                //
                // The stub for Foo should use:
                //     super((Integer) i);
                // Not:
                //     super((Number) i);
                //
                // However, if the super class is referenced as a raw type then there will be no
                // mapping in which case fall back to the erased type which will use the type
                // variable's lower bound. e.g.
                //
                // Given:
                //   class Foo extends Bar {
                //       public Foo(Integer i) { super(i); }
                //   }
                //
                // The stub for Foo should use:
                //     super((Number) i);
                type.convertType(typeParameterBindings)
            } else {
                type
            }

        // Casting to the erased type could lead to unchecked warnings (which are suppressed) but
        // avoids having to deal with parameterized types and ensures that casting to a vararg
        // parameter uses an array type.
        val erasedTypeString = mappedType.toErasedTypeString()
        return "($erasedTypeString)null"
    }

    override fun visitMethod(method: MethodItem) {
        writeMethod(method.containingClass(), method)
    }

    private fun writeMethod(containingClass: ClassItem, method: MethodItem) {
        writer.println()
        appendDocumentation(method, writer, config)

        appendModifiers(method)
        generateTypeParameterList(typeList = method.typeParameterList, addSpace = true)

        val returnType = method.returnType()
        writer.print(returnType.toTypeString(annotations = false))

        writer.print(' ')
        writer.print(method.name())
        generateParameterList(method)
        generateThrowsList(method)

        if (containingClass.isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                writer.print(" default ")
                writer.print(default)
            }
        }

        if (ModifierListWriter.requiresMethodBodyInStubs(method.actualItem)) {
            writer.print(" { ")
            writeThrowStub()
            writer.println(" }")
        } else {
            writer.println(";")
        }
    }

    override fun visitField(field: FieldItem) {
        // Handled earlier in visitClass
        if (field.isEnumConstant()) {
            return
        }

        writer.println()

        appendDocumentation(field, writer, config)
        appendModifiers(field)
        writer.print(field.type().toTypeString(annotations = false))
        writer.print(' ')
        writer.print(field.name())
        val needsInitialization =
            field.actualItem.modifiers.isFinal() &&
                field.initialValue(true) == null &&
                field.containingClass().isClass()
        field.writeValueWithSemicolon(
            writer,
            allowDefaultValue = !needsInitialization,
            requireInitialValue = !needsInitialization
        )
        writer.print("\n")

        if (needsInitialization) {
            if (field.modifiers.isStatic()) {
                writer.print("static ")
            }
            writer.print("{ ${field.name()} = ${field.type().defaultValueString()}; }\n")
        }
    }

    private fun writeThrowStub() {
        writer.write("throw new RuntimeException(\"Stub!\");")
    }

    private fun generateParameterList(callable: CallableItem) {
        writer.print("(")
        callable.parameters().asSequence().forEachIndexed { i, parameter ->
            if (i > 0) {
                writer.print(", ")
            }
            appendModifiers(parameter)
            writer.print(parameter.type().toTypeString(annotations = false))
            writer.print(' ')
            val name = parameter.publicName() ?: parameter.name()
            writer.print(name)
        }
        writer.print(")")
    }

    private fun generateThrowsList(callable: CallableItem) {
        val throws = callable.throwsTypes()
        if (throws.isNotEmpty()) {
            writer.print(" throws ")
            throws.sortedWith(ExceptionTypeItem.fullNameComparator).forEachIndexed { i, type ->
                if (i > 0) {
                    writer.print(", ")
                }
                writer.print(type.toTypeString())
            }
        }
    }
}
