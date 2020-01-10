package com.exponea.sdk.telemetry

import com.exponea.sdk.telemetry.model.ErrorData
import com.exponea.sdk.telemetry.model.ErrorStackTraceElement
import com.exponea.sdk.testutil.ExponeaSDKTest
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(Enclosed::class)
internal class TelemetryUtilityTest {
    @RunWith(ParameterizedRobolectricTestRunner::class)
    internal class ParametrizedTest(
        private val name: String,
        private val throwableConstructor: () -> Throwable,
        private val expectedErrorData: ErrorData,
        private val expectedSDKRelated: Boolean
    ) : ExponeaSDKTest() {
        companion object {
            class TestCase(
                val name: String,
                val throwableConstructor: () -> Throwable,
                val expectedErrorData: ErrorData,
                val expectedSDKRelated: Boolean
            )

            private val testCases = arrayListOf(
                TestCase(
                    "Exception without message",
                    {
                        val e = Exception()
                        e.stackTrace = arrayOf()
                        e
                    },
                    ErrorData(
                        "java.lang.Exception",
                        "",
                        arrayListOf(),
                        null
                    ),
                    false
                ),
                TestCase(
                    "Exception with message",
                    {
                        val e = Exception("Exception happened")
                        e.stackTrace = arrayOf()
                        e
                    },
                    ErrorData(
                        "java.lang.Exception",
                        "Exception happened",
                        arrayListOf(),
                        null
                    ),
                    false
                ),
                TestCase(
                    "Exception with cause",
                    {
                        val e = RuntimeException("Exception happened", Exception("Cause exception"))
                        e.cause?.stackTrace = arrayOf()
                        e.stackTrace = arrayOf()
                        e
                    },
                    ErrorData(
                        "java.lang.RuntimeException",
                        "Exception happened",
                        arrayListOf(),
                        ErrorData(
                            "java.lang.Exception",
                            "Cause exception",
                            arrayListOf(),
                            null
                        )
                    ),
                    false
                ),
                TestCase(
                    "Exception with cyclic cause",
                    {
                        val e = Exception("Exception happened")
                        val otherException = Exception("Cause exception")
                        e.initCause(otherException)
                        otherException.initCause(e)
                        e.stackTrace = arrayOf()
                        otherException.stackTrace = arrayOf()
                        e
                    },
                    ErrorData(
                        "java.lang.Exception",
                        "Exception happened",
                        arrayListOf(),
                        ErrorData(
                            "java.lang.Exception",
                            "Cause exception",
                            arrayListOf(),
                            null
                        )
                    ),
                    false
                ),
                TestCase(
                    "Exception with cause related to SDK",
                    {
                        val e = Exception("Exception happened")
                        val otherException = NullPointerException("Brought to you by null pointer")
                        e.initCause(otherException)
                        otherException.initCause(e)
                        e.stackTrace = arrayOf()
                        otherException.stackTrace = arrayOf(
                            StackTraceElement("com.java.MockClass", "mockMethod", "mockFile", 1),
                            StackTraceElement("org.android.MockClass", "mockMethod", "mockFile", 2),
                            StackTraceElement("com.exponea.MockClass", "mockMethod", "mockFile", 3)
                        )
                        e
                    },
                    ErrorData(
                        "java.lang.Exception",
                        "Exception happened",
                        arrayListOf(),
                        ErrorData(
                            "java.lang.NullPointerException",
                            "Brought to you by null pointer",
                            arrayListOf(
                                ErrorStackTraceElement("com.java.MockClass", "mockMethod", "mockFile", 1),
                                ErrorStackTraceElement("org.android.MockClass", "mockMethod", "mockFile", 2),
                                ErrorStackTraceElement("com.exponea.MockClass", "mockMethod", "mockFile", 3)
                            ),
                            null
                        )
                    ),
                    true
                )
            )

            @JvmStatic
            @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
            fun data(): List<Array<out Any?>> {
                return testCases.map {
                    arrayOf(
                        it.name,
                        it.throwableConstructor,
                        it.expectedErrorData,
                        it.expectedSDKRelated
                    )
                }
            }
        }

        @Test
        fun `should get error data`() {
            val e = throwableConstructor()
            assertEquals(expectedErrorData, TelemetryUtility.getErrorData(e))
        }

        @Test
        fun `should check if throwable is sdk related`() {
            val e = throwableConstructor()
            assertEquals(expectedSDKRelated, TelemetryUtility.isSDKRelated(e))
        }
    }

    internal class NonParametrizedTest() {
        @Test
        fun `should truncate error data stacktrace`() {
            val e = Exception("test")
            e.stackTrace = arrayOf()
            for (i in 1..1000) e.stackTrace += StackTraceElement("mock class", "mock method", "mock file", i)
            assertEquals(100, TelemetryUtility.getErrorData(e).stackTrace.size)
        }
    }
}
