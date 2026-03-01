package com.patrick.aiaura

data class AssistantConfig(
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val firebaseApiKey: String = "",
    val firebaseProjectId: String = "",
    val firebaseEmail: String = "",
    val firebasePassword: String = "",
) {
    val hasGeminiConfig: Boolean
        get() = geminiApiKey.isNotBlank()

    val hasFirebaseConfig: Boolean
        get() = firebaseApiKey.isNotBlank() &&
            firebaseProjectId.isNotBlank() &&
            firebaseEmail.isNotBlank() &&
            firebasePassword.isNotBlank()
}

enum class MessageRole {
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val role: MessageRole,
    val text: String,
) {
    companion object {
        fun user(text: String): ChatMessage = ChatMessage(MessageRole.USER, text.trim())
        fun assistant(text: String): ChatMessage = ChatMessage(MessageRole.ASSISTANT, text.trim())
    }
}
