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

package com.android.tools.metalava.model.testsuite

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.source.SourceCodebase
import java.util.ServiceLoader
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/**
 * Base class for tests that verify the behavior of model implementations.
 *
 * This is parameterized by the runners as even though the tests are run in different projects the
 * test results are collated and reported together. Having the runner in the test name makes it
 * easier to differentiate them.
 *
 * Note: In the top-level test report produced by Gradle it appears to just display whichever test
 * ran last. However, the test reports in the model implementation projects do list each run
 * separately. If this is an issue then the [ModelSuiteRunner] implementations could all be moved
 * into the same project and run tests against them all at the same time.
 */
abstract class BaseModelTest(private val runner: ModelSuiteRunner) {

    @get:Rule val temporaryFolder = TemporaryFolder()

    @get:Rule val baselineTestRule: TestRule = BaselineTestRule(runner)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun runners(): Iterable<ModelSuiteRunner> {
            val loader = ServiceLoader.load(ModelSuiteRunner::class.java)
            val list = loader.toList()
            if (list.isEmpty()) {
                fail("No runners found")
            }
            return list
        }
    }

    /**
     * Create a [Codebase] from one of the supplied [signature] or [source] files and then run a
     * test on that [Codebase].
     *
     * This must be called with [signature] and [source] contents that are equivalent so that the
     * test can have the same behavior on models that consume the different formats. Subclasses of
     * this must implement this method consuming at least one of them to create a [Codebase] on
     * which the test is run.
     */
    fun createCodebaseAndRun(
        signature: String,
        source: TestFile,
        test: (Codebase) -> Unit,
    ) {
        val tempDir = temporaryFolder.newFolder()
        runner.createCodebaseAndRun(tempDir, signature, source, test)
    }

    /**
     * Create a [SourceCodebase] from one of the supplied [signature] or [source] files and then run
     * a test on that [SourceCodebase].
     */
    fun runSourceCodebaseTest(
        source: TestFile,
        test: (SourceCodebase) -> Unit,
    ) {
        val tempDir = temporaryFolder.newFolder()
        runner.createCodebaseAndRun(tempDir, null, source) { test(it as SourceCodebase) }
    }

    /** Get the class from the [Codebase], failing if it does not exist. */
    fun Codebase.assertClass(qualifiedName: String): ClassItem {
        val classItem = findClass(qualifiedName)
        assertNotNull(classItem) { "Expected $qualifiedName to be defined" }
        return classItem
    }

    /** Get the package from the [Codebase], failing if it does not exist. */
    fun Codebase.assertPackage(pkgName: String): PackageItem {
        val packageItem = findPackage(pkgName)
        assertNotNull(packageItem) { "Expected $pkgName to be defined" }
        return packageItem
    }

    /** Get the field from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertField(fieldName: String): FieldItem {
        val fieldItem = findField(fieldName)
        assertNotNull(fieldItem) { "Expected $fieldName to be defined" }
        return fieldItem
    }

    /** Get the method from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertMethod(methodName: String, parameters: String): MethodItem {
        val methodItem = findMethod(methodName, parameters)
        assertNotNull(methodItem) { "Expected $methodName($parameters) to be defined" }
        return methodItem
    }

    /** Get the constructor from the [ClassItem], failing if it does not exist. */
    fun ClassItem.assertConstructor(parameters: String): ConstructorItem {
        val methodItem = findMethod(simpleName(), parameters)
        assertNotNull(methodItem) { "Expected ${simpleName()}($parameters) to be defined" }
        return assertIs(methodItem)
    }
}

private const val GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE =
    "`scripts/refresh-testsuite-baselines.sh` to update the baseline"

/** A JUnit [TestRule] that uses information from the [ModelTestSuiteBaseline] to ignore tests. */
private class BaselineTestRule(private val runner: ModelSuiteRunner) : TestRule {

    /**
     * The [ModelTestSuiteBaseline] that indicates whether the tests are expected to fail or not.
     */
    private val baseline = ModelTestSuiteBaseline.fromResource

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val expectedFailure =
                    baseline.isExpectedFailure(description.className, description.methodName)
                try {
                    // Run the test even if it is expected to fail as a change that fixes one test
                    // may fix more. Instead, this will just discard any failure.
                    base.evaluate()
                    if (expectedFailure) {
                        // If a test that was expected to fail passes then updating the baseline
                        // will remove that test from the expected test failures.
                        System.err.println(
                            "Test was expected to fail but passed, please run $GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE"
                        )
                    }
                } catch (e: Throwable) {
                    if (expectedFailure) {
                        // If this was expected to fail then throw an AssumptionViolatedException
                        // so it is not treated as either a pass or fail.
                        throw AssumptionViolatedException(
                            "Test skipped since it is listed in the baseline file for $runner"
                        )
                    } else {
                        // Inform the developer on how to ignore this failing test.
                        System.err.println(
                            "Failing tests can be ignored by running $GRADLEW_UPDATE_MODEL_TEST_SUITE_BASELINE"
                        )

                        // Rethrow the error
                        throw e
                    }
                }
            }
        }
    }
}
