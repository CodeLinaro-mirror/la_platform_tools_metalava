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

package com.android.tools.metalava

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import java.io.PrintWriter
import java.util.BitSet

class SignatureWriter(
    private val writer: PrintWriter,
    private var emitHeader: EmitFileHeader = EmitFileHeader.ALWAYS,
    private val fileFormat: FileFormat,
) : DelegatedVisitor {

    init {
        // If a header must always be written out (even if the file is empty) then write it here.
        if (emitHeader == EmitFileHeader.ALWAYS) {
            writer.print(fileFormat.header())
        }
    }

    /**
     * Create an [ApiVisitor] that will filter the [Item] to which is applied according to the
     * supplied parameters and in a manner appropriate for writing signatures, e.g. flattening
     * nested classes. It will delegate any visitor calls that pass through its filter to this
     * [SignatureWriter] instance.
     */
    fun createFilteringVisitor(
        apiType: ApiType,
        preFiltered: Boolean,
        showUnannotated: Boolean,
        apiVisitorConfig: ApiVisitor.Config,
    ): ApiVisitor {
        val filterEmit = apiType.getEmitFilter(apiVisitorConfig.apiPredicateConfig)
        val filterReference = apiType.getReferenceFilter(apiVisitorConfig.apiPredicateConfig)

        val (interfaceListSorter, interfaceListComparator) =
            if (fileFormat.sortWholeExtendsList) Pair(null, TypeItem.totalComparator)
            else Pair(::getInterfacesInOrder, null)
        return FilteringApiVisitor(
            delegate = this,
            preserveClassNesting = false,
            inlineInheritedFields = true,
            callableComparator = fileFormat.overloadedMethodOrder.comparator,
            interfaceListSorter = interfaceListSorter,
            interfaceListComparator = interfaceListComparator,
            filterEmit = filterEmit,
            filterReference = filterReference,
            preFiltered = preFiltered,
            showUnannotated = showUnannotated,
            config = apiVisitorConfig,
        )
    }

    private val modifierListWriter =
        ModifierListWriter.forSignature(
            writer = writer,
            skipNullnessAnnotations = fileFormat.kotlinStyleNulls,
        )

    internal fun write(text: String) {
        // If a header must only be written out when the file is not empty then write it here as
        // this is not called
        if (emitHeader == EmitFileHeader.IF_NONEMPTY_FILE) {
            writer.print(fileFormat.header())
            // Remember that the header was written out, so it will not be written again.
            emitHeader = EmitFileHeader.NEVER
        }
        writer.print(text)
    }

    override fun visitPackage(pkg: PackageItem) {
        write("package ")
        writeModifiers(pkg)
        write("${pkg.qualifiedName()} {\n\n")
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        write("}\n\n")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        fun writeConstructor(skipMask: BitSet? = null) {
            write("    ctor ")
            writeModifiers(constructor)
            writeTypeParameterList(constructor.typeParameterList, addSpace = true)
            write(constructor.containingClass().fullName())
            writeParameterList(constructor, skipMask)
            writeThrowsList(constructor)
            write(";\n")
        }

        // Workaround for https://youtrack.jetbrains.com/issue/KT-57537
        if (constructor.shouldExpandOverloads()) {
            val parameters = constructor.parameters()
            val defaultMask = BitSet(parameters.size)

            // fill the bitmask for all parameters
            parameters.forEachIndexed { i, item -> defaultMask.set(i, item.hasDefaultValue()) }

            // expand overloads ordered by number of parameters, skipping last parameters first
            for (i in parameters.indices) {
                if (!defaultMask.get(i)) continue
                writeConstructor(defaultMask)
                defaultMask.clear(i)
            }
        }

        writeConstructor()
    }

    override fun visitField(field: FieldItem) {
        val name = if (field.isEnumConstant()) "enum_constant" else "field"
        write("    ")
        write(name)
        write(" ")
        writeModifiers(field)

        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the field, then the type.
            write(field.name())
            write(": ")
            writeType(field.type())
        } else {
            // Java style: write the type, then the name of the field.
            writeType(field.type())
            write(" ")
            write(field.name())
        }

        field.writeValueWithSemicolon(
            writer,
            allowDefaultValue = false,
            requireInitialValue = false
        )
        write("\n")
    }

    override fun visitProperty(property: PropertyItem) {
        write("    property ")
        writeModifiers(property)
        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the property, then the type.
            write(property.name())
            write(": ")
            writeType(property.type())
        } else {
            // Java style: write the type, then the name of the property.
            writeType(property.type())
            write(" ")
            write(property.name())
        }
        write(";\n")
    }

    override fun visitMethod(method: MethodItem) {
        write("    method ")
        writeModifiers(method)
        writeTypeParameterList(method.typeParameterList, addSpace = true)

        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the method and the parameters, then the type.
            write(method.name())
            writeParameterList(method)
            write(": ")
            writeType(method.returnType())
        } else {
            // Java style: write the type, then the name of the method and the parameters.
            writeType(method.returnType())
            write(" ")
            write(method.name())
            writeParameterList(method)
        }

        writeThrowsList(method)

        if (method.containingClass().isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                write(" default ")
                write(default)
            }
        }

        write(";\n")
    }

    override fun visitClass(cls: ClassItem) {
        write("  ")

        writeModifiers(cls)

        if (cls.isAnnotationType()) {
            write("@interface")
        } else if (cls.isInterface()) {
            write("interface")
        } else if (cls.isEnum()) {
            write("enum")
        } else {
            write("class")
        }
        write(" ")
        write(cls.fullName())
        writeTypeParameterList(cls.typeParameterList, addSpace = false)
        writeSuperClassStatement(cls)
        writeInterfaceList(cls)

        write(" {\n")
    }

    override fun afterVisitClass(cls: ClassItem) {
        write("  }\n\n")
    }

    private fun writeModifiers(item: Item) {
        modifierListWriter.write(item.actualItem)
    }

    private fun writeSuperClassStatement(cls: ClassItem) {
        if (cls.isEnum() || cls.isAnnotationType() || cls.isInterface()) {
            return
        }

        /** Get the super class type, ignoring java.lang.Object. */
        val superClassType = cls.superClassType()
        if (superClassType == null || superClassType.isJavaLangObject()) return

        write(" extends")
        writeExtendsOrImplementsType(superClassType)
    }

    private fun writeExtendsOrImplementsType(typeItem: TypeItem) {
        val superClassString =
            typeItem.toTypeString(
                annotations = fileFormat.includeTypeUseAnnotations,
                kotlinStyleNulls = fileFormat.kotlinStyleNulls,
            )
        write(" ")
        write(superClassString)
    }

    private fun writeInterfaceList(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            return
        }

        // There is no need to sort the interface types as that is done by the `interfaceTypes()`
        // method, using the `interfaceListAccessor(...)` method.
        val orderedInterfaces = cls.interfaceTypes()
        if (orderedInterfaces.isEmpty()) return

        val label = if (cls.isInterface()) " extends" else " implements"
        write(label)

        orderedInterfaces.forEach { typeItem -> writeExtendsOrImplementsType(typeItem) }
    }

    private fun writeTypeParameterList(typeList: TypeParameterList, addSpace: Boolean) {
        val typeListString = typeList.toString()
        if (typeListString.isNotEmpty()) {
            write(typeListString)
            if (addSpace) {
                write(" ")
            }
        }
    }

    private fun writeParameterList(callable: CallableItem, skipMask: BitSet? = null) {
        write("(")
        var writtenParams = 0
        callable.parameters().asSequence().forEachIndexed { i, parameter ->
            // skip over defaults when generating @JvmOverloads permutations
            if (skipMask != null && skipMask.get(i)) return@forEachIndexed

            if (writtenParams > 0) {
                write(", ")
            }
            if (parameter.hasDefaultValue() && fileFormat.conciseDefaultValues) {
                // Concise representation of a parameter with a default
                write("optional ")
            }
            writeModifiers(parameter)

            if (fileFormat.kotlinNameTypeOrder) {
                // Kotlin style: the parameter must have a name (use `_` if it doesn't have a public
                // name). Write the name and then the type.
                val name = parameter.publicName() ?: "_"
                write(name)
                write(": ")
                writeType(parameter.type())
            } else {
                // Java style: write the type, then the name if it has a public name.
                writeType(parameter.type())
                val name = parameter.publicName()
                if (name != null) {
                    write(" ")
                    write(name)
                }
            }

            if (parameter.isDefaultValueKnown() && !fileFormat.conciseDefaultValues) {
                write(" = ")
                val defaultValue = parameter.defaultValueAsString()
                if (defaultValue != null) {
                    write(defaultValue)
                } else {
                    // null is a valid default value!
                    write("null")
                }
            }
            writtenParams++
        }
        write(")")
    }

    private fun writeType(type: TypeItem?) {
        type ?: return

        var typeString =
            type.toTypeString(
                annotations = fileFormat.includeTypeUseAnnotations,
                kotlinStyleNulls = fileFormat.kotlinStyleNulls,
            )

        // Strip java.lang. prefix
        typeString = TypeItem.shortenTypes(typeString)

        write(typeString)
    }

    private fun writeThrowsList(callable: CallableItem) {
        val throws = callable.throwsTypes()
        if (throws.isNotEmpty()) {
            write(" throws ")
            throws.sortedWith(ExceptionTypeItem.fullNameComparator).forEachIndexed { i, type ->
                if (i > 0) {
                    write(", ")
                }
                write(type.toTypeString())
            }
        }
    }
}

