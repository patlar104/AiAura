package com.patrick.aiaura

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
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
}