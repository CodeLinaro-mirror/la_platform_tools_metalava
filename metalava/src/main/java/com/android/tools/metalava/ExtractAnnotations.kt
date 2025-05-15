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

import com.android.tools.lint.LintCliClient.Companion.printWriter
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROIDX_FLOAT_RANGE
import com.android.tools.metalava.model.ANDROIDX_INT_RANGE
import com.android.tools.metalava.model.ANDROIDX_REQUIRES_PERMISSION_READ
import com.android.tools.metalava.model.ANDROIDX_REQUIRES_PERMISSION_WRITE
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.asAnnotationAttributeValue
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.SingleArrayElementFormat
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueStringConfiguration
import com.android.tools.metalava.model.value.asDouble
import com.android.tools.metalava.model.value.asLong
import com.android.tools.metalava.model.value.provider
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.google.common.xml.XmlEscapers
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

// Like the tools/base Extractor class, but limited to our own (mapped) AnnotationItems,
// and only those with source retention (and in particular right now that just means the
// typedef annotations.)
class ExtractAnnotations(
    private val codebase: Codebase,
    private val reporter: Reporter,
    private val outputFile: File,
) :
    ApiVisitor(
        apiPredicateConfig = @Suppress("DEPRECATION") options.apiPredicateConfig,
    ) {
    // Used linked hash map for order such that we always emit parameters after their surrounding
    // method etc
    private val packageToAnnotationPairs =
        LinkedHashMap<PackageItem, MutableList<Pair<Item, AnnotationItem>>>()

    private val classToAnnotationHolder = mutableMapOf<String, AnnotationItem>()

    fun extractAnnotations() {
        codebase.accept(this)

        // Write external annotations
        FileOutputStream(outputFile).use { fileOutputStream ->
            JarOutputStream(BufferedOutputStream(fileOutputStream)).use { zos ->
                val sortedPackages =
                    packageToAnnotationPairs.keys
                        .asSequence()
                        .sortedBy { it.qualifiedName() }
                        .toList()

                // Create a print writer to the JarOutputStream. Care must be taken not to close
                // this until all entries have been written.
                val printWriter = zos.printWriter()

                for (pkg in sortedPackages) {
                    // Note: Using / rather than File.separator: jar lib requires it
                    val name = pkg.qualifiedName().replace('.', '/') + "/annotations.xml"

                    val outEntry = JarEntry(name)
                    outEntry.time = 0
                    zos.putNextEntry(outEntry)

                    val pairs = packageToAnnotationPairs[pkg] ?: continue

                    // Ensure stable output
                    if (pairs.size > 1) {
                        pairs.sortBy { it.first.getExternalAnnotationSignature() }
                    }

                    printWriter.let { writer ->
                        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>")

                        var open = false
                        var previousSignature: String? = null
                        for ((item, annotation) in pairs) {
                            val signature = item.getExternalAnnotationSignature()
                            if (signature != previousSignature) {
                                if (open) {
                                    writer.print("  </item>")
                                    writer.println()
                                }
                                writer.print("  <item name=\"")
                                writer.print(signature)
                                writer.println("\">")
                                open = true
                            }
                            previousSignature = signature

                            writeAnnotation(writer, item, annotation)
                        }
                        if (open) {
                            writer.print("  </item>")
                            writer.println()
                        }
                        writer.println("</root>\n")

                        // Flush the writer to ensure all the data is written to the zip entry
                        // before it is closed. Do not close the writer as that will close the whole
                        // zip output stream.
                        writer.flush()
                    }

                    // Close the zip entry.
                    zos.closeEntry()
                }
            }
        }
    }

    private fun addItem(item: Item, annotation: AnnotationItem) {
        val pkg =
            when (item) {
                is ClassItem -> item.containingPackage()
                is MemberItem -> item.containingClass().containingPackage()
                is ParameterItem -> item.containingCallable().containingClass().containingPackage()
                else -> return
            }

        val list =
            packageToAnnotationPairs[pkg]
                ?: run {
                    val new = mutableListOf<Pair<Item, AnnotationItem>>()
                    packageToAnnotationPairs[pkg] = new
                    new
                }
        list.add(Pair(item, annotation))
    }

    override fun visitClass(cls: ClassItem) {
        checkItem(cls)
    }

    override fun visitField(field: FieldItem) {
        checkItem(field)
    }

    override fun visitCallable(callable: CallableItem) {
        checkItem(callable)
    }

    override fun visitParameter(parameter: ParameterItem) {
        checkItem(parameter)
    }

    /** For a given item, extract the relevant annotations for that item */
    private fun checkItem(item: Item) {
        for (annotation in item.modifiers.annotations()) {
            val qualifiedName = annotation.qualifiedName
            if (
                qualifiedName.startsWith(JAVA_LANG_PREFIX) ||
                    qualifiedName.startsWith(ANDROIDX_ANNOTATION_PREFIX) ||
                    qualifiedName.startsWith(ANDROID_ANNOTATION_PREFIX)
            ) {
                if (annotation.isTypeDefAnnotation()) {
                    // Imported typedef
                    addItem(item, annotation)
                } else if (
                    annotation.targets.contains(AnnotationTarget.EXTERNAL_ANNOTATIONS_FILE)
                ) {
                    addItem(item, annotation)
                }

                continue
            } else if (
                qualifiedName.startsWith(ORG_JETBRAINS_ANNOTATIONS_PREFIX) ||
                    qualifiedName.startsWith(ORG_INTELLIJ_LANG_ANNOTATIONS_PREFIX)
            ) {
                // Externally merged metadata, like @Contract and @Language
                addItem(item, annotation)
                continue
            }

            val typeDefClass = annotation.resolve() ?: continue
            val className = typeDefClass.qualifiedName()
            if (typeDefClass.isAnnotationType()) {
                val cached = classToAnnotationHolder[className]
                if (cached != null) {
                    addItem(item, cached)
                    continue
                }

                val typeDefAnnotation =
                    typeDefClass.modifiers.findAnnotation(AnnotationItem::isTypeDefAnnotation)
                if (typeDefAnnotation != null) {
                    // Make sure it has the right retention
                    if (typeDefClass.annotationClass.retention != AnnotationRetention.SOURCE) {
                        reporter.report(
                            Issues.ANNOTATION_EXTRACTION,
                            typeDefClass,
                            "This typedef annotation class should have @Retention(RetentionPolicy.SOURCE)"
                        )
                    }

                    if (filterEmit.test(typeDefClass)) {
                        reporter.report(
                            Issues.ANNOTATION_EXTRACTION,
                            typeDefClass,
                            "This typedef annotation class should be marked @hide or should not be marked public"
                        )
                    }

                    classToAnnotationHolder[className] = typeDefAnnotation
                    addItem(item, typeDefAnnotation)

                    if (
                        item is MethodItem &&
                            !reporter.isSuppressed(Issues.RETURNING_UNEXPECTED_CONSTANT)
                    ) {
                        item.body.verifyReturnedConstants(typeDefAnnotation, typeDefClass)
                    }
                }
            }
        }
    }

    private fun escapeXml(unescaped: String): String {
        return XmlEscapers.xmlAttributeEscaper().escape(unescaped)
    }

    private fun Item.getExternalAnnotationSignature(): String? {
        when (this) {
            is PackageItem -> {
                return escapeXml(qualifiedName())
            }
            is ClassItem -> {
                return escapeXml(qualifiedName())
            }
            is CallableItem -> {
                val sb = StringBuilder(100)
                sb.append(escapeXml(containingClass().qualifiedName()))
                sb.append(' ')

                if (isConstructor()) {
                    sb.append(escapeXml(containingClass().simpleName()))
                } else {
                    sb.append(escapeXml(returnType().toTypeString()))
                    sb.append(' ')
                    sb.append(escapeXml(name()))
                }

                sb.append('(')

                // The signature must match *exactly* the formatting used by IDEA,
                // since it looks up external annotations in a map by this key.
                // Therefore, it is vital that the parameter list uses exactly one
                // space after each comma between parameters, and *no* spaces between
                // generics variables, e.g. foo(Map<A,B>, int)
                var i = 0
                val parameterList = parameters()
                val n = parameterList.size
                while (i < n) {
                    if (i > 0) {
                        sb.append(',').append(' ')
                    }
                    val type =
                        parameterList[i]
                            .type()
                            .toTypeString()
                            .replace(" ", "")
                            .replace("?extends", "? extends ")
                            .replace("?super", "? super ")
                    sb.append(escapeXml(type))
                    i++
                }
                sb.append(')')
                return sb.toString()
            }
            is FieldItem -> {
                return escapeXml(containingClass().qualifiedName()) + " " + name()
            }
            is ParameterItem -> {
                return containingCallable().getExternalAnnotationSignature() +
                    " " +
                    this.parameterIndex
            }
        }

        return null
    }

    private fun writeAnnotation(writer: PrintWriter, item: Item, annotationItem: AnnotationItem) {
        // Retrieve the attributes from the annotation item.
        val attributes = retrieveAttributes(item, annotationItem)

        // Some annotations need to keep field references and some need to replace them with their
        // constant value.
        val keepFieldReferences = keepFieldReferences(annotationItem)

        // Perform some transformations and filtering on the attributes.
        val transformedAttributes =
            attributes.mapNotNull { attribute ->
                val name = attribute.name

                // Platform typedef annotations declare prefix/suffix attributes for historical
                // reasons, and they are no longer necessary; they should also not be part of the
                // extracted metadata.
                if (
                    ("prefix" == name || "suffix" == name) && annotationItem.isTypeDefAnnotation()
                ) {
                    reporter.report(
                        Issues.SUPERFLUOUS_PREFIX,
                        item,
                        "Superfluous $name attribute on typedef"
                    )
                    return@mapNotNull null
                }

                // Transform/filter the value.
                val transformedValue =
                    attribute.value.transform { value ->
                        when (value) {
                            // If the value is a field then it needs some additional checking.
                            is FieldReferenceValue -> {
                                // Make sure it can be resolved, if not report an issue.
                                val fieldItem = value.resolve()
                                if (fieldItem == null) {
                                    reporter.report(
                                        Issues.INTERNAL_ERROR,
                                        reportable = null,
                                        "Unexpected reference to ${value.toValueString()}",
                                        location = annotationItem.fileLocation,
                                    )
                                    return@transform null
                                }

                                if (keepFieldReferences) {
                                    // If keeping the field then make sure it can be referenced from
                                    // the API. If not then discard it.
                                    if (!filterReference.test(fieldItem)) {
                                        // This field is not visible: remove from typedef
                                        reporter.report(
                                            Issues.HIDDEN_TYPEDEF_CONSTANT,
                                            fieldItem,
                                            "Typedef class references hidden field $fieldItem: removed from typedef metadata"
                                        )
                                        return@transform null
                                    }

                                    value
                                } else {
                                    value.asLiteralValue()
                                }
                            }
                            // Other values can just be passed straight through.
                            else -> value
                        }
                    }

                // If the transformed value is null then filter it out.
                transformedValue ?: return@mapNotNull null

                name to transformedValue
            }

        // If an annotation had attributes, but they were all filtered out then the chances are that
        // the annotation is worthless so drop it altogether.
        if (attributes.isNotEmpty() && transformedAttributes.isEmpty()) {
            // All items were filtered out: don't write the annotation at all
            return
        }

        // Write the annotation element.
        val qualifiedName = annotationItem.qualifiedName
        writeAnnotationElement(writer, qualifiedName, transformedAttributes)
    }

    /** Retrieve the attributes from [annotationItem]. */
    private fun retrieveAttributes(
        item: Item,
        annotationItem: AnnotationItem
    ): List<AnnotationAttribute> {
        val qualifiedName = annotationItem.qualifiedName

        // Ensure consistent ordering.
        val attributes =
            annotationItem.attributes.sortedWith(
                compareBy(
                    // Ensure that the value attribute is written first
                    { it.name != ANNOTATION_ATTR_VALUE },
                    { it.name },
                )
            )

        when (qualifiedName) {
            ANDROIDX_REQUIRES_PERMISSION_READ,
            ANDROIDX_REQUIRES_PERMISSION_WRITE -> {
                if (attributes.size == 1) {
                    // The external annotations format does not allow for nested/complex
                    // annotations. However, these special annotations (@RequiresPermission.Read,
                    // @RequiresPermission.Write) are known to only be simple containers with a
                    // single permission child, so instead we "inline" the content:
                    //  @Read(@RequiresPermission(allOf={P1,P2},conditional=true)
                    //     =>
                    //  @RequiresPermission.Read(allOf({P1,P2},conditional=true)
                    //
                    // That's setting attributes that don't actually exist on the container
                    // permission, but we'll counteract that on the read-annotations side.
                    (attributes[0].value as? AnnotationValue)?.let { value ->
                        return value.annotationItem.attributes
                    }
                }
            }
            // `@IntRange` can be used to set the range of both `int`s and `long`s. As a result its
            // `from` and `to` attributes are `long` as that covers both types. However, it makes
            // little sense to use `long` values when the type to which it is applied is an `int`.
            // In that case this converts those attributes to `int`s.
            // TODO(b/354633349): Consider moving this to annotation item creation to make the value
            //   types appropriate for the annotated item everywhere not just here.
            ANDROIDX_INT_RANGE -> {
                val type = item.type()
                if (type is PrimitiveTypeItem && type.kind == PrimitiveTypeItem.Primitive.INT) {
                    return attributes.mapNotNull { attribute ->
                        val name = attribute.name
                        if (name == "from" || name == "to") {
                            attribute.value.asLong()?.let { long ->
                                val intValue = Value.createLiteralValue(null, long.toInt())
                                DefaultAnnotationAttribute(
                                    name,
                                    intValue.provider(),
                                    intValue.asAnnotationAttributeValue()
                                )
                            }
                        } else attribute
                    }
                }
            }
            // `@FloatRange` can be used to set the range of both `float`s and `doubles`s. As a
            // result its `from` and `to` attributes are `doubles` as that covers both types.
            // However, it makes little sense to use `doubles` values when the type to which it is
            // applied is a `float`. Especially given that converting a `float` to a `double` can
            // result in a different serialized form. In that case this converts those attributes to
            // `float`s.
            ANDROIDX_FLOAT_RANGE -> {
                val type = item.type()
                if (type is PrimitiveTypeItem && type.kind == PrimitiveTypeItem.Primitive.FLOAT) {
                    return attributes.mapNotNull { attribute ->
                        val name = attribute.name
                        if (name == "from" || name == "to") {
                            attribute.value.asDouble()?.let { double ->
                                val floatValue = Value.createLiteralValue(null, double.toFloat())
                                DefaultAnnotationAttribute(
                                    name,
                                    floatValue.provider(),
                                    floatValue.asAnnotationAttributeValue()
                                )
                            }
                        } else attribute
                    }
                }
            }
        }

        return attributes
    }

    /**
     * Write the annotation element to [writer].
     *
     * @param qualifiedName the name of the annotation class.
     * @param attributes the attributes, as a list of name/value pairs.
     */
    private fun writeAnnotationElement(
        writer: PrintWriter,
        qualifiedName: String,
        attributes: List<Pair<String, Value>>
    ) {
        // Begin the annotation element.
        writer.print("    <annotation name=\"")
        writer.print(qualifiedName)

        // If no attributes are provided then close it immediately.
        if (attributes.isEmpty()) {
            writer.print("\"/>")
            writer.println()
            return
        }

        // Complete the open annotation element.
        writer.print("\">")
        writer.println()

        // Add entries for each attribute.
        for ((name, value) in attributes) {
            val valueString = value.toValueString(EXTRACT_VALUE_STRING_CONFIGURATION)

            // The value could contain fully qualified references to enum values that are in the
            // android.annotation package. If so, then replace them with references in the
            // androidx.annotation package.
            val normalizedValueString =
                valueString.replace(ANDROID_ANNOTATION_PREFIX, ANDROIDX_ANNOTATION_PREFIX)

            writer.print("      <val name=\"")
            writer.print(name)
            writer.print("\" val=\"")
            writer.print(escapeXml(normalizedValueString))
            writer.println("\" />")
        }

        writer.println("    </annotation>")
    }

    /** Type def annotations must keep field references. */
    private fun keepFieldReferences(annotationItem: AnnotationItem): Boolean {
        return annotationItem.isTypeDefAnnotation()
    }

    companion object {
        /**
         * [ValueStringConfiguration] that is used when serializing [Value]s to an `annotations.xml`
         * file.
         */
        private val EXTRACT_VALUE_STRING_CONFIGURATION =
            ValueStringConfiguration(
                singleArrayElementFormat = SingleArrayElementFormat.UNWRAP,
            )
    }
}
