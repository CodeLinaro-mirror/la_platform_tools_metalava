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

package com.android.tools.metalava.testing

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/**
 * Marks a function (or constructor) that is an entry point into some test infrastructure.
 *
 * The purpose of this is to allow an [EntryPointCallerTracker] to scan a stack trace, ignore test
 * infrastructure methods, and find the caller into the test infrastructure as that is probably the
 * most useful place to go to in the event of a test failure.
 *
 * Functions (or constructors) that are annotated with this must not be private as Kotlin will drop
 * the annotation. It will also mangle the name but that is not an issue as the mangled name appears
 * in the stack traces anyway.
 *
 * See [EntryPointCallerRule].
 */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION) annotation class EntryPoint

/**
 * Marks a function (or constructor) that is an exit point from a test infrastructure.
 *
 * If some test infrastructure code calls a lambda provided by the test case then the function (or
 * constructor) that calls the lambda should be annotated with this. That will ensure that calls
 * from the lambda are treated as calls into the test infrastructure and not part of the test
 * infrastructure itself.
 *
 * A function (or constructor) cannot be both an entry and exit point. If that is necessary, then it
 * must be split into two, with the part that calls the lambda being annotated with this.
 *
 * See [EntryPointCallerRule].
 */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION) annotation class ExitPoint

/**
 * Records the stack trace of this object's creation and uses it to rewrite the stack trace of a
 * [Throwable] in the event of a test failure.
 *
 * The purpose of this is to make the stack trace for a test failure in a parameterized test more
 * useful by pointing to the location where the test data was created rather than the location of
 * the failing assertion which will be the same for all tests.
 *
 * This would typically be used in a parameterized test where the same test method and assertion is
 * run for multiple different set of arguments. In the event of a failing test, the location of the
 * failing assertion is probably less important than the location of the code that created the
 * particular set of arguments being tested.
 *
 * That can be achieved by creating an instance of this in the set of arguments being tested and
 * running any test code within [runTest]. If that test code fails with a [Throwable] then it will
 * replace the stack trace of that exception with the stack trace for the creation of this instance.
 *
 * Even then there will be many stack frames, and it would not be clear which one was the important
 * one and which was not. To address that, entry points into the test infrastructure that could lead
 * to the creation of this must be marked with @[EntryPoint]. This will then make sure that the
 * stack frame for the caller of one of those methods is at the top of the stack trace.
 *
 * e.g.
 *
 * ```
 *     ... val testCase: TestCase
 *
 *     class TestCase(...) {
 *         val entryPointCallerTracker = EntryPointCallerTracker()
 *     }
 *
 *     @EntryPoint
 *     fun addTestCase(...) {
 *         testCases.add(TestCase(...));
 *     }
 *
 *     val testCases = buildTestCases {
 *         addTestCase(...broken...)
 *     }
 *
 *     @Test
 *     fun `run test`() {
 *         testCase.entryPointCallerTracker.runTest {
 *             assert...
 *         }
 *     }
 * ```
 *
 * When the assertion fails the top stack frame will reference `addTestCase(...broken...)`.
 */
class EntryPointCallerTracker @EntryPoint constructor() {
    /**
     * Create a [Throwable] to preserve the stack trace at the point this was created but defer all
     * the rest of the work until a [Throwable] is thrown in [runTest].
     */
    private val throwable = Throwable()

    /**
     * Run [body] and if it throws a [Throwable] and it has no init cause then replace its stack
     * trace with a stripped stack trace such that the top frame is the caller into the entry point
     * that caused this to be created.
     *
     * It only modifies the stack trace of a [Throwable] that has no init cause as the init cause is
     * used to preserve the original stack trace from the [Throwable].
     */
    fun runTest(body: () -> Unit) {
        try {
            body()
        } catch (e: Throwable) {
            // Only modify the stack trace if there was no cause provided as otherwise there is no
            // place to attach the original stack trace. That means modifying the stack trace would
            // be lossy and the convenience of a fast way to navigate to the test data creation is
            // not worth losing information that may be vital to debugging the problem.
            if (e.cause == null) {
                // Preserve the original stack trace in the init cause just in case it is needed for
                // debugging.
                val initCause =
                    Exception("Original stack trace").also { it.stackTrace = e.stackTrace }
                e.initCause(initCause)

                // Set the stack trace to point to the caller of an entry point into the test
                // infrastructure.
                e.stackTrace = throwable.entryPointStack
            }

            throw e
        }
    }

    /** Get this [Throwable]'s [stackTrace], starting from the caller into the entry point. */
    private val Throwable.entryPointStack: Array<StackTraceElement>?
        get() {
            val elements = stackTrace

            var entryPointCallerIndex = -1
            for ((index, element) in elements.withIndex()) {
                val elementType = element.elementType()
                if (elementType == StackElementType.ENTRY) entryPointCallerIndex = index
                else if (elementType == StackElementType.EXIT) break
            }
            return if (entryPointCallerIndex == -1) {
                elements
            } else {
                elements.sliceArray((entryPointCallerIndex + 1)..<elements.size)
            }
        }

