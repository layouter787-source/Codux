package dev.codux.data

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

class CoduxViewModel(application: Application) : AndroidViewModel(application) {
    private val embeddedHomePath = java.io.File(application.filesDir, "home").absolutePath
    private val preferences = application.getSharedPreferences("codux_settings", 0)
    private val secureTokens = SecureTokenStore(application)
    private val termux = TermuxBridge(application)
    private val projectFiles = ProjectFiles(application)
    private val workspaceSync = ProjectWorkspaceSync(application)
    private val _state = MutableStateFlow(AppState(
        workspacePath = preferences.getString("workspace_path", embeddedHomePath)?.takeUnless { it.startsWith("/data/data/com.termux/files/") } ?: embeddedHomePath,
        terminalTimeoutSeconds = preferences.getInt("terminal_timeout", 300),
        themeMode = preferences.getString("theme_mode", "system") ?: "system",
        accentColor = preferences.getString("accent_color", "blue") ?: "blue",
        textScale = preferences.getFloat("text_scale", 1f),
        transparency = preferences.getFloat("transparency", 0.12f),
        projects = readProjects()
    ))
    val state: StateFlow<AppState> = _state.asStateFlow()
    init {
        preferences.edit().putString("workspace_path", _state.value.workspacePath).apply()
        persistProjects(_state.value.projects)
    }
    private var client: CodexAppServerClient? = null
    private var eventJob: Job? = null
    private var terminalTimeoutJob: Job? = null
    private var directoryLoadId = 0L
    private var threadLoadId = 0L
    private val terminalDecoder = Utf8StreamDecoder()

    private fun readProjects(): List<ProjectWorkspace> = runCatching {
        val rows = JSONArray(preferences.getString("projects", "[]"))
        (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val uri = runCatching { Uri.parse(row.optString("uri")) }.getOrNull() ?: return@mapNotNull null
            ProjectWorkspace(uri, row.optString("name", "Projeto"), row.optString("termuxPath", embeddedHomePath).let { if (it.startsWith("/data/data/com.termux/files/")) embeddedHomePath else it }, row.optLong("lastOpenedAt"))
        }
    }.getOrDefault(emptyList())

    private fun persistProjects(projects: List<ProjectWorkspace>) {
        val rows = JSONArray()
        projects.forEach { project ->
            rows.put(JSONObject().put("uri", project.uri.toString()).put("name", project.name)
                .put("termuxPath", project.termuxPath).put("lastOpenedAt", project.lastOpenedAt))
        }
        preferences.edit().putString("projects", rows.toString()).apply()
    }

    fun setWorkspacePath(path: String) {
        val normalized = path.trim()
        if (normalized.isBlank() || !normalized.startsWith('/') || normalized.contains('\n') || normalized.contains('\r')) {
            setError("Indica um caminho absoluto válido no Termux.")
            return
        }
        preferences.edit().putString("workspace_path", normalized).apply()
        val projects = _state.value.projects.map { project ->
            if (project.uri == _state.value.workspaceUri) project.copy(termuxPath = normalized) else project
        }
        persistProjects(projects)
        _state.value = _state.value.copy(workspacePath = normalized, projects = projects, error = null)
    }

    fun setThemeMode(mode: String) {
        val safe = mode.takeIf { it in setOf("system", "light", "dark") } ?: "system"
        preferences.edit().putString("theme_mode", safe).apply()
        _state.value = _state.value.copy(themeMode = safe)
    }

    fun setAccentColor(color: String) {
        val safe = color.takeIf { it in setOf("blue", "violet", "mint", "orange", "rose") } ?: "blue"
        preferences.edit().putString("accent_color", safe).apply()
        _state.value = _state.value.copy(accentColor = safe)
    }

    fun setTextScale(scale: Float) {
        val bounded = scale.coerceIn(0.85f, 1.25f)
        preferences.edit().putFloat("text_scale", bounded).apply()
        _state.value = _state.value.copy(textScale = bounded)
    }

    fun setTransparency(value: Float) {
        val bounded = value.coerceIn(0f, 0.35f)
        preferences.edit().putFloat("transparency", bounded).apply()
        _state.value = _state.value.copy(transparency = bounded)
    }

    fun setTerminalTimeout(seconds: Int) {
        val bounded = seconds.coerceIn(10, 3600)
        preferences.edit().putInt("terminal_timeout", bounded).apply()
        _state.value = _state.value.copy(terminalTimeoutSeconds = bounded)
    }

