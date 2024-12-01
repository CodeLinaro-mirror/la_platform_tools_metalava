package com.android.tools.metalava.apilevels

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedSdkVersionTest {

    data class TestData(
        val input: String,
        val expectedValid: Boolean = true,
        val expectedString: String = input,
        val expectedIncremented: String,
    ) {
        override fun toString() = input
    }

    @Parameterized.Parameter(0) lateinit var testData: TestData

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() =
            listOf(
                TestData(
                    input = "0",
                    expectedValid = false,
                    expectedIncremented = "1",
                ),
                TestData(
                    input = "1",
                    expectedIncremented = "2",
                ),
                TestData(
                    input = "01",
                    expectedString = "1",
                    expectedIncremented = "2",
                ),
            )
    }

    /** Get an [SdkVersion] from [text]. */
    private fun getSdkVersionFromString(text: String) = SdkVersion(text.toInt())

    @Test
    fun test() {
        val version = getSdkVersionFromString(testData.input)

        assertThat(version.isValid).isEqualTo(testData.expectedValid)
        assertThat(version.toString()).isEqualTo(testData.expectedString)

        val incrementedVersion = version + 1
        val expectedIncrementedVersion = getSdkVersionFromString(testData.expectedIncremented)
        assertThat(incrementedVersion).isEqualTo(expectedIncrementedVersion)
    }
}
