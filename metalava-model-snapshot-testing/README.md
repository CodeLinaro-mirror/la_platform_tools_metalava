# metalava-model-snapshot-testing

The purpose of this module is to test the snapshot mechanism by running the testsuite against a number of different
providers to create a `Codebase` and then take a snapshot of the `Codebase` and perform the actual test against the
snapshot. This will ensure that the snapshot provides the same capabilities as the original providers.

It does that by registering a `com.android.tools.metalava.model.testing.transformer.CodebaseTransformer` service which
is loaded by the `com.android.tools.metalava.model.testsuite.ModelSuiteRunner` and used to transform the `Codebase`
before performing the test.
