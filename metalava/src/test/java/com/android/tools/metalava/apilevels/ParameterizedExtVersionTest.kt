package com.android.tools.metalava.apilevels

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedExtVersionTest {

    data class TestData(
        val input: String,
        val expectedValid: Boolean = true,
        val expectedString: String = input,
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
                ),
                TestData(
                    input = "1",
                ),
                TestData(
                    input = "01",
                    expectedString = "1",
                ),
            )
    }

    /** Get an [ExtVersion] from [text]. */
    private fun getExtVersionFromString(text: String) = ExtVersion(text.toInt())

    @Test
    fun test() {
        val version = getExtVersionFromString(testData.input)

        assertThat(version.isValid).isEqualTo(testData.expectedValid)
        assertThat(version.toString()).isEqualTo(testData.expectedString)
    }
}