enum class EmitFileHeader {
    ALWAYS,
    NEVER,
    IF_NONEMPTY_FILE
}

/**
 * Get the filtered list of [ClassItem.interfaceTypes], in the correct legacy order.
 *
 * Historically, on interface classes its first implemented interface type was stored in the
 * [ClassItem.superClassType] and if it was not filtered out it was always written out first in the
 * signature files, while the rest of the interface types were sorted by their [ClassItem.fullName].
 * This implements that behavior.
 */
private fun getInterfacesInOrder(
    classItem: ClassItem,
    filteredInterfaceTypes: List<ClassTypeItem>,
    unfilteredInterfaceTypes: List<ClassTypeItem>,
): List<ClassTypeItem> {
    // Sort before prepending the super class (if this is an interface) as the super class
    // always comes first because it was previously written out by writeSuperClassStatement.
    @Suppress("DEPRECATION")
    val sortedInterfaces = filteredInterfaceTypes.sortedWith(TypeItem.partialComparator)

    // Combine the super class and interfaces into a full list of them.
    if (classItem.isInterface()) {
        // Previously, when the first interface in the extends list was stored in
        // superClass, if that interface was visible in the signature then it would always
        // be first even though the other interfaces are sorted in alphabetical order. This
        // implements similar logic.
        val firstUnfilteredInterfaceType = unfilteredInterfaceTypes.first()

        // Check to see whether the first unfiltered interface type is in the sorted set of
        // interfaces. If it is, and it is not the first then it needs moving to the beginning.
        val index = sortedInterfaces.indexOf(firstUnfilteredInterfaceType)
        if (index > 0) {
            // Create a mutable list and move the first unfiltered interface type to the beginning.
            return sortedInterfaces.toMutableList().also { mutable ->
                // Remove it from its existing position.
                mutable.removeAt(index)

                // Add it at the beginning.
                mutable.add(0, firstUnfilteredInterfaceType)
            }
        }
    }

    return sortedInterfaces
}
