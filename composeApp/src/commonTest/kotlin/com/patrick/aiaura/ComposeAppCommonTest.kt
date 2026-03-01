package com.patrick.aiaura

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeAppCommonTest {
    @Test
    fun buildGeminiContents_mapsUserAndAssistantRoles() {
        val contents = buildGeminiContents(
            listOf(
                ChatMessage.user("hello"),
                ChatMessage.assistant("hey there"),
            ),
        )

        assertEquals("user", contents[0].jsonObject["role"]?.jsonPrimitive?.content)
        assertEquals("model", contents[1].jsonObject["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun firestoreEncoding_roundTripsMessages() {
        val original = listOf(
            ChatMessage.user("How are you?"),
            ChatMessage.assistant("I am doing great."),
        )

        val encoded = encodeMessagesForFirestore(original)
        val decoded = decodeMessagesFromFirestore(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun firestoreDecoding_ignoresInvalidPayload() {
        val decoded = decodeMessagesFromFirestore("not-json")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun parseGeminiReply_returnsFirstCandidateText() {
        val response = Json.parseToJsonElement(
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Answer from model" }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val reply = parseGeminiReply(response)
        assertEquals("Answer from model", reply)
    }

    @Test
    fun parseGeminiReply_joinsMultipleTextParts() {
        val response = Json.parseToJsonElement(
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Line 1" },
                      { "text": "Line 2" }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val reply = parseGeminiReply(response)
        assertEquals("Line 1\nLine 2", reply)
    }

    @Test
    fun buildGeminiRequestPayload_includesSystemInstructionAndGenerationConfig() {
        val config = AssistantConfig(
            geminiApiKey = "test",
            geminiModel = "gemini-2.5-flash",
            systemInstruction = "Be precise.",
            temperature = "0.3",
            maxOutputTokens = "256",
        )

        val payload = buildGeminiRequestPayload(
            config = config,
            history = listOf(ChatMessage.user("hello")),
        )

        val instructionText = payload
            .jsonObject["systemInstruction"]
            ?.jsonObject
            ?.jsonObject["parts"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.jsonObject["text"]
            ?.jsonPrimitive
            ?.content

        val generationConfig = payload.jsonObject["generationConfig"]?.jsonObject
        assertEquals("Be precise.", instructionText)
        assertEquals("0.3", generationConfig?.get("temperature")?.jsonPrimitive?.content)
        assertEquals("256", generationConfig?.get("maxOutputTokens")?.jsonPrimitive?.content)
    }

    @Test
    fun parseGeminiTemperature_andMaxTokens_validateRanges() {
        assertEquals(0.0, parseGeminiTemperature("0.0"))
        assertEquals(2.0, parseGeminiTemperature("2.0"))
        assertNull(parseGeminiTemperature("2.5"))
        assertNull(parseGeminiTemperature("abc"))

        assertEquals(1, parseGeminiMaxOutputTokens("1"))
        assertEquals(8192, parseGeminiMaxOutputTokens("8192"))
        assertNull(parseGeminiMaxOutputTokens("0"))
        assertNull(parseGeminiMaxOutputTokens("9000"))
    }
}