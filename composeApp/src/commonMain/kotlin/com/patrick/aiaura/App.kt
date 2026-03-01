package com.patrick.aiaura

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val QUICK_PROMPTS = listOf(
    "Plan my day in 5 bullet points.",
    "Draft a concise professional email.",
    "Give me 3 creative ideas for tonight.",
)

@Composable
@Preview
fun App() {
    MaterialTheme {
        AiAuraAssistantApp()
    }
}

@Composable
private fun AiAuraAssistantApp() {
    val service = remember { GeminiFirebaseService() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var config by remember { mutableStateOf(AssistantConfig()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(true) }
    var lastUserPrompt by remember { mutableStateOf<String?>(null) }

    var isSending by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Set your Gemini API key, then start chatting.") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun runFailure(message: String, throwable: Throwable) {
        statusMessage = message
        errorMessage = throwable.message ?: "Unknown error"
    }

    val visibleMessageCount = messages.size + if (isSending) 1 else 0
    val canSend = draft.isNotBlank() && !isSending && config.hasGeminiConfig

    LaunchedEffect(visibleMessageCount) {
        if (visibleMessageCount > 0) {
            listState.animateScrollToItem(visibleMessageCount - 1)
        }
    }

    fun saveToCloud() {
        if (isSending || isSyncing) return
        scope.launch {
            isSyncing = true
            errorMessage = null
            runCatching {
                service.saveMessagesToCloud(config, messages.toList())
            }.onSuccess {
                statusMessage = "Cloud history saved."
            }.onFailure { throwable ->
                runFailure("Could not save cloud history.", throwable)
            }
            isSyncing = false
        }
    }

    fun loadFromCloud() {
        if (isSending || isSyncing) return
        scope.launch {
            isSyncing = true
            errorMessage = null
            runCatching {
                service.loadMessagesFromCloud(config)
            }.onSuccess { cloudMessages ->
                messages.clear()
                messages.addAll(cloudMessages)
                statusMessage = if (cloudMessages.isEmpty()) {
                    "No cloud history found yet."
                } else {
                    "Loaded ${cloudMessages.size} message(s) from cloud history."
                }
            }.onFailure { throwable ->
                runFailure("Could not load cloud history.", throwable)
            }
            isSyncing = false
        }
    }

    fun sendMessage(explicitText: String? = null) {
        val text = explicitText?.trim() ?: draft.trim()
        if (text.isEmpty() || isSending) return
        if (!config.hasGeminiConfig) {
            statusMessage = "Add a Gemini API key before sending."
            errorMessage = null
            return
        }

        messages.add(ChatMessage.user(text))
        lastUserPrompt = text
        draft = ""
        errorMessage = null
        statusMessage = "Waiting for Gemini..."

        scope.launch {
            isSending = true
            runCatching {
                service.generateAssistantReply(config, messages.toList())
            }.onSuccess { reply ->
                messages.add(ChatMessage.assistant(reply))
                statusMessage = "Assistant replied."

                if (config.hasFirebaseConfig) {
                    runCatching {
                        service.saveMessagesToCloud(config, messages.toList())
                    }.onSuccess {
                        statusMessage = "Assistant replied and cloud history synced."
                    }.onFailure { throwable ->
                        runFailure("Assistant replied, but cloud sync failed.", throwable)
                    }
                }
            }.onFailure { throwable ->
                runFailure("Could not reach Gemini.", throwable)
            }
            isSending = false
        }
    }

    fun retryLastPrompt() {
        if (lastUserPrompt == null || isSending || isSyncing) return
        sendMessage(explicitText = lastUserPrompt)
    }

    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "AiAura Private Assistant",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Use the same Firebase credentials on every device to sync one private chat history.",
            style = MaterialTheme.typography.bodyMedium,
        )

        ReadinessRow(
            hasGemini = config.hasGeminiConfig,
            hasCloud = config.hasFirebaseConfig,
            hasAdvancedGeminiValues = config.hasAdvancedGeminiValues,
        )

        SettingsCard(
            config = config,
            showSettings = showSettings,
            onToggleSettings = { showSettings = !showSettings },
            onConfigChange = { config = it },
            controlsEnabled = !isSending && !isSyncing,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 1.dp,
        ) {
            if (messages.isEmpty() && !isSending) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "No messages yet. Send your first message to start the conversation.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(messages) { _, message ->
                        MessageBubble(message = message)
                    }
                    if (isSending) {
                        item {
                            TypingBubble()
                        }
                    }
                }
            }
        }

        QuickPromptsRow(
            onPromptClicked = { draft = it },
            enabled = !isSending,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") },
                placeholder = { Text("Ask anything...") },
                minLines = 1,
                maxLines = 4,
                enabled = !isSending,
            )

            if (!config.hasGeminiConfig) {
                Text(
                    text = "Gemini API key required before sending messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { sendMessage() },
                enabled = canSend,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Send")
                }
            }

            ActionButtonsRow(
                isSending = isSending,
                isSyncing = isSyncing,
                hasMessages = messages.isNotEmpty(),
                hasLastPrompt = lastUserPrompt != null,
                onLoadCloud = ::loadFromCloud,
                onSaveCloud = ::saveToCloud,
                onRetry = ::retryLastPrompt,
                onClear = {
                    messages.clear()
                    statusMessage = "Chat cleared locally."
                    errorMessage = null
                },
            )
        }
    }
}

