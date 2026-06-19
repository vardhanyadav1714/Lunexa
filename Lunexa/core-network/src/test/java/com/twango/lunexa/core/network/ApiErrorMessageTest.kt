package com.twango.lunexa.core.network

import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiErrorMessageTest {

    // ============== Non-HttpException Tests ==============

    @Test
    fun `toApiMessage returns message for generic throwable`() {
        val exception = Exception("Custom error message")

        val result = exception.toApiMessage("Default message")

        assertEquals("Custom error message", result)
    }

    @Test
    fun `toApiMessage returns default message when throwable has no message`() {
        val exception = RuntimeException()

        val result = exception.toApiMessage("Default message")

        assertEquals("Default message", result)
    }

    // ============== HttpException Tests ==============

    @Test
    fun `toApiMessage returns detail message from error body`() {
        val errorJson = """
        {
            "error": {
                "code": "VALIDATION_ERROR",
                "message": "Validation failed",
                "details": [
                    {"message": "Email is required"}
                ]
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Email is required", result)
    }

    @Test
    fun `toApiMessage returns error message when details are empty`() {
        val errorJson = """
        {
            "error": {
                "code": "NOT_FOUND",
                "message": "Resource not found",
                "details": []
            }
        }
        """.trimIndent()

        val exception = createHttpException(404, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Resource not found", result)
    }

    @Test
    fun `toApiMessage returns error message when details field is missing`() {
        val errorJson = """
        {
            "error": {
                "code": "UNAUTHORIZED",
                "message": "Authentication required"
            }
        }
        """.trimIndent()

        val exception = createHttpException(401, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Authentication required", result)
    }

    @Test
    fun `toApiMessage returns formatted error when JSON parsing fails`() {
        val invalidJson = "Not valid JSON"

        val exception = createHttpException(500, invalidJson)

        val result = exception.toApiMessage("Default error")

        assertTrue(result.contains("Request failed"))
        assertTrue(result.contains("500"))
    }

    @Test
    fun `toApiMessage returns formatted error when error body is empty`() {
        val exception = createHttpException(502, "")

        val result = exception.toApiMessage("Default error")

        assertTrue(result.contains("Request failed"))
        assertTrue(result.contains("502"))
    }

    @Test
    fun `toApiMessage returns formatted error when error body is null`() {
        val exception = HttpException(
            Response.error<Void>(
                403,
                ResponseBody.create(null, "")
            )
        )

        val result = exception.toApiMessage("Default error")

        assertTrue(result.contains("Request failed"))
        assertTrue(result.contains("403"))
    }

    @Test
    fun `toApiMessage handles nested details correctly`() {
        val errorJson = """
        {
            "error": {
                "code": "MULTIPLE_ERRORS",
                "message": "Multiple validation errors",
                "details": [
                    {"message": "First error"},
                    {"message": "Second error"}
                ]
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("First error", result)
    }

    @Test
    fun `toApiMessage handles details with non-array value`() {
        val errorJson = """
        {
            "error": {
                "code": "SOME_ERROR",
                "message": "Error occurred",
                "details": "Not an array"
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Error occurred", result)
    }

    @Test
    fun `toApiMessage handles missing error object`() {
        val errorJson = """
        {
            "status": "error",
            "message": "Something went wrong"
        }
        """.trimIndent()

        val exception = createHttpException(500, errorJson)

        val result = exception.toApiMessage("Default error")

        assertTrue(result.contains("Request failed"))
    }

    @Test
    fun `toApiMessage handles malformed JSON with missing message in details`() {
        val errorJson = """
        {
            "error": {
                "code": "ERROR",
                "message": "Base error",
                "details": [
                    {"field": "email"}
                ]
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Base error", result)
    }

    @Test
    fun `toApiMessage uses default message for 404 when parsing fails`() {
        val exception = createHttpException(404, "Not JSON")

        val result = exception.toApiMessage("Resource not found")

        assertTrue(result.contains("404"))
    }

    @Test
    fun `toApiMessage returns first detail even when additional fields present`() {
        val errorJson = """
        {
            "error": {
                "code": "VALIDATION_ERROR",
                "message": "Validation failed",
                "details": [
                    {
                        "field": "password",
                        "message": "Password is too weak",
                        "code": "WEAK_PASSWORD"
                    }
                ]
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Password is too weak", result)
    }

    @Test
    fun `toApiMessage handles empty details array gracefully`() {
        val errorJson = """
        {
            "error": {
                "code": "ERROR",
                "message": "Some error",
                "details": []
            }
        }
        """.trimIndent()

        val exception = createHttpException(400, errorJson)

        val result = exception.toApiMessage("Default error")

        assertEquals("Some error", result)
    }

    // ============== Helper Functions ==============

    private fun createHttpException(statusCode: Int, errorBody: String): HttpException {
        val response = Response.error<Void>(
            statusCode,
            ResponseBody.create("application/json".toMediaType(), errorBody)
        )
        return HttpException(response)
    }
}
