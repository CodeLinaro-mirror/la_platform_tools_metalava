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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.junit4.ParameterFilter
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig
import com.android.tools.metalava.model.testing.value.assertValuesAreStrictlyEqual
import com.android.tools.metalava.model.testing.value.runValueTest
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.ModelSuiteRunner
import com.android.tools.metalava.model.testsuite.value.BaseCommonParameterizedValueTest.Companion.testCases
import com.android.tools.metalava.model.testsuite.value.BaseCommonParameterizedValueTest.TestClass
import com.android.tools.metalava.model.testsuite.value.CommonParameterizedFieldWriteWithSemicolonValueTest.Companion.testParameters
import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.ATTRIBUTE_NAME
import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.FIELD_NAME
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.valueExamples
import com.android.tools.metalava.model.value.FieldReferenceValue
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueKind
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.runners.Parameterized

/**
 * Base class of parameterized tests that will run test cases against both jars and sources to check
 * the behavior of values.
 *
 * This only has a single test method which runs a list of [TestCase]s against a list of
 * [CodebaseProducer]s. A [TestCase] defines the test to run and the [CodebaseProducer] defines the
 * [Codebase] it will be run on, either jar or source based.
 *
 * [TestCase] provides the details of the test to run but the actual test logic is provided by
 * subclasses of this class. Each subclass selects the [TestCase]s that apply to it and then runs
 * the test to check different [LegacyValueUseSite]s, e.g. annotation value, method default value,
 * field value. This approach was taken instead of having methods for each use of a value because
 * the values should be being handled consistently irrespective of where they are being used, but
 * currently they are not. Having all the tests for them being run on the same [TestCase]s
 * highlights the inconsistencies and makes it easier to migrate to consistent handling of the
 * values.
 *
 * Each [TestCase] runs against a [TestClass] which specifies the class name to use and the set of
 * [TestFile]s to use. The jar tests run against a jar constructed from all the distinct [TestFile]
 * used by all the [testCases]. The source tests run against just the set of sources needed by the
 * [TestClass].
 *
 * The set of files needed by a [TestClass] are encapsulated in a [TestClass.testFileSet] which
 * tracks dependencies.
 *
 * The set of tests to run is defined by a list of [ValueExample]s which provide the necessary
 * information to construct [TestClass]es and the [TestCase]s that run against them. Each
 * [ValueExample] is tested in all possible [LegacyValueUseSite] (although some examples do not work
 * on some sites). The aim is to create an exhaustive set of tests that first map out the existing
 * inconsistencies and eventually ensure consistent behavior.
 *
 * @param testFileCache the [TestFileCache] in which all the [TestFile]s used by this test class
 *   will be cached.
 * @param testJarFile the [TestFile] for the jar file built from all the java source files used by
 *   this test class.
 * @param legacyValueUseSite the [LegacyValueUseSite] being tested by this class.
 */
