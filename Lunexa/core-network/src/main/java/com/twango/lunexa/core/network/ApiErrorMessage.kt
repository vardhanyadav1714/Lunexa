package com.twango.lunexa.core.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import retrofit2.HttpException

fun Throwable.toApiMessage(defaultMessage: String): String {
    if (this !is HttpException) {
        return message ?: defaultMessage
    }

    val rawBody = response()?.errorBody()?.string().orEmpty()
    val parsedMessage = runCatching {
        val root = JsonParser.parseString(rawBody).asJsonObject
        val error = root.getAsJsonObject("error")
        error.firstDetailMessage()
            ?: error.get("message")?.asString
    }.getOrNull()

    return parsedMessage ?: "Request failed (${code()})."
}

private fun JsonObject.firstDetailMessage(): String? {
    val details = get("details") ?: return null
    if (!details.isJsonArray) return null

    return (details as JsonArray)
        .firstOrNull()
        ?.asJsonObject
        ?.get("message")
        ?.asString
}