    /** The type of [StackTraceElement]. */
    private enum class StackElementType {
        ENTRY,
        EXIT,
    }

    /**
     * Get the type of this, depends on the annotations associated with the method/constructor to
     * which this refers.
     *
     * If it is not annotated with either [EntryPoint] or [ExitPoint] then this returns `null`.
     */
    private fun StackTraceElement.elementType(): StackElementType? {
        if (!className.startsWith("com.android.tools.metalava.")) return null
        val javaClass =
            try {
                Class.forName(className)
            } catch (e: Exception) {
                return null
            }

        // There could be overloads of the method as the StackTraceElement does not provide enough
        // information to differentiate between them. This just picks the first one found which is
        // good enough.
        val method = javaClass.findMethodOrConstructor(methodName) ?: return null
        val isEntryPoint = method.getAnnotation(EntryPoint::class.java) != null
        val isExitPoint = method.getAnnotation(ExitPoint::class.java) != null
        if (isEntryPoint && isExitPoint) {
            error("$method has both @EntryPoint and @ExitPoint, pick one")
        }
        if (isEntryPoint) return StackElementType.ENTRY
        if (isExitPoint) return StackElementType.EXIT
        return null
    }

    /**
     * Find a method or constructor (if [name] is `<init>`) on this class.
     *
     * Will return the first one found.
     */
    private fun Class<*>.findMethodOrConstructor(name: String) =
        if (name == "<init>") {
            constructors.firstOrNull()
        } else {
            methods.find { it.name == name }
        }
}