abstract class BaseCommonParameterizedValueTest(
    private val testFileCache: TestFileCache,
    private val testJarFile: TestFile,
    private val legacyValueUseSite: LegacyValueUseSite,
) : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var codebaseProducer: CodebaseProducer

    @Parameterized.Parameter(1) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule
    val entryPointCallerRule = EntryPointCallerRule {
        testCase.valueExample.entryPointCallerTracker
    }

    /** Produces a [Codebase] to test and runs the test on it. */
    sealed class CodebaseProducer(val kind: ProducerKind) {
        /**
         * Produce a [Codebase] and run [test] on it.
         *
         * Run with [BaseCommonParameterizedValueTest] as the receiver so it can access
         * [runSourceCodebaseTest].
         */
        abstract fun BaseCommonParameterizedValueTest.runCodebaseProducerTest(
            testFileCache: TestFileCache,
            testCase: TestCase,
            test: TestCaseContext.() -> Unit,
        )

        protected fun CodebaseContext.runTestCase(
            testCase: TestCase,
            legacyValueUseSite: LegacyValueUseSite,
            test: TestCaseContext.() -> Unit
        ) {
            val testCaseContext = TestCaseContext(this, testCase, kind, legacyValueUseSite)
            testCaseContext.test()
        }

        final override fun toString() = kind.toString().lowercase()
    }

    /**
     * Base of classes that perform a specific test on a [Codebase] produced by [CodebaseProducer].
     */
    class TestCase(
        /** The [ValueExample] on which this test case is based. */
        val valueExample: ValueExample,
    ) : Assertions {
        private val testClassesByInputFormat = mutableMapOf<InputFormat, TestClasses?>()

        /** Get the [TestClass] appropriate for [legacyValueUseSite]. */
        fun testClassFor(inputFormat: InputFormat, legacyValueUseSite: LegacyValueUseSite) =
            testClassesByInputFormat
                .computeIfAbsent(inputFormat) {
                    if (it !in valueExample.validForInputFormats) return@computeIfAbsent null
                    val creator =
                        when (it) {
                            InputFormat.JAVA -> JavaTestClassCreator
                            InputFormat.KOTLIN -> KotlinTestClassCreator
                            InputFormat.SIGNATURE -> SignatureTestClassCreator
                            else -> error("Unknown input format: $inputFormat")
                        }

                    TestClasses(creator, valueExample)
                }
                ?.testClassFor(legacyValueUseSite)

        override fun toString() = valueExample.name
    }

    /**
     * Creates and caches the [TestClass]es needed for [valueExample].
     *
     * When first requested for a [TestClass] for [LegacyValueUseSite] in [testClassFor] it will
     * invoke [testClassCreator] to create one, cache it and return it. On subsequent calls it will
     * return the cached version. This ensures a single [TestClass] for each
     * [LegacyValueUseSite]/[ValueExample] combination.
     *
     * @param testClassCreator responsible for creating instances of the [TestClass] that this
     *   caches.
     * @param valueExample the [ValueExample] for which the [TestClass]es are created.
     */
    class TestClasses(
        private val testClassCreator: TestClassCreator,
        private val valueExample: ValueExample
    ) {
        /** Get the [TestClass] appropriate for [legacyValueUseSite]. */
        fun testClassFor(legacyValueUseSite: LegacyValueUseSite) =
            when (legacyValueUseSite) {
                LegacyValueUseSite.ATTRIBUTE_VALUE,
                LegacyValueUseSite.ANNOTATION_TO_SOURCE -> annotatedWithAnnotationWithoutDefaults
                LegacyValueUseSite.ATTRIBUTE_DEFAULT_VALUE -> annotationWithDefaults
                LegacyValueUseSite.FIELD_VALUE,
                LegacyValueUseSite.FIELD_WRITE_WITH_SEMICOLON -> field
            }

        /**
         * A class called `AnnotationWithDefault_...` for [valueExample] using
         * [ValueExample.javaExpression] as the default value of the [ATTRIBUTE_NAME] attribute.
         */
        private val annotationWithDefaults by
            lazy(LazyThreadSafetyMode.NONE) {
                testClassCreator.generateAnnotationClass(
                    valueExample,
                    "AnnotationWithDefaults",
                    withDefaults = true,
                )
            }

        /**
         * A class called `AnnotationTestClass_...` for [valueExample] which is annotated with an
         * annotation called `AnnotationWithoutDefaults_...` whose [ATTRIBUTE_NAME] attribute has a
         * value of [ValueExample.javaExpression].
         */
        private val annotatedWithAnnotationWithoutDefaults by
            lazy(LazyThreadSafetyMode.NONE) {
                val annotationWithoutDefaults =
                    testClassCreator.generateAnnotationClass(
                        valueExample,
                        "AnnotationWithoutDefaults",
                        withDefaults = false,
                    )

                testClassCreator.generateAnnotatedTestClass(
                    valueExample,
                    "AnnotationTestClass",
                    annotationWithoutDefaults
                )
            }

        /**
         * A class called `FieldTestClass_...` for [valueExample] whose [FIELD_NAME] has a value of
         * [ValueExample.javaExpression].
         */
        private val field by
            lazy(LazyThreadSafetyMode.NONE) {
                testClassCreator.generateFieldTestClass(
                    valueExample,
                    "FieldTestClass",
                )
            }
    }

    /**
     * A test class to run against.
     *
     * @param className the name of the test class (in the `test.pkg` package).
     * @param testFileSet the complete set of [TestFile]s needed to compile the test class.
     */
    data class TestClass(val className: String, val testFileSet: Set<TestFile>) {
        /** Return a new instance of this adding [dependency] to [testFileSet]. */
        fun dependsOn(dependency: TestFile) = copy(testFileSet = testFileSet + dependency)

        /** Return a new instance of this adding [dependency] to [testFileSet]. */
        fun dependsOn(dependency: TestClass) =
            copy(testFileSet = testFileSet + dependency.testFileSet)
    }

    companion object {
        /** Filter the parameters. */
        @JvmStatic
        @ParameterFilter
        fun parameterFilter(
            config: CodebaseCreatorConfig<ModelSuiteRunner>,
            producer: CodebaseProducer,
            testCase: TestCase,
        ): Boolean {
            val inputFormat = config.inputFormat

            // Ignore any tests that are not valid for the InputFormat.
            return inputFormat in testCase.valueExample.validForInputFormats &&
                // Supports all input formats but only Java can produce jars.
                (inputFormat == InputFormat.JAVA || producer.kind != ProducerKind.JAR)
        }

        /** The set of [TestCase]s to run in each [CodebaseProducer] in [codebaseProducers]. */
        private val testCases = run {
            // Verify that all the ValueExamples have distinct names.
            val duplicates =
                valueExamples
                    .groupingBy { it.name }
                    .eachCount()
                    .filter { it.value > 1 }
                    .map { it.key }
            if (duplicates.isNotEmpty()) {
                error("Duplicate value examples: $duplicates")
            }

            // Construct [TestCase]s from [ValueExample].
            valueExamples.map { TestCase(it) }
        }

        /** The list of [CodebaseProducer]s for which all the [testCases] will be run. */
        private val codebaseProducers =
            listOf(
                JarCodebaseProducer(),
                SourceCodebaseProducer,
            )

        internal fun testCasesForValueUseSite(legacyValueUseSite: LegacyValueUseSite) =
            testCases.filter {
                // Only select TestCase's whose ValueExample is suitable for the specified
                // LegacyValueUseSite.
                legacyValueUseSite in it.valueExample.suitableFor
            }

        /** Create cross product of [codebaseProducers] and [testCases]. */
        internal fun testCasesForCodebaseProducers(
            testCases: List<TestCase>,
        ) =
            codebaseProducers.flatMap { codebaseProducer ->
                testCases.map { testCase -> arrayOf(codebaseProducer, testCase) }
            }
    }

    /**
     * Produce a [Codebase] from [TestCase.testClassFor]'s [TestClass.testFileSet] and then run
     * [TestCase] against it.
     */
    private object SourceCodebaseProducer : CodebaseProducer(ProducerKind.SOURCE) {
        override fun BaseCommonParameterizedValueTest.runCodebaseProducerTest(
            testFileCache: TestFileCache,
            testCase: TestCase,
            test: TestCaseContext.() -> Unit
        ) {
            val testClass =
                testCase.testClassFor(inputFormat, legacyValueUseSite)
                    ?: error("No $inputFormat class provided for $legacyValueUseSite")
            // Cache the sources so that they can be reused.
            val sources = testClass.testFileSet.map { it.cacheIn(testFileCache) }

            // Run the test on the sources.
            runSourceCodebaseTest(inputSet(sources.toList())) {
                runTestCase(testCase, legacyValueUseSite, test)
            }
        }
    }

    /** Produce a [Codebase] from [testJarFile]. */
    private class JarCodebaseProducer : CodebaseProducer(ProducerKind.JAR) {
        override fun BaseCommonParameterizedValueTest.runCodebaseProducerTest(
            testFileCache: TestFileCache,
            testCase: TestCase,
            test: TestCaseContext.() -> Unit
        ) {
            // Cache the jar file so that it will be reused.
            val cachedJarFile = testJarFile.cacheIn(testFileCache)

            runSourceCodebaseTest(
                // Unused class, present simply to force the test to be run against models that
                // support Java.
                java(
                    """
                        package test.pkg;
                        public class Foo {}
                    """
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(cachedJarFile.createFile(temporaryFolder.root))
                    ),
            ) {
                runTestCase(testCase, legacyValueUseSite, test)
            }
        }
    }

    /**
     * Base class for the subclass companion objects.
     *
     * Makes it easy to share behavior between them.
     */
    open class BaseCompanion(private val legacyValueUseSite: LegacyValueUseSite) {
        /** The list of all [legacyValueUseSite] test cases. */
        private val valueUseTestCases = testCasesForValueUseSite(legacyValueUseSite)

        /** The list of parameters for this test class. */
        val testParameters = testCasesForCodebaseProducers(valueUseTestCases)

        /** Jar file built from all java source files in [testParameters]. */
        val testJarFile = produceJarTestFile(valueUseTestCases)

        /** Produce a jar from all the distinct [TestFile]s used by [testCases]. */
        private fun produceJarTestFile(testCases: List<TestCase>): TestFile {
            // The jar includes all the distinct [TestFile]s used by [testCases].
            val sourcesForJar = buildSet {
                for (testCase in testCases) {
                    testCase.testClassFor(InputFormat.JAVA, legacyValueUseSite)?.testFileSet?.let {
                        addAll(it)
                    }
                }
            }

            // Jar file containing compiled versions of [sourcesForJar].
            return jarFromSources(
                "binary-class.jar",
                *sourcesForJar.toTypedArray(),
            )
        }
    }

    /** Context within which test cases will be run. */
    class TestCaseContext(
        delegate: CodebaseContext,
        private val testCase: TestCase,
        val producerKind: ProducerKind,
        private val legacyValueUseSite: LegacyValueUseSite,
    ) : CodebaseContext by delegate {
        /** Get the [ClassItem] to be tested from this [Codebase]. */
        val testClassItem
            get(): ClassItem {
                return testCase.testClassFor(inputFormat, legacyValueUseSite)?.className?.let {
                    className ->
                    val qualifiedName = "test.pkg.$className"
                    codebase.resolveClass(qualifiedName)
                        ?: error("Expected $qualifiedName to be defined")
                } ?: error("No $inputFormat class provided for $legacyValueUseSite")
            }
    }

    /** Run a test on the [Codebase] produced by [codebaseProducer]. */
    private fun runTestOnCodebase(function: TestCaseContext.() -> Unit) {
        val thisClass = this
        with(codebaseProducer) {
            thisClass.runCodebaseProducerTest(testFileCache, testCase, function)
        }
    }

    /**
     * Run an expectation test.
     *
     * Invokes [actualGetter] to get the actual value to compare and compares it against the
     * expected value retrieved from [expectation].
     *
     * @param expectation the [Expectation] that will provide the expected value.
     * @param actualGetter lambda to get the actual value to test.
     */
    private fun <T> runExpectationTest(
        expectation: Expectation<T>,
        actualGetter: TestCaseContext.() -> T,
    ) {
        runTestOnCodebase {
            // If a test is not valid for the expected value then it is not valid for any other
            // tests from the same example so check to make sure that it is valid, skipping if it is
            // not.
            skipTestIfNotValidForExpectedValue(
                testCase.valueExample.expectedValue.expectationFor(producerKind, legacyValueUseSite)
            )

            // Get the actual value.
            val actual = actualGetter()

            // Get the expected value.
            val expected = expectation.expectationFor(producerKind, legacyValueUseSite)

            // Compare the two.
            if (expected is Array<*> && actual is Array<*>) {
                assertArrayEquals(expected, actual)
            } else {
                assertEquals(expected, actual)
            }
        }
    }

    /**
     * Check the [ValueExample.expectedLegacySource] against the [String] returned by
     * [LegacyValueUseSite.legacySourceGetter].
     */
    protected fun checkLegacySource() {
        val expectedLegacySource = testCase.valueExample.expectedLegacySourceFor(inputFormat)
        val legacySourceGetter =
            legacyValueUseSite.legacySourceGetter
                ?: error(
                    "LegacyValueUseSite.$legacyValueUseSite does not provide a legacySourceGetter"
                )

        runExpectationTest(expectedLegacySource, legacySourceGetter)
    }

    /**
     * Check the [ValueExample.expectedValue] against the [Value] returned by [actualValueGetter].
     */
    protected fun checkExpectedValue(
        actualValueGetter: TestCaseContext.() -> Value?,
    ) {
        runTestOnCodebase {
            // Get the expected value.
            val expectation = testCase.valueExample.expectedValue
            expectation.expectationFor(producerKind, legacyValueUseSite).runValueTest { expected ->
                // Make sure the expected value is valid for this test.
                skipTestIfNotValidForExpectedValue(expected)

                // Filter the expected value for fields. FieldItem.constantValue can only be a
                // constant value, i.e. a primitive or String literal. However, the source can be
                // given a non-constant value, e.g. an array, field reference, etc. A reference to
                // a constant field will be replaced with its constant value but otherwise the field
                // will have a null expectation. This ensures that the expectation is correct.
                val filteredExpected =
                    if (expected != null && legacyValueUseSite.valueUseSite == ValueUseSite.FIELD) {
                        // Fields only use constant literal values.
                        expected.asLiteralValue()
                    } else expected

                // Get the actual value.
                val actual = actualValueGetter()

                // Strictly compare the Values to ensure that where necessary they have included any
                // information needed to generate correct legacy string representations.
                assertValuesAreStrictlyEqual(filteredExpected, actual)

                // Fields are equal if they reference the same qualified class name and field name.
                // However, for testing purposes this needs to verify that their constant values
                // also match.
                if (filteredExpected is FieldReferenceValue) {
                    assertTrue(
                        actual is FieldReferenceValue,
                        message = "value is not a field it is ${actual?.javaClass}"
                    )
                    assertValuesAreStrictlyEqual(
                        filteredExpected.asLiteralValue(),
                        actual.asLiteralValue(),
                        message = "field constant: "
                    )
                }
            }
        }
    }

    /**
     * Check to make sure that the test is valid for the combination of [expectedValue],
     * [inputFormat] and [legacyValueUseSite].
     *
     * These tests cannot be filtered out by the [ParameterFilter] as that only has access to the
     * first two.
     */
    private fun skipTestIfNotValidForExpectedValue(expectedValue: Value?) {
        // Null expected values are ok.
        if (expectedValue == null) return

        // Only fields have special restrictions.
        if (legacyValueUseSite.valueUseSite != ValueUseSite.FIELD) return

        // Signature file fields only use literal values in their initializers. So, using any other
        // kind is invalid.
        val kind = expectedValue.kind
        if (inputFormat == InputFormat.SIGNATURE && kind !in ValueKind.LITERAL_KINDS) {
            throw AssumptionViolatedException(
                "Using value `$expectedValue` as an initializer for a field in a signature file is invalid; ignoring"
            )
        }
    }
}