    fun selectWorkspaceTree(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (error: SecurityException) {
            _state.value = _state.value.copy(error = "O Android não concedeu acesso persistente a esta pasta: ${error.message}")
            return
        }
        preferences.edit().putString("workspace_tree_uri", uri.toString()).apply()
        val path = workspaceSync.localPath(uri)
        val name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "Projeto"
        val project = ProjectWorkspace(uri, name, path, System.currentTimeMillis())
        val projects = (_state.value.projects.filterNot { it.uri == uri } + project).sortedByDescending { it.lastOpenedAt }
        persistProjects(projects)
        preferences.edit().putString("workspace_path", path).apply()
        _state.value = _state.value.copy(workspaceUri = uri, workspacePath = path, projects = projects, currentDirectoryUri = uri, directoryStack = listOf(uri), openDocument = null, error = null)
        listFiles(uri)
    }

    fun restoreWorkspaceTree() {
        val text = preferences.getString("workspace_tree_uri", null) ?: return
        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return
        val project = _state.value.projects.firstOrNull { it.uri == uri } ?: ProjectWorkspace(
            uri = uri,
            name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "Projeto",
            termuxPath = _state.value.workspacePath,
            lastOpenedAt = System.currentTimeMillis()
        )
        val path = workspaceSync.localPath(uri)
        val projectPath = project.copy(termuxPath = path)
        val projects = if (_state.value.projects.any { it.uri == uri }) _state.value.projects.map { if (it.uri == uri) projectPath else it } else (_state.value.projects + projectPath).sortedByDescending { it.lastOpenedAt }
        persistProjects(projects)
        _state.value = _state.value.copy(workspaceUri = uri, projects = projects, currentDirectoryUri = uri, directoryStack = listOf(uri), workspacePath = path)
        preferences.edit().putString("workspace_path", path).apply()
        listFiles(uri)
    }

    fun openProject(project: ProjectWorkspace) {
        val opened = project.copy(termuxPath = workspaceSync.localPath(project.uri), lastOpenedAt = System.currentTimeMillis())
        val projects = (_state.value.projects.filterNot { it.uri == project.uri } + opened).sortedByDescending { it.lastOpenedAt }
        persistProjects(projects)
        preferences.edit().putString("workspace_tree_uri", project.uri.toString()).putString("workspace_path", opened.termuxPath).apply()
        _state.value = _state.value.copy(workspaceUri = project.uri, workspacePath = opened.termuxPath, projects = projects,
            currentDirectoryUri = project.uri, directoryStack = listOf(project.uri), openDocument = null, error = null)
        prepareProjectWorkspace(project.uri, opened.termuxPath)
    }

    private fun prepareProjectWorkspace(uri: Uri, path: String) {
        viewModelScope.launch {
            runCatching { workspaceSync.fromSaf(uri, path) }
                .onSuccess { listFiles(uri) }
                .onFailure { _state.value = _state.value.copy(error = "Não foi possível preparar o projeto para o ambiente integrado: ${it.message}") }
        }
    }