@Composable
private fun QuickPromptsRow(
    onPromptClicked: (String) -> Unit,
    enabled: Boolean,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(QUICK_PROMPTS) { prompt ->
            FilledTonalButton(
                onClick = { onPromptClicked(prompt) },
                enabled = enabled,
            ) {
                Text(prompt, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    isSending: Boolean,
    isSyncing: Boolean,
    hasMessages: Boolean,
    hasLastPrompt: Boolean,
    onLoadCloud: () -> Unit,
    onSaveCloud: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilledTonalButton(
                onClick = onLoadCloud,
                enabled = !isSending && !isSyncing,
            ) {
                Text(if (isSyncing) "Working..." else "Load Cloud")
            }
        }
        item {
            FilledTonalButton(
                onClick = onSaveCloud,
                enabled = !isSending && !isSyncing && hasMessages,
            ) {
                Text("Save Cloud")
            }
        }
        item {
            FilledTonalButton(
                onClick = onRetry,
                enabled = !isSending && !isSyncing && hasLastPrompt,
            ) {
                Text("Retry Last")
            }
        }
        item {
            FilledTonalButton(
                onClick = onClear,
                enabled = !isSending && !isSyncing && hasMessages,
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun ReadinessRow(
    hasGemini: Boolean,
    hasCloud: Boolean,
    hasAdvancedGeminiValues: Boolean,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ReadinessBadge(
                label = if (hasGemini) "Gemini ready" else "Gemini missing",
                ok = hasGemini,
            )
        }
        item {
            ReadinessBadge(
                label = if (hasCloud) "Cloud sync ready" else "Cloud sync optional",
                ok = hasCloud,
            )
        }
        item {
            ReadinessBadge(
                label = if (hasAdvancedGeminiValues) "Advanced config valid" else "Check advanced config",
                ok = hasAdvancedGeminiValues,
            )
        }
    }
}

@Composable
private fun ReadinessBadge(
    label: String,
    ok: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SettingsCard(
    config: AssistantConfig,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onConfigChange: (AssistantConfig) -> Unit,
    controlsEnabled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onToggleSettings, enabled = controlsEnabled) {
                    Text(if (showSettings) "Hide" else "Show")
                }
            }

            if (showSettings) {
                OutlinedTextField(
                    value = config.geminiApiKey,
                    onValueChange = { onConfigChange(config.copy(geminiApiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.geminiModel,
                    onValueChange = { onConfigChange(config.copy(geminiModel = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini model") },
                    placeholder = { Text("gemini-2.5-flash") },
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.systemInstruction,
                    onValueChange = { onConfigChange(config.copy(systemInstruction = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("System instruction") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.temperature,
                    onValueChange = { onConfigChange(config.copy(temperature = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Temperature (0.0 - 2.0)") },
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.maxOutputTokens,
                    onValueChange = { onConfigChange(config.copy(maxOutputTokens = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Max output tokens (1 - 8192)") },
                    enabled = controlsEnabled,
                )

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = config.firebaseApiKey,
                    onValueChange = { onConfigChange(config.copy(firebaseApiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Firebase Web API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.firebaseProjectId,
                    onValueChange = { onConfigChange(config.copy(firebaseProjectId = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Firebase project id") },
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.firebaseEmail,
                    onValueChange = { onConfigChange(config.copy(firebaseEmail = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Firebase email") },
                    enabled = controlsEnabled,
                )
                OutlinedTextField(
                    value = config.firebasePassword,
                    onValueChange = { onConfigChange(config.copy(firebasePassword = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Firebase password") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = controlsEnabled,
                )

                Text(
                    text = "Gemini key is required for replies. Fill Firebase settings to enable cloud history sync.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Tip: gemini-2.5-flash is a solid default for low-latency chat.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(14.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = if (isUser) "You" else "AiAura",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.55f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.height(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "AiAura is thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