/** Interface for objects that create [TestClass] instances. */
interface TestClassCreator {
    companion object {
        const val ATTRIBUTE_NAME = "attr"
        const val FIELD_NAME = "FIELD"
    }

    /**
     * Create an annotation [TestClass] for [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param withDefaults true if defaults should be added, false otherwise.
     */
    fun generateAnnotationClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        withDefaults: Boolean
    ): TestClass

    /**
     * Create a normal [TestClass] annotated with [annotationTestClass].
     *
     * The annotation uses the appropriate values from this [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param annotationTestClass the annotation [TestClass] to annotate the class with.
     */
    fun generateAnnotatedTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        annotationTestClass: TestClass,
    ): TestClass

    /**
     * Create a [TestClass] containing a "constant" field for this [[valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     */
    fun generateFieldTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
    ): TestClass

    /** Create a [TestClass] for [className] containing this [TestFile]. */
    fun TestFile.asTestClass(className: String): TestClass {
        return TestClass(className, setOf(this))
    }
}

/** Create java [TestClass]es for use by [BaseCommonParameterizedValueTest]. */
object JavaTestClassCreator : TestClassCreator {
    private val testConstantsClass =
        java(
            """
                package test.pkg;
                public interface Constants {
                    String STRING_CONSTANT = "constant";
                    int INT_CONSTANT = 37;
                    long LONG_CONSTANT = 9L;
                }
            """
        )