    fun removeProject(project: ProjectWorkspace) {
        val projects = _state.value.projects.filterNot { it.uri == project.uri }
        persistProjects(projects)
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(project.uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (_state.value.workspaceUri == project.uri) {
            preferences.edit().remove("workspace_tree_uri").apply()
            _state.value = _state.value.copy(projects = projects, workspaceUri = null, currentDirectoryUri = null, directoryStack = emptyList(), files = emptyList())
        } else _state.value = _state.value.copy(projects = projects)
    }

    fun createProjectItem(name: String, directory: Boolean) {
        val parent = _state.value.currentDirectoryUri ?: return setError("Abre um projeto antes de criar itens.")
        viewModelScope.launch {
            runCatching { projectFiles.create(parent, name, directory) }
                .onSuccess { listFiles(parent) }
                .onFailure { setError(it.message ?: "Não foi possível criar o item.") }
        }
    }

    fun importProjectFile(sourceUri: Uri) {
        val parent = _state.value.currentDirectoryUri ?: return setError("Abre um projeto antes de importar ficheiros.")
        viewModelScope.launch {
            runCatching { projectFiles.importFile(parent, sourceUri) }
                .onSuccess { listFiles(parent) }
                .onFailure { setError(it.message ?: "Não foi possível importar o ficheiro.") }
        }
    }

    fun copyProjectItem(file: WorkspaceFile) {
        val parent = _state.value.currentDirectoryUri ?: return
        viewModelScope.launch {
            runCatching { projectFiles.copy(file, parent) }
                .onSuccess { listFiles(parent) }
                .onFailure { setError(it.message ?: "Não foi possível copiar o item.") }
        }
    }

    fun moveProjectItem(file: WorkspaceFile, destination: Uri) {
        val source = _state.value.currentDirectoryUri ?: return
        viewModelScope.launch {
            runCatching { projectFiles.move(file, source, destination) }
                .onSuccess { listFiles(source) }
                .onFailure { setError(it.message ?: "Não foi possível mover o item.") }
        }
    }

    fun renameProjectItem(file: WorkspaceFile, name: String) {
        val parent = _state.value.currentDirectoryUri ?: return
        viewModelScope.launch {
            runCatching { projectFiles.rename(file, name) }
                .onSuccess { renamed ->
                    val doc = _state.value.openDocument?.takeIf { it.uri == file.uri }?.copy(uri = renamed.uri, name = renamed.name)
                    _state.value = _state.value.copy(openDocument = doc ?: _state.value.openDocument)
                    listFiles(parent)
                }
                .onFailure { setError(it.message ?: "Não foi possível renomear o item.") }
        }
    }

    fun deleteProjectItem(file: WorkspaceFile) {
        val parent = _state.value.currentDirectoryUri ?: return
        viewModelScope.launch {
            runCatching { projectFiles.delete(file) }
                .onSuccess {
                    if (_state.value.openDocument?.uri == file.uri) closeDocument()
                    listFiles(parent)
                }
                .onFailure { setError(it.message ?: "Não foi possível apagar o item.") }
        }
    }

    fun listFiles() {
        val uri = _state.value.currentDirectoryUri ?: return
        listFiles(uri)
    }

    fun listFiles(uri: Uri) {
        val requestId = ++directoryLoadId
        viewModelScope.launch {
            try {
                val files = projectFiles.list(uri)
                if (requestId != directoryLoadId) return@launch
                val currentStack = _state.value.directoryStack
                val stack = when {
                    currentStack.lastOrNull() == uri -> currentStack
                    uri in currentStack -> currentStack.take(currentStack.indexOf(uri) + 1)
                    else -> currentStack + uri
                }
                _state.value = _state.value.copy(currentDirectoryUri = uri, directoryStack = stack, files = files, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestId == directoryLoadId) _state.value = _state.value.copy(error = error.message ?: "Falha ao listar a pasta.")
            }
        }
    }

    fun navigateUp() {
        val stack = _state.value.directoryStack
        if (stack.size <= 1) return
        listFiles(stack[stack.lastIndex - 1])
    }

    fun openFile(file: WorkspaceFile) {
        if (file.isDirectory) {
            listFiles(file.uri)
            return
        }
        val parent = _state.value.currentDirectoryUri ?: return
        viewModelScope.launch {
            runCatching { projectFiles.read(parent, file.uri, file.name) }
                .onSuccess { _state.value = _state.value.copy(openDocument = it, diffPreview = null, error = null) }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Não foi possível abrir o ficheiro.") }
        }
    }

    fun editDocument(text: String) {
        _state.value.openDocument?.let { _state.value = _state.value.copy(openDocument = it.copy(editedText = text)) }
    }

    fun previewDocumentDiff() {
        val doc = _state.value.openDocument ?: return
        _state.value = _state.value.copy(diffPreview = UnifiedDiff.create(doc.originalText, doc.editedText))
    }

    fun saveDocument() {
        val document = _state.value.openDocument ?: return
        if (document.originalText == document.editedText) {
            _state.value = _state.value.copy(error = "Não há alterações para guardar.")
            return
        }
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val saved = projectFiles.save(document)
                _state.value = _state.value.copy(openDocument = saved, diffPreview = null, error = null)
                listFiles(saved.parentUri)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: "Falha ao guardar o ficheiro.")
            } finally {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    fun closeDocument() { _state.value = _state.value.copy(openDocument = null, diffPreview = null) }

    fun connectToTermux() {
        if (_state.value.connecting || _state.value.connected) return
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, connectionMessage = "A preparar o ambiente integrado…", error = null)
            val runtimeReady = runCatching { termux.installRuntimeIfNeeded() }.isSuccess
            if (!runtimeReady || !termux.isInstalled()) {
                _state.value = _state.value.copy(connecting = false, connectionMessage = "Runtime integrado indisponível.", error = "O runtime ARM64 ainda não está disponível nesta versão. Aguarda uma versão do Codux que inclua o bootstrap.")
                return@launch
            }
            _state.value = _state.value.copy(connecting = true, connectionMessage = "A iniciar o servidor Codex integrado…", error = null)
            if (!termux.isCodexInstalled()) {
                _state.value = _state.value.copy(connecting = false, error = "O Codex CLI não está instalado no ambiente integrado. Abre Terminal integrado e instala o Codex CLI antes de ligar.")
                return@launch
            }
            val token = secureTokens.getOrCreate()
            val savedPort = preferences.getInt("app_server_port", 0)
            var connection = if (savedPort in 1024..65535) TermuxBridge.ServerConnection(savedPort, "ws://127.0.0.1:$savedPort", token) else null
            var connected = connection?.let { tryConnect(it) } == true
            if (!connected) {
                termux.stopAppServer()
                delay(350)
                connection = runCatching { termux.createConnection(token) }.getOrElse {
                    _state.value = _state.value.copy(connecting = false, error = it.message ?: "Falha ao selecionar uma porta local.")
                    return@launch
                }
                preferences.edit().putInt("app_server_port", connection.port).apply()
                try {
                    termux.launchAppServer(connection, _state.value.workspacePath)
                } catch (error: Exception) {
                    _state.value = _state.value.copy(connecting = false, error = "Não foi possível iniciar o Codex integrado: ${error.message}")
                    return@launch
                }
                for (attempt in 0 until 45) {
                    if (tryConnect(connection)) {
                        connected = true
                        break
                    }
                    delay(700)
                }
            }
            if (!connected) {
                _state.value = _state.value.copy(
                    connecting = false,
                    connectionMessage = "Sem ligação ao App Server.",
                    error = "O servidor não respondeu. Confirma que o Codex CLI está instalado e autenticado no ambiente integrado."
                )
                return@launch
            }
            _state.value = _state.value.copy(connected = true, connecting = false, connectionMessage = "Codex ligado ao ambiente integrado.", error = null)
            loadThreads()
        }
    }

    private suspend fun tryConnect(connection: TermuxBridge.ServerConnection): Boolean {
        val newClient = CodexAppServerClient(connection.endpoint, connection.token)
        val collector = viewModelScope.launch {
            newClient.events.collect { handleEvent(it) }
        }
        return try {
            newClient.connect()
            client?.close()
            eventJob?.cancel()
            client = newClient
            eventJob = collector
            true
        } catch (_: Exception) {
            collector.cancel()
            newClient.close()
            false
        }
    }

    fun disconnect() {
        val currentClient = client
        val processHandle = _state.value.terminalHandle
        _state.value = _state.value.copy(connecting = true, connectionMessage = "A desligar do ambiente integrado…")
        viewModelScope.launch {
            if (processHandle != null) {
                runCatching {
                    withTimeout(2_000) { currentClient?.request("process/kill", JSONObject().put("processHandle", processHandle)) }
                }
            }
            currentClient?.close()
            if (client === currentClient) client = null
            eventJob?.cancel()
            eventJob = null
            terminalTimeoutJob?.cancel()
            terminalTimeoutJob = null
            terminalDecoder.reset()
            termux.stopAppServer()
            runCatching { dev.codux.CodexSessionService.stop(getApplication()) }
            _state.value = _state.value.copy(
                connected = false,
                connecting = false,
                connectionMessage = "Desligado do ambiente integrado.",
                isTurnRunning = false,
                activeTurnId = null,
                pendingApproval = null,
                terminalRunning = false,
                terminalHandle = null,
                showTerminalConfirmation = false
            )
        }
    }

    private suspend fun loadThreads(reset: Boolean = true) {
        val activeClient = client ?: return
        if (!reset && (_state.value.isLoadingThreads || _state.value.threadCursor == null)) return
        val cursor = if (reset) null else _state.value.threadCursor
        val requestId = ++threadLoadId
        _state.value = _state.value.copy(isLoadingThreads = true)
        val params = JSONObject()
            .put("limit", 100)
            .put("sortKey", "updated_at")
            .put("sortDirection", "desc")
            .put("archived", false)
            .put("sourceKinds", JSONArray().put("cli").put("vscode").put("appServer"))
        if (cursor != null) params.put("cursor", cursor)
        runCatching { activeClient.request("thread/list", params) }
            .onSuccess { result ->
                if (requestId != threadLoadId) return@onSuccess
                val data = result.optJSONArray("data") ?: JSONArray()
                val page = (0 until data.length()).mapNotNull { index ->
                    data.optJSONObject(index)?.let { row ->
                        val id = row.optString("id").takeIf(String::isNotBlank) ?: return@let null
                        CodexThread(id, row.optString("name").ifBlank { row.optString("preview").take(48).ifBlank { "Conversa" } }, row.optString("preview"), row.optLong("updatedAt"), row.optBoolean("archived"))
                    }
                }
                val nextCursor = result.optString("nextCursor").takeIf(String::isNotBlank)
                val threads = if (reset) page else (_state.value.threads + page).distinctBy { it.id }
                _state.value = _state.value.copy(threads = threads, threadCursor = nextCursor, isLoadingThreads = false)
            }
            .onFailure {
                if (requestId == threadLoadId) _state.value = _state.value.copy(isLoadingThreads = false, error = "Não foi possível carregar o histórico: ${it.message}")
            }
    }

    fun loadMoreThreads() {
        if (_state.value.threadCursor == null || _state.value.isLoadingThreads) return
        viewModelScope.launch { loadThreads(reset = false) }
    }

    fun createThread() {
        if (_state.value.isTurnRunning) {
            setError("Cancela ou aguarda a resposta atual antes de mudar de conversa.")
            return
        }
        viewModelScope.launch {
            val activeClient = client ?: return@launch setError("Liga ao Termux primeiro.")
            val state = _state.value
            runCatching {
                activeClient.request("thread/start", JSONObject()
                    .put("cwd", state.workspacePath)
                    .put("approvalPolicy", "on-request")
                    .put("sandbox", "workspace-write")
                    .put("ephemeral", false))
            }.onSuccess { result ->
                val thread = result.optJSONObject("thread") ?: JSONObject()
                val id = thread.optString("id")
                if (id.isBlank()) return@onSuccess setError("O Codex não devolveu o ID da conversa.")
                _state.value = _state.value.copy(activeThreadId = id, activeThreadName = "Nova conversa", messages = emptyList(), currentDiff = "", pendingApproval = null, error = null)
                loadThreads()
            }.onFailure { setError("Não foi possível criar a conversa: ${it.message}") }
        }
    }

    fun openThread(thread: CodexThread) {
        if (_state.value.isTurnRunning) {
            setError("Cancela ou aguarda a resposta atual antes de mudar de conversa.")
            return
        }
        viewModelScope.launch {
            val activeClient = client ?: return@launch setError("Liga ao Termux primeiro.")
            val cwd = _state.value.workspacePath
            runCatching {
                activeClient.request("thread/resume", JSONObject().put("threadId", thread.id).put("cwd", cwd).put("excludeTurns", true))
                activeClient.request("thread/turns/list", JSONObject().put("threadId", thread.id).put("limit", 100).put("sortDirection", "desc").put("itemsView", "full"))
            }.onSuccess { result ->
                val data = result.optJSONArray("data") ?: JSONArray()
                val messages = mutableListOf<ChatMessage>()
                for (i in data.length() - 1 downTo 0) {
                    val entry = data.optJSONObject(i) ?: continue
                    val turn = entry.optJSONObject("turn") ?: entry
                    val items = turn.optJSONArray("items") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        when (item.optString("type")) {
                            "userMessage" -> messages += ChatMessage(item.optString("id", UUID.randomUUID().toString()), MessageRole.USER, textFromContent(item.optJSONArray("content")))
                            "agentMessage" -> messages += ChatMessage(item.optString("id", UUID.randomUUID().toString()), MessageRole.ASSISTANT, item.optString("text"))
                        }
                    }
                }
                val newestTurn = data.optJSONObject(0)?.let { it.optJSONObject("turn") ?: it }
                val activeTurnId = newestTurn?.takeIf { it.optString("status") == "inProgress" }
                    ?.optString("id")?.takeIf(String::isNotBlank)
                _state.value = _state.value.copy(activeThreadId = thread.id, activeThreadName = thread.name, messages = messages, currentDiff = "", pendingApproval = null, isTurnRunning = activeTurnId != null, activeTurnId = activeTurnId, error = null)
                if (activeTurnId != null) startSessionNotification("O Codex está a trabalhar nesta conversa")
            }.onFailure { setError("Não foi possível reabrir a conversa: ${it.message}") }
        }
    }

    fun sendMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        val activeClient = client ?: return setError("Liga ao Termux primeiro.")
        startSessionNotification("A preparar a resposta do Codex")
        viewModelScope.launch {
            val selectedWorkspace = _state.value
            try {
                selectedWorkspace.workspaceUri?.let { workspaceSync.fromSaf(it, selectedWorkspace.workspacePath) }
            } catch (error: Exception) {
                _state.value = _state.value.copy(isTurnRunning = false, error = "Não foi possível sincronizar o projeto antes da conversa: ${error.message}")
                stopSessionNotificationIfIdle()
                return@launch
            }
            val threadId = _state.value.activeThreadId ?: createThreadAndWait()
            if (threadId == null) {
                stopSessionNotificationIfIdle()
                return@launch
            }
            val userMessage = ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, cleaned)
            _state.value = _state.value.copy(messages = _state.value.messages + userMessage, isTurnRunning = true, pendingApproval = null, error = null)
            val cwd = _state.value.workspacePath
            val sandbox = JSONObject().put("type", "workspaceWrite").put("writableRoots", JSONArray().put(cwd)).put("networkAccess", true)
            runCatching {
                activeClient.request("turn/start", JSONObject()
                    .put("threadId", threadId)
                    .put("input", JSONArray().put(JSONObject().put("type", "text").put("text", cleaned)))
                    .put("cwd", cwd)
                    .put("approvalPolicy", "on-request")
                    .put("sandboxPolicy", sandbox))
            }.onSuccess { result ->
                val turnId = result.optJSONObject("turn")?.optString("id")
                if (!turnId.isNullOrBlank()) _state.value = _state.value.copy(activeTurnId = turnId)
            }.onFailure { error ->
                _state.value = _state.value.copy(isTurnRunning = false, activeTurnId = null, messages = _state.value.messages + ChatMessage(UUID.randomUUID().toString(), MessageRole.SYSTEM, "Erro ao iniciar: ${error.message}"))
                stopSessionNotificationIfIdle()
            }
        }
    }

    private suspend fun createThreadAndWait(): String? {
        val activeClient = client ?: return null
        return runCatching {
            activeClient.request("thread/start", JSONObject()
                .put("cwd", _state.value.workspacePath)
                .put("approvalPolicy", "on-request")
                .put("sandbox", "workspace-write")
                .put("ephemeral", false))
        }.fold(onSuccess = { result ->
            val id = result.optJSONObject("thread")?.optString("id").orEmpty()
            if (id.isBlank()) {
                setError("O Codex não devolveu o ID da conversa.")
                null
            } else {
                _state.value = _state.value.copy(activeThreadId = id, activeThreadName = "Nova conversa")
                id
            }
        }, onFailure = { error ->
            setError("Não foi possível criar a conversa: " + error.message)
            null
        })
    }

    fun cancelTurn() {
        val threadId = _state.value.activeThreadId ?: return
        val turnId = _state.value.activeTurnId ?: return
        viewModelScope.launch {
            runCatching { client?.request("turn/interrupt", JSONObject().put("threadId", threadId).put("turnId", turnId)) }
                .onFailure { setError("Não foi possível cancelar: ${it.message}") }
        }
    }

    fun answerApproval(decision: String) {
        val request = _state.value.pendingApproval ?: return
        val allowed = setOf("accept", "acceptForSession", "decline", "cancel")
        if (decision !in allowed) return
        val result = if (request.method == "item/permissions/requestApproval") {
            val permissions = if (decision == "decline" || decision == "cancel") JSONObject()
                else request.params.optJSONObject("permissions")
                    ?: request.params.optJSONObject("additionalPermissions")
                    ?: JSONObject()
            JSONObject().put("permissions", permissions)
                .put("scope", if (decision == "acceptForSession") "session" else "turn")
        } else {
            JSONObject().put("decision", decision)
        }
        client?.respond(request.requestId, result)
        _state.value = _state.value.copy(pendingApproval = null)
    }

    fun startTerminalCommand(command: String) {
        if (command.isBlank() || _state.value.terminalRunning) return
        _state.value = _state.value.copy(terminalCommand = command, showTerminalConfirmation = true)
    }

    fun confirmTerminalCommand() {
        val command = _state.value.terminalCommand.trim()
        val activeClient = client
        if (command.isEmpty() || activeClient == null) {
            _state.value = _state.value.copy(showTerminalConfirmation = false)
            return
        }
        val timeoutSeconds = _state.value.terminalTimeoutSeconds
        _state.value = _state.value.copy(showTerminalConfirmation = false, terminalRunning = true, terminalOutput = "", terminalHandle = null, error = null)
        startSessionNotification("A executar comando confirmado no Termux")
        viewModelScope.launch {
            val selectedWorkspace = _state.value
            try {
                selectedWorkspace.workspaceUri?.let { workspaceSync.fromSaf(it, selectedWorkspace.workspacePath) }
            } catch (error: Exception) {
                terminalDecoder.reset()
                _state.value = _state.value.copy(terminalRunning = false, terminalHandle = null, terminalOutput = "Não foi possível sincronizar o projeto antes do comando: ${error.message}")
                stopSessionNotificationIfIdle()
                return@launch
            }
            val handle = "codux-${UUID.randomUUID()}"
            terminalDecoder.reset()
            _state.value = _state.value.copy(terminalHandle = handle)
            runCatching {
                activeClient.request("process/spawn", JSONObject()
                    .put("command", JSONArray().put("bash").put("-lc").put(command))
                    .put("processHandle", handle)
                    .put("cwd", _state.value.workspacePath)
                    .put("tty", true)
                    .put("timeoutMs", timeoutSeconds * 1000L))
            }.onFailure { error ->
                terminalDecoder.reset()
                _state.value = _state.value.copy(terminalRunning = false, terminalHandle = null, terminalOutput = "Falha ao iniciar o comando: ${error.message}")
                stopSessionNotificationIfIdle()
                return@launch
            }
            terminalTimeoutJob?.cancel()
            terminalTimeoutJob = viewModelScope.launch {
                delay(timeoutSeconds * 1000L)
                if (_state.value.terminalHandle == handle && _state.value.terminalRunning) {
                    runCatching { client?.request("process/kill", JSONObject().put("processHandle", handle)) }
                    appendTerminal("\n[Codux] Comando cancelado ao atingir o limite de ${timeoutSeconds}s.\n")
                }
            }
        }
    }

    fun dismissTerminalConfirmation() { _state.value = _state.value.copy(showTerminalConfirmation = false) }

    fun sendTerminalInput(text: String, closeStdin: Boolean = false) {
        val handle = _state.value.terminalHandle ?: return
        val params = JSONObject().put("processHandle", handle)
        if (text.isNotEmpty()) params.put("deltaBase64", Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (closeStdin) params.put("closeStdin", true)
        viewModelScope.launch { runCatching { client?.request("process/writeStdin", params) }.onFailure { setError(it.message ?: "Falha ao escrever no processo.") } }
    }

    fun cancelTerminal() {
        val handle = _state.value.terminalHandle ?: return
        viewModelScope.launch {
            runCatching { client?.request("process/kill", JSONObject().put("processHandle", handle)) }
                .onFailure { setError("Não foi possível parar o processo: ${it.message}") }
        }
    }

    private suspend fun handleEvent(event: AppServerEvent) {
        val p = event.params
        val eventThreadId = p.optString("threadId")
        val activeThreadId = _state.value.activeThreadId
        val isThreadScoped = event.method.startsWith("thread/") || event.method.startsWith("turn/") || event.method.startsWith("item/")
        if (isThreadScoped && eventThreadId.isNotBlank() && activeThreadId != eventThreadId && event.method != "thread/name/updated") return
        when (event.method) {
            "thread/name/updated" -> {
                val threadId = p.optString("threadId")
                val name = p.optString("threadName").takeIf(String::isNotBlank) ?: return
                _state.value = _state.value.copy(
                    activeThreadName = if (_state.value.activeThreadId == threadId) name else _state.value.activeThreadName,
                    threads = _state.value.threads.map { if (it.id == threadId) it.copy(name = name) else it }
                )
            }
            "transport/error", "transport/closed" -> {
                terminalTimeoutJob?.cancel()
                terminalTimeoutJob = null
                terminalDecoder.reset()
                _state.value = _state.value.copy(
                    connected = false, connecting = false, connectionMessage = "Ligação interrompida.",
                    isTurnRunning = false, activeTurnId = null, pendingApproval = null,
                    terminalRunning = false, terminalHandle = null,
                    error = p.optString("message", "A ligação com o Codex foi interrompida.")
                )
                stopSessionNotificationIfIdle()
            }
            "turn/started" -> {
                val turn = p.optJSONObject("turn")
                _state.value = _state.value.copy(isTurnRunning = true, activeTurnId = turn?.optString("id"))
                updateSessionNotification("O Codex está a trabalhar")
            }
            "turn/completed" -> {
                val turn = p.optJSONObject("turn")
                val status = turn?.optString("status") ?: "completed"
                val error = turn?.optJSONObject("error")?.optString("message")
                val workspace = _state.value
                val syncError = workspace.workspaceUri?.let { uri -> runCatching { workspaceSync.toSaf(workspace.workspacePath, uri) }.exceptionOrNull() }

                _state.value = _state.value.copy(isTurnRunning = false, activeTurnId = null, pendingApproval = null, error = syncError?.let { "As alterações foram feitas no ambiente integrado, mas não foi possível sincronizá-las para a pasta escolhida: ${it.message}" } ?: if (status == "failed") error else null,
                    messages = if (status == "failed" && !error.isNullOrBlank()) _state.value.messages + ChatMessage(UUID.randomUUID().toString(), MessageRole.SYSTEM, error) else _state.value.messages)
                stopSessionNotificationIfIdle()
                loadThreads()
            }
            "item/agentMessage/delta" -> {
                val itemId = p.optString("itemId").ifBlank { p.optJSONObject("item")?.optString("id").orEmpty() }.ifBlank { UUID.randomUUID().toString() }
                val delta = p.optString("delta")
                updateAssistant(itemId, delta)
            }
            "item/started", "item/completed" -> {
                val item = p.optJSONObject("item") ?: return
                when (item.optString("type")) {
                    "agentMessage" -> upsertAssistant(item.optString("id"), item.optString("text"), event.method == "item/started")
                    "commandExecution" -> {
                        val command = item.optJSONArray("command")?.let { arr -> (0 until arr.length()).joinToString(" ") { arr.optString(it) } }.orEmpty()
                        if (event.method == "item/started" && command.isNotBlank()) addSystemMessage("A executar: $command")
                    }
                    "fileChange" -> {
                        val changes = item.optJSONArray("changes") ?: JSONArray()
                        val diffs = (0 until changes.length()).mapNotNull { changes.optJSONObject(it)?.optString("diff")?.takeIf(String::isNotBlank) }
                        if (diffs.isNotEmpty()) _state.value = _state.value.copy(currentDiff = diffs.joinToString("\n"))
                    }
                }
            }
            "turn/diff/updated" -> _state.value = _state.value.copy(currentDiff = p.optString("diff"))
            "item/commandExecution/requestApproval", "item/fileChange/requestApproval", "item/permissions/requestApproval" -> {
                val decisions = p.optJSONArray("availableDecisions")
                _state.value = _state.value.copy(pendingApproval = ApprovalRequest(
                    event.requestId ?: return,
                    event.method,
                    p.optString("threadId"),
                    p.optString("turnId"),
                    p.optString("itemId"),
                    if (event.method.contains("fileChange")) "Aprovar alterações" else if (event.method.contains("permissions")) "Permissões solicitadas" else "Aprovar comando",
                    buildString {
                        p.optString("reason").takeIf(String::isNotBlank)?.let { append(it).append("\n\n") }
                        p.optString("command").takeIf(String::isNotBlank)?.let { append(it).append("\n") }
                        p.optString("cwd").takeIf(String::isNotBlank)?.let { append("Diretório: ").append(it) }
                        p.optJSONObject("permissions")?.let { append("\n\nPermissões pedidas: ").append(it.toString(2)) }
                        p.optJSONObject("additionalPermissions")?.let { append("\n\nPermissões adicionais: ").append(it.toString(2)) }
                        if (isEmpty()) append("O Codex está à espera da tua decisão.")
                    },
                    if (decisions == null) listOf("accept", "decline", "cancel") else (0 until decisions.length()).map { decisions.optString(it) }.ifEmpty { listOf("accept", "decline", "cancel") },
                    JSONObject(p.toString())
                ))
            }
            "serverRequest/resolved" -> _state.value = _state.value.copy(pendingApproval = null)
            "process/outputDelta" -> {
                val handle = p.optString("processHandle")
                if (handle.isBlank() || handle != _state.value.terminalHandle) return
                val bytes = runCatching { Base64.decode(p.optString("deltaBase64"), Base64.DEFAULT) }.getOrDefault(ByteArray(0))
                appendTerminal(terminalDecoder.append(bytes))
            }
            "process/exited" -> {
                val handle = p.optString("processHandle")
                if (handle.isBlank() || handle != _state.value.terminalHandle) return
                terminalTimeoutJob?.cancel()
                appendTerminal(terminalDecoder.finish())
                val code = p.optInt("exitCode", -1)
                appendTerminal("\n[processo terminou: código $code]\n")
                val workspace = _state.value
                workspace.workspaceUri?.let { uri -> runCatching { workspaceSync.toSaf(workspace.workspacePath, uri) }
                    .onFailure { setError("O terminal terminou, mas não foi possível sincronizar os ficheiros do projeto: ${it.message}") }
                }
                _state.value = _state.value.copy(terminalRunning = false, terminalHandle = null)
                stopSessionNotificationIfIdle()
            }
            "warning", "configWarning" -> addSystemMessage(p.optString("message", p.optString("summary", "Aviso do Codex")))
        }
    }

    private fun updateAssistant(id: String, delta: String) {
        val current = _state.value.messages.toMutableList()
        val index = current.indexOfLast { it.id == id && it.role == MessageRole.ASSISTANT }
        if (index < 0) current += ChatMessage(id, MessageRole.ASSISTANT, delta, true)
        else current[index] = current[index].copy(text = current[index].text + delta, isStreaming = true)
        _state.value = _state.value.copy(messages = current)
    }

    private fun upsertAssistant(id: String, text: String, streaming: Boolean) {
        if (id.isBlank()) return
        val current = _state.value.messages.toMutableList()
        val index = current.indexOfLast { it.id == id && it.role == MessageRole.ASSISTANT }
        if (index < 0) current += ChatMessage(id, MessageRole.ASSISTANT, text, streaming)
        else current[index] = current[index].copy(text = text.ifBlank { current[index].text }, isStreaming = streaming)
        _state.value = _state.value.copy(messages = current)
    }

    private fun startSessionNotification(text: String) {
        runCatching { dev.codux.CodexSessionService.start(getApplication(), text) }
            .onFailure { setError("Não foi possível manter a notificação da sessão: ${it.message}") }
    }

    private fun updateSessionNotification(text: String) {
        runCatching { dev.codux.CodexSessionService.update(getApplication(), text) }
    }

    private fun stopSessionNotificationIfIdle() {
        if (!_state.value.isTurnRunning && !_state.value.terminalRunning) {
            runCatching { dev.codux.CodexSessionService.stop(getApplication()) }
        }
    }

    private fun appendTerminal(text: String) {
        _state.value = _state.value.copy(terminalOutput = (_state.value.terminalOutput + text).takeLast(80_000))
    }

    private fun addSystemMessage(text: String) {
        if (text.isBlank()) return
        _state.value = _state.value.copy(messages = _state.value.messages + ChatMessage(UUID.randomUUID().toString(), MessageRole.SYSTEM, text))
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun showError(text: String) { setError(text) }

    private fun setError(text: String) { _state.value = _state.value.copy(error = text) }

    private fun textFromContent(content: JSONArray?): String {
        if (content == null) return ""
        return (0 until content.length()).mapNotNull { content.optJSONObject(it)?.optString("text") }.joinToString("")
    }

    override fun onCleared() {
        terminalTimeoutJob?.cancel()
        eventJob?.cancel()
        client?.close()
        super.onCleared()
    }
}
