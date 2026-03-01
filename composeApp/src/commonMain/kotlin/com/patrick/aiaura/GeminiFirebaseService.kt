package com.patrick.aiaura

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_CONTEXT_MESSAGES = 40
private const val MAX_PERSISTED_MESSAGES = 120

class GeminiFirebaseService(
    private val httpClient: HttpClient = createAiAuraHttpClient(),
) {
    suspend fun generateAssistantReply(
        config: AssistantConfig,
        history: List<ChatMessage>,
    ): String {
        require(config.hasGeminiConfig) {
            "Gemini API key is missing. Open Settings and set a key."
        }
        validateGeminiAdvancedConfig(config)

        val endpoint =
            "https://generativelanguage.googleapis.com/v1beta/models/${config.geminiModel}:generateContent" +
                "?key=${config.geminiApiKey}"
        val payload = buildGeminiRequestPayload(config, history)

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        return parseGeminiReply(parseJsonResponse(response, "Gemini request"))
    }

    suspend fun loadMessagesFromCloud(config: AssistantConfig): List<ChatMessage> {
        require(config.hasFirebaseConfig) {
            "Firebase settings are incomplete. Fill API key, project id, email, and password."
        }

        val session = signInWithFirebase(config)
        val response = httpClient.get(firestoreDocumentUrl(config.firebaseProjectId, session.uid)) {
            header(HttpHeaders.Authorization, "Bearer ${session.idToken}")
        }

        if (response.status == HttpStatusCode.NotFound) {
            return emptyList()
        }

        val document = parseJsonResponse(response, "Firestore load")
        val encodedHistory = document
            .jsonObjectOrNull("fields")
            ?.jsonObjectOrNull("historyJson")
            ?.stringOrNull("stringValue")
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        return decodeMessagesFromFirestore(encodedHistory)
    }

    suspend fun saveMessagesToCloud(
        config: AssistantConfig,
        history: List<ChatMessage>,
    ) {
        require(config.hasFirebaseConfig) {
            "Firebase settings are incomplete. Fill API key, project id, email, and password."
        }

        val session = signInWithFirebase(config)
        val safeHistory = history.takeLast(MAX_PERSISTED_MESSAGES)
        val payload = buildJsonObject {
            put(
                "fields",
                buildJsonObject {
                    put(
                        "historyJson",
                        buildJsonObject {
                            put("stringValue", JsonPrimitive(encodeMessagesForFirestore(safeHistory)))
                        },
                    )
                },
            )
        }

        val response = httpClient.patch(firestoreDocumentUrl(config.firebaseProjectId, session.uid)) {
            header(HttpHeaders.Authorization, "Bearer ${session.idToken}")
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        parseJsonResponse(response, "Firestore save")
    }

    private suspend fun signInWithFirebase(config: AssistantConfig): FirebaseSession {
        val endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${config.firebaseApiKey}"
        val payload = buildJsonObject {
            put("email", JsonPrimitive(config.firebaseEmail))
            put("password", JsonPrimitive(config.firebasePassword))
            put("returnSecureToken", JsonPrimitive(true))
        }

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val json = parseJsonResponse(response, "Firebase sign-in")

        val idToken = json.stringOrNull("idToken")
            ?: error("Firebase sign-in succeeded but idToken was missing.")
        val uid = json.stringOrNull("localId")
            ?: error("Firebase sign-in succeeded but localId was missing.")

        return FirebaseSession(
            uid = uid,
            idToken = idToken,
        )
    }

    private fun firestoreDocumentUrl(projectId: String, uid: String): String {
        return "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/users/$uid/conversations/default"
    }
}

private data class FirebaseSession(
    val uid: String,
    val idToken: String,
)

private val serviceJson = Json {
    ignoreUnknownKeys = true
}

fun createAiAuraHttpClient(): HttpClient {
    return HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 45_000
        }
    }
}

internal fun buildGeminiContents(history: List<ChatMessage>): JsonArray {
    return buildJsonArray {
        history.forEach { message ->
            add(
                buildJsonObject {
                    put(
                        "role",
                        JsonPrimitive(
                            if (message.role == MessageRole.USER) {
                                "user"
                            } else {
                                "model"
                            },
                        ),
                    )
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(message.text)) })
                        },
                    )
                },
            )
        }
    }
}