    private val testGenericClass =
        java(
            """
                package test.pkg;
                public interface GenericClass<T> {
                    String STRING_CONSTANT = "constant";
                }
            """
        )

    private val testEnumClass =
        java(
            """
                package test.pkg;
                public enum TestEnum {
                    DEFAULT,
                    VALUE1,
                }
            """
        )

    private val otherAnnotationClass =
        java(
                """
                    package test.pkg;

                    public @interface OtherAnnotation {
                        Class<?> classType() default void.class;
                        TestEnum enumType() default TestEnum.DEFAULT;
                        int intType() default -1;
                        String stringType() default "default";
                        String[] stringArrayType() default {};
                    }
                """
            )
            .asTestClass("OtherAnnotation")
            .dependsOn(testEnumClass)

    private val singleValueAnnotationClass =
        java(
                """
                    package test.pkg;
                    
                    public @interface SingleValueAnnotation {
                        String value();
                    }
                """
            )
            .asTestClass("SingleValueAnnotation")

    /** Append all the imports provided by this list to [buffer]. */
    private fun appendImportsTo(valueExample: ValueExample, buffer: StringBuilder) {
        for (javaImport in valueExample.javaImports) {
            buffer.append("import ")
            buffer.append(javaImport)
            buffer.append(";\n")
        }
    }

