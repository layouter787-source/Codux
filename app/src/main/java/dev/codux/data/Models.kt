package dev.codux.data

import org.json.JSONObject

 data class CodexThread(
    val id: String,
    val name: String,
    val preview: String,
    val updatedAt: Long = 0L,
    val archived: Boolean = false
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val isStreaming: Boolean = false
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ApprovalRequest(
    val requestId: Any,
    val method: String,
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val title: String,
    val description: String,
    val availableDecisions: List<String>,
    val params: JSONObject = JSONObject()
)

data class WorkspaceFile(
    val uri: android.net.Uri,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?
)

data class OpenDocument(
    val uri: android.net.Uri,
    val parentUri: android.net.Uri,
    val name: String,
    val originalText: String,
    val editedText: String = originalText,
    val mimeType: String = "text/plain"
)

data class ProcessOutput(
    val handle: String,
    val stream: String,
    val text: String
)

data class AppServerEvent(
    val method: String,
    val params: JSONObject,
    val requestId: Any? = null
)

data class AppState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val connectionMessage: String = "Liga ao Termux para iniciar o Codex.",
    val threads: List<CodexThread> = emptyList(),
    val threadCursor: String? = null,
    val isLoadingThreads: Boolean = false,
    val activeThreadId: String? = null,
    val activeThreadName: String = "Nova conversa",
    val messages: List<ChatMessage> = emptyList(),
    val isTurnRunning: Boolean = false,
    val activeTurnId: String? = null,
    val pendingApproval: ApprovalRequest? = null,
    val currentDiff: String = "",
    val workspacePath: String = "/data/data/com.termux/files/home",
    val workspaceUri: android.net.Uri? = null,
    val files: List<WorkspaceFile> = emptyList(),
    val currentDirectoryUri: android.net.Uri? = null,
    val directoryStack: List<android.net.Uri> = emptyList(),
    val openDocument: OpenDocument? = null,
    val isSaving: Boolean = false,
    val diffPreview: String? = null,
    val isListening: Boolean = false,
    val terminalCommand: String = "",
    val terminalOutput: String = "",
    val terminalHandle: String? = null,
    val terminalRunning: Boolean = false,
    val terminalTimeoutSeconds: Int = 300,
    val showTerminalConfirmation: Boolean = false,
    val themeMode: String = "system",
    val error: String? = null
)