internal fun buildGeminiRequestPayload(
    config: AssistantConfig,
    history: List<ChatMessage>,
): JsonObject {
    val temperature = parseGeminiTemperature(config.temperature)
    val maxOutputTokens = parseGeminiMaxOutputTokens(config.maxOutputTokens)
    val systemInstruction = config.systemInstruction.trim().takeIf { it.isNotEmpty() }

    return buildJsonObject {
        put("contents", buildGeminiContents(history.takeLast(MAX_CONTEXT_MESSAGES)))

        if (systemInstruction != null) {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(systemInstruction)) })
                        },
                    )
                },
            )
        }

        val generationConfig = buildGeminiGenerationConfig(
            temperature = temperature,
            maxOutputTokens = maxOutputTokens,
        )
        if (generationConfig.isNotEmpty()) {
            put("generationConfig", generationConfig)
        }
    }
}

internal fun buildGeminiGenerationConfig(
    temperature: Double?,
    maxOutputTokens: Int?,
): JsonObject {
    return buildJsonObject {
        if (temperature != null) {
            put("temperature", JsonPrimitive(temperature))
        }
        if (maxOutputTokens != null) {
            put("maxOutputTokens", JsonPrimitive(maxOutputTokens))
        }
    }
}

internal fun parseGeminiTemperature(rawValue: String): Double? {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.toDoubleOrNull()?.takeIf { it in 0.0..2.0 }
}

internal fun parseGeminiMaxOutputTokens(rawValue: String): Int? {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) return null
    return trimmed.toIntOrNull()?.takeIf { it in 1..8192 }
}

private fun validateGeminiAdvancedConfig(config: AssistantConfig) {
    if (config.temperature.isNotBlank() && parseGeminiTemperature(config.temperature) == null) {
        error("Temperature must be between 0.0 and 2.0.")
    }
    if (config.maxOutputTokens.isNotBlank() && parseGeminiMaxOutputTokens(config.maxOutputTokens) == null) {
        error("Max output tokens must be an integer between 1 and 8192.")
    }
}

internal fun parseGeminiReply(response: JsonObject): String {
    val text = response
        .jsonArrayOrNull("candidates")
        ?.firstOrNull()
        ?.jsonObjectOrNull()
        ?.jsonObjectOrNull("content")
        ?.jsonArrayOrNull("parts")
        .orEmpty()
        .mapNotNull { part ->
            part.jsonObjectOrNull()
                ?.stringOrNull("text")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        .joinToString(separator = "\n")
        .trim()

    if (text.isBlank()) {
        error("Gemini returned an empty answer. Try again or use a different model.")
    }
    return text
}

internal fun encodeMessagesForFirestore(history: List<ChatMessage>): String {
    val payload = buildJsonArray {
        history.forEach { message ->
            add(
                buildJsonObject {
                    put("role", JsonPrimitive(message.role.name))
                    put("text", JsonPrimitive(message.text))
                },
            )
        }
    }
    return payload.toString()
}

internal fun decodeMessagesFromFirestore(encodedHistory: String): List<ChatMessage> {
    val parsed = runCatching {
        serviceJson.parseToJsonElement(encodedHistory).jsonArray
    }.getOrElse {
        return emptyList()
    }

    return parsed.mapNotNull { item ->
        val objectValue = item.jsonObjectOrNull() ?: return@mapNotNull null
        val role = objectValue.stringOrNull("role")
            ?.let { raw -> MessageRole.entries.firstOrNull { it.name == raw } }
            ?: return@mapNotNull null
        val text = objectValue.stringOrNull("text")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        ChatMessage(role = role, text = text)
    }
}

private suspend fun parseJsonResponse(response: HttpResponse, action: String): JsonObject {
    val rawBody = response.bodyAsText()
    if (response.status.value !in 200..299) {
        error("$action failed (${response.status.value}): ${extractErrorMessage(rawBody)}")
    }
    return runCatching {
        serviceJson.parseToJsonElement(rawBody).jsonObject
    }.getOrElse {
        error("$action returned invalid JSON.")
    }
}

private fun extractErrorMessage(rawBody: String): String {
    val parsed = runCatching {
        serviceJson.parseToJsonElement(rawBody).jsonObject
    }.getOrNull() ?: return "Unknown API error"

    val explicitMessage = parsed
        .jsonObjectOrNull("error")
        ?.stringOrNull("message")
        ?.takeIf { it.isNotBlank() }

    return explicitMessage ?: "Unknown API error"
}

private fun JsonObject.stringOrNull(key: String): String? {
    return this[key]
        ?.jsonPrimitive
        ?.contentOrNull
}

private fun JsonObject.jsonObjectOrNull(key: String): JsonObject? {
    return this[key]
        ?.jsonObjectOrNull()
}

private fun JsonObject.jsonArrayOrNull(key: String): JsonArray? {
    return this[key]
        ?.jsonArrayOrNull()
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? {
    return this as? JsonObject
}

private fun JsonElement.jsonArrayOrNull(): JsonArray? {
    return this as? JsonArray
}