/**
 * A [TestRule] that is intended for use with parameterized tests to make it easier to debug test
 * failures by rewriting the stack trace to the place where the test data was created.
 *
 * [Parameterized] tests use the same test code to run multiple tests based on parameters injected
 * into the tests. The problem with that is that when there is a test failure it is most likely
 * caused by the test data but the stack trace always points to the location where the test data is
 * used not where the test data was created. This addresses that by catching and rewriting the stack
 * trace of [Throwable]s thrown by a test method to refer to where the test data was created.
 *
 * It is used as follows:
 * 1. Create an [EntryPointCallerTracker] instance and store it in the test data when it is created.
 *    That records the stack trace by creating an exception.
 * 2. Add this rule, passing in a lambda that will return the [EntryPointCallerTracker] from the
 *    test data that is injected into the test class instance.
 *
 * That will rewrite the stack trace for any [Throwable] thrown to the one used to create the
 * [EntryPointCallerTracker]. However, before it does that it will filter out any elements from the
 * top of [EntryPointCallerTracker]'s stack trace that are part of the test infrastructure, i.e. not
 * related to the creation of a specific instance of test data. The intent is to make sure that the
 * top of the stack is most relevant to the current test data being tested.
 *
 * That filtering is controlled via the [EntryPoint] and [ExitPoint] annotations on the methods or
 * constructors in the stack trace. The basic behavior is that the top of the stack will refer to
 * the first caller to an [EntryPoint] found while searching from the start position upwards to the
 * top of the stack. The start position will be either the first [ExitPoint] found while searching
 * from the top of the stack downwards, or the bottom of the stack if no [ExitPoint] is found.
 *
 * e.g. The [EntryPointCallerTracker]'s constructor is not important, so it is annotated with
 * [EntryPoint]. So, by default the top of the stack will be the location that called the
 * [EntryPointCallerTracker] constructor not the constructor itself.
 *
 * In practice, the [EntryPointCallerTracker] is most likely created something like this:
 * ```
 *     data class TestData(...) {
 *       internal val entryPointCallerTracker = EntryPointCallerTracker()
 *     }
 * ```
 *
 * In that case the top of the stack will be the `TestData` constructor `TestData.<init>()` which is
 * not relevant when dealing with an issue with a specific `TestData` instance. To remove that it
 * can be annotated with [EntryPoint], e.g.
 *
 * ```
 *     data class @EntryPoint constructor TestData(...) {
 *       ...
 *     }
 * ```
 *
 * That way the top of the stack will now point to the code that created `TestData`.
 *
 * If the creation of the test data is more complex and does not just involve calling
 * `TestData(...)` directly then the methods/constructors that provide support can be annotated as
 * well. It is not necessary to annotate every such method/constructor, only those which are called
 * directly to create a specific instance of `TestData`.
 *
 * If the test infrastructure uses builder methods, i.e. methods that are supplied with a lambda
 * that can create test data then the methods called by the lambda needs to be annotated with
 * [EntryPoint] but the builder method does not. Unless, the builder method also creates test data
 * directly too.
 *
 * e.g. in the following `builder` method does not need annotation with [EntryPoint] but the
 * `Builder.addTest(...)` method does.
 *
 * ```
 *     fun builder(body: Builder.() -> Unit): List<TestData> {
 *       val builder = Builder()
 *       builder.body()
 *       return builder.build()
 *     }
 *
 *     class Builder {
 *       @EntryPoint
 *       fun addTest(name: String, data: Int) {...}
 *       fun build(): List<TestData> {...}
 *     }
 *
 *     val testCases = builder {
 *        ...
 *        addTest("fred", 1)
 *        addTest("wilma", 2)
 *        addTest("barney", 3)
 *        ...
 *     }
 * ```
 *
 * A test failure with `"fred"` data would be reported on the `addTest("fred", 1)` line, while a
 * test failure with `"wilma"` data would be reported on the `addTest("wilma", 2)` line, and so on.
 *
 * However, if the `builder` method was changed to add its own test data like the following then it
 * does need annotation with [EntryPoint]:
 * ```
 *     fun builder(defaultData: Int, body: Builder.() -> Unit): List<TestData> {
 *       val builder = Builder()
 *       builder.addTest("default", defaultData)
 *       builder.body()
 *       return builder.build()
 *     }
 *
 *     val testCases = builder(defaultData = 4) {
 *        ...
 *        addTest("fred", 1)
 *        addTest("wilma", 2)
 *        addTest("barney", 3)
 *        ...
 *     }
 * ```
 *
 * Without it an error with the `"default"` data would be reported on the
 * `builder.addTest("default", defaultData)` line, but it would be more helpful if it was reported
 * on the `builder(defaultData = 4) {` line.
 *
 * Unfortunately, just annotating it with `[EntryPoint]` would mean that errors in the `"fred"` data
 * would also be reported on the `builder(defaultData = 4) {` as that will be the first [EntryPoint]
 * found while searching from the bottom of the stack as the unfiltered stack for creating the
 * `"fred"` data will look something like this:
 * ```
 *     EntryPointCallerTracker.<init>  [EntryPoint]
 *     <*Test>$TestData.<init>
 *     <*Test>$Builder.addTest         [EntryPoint]
 *     <*Test>$lambda
 *     <*Test>.builder                 [EntryPoint]
 *     <*Test>$Companion.<clinit>
 * ```
 *
 * The first (from the bottom) `[EntryPoint]` (which is the call to `builder(...)`) and everything
 * above will be removed.
 *
 * That can be avoided by annotating the method that calls the lambda with [ExitPoint] that will
 * reset the starting point for the search to that place. However, it would not help to add that to
 * the `builder` method directly as it would not be clear that it applied only to the lambda (which
 * is why using an [EntryPoint] and [ExitPoint] annotation on the same method is not allowed). So,
 * the call to the lambda needs moving out into its own method which is annotated with [ExitPoint].
 *
 * e.g. the result would be something like this:
 * ```
 *     fun builder(defaultData: Int, body: Builder.() -> Unit): List<TestData> {
 *       val builder = Builder()
 *       builder.addTest("default", defaultData)
 *       return builder.build(body)
 *     }
 *
 *     class Builder {
 *       @EntryPoint
 *       fun addTest(name: String, data: Int) {...}
 *       @ExitPoint
 *       fun build(body: Builder.() -> Unit): List<TestData> {
 *         body()
 *         ...
 *       }
 *     }
 *
 *     val testCases = builder(defaultData = 4) {
 *        ...
 *        addTest("fred", 1)
 *        addTest("wilma", 2)
 *        addTest("barney", 3)
 *        ...
 *     }
 * ```
 *
 * With that, the stack trace for the `"default"` data would be:
 * ```
 *     EntryPointCallerTracker.<init>  [EntryPoint]
 *     <*Test>$TestData.<init>
 *     <*Test>$Builder.addTest         [EntryPoint]
 *     <*Test>.builder                 [EntryPoint]
 *     <*Test>$Companion.<clinit>
 * ```
 *
 * In the event of a test failure the elements for `builder` and above would be removed so the top
 * of the stack trace would correctly refer to the call to `builder(defaultData = 4) {`.
 *
 * The stack trace for `"fred"` data would be:
 * ```
 *     EntryPointCallerTracker.<init>  [EntryPoint]
 *     <*Test>$TestData.<init>
 *     <*Test>$Builder.addTest         [EntryPoint]
 *     <*Test>$lambda
 *     <*Test>$Builder.build           [ExitPoint]
 *     <*Test>.builder                 [EntryPoint]
 *     <*Test>$Companion.<clinit>
 * ```
 *
 * The `[ExitPoint]` would cause the upwards search for an [EntryPoint] to start from there so the
 * fact that `builder` is an [EntryPoint] would be ignored. So, the top of the stack trace for the
 * `"fred"` data would be the call from the lambda to `addTest("fred", 1)`.
 *
 * @param entryPointCallerTrackerProvider provides the [EntryPointCallerTracker] when needed. The
 *   [EntryPointCallerTracker] cannot be supplied directly when this is constructed as this is
 *   created before the test data is injected into the test class.
 */
class EntryPointCallerRule(
    private val entryPointCallerTrackerProvider: () -> EntryPointCallerTracker
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val entryPointCaller = entryPointCallerTrackerProvider()
                entryPointCaller.runTest { base.evaluate() }
            }
        }
    }
}