    /**
     * Create an annotation [TestClass] for [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param withDefaults true if defaults should be added, false otherwise.
     */
    override fun generateAnnotationClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        withDefaults: Boolean
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return java(
                buildString {
                    append("package test.pkg;\n")
                    appendImportsTo(valueExample, this)
                    append("public @interface $className {\n")
                    append("    ")
                    append(valueExample.javaType)
                    append(" ")
                    append(ATTRIBUTE_NAME)
                    append("()")
                    if (withDefaults) {
                        append(" default ")
                        append(valueExample.javaExpression)
                    }
                    append(";\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(singleValueAnnotationClass)
            .dependsOn(testConstantsClass)
            .dependsOn(testGenericClass)
    }

    /**
     * Create a normal [TestClass] annotated with [annotationTestClass].
     *
     * The annotation uses the appropriate values from this [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param annotationTestClass the annotation [TestClass] to annotate the class with.
     */
    override fun generateAnnotatedTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        annotationTestClass: TestClass,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return java(
                buildString {
                    append("package test.pkg;\n")
                    appendImportsTo(valueExample, this)
                    append("@")
                    append(annotationTestClass.className)
                    append("(")
                    append(ATTRIBUTE_NAME)
                    append(" = ")
                    append(valueExample.javaExpression)
                    append(")\n")
                    append("public class $className {}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(annotationTestClass)
    }

    /**
     * Create a [TestClass] containing a "constant" field for this [[valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     */
    override fun generateFieldTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return java(
                buildString {
                    append("package test.pkg;\n")
                    appendImportsTo(valueExample, this)
                    append("public class $className {\n")
                    append("    public static final ")
                    append(valueExample.javaType)
                    append(" ")
                    append(FIELD_NAME)
                    append(" = ")
                    append(valueExample.javaExpression)
                    append(";\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(singleValueAnnotationClass)
            .dependsOn(testConstantsClass)
            .dependsOn(testGenericClass)
    }
}

/** Create kotlin [TestClass]es for use by [BaseCommonParameterizedValueTest]. */
object KotlinTestClassCreator : TestClassCreator {
    private val testConstantsClass =
        kotlin(
            """
                package test.pkg
                object Constants {
                    const val STRING_CONSTANT = "constant"
                    const val INT_CONSTANT = 37
                    const val LONG_CONSTANT = 9L
                }
            """
        )

    private val testGenericClass =
        kotlin(
            """
                package test.pkg
                interface GenericClass<T> {
                    companion object {
                        const val STRING_CONSTANT = "constant"
                    }
                }
            """
        )

    private val testEnumClass =
        kotlin(
            """
                package test.pkg
                enum class TestEnum {
                    DEFAULT,
                    VALUE1,
                }
            """
        )

    private val otherAnnotationClass =
        kotlin(
                """
                    package test.pkg

                    import kotlin.reflect.KClass

                    annotation class OtherAnnotation(
                        val classType: KClass<*> = Unit::class,
                        val enumType: TestEnum = TestEnum.DEFAULT,
                        val intType: Int = -1,
                        val stringType: String = "default",
                        val stringArrayType: Array<String> = [],
                    )
                """
            )
            .asTestClass("OtherAnnotation")
            .dependsOn(testEnumClass)

    private val singleValueAnnotationClass =
        kotlin(
                """
                    package test.pkg

                    annotation class SingleValueAnnotation(
                        val value: String,
                    )
                """
            )
            .asTestClass("SingleValueAnnotation")

    /** Append all the imports provided by this list to [buffer]. */
    private fun appendImportsTo(valueExample: ValueExample, buffer: StringBuilder) {
        for (kotlinImport in valueExample.kotlinImports) {
            buffer.append("import ")
            buffer.append(kotlinImport)
            buffer.append("\n")
        }
    }

    /**
     * Create an annotation [TestClass] for [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param withDefaults true if defaults should be added, false otherwise.
     */
    override fun generateAnnotationClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        withDefaults: Boolean
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return kotlin(
                buildString {
                    append("package test.pkg\n")
                    appendImportsTo(valueExample, this)
                    append("annotation class $className(\n")
                    append("    val ")
                    append(ATTRIBUTE_NAME)
                    append(": ")
                    append(valueExample.kotlinTypeForAnnotation)
                    if (withDefaults) {
                        append(" = ")
                        // Kotlin nested annotations do not use an @ prefix so remove it.
                        append(valueExample.kotlinExpressionForAnnotation.removePrefix("@"))
                    }
                    append("\n")
                    append(")\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(singleValueAnnotationClass)
            .dependsOn(testConstantsClass)
            .dependsOn(testGenericClass)
    }

    /**
     * Create a normal [TestClass] annotated with [annotationTestClass].
     *
     * The annotation uses the appropriate values from this [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param annotationTestClass the annotation [TestClass] to annotate the class with.
     */
    override fun generateAnnotatedTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        annotationTestClass: TestClass,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return kotlin(
                buildString {
                    append("package test.pkg\n")
                    appendImportsTo(valueExample, this)
                    append("@")
                    append(annotationTestClass.className)
                    append("(")
                    append(ATTRIBUTE_NAME)
                    append(" = ")
                    // Kotlin nested annotations do not use an @ prefix so remove it.
                    append(valueExample.kotlinExpressionForAnnotation.removePrefix("@"))
                    append(")\n")
                    append("class $className {}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(annotationTestClass)
    }

    /**
     * Create a [TestClass] containing a "constant" field for this [[valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     */
    override fun generateFieldTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return kotlin(
                buildString {
                    append("package test.pkg\n")
                    appendImportsTo(valueExample, this)
                    append("class $className {\n")
                    append("    companion object {\n")
                    append("        const val ")
                    append(FIELD_NAME)
                    append(": ")
                    append(valueExample.kotlinType)
                    append(" = ")
                    append(valueExample.kotlinExpression)
                    append("\n")
                    append("    }\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(singleValueAnnotationClass)
            .dependsOn(testConstantsClass)
            .dependsOn(testGenericClass)
    }
}

/** Creates signature [TestClass]es for use by [BaseCommonParameterizedValueTest]. */
object SignatureTestClassCreator : TestClassCreator {
    private val testConstantsClass =
        signature(
            "constants.txt",
            """
                // Signature format: 2.0
                package test.pkg {
                  public interface Constants {
                    field public static final String STRING_CONSTANT = "constant";
                    field public static final int INT_CONSTANT = 37;
                    field public static final long LONG_CONSTANT = 9L;
                  }
                  public interface GenericClass<T> {
                    field public static final String STRING_CONSTANT = "constant";
                  }
                }
            """
        )

    private val testEnumClass =
        signature(
            "test-enum.txt",
            """
                // Signature format: 2.0
                package test.pkg {
                  public enum TestEnum {
                    enum_constant public static final test.pkg.TestEnum DEFAULT;
                    enum_constant public static final test.pkg.TestEnum VALUE1;
                  }
                }
            """
        )

    private val otherAnnotationClass =
        signature(
                "other-annotation.txt",
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public @interface OtherAnnotation {
                        method public Class<?> classType() default void.class;
                        method public test.pkg.TestEnum enumType() default test.pkg.TestEnum.DEFAULT;
                        method public int intType() default -1;
                        method public String stringType() default "default";
                        method public String[] stringArrayType() default {};
                      }
                    }
                """
            )
            .asTestClass("OtherAnnotation")
            .dependsOn(testEnumClass)

    /**
     * Create an annotation [TestClass] for [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param withDefaults true if defaults should be added, false otherwise.
     */
    override fun generateAnnotationClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        withDefaults: Boolean
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return signature(
                "$className.txt",
                buildString {
                    append("// Signature format: 2.0\n")
                    append("package test.pkg {\n")
                    append("  public @interface $className {\n")
                    append("    method public abstract ")
                    append(valueExample.signatureType)
                    append(" ")
                    append(ATTRIBUTE_NAME)
                    append("()")
                    if (withDefaults) {
                        append(" default ")
                        append(valueExample.signatureExpression)
                    }
                    append(";\n")
                    append("  }\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(testConstantsClass)
    }

    /**
     * Create a normal [TestClass] annotated with [annotationTestClass].
     *
     * The annotation uses the appropriate values from this [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     * @param annotationTestClass the annotation [TestClass] to annotate the class with.
     */
    override fun generateAnnotatedTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
        annotationTestClass: TestClass,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return signature(
                "$className.txt",
                buildString {
                    append("// Signature format: 2.0\n")
                    append("package test.pkg {\n")
                    append("  @test.pkg.")
                    append(annotationTestClass.className)
                    append("(")
                    append(ATTRIBUTE_NAME)
                    append(" = ")
                    append(valueExample.signatureExpression)
                    append(") ")
                    append("public class $className {\n")
                    append("  }\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(annotationTestClass)
    }

    /**
     * Create a [TestClass] containing a "constant" field for this [valueExample].
     *
     * @param valueExample the [ValueExample] for which this is being created.
     * @param classNamePrefix the prefix of the class.
     */
    override fun generateFieldTestClass(
        valueExample: ValueExample,
        classNamePrefix: String,
    ): TestClass {
        val className = "${classNamePrefix}_${valueExample.classSuffix}"
        return signature(
                "$className.txt",
                buildString {
                    append("// Signature format: 2.0\n")
                    append("package test.pkg {\n")
                    append("  public class $className {\n")
                    append("    field public static final ")
                    append(valueExample.signatureType)
                    append(" ")
                    append(FIELD_NAME)
                    append(" = ")
                    append(valueExample.signatureExpression)
                    append(";\n")
                    append("  }\n")
                    append("}\n")
                }
            )
            .asTestClass(className)
            .dependsOn(otherAnnotationClass)
            .dependsOn(testConstantsClass)
    }
}
